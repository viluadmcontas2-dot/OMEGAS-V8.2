from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/com/omegas/prohub/usb/UsbSerialManager.kt").read_text(encoding="utf-8")

guard = 'if (permissionPending && activeDeviceName == device.deviceName)'
request = 'usbManager.requestPermission(device, permissionIntent)'
permission_branch = SOURCE.index('if (!usbManager.hasPermission(device))')
guard_at = SOURCE.index(guard, permission_branch)
request_at = SOURCE.index(request, permission_branch)

assert permission_branch >= 0
assert guard_at > permission_branch
assert request_at > guard_at, "requestPermission precisa ficar depois do guard de pedido pendente"
assert 'Permissão OMEGAS já solicitada; aguardando resposta Android' in SOURCE
assert SOURCE.count(request) == 1, "deve existir uma única superfície de requestPermission"
disconnect_at = SOURCE.index('private fun disconnectInternal')
assert 'permissionPending = false' in SOURCE[disconnect_at:], "disconnect precisa limpar pending para uma nova geração"

print("USB_PERMISSION_PENDING_DEDUPE_CONTRACT=PASS")
