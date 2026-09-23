#!/usr/bin/env bash
set -u

chmod +x gradlew
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace || exit 2

adb shell wm size 1280x720 || exit 2
adb shell wm density 160 || exit 2

adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 2
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || exit 2
adb shell pm grant com.omegas.v7.test.amarelo android.permission.POST_NOTIFICATIONS || true
adb shell dumpsys deviceidle whitelist +com.omegas.v7.test.amarelo || true

mkdir -p rendered-evidence
set +e
adb shell am instrument -w -r   -e class "com.omegas.prohub.AmareloAutoCalRenderTest#autocalOriginalDerivedAdaptiveRender"   com.omegas.v7.test.amarelo.test/androidx.test.runner.AndroidJUnitRunner   > rendered-evidence/amarelo-autocal-original-derived-instrumentation.txt 2>&1
rc=$?
set -e

cat rendered-evidence/amarelo-autocal-original-derived-instrumentation.txt
adb pull /sdcard/Android/data/com.omegas.v7.test.amarelo/files/omegas-evidence/. rendered-evidence/ || true
adb exec-out screencap -p > rendered-evidence/amarelo-autocal-original-derived-post.png || true

if [ "$rc" -ne 0 ] ||
   grep -q 'FAILURES!!!' rendered-evidence/amarelo-autocal-original-derived-instrumentation.txt ||
   grep -q 'INSTRUMENTATION_STATUS_CODE: -2' rendered-evidence/amarelo-autocal-original-derived-instrumentation.txt ||
   ! grep -q 'OK (1 test)' rendered-evidence/amarelo-autocal-original-derived-instrumentation.txt; then
  echo "AMARELO_AUTOCAL_RENDER=FAIL"
  exit 1
fi

test -s rendered-evidence/amarelo-autocal-original-derived.json || {
  echo "Missing DOM/source receipt"
  exit 1
}
test -s rendered-evidence/amarelo-autocal-original-derived.png || {
  echo "Missing rendered screenshot"
  exit 1
}

set +e
adb shell am instrument -w -r \
  -e class "com.omegas.prohub.AmareloAutoCalRuntimeBridgeTest#canonicalReplayFlowsThroughRuntimeBridgeAndWebView" \
  com.omegas.v7.test.amarelo.test/androidx.test.runner.AndroidJUnitRunner \
  > rendered-evidence/amarelo-autocal-runtime-bridge-instrumentation.txt 2>&1
runtime_rc=$?
set -e

cat rendered-evidence/amarelo-autocal-runtime-bridge-instrumentation.txt
adb pull /sdcard/Android/data/com.omegas.v7.test.amarelo/files/omegas-evidence/. rendered-evidence/ || true

if [ "$runtime_rc" -ne 0 ] ||
   grep -q 'FAILURES!!!' rendered-evidence/amarelo-autocal-runtime-bridge-instrumentation.txt ||
   grep -q 'INSTRUMENTATION_STATUS_CODE: -2' rendered-evidence/amarelo-autocal-runtime-bridge-instrumentation.txt ||
   ! grep -q 'OK (1 test)' rendered-evidence/amarelo-autocal-runtime-bridge-instrumentation.txt; then
  echo "AMARELO_AUTOCAL_RUNTIME_BRIDGE=FAIL"
  exit 1
fi

test -s rendered-evidence/amarelo-autocal-canonical-replay-runtime-bridge.json || {
  echo "Missing canonical replay runtime/bridge receipt"
  exit 1
}
test -s rendered-evidence/amarelo-autocal-canonical-replay-runtime-bridge.png || {
  echo "Missing canonical replay runtime/bridge screenshot"
  exit 1
}
python3 - <<'PY'
import json
from pathlib import Path
receipt = json.loads(Path("rendered-evidence/amarelo-autocal-canonical-replay-runtime-bridge.json").read_text())
assert receipt["classification"] == "CANONICAL_REPLAY_RUNTIME_BRIDGE_WEBVIEW"
assert receipt["replayProvenance"]["sourceRawSha256"] == "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64"
dom = receipt["bridgeDom"]
assert dom["bridgeAvailable"] is True
assert dom["projectionOk"] is True
assert dom["projectionSource"] == "NATIVE_MONITOR"
assert dom["nativeSnapshotSource"] == "ECU_READ"
print("AMARELO_AUTOCAL_RUNTIME_BRIDGE_RECEIPT=PASS")
PY

echo "AMARELO_AUTOCAL_RENDER=PASS"
