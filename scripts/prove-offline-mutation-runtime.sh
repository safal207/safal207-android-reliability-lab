#!/usr/bin/env bash
set -euo pipefail

# Execute every existing Bead 001-003 gate and keep its original evidence files.
bash scripts/prove-room-runtime.sh

evidence=runtime-evidence/offline-mutation
package=com.safal207.androidreliabilitylab
mkdir -p "$evidence"
# A separate cold launch with the same verified HTTP flow starts this scenario.
EVIDENCE_DIRECTORY="$evidence/before" bash scripts/prove-http-runtime.sh

printf 'disconnect\n' > "$evidence/mutation-mode.txt"
python3 scripts/mutation-fixture.py --requests "$evidence/mutation-requests.jsonl" \
  --mode-file "$evidence/mutation-mode.txt" > "$evidence/mutation-server.log" 2>&1 &
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
assert json.load(open(sys.argv[1])) == {"ready": True, "mutation_mode": "disconnect"}
PY

# Locate the one visible enabled button from the real hierarchy, then tap once.
read -r tap_x tap_y < <(python3 scripts/verify-offline-mutation-evidence.py "$evidence" --locate-action)
adb shell input tap "$tap_x" "$tap_y"
for attempt in {1..30}; do
  if adb shell uiautomator dump /sdcard/pending-window.xml && \
     adb pull /sdcard/pending-window.xml "$evidence/pending-window.xml" && \
     python3 scripts/verify-offline-mutation-evidence.py "$evidence" --ui-only; then
    break
  fi
  sleep 1
done
python3 scripts/verify-offline-mutation-evidence.py "$evidence" --ui-only
python3 scripts/verify-offline-mutation-evidence.py "$evidence" --observe
adb shell uiautomator dump /sdcard/pending-window.xml
adb pull /sdcard/pending-window.xml "$evidence/pending-window.xml"
adb exec-out screencap -p > "$evidence/pending-incident.png"
adb shell pidof "$package" | tr -d '\r' > "$evidence/app-pid.txt"
cmp "$evidence/before/app-pid.txt" "$evidence/app-pid.txt"
adb logcat -d -t 500 > "$evidence/logcat.txt"
adb shell am force-stop "$package"
if adb shell pidof "$package"; then
  echo "App is still live; cannot take a quiescent database snapshot" >&2
  exit 1
fi
adb exec-out run-as "$package" tar -cf - databases > "$evidence/pending-database.tar"
python3 scripts/verify-offline-mutation-evidence.py "$evidence"

# Extra tests supplement the unchanged five-test Bead 003 runner and validator.
mapfile -t test_apks < <(find instrumentation-apk -type f -name '*.apk')
test "${#test_apks[@]}" -eq 1
timeout 180 adb shell am instrument -w -r \
  -e class com.safal207.androidreliabilitylab.data.OfflineMutationTest \
  com.safal207.androidreliabilitylab.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$evidence/mutation-instrumentation.txt"
python3 scripts/verify-mutation-instrumentation.py "$evidence" --test-apk "${test_apks[0]}"
