#!/usr/bin/env bash
set -euo pipefail

# Preserve every Bead 002 HTTP/UI/APK/process gate before inspecting persistence.
bash scripts/prove-http-runtime.sh

evidence=runtime-evidence
package=com.safal207.androidreliabilitylab
# Freeze writes before copying the database and WAL together. No relaunch is made,
# and this snapshot boundary is not a process-recovery test.
adb shell am force-stop "$package"
if adb shell pidof "$package"; then
  echo "App is still live; cannot take a quiescent database snapshot" >&2
  exit 1
fi
adb exec-out run-as "$package" tar -cf - databases > "$evidence/room-database.tar"
test -s "$evidence/room-database.tar"
python3 scripts/verify-room-evidence.py "$evidence"

mapfile -t test_apks < <(find instrumentation-apk -type f -name '*.apk')
test "${#test_apks[@]}" -eq 1
adb install -r "${test_apks[0]}" | tee "$evidence/instrumentation-install.txt"
timeout 180 adb shell am instrument -w -r \
  -e class com.safal207.androidreliabilitylab.data.RoomPersistenceTest \
  com.safal207.androidreliabilitylab.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$evidence/room-instrumentation.txt"
python3 scripts/verify-room-instrumentation.py "$evidence" --test-apk "${test_apks[0]}"
