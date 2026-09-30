#!/usr/bin/env bash
set -euo pipefail

# Runtime proof is produced on the API 35 CI emulator, without Android Studio.
# External browser intents are intercepted in instrumentation; no website claim.
evidence=${EVIDENCE_DIRECTORY:-robys-runtime-evidence}
package=com.robys.coffeehouse
mkdir -p "$evidence"
finish() {
  local result=$?
  if [[ "$result" -ne 0 ]]; then
    adb logcat -d -t 1000 > "$evidence/failure-logcat.txt" 2>&1 || true
    adb exec-out screencap -p > "$evidence/failure-screen.png" || true
    adb shell dumpsys activity activities > "$evidence/failure-activity.txt" 2>&1 || true
  fi
  exit "$result"
}
trap finish EXIT

mapfile -t apks < <(find robys-runtime-apk -type f -name '*.apk')
mapfile -t test_apks < <(find robys-instrumentation-apk -type f -name '*.apk')
test "${#apks[@]}" -eq 1
test "${#test_apks[@]}" -eq 1
apk=${apks[0]}
test_apk=${test_apks[0]}

python3 - "$apk" "$test_apk" "$evidence" <<'PY'
import hashlib, json, shutil, subprocess, sys
from pathlib import Path
apk, test_apk, evidence = map(Path, sys.argv[1:])
receipt = json.loads(Path('robys-build-evidence/build-receipt.json').read_text())
sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
assert receipt['proof_commit_sha'] == sha, 'APK source differs from runtime checkout'
assert receipt['apk_sha256'] == hashlib.sha256(apk.read_bytes()).hexdigest(), 'App APK hash differs'
assert receipt['test_apk_sha256'] == hashlib.sha256(test_apk.read_bytes()).hexdigest(), 'Test APK hash differs'
shutil.copyfile('robys-build-evidence/build-receipt.json', evidence / 'build-receipt.json')
PY

adb install -r "$apk" | tee "$evidence/install.txt"
adb install -r "$test_apk" | tee "$evidence/instrumentation-install.txt"
adb shell pm clear "$package" | tee "$evidence/clear-data.txt"
python3 - "$evidence/clear-data.txt" <<'PY'
import sys
from pathlib import Path
assert Path(sys.argv[1]).read_text().strip() == 'Success', 'App data was not cleared'
PY
adb logcat -c
adb shell am force-stop "$package"
if adb shell pidof "$package"; then
  echo 'App remained live after force-stop; cannot prove cold launch' >&2
  exit 1
fi
adb shell am start -W -n "$package/.MainActivity" | tee "$evidence/cold-launch.txt"
python3 - "$evidence/cold-launch.txt" <<'PY'
import sys
from pathlib import Path
output = Path(sys.argv[1]).read_text()
assert 'Status: ok' in output, f'Activity did not start: {output}'
PY
adb shell getprop ro.build.version.sdk | tr -d '\r' > "$evidence/api-level.txt"
adb shell getprop ro.build.fingerprint > "$evidence/build-fingerprint.txt"
test "$(cat "$evidence/api-level.txt")" = 35

# Wait for rendered content rather than treating process start as UI success.
for attempt in {1..30}; do
  if adb shell uiautomator dump /sdcard/robys-window.xml \
      && adb pull /sdcard/robys-window.xml "$evidence/cold-window.xml" \
      && python3 - "$evidence/cold-window.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes = list(ET.parse(sys.argv[1]).iter('node'))
labels = {node.get('text', '') for node in nodes} | {node.get('content-desc', '') for node in nodes}
assert {'Ana Sayfa', 'Keşfet', 'Ziyaret', 'İyi kahve.'} <= labels, labels
assert any(node.get('package') == 'com.robys.coffeehouse' for node in nodes)
PY
  then
    break
  fi
  sleep 1
done
python3 - "$evidence/cold-window.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes = list(ET.parse(sys.argv[1]).iter('node'))
labels = {node.get('text', '') for node in nodes} | {node.get('content-desc', '') for node in nodes}
assert {'Ana Sayfa', 'Keşfet', 'Ziyaret', 'İyi kahve.'} <= labels, labels
assert any(node.get('package') == 'com.robys.coffeehouse' for node in nodes)
PY
adb shell pidof "$package" | tr -d '\r' > "$evidence/cold-app-pid.txt"
test -s "$evidence/cold-app-pid.txt"
adb exec-out screencap -p > "$evidence/cold-home.png"
test -s "$evidence/cold-home.png"

# Instrumentation verifies UI semantics, tabs, state, and intercepted intents.
# Its complete per-test result is checked below; adb can return zero on failures.
timeout 240 adb shell am instrument -w -r \
  -e class com.robys.coffeehouse.RobysNavigationTest \
  "$package.test/androidx.test.runner.AndroidJUnitRunner" \
  | tee "$evidence/instrumentation.txt"

for screen in home discover visit visit-landscape-last-action; do
  adb pull "/sdcard/Android/data/$package/files/runtime-evidence/$screen.png" "$evidence/$screen.png"
  test -s "$evidence/$screen.png"
done
adb logcat -d -t 1000 > "$evidence/logcat.txt"

python3 - "$evidence" <<'PY'
import hashlib, json, re, subprocess, sys
from collections import Counter
from pathlib import Path

evidence = Path(sys.argv[1])
output = (evidence / 'instrumentation.txt').read_text()
expected = {
    # Keep these explicit so a silently removed test cannot pass the runtime gate.
    'tabsAndHomeActionNavigateBetweenNativeScreens',
    'categorySelectionFiltersSamples',
    'selectedTabAndCategorySurviveActivityRecreation',
    'visitLastActionRemainsReachableInLandscape',
    'discoverActionOpensExactExternalUrl',
}
completed, status = [], {}
for line in output.splitlines():
    if line.startswith('INSTRUMENTATION_STATUS: '):
        key, value = line.removeprefix('INSTRUMENTATION_STATUS: ').split('=', 1)
        status[key] = value
    elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
        code = int(line.removeprefix('INSTRUMENTATION_STATUS_CODE: '))
        assert code in (0, 1), f'Failed/skipped/ignored test: {code} {status}'
        if code == 0:
            assert status.get('class') == 'com.robys.coffeehouse.RobysNavigationTest', status
            completed.append(status.get('test'))
        status = {}
assert Counter(completed) == Counter(expected), f'Missing/extra/duplicate tests: {completed}'
assert re.search(r'^OK \(5 tests\)\s*$', output, re.MULTILINE), 'No successful five-test summary'
assert re.search(r'^INSTRUMENTATION_CODE: -1\s*$', output, re.MULTILINE), 'Runner did not complete'
for name in ('cold-home.png', 'home.png', 'discover.png', 'visit.png', 'visit-landscape-last-action.png'):
    assert (evidence / name).read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), f'Invalid screenshot: {name}'
receipt = {
    'proof_commit_sha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
    'build': json.loads((evidence / 'build-receipt.json').read_text()),
    'api_level': int((evidence / 'api-level.txt').read_text()),
    'passed': sorted(completed), 'failed': [], 'skipped': [],
    'external_discover_url': 'https://safal207.github.io/robys-coffee-house-demo/discover.html',
    'file_sha256': {
        path.name: hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(evidence.iterdir()) if path.is_file() and path.name != 'runtime-receipt.json'
    },
    'claim_ceiling': 'API 35 cold launch, native three-tab navigation, category filtering, recreation, small landscape reachability, and intercepted browser Intent configuration. No external website, live prices, order processing, or production release claim.',
}
(evidence / 'runtime-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt, indent=2))
PY
