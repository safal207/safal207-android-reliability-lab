#!/usr/bin/env python3
"""Deterministic PUT fixture. SQLite FULL commits effects/receipts before response loss."""

import argparse
import hashlib
import json
import os
import socket
import sqlite3
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def sha(value):
    return hashlib.sha256(value.encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--requests", type=Path, required=True)
    parser.add_argument("--mode-file", type=Path, required=True)
    parser.add_argument("--ledger", type=Path, required=True)
    args = parser.parse_args()
    ledger = sqlite3.connect(args.ledger)
    ledger.row_factory = sqlite3.Row
    ledger.execute("PRAGMA journal_mode=DELETE")
    ledger.execute("PRAGMA synchronous=FULL")
    ledger.executescript("""
        CREATE TABLE IF NOT EXISTS effects (
            sequence INTEGER PRIMARY KEY, action_id TEXT UNIQUE NOT NULL,
            canonical_payload TEXT NOT NULL, payload_sha256 TEXT NOT NULL,
            effect_id TEXT UNIQUE NOT NULL, receipt TEXT NOT NULL, receipt_sha256 TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS attempts (
            sequence INTEGER PRIMARY KEY, action_id TEXT NOT NULL,
            canonical_payload TEXT NOT NULL, payload_sha256 TEXT NOT NULL,
            status INTEGER, outcome TEXT NOT NULL, applied INTEGER NOT NULL,
            receipt TEXT, receipt_sha256 TEXT
        );
        CREATE TABLE IF NOT EXISTS incident_state (id TEXT PRIMARY KEY, status TEXT NOT NULL);
        INSERT OR IGNORE INTO incident_state VALUES ('INC-API-001', 'OPEN');
    """)
    with args.requests.open("a") as log:
        class Handler(BaseHTTPRequestHandler):
            def record(self, **fields):
                log.write(json.dumps({
                    "time_utc": datetime.now(timezone.utc).isoformat(),
                    "method": self.command, "path": self.path,
                    "user_agent": self.headers.get("User-Agent"),
                    **fields,
                }, sort_keys=True) + "\n")
                log.flush()
                os.fsync(log.fileno())

            def respond(self, status, payload):
                body = canonical(payload).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_GET(self):
                status = 200 if self.path == "/health" else 404
                self.record(status=status)
                self.respond(status, {"ready": status == 200, "mutation_mode": args.mode_file.read_text().strip()})

            def do_PUT(self):
                body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
                try:
                    payload = json.loads(body)
                except (ValueError, UnicodeError):
                    self.respond(400, {"error": "invalid JSON"})
                    return
                mode = args.mode_file.read_text().strip()
                action_id = self.headers.get("Idempotency-Key")
                valid = (self.path == "/incidents/INC-API-001/status" and isinstance(payload, dict)
                         and set(payload) == {"status"} and payload["status"] in ("OPEN", "INVESTIGATING", "RESOLVED")
                         and bool(action_id) and mode in ("disconnect", "online", "apply-disconnect"))
                if not valid:
                    self.record(status=400, outcome="invalid-request", body=payload, idempotency_key=action_id, applied=False)
                    self.respond(400, {"error": "invalid action"})
                    return
                value = canonical({"incidentId": "INC-API-001", "targetStatus": payload["status"]})
                receipt = None
                applied = False
                status, outcome = 200, "http-response"
                with ledger:  # One durable transaction binds payload, state, effect, receipt and attempt.
                    bound = ledger.execute("SELECT * FROM effects WHERE action_id=?", (action_id,)).fetchone()
                    if bound and bound["canonical_payload"] != value:
                        status, outcome = 409, "payload-conflict"
                    elif mode == "disconnect":  # Preserve Bead 004's pre-effect failure vector.
                        status, outcome = None, "transport-disconnect-before-response"
                    else:
                        if bound:
                            receipt = json.loads(bound["receipt"])
                        else:
                            sequence = ledger.execute("SELECT COALESCE(MAX(sequence), 0) + 1 FROM effects").fetchone()[0]
                            receipt = {"actionId": action_id, "incidentId": "INC-API-001", "targetStatus": payload["status"],
                                       "effectId": f"effect-{sequence}", "receiptVersion": 1, "effectSequence": sequence}
                            encoded = canonical(receipt)
                            ledger.execute("UPDATE incident_state SET status=? WHERE id='INC-API-001'", (payload["status"],))
                            ledger.execute("INSERT INTO effects VALUES (?, ?, ?, ?, ?, ?, ?)",
                                           (sequence, action_id, value, sha(value), receipt["effectId"], encoded, sha(encoded)))
                            applied = True
                        if mode == "apply-disconnect":
                            status, outcome = None, "effect-committed-response-lost"
                    encoded = canonical(receipt) if receipt else None
                    ledger.execute("INSERT INTO attempts (action_id, canonical_payload, payload_sha256, status, outcome, applied, receipt, receipt_sha256) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                   (action_id, value, sha(value), status, outcome, int(applied), encoded, sha(encoded) if encoded else None))
                # A client cannot see success or disconnection until FULL commit and fsynced log finish.
                self.record(status=status, outcome=outcome, applied=applied, body=payload,
                            body_sha256=hashlib.sha256(body).hexdigest(), idempotency_key=action_id,
                            canonical_payload=json.loads(value), payload_sha256=sha(value),
                            receipt=receipt, receipt_sha256=sha(encoded) if encoded else None,
                            ledger_committed_before_response=True)
                if status is None:
                    self.close_connection = True
                    self.connection.shutdown(socket.SHUT_RDWR)
                    self.connection.close()
                    return
                self.respond(status, receipt if receipt else {"error": "action identity is already bound to another payload"})

        HTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
