from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def assert_budget_fields(source: str, fields: tuple[str, ...]) -> None:
    for field in fields:
        assert field in source, f"missing budget field {field}"


def test_live_delivery_declares_bounded_latest_only_budget():
    source = read("app/src/main/java/com/omegas/prohub/util/LatestOnlyBackgroundPipeline.kt")
    assert_budget_fields(source, (
        '"consumer"',
        '"trigger"',
        '"cadence"',
        '"queueBound"',
        '"overloadPolicy"',
        '"pendingEstimatedBytes"',
        '"lastQueueDelayMs"',
        '"maxQueueDelayMs"',
        '"lastProcessingMs"',
        '"maxProcessingMs"',
        '"lastThreadCpuMs"',
        '"maxThreadCpuMs"',
        '"dropAffectsAcquisition"',
    ))
    assert "COALESCE_PENDING_TO_LATEST" in source
    assert "queueBound\", 1" in source


def test_science_router_declares_semantic_bounded_budget():
    source = read("app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt")
    assert_budget_fields(source, (
        '"consumer"',
        '"trigger"',
        '"cadence"',
        '"queueBoundImportant"',
        '"queueBoundDiagnostic"',
        '"overloadPolicy"',
        '"queuedEstimatedBytes"',
        '"lastQueueDelayMs"',
        '"maxQueueDelayMs"',
        '"lastProcessingMs"',
        '"maxProcessingMs"',
        '"lastThreadCpuMs"',
        '"maxThreadCpuMs"',
        '"acquisitionDropAllowed"',
    ))
    assert "MAX_HOT_EVIDENCE = 3" in source
    assert "EVENT_DRIVEN_NO_TIMER" in source


def test_session_recorder_declares_its_own_queue_and_disk_side_budget():
    source = read("app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt")
    assert_budget_fields(source, (
        '"consumerBudget"',
        '"queueDepth"',
        '"pendingEvents"',
        '"pendingPayloadBytes"',
        '"maxPendingPayloadBytes"',
        '"overloadPolicy"',
        '"dropAffectsAcquisition"',
        '"lastQueueDelayMs"',
        '"maxQueueDelayMs"',
        '"lastProcessingMs"',
        '"maxProcessingMs"',
        '"lastThreadCpuMs"',
        '"maxThreadCpuMs"',
    ))
    assert "ArrayBlockingQueue" not in source
    assert "MAX_PENDING_PAYLOAD_BYTES" in source
    assert "tryReservePayloadBytes" in source
    assert "DROP_INCOMING_RECORDER_EVENT_ON_BYTE_BUDGET" in source
