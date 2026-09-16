#!/usr/bin/env bash
set -euo pipefail

evidence=runtime-evidence
mkdir -p "$evidence"
python3 scripts/incident-fixture.py --requests "$evidence/fixture-requests.jsonl" \
  > "$evidence/fixture-server.log" 2>&1 &
fixture_pid=$!
finish() {
  local result=$?
  if [[ "$result" -ne 0 ]]; then
    # Preserve failure evidence without replacing the original nonzero result.
    adb logcat -d -t 1000 > "$evidence/failure-logcat.txt" 2>&1 || true
    adb exec-out screencap -p > "$evidence/failure-screen.png" || true
    adb shell dumpsys connectivity > "$evidence/failure-connectivity.txt" 2>&1 || true
  fi
  kill "$fixture_pid" 2>/dev/null || true
  exit "$result"
}
trap finish EXIT
for attempt in {1..30}; do
  kill -0 "$fixture_pid"
  if curl --fail --silent --max-time 1 http://127.0.0.1:8765/health > /dev/null; then
    break
  fi
  sleep 1
done
curl --fail --silent --max-time 1 http://127.0.0.1:8765/health > "$evidence/fixture-health.json"

# Boot completion does not establish the guest-to-host network boundary.
# Probe only /health, before launch: never issue or retry the app's /incidents.
for attempt in {1..30}; do
  if adb shell 'printf "GET /health HTTP/1.0\r\nHost: 10.0.2.2\r\n\r\n" | toybox nc -w 2 10.0.2.2 8765' \
      > "$evidence/emulator-fixture-health.txt" 2>&1 && \
      grep -q '200 OK' "$evidence/emulator-fixture-health.txt" && \
      grep -q '"ready":true' "$evidence/emulator-fixture-health.txt"; then
    break
  fi
  sleep 1
done
grep -q '200 OK' "$evidence/emulator-fixture-health.txt"
grep -q '"ready":true' "$evidence/emulator-fixture-health.txt"

mapfile -t apks < <(find runtime-apk -type f -name '*.apk')
test "${#apks[@]}" -eq 1
apk="${apks[0]}"
adb install -r "$apk" | tee "$evidence/install.txt"
adb shell am force-stop com.safal207.androidreliabilitylab
adb shell am start -W -n com.safal207.androidreliabilitylab/.MainActivity | tee "$evidence/launch.txt"
adb shell getprop ro.build.version.sdk | tr -d '\r' > "$evidence/api-level.txt"
adb shell getprop ro.build.fingerprint > "$evidence/build-fingerprint.txt"

# Bounded wait for the actual HTTP content, never a fixed sleep as proof.
for attempt in {1..30}; do
  if adb shell uiautomator dump /sdcard/window.xml && \
     adb pull /sdcard/window.xml "$evidence/window.xml" && \
     python3 scripts/verify-http-evidence.py "$evidence" --ui-only; then
    break
  fi
  sleep 1
done
python3 scripts/verify-http-evidence.py "$evidence" --ui-only
adb shell pidof com.safal207.androidreliabilitylab | tr -d '\r' > "$evidence/app-pid.txt"
test -s "$evidence/app-pid.txt"
adb exec-out screencap -p > "$evidence/incident-list.png"
test -s "$evidence/incident-list.png"
adb logcat -d -t 500 > "$evidence/logcat.txt"
python3 scripts/verify-http-evidence.py "$evidence" --apk "$apk"
