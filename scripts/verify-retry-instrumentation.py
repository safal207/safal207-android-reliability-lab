#!/usr/bin/env python3
"""Fail unless every Bead 005 vector and the additive migration actually passed."""

import argparse
import hashlib
import json
import os
import re
import subprocess
from collections import Counter
from pathlib import Path

TEST_CLASS = "com.safal207.androidreliabilitylab.data.RetrySafetyTest"
EXPECTED = {
    "vectorAOnlineSuccessStoresMatchingReceipt",
    "vectorBAppliedThenLostResponseKeepsSamePendingIdentity",
    "vectorCExplicitReplayReturnsSameReceiptWithoutSecondEffect",
    "vectorDSameIdentityDifferentPayloadIs409WithoutCorruption",
    "vectorEMismatchedReceiptNeverClearsPendingOrConfirms",
    "vectorFFailedReceiptTransactionRollsBackAndRetainsDurablePending",
    "vectorGReceiptSurvivesFileBackedCloseAndFreshReopen",
    "versionTwoMigrationPreservesPendingWithoutReplay",
}


def verify(output):
    completed, status = [], {}
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, value = line.removeprefix("INSTRUMENTATION_STATUS: ").split("=", 1)
            status[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.removeprefix("INSTRUMENTATION_STATUS_CODE: "))
            if code not in (0, 1):
                raise ValueError(f"Failed/skipped/ignored mutation test: {code} {status}")
            if code == 0:
                if status.get("class") != TEST_CLASS:
                    raise ValueError(f"Unexpected test class: {status}")
                completed.append(status.get("test"))
            status = {}
    if Counter(completed) != Counter(EXPECTED):
        raise ValueError(f"Missing/duplicate/extra mutation tests: {completed}")
    if not re.search(r"^OK \(8 tests\)\s*$", output, re.MULTILINE):
        raise ValueError("Missing successful eight-test summary")
    if not re.search(r"^INSTRUMENTATION_CODE: -1\s*$", output, re.MULTILINE):
        raise ValueError("Missing runner completion")
    return sorted(completed)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--test-apk", type=Path, required=True)
    args = parser.parse_args()
    output = (args.directory / "retry-instrumentation.txt").read_bytes()
    passed = verify(output.decode())
    receipt = {
        "proof_commit_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
        "actions_run_id": os.environ.get("GITHUB_RUN_ID"), "actions_run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "test_class": TEST_CLASS, "passed": passed, "failed": [], "skipped": [],
        "runner_output_sha256": hashlib.sha256(output).hexdigest(),
        "test_apk_sha256": hashlib.sha256(args.test_apk.read_bytes()).hexdigest(),
        "claim_ceiling": "Vectors A-G against an independent durable test fixture, explicit replay only, additive v2-v3 migration. No production backend, process recovery, background retry or concurrent conflict claim.",
    }
    (args.directory / "retry-instrumentation-results.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
