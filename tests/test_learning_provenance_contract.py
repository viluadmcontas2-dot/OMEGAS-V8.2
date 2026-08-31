from pathlib import Path


# This existing contract intentionally participates in both curated and exhaustive CI gates.
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
    # The didactic UI was intentionally renamed. Preserve the scientific contract,
    # not obsolete labels: exact observed/equivalent pairs must remain visibly
    # distinct from aggregate regional summaries and their support provenance.
    for text in (
        "Gasolina esperada",
        "GNV observado",
        "Por que confiar",
        "Precisão local",
        "Resumo projetado da célula",
        "não é o par usado no cálculo",
        "no par equivalente usado no cálculo",
        "no par observado",
        "no resumo agregado da região",
    ):
        assert text in VIEW, f"missing current provenance explanation {text!r}"

    assert "comparison?.observed_pair" in VIEW
    assert "comparison?.reference_support" in VIEW
    assert "referenceSupport?.support_type" in VIEW
    assert "referenceSupport?.nearest_distance" in VIEW


if __name__ == "__main__":
    test_comparison_exports_exact_pair_reference_support_and_calibration_context()
    test_detail_never_presents_aggregate_summaries_as_the_observed_pair()
    print("LEARNING_PROVENANCE_CONTRACT=PASS")
