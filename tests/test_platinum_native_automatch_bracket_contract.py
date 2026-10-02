from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRACKET = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoMatchEvidenceBracket.kt"
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"


def test_native_automatch_bracket_is_epoch_bound_and_read_only():
    assert BRACKET.exists(), "Platinum P-002 bracket is not implemented yet"
    text = BRACKET.read_text(encoding="utf-8")
    monitor = MONITOR.read_text(encoding="utf-8")

    for token in (
        "FACTOR_CHANGE_CONFIRMED",
        "NO_FACTOR_CHANGE_OBSERVED",
        "INCONCLUSIVE",
        "beforeCount",
        "afterCount",
        "beforeRaw",
        "afterRaw",
        "pointDeltas",
        "16384.0",
    ):
        assert token in text, token

    assert "event.beforeCount" in text
    assert "event.afterCount" in text
    assert "before.autoMatchCount" in text
    assert "afterAutoMatchCount" in text
    assert '.put("appWritePerformed", false)' in text
    assert '.put("appAutomaticWrite", false)' in text
    assert "Mp48WorkClass.MANUAL_WRITE" not in text
    assert "serial.transaction" not in text

    assert "NativeAutoMatchEvidenceBracket.evaluate" in monitor
    assert '"nativeAutoMatchEvidence"' in monitor
    assert '"ECU_AUTOMATCH_COUNT_CHANGED"' in monitor
    assert '"pointDeltas"' in monitor


if __name__ == "__main__":
    test_native_automatch_bracket_is_epoch_bound_and_read_only()
    print("PLATINUM_NATIVE_AUTOMATCH_BRACKET_CONTRACT=PASS")
