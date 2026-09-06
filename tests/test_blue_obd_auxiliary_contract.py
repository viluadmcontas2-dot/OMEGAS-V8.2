from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

required_paths = [
    "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt",
    "app/src/main/java/com/omegas/prohub/obd/ObdLearningGate.kt",
    "app/src/main/assets/ui/screens/obd.js",
]
for rel in required_paths:
    assert (ROOT / rel).exists(), f"required optional OBD subsystem missing: {rel}"

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in [
    "android.hardware.bluetooth",
    "android.permission.BLUETOOTH_CONNECT",
    "android.permission.BLUETOOTH_SCAN",
]:
    assert token in manifest, f"OBD Bluetooth surface missing: {token}"

index = (ROOT / "app/src/main/assets/ui/index.html").read_text(encoding="utf-8")
assert 'data-route="obd"' in index
assert 'data-screen="obd"' in index
assert "STFT" in index and "LTFT" in index

bridge = (ROOT / "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt").read_text(encoding="utf-8")
for token in ["getObdStatus", "listObdDevices", "connectObd", "disconnectObd"]:
    assert token in bridge, f"OBD bridge capability missing: {token}"

obd = (ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt").read_text(encoding="utf-8")
assert "STFT é o sinal principal" in obd
assert "LTFT é contexto" in obd
assert "import com.omegas.prohub.calibration.KWriteManager" not in obd
assert "startKWrite(" not in obd
assert "startKFactorWrite(" not in obd

# Blue must remain able to operate without OBD. OBD is independent feedback,
# not correction authority or a prerequisite for the causal engine.
for rel in [
    "app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt",
    "app/src/main/java/com/omegas/prohub/learning/BlueEvidenceStore.kt",
]:
    text = (ROOT / rel).read_text(encoding="utf-8").lower()
    assert "obd" not in text, f"Blue core gained an OBD dependency: {rel}"

print("BLUE_OBD_AUXILIARY_CONTRACT=PASS")
