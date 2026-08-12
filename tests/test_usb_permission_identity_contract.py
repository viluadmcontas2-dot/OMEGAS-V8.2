#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
filter_path = ROOT / "app/src/main/res/xml/device_filter.xml"
usb_path = ROOT / "app/src/main/java/com/omegas/prohub/usb/UsbSerialManager.kt"
identity_path = ROOT / "app/src/main/java/com/omegas/prohub/usb/OmegasUsbIdentity.kt"
recovery_path = ROOT / "app/src/main/java/com/omegas/prohub/usb/UsbRecoveryPolicy.kt"

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
assert "beginTransientRecovery" in usb and "openRecoveredPort" in usb
assert "USB em recuperação transitória" in usb
recovery = usb.split("private fun beginTransientRecovery", 1)[1].split("private fun scheduleRecoveryAttempt", 1)[0]
assert "closePortOnly()" in recovery
assert "connected = false" not in recovery, "falha transitória nao deve derrubar sessao logica imediatamente"
assert "ACTION_USB_DEVICE_DETACHED" in usb and "hardDisconnect" in usb

policy = recovery_path.read_text(encoding="utf-8")
assert "250L" in policy and "750L" in policy and "1_500L" in policy

print("USB_PERMISSION_IDENTITY_CONTRACT=PASS")
