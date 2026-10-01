from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
ENGINE = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt").read_text(encoding="utf-8")
MANAGER = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text(encoding="utf-8")


def body(source: str, start_token: str, end_token: str) -> str:
    return source.split(start_token, 1)[1].split(end_token, 1)[0]


def test_service_disconnect_does_not_explicitly_disable_or_reset_autocal():
    disconnect = body(SERVICE, "fun disconnectUsb()", "fun usbDevicesJson")
    destroy = body(SERVICE, "override fun onDestroy()", "fun status()")
    forbidden = (
        "DISABLE_AUTO_CAL",
        "AUTO_CAL_ENABLE",
        "startResetToNeutral",
        "RESET_K_FACTOR",
        "RESET_ALL",
        "RESET_GAS",
        "RESET_PETROL",
    )
    for token in forbidden:
        assert token not in disconnect, token
        assert token not in destroy, token


def test_engine_graceful_disconnect_is_session_disconnect_only():
    graceful = body(ENGINE, "private fun gracefulDisconnect()", "private fun handshakeRejected")
    assert "Mp48Protocol.CMD_DISCONNECT" in graceful
    assert "AutoCalProtocol" not in graceful
    assert "AUTO_CAL_ENABLE" not in graceful


def test_manager_close_is_non_mutating():
    close = MANAGER.split("fun close()", 1)[1].split("private fun runPrepared", 1)[0]
    assert "executor.shutdownNow()" in close
    assert "transaction(" not in close
    assert "AUTO_CAL_ENABLE" not in close


if __name__ == "__main__":
    test_service_disconnect_does_not_explicitly_disable_or_reset_autocal()
    test_engine_graceful_disconnect_is_session_disconnect_only()
    test_manager_close_is_non_mutating()
    print("AUTOCAL_DISCONNECT_NONMUTATION_CONTRACT=PASS")