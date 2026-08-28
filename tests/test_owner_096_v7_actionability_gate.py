from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_v7_projection_preserves_history_but_blocks_pending_actionability_during_unknown():
    source = read("app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt")
    assert "LearningMutationAuthority.current()" in source
    assert 'put("actionabilityBlocked", mutation.blocksActiveScience)' in source
    assert 'put("storedLifecycle", "PENDING")' in source
    assert 'put("lifecycle", "OBSERVING")' in source
    assert 'put("actionable", false)' in source
    assert 'put("suggestionPending", 0)' in source


def test_v7_stale_science_and_suggestion_apply_fail_closed_until_reconcile():
    source = read("app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt")
    for operation in (
        'mutationBlockedOperation("synchronize_advisor_suggestions")',
        'mutationBlockedOperation("ingest_learning_snapshot")',
        'mutationBlockedOperation("apply_suggestion")',
    ):
        assert operation in source
    assert 'put("reasonCode", mutation.state.name)' in source
    assert 'put("automaticWrite", false)' in source


def test_manual_ecu_synchronization_remains_available_for_recovery():
    source = read("app/src/main/java/com/omegas/prohub/service/V7CalibrationAccess.kt")
    # Recovery/read-only synchronization must remain callable while actionability is blocked.
    sync_section = source.split("fun TelemetryForegroundService.v7SynchronizeCalibration", 1)[1].split(
        "fun TelemetryForegroundService.v7ReconcileConfirmedManualWrite", 1
    )[0]
    assert "mutationBlockedOperation" not in sync_section
    assert "synchronizedFromEcu" in sync_section
