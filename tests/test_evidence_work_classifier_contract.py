from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt"
CLASSIFIER = ROOT / "app/src/main/java/com/omegas/prohub/util/EvidenceWorkClassifier.kt"


def test_057a_runtime_producer_uses_semantic_work_class_not_important_boolean():
    runtime = RUNTIME.read_text(encoding="utf-8")
    classifier = CLASSIFIER.read_text(encoding="utf-8")

    assert "EvidenceWorkClassifier.classify(" in runtime
    assert "workClass = workClass" in runtime
    assert "important = important" not in runtime
    assert "val important =" not in runtime

    for name in (
        "STATIC_REFERENCE",
        "DYNAMIC_COHERENT",
        "FAST_KSTAR",
        "POST_WRITE_REVALIDATION",
        "DIAGNOSTIC_ONLY",
    ):
        assert name in classifier

    assert "postWriteRevalidationPending" in runtime
    assert "reasonCode.uppercase().startsWith(\"FAST_KSTAR\")" in classifier
