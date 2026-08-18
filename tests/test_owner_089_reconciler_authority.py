from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_reconciler_uses_single_equivalence_authority_and_never_zeroes_invalid_denominator():
    source = read("app/src/main/java/com/omegas/prohub/learning/LearningSnapshotReconciler.kt")
    assert "FuelEquivalenceObjective.evaluate(" in source
    assert "if (!equivalence.valid) return null" in source
    assert 'rejectionCounts["INVALID_EQUIVALENCE"]' in source
    assert "difference / petrolTarget * 100.0" not in source
    assert "if (petrolTarget <= 0.05) 0.0" not in source


def test_reconciled_comparison_preserves_reference_provenance_and_units():
    source = read("app/src/main/java/com/omegas/prohub/learning/LearningSnapshotReconciler.kt")
    for token in (
        '"reference_region_ids"',
        '"reference_updated_at_wall_ms"',
        '"reference_contexts"',
        '"request_environment"',
        '"reference_denominator_ms"',
        '"reference_unit"',
        '"observed_unit"',
        '"difference_unit"',
        '"error_ratio_unit"',
        '"error_percent_unit"',
        '"equivalence_reason_code"',
    ):
        assert token in source
