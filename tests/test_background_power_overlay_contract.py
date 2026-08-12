#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


manifest = read("app/src/main/AndroidManifest.xml")
activity = read("app/src/main/java/com/omegas/prohub/MainActivity.kt")
power_bridge = read("app/src/main/java/com/omegas/prohub/web/PowerJavascriptBridge.kt")
overlay = read("app/src/main/java/com/omegas/prohub/service/TelemetryOverlayController.kt")
service = read("app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt")
api = read("app/src/main/assets/ui/core/native-api.js")
obd = read("app/src/main/assets/ui/screens/obd.js")
html = read("app/src/main/assets/ui/index.html")

# Bateria: pedido explícito pelo fluxo oficial do Android, com prompt automático
# único e botão manual reaproveitando a mesma ação.
assert "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in manifest
assert "Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in activity
assert "isIgnoringBatteryOptimizations" in activity
assert "battery_optimization_prompted_v1" in activity
assert "maybePromptBatteryOptimization" in activity
assert "requestBatteryOptimizationExemption" in api
assert "data-obd-battery-request" in obd

# Overlay: permissão especial oficial, opcional e sempre sob decisão do usuário.
assert "android.permission.SYSTEM_ALERT_WINDOW" in manifest
assert "Settings.ACTION_MANAGE_OVERLAY_PERMISSION" in power_bridge
assert "Settings.canDrawOverlays" in power_bridge
assert "requestOverlayPermissionAndEnable" in api
assert "setTelemetryOverlayEnabled" in api
assert "data-obd-overlay-request" in obd
assert "data-obd-overlay-enable" in obd
assert "data-obd-overlay-disable" in obd

# O flutuante mostra somente os quatro campos aprovados e não possui writers.
for marker in ["CÉLULA", "STFT", "PETROL", "RPM"]:
    assert marker in overlay
for forbidden in [
    "KWriteManager", "KFactorManager", "startKWrite", "startKBatchWrite",
    "startKFactorWrite", "writeMap", "writeCurve", "UsbSerialManager",
]:
    assert forbidden not in overlay

# O overlay não cria scheduler/timer paralelo. Atualizações são empurradas pelo
# mesmo serviço, limitadas para renderização e criação duplicada é bloqueada.
assert "Executors" not in overlay
assert "Scheduled" not in overlay
assert "setInterval" not in overlay
assert "updateOverlay()" in service
assert "stateChanged()" in service
assert "250L" in overlay
assert "showPending" in overlay
assert "overlayWindowType" in overlay
assert "TYPE_APPLICATION_OVERLAY" in overlay

# A nova tela OBD mantém energia/conexão/PIDs na visão própria, sem reintroduzir
# a página longa antiga e sem writer.
for marker in [
    'data-obd-view="setup"', 'id="obdConnectionCenter"', 'id="obdPowerCard"',
    'id="obdSensorList"', 'data-obd-view="map"', 'id="obdIndependentMap"',
]:
    assert marker in html
assert "renderConnection(state)" in obd
assert "renderPower()" in obd
assert "renderSensors(obd)" in obd
assert "data-obd-cell-key" in obd
assert "setInterval" not in obd
for forbidden in ["writeMap", "writeCurve", "startKWrite", "startKFactorWrite"]:
    assert forbidden not in obd

print("BACKGROUND_POWER_OVERLAY_CONTRACT=PASS")
