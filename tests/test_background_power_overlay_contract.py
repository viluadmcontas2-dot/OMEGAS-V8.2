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
tools = read("app/src/main/assets/ui/components/drawers.js")  # bateria/flutuante moraram no OBD; agora em Ferramentas
html = read("app/src/main/assets/ui/index.html")

# Bateria: pedido explícito pelo fluxo oficial do Android, com prompt automático
# único e botão manual reaproveitando a mesma ação.
assert "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in manifest
assert "Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in activity
assert "isIgnoringBatteryOptimizations" in activity
assert "battery_optimization_prompted_v1" in activity
assert "maybePromptBatteryOptimization" in activity
assert "requestBatteryOptimizationExemption" in api
assert "data-tool-battery-request" in tools

# Overlay: permissão especial oficial, opcional e sempre sob decisão do usuário.
assert "android.permission.SYSTEM_ALERT_WINDOW" in manifest
assert "Settings.ACTION_MANAGE_OVERLAY_PERMISSION" in power_bridge
assert "Settings.canDrawOverlays" in power_bridge
assert "requestOverlayPermissionAndEnable" in api
assert "setTelemetryOverlayEnabled" in api
assert "data-tool-overlay-request" in tools
assert "data-tool-overlay-enable" in tools
assert "data-tool-overlay-disable" in tools

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
assert "@Volatile private var closed = false" in overlay
assert "private val showEpoch = AtomicLong(0L)" in overlay
assert "if (closed || appForeground || root != null || showPending || !permissionGranted()) return" in overlay
# O balão nunca cobre o próprio OMEGAS (cobriu o botão Gravar na ECU no carro).
assert "fun setAppForeground(foreground: Boolean)" in overlay
assert "setAppForeground(true)" in activity and "setAppForeground(false)" in activity
assert "epoch != showEpoch.get()" in overlay
assert "showEpoch.incrementAndGet()" in overlay
assert "closed = true" in overlay
assert "overlayWindowType" in overlay
assert "TYPE_APPLICATION_OVERLAY" in overlay

# OBD removido do produto: nenhuma tela/rota OBD; energia e flutuante ficam em Ferramentas.
assert 'data-route="obd"' not in html
assert 'data-screen="obd"' not in html
for forbidden in ["writeMap", "writeCurve", "startKWrite", "startKFactorWrite"]:
    assert forbidden not in tools
