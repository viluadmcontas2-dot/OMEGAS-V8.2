#!/usr/bin/env bash
set -u
chmod +x gradlew
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace || exit 2

adb shell wm size 1280x720 || exit 2
adb shell wm density 160 || exit 2
adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 2
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || exit 2
adb shell pm grant com.omegas.v7.test android.permission.POST_NOTIFICATIONS || true
adb shell dumpsys deviceidle whitelist +com.omegas.v7.test || true

mkdir -p rendered-evidence
overall=0

run_case() {
  local scenario="$1"
  local method="$2"
  set +e
  adb shell am instrument -w -r \
    -e class "com.omegas.prohub.DashboardLevelsRenderTest#${method}" \
    com.omegas.v7.test.test/androidx.test.runner.AndroidJUnitRunner \
    > "rendered-evidence/${scenario}-instrumentation.txt" 2>&1
  local rc=$?
  set -e
  cat "rendered-evidence/${scenario}-instrumentation.txt"
  adb pull /sdcard/Android/data/com.omegas.v7.test/files/omegas-evidence/. rendered-evidence/ || true
  adb exec-out screencap -p > "rendered-evidence/${scenario}-post.png" || true
  if [ "$rc" -ne 0 ] ||
     grep -q 'FAILURES!!!' "rendered-evidence/${scenario}-instrumentation.txt" ||
     grep -q 'INSTRUMENTATION_STATUS_CODE: -2' "rendered-evidence/${scenario}-instrumentation.txt" ||
     ! grep -q 'OK (1 test)' "rendered-evidence/${scenario}-instrumentation.txt"; then
    echo "SCENARIO_RESULT=${scenario}:FAIL"
    overall=1
  else
    echo "SCENARIO_RESULT=${scenario}:PASS"
  fi
}

set -e
run_case "dashboard-fresh" "dashboardFreshLevelsRaw"
run_case "dashboard-invalid" "dashboardInvalidLevelsPlaceholder"
run_case "autocal-fresh-control" "autocalFreshTelemetryControl"
run_case "autocal-reference-curves" "autocalReferenceCurvesRenderFixture"
run_case "autocal-equivalence-shifted" "autocalShiftedEquivalenceRendersHorizontalProjection"
run_case "learning-fresh-context" "learningFreshMp48ContextRender"
run_case "map-fresh-context" "mapFreshMp48ContextRender"
run_case "curve-offline-honest" "curveOfflineDoesNotFabricateEcuRead"
run_case "obd-offline-honest" "obdOfflineIsHonest"
run_case "dashboard-session-invalidated" "sessionChangeInvalidatesOldTelemetry"
exit "$overall"
