from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MEMORY = ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt"
OBJECTIVE = ROOT / "app/src/main/java/com/omegas/prohub/learning/FuelEquivalenceObjective.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_owner_089_uses_owner_070_as_single_error_authority():
    memory = read(MEMORY)
    objective = read(OBJECTIVE)
    assert "FuelEquivalenceObjective.evaluate" in memory
    assert "REFERENCE_DENOMINATOR_TOO_SMALL" in objective
    assert "if (petrolTargetMs <= 0.05) 0.0" not in memory
    assert "if (targetMs <= 0.05) 0.0" not in memory
    assert "equivalence.valid" in memory
    assert "equivalenceDirection(equivalence.state)" in memory


def test_invalid_equivalence_cannot_publish_fake_number_or_actionability():
    memory = read(MEMORY)
    invalid = memory.split("private fun invalidEquivalenceStatus", 1)[1].split("private fun compare", 1)[0]
    assert '.put("equivalence_valid", false)' in invalid
    assert '.put("difference_ms", JSONObject.NULL)' in invalid
    assert '.put("error_ratio", JSONObject.NULL)' in invalid
    assert '.put("error_pct", JSONObject.NULL)' in invalid
    assert '.put("direction", JSONObject.NULL)' in invalid
    assert '.put("actionable", false)' in invalid
    assert '.put("suggested_delta_k_percent", JSONObject.NULL)' in invalid
    assert '.put("suggested_delta_k", JSONObject.NULL)' in invalid


def test_comparison_preserves_denominator_units_ids_timestamps_and_context():
    memory = read(MEMORY)
    for token in (
        '"reference_region_ids"',
        '"reference_denominator_ms"',
        '"reference_unit"',
        '"observed_unit"',
        '"difference_unit"',
        '"error_ratio_unit"',
        '"error_percent_unit"',
        '"reference_updated_at_wall_ms"',
        '"reference_contexts"',
        '"request_environment"',
        '"cng_sample_started_at_elapsed_ms"',
        '"cng_sample_ended_at_elapsed_ms"',
        '"timestamp_domains"',
    ):
        assert token in memory
    assert "updatedAtMs = region.updatedAt" in memory
    assert "selectedRegionContexts = result.selectedRegionContexts" in memory


def test_legacy_invalid_comparisons_are_not_restored_as_zero_error_evidence():
    memory = read(MEMORY)
    assert "fun fromJson(raw: JSONObject): FuelComparison?" in memory
    assert "if (!equivalence.valid) return null" in memory
    assert "invalidComparisons += 1" in memory
    assert "não entram na evidência ativa" in memory
