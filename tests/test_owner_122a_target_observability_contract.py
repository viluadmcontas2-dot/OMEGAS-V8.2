from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCal122ATargetMetrics.kt"
OBSERVER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalDualFuelMaturityObserver.kt"
PROJECTOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMaturityEventProjector.kt"
SCHEDULER = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48BackpressureScheduler.kt"


def test_122a_target_receipt_is_observational_and_bounded():
    target = TARGET.read_text(encoding="utf-8")
    assert 'MIN_SAMPLE_INTERVAL_MS = 10_000L' in target
    assert 'Process.getElapsedCpuTime()' in target
    assert 'Debug.getPss()' in target
    assert 'File("/proc/self/status")' in target
    assert 'timeToFirstAnchorMs' in target
    assert 'serialReadOnlyAverageSchedulerDelayUpperBoundMs' in target
    assert 'UPPER_BOUND_QUEUE_PLUS_ENGINE_OVERHEAD' in target

    forbidden = (
        'Executors.',
        'Thread(',
        'Timer(',
        'serial.transaction(',
        'UsbSerialManager',
        'MANUAL_WRITE',
        'writeK',
    )
    for token in forbidden:
        assert token not in target, token


def test_122a_uses_existing_autocal_opportunities_and_real_correlated_anchor():
    observer = OBSERVER.read_text(encoding="utf-8")
    projector = PROJECTOR.read_text(encoding="utf-8")
    assert 'AutoCal122ATargetMetrics.ensureSession' in observer
    assert 'AutoCal122ATargetMetrics.sampleProcess' in observer
    assert 'Mp48BackpressureScheduler' in observer
    assert 'metricsSnapshot()' in observer
    assert 'correlation.state == "CORRELATED"' in projector
    assert 'AutoCal122ATargetMetrics.markFirstAnchor' in projector


def test_122a_scheduler_metrics_do_not_change_existing_lane_policy():
    scheduler = SCHEDULER.read_text(encoding="utf-8")
    assert 'semaphore.tryAcquire()' in scheduler
    assert 'semaphore.tryAcquire(waitTimeoutMs, TimeUnit.MILLISECONDS)' in scheduler
    assert 'readOnlySchedulerDelayUpperBoundNanos' in scheduler
    assert 'schedulerDelaySemantics' in scheduler
    assert 'UPPER_BOUND_QUEUE_PLUS_ENGINE_OVERHEAD' in scheduler
