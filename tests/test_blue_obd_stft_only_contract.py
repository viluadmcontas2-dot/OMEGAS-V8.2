from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"


def section(text: str, start: str, end: str) -> str:
    a = text.index(start)
    b = text.index(end, a)
    return text[a:b]


def main() -> None:
    text = MANAGER.read_text(encoding="utf-8")
    poll = section(text, "    private fun pollCycle(sock: BluetoothSocket) {", "    private fun readPid(sock: BluetoothSocket")
    required = [
        'readPidTimed(sock, "0106", 0x06)', "ObdStftCodec.percent", "onLiveSample",
        '"STFT_OBSERVATION"', '"PENDING_MP48_PAIR"', 'put("requestedAtMs"', 'put("observedAtMs"',
    ]
    missing = [token for token in required if token not in poll]
    assert not missing, f"STFT witness handoff metadata missing: {missing}"
    for forbidden in ['"0103"', '"010C"', "qualification(", "collectQualified(", "independentMap.observe("]:
        assert forbidden not in poll, f"live OBD acquisition is not STFT-only: {forbidden}"
    assert 'readPid(sock, "0100", 0x00)' in text
    print("BLUE_OBD_STFT_ONLY_CONTRACT=PASS")


if __name__ == "__main__":
    main()
