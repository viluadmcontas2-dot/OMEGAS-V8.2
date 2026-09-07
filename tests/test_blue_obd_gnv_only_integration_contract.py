from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdWitnessEngine.kt"
CONF = ROOT / "app/src/main/java/com/omegas/prohub/blue/BlueWitnessConfidence.kt"
ADDRESS = ROOT / "app/src/main/java/com/omegas/prohub/blue/BlueMapKAddressing.kt"
COORD = ROOT / "app/src/main/java/com/omegas/prohub/calibration/BlueCalibrationCoordinator.kt"
SPEC = ROOT / "specs/001-blue-runtime-convergence/spec.md"


def main() -> None:
    engine = ENGINE.read_text(encoding="utf-8")
    conf = CONF.read_text(encoding="utf-8")
    address = ADDRESS.read_text(encoding="utf-8")
    coord = COORD.read_text(encoding="utf-8")
    spec = SPEC.read_text(encoding="utf-8")

    assert "ObdScientificFuel.CNG" in engine
    assert "ObdScientificFuel.PETROL" not in engine, "gasoline OBD must not drive witness math"
    assert "gnvStftPct" in conf and "obdGnvStftPct" in conf
    assert "obdResidualPp" not in conf
    assert "petrolOnCngMs" in address
    assert "petrolTargetMs" not in address, "gasoline reference cannot address Map K"
    assert "BlueMapKAddressing.cell(comparison)" in coord
    assert "expectedPetrolOnCngMs = comparison.petrolOnCngMs" in coord
    assert "gasoline OBD STFT and LTFT are not requirements" in spec

    executable = re.sub(r"/\*.*?\*/|//[^\n]*", "", engine + conf + address, flags=re.S)
    assert "ltft" not in executable.lower(), "LTFT entered decision code"
    for forbidden in ["KWriteManager", "KFactorManager", "startWrite(", "startBatchWrite("]:
        assert forbidden not in executable, f"OBD witness gained writer reachability: {forbidden}"
    print("BLUE_OBD_GNV_ONLY_INTEGRATION_CONTRACT=PASS")


if __name__ == "__main__":
    main()
