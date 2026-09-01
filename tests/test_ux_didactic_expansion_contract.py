#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


manifest = read("app/src/main/AndroidManifest.xml")
service = read("app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt")
runtime = read("app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt")
api = read("app/src/main/assets/ui/core/native-api.js")
learning = read("app/src/main/assets/ui/screens/learning.js")
learning_model = read("app/src/main/assets/ui/core/learning-model.js")
physical_grid = read("app/src/main/assets/ui/components/physical-grid.js")
app = read("app/src/main/assets/ui/app.js")
styles = read("app/src/main/assets/ui/styles.css")
refine_styles = read("app/src/main/assets/ui/styles-refine.css")
obd = read("app/src/main/assets/ui/screens/obd.js")
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
assert "learningToleranceSettings" in api
assert "setLearningToleranceControls" in api
assert "resetLearningToleranceSettings" in api
assert "learningDecision" in learning
assert "reason_code" in learning
assert "frame_count" in api
assert 'data-learning-inspector="tolerances"' not in learning
assert "LIMITES CONFIGURADOS" not in learning
assert "COLETA AUTOMÁTICA" in learning
assert "Gasolina esperada" in learning
assert "GNV observado" in learning
assert "Diferença aprendida" in learning
assert "Situação" in learning
assert "Detalhes técnicos" in learning
assert "<dt>Onde</dt>" in learning
assert "<dt>Por que confiar</dt>" in learning
assert "comparisonTargetMs" in learning
assert "comparisonObservedMs" in learning
assert "source.petrolMs" in learning
assert "mapBar" in learning_model
assert "petrolMs" in learning_model
assert "rpm" in learning_model
assert "setInterval" not in learning
assert "writeMap" not in learning
assert "writeCurve" not in learning

# Na multimídia fraca, a posição viva é apenas texto. A interpolação bilinear
# continua no Kotlin/telemetria, mas a WebView não persegue pesos/células no DOM.
assert "setTrace(" not in physical_grid
assert "TRACE_MAX_CONTRIBUTORS" not in physical_grid
assert "TRACE_WEIGHT_STEPS" not in physical_grid
assert "live-contributor" not in physical_grid
assert "live-nearest" not in physical_grid
assert "learning.grid.setTrace" not in app
assert "continuousWeights" not in learning
assert "function renderLightLiveContext" in app
assert "learningLiveLabel" in app
assert "célula ${row + 1}×${column + 1}" in app
assert "A posição ao vivo é apenas informativa" in learning
assert "a interpolação continua no núcleo Kotlin" in learning
assert "physical-grid-with-axes" in physical_grid
assert "setAxes(rpmBins, petrolBins)" in physical_grid
assert ".cell-value{font-size:12px" in styles
assert ".cell-subvalue" in styles
assert ".physical-grid-with-axes" in refine_styles

# Tocar no mapa aprendido pode abrir a mesma autoridade do Mapa K, sem escrita.
assert "data-edit-learning-cell" in learning
assert "this.router.navigate('map'" in learning
assert "origin: 'learning'" in learning
assert "Abrir o editor não escreve na ECU" in learning

# OBD continua observacional e usa a mesma autoridade temporal da aplicação.
assert "obdDevices" in api
assert "listObdDevices" in api
assert "connectObd" in obd
assert "obdDevices" in obd
assert "data-obd-connect" in obd
assert "setInterval" not in obd
assert "writeMap" not in obd
assert "writeCurve" not in obd

assert scheduler.count("setInterval") == 1

print("UX_DIDACTIC_EXPANSION_CONTRACT=PASS")