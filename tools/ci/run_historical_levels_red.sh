#!/usr/bin/env bash
set -euo pipefail
chmod +x gradlew
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
adb shell wm size 1280x720
adb shell wm density 160
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p historical-levels-evidence
set +e
adb shell am instrument -w -r \
  -e class 'com.omegas.prohub.DashboardLevelsRenderTest#historicalPreLevelsDashboardOmitsRawLevels' \
  com.omegas.v7.test.test/androidx.test.runner.AndroidJUnitRunner \
  > historical-levels-evidence/instrumentation.txt 2>&1
rc=$?
set -e
cat historical-levels-evidence/instrumentation.txt
adb pull /sdcard/Android/data/com.omegas.v7.test/files/omegas-evidence/. historical-levels-evidence/ || true
adb exec-out screencap -p > historical-levels-evidence/post.png || true
if [ "$rc" -ne 0 ] ||
   grep -q 'FAILURES!!!' historical-levels-evidence/instrumentation.txt ||
   grep -q 'INSTRUMENTATION_STATUS_CODE: -2' historical-levels-evidence/instrumentation.txt ||
   ! grep -q 'OK (1 test)' historical-levels-evidence/instrumentation.txt; then
  echo "HISTORICAL_LEVELS_RED_HARNESS=FAIL"
  exit 1
fi
echo "HISTORICAL_LEVELS_RED_HARNESS=PASS"
