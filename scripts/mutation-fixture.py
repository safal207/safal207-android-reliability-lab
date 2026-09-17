#!/usr/bin/env python3
"""A bounded mutation fixture: record PUT, then disconnect before any HTTP response."""

import argparse
import hashlib
import json
import os
import socket
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--requests", type=Path, required=True)
    parser.add_argument("--mode-file", type=Path, required=True)
    args = parser.parse_args()
    with args.requests.open("w") as log:
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

            def do_GET(self):
                status = 200 if self.path == "/health" else 404
                body = json.dumps({"ready": status == 200, "mutation_mode": args.mode_file.read_text().strip()}).encode()
                self.send_response(status)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                self.record(status=status)

            def do_PUT(self):
                body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
                payload = json.loads(body)
                mode = args.mode_file.read_text().strip()
                valid = self.path == "/incidents/INC-API-001/status" and payload == {"status": "RESOLVED"}
                if valid and mode == "disconnect":
                    self.record(status=None, outcome="transport-disconnect-before-response", applied=False,
                                body=payload, body_sha256=hashlib.sha256(body).hexdigest(),
                                idempotency_key=self.headers.get("Idempotency-Key"))
                    self.close_connection = True
                    self.connection.shutdown(socket.SHUT_RDWR)
                    self.connection.close()
                    return
                status = 204 if valid and mode == "online" else 400
                self.record(status=status, outcome="http-response", applied=status == 204,
                            body=payload, body_sha256=hashlib.sha256(body).hexdigest(),
                            idempotency_key=self.headers.get("Idempotency-Key"))
                self.send_response(status)
                self.send_header("Content-Length", "0")
                self.end_headers()

        HTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
