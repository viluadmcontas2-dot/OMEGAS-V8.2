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
  local klass="${3:-DashboardLevelsRenderTest}"
  set +e
  adb shell am instrument -w -r \
    -e class "com.omegas.prohub.${klass}#${method}" \
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

# Prova de queda do app: fase 1 grava e morre (SIGKILL), fase 2 roda em processo novo e recupera.
run_kill_case() {
  local scenario="$1"
  local klass="com.omegas.prohub.SessionKillRecoveryTest"
  local runner="com.omegas.v7.test.test/androidx.test.runner.AndroidJUnitRunner"
  set +e
  adb shell am instrument -w -r -e class "${klass}#faseUmGravaEMorre" "$runner" \
    > "rendered-evidence/${scenario}-fase1.txt" 2>&1
  cat "rendered-evidence/${scenario}-fase1.txt"
  sleep 3
  adb shell am instrument -w -r -e class "${klass}#faseDoisRecupera" "$runner" \
    > "rendered-evidence/${scenario}-fase2.txt" 2>&1
  local rc=$?
  set -e
  cat "rendered-evidence/${scenario}-fase2.txt"
  adb pull /sdcard/Android/data/com.omegas.v7.test/files/omegas-evidence/. rendered-evidence/ || true
  if grep -q 'OK (1 test)' "rendered-evidence/${scenario}-fase1.txt"; then
    echo "A fase 1 terminou sem a queda: a prova não vale."
    echo "SCENARIO_RESULT=${scenario}:FAIL"
    overall=1
  elif [ "$rc" -ne 0 ] || ! grep -q 'OK (1 test)' "rendered-evidence/${scenario}-fase2.txt"; then
    echo "SCENARIO_RESULT=${scenario}:FAIL"
    overall=1
  else
    echo "SCENARIO_RESULT=${scenario}:PASS"
  fi
}

set -e
if [ "$#" -ge 2 ]; then
  if [ "$2" = "killProof" ]; then
    run_kill_case "$1"
  else
    run_case "$1" "$2" "${3:-DashboardLevelsRenderTest}"
  fi
  exit "$overall"
fi

run_case "dashboard-fresh" "dashboardFreshLevelsRaw"
run_case "dashboard-invalid" "dashboardInvalidLevelsPlaceholder"
run_case "autocal-fresh-control" "autocalFreshTelemetryControl"
run_case "autocal-reference-curves" "autocalReferenceCurvesRenderFixture"
run_case "autocal-equivalence-shifted" "autocalShiftedEquivalenceRendersHorizontalProjection"
run_case "learning-fresh-context" "learningFreshMp48ContextRender"
run_case "map-fresh-context" "mapFreshMp48ContextRender"
run_case "curve-offline-honest" "curveOfflineDoesNotFabricateEcuRead"
run_case "obd-removed" "obdRouteWasRemoved"
run_case "dashboard-session-invalidated" "sessionChangeInvalidatesOldTelemetry"
run_case "dashboard-session-recovered" "sessionReconnectRecoversFreshTelemetry"
run_case "refino-ecu-automatico" "refinoEcuNoAutomatico" RefinoRenderTest
run_case "refino-coletando" "refinoColetando" RefinoRenderTest
run_case "refino-curva-pronta" "refinoCurvaPronta" RefinoRenderTest
run_case "refino-app-novo-ecu-pronta" "refinoAppNovoEcuPronta" RefinoRenderTest
run_case "ferramentas-balao-prompt" "ferramentasEBalaoFlutuante" RefinoRenderTest
run_case "refino-verificando" "refinoVerificando" RefinoRenderTest
run_case "refino-estavel" "refinoEstavel" RefinoRenderTest
run_case "refino-restaurar-trecho" "refinoRestaurarTrecho" RefinoRenderTest
run_case "refino-apagoes" "refinoApagoes" RefinoRenderTest
run_case "refino-agora-acompanha" "refinoAgoraAcompanhaATelemetria" RefinoRenderTest
run_case "refino-latencia-da-ponte" "refinoLatenciaDaPonte" RefinoRenderTest
run_kill_case "session-kill-recovery"
exit "$overall"
