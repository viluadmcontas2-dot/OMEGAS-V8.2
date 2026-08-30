from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MEMORY = (ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt").read_text("utf-8")
VIEW = (ROOT / "app/src/main/assets/ui/screens/learning.js").read_text("utf-8")


def test_comparison_exports_exact_pair_reference_support_and_calibration_context():
    for key in (
        '"observed_pair"',
        '"reference_support"',
        '"calibration_context"',
        '"support_type"',
        '"nearest_distance"',
        '"calibration_hash"',
    ):
        assert key in MEMORY, f"missing durable provenance key {key}"


def test_detail_never_presents_aggregate_summaries_as_the_observed_pair():
    assert "Resumo projetado da célula" in VIEW
    assert "não é o par usado no cálculo" in VIEW
    assert "Par observado usado no cálculo" in VIEW
    assert "Suporte da referência" in VIEW
    assert "Precisão local" in VIEW


if __name__ == "__main__":
    test_comparison_exports_exact_pair_reference_support_and_calibration_context()
    test_detail_never_presents_aggregate_summaries_as_the_observed_pair()
    print("LEARNING_PROVENANCE_CONTRACT=PASS")
