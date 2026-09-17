#!/usr/bin/env python3
"""Check one visible queued action, bounded request observation, and independent SQLite rows."""

import argparse
import hashlib
import json
import re
import sqlite3
import subprocess
import tarfile
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

PACKAGE = "com.safal207.androidreliabilitylab"
OBSERVATION_SECONDS = 5


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


def locate_action(directory):
    root = ET.parse(directory / "before/window.xml").getroot()
    parents = {child: parent for parent in root.iter() for child in parent}
    labels = [node for node in root.iter("node") if node.get("package") == PACKAGE and node.get("text") == "Resolve incident"]
    if len(labels) != 1:
        raise ValueError("Expected one visible Resolve incident button")
    button = labels[0]
    while button.get("clickable") != "true" and button in parents:
        button = parents[button]
    if button.get("package") != PACKAGE or button.get("clickable") != "true" or button.get("enabled") != "true":
        raise ValueError("Resolve incident button is not enabled/clickable")
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", button.get("bounds", ""))
    if bounds is None:
        raise ValueError("Missing visible button bounds")
    x1, y1, x2, y2 = map(int, bounds.groups())
    if x2 <= x1 or y2 <= y1:
        raise ValueError("Empty button bounds")
    x, y = (x1 + x2) // 2, (y1 + y2) // 2
    write_json(directory / "action.json", {
        "action": "Resolve incident", "incident_id": "INC-API-001", "target_status": "RESOLVED",
        "tap_count": 1, "tap_coordinates": [x, y], "button_bounds": [x1, y1, x2, y2],
        "before_hierarchy_sha256": digest(directory / "before/window.xml"),
        "time_utc": datetime.now(timezone.utc).isoformat(),
    })
    print(x, y)


def verify_ui(directory):
    root = ET.parse(directory / "pending-window.xml").getroot()
    texts = [node.get("text") for node in root.iter("node") if node.get("package") == PACKAGE and node.get("text")]
    for text in ("Android Reliability Lab", "INC-API-001", "INC-API-002", "INC-API-003",
                 "HTTP boundary received", "DTO mapping checked", "API content rendered", "INVESTIGATING"):
        if text not in texts:
            raise ValueError(f"Missing app-owned UI text: {text}")
    if any(text in texts for text in ("OPEN", "Resolved on server", "Sending change…", "Unable to change incident status.", "Unable to load incidents.")):
        raise ValueError("Pending UI contains stale, sending, server-confirmed or error state")
    first_incident = texts[texts.index("INC-API-001"):texts.index("INC-API-002")]
    if not {"RESOLVED", "Pending synchronization"} <= set(first_incident):
        raise ValueError("Target incident does not visibly distinguish local pending state")


def mutation_request(directory):
    records = [json.loads(line) for line in (directory / "mutation-requests.jsonl").read_text().splitlines()]
    requests = [record for record in records if record["path"] != "/health"]
    if len(requests) != 1:
        raise ValueError(f"Expected one mutation attempt and no replay, found {len(requests)}")
    request = requests[0]
    if (request["method"], request["path"], request["status"], request["outcome"], request["applied"]) != (
        "PUT", "/incidents/INC-API-001/status", None, "transport-disconnect-before-response", False,
    ):
        raise ValueError(f"Not the expected transport failure: {request}")
    if request["body"] != {"status": "RESOLVED"} or not request["idempotency_key"]:
        raise ValueError("Missing stable identity or unexpected mutation DTO")
    if not request.get("user_agent", "").startswith("okhttp/"):
        raise ValueError("Mutation attempt is not from the Android HTTP client")
    return request


def observe(directory):
    mutation_request(directory)
    # The fixture can accept another PUT now; the app must leave the intent pending.
    (directory / "mutation-mode.txt").write_text("online\n")
    started_utc = datetime.now(timezone.utc).isoformat()
    started = time.monotonic()
    while time.monotonic() - started < OBSERVATION_SECONDS:
        mutation_request(directory)
        time.sleep(0.1)
    mutation_request(directory)
    write_json(directory / "no-replay.json", {
        "started_utc": started_utc, "ended_utc": datetime.now(timezone.utc).isoformat(),
        "duration_seconds": time.monotonic() - started,
        "minimum_seconds": OBSERVATION_SECONDS,
        "fixture_mode_during_observation": "online",
        "initial_mutation_requests": 1, "final_mutation_requests": 1,
        "request_log_sha256": digest(directory / "mutation-requests.jsonl"),
    })


def inspect_snapshot(directory, expected):
    hashes = {}
    allowed = {"incidents.db", "incidents.db-wal", "incidents.db-shm", "incidents.db-journal"}
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        with tarfile.open(directory / "pending-database.tar", "r:") as archive:
            for member in archive:
                path = Path(member.name)
                if member.isdir() and member.name.rstrip("/") == "databases":
                    continue
                if not member.isfile() or path.parts != ("databases", path.name) or path.name not in allowed or path.name in hashes:
                    raise ValueError(f"Unexpected database snapshot member: {member.name}")
                data = archive.extractfile(member).read()
                hashes[path.name] = hashlib.sha256(data).hexdigest()
                (root / path.name).write_bytes(data)
        database = root / "incidents.db"
        if not database.is_file() or database.read_bytes()[:16] != b"SQLite format 3\x00":
            raise ValueError("Missing SQLite database")
        connection = sqlite3.connect(database)
        try:
            connection.row_factory = sqlite3.Row
            if [row[0] for row in connection.execute("PRAGMA integrity_check")] != ["ok"]:
                raise ValueError("SQLite integrity failure")
            version = connection.execute("PRAGMA user_version").fetchone()[0]
            if version != 3:
                raise ValueError(f"Expected schema version 3, found {version}")
            if connection.execute("SELECT COUNT(*) FROM mutation_receipts").fetchone()[0] != 0:
                raise ValueError("An unconfirmed transport failure must not create a receipt")
            identity = connection.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone()[0]
            incidents = [dict(row) for row in connection.execute("SELECT id, title, status FROM incidents ORDER BY id")]
            pending = [dict(row) for row in connection.execute("SELECT mutationId, incidentId, targetStatus, createdOrder FROM pending_mutations ORDER BY createdOrder, mutationId")]
            if incidents != sorted(expected, key=lambda row: row["id"]):
                raise ValueError(f"Unexpected local incident state: {incidents}")
            if len(pending) != 1:
                raise ValueError(f"Expected exactly one pending row, found {pending}")
            row = pending[0]
            if (row["incidentId"], row["targetStatus"], row["createdOrder"]) != ("INC-API-001", "RESOLVED", 1):
                raise ValueError(f"Unexpected pending intent: {row}")
            if str(uuid.UUID(row["mutationId"])) != row["mutationId"]:
                raise ValueError("Missing canonical local mutation id")
        finally:
            connection.close()
    return {"schema_version": version, "integrity_check": "ok", "room_identity_hash": identity,
            "local_incidents": incidents, "pending_mutations": pending}, hashes


def verify(directory):
    verify_ui(directory)
    request = mutation_request(directory)
    http = json.loads((directory / "before/receipt.json").read_text())
    room = json.loads((directory.parent / "room-receipt.json").read_text())
    for key in ("proof_commit_sha", "actions_run_id", "actions_run_attempt", "apk_sha256", "fixture_sha256"):
        if http[key] != room[key]:
            raise ValueError(f"Existing Room proof and mutation scenario disagree: {key}")
    if http["proof_commit_sha"] != subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip():
        raise ValueError("HTTP proof is not for this commit")
    for name, expected_hash in http["evidence_sha256"].items():
        if digest(directory / "before" / name) != expected_hash:
            raise ValueError(f"Changed pre-mutation HTTP evidence: {name}")
    fixture = Path(__file__).resolve().parent.parent / "fixtures/incidents.json"
    if digest(fixture) != http["fixture_sha256"]:
        raise ValueError("HTTP fixture mismatch")
    expected = json.loads(fixture.read_text())
    for incident in expected:
        if incident["id"] == "INC-API-001":
            incident["status"] = "RESOLVED"
    rows, hashes = inspect_snapshot(directory, expected)
    if rows["pending_mutations"][0]["mutationId"] != request["idempotency_key"]:
        raise ValueError("Pending identity differs from the first HTTP action identity")
    with sqlite3.connect(f"file:{directory / 'fixture-ledger.sqlite'}?mode=ro", uri=True) as ledger:
        if ledger.execute("SELECT COUNT(*) FROM effects").fetchone()[0] != 0:
            raise ValueError("Bead 004 pre-effect disconnect unexpectedly applied a server effect")
        if ledger.execute("SELECT COUNT(*) FROM attempts").fetchone()[0] != 1:
            raise ValueError("Bead 004 ledger does not contain exactly one attempt")
    initial_rows = json.loads((directory.parent / "room-rows.json").read_text())
    if rows["room_identity_hash"] != initial_rows["room_identity_hash"]:
        raise ValueError("Room identity changed during mutation")
    action = json.loads((directory / "action.json").read_text())
    if action["tap_count"] != 1 or action["before_hierarchy_sha256"] != digest(directory / "before/window.xml"):
        raise ValueError("Missing one visible user action")
    if datetime.fromisoformat(action["time_utc"]) > datetime.fromisoformat(request["time_utc"]):
        raise ValueError("Mutation request predates the user action")
    observation = json.loads((directory / "no-replay.json").read_text())
    if observation["duration_seconds"] < OBSERVATION_SECONDS or observation["minimum_seconds"] != OBSERVATION_SECONDS:
        raise ValueError("No-replay observation is too short")
    if (observation["initial_mutation_requests"], observation["final_mutation_requests"], observation["fixture_mode_during_observation"]) != (1, 1, "online"):
        raise ValueError("Unexpected replay observation")
    if observation["request_log_sha256"] != digest(directory / "mutation-requests.jsonl"):
        raise ValueError("Requests changed after the observation window")
    screenshot = (directory / "pending-incident.png").read_bytes()
    if not screenshot.startswith(b"\x89PNG\r\n\x1a\n") or len(screenshot) < 100:
        raise ValueError("Missing pending screenshot")
    if (directory / "app-pid.txt").read_text().strip() != http["app_pid"]:
        raise ValueError("App process changed between initial GET and queued UI")
    write_json(directory / "pending-rows.json", rows)
    receipt = {
        **{key: http[key] for key in ("proof_commit_sha", "actions_run_id", "actions_run_attempt", "apk_sha256", "fixture_sha256")},
        "api_level": http["api_level"], "app_pid": http["app_pid"], "initial_get": http["request"],
        "mutation_attempt": request, "pending_count": 1, "pending_mutation": rows["pending_mutations"][0],
        "snapshot_sha256": digest(directory / "pending-database.tar"), "snapshot_files_sha256": hashes,
        "rows_sha256": digest(directory / "pending-rows.json"),
        "prior_room_receipt_sha256": digest(directory.parent / "room-receipt.json"),
        "evidence_sha256": {name: digest(directory / name) for name in (
            "fixture-ledger.sqlite", "before/receipt.json", "action.json", "no-replay.json", "mutation-requests.jsonl", "pending-incident.png", "pending-window.xml",
        )},
        "no_replay_observation_seconds": observation["duration_seconds"],
        "inspection": "run-as app-owned database plus WAL after force-stop; independent host SQLite on a disposable copy",
        "claim_ceiling": "One visible action is locally pending after a deterministic transport disconnect; no automatic replay observed for the stated interval. Database reopen is tested separately. No retry policy, idempotency, duplicate-effect safety, background work, process recovery, conflict handling or offline startup proof.",
    }
    write_json(directory / "pending-receipt.json", receipt)
    print(json.dumps(receipt, indent=2))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--locate-action", action="store_true")
    mode.add_argument("--ui-only", action="store_true")
    mode.add_argument("--observe", action="store_true")
    args = parser.parse_args()
    if args.locate_action:
        locate_action(args.directory)
    elif args.ui_only:
        verify_ui(args.directory)
    elif args.observe:
        observe(args.directory)
    else:
        verify(args.directory)


if __name__ == "__main__":
    main()
