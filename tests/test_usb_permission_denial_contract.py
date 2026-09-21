from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
USB = (ROOT / "app/src/main/java/com/omegas/prohub/usb/UsbSerialManager.kt").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt").read_text(encoding="utf-8")

assert '@Volatile var permissionDeniedDeviceName = ""' in USB, (
    "negação precisa sobreviver ao health tick dentro da geração física atual"
)
assert 'allowPermissionRetry: Boolean = false' in USB, (
    "retry automático e retry explicitamente solicitado pelo usuário precisam ser distintos"
)

permission_branch = USB.index('if (!usbManager.hasPermission(device))')
pending_guard = USB.index('if (permissionPending && activeDeviceName == device.deviceName)', permission_branch)
denied_guard = USB.index(
    'if (permissionDeniedDeviceName == device.deviceName && !allowPermissionRetry)',
    permission_branch,
)
request = USB.index('usbManager.requestPermission(device, permissionIntent)', permission_branch)
assert pending_guard < denied_guard < request, (
    "pedido pendente e negação anterior precisam bloquear requestPermission antes do novo prompt"
)

assert 'permissionDeniedDeviceName = deniedDeviceName' in USB, (
    "callback de negação precisa registrar o dispositivo negado"
)
assert 'if (permissionDeniedDeviceName == attached.deviceName) permissionDeniedDeviceName = ""' in USB, (
    "novo attach físico deve rearmar uma única solicitação automática"
)
assert 'permissionDeniedDeviceName = ""' in USB[USB.index('private fun open(device: UsbDevice)'):], (
    "open confirmado precisa limpar a negação anterior"
)

assert 'userInitiated: Boolean = false' in SERVICE
assert 'usb.connect(deviceName, allowPermissionRetry = userInitiated)' in SERVICE
assert 'if (usb.connected) disconnectUsb() else connectUsb(userInitiated = true)' in SERVICE, (
    "ação explícita de conexão deve poder rearmar a permissão"
)

health = SERVICE.split('private fun healthTick()', 1)[1]
assert 'connectUsb()' in health, "health tick deve continuar usando retry automático não interativo"
assert 'connectUsb(userInitiated = true)' not in health, (
    "health tick nunca pode reapresentar prompt depois de uma negação"
)

assert 'connectUsb(deviceName.ifBlank { null }, userInitiated = true)' in BRIDGE, (
    "botão da WebView é ação humana explícita e pode tentar novamente"
)

print("USB_PERMISSION_DENIAL_CONTRACT=PASS")
