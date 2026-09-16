#!/usr/bin/env python3
"""Check the HTTP receipt and app-owned UI text against the committed fixture."""

import argparse
import hashlib
import json
import os
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.safal207.androidreliabilitylab"


def verify_ui(directory, incidents):
    root = ET.parse(directory / "window.xml").getroot()
    texts = {node.get("text") for node in root.iter("node") if node.get("package") == PACKAGE}
    expected = {"Android Reliability Lab"}
    for incident in incidents:
        expected.update(incident.values())
    if not expected <= texts:
        raise ValueError(f"Missing app UI text: {sorted(expected - texts)}")
    if {"INC-001", "INC-002", "INC-003", "Unable to load incidents."} & texts:
        raise ValueError("Unexpected fake/error UI in HTTP success proof")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--ui-only", action="store_true")
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    fixture = (Path(__file__).resolve().parent.parent / "fixtures/incidents.json").read_bytes()
    verify_ui(args.directory, json.loads(fixture))
    if args.ui_only:
        return
    if args.apk is None:
        parser.error("--apk is required for the full proof")
    requests = [json.loads(line) for line in (args.directory / "fixture-requests.jsonl").read_text().splitlines()]
    incident_requests = [request for request in requests if request["path"] != "/health"]
    if len(incident_requests) != 1:
        raise ValueError(f"Expected exactly one incident request, found {len(incident_requests)}")
    request = incident_requests[0]
    fixture_sha256 = hashlib.sha256(fixture).hexdigest()
    if (request["method"], request["path"], request["status"], request["response_sha256"]) != (
        "GET", "/incidents", 200, fixture_sha256,
    ):
        raise ValueError(f"Unexpected HTTP receipt: {request}")
    if not (request.get("user_agent") or "").startswith("okhttp/"):
        raise ValueError("Expected the Android HTTP client's User-Agent")
    screenshot = (args.directory / "incident-list.png").read_bytes()
    if not screenshot.startswith(b"\x89PNG\r\n\x1a\n") or len(screenshot) < 100:
        raise ValueError("Missing PNG screenshot")
    pid = (args.directory / "app-pid.txt").read_text().strip()
    if not pid.isdigit():
        raise ValueError("Missing live app PID")
    if (args.directory / "api-level.txt").read_text().strip() != "35":
        raise ValueError("Runtime proof requires API 35")
    receipt = {
        "proof_commit_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
        "actions_run_id": os.environ.get("GITHUB_RUN_ID"),
        "actions_run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "apk_sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        "fixture_sha256": fixture_sha256,
        "request": request,
        "app_pid": pid,
        "api_level": 35,
        "evidence_sha256": {
            name: hashlib.sha256((args.directory / name).read_bytes()).hexdigest()
            for name in ("incident-list.png", "window.xml", "fixture-requests.jsonl")
        },
        "claim_ceiling": "HTTP fetch -> DTO mapping -> Loading/Content/Error (JVM); API 35 runtime success only. No persistence, offline, retry safety, idempotency or recovery proof.",
    }
    (args.directory / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
