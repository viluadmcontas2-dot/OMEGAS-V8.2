from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_055a_semantic_information_value_is_explicit_and_cost_is_observable():
    work = read("app/src/main/java/com/omegas/prohub/util/EvidenceWorkClass.kt")
    buffer = read("app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt")
    for name in (
        "DIAGNOSTIC_PRESENT_STATE",
        "REUSABLE_REFERENCE",
        "CONTEXT_COHERENT_OBSERVATION",
        "FAST_OBJECTIVE_OBSERVATION",
        "CAUSAL_POST_INTERVENTION",
    ):
        assert name in work
    for cost_metric in (
        '"lastProcessingMs"',
        '"maxProcessingMs"',
        '"lastThreadCpuMs"',
        '"maxThreadCpuMs"',
        '"lastQueueDelayMs"',
        '"maxQueueDelayMs"',
    ):
        assert cost_metric in buffer


def test_057a_router_has_all_semantic_classes_and_never_controls_acquisition():
    work = read("app/src/main/java/com/omegas/prohub/util/EvidenceWorkClass.kt")
    buffer = read("app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt")
    for name in (
        "STATIC_REFERENCE",
        "DYNAMIC_COHERENT",
        "FAST_KSTAR",
        "POST_WRITE_REVALIDATION",
        "DIAGNOSTIC_ONLY",
    ):
        assert name in work
    assert "incoming.valueRank >= pending.valueRank" in work
    assert '"acquisitionDropAllowed", false' in buffer
    assert "MAX_HOT_EVIDENCE = 3" in buffer


def test_063a_snapshot_persistence_is_deferred_and_coalesced_after_sample_formation():
    store = read("app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt")
    writer = read("app/src/main/java/com/omegas/prohub/learning/CoalescedSnapshotWriter.kt")
    assert "if (source != null) persistEvidenceState()" in store
    assert "latestPayloadProvider" in writer
    assert "payloadProvider" in writer
    assert "executor.execute { drain() }" in writer
    assert "while (dirty.getAndSet(false))" in writer
    # Diagnostic SessionRecorder is deliberately a separate durable audit stream;
    # this assertion is about replaceable scientific snapshots, not raw forensics.


def test_068a_routes_do_not_start_map_or_curve_read_on_enter():
    map_screen = read("app/src/main/assets/ui/screens/map.js")
    curve_screen = read("app/src/main/assets/ui/screens/curve.js")
    map_on_enter = map_screen.split("onEnter(context)", 1)[1].split("startRead(", 1)[0]
    curve_on_enter = curve_screen.split("onEnter(context)", 1)[1].split("startRead(", 1)[0]
    assert "startMapRead" not in map_on_enter
    assert "startCurveRead" not in curve_on_enter
    assert "Toque em Ler mapa K" in map_on_enter
    assert "Toque em Ler Curva K" in curve_on_enter


def test_068ab_adaptive_reuses_canonical_typed_frame_and_has_no_second_backbone():
    contract = read("app/src/main/java/com/omegas/prohub/adaptive/AdaptiveEvidenceContracts.kt")
    assert "typealias CanonicalEvidence = RuntimeTelemetryFrame" in contract
    assert "SINGLE_PHYSICAL_ACQUISITION = true" in contract
    assert "MAY_CREATE_SECOND_MP48_POLLING = false" in contract
    assert "MAY_REPARSE_JSON_TO_FORM_SCIENCE = false" in contract
    assert "MAY_WRITE_ECU = false" in contract
