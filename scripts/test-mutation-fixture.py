#!/usr/bin/env python3
"""Exercise the actual runtime fixture over HTTP and inspect its durable SQLite ledger."""
import http.client
import json
from pathlib import Path
import socket
import sqlite3
import subprocess
import sys
import tempfile
import time
import unittest


class MutationFixtureTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.mode = self.root / "mode.txt"
        self.mode.write_text("online")
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            self.port = sock.getsockname()[1]
        self.start()

    def start(self):
        self.process = subprocess.Popen([sys.executable, str(Path(__file__).with_name("mutation-fixture.py")),
            "--port", str(self.port), "--requests", str(self.root / "requests.jsonl"),
            "--mode-file", str(self.mode), "--ledger", str(self.root / "ledger.sqlite")],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        deadline = time.monotonic() + 5
        while True:
            if self.process.poll() is not None:
                self.fail("Fixture exited before readiness")
            try:
                conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=1)
                conn.request("GET", "/health")
                self.assertEqual(200, conn.getresponse().status)
                conn.close()
                break
            except OSError:
                if time.monotonic() > deadline:
                    raise
                time.sleep(.05)

    def stop(self):
        self.process.terminate()
        self.process.wait(timeout=5)

    def tearDown(self):
        self.stop()
        self.temporary.cleanup()

    def put(self, status="RESOLVED"):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=3)
        try:
            conn.request("PUT", "/incidents/INC-API-001/status", json.dumps({"status": status}),
                         {"Idempotency-Key": "stable-fixture-action", "Content-Type": "application/json"})
            response = conn.getresponse()
            return response.status, json.loads(response.read())
        finally:
            conn.close()

    def rows(self, table):
        with sqlite3.connect(self.root / "ledger.sqlite") as db:
            db.row_factory = sqlite3.Row
            self.assertEqual("ok", db.execute("PRAGMA integrity_check").fetchone()[0])
            return [dict(row) for row in db.execute(f"SELECT * FROM {table}")]

    def test_online_receipt_and_conflicting_payload(self):
        code, receipt = self.put()
        self.assertEqual(200, code)
        self.assertEqual("stable-fixture-action", receipt["actionId"])
        self.assertEqual(1, len(self.rows("effects")))
        self.assertEqual(1, len(self.rows("attempts")))
        original = self.rows("effects")
        self.assertEqual(409, self.put("INVESTIGATING")[0])
        self.assertEqual(original, self.rows("effects"))
        self.assertEqual([{"id": "INC-API-001", "status": "RESOLVED"}], self.rows("incident_state"))

    def test_commit_then_disconnect_and_replay_after_fixture_reopen(self):
        self.mode.write_text("apply-disconnect")
        with self.assertRaises(http.client.RemoteDisconnected):
            self.put()
        effects = self.rows("effects")
        self.assertEqual(1, len(effects))
        self.assertEqual(1, len(self.rows("attempts")))
        self.assertEqual(1, self.rows("attempts")[0]["applied"])
        self.stop()
        self.mode.write_text("online")
        self.start()
        code, receipt = self.put()
        self.assertEqual(200, code)
        self.assertEqual(json.loads(effects[0]["receipt"]), receipt)
        self.assertEqual(effects, self.rows("effects"))
        self.assertEqual(2, len(self.rows("attempts")))
        self.assertEqual([1, 0], [row["applied"] for row in self.rows("attempts")])

    def test_bead004_disconnect_still_has_no_effect_or_receipt(self):
        self.mode.write_text("disconnect")
        with self.assertRaises(http.client.RemoteDisconnected):
            self.put()
        self.assertEqual([], self.rows("effects"))
        self.assertEqual("OPEN", self.rows("incident_state")[0]["status"])
        self.assertEqual(0, self.rows("attempts")[0]["applied"])
        self.assertIsNone(self.rows("attempts")[0]["receipt"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
