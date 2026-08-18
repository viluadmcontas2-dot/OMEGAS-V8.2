from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "app/src/main/java/com/omegas/prohub/util/EvidenceWorkClass.kt"
BUFFER = ROOT / "app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt"
RUNTIME = ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt"


class EvidenceRouterContract(unittest.TestCase):
    def test_all_required_semantic_classes_exist(self):
        source = WORK.read_text("utf-8")
        for token in (
            "STATIC_REFERENCE",
            "DYNAMIC_COHERENT",
            "FAST_KSTAR",
            "POST_WRITE_REVALIDATION",
            "DIAGNOSTIC_ONLY",
        ):
            self.assertIn(token, source)
        self.assertIn("incoming.valueRank >= pending.valueRank", source)

    def test_router_is_the_existing_science_buffer_not_a_second_queue_or_transport(self):
        source = BUFFER.read_text("utf-8")
        self.assertIn("workClass: EvidenceWorkClass", source)
        self.assertIn("EvidenceBackpressurePolicy.fromLegacyImportant", source)
        self.assertIn("SUPERSEDE_LOWEST_VALUE_PENDING_OR_REJECT_INCOMING", source)
        self.assertIn('.put("acquisitionDropAllowed", false)', source)
        self.assertIn("MAX_HOT_EVIDENCE = 3", source)
        self.assertIn("rejectedLowValue", source)
        self.assertIn("supersededByClass", source)
        self.assertNotIn("UsbSerialManager", source)
        self.assertNotIn("Mp48SerialScheduler", source)

    def test_runtime_publishes_canonical_evidence_before_science_router(self):
        source = RUNTIME.read_text("utf-8")
        publish = source.index("if (!latestCanonicalEvidence.publish(evidence)) return")
        classify = source.index("val workClass = EvidenceWorkClassifier.classify(")
        science = source.index("val accepted = learningPipeline.submit(")
        self.assertLess(publish, classify)
        self.assertLess(publish, science)
        self.assertIn("CanonicalEvidence.from(", source)
        self.assertIn("RealtimeLearningBuffer(", source)


if __name__ == "__main__":
    unittest.main()
