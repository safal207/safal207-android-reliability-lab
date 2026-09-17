#!/usr/bin/env python3
"""Require every named Room vector; adb exit status alone does not prove tests passed."""

import argparse
import hashlib
import json
import re
import subprocess
from collections import Counter
from pathlib import Path

TEST_CLASS = "com.safal207.androidreliabilitylab.data.RoomPersistenceTest"
EXPECTED = {
    "http200PersistsAllFieldsBeforeContent",
    "fileBackedDataSurvivesCloseAndFreshReopen",
    "secondSuccessfulRefreshReplacesRemovedAndChangedRows",
    "http500ReturnsErrorWithoutServingExistingRoomRows",
    "failedRoomWriteRollsBackAndNeverPublishesContent",
}


def verify(output):
    completed = []
    status = {}
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, value = line.removeprefix("INSTRUMENTATION_STATUS: ").split("=", 1)
            status[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.removeprefix("INSTRUMENTATION_STATUS_CODE: "))
            if code not in (0, 1):
                raise ValueError(f"Failed, skipped or ignored instrumentation test: {code} {status}")
            if code == 0:
                if status.get("class") != TEST_CLASS:
                    raise ValueError(f"Unexpected test class: {status}")
                completed.append(status.get("test"))
            status = {}
    if Counter(completed) != Counter(EXPECTED):
        raise ValueError(f"Missing/duplicate/extra Room tests: {completed}")
    if not re.search(r"^OK \(5 tests\)\s*$", output, re.MULTILINE):
        raise ValueError("Missing successful five-test runner summary")
    if not re.search(r"^INSTRUMENTATION_CODE: -1\s*$", output, re.MULTILINE):
        raise ValueError("Missing runner completion")
    return sorted(completed)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--test-apk", type=Path, required=True)
    args = parser.parse_args()
    output = (args.directory / "room-instrumentation.txt").read_bytes()
    tests = verify(output.decode())
    receipt = {
        "proof_commit_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
        "test_class": TEST_CLASS, "passed": tests, "failed": [], "skipped": [],
        "runner_output_sha256": hashlib.sha256(output).hexdigest(),
        "test_apk_sha256": hashlib.sha256(args.test_apk.read_bytes()).hexdigest(),
        "claim_ceiling": "HTTP persistence, file-backed Room close/reopen, deterministic replacement, HTTP 500 without cached fallback, and write failure rollback. No process-death recovery or offline behaviour claim.",
    }
    (args.directory / "room-instrumentation-results.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
