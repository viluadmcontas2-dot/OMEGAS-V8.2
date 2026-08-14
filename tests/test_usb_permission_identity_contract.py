#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
filter_path = ROOT / "app/src/main/res/xml/device_filter.xml"
usb_path = ROOT / "app/src/main/java/com/omegas/prohub/usb/UsbSerialManager.kt"
identity_path = ROOT / "app/src/main/java/com/omegas/prohub/usb/OmegasUsbIdentity.kt"
recovery_path = ROOT / "app/src/main/java/com/omegas/prohub/usb/UsbRecoveryPolicy.kt"
service_path = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"
transition_path = ROOT / "app/src/main/java/com/omegas/prohub/service/UsbSessionTransitionPolicy.kt"

root = ET.parse(filter_path).getroot()
devices = root.findall("usb-device")
assert len(devices) == 1, "attach automatico deve reconhecer somente a interface OMEGAS"
assert devices[0].attrib.get("vendor-id") == "4292", "VID esperado 0x10C4 / 4292"
assert devices[0].attrib.get("product-id") == "60000", "PID esperado 0xEA60 / 60000"

identity = identity_path.read_text(encoding="utf-8")
assert "0x10C4" in identity and "0xEA60" in identity

usb = usb_path.read_text(encoding="utf-8")
assert "OmegasUsbIdentity.matches" in usb, "selecao USB deve usar identidade OMEGAS"
assert "filter(::isOmegasDevice)" in usb, "candidatos devem ser filtrados antes da permissao"
assert "usbManager.requestPermission(device, permissionIntent)" in usb
request_block = usb.split("if (!usbManager.hasPermission(device))", 1)[0]
assert "filter(::isOmegasDevice)" in request_block, "requestPermission nao pode receber USB serial generico"
assert "UsbRecoveryPolicy.decide" in usb
assert "beginTransientRecovery" in usb
assert "ACTION_USB_DEVICE_DETACHED" in usb and "hardDisconnect" in usb
assert "connectionSessionId = sessionCounter.incrementAndGet()" in usb, "cada open fisico deve criar geracao nova"

policy = recovery_path.read_text(encoding="utf-8")
assert "UsbRecoveryAction.HARD_DISCONNECT" in policy
assert "RETRY_TRANSPORT" in policy, "enum legado pode permanecer durante a reconstrucao"
assert "esta política não o emite" in policy

transition = transition_path.read_text(encoding="utf-8")
assert "GENERATION_CHANGED" in transition
assert "sessionId != lastSessionId" in transition

service = service_path.read_text(encoding="utf-8")
assert 'runtime.endUsbSession("USB_SESSION_REPLACED")' in service
assert "nativeAutoCal.endUsbSession()" in service
assert 'telemetryStore.invalidate("USB_SESSION_REPLACED")' in service
assert "telemetryStore.beginSession(sessionId)" in service
assert "runtime.beginUsbSession(sessionId)" in service
assert "kWriter.beginUsbSession(sessionId)" in service
assert "kFactor.beginUsbSession(sessionId)" in service
assert "nativeAutoCal.beginUsbSession(sessionId)" in service

print("USB_PERMISSION_IDENTITY_CONTRACT=PASS")
