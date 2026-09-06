from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COORDINATOR = ROOT / "app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt"
ACCESS = ROOT / "app/src/main/java/com/omegas/prohub/service/BlueCalibrationAccess.kt"
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"
OBD_DIR = ROOT / "app/src/main/java/com/omegas/prohub/obd"


def require_tokens(label: str, text: str, tokens: list[str]) -> None:
    missing = [token for token in tokens if token not in text]
    assert not missing, f"{label} missing witness confidence wiring: {missing}"


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
            "fun TelemetryForegroundService.blueUpdateObdWitness(witness: JSONObject)",
            "BlueCalibrationRegistry.get(this).updateObdWitness(witness)",
        ],
    )
    require_tokens(
        "TelemetryForegroundService",
        service,
        [
            "pairObdStftWitness(sample)",
            "blueUpdateObdWitness(witness)",
        ],
    )

    forbidden = ["KWriteManager", "KFactorManager", "startWrite(", "startBatchWrite("]
    violations: list[str] = []
    for path in sorted(OBD_DIR.glob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for token in forbidden:
            if token in text:
                violations.append(f"{path.name}: {token}")
    assert not violations, "OBD witness must have zero writer reachability: " + ", ".join(violations)

    print("BLUE_OBD_WITNESS_CONFIDENCE_PROJECTION_CONTRACT=PASS")


if __name__ == "__main__":
    main()
