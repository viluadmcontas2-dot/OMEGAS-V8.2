from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"


def section(text: str, start: str, end: str) -> str:
    a = text.index(start)
    b = text.index(end, a)
    return text[a:b]


def main() -> None:
    text = MANAGER.read_text(encoding="utf-8")
    poll = section(text, "    private fun pollCycle(sock: BluetoothSocket) {", "    private fun readContext(sock: BluetoothSocket)")

    required = [
        'readPidTimed(sock, "0106", 0x06)',
        "ObdStftCodec.percent",
        "onLiveSample",
        '"STFT_OBSERVATION"',
        '"PENDING_MP48_PAIR"',
        'put("requestedAtMs"',
        'put("observedAtMs"',
    ]
    missing = [token for token in required if token not in poll]
    assert not missing, f"STFT witness handoff metadata missing: {missing}"

    forbidden = {
        '"0103"': "fuel-system PID",
        '"010C"': "OBD RPM PID",
        "readContext(sock)": "scanner context sweep",
        "qualification(": "legacy multi-PID learning gate",
        "collectQualified(": "legacy OBD map collector",
        "independentMap.observe(": "legacy independent OBD map",
    }
    present = [f"{label}: {token}" for token, label in forbidden.items() if token in poll]
    assert not present, "live OBD acquisition is not STFT-only: " + ", ".join(present)

    print("BLUE_OBD_STFT_ONLY_CONTRACT=PASS")


if __name__ == "__main__":
    main()
