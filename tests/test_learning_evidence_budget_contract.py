from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUDGET = ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningEvidenceBudget.kt"
STORE = ROOT / "app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt"
WRITER = ROOT / "app/src/main/java/com/omegas/prohub/learning/CoalescedSnapshotWriter.kt"
MEMORY_BUDGET_TEST = ROOT / "app/src/test/java/com/omegas/prohub/learning/LearningMemoryBudgetTest.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_learning_evidence_budget_is_explicit_and_bounded():
    budget = read(BUDGET)
    store = read(STORE)
    assert "MAX_PERSISTED_BYTES = 256 * 1024" in budget
    assert "MAX_NATIVE_SNAPSHOTS = 16" in budget
    assert "MAX_VISIT_ACCUMULATORS = 256" in budget
    assert "MAX_PROVENANCE_ENTRIES = 64" in budget
    assert "trimNativeEvidenceLocked()" in store
    assert "trimVisitAccumulatorsLocked()" in store


def test_learning_evidence_snapshot_coalesces_before_payload_build():
    store = read(STORE)
    writer = read(WRITER)
    assert 'EVIDENCE_STATE_SCHEMA = "omegas-learning-evidence-v6-v3"' in store
    assert "evidenceStateWriter.request { buildEvidencePayload(snapshot) }" in store
    assert "fun request(payloadProvider: () -> String): Boolean" in writer
    assert "latestPayloadProvider" in writer
    assert "while (dirty.getAndSet(false))" in writer


def test_live_ingest_does_not_publish_heavy_evidence_tree_per_frame():
    store = read(STORE)
    assert "return decorate(result, includeAdvisor = false)" in store
    assert 'if (includeAdvisor) {' in store
    assert '.put("native_ecu_evidence"' in store


def test_budget_behavior_is_exercised_by_authoritative_gradle_junit_suite():
    junit = read(MEMORY_BUDGET_TEST)
    # Python owns the structural contract only. Behavioral execution belongs to
    # :app:testDebugUnitTest so CI uses the project's Kotlin/JDK toolchain rather
    # than an incidental system kotlinc discovered during pytest collection.
    assert "10_000" in junit
    assert "MAX_PERSISTED_BYTES" in junit
    assert "MAX_VISIT_ACCUMULATORS" in junit
    assert "MAX_PROVENANCE_ENTRIES" in junit
