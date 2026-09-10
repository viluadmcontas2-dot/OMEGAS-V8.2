from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = (ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
SETTINGS = (ROOT / "app/src/main/java/com/omegas/prohub/settings/AppSettings.kt").read_text(encoding="utf-8")
UI = (ROOT / "app/src/main/assets/ui/screens/obd.js").read_text(encoding="utf-8")

for token in ['readPidTimed(sock, "010C", 0x0C)', 'readPidTimed(sock, "010B", 0x0B)', 'readPidTimed(sock, "0106", 0x06)', "ObdPidCycle.decode"]:
    assert token in MANAGER, f"independent OBD acquisition missing: {token}"
assert "dataReady" in MANAGER
assert "gnvModeDeclared" in MANAGER
assert "obdGnvLearningEnabled" in SETTINGS
assert "ObdIndependentLearningEngine" in SERVICE
learning_start = SERVICE.index("private fun consumeObdLearningSample")
learning_end = SERVICE.index("private fun persistObdLearning", learning_start)
learning_section = SERVICE[learning_start:learning_end]
assert "telemetryStore.nearestFrame" not in learning_section, "OBD learning must not pair with MP48"
assert "petrol_ms" not in learning_section
assert "OBD independente" in UI
assert "MP48 + STFT" not in UI
print("BLUE_OBD_INDEPENDENT_RUNTIME_CONTRACT=PASS")
