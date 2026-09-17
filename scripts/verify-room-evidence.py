#!/usr/bin/env python3
"""Inspect an app-owned SQLite snapshot with host sqlite3, independently of Room code."""

import argparse
import hashlib
import json
import sqlite3
import tarfile
import tempfile
from pathlib import Path


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def inspect_snapshot(archive, expected):
    # Keep the original tar untouched; SQLite may replay/checkpoint WAL in this copy.
    allowed = {"incidents.db", "incidents.db-wal", "incidents.db-shm", "incidents.db-journal"}
    hashes = {}
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        with tarfile.open(archive, "r:") as snapshot:
            for member in snapshot.getmembers():
                path = Path(member.name)
                if member.isdir() and member.name.rstrip("/") == "databases":
                    continue
                if not member.isfile() or path.parts != ("databases", path.name) or path.name not in allowed:
                    raise ValueError(f"Unexpected snapshot member: {member.name}")
                if path.name in hashes:
                    raise ValueError(f"Duplicate snapshot member: {member.name}")
                data = snapshot.extractfile(member).read()
                hashes[path.name] = sha256(data)
                (root / path.name).write_bytes(data)
        database = root / "incidents.db"
        if not database.is_file() or database.read_bytes()[:16] != b"SQLite format 3\x00":
            raise ValueError("Missing SQLite database file")
        with sqlite3.connect(database) as connection:
            connection.row_factory = sqlite3.Row
            integrity = connection.execute("PRAGMA integrity_check").fetchall()
            if [row[0] for row in integrity] != ["ok"]:
                raise ValueError("SQLite integrity check failed")
            version = connection.execute("PRAGMA user_version").fetchone()[0]
            if version != 1:
                raise ValueError(f"Expected Room schema version 1, found {version}")
            identity = connection.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone()
            if identity is None or not identity[0]:
                raise ValueError("Missing Room identity hash")
            rows = [dict(row) for row in connection.execute("SELECT id, title, status FROM incidents ORDER BY id")]
            if rows != sorted(expected, key=lambda row: row["id"]):
                raise ValueError(f"Room rows differ from HTTP fixture: {rows}")
        return {
            "schema_version": version, "integrity_check": "ok",
            "room_identity_hash": identity[0], "row_count": len(rows), "rows": rows,
        }, hashes


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    directory = args.directory
    fixture = (Path(__file__).resolve().parent.parent / "fixtures/incidents.json").read_bytes()
    http_receipt_bytes = (directory / "receipt.json").read_bytes()
    http_receipt = json.loads(http_receipt_bytes)
    if http_receipt["fixture_sha256"] != sha256(fixture):
        raise ValueError("HTTP receipt and committed fixture do not match")
    archive = directory / "room-database.tar"
    rows, hashes = inspect_snapshot(archive, json.loads(fixture))
    rows_path = directory / "room-rows.json"
    rows_path.write_text(json.dumps(rows, indent=2) + "\n")
    receipt = {
        "proof_commit_sha": http_receipt["proof_commit_sha"],
        "actions_run_id": http_receipt["actions_run_id"],
        "actions_run_attempt": http_receipt["actions_run_attempt"],
        "apk_sha256": http_receipt["apk_sha256"],
        "fixture_sha256": sha256(fixture),
        "http_receipt_sha256": sha256(http_receipt_bytes),
        "snapshot_sha256": sha256(archive.read_bytes()),
        "snapshot_files_sha256": hashes,
        "rows_sha256": sha256(rows_path.read_bytes()),
        "inspection": "run-as snapshot of app databases after force-stop; host Python sqlite3 inspects a disposable copy, including WAL",
        "row_count": rows["row_count"],
        "claim_ceiling": "Successful HTTP data exists in the app-owned Room database and matches the fixture. Close/reopen and replacement are checked separately by instrumentation. No offline fallback, retries, process-death recovery, idempotency or conflict handling is proven.",
    }
    (directory / "room-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
