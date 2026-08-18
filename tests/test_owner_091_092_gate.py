from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_091_repetition_is_bounded_before_confidence():
    novelty_test = read("app/src/test/java/com/omegas/prohub/learning/ContinuousWindowNoveltyTest.kt")
    store = read("app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt")
    confidence = read("app/src/main/java/com/omegas/prohub/learning/VisitConfidence.kt")

    assert "repeat(10_000)" in novelty_test
    assert "accumulatedNewFrames" in novelty_test
    assert "assertEquals(0, accumulatedNewFrames)" in novelty_test
    assert "novelty.duplicate" in store
    assert "sample = null" in store
    assert "learningEligible = false" in store
    assert "source.quality * novelty.fraction" in store

    # Confidence is driven by independent/effective visits, not raw frame count.
    assert "uniqueVisits" in confidence
    assert "effectiveVisits" in confidence
    assert "frameCount" not in confidence


def test_092_residual_stats_are_descriptive_not_a_writer_decision():
    stats = read("app/src/main/java/com/omegas/prohub/learning/ResidualSpatialStats.kt")
    stats_test = read("app/src/test/java/com/omegas/prohub/learning/ResidualSpatialStatsTest.kt")
    assembler = read("app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt")

    for field in (
        '"cell_count"',
        '"signed_mean_error_pct"',
        '"mean_abs_error_pct"',
        '"rms_error_pct"',
        '"row_span"',
        '"column_span"',
        '"dominant_sign_fraction"',
        '"largest_same_sign_component_fraction"',
    ):
        assert field in stats

    assert '"classification_policy_applied", false' in stats
    assert '"chooses_map_or_curve", false' in stats
    assert "localized residual remains spatially concentrated" in stats_test
    assert "broad coherent residual exposes large connected footprint" in stats_test
    assert "contradictory checkerboard exposes low same-sign connectivity" in stats_test
    assert '"residualSpatialStats"' in assembler
    assert "ResidualSpatialStats.from" in assembler
