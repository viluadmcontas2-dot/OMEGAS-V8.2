from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
RECORDER = (ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt").read_text(encoding="utf-8")
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")


def test_usb_generation_is_the_session_boundary():
    generation = SERVICE[SERVICE.index("if (connected) {"):SERVICE.index("} else {", SERVICE.index("if (connected) {"))]
    assert 'sessionRecorder.stop("USB_SESSION_REPLACED")' in generation
    assert '.put("usbSessionId", sessionId)' in generation
    assert generation.index('sessionRecorder.stop("USB_SESSION_REPLACED")') < generation.index('sessionRecorder.start(')


def test_manual_session_carries_current_physical_generation():
    start = SERVICE[SERVICE.index("fun startSessionRecording"):SERVICE.index("fun stopSessionRecording")]
    assert 'usb.connectionSessionId' in start
    assert '"usbSessionId"' in start


def test_recorder_owns_one_semantic_projection_not_another_capture_loop():
    assert "SessionSemanticLedger" in RECORDER
    assert "session_summary.json" in (ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionSemanticLedger.kt").read_text(encoding="utf-8")
    assert 'record("telemetry", "mp48", live)' in SERVICE
    assert "ScheduledExecutor" not in (ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionSemanticLedger.kt").read_text(encoding="utf-8")


def test_manual_autocal_snapshot_is_recorded_into_same_session_evidence():
    assert 'sessionRecorder.record("autocal_manual_snapshot", "autocal", snapshot, force = true)' in BRIDGE


def test_session_summary_is_exportable_and_never_claims_automatic_map_write():
    ledger = (ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionSemanticLedger.kt").read_text(encoding="utf-8")
    assert 'automaticMapKMutation' in ledger
    assert '.put("automaticMapKMutation", false)' in ledger
    assert "session_summary.json" in RECORDER
