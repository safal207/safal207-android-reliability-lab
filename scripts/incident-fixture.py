#!/usr/bin/env python3
"""Deterministic host fixture. Only the emulator should request /incidents in CI."""

import argparse
import hashlib
import json
import os
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--requests", type=Path, required=True)
    args = parser.parse_args()
    payload = (Path(__file__).resolve().parent.parent / "fixtures/incidents.json").read_bytes()
    payload_sha256 = hashlib.sha256(payload).hexdigest()
    args.requests.parent.mkdir(parents=True, exist_ok=True)

    # A fresh log prevents receipts from an earlier run satisfying this run.
    with args.requests.open("w", encoding="utf-8") as request_log:
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path == "/health":
                    status, body = 200, b'{"ready":true}'
                elif self.path == "/incidents":
                    status, body = 200, payload
                else:
                    status, body = 404, b'{"error":"not found"}'
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                self.wfile.flush()
                request_log.write(json.dumps({
                    "time_utc": datetime.now(timezone.utc).isoformat(),
                    "method": "GET", "path": self.path, "status": status,
                    "response_sha256": hashlib.sha256(body).hexdigest(),
                    "user_agent": self.headers.get("User-Agent"),
                    "peer": self.client_address[0],
                }, sort_keys=True) + "\n")
                request_log.flush()
                os.fsync(request_log.fileno())

        server = HTTPServer(("127.0.0.1", args.port), Handler)
        print(json.dumps({"port": args.port, "fixture_sha256": payload_sha256}), flush=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
