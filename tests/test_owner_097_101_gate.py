from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_097_persistence_is_coalesced_and_observable():
    writer = read("app/src/main/java/com/omegas/prohub/learning/CoalescedSnapshotWriter.kt")
    test = read("app/src/test/java/com/omegas/prohub/learning/CoalescedSnapshotWriterTest.kt")
    assert "while (dirty.getAndSet(false))" in writer
    for metric in (
        '"requests"', '"writes"', '"coalesced"', '"failures"',
        '"lastQueueDelayMs"', '"maxQueueDelayMs"',
        '"lastWriteDurationMs"', '"maxWriteDurationMs"',
    ):
        assert metric in writer
    assert "1_001" in test
    assert 'metrics.getLong("writes") <= 3L' in test
    assert 'metrics.getLong("coalesced") >= 998L' in test


def test_098_primary_and_sidecar_cardinality_are_bounded():
    memory = read("app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt")
    budget = read("app/src/main/java/com/omegas/prohub/learning/LearningMemoryBudget.kt")
    evidence_budget = read("app/src/main/java/com/omegas/prohub/learning/LearningEvidenceBudget.kt")
    budget_test = read("app/src/test/java/com/omegas/prohub/learning/LearningMemoryBudgetTest.kt")

    assert "MAX_REGIONS = 2000" in memory
    assert "MAX_COMPARISONS = 600" in memory
    assert "MAX_SESSIONS = 100" in memory
    assert "if (regions.size > MAX_REGIONS) regions.removeAt(0)" in memory
    assert "while (comparisons.size > MAX_COMPARISONS) comparisons.removeFirst()" in memory
    assert "while (sessions.size > MAX_SESSIONS) sessions.removeFirst()" in memory
    assert "TARGET_PERSISTED_BYTES = 5 * 1024 * 1024" in budget
    assert "MAX_REGION_VISIT_IDS = 16" in budget
    assert "MAX_REGION_SESSION_IDS = 8" in budget
    assert "MAX_NATIVE_SNAPSHOTS = 16" in evidence_budget
    assert "MAX_NATIVE_ANCHORS = 256" in evidence_budget
    assert "MAX_VISIT_ACCUMULATORS = 256" in evidence_budget
    assert "MAX_PROVENANCE_ENTRIES = 64" in evidence_budget
    assert "repeat(10_000)" in budget_test


def test_099_100_restore_states_are_explicit_and_never_restore_actionable_cng():
    validator = read("app/src/main/java/com/omegas/prohub/learning/LearningRestoreValidator.kt")
    tests = read("app/src/test/java/com/omegas/prohub/learning/LearningRestoreValidatorTest.kt")
    for state in (
        "CURRENT_COMPATIBLE", "LEGACY_OBSERVATIONAL", "INCOMPATIBLE", "CORRUPT",
        "CURRENT_KNOWN_GEOMETRY", "IDENTITY_ONLY", "MISSING", "INVALID",
    ):
        assert state in validator
    assert 'put("restored_cng_actionable", false)' in validator
    assert 'put("requires_live_calibration_identity_for_cng", true)' in validator
    assert "old state without digest is downgraded" in tests
    assert "corrupt and incompatible states are explicit" in tests
    assert "identity only remains non actionable" in tests


def test_101_heavy_restore_stays_off_first_usable_startup_for_small_medium_large():
    store = read("app/src/main/java/com/omegas/prohub/learning/DeferredLiveOnlyLearningStore.kt")
    test = read("app/src/test/java/com/omegas/prohub/learning/DeferredLiveOnlyLearningStoreTest.kt")
    assert 'Thread(runnable, "omegas-learning-restore")' in store
    assert "small medium and large persisted payloads do not enter hot constructor path" in test
    assert '"small" to 4 * 1024' in test
    assert '"medium" to 512 * 1024' in test
    assert '"large" to 5 * 1024 * 1024' in test
    assert "constructorMs < 500L" in test
    assert "restoring" in test
