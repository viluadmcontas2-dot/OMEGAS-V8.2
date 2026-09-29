from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
ACQ = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalAcquisition.kt").read_text(encoding="utf-8")
MONITOR = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")
ORACLE = json.loads(
    (ROOT / "tests/fixtures/progbase-autocal-resource-defaults-v1.json").read_text(encoding="utf-8")
)


def main():
    # The canonical ProgBase DUMP/disassembly closes the runtime selector map.
    selectors = ORACLE["calibrationGrid"]["runtimeMaturitySelectors"]
    assert selectors["zoneBoundariesInclusive"] == [5, 9, 13]
    assert selectors["petrolLow"] == "VECT_AUTOCAL_U8_1"
    assert selectors["petrolNormal"] == "CALIBRATION_VAL_1[2]"
    assert selectors["gasLow"] == "CALIBRATION_VAL_1[5]"
    assert selectors["gasNormal"] == "CALIBRATION_VAL_1[8]"

    assert "val petrolNormalThreshold = calibration.getOrNull(2)" in ACQ
    assert "val gasLowThreshold = calibration.getOrNull(5)" in ACQ
    assert "val gasNormalThreshold = calibration.getOrNull(8)" in ACQ
    assert '"calibrationValueMapping", "PROGBASE_DUMP_GRID_PROVEN"' in ACQ
    assert '"maturityThresholdsPromoted", maturityThresholdsAvailable' in ACQ
    assert '"thresholdSemantics", thresholdSemantics' in ACQ
    assert '"PROGBASE_DUMP_RUNTIME_SELECTOR"' in ACQ
    assert '"zoneAcquired", zoneFlag == 1' in ACQ
    assert '.put("draw", activityPresent)' in ACQ

    # Threshold knowledge must not become a serial gate: the 1s operational
    # mirror remains active whenever native acquisition itself is enabled.
    assert "val acquisitionEnabled = thresholds.third == 1" in MONITOR
    assert "!fullSnapshotAlreadyDue && acquisitionEnabled && refreshDue.acquisition" in MONITOR
    assert "!fullSnapshotAlreadyDue && thresholdsReady && refreshDue.acquisition" not in MONITOR

    print("PLATINUM_AUTOCAL_MATURITY_BOUNDARY=PASS")


if __name__ == "__main__":
    main()
