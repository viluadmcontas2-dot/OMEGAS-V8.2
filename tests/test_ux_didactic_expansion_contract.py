#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


manifest = read("app/src/main/AndroidManifest.xml")
service = read("app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt")
runtime = read("app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt")
api = read("app/src/main/assets/ui/core/native-api.js")
index = read("app/src/main/assets/ui/index.html")
app = read("app/src/main/assets/ui/app.js")
styles = read("app/src/main/assets/ui/styles.css")
refine_styles = read("app/src/main/assets/ui/styles-refine.css")
scheduler = read("app/src/main/assets/ui/core/scheduler.js")

# Segundo plano nativo permanece intacto.
assert "FOREGROUND_SERVICE_DATA_SYNC" not in manifest
assert "dataSync" not in manifest
assert "connectedDevice" in manifest
assert "android.permission.CHANGE_NETWORK_STATE" in manifest
assert "FOREGROUND_SERVICE_TYPE_DATA_SYNC" not in service
assert "FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE" in service
assert "START_STICKY" in service
assert 'android:stopWithTask="false"' in manifest

# Aprendizado explicável continua vindo do Kotlin; a UI não inventa equivalência.
assert "sample_reason" in runtime
assert "learningDecision" in api
assert "this.fullSnapshot()" in api
# Tolerâncias do cérebro 2 saíram da ponte JS na F3 (vivem só nas SharedPreferences do Kotlin).
assert "learningToleranceSettings" not in api
assert "setLearningToleranceControls" not in api
assert "function renderLightLiveContext" in app

# Tocar no mapa aprendido pode abrir a mesma autoridade do Mapa K, sem escrita.

# OBD removido do produto (decisão do dono): a API não expõe mais OBD.
assert "connectObd" not in api
assert "listObdDevices" not in api

assert scheduler.count("setInterval") == 1

print("UX_DIDACTIC_EXPANSION_CONTRACT=PASS")
