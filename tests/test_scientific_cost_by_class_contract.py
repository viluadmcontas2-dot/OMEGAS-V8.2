from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUFFER = ROOT / "app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt"
WORK = ROOT / "app/src/main/java/com/omegas/prohub/util/EvidenceWorkClass.kt"


def test_055a_information_value_is_not_confidence_and_cost_is_observable_per_class():
    buffer = BUFFER.read_text(encoding="utf-8")
    work = WORK.read_text(encoding="utf-8")

    assert "enum class MarginalInformationClass" in work
    assert "Purely semantic ordering for backpressure; never a confidence or probability" in work
    assert '"QUALITATIVE_ORDER_ONLY_NOT_CONFIDENCE_OR_PROBABILITY"' in buffer
    assert '"costByClass"' in buffer
    assert '"marginalInformationClass"' in buffer
    assert '"avgQueueDelayMs"' in buffer
    assert '"avgProcessingMs"' in buffer
    assert '"avgThreadCpuMs"' in buffer
