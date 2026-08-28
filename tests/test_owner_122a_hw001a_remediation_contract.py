from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TELEMETRY = ROOT / "app/src/main/java/com/omegas/prohub/telemetry/TelemetryStateStore.kt"
RECORDER = ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt"


def test_s4_retained_telemetry_history_is_typed_not_json_object_per_frame():
    source = TELEMETRY.read_text(encoding="utf-8")
    assert "ArrayDeque<JSONObject>" not in source
    assert "history.addLast(\n                JSONObject()" not in source
    assert "TelemetryHistorySample" in source


def test_s7_session_recorder_is_bounded_by_payload_bytes_not_event_count_only():
    source = RECORDER.read_text(encoding="utf-8")
    assert "ArrayBlockingQueue" not in source
    assert "QUEUE_CAPACITY = 8192" not in source
    assert "MAX_PENDING_PAYLOAD_BYTES" in source
    assert "tryReservePayloadBytes" in source
    assert "DROP_INCOMING_RECORDER_EVENT_ON_BYTE_BUDGET" in source
    assert '"pendingPayloadBytes"' in source
    assert '"maxPendingPayloadBytes"' in source
