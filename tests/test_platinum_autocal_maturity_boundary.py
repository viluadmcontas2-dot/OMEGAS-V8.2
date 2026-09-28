from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACQ = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalAcquisition.kt").read_text(encoding="utf-8")
MONITOR = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")


def main():
    # CALIBRATION_VAL_1 is 10 elements on the observed module while ProgBase's
    # settings editor exposes 12 conceptual rows. No 10->12 positional guess is allowed.
    for forbidden in ("calibration.getOrNull(2)", "calibration.getOrNull(5)", "calibration.getOrNull(8)"):
        assert forbidden not in ACQ, forbidden

    assert '"calibrationValueMapping", "UNRESOLVED_10_OF_12"' in ACQ
    assert '"maturityThresholdsPromoted", false' in ACQ
    assert '"thresholdSemantics", "UNRESOLVED_CALIBRATION_VAL_MAPPING"' in ACQ
    assert '"zoneAcquired", zoneFlag == 1' in ACQ
    assert '.put("draw", activityPresent)' in ACQ

    # Unknown maturity semantics must not turn off the 1s operational mirror.
    assert "val acquisitionEnabled = thresholds.third == 1" in MONITOR
    assert "!fullSnapshotAlreadyDue && acquisitionEnabled && refreshDue.acquisition" in MONITOR
    assert "!fullSnapshotAlreadyDue && thresholdsReady && refreshDue.acquisition" not in MONITOR

    print("PLATINUM_AUTOCAL_MATURITY_BOUNDARY=PASS")


if __name__ == "__main__":
    main()
