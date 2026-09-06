from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
COORDINATOR = ROOT / "app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt"
ACCESS = ROOT / "app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt"
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"
OBD_DIR = ROOT / "app/src/main/java/com/omegas/prohub/obd"


def require_tokens(label: str, text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"{label} missing witness confidence wiring: {missing}"


def executable_kotlin(text: str) -> str:
    """Ignore comments so documentation like 'never calls KWriteManager' is not a dependency."""
    without_blocks = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", without_blocks)


def main() -> None:
    coordinator = COORDINATOR.read_text(encoding="utf-8")
    access = ACCESS.read_text(encoding="utf-8")
    service = SERVICE.read_text(encoding="utf-8")

    require_tokens(
        "BlueCalibrationCoordinator",
        coordinator,
        [
            "BlueWitnessConfidence",
            "latestObdWitness",
            "fun updateObdWitness(witness: JSONObject)",
            "BlueWitnessConfidence.project(",
            "expectedCalibrationState = calibrationStateId(",
            '"effectiveConfidence"',
        ],
    )
    require_tokens(
        "BlueCalibrationAccess",
        access,
        [
            "syncObdWitness(this, coordinator)",
            "coordinator.updateObdWitness(JSONObject(service.obdWitnessStatusJson()))",
        ],
    )
    require_tokens(
        "TelemetryForegroundService",
        service,
        [
            "pairObdStftWitness(sample)",
            "fun obdWitnessStatusJson()",
        ],
    )

    forbidden = ["KWriteManager", "KFactorManager", "startWrite(", "startBatchWrite("]
    violations: list[str] = []
    for path in sorted(OBD_DIR.glob("*.kt")):
        code = executable_kotlin(path.read_text(encoding="utf-8"))
        for token in forbidden:
            if token in code:
                violations.append(f"{path.name}: {token}")
    assert not violations, "OBD witness must have zero writer reachability: " + ", ".join(violations)

    print("BLUE_OBD_WITNESS_CONFIDENCE_PROJECTION_CONTRACT=PASS")


if __name__ == "__main__":
    main()
