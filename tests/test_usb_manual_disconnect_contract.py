from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")

assert "fun disconnectUsb()" in SERVICE
disconnect = SERVICE[SERVICE.index("fun disconnectUsb()"):SERVICE.index("fun usbDevicesJson()", SERVICE.index("fun disconnectUsb()"))]
assert "monitoringPausedByUser = true" in disconnect, (
    "desconexão explícita precisa marcar intenção humana de permanecer desconectado"
)

connect = SERVICE[SERVICE.index("fun connectUsb("):SERVICE.index("fun disconnectUsb()", SERVICE.index("fun connectUsb("))]
assert "monitoringPausedByUser = false" in connect, (
    "conexão explícita precisa rearmar monitoramento"
)

health = SERVICE[SERVICE.index("private fun healthTick()"):]
reconnect_line = next(
    line.strip() for line in health.splitlines()
    if "autoReconnectUsb" in line and "hasCompatibleDevice" in line
)
assert "!monitoringPausedByUser" in reconnect_line, (
    "healthTick não pode desfazer disconnectUsb() no mesmo ciclo; "
    f"condição atual: {reconnect_line}"
)

connected_transition = SERVICE[
    SERVICE.index("if (connected) {", SERVICE.index("private fun handleUsbTransition")):
    SERVICE.index("} else {", SERVICE.index("private fun handleUsbTransition"))
]
assert "monitoringPausedByUser = false" in connected_transition, (
    "nova conexão física confirmada deve limpar a pausa manual"
)

print("USB_MANUAL_DISCONNECT_CONTRACT=PASS")
