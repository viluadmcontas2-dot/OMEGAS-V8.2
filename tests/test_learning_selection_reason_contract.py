from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_canonical_reason_vocabulary_is_explicit_and_bounded():
    source = read("app/src/main/java/com/omegas/prohub/learning/LearningSelectionReason.kt")
    for reason in (
        "REFERENCE_FOUND",
        "NO_REGION",
        "INSUFFICIENT_SUPPORT",
        "ENV_MISMATCH",
        "STALE",
        "GEOMETRY_UNKNOWN",
        "INVALID_CONDITION",
        "NUMERIC_INVALID",
        "UNKNOWN",
    ):
        assert reason in source


def test_selector_preserves_detail_and_publishes_canonical_reason():
    source = read("app/src/main/java/com/omegas/prohub/learning/PetrolReferenceSelector.kt")
    assert '.put("reason_code", reasonCode)' in source
    assert '.put("detail_reason_code", reasonCode)' in source
    assert '.put("selection_reason_code", selectionReason().name)' in source
    assert "LearningSelectionReason.fromReference(" in source


def test_geometry_unknown_does_not_overwrite_a_more_primary_reference_failure():
    source = read("app/src/main/java/com/omegas/prohub/learning/LearningSelectionReason.kt")
    assert "available && referenceReason == REFERENCE_FOUND && !geometryKnown" in source
    assert '"NO_PETROL_REGIONS"' in source
    assert '"REFERENCE_SPREAD_EXCEEDED"' in source
