from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
COORDINATOR = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt").read_text(encoding="utf-8")
ACCESS = (ROOT / "app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
CONFIDENCE = (ROOT / "app/src/main/java/com/omegas/prohub/blue/BlueWitnessConfidence.kt").read_text(encoding="utf-8")
OBD_DIR = ROOT / "app/src/main/java/com/omegas/prohub/obd"

assert "BlueWitnessConfidence.project(" in COORDINATOR
assert "syncObdWitness(this, coordinator)" in ACCESS
assert "consumeObdLearningSample(sample)" in SERVICE
assert "fun obdWitnessStatusJson()" in SERVICE
assert "OPTIONAL_NON_BLOCKING_CONFIDENCE" in CONFIDENCE
assert "OBD_CONFLICT_COLLECT_MORE" not in CONFIDENCE

def executable(text: str) -> str:
    return re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", text, flags=re.S))

violations = []
for path in sorted(OBD_DIR.glob("*.kt")):
    code = executable(path.read_text(encoding="utf-8"))
    for token in ["KWriteManager", "KFactorManager", "startWrite(", "startBatchWrite("]:
        if token in code:
            violations.append(f"{path.name}: {token}")
assert not violations, "OBD has writer reachability: " + ", ".join(violations)
print("BLUE_OBD_WITNESS_CONFIDENCE_PROJECTION_CONTRACT=PASS")
