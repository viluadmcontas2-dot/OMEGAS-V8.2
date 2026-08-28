from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_ui_projection_labels_reference_and_cng_provenance_explicitly():
    source = read("app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt")
    for token in (
        '"OBSERVED"',
        '"AGGREGATED_INTERPOLATED"',
        '"EXTRAPOLATED"',
        '"reference_provenance"',
        '"cng_value_provenance"',
        '"provenance_confidence_basis"',
        '"provenance_effective_weight"',
    ):
        assert token in source


def test_provenance_reuses_existing_selector_quality_instead_of_new_magic_multiplier():
    ui = read("app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt")
    selector = read("app/src/main/java/com/omegas/prohub/learning/PetrolReferenceSelector.kt")
    advisor = read("app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt")
    assert 'comparison.optDouble("quality", 0.0)' in ui
    assert "EXTRAPOLATION_QUALITY_FACTOR" in read("app/src/main/java/com/omegas/prohub/learning/ScientificConstantRegistry.kt")
    assert "val extrapolationFactor = if (extrapolated) 0.35 else 1.0" in selector
    assert 'raw.optDouble("quality", 0.1)' in advisor
