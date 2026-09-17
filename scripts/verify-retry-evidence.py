#!/usr/bin/env python3
"""Independently compare wire attempts, fixture effects/receipts, Room snapshots and visible actions."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tarfile
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET

PACKAGE = "com.safal207.androidreliabilitylab"
SECONDS = 5
FIELDS = "actionId, incidentId, targetStatus, effectId, receiptVersion, effectSequence"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def value_hash(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def read(path):
    return json.loads(path.read_text())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pending_ui(directory):
    spec = importlib.util.spec_from_file_location("prior_mutation_proof", Path(__file__).with_name("verify-offline-mutation-evidence.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.verify_ui(directory)  # Retain all prior list/field/absence assertions.


def confirmed_ui(directory):
    nodes = ET.parse(directory / "confirmed-window.xml").getroot().iter("node")
    texts = [n.get("text") for n in nodes if n.get("package") == PACKAGE and n.get("text")]
    expected = {"Android Reliability Lab", "INC-API-001", "INC-API-002", "INC-API-003",
                "HTTP boundary received", "DTO mapping checked", "API content rendered", "INVESTIGATING"}
    require(expected <= set(texts), "Missing confirmed incident list fields")
    require(not set(texts) & {"Pending synchronization", "OPEN", "Sending change…", "Unable to load incidents.",
                             "Unable to change incident status.", "Change is still pending. Confirmation failed.", "Retry pending change"},
            "Confirmed UI contains pending/stale/error state")
    target = texts[texts.index("INC-API-001"):texts.index("INC-API-002")]
    require({"RESOLVED", "Resolved on server"} <= set(target), "Target incident is not visibly confirmed")


def locate_retry(directory):
    pending_ui(directory)
    root = ET.parse(directory / "pending-window.xml").getroot()
    parents = {child: parent for parent in root.iter() for child in parent}
    labels = [n for n in root.iter("node") if n.get("package") == PACKAGE and n.get("text") == "Retry pending change"]
    require(len(labels) == 1, "Expected one visible retry button")
    button = labels[0]
    while button.get("clickable") != "true" and button in parents:
        button = parents[button]
    require(button.get("package") == PACKAGE and button.get("clickable") == "true" and button.get("enabled") == "true", "Retry is not enabled/clickable")
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", button.get("bounds", ""))
    require(bounds is not None, "Missing retry bounds")
    x1, y1, x2, y2 = map(int, bounds.groups())
    require(x2 > x1 and y2 > y1, "Empty retry bounds")
    x, y = (x1 + x2) // 2, (y1 + y2) // 2
    write(directory / "retry-action.json", {"action": "Retry pending change", "tap_count": 1,
          "tap_coordinates": [x, y], "button_bounds": [x1, y1, x2, y2],
          "pending_hierarchy_sha256": digest(directory / "pending-window.xml"),
          "time_utc": datetime.now(timezone.utc).isoformat()})
    print(x, y)


def requests(path):
    return [row for line in path.read_text().splitlines() if (row := json.loads(line))["path"] != "/health"]


def check_request(row, first):
    require(row["method"] == "PUT" and row["path"] == "/incidents/INC-API-001/status", "Unexpected HTTP attempt")
    require(row["body"] == {"status": "RESOLVED"} and row["body_sha256"] == value_hash(row["body"]), "Wire payload mismatch")
    require(row["canonical_payload"] == {"incidentId": "INC-API-001", "targetStatus": "RESOLVED"}, "Canonical payload mismatch")
    require(row["payload_sha256"] == value_hash(row["canonical_payload"]), "Canonical payload hash mismatch")
    require(str(uuid.UUID(row["idempotency_key"])) == row["idempotency_key"], "Missing canonical action identity")
    receipt = {"actionId": row["idempotency_key"], "incidentId": "INC-API-001", "targetStatus": "RESOLVED",
               "effectId": "effect-1", "receiptVersion": 1, "effectSequence": 1}
    require(row["receipt"] == receipt and row["receipt_sha256"] == value_hash(receipt), "Invalid receipt identity/hash")
    require(row["ledger_committed_before_response"] is True, "Missing durable boundary marker")
    require(row.get("user_agent", "").startswith("okhttp/"), "Attempt is not from Android HTTP client")
    require((row["status"], row["outcome"], row["applied"]) ==
            ((None, "effect-committed-response-lost", True) if first else (200, "http-response", False)),
            "Unexpected mutation outcome")


def ledger_rows(path, wire):
    with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as db:
        db.row_factory = sqlite3.Row
        require([r[0] for r in db.execute("PRAGMA integrity_check")] == ["ok"], "Fixture ledger integrity failure")
        effects = [dict(r) for r in db.execute("SELECT * FROM effects ORDER BY sequence")]
        attempts = [dict(r) for r in db.execute("SELECT * FROM attempts ORDER BY sequence")]
        state = [dict(r) for r in db.execute("SELECT * FROM incident_state ORDER BY id")]
    require(len(effects) == 1 and len(attempts) == len(wire), "Fixture effects/attempts invariant failed")
    require(state == [{"id": "INC-API-001", "status": "RESOLVED"}], "Server incident state was not applied")
    first = wire[0]
    require(effects == [{"sequence": 1, "action_id": first["idempotency_key"],
            "canonical_payload": canonical(first["canonical_payload"]), "payload_sha256": first["payload_sha256"],
            "effect_id": first["receipt"]["effectId"], "receipt": canonical(first["receipt"]), "receipt_sha256": first["receipt_sha256"]}],
            "Durable server effect/receipt differs from wire evidence")
    for index, (attempt, request) in enumerate(zip(attempts, wire), 1):
        expected = {"sequence": index, "action_id": request["idempotency_key"],
                    "canonical_payload": canonical(request["canonical_payload"]), "payload_sha256": request["payload_sha256"],
                    "status": request["status"], "outcome": request["outcome"], "applied": int(request["applied"]),
                    "receipt": canonical(request["receipt"]), "receipt_sha256": request["receipt_sha256"]}
        require(attempt == expected, "Fixture attempt differs from HTTP receipt")
    require(sum(r["applied"] for r in attempts) == 1, "Expected exactly one logical application")
    return {"effects": effects, "attempts": attempts, "incident_state": state}


def snapshot(path):
    hashes = {}
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        with tarfile.open(path, "r:") as archive:
            for member in archive:
                name = Path(member.name)
                if member.isdir() and member.name.rstrip("/") == "databases":
                    continue
                require(member.isfile() and name.parts == ("databases", name.name) and name.name not in hashes
                        and name.name in {"incidents.db", "incidents.db-wal", "incidents.db-shm", "incidents.db-journal"},
                        f"Unexpected snapshot member: {member.name}")
                data = archive.extractfile(member).read()
                hashes[name.name] = hashlib.sha256(data).hexdigest()
                (root / name.name).write_bytes(data)
        require((root / "incidents.db").read_bytes()[:16] == b"SQLite format 3\x00", "Missing SQLite file")
        with sqlite3.connect(root / "incidents.db") as db:
            db.row_factory = sqlite3.Row
            require([r[0] for r in db.execute("PRAGMA integrity_check")] == ["ok"], "Room snapshot integrity failed")
            require(db.execute("PRAGMA user_version").fetchone()[0] == 3, "Wrong Room schema version")
            identity = db.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone()[0]
            rows = {"schema_version": 3, "integrity_check": "ok", "room_identity_hash": identity,
                    "incidents": [dict(r) for r in db.execute("SELECT id, title, status FROM incidents ORDER BY id")],
                    "pending": [dict(r) for r in db.execute("SELECT mutationId, incidentId, targetStatus, createdOrder FROM pending_mutations ORDER BY createdOrder, mutationId")],
                    "receipts": [dict(r) for r in db.execute(f"SELECT {FIELDS} FROM mutation_receipts ORDER BY actionId")]}
    return rows, hashes


def check_local(directory, rows, request, pending):
    fixture = read(Path(__file__).resolve().parent.parent / "fixtures/incidents.json")
    for row in fixture:
        if row["id"] == "INC-API-001":
            row["status"] = "RESOLVED"
    require(rows["incidents"] == sorted(fixture, key=lambda row: row["id"]), "Local incident fields differ")
    require(rows["room_identity_hash"] == read(directory.parent / "room-rows.json")["room_identity_hash"], "Room identity changed")
    require(rows["pending"] == ([{"mutationId": request["idempotency_key"], "incidentId": "INC-API-001", "targetStatus": "RESOLVED", "createdOrder": 1}] if pending else []), "Unexpected pending rows/identity")
    require(rows["receipts"] == ([] if pending else [request["receipt"]]), "Unexpected durable local receipts")


def observe(directory):
    start, started = time.monotonic(), datetime.now(timezone.utc).isoformat()
    (directory / "mutation-mode.txt").write_text("online\n")
    while True:
        wire = requests(directory / "mutation-requests.jsonl")
        require(len(wire) == 1, "Automatic replay occurred")
        check_request(wire[0], True)
        ledger_rows(directory / "fixture-ledger.sqlite", wire)
        if time.monotonic() - start >= SECONDS:
            break
        time.sleep(.1)
    write(directory / "no-replay.json", {"started_utc": started, "ended_utc": datetime.now(timezone.utc).isoformat(),
          "duration_seconds": time.monotonic() - start, "minimum_seconds": SECONDS, "fixture_mode": "online",
          "initial_attempts": 1, "final_attempts": 1, "logical_effects": 1,
          "request_log_sha256": digest(directory / "mutation-requests.jsonl")})


def verify_pending(directory):
    pending_ui(directory)
    wire = requests(directory / "first-request.jsonl")
    require(len(wire) == 1, "Expected one ambiguous attempt")
    check_request(wire[0], True)
    ledger = ledger_rows(directory / "pending-fixture-ledger.sqlite", wire)
    rows, hashes = snapshot(directory / "pending-database.tar")
    stable_rows, stable_hashes = snapshot(directory / "pending-stability.tar")
    require(hashes == stable_hashes and rows == stable_rows, "Live pending snapshot was not stable")
    check_local(directory, rows, wire[0], True)
    observation = read(directory / "no-replay.json")
    require(observation["duration_seconds"] >= SECONDS and observation["minimum_seconds"] == SECONDS
            and (datetime.fromisoformat(observation["ended_utc"]) - datetime.fromisoformat(observation["started_utc"])).total_seconds() >= SECONDS,
            "Bounded no-replay observation is too short")
    require((observation["initial_attempts"], observation["final_attempts"], observation["logical_effects"], observation["fixture_mode"]) == (1, 1, 1, "online"), "Unexpected observation counts/mode")
    require(observation["request_log_sha256"] == digest(directory / "first-request.jsonl"), "First request log changed")
    write(directory / "pending-rows.json", rows)
    write(directory / "pending-proof.json", {"rows_sha256": digest(directory / "pending-rows.json"),
          "snapshot_files_sha256": hashes, "fixture_ledger_sha256": digest(directory / "pending-fixture-ledger.sqlite"),
          "pending_count": 1, "receipt_count": 0, "attempt_count": 1, "logical_effect_count": 1,
          "action_id": wire[0]["idempotency_key"], "server_receipt": wire[0]["receipt"],
          "inspection": "Two byte-identical DB/WAL/SHM copies while app idle and alive; independent SQLite integrity/row inspection. No process restart."})
    return wire[0], ledger


def verify(directory):
    first, prior_ledger = verify_pending(directory)
    confirmed_ui(directory)
    wire = requests(directory / "mutation-requests.jsonl")
    require(len(wire) == 2 and wire[0] == first, "Expected the original first request and one replay")
    check_request(wire[1], False)
    for key in ("idempotency_key", "body", "body_sha256", "canonical_payload", "payload_sha256", "receipt", "receipt_sha256"):
        require(first[key] == wire[1][key], f"Replay changed {key}")
    ledger = ledger_rows(directory / "fixture-ledger.sqlite", wire)
    require(prior_ledger["effects"] == ledger["effects"], "Original durable receipt/effect changed")
    rows, hashes = snapshot(directory / "confirmed-database.tar")
    check_local(directory, rows, first, False)
    http = read(directory / "before/receipt.json")
    room = read(directory.parent / "room-receipt.json")
    prior = read(directory.parent / "offline-mutation/pending-receipt.json")
    for key in ("proof_commit_sha", "actions_run_id", "actions_run_attempt", "apk_sha256", "fixture_sha256"):
        require(http[key] == room[key] == prior[key], f"Prior proof disagrees on {key}")
    require(http["proof_commit_sha"] == subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(), "Wrong proof commit")
    for name, expected in http["evidence_sha256"].items():
        require(digest(directory / "before" / name) == expected, f"Pre-action evidence changed: {name}")
    for stage in ("pending", "confirmed"):
        require((directory / f"{stage}-pid.txt").read_text().strip() == http["app_pid"], "App process changed during mutation/replay")
        png = (directory / f"{stage}-incident.png").read_bytes()
        require(png.startswith(b"\x89PNG\r\n\x1a\n") and len(png) > 100, f"Missing {stage} screenshot")
    resolve, retry = read(directory / "action.json"), read(directory / "retry-action.json")
    require(resolve["tap_count"] == retry["tap_count"] == 1, "Expected one visible Resolve and one explicit Retry")
    require(resolve["before_hierarchy_sha256"] == digest(directory / "before/window.xml"), "Resolve hierarchy changed")
    require(retry["pending_hierarchy_sha256"] == digest(directory / "pending-window.xml"), "Retry hierarchy changed")
    times = [resolve["time_utc"], first["time_utc"], read(directory / "no-replay.json")["ended_utc"], retry["time_utc"], wire[1]["time_utc"]]
    require(list(map(datetime.fromisoformat, times)) == sorted(map(datetime.fromisoformat, times)), "Request/action ordering is wrong")
    write(directory / "confirmed-rows.json", rows)
    write(directory / "fixture-rows.json", ledger)
    names = ("before/receipt.json", "action.json", "retry-action.json", "scenario-reset.txt", "no-replay.json", "first-request.jsonl",
             "mutation-requests.jsonl", "pending-window.xml", "confirmed-window.xml", "pending-incident.png", "confirmed-incident.png",
             "pending-database.tar", "pending-stability.tar", "confirmed-database.tar", "pending-fixture-ledger.sqlite", "fixture-ledger.sqlite",
             "pending-rows.json", "confirmed-rows.json", "fixture-rows.json", "pending-proof.json")
    write(directory / "retry-receipt.json", {
        **{key: http[key] for key in ("proof_commit_sha", "actions_run_id", "actions_run_attempt", "apk_sha256", "fixture_sha256", "api_level", "app_pid")},
        "attempt_count": 2, "logical_effect_count": 1, "same_action_id": True, "same_receipt": True,
        "action_id": first["idempotency_key"], "canonical_payload": first["canonical_payload"], "payload_sha256": first["payload_sha256"],
        "server_receipt": first["receipt"], "server_receipt_sha256": first["receipt_sha256"],
        "first_request": first, "explicit_replay": wire[1], "pending_before": 1, "pending_after": 0, "receipts_after": 1,
        "confirmed_snapshot_files_sha256": hashes,
        "evidence_sha256": {name: digest(directory / name) for name in names},
        "prior_gate_receipts_sha256": {name: digest(directory.parent / name) for name in (
            "receipt.json", "room-receipt.json", "room-instrumentation-results.json", "offline-mutation/pending-receipt.json", "offline-mutation/mutation-instrumentation-results.json")},
        "claim_ceiling": "Deterministic fixture only: one explicit replay of one stable action/payload after an applied effect and lost response; two attempts, one durable effect/receipt. Matching local receipt commits before confirmation. No automatic/background retry, production backend guarantee, process recovery, concurrent conflict or cross-device deduplication proof.",
    })
    print((directory / "retry-receipt.json").read_text())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    modes = parser.add_mutually_exclusive_group()
    for flag in ("pending-ui", "confirmed-ui", "locate-retry", "observe", "pending"):
        modes.add_argument(f"--{flag}", action="store_true")
    args = parser.parse_args()
    if args.pending_ui:
        pending_ui(args.directory)
    elif args.confirmed_ui:
        confirmed_ui(args.directory)
    elif args.locate_retry:
        locate_retry(args.directory)
    elif args.observe:
        observe(args.directory)
    elif args.pending:
        verify_pending(args.directory)
    else:
        verify(args.directory)


if __name__ == "__main__":
    main()
