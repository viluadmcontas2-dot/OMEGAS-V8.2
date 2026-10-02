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
tools = read("app/src/main/assets/ui/components/drawers.js")
html = read("app/src/main/assets/ui/index.html")

# Bateria: pedido explícito pelo fluxo oficial do Android, com prompt automático
# único e botão manual reaproveitando a mesma ação.
assert "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in manifest
assert "Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in activity
assert "isIgnoringBatteryOptimizations" in activity
assert "battery_optimization_prompted_v1" in activity
assert "maybePromptBatteryOptimization" in activity
assert "requestBatteryOptimizationExemption" in api
assert "data-power-battery-request" in tools

# Overlay: permissão especial oficial, opcional e sempre sob decisão do usuário.
assert "android.permission.SYSTEM_ALERT_WINDOW" in manifest
assert "Settings.ACTION_MANAGE_OVERLAY_PERMISSION" in power_bridge
assert "Settings.canDrawOverlays" in power_bridge
assert "requestOverlayPermissionAndEnable" in api
assert "setTelemetryOverlayEnabled" in api

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

# Bateria e flutuante vivem em Ferramentas (a tela OBD foi removida na WU-006).
for marker in ["data-power-overlay-request", "data-power-overlay-enable", "data-power-overlay-disable"]:
    assert marker in tools
for forbidden in ["writeMap", "writeCurve", "startKWrite", "startKFactorWrite"]:
    assert forbidden not in tools

print("BACKGROUND_POWER_OVERLAY_CONTRACT=PASS")
