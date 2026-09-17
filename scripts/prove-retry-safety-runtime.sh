#!/usr/bin/env bash
set -euo pipefail

# Preserve and execute every Bead 001-004 gate before the new isolated scenario.
bash scripts/prove-offline-mutation-runtime.sh

evidence=runtime-evidence/retry-safety
package=com.safal207.androidreliabilitylab
mkdir -p "$evidence"
# Test isolation only: the preceding Bead 004 snapshots/receipts remain immutable.
# No queue migration, startup recovery or production reset path is being exercised.
adb shell pm clear "$package" | tee "$evidence/scenario-reset.txt"
grep -qx 'Success' <(tr -d '\r' < "$evidence/scenario-reset.txt")
EVIDENCE_DIRECTORY="$evidence/before" bash scripts/prove-http-runtime.sh

printf 'apply-disconnect\n' > "$evidence/mutation-mode.txt"
python3 scripts/mutation-fixture.py --requests "$evidence/mutation-requests.jsonl" \
  --mode-file "$evidence/mutation-mode.txt" --ledger "$evidence/fixture-ledger.sqlite" \
  > "$evidence/mutation-server.log" 2>&1 &
fixture_pid=$!
finish() {
  local result=$?
  if [[ "$result" -ne 0 ]]; then
    adb logcat -d -t 1000 > "$evidence/failure-logcat.txt" 2>&1 || true
    adb exec-out screencap -p > "$evidence/failure-screen.png" || true
  fi
  kill "$fixture_pid" 2>/dev/null || true
  exit "$result"
}
trap finish EXIT
for attempt in {1..30}; do
  kill -0 "$fixture_pid"
  if curl --fail --silent --max-time 1 http://127.0.0.1:8765/health > "$evidence/transport-health.json"; then
    break
  fi
  sleep 1
done
python3 - "$evidence/transport-health.json" <<'PY'
import json, sys
assert json.load(open(sys.argv[1])) == {"ready": True, "mutation_mode": "apply-disconnect"}
PY
read -r tap_x tap_y < <(python3 scripts/verify-offline-mutation-evidence.py "$evidence" --locate-action)
adb shell input tap "$tap_x" "$tap_y"
for attempt in {1..30}; do
  if adb shell uiautomator dump /sdcard/pending-window.xml && \
     adb pull /sdcard/pending-window.xml "$evidence/pending-window.xml" && \
     python3 scripts/verify-retry-evidence.py "$evidence" --pending-ui; then
    break
  fi
  sleep 1
done
python3 scripts/verify-retry-evidence.py "$evidence" --pending-ui
python3 scripts/verify-retry-evidence.py "$evidence" --observe
adb exec-out screencap -p > "$evidence/pending-incident.png"
adb shell pidof "$package" | tr -d '\r' > "$evidence/pending-pid.txt"
cmp "$evidence/before/app-pid.txt" "$evidence/pending-pid.txt"
# The app stays alive. UI has completed its pending transaction and is idle.
# Require identical DB/WAL/SHM bytes in two consecutive copies before inspection.
adb exec-out run-as "$package" tar -cf - databases > "$evidence/pending-database.tar"
adb exec-out run-as "$package" tar -cf - databases > "$evidence/pending-stability.tar"
cp "$evidence/fixture-ledger.sqlite" "$evidence/pending-fixture-ledger.sqlite"
cp "$evidence/mutation-requests.jsonl" "$evidence/first-request.jsonl"
python3 scripts/verify-retry-evidence.py "$evidence" --pending

read -r tap_x tap_y < <(python3 scripts/verify-retry-evidence.py "$evidence" --locate-retry)
adb shell input tap "$tap_x" "$tap_y"
for attempt in {1..30}; do
  if adb shell uiautomator dump /sdcard/confirmed-window.xml && \
     adb pull /sdcard/confirmed-window.xml "$evidence/confirmed-window.xml" && \
     python3 scripts/verify-retry-evidence.py "$evidence" --confirmed-ui; then
    break
  fi
  sleep 1
done
python3 scripts/verify-retry-evidence.py "$evidence" --confirmed-ui
adb exec-out screencap -p > "$evidence/confirmed-incident.png"
adb shell pidof "$package" | tr -d '\r' > "$evidence/confirmed-pid.txt"
cmp "$evidence/before/app-pid.txt" "$evidence/confirmed-pid.txt"
adb logcat -d -t 500 > "$evidence/logcat.txt"
adb shell am force-stop "$package"
if adb shell pidof "$package"; then
  echo "App is still live; cannot take the final quiescent snapshot" >&2
  exit 1
fi
adb exec-out run-as "$package" tar -cf - databases > "$evidence/confirmed-database.tar"
python3 scripts/verify-retry-evidence.py "$evidence"

mapfile -t test_apks < <(find instrumentation-apk -type f -name '*.apk')
test "${#test_apks[@]}" -eq 1
timeout 180 adb shell am instrument -w -r \
  -e class com.safal207.androidreliabilitylab.data.RetrySafetyTest \
  com.safal207.androidreliabilitylab.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$evidence/retry-instrumentation.txt"
python3 scripts/verify-retry-instrumentation.py "$evidence" --test-apk "${test_apks[0]}"
