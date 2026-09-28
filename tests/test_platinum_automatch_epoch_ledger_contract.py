from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONITOR = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")


def main():
    assert "onNativeAutoMatchObserved" in MONITOR
    assert '"eventType", "NATIVE_AUTOMATCH_EPOCH"' in MONITOR
    assert '"evidence", autoMatchEvidence.toJson()' in MONITOR
    assert '"acquisition", acquisition' in MONITOR
    assert '"snapshotHash", snapshot.snapshotHash' in MONITOR
    assert "autoMatchCounterEvent != null" in MONITOR
    assert "onNativeAutoMatchObserved(epoch)" in MONITOR

    assert 'sessionRecorder.record(' in SERVICE
    assert '"autocal_native_automatch_epoch"' in SERVICE
    assert '"autocal"' in SERVICE

    # Algorithm-corpus ledger must remain observational.
    assert '"appWritePerformed", false' in MONITOR
    assert '"appAutomaticWrite", false' in MONITOR

    print("PLATINUM_AUTOMATCH_EPOCH_LEDGER_CONTRACT=PASS")


if __name__ == "__main__":
    main()
