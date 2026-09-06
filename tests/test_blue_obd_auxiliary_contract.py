from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

for rel in [
    "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt",
    "app/src/main/java/com/omegas/prohub/obd/ObdWitnessEngine.kt",
    "app/src/main/assets/ui/screens/obd.js",
]:
    assert (ROOT / rel).exists(), f"required optional OBD witness missing: {rel}"

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in ["android.hardware.bluetooth", "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"]:
    assert token in manifest, f"OBD Bluetooth surface missing: {token}"

index = (ROOT / "app/src/main/assets/ui/index.html").read_text(encoding="utf-8")
assert 'data-route="obd"' in index and 'data-screen="obd"' in index

bridge = (ROOT / "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt").read_text(encoding="utf-8")
for token in ["getObdStatus", "listObdDevices", "connectObd", "disconnectObd"]:
    assert token in bridge, f"OBD bridge capability missing: {token}"
for forbidden in ["getObdMaps", "setObdManualFuel"]:
    assert forbidden not in bridge

obd = (ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt").read_text(encoding="utf-8")
assert "STFT Bank 1" in obd and '"0106"' in obd
for forbidden in ["KWriteManager", "startKWrite(", "startKFactorWrite(", "ObdLearningGate", "ObdEvidenceLedger"]:
    assert forbidden not in obd

for rel in ["app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt", "app/src/main/java/com/omegas/prohub/learning/BlueEvidenceStore.kt"]:
    text = (ROOT / rel).read_text(encoding="utf-8").lower()
    assert "obd" not in text, f"Blue core gained an OBD dependency: {rel}"

print("BLUE_OBD_AUXILIARY_CONTRACT=PASS")
