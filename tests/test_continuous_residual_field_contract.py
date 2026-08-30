from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FIELD = ROOT / "app/src/main/java/com/omegas/prohub/learning/ContinuousResidualField.kt"
ADVISOR = (ROOT / "app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt").read_text("utf-8")
ADAPTER = (ROOT / "app/src/main/java/com/omegas/prohub/calibration/AdvisorSuggestionAdapterV7.kt").read_text("utf-8")


def test_continuous_rpm_map_field_is_published_without_replacing_raw_evidence():
    assert FIELD.exists()
    source = FIELD.read_text("utf-8")
    for token in ("DIRECT", "NEAR", "GLOBAL_ONLY", "localResidualPercent", "nearestDistance", "inferredMapBar"):
        assert token in source
    assert '"mapResidualPredictions"' in ADVISOR
    assert 'optJSONArray("mapResidualPredictions")' in ADAPTER
    assert 'optJSONArray("mapResidualSuggestions")' in ADAPTER


if __name__ == "__main__":
    test_continuous_rpm_map_field_is_published_without_replacing_raw_evidence()
    print("CONTINUOUS_RESIDUAL_FIELD_CONTRACT=PASS")
