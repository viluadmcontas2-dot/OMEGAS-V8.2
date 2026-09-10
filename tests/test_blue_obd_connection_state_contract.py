from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"


def main() -> None:
    text = MANAGER.read_text(encoding="utf-8")
    required = [
        "ElmConnectionState(",
        "ElmStage.RFCOMM",
        "ElmStage.ELM_INIT",
        "ElmStage.PROTOCOL",
        "ElmStage.STFT_READY",
        "ElmStage.LIVE",
        'put("connectionStage"',
        'put("connectionErrorCode"',
        'put("connectionDetail"',
        'put("retryable"',
        'listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")',
        "ElmResponseParser.mode01(response, pid)",
        "omegas-obd-rfcomm-watchdog",
        '"RFCOMM_TIMEOUT"',
        '"BLUETOOTH_PERMISSION_REQUIRED"',
        '"PROTOCOL_FAILED"',
        '"STFT_PROBE_FAILED"',
        '"LIVE_LINK_LOST"',
        "connectionState.snapshot().stage in setOf(ElmStage.STFT_READY, ElmStage.LIVE)",
        '"Conexão ELM foi encerrada durante aquisição STFT"',
    ]
    missing = [needle for needle in required if needle not in text]
    assert not missing, f"OBD connection is not stage-aware yet: {missing}"

    init = text[text.index("    private fun initializeElm"):text.index("    private fun pollCycle")]
    for forbidden in ['"ATI"', '"ATAT1"', "discoverStandardPids", "supportsStandardPid", "stftProbe"]:
        assert forbidden not in init, f"field-proven handshake regressed to strict gate: {forbidden}"

    print("BLUE_OBD_CONNECTION_STATE_CONTRACT=PASS")


if __name__ == "__main__":
    main()
