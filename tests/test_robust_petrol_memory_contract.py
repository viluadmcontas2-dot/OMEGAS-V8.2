from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MEMORY = ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt"
SUMMARY = ROOT / "app/src/main/java/com/omegas/prohub/learning/BoundedRobustPetrolSummary.kt"
REGISTRY = ROOT / "app/src/main/java/com/omegas/prohub/learning/ScientificConstantRegistry.kt"


class RobustPetrolMemoryContract(unittest.TestCase):
    def test_learning_region_persists_and_restores_bounded_robust_summary(self):
        memory = MEMORY.read_text("utf-8")
        for token in (
            "val petrolRobust: BoundedRobustPetrolSummary",
            "petrolRobust.observe(sample.petrolMs)",
            "petrolRobust.merge(other.petrolRobust)",
            'put("petrol_robust"',
            'raw.optJSONObject("petrol_robust")',
            "petrolRobust.copySummary()",
        ):
            self.assertIn(token, memory)

    def test_reference_selector_consumes_robust_median_not_legacy_mean(self):
        memory = MEMORY.read_text("utf-8")
        self.assertIn("petrolMs = region.petrolRobust.median(region.petrolMean)", memory)
        self.assertNotIn("petrolMs = region.petrolMean,\n                        confidence", memory)

    def test_robust_tail_is_hard_bounded_and_registered_as_resource_budget(self):
        summary = SUMMARY.read_text("utf-8")
        registry = REGISTRY.read_text("utf-8")
        self.assertIn("const val MAX_RETAINED_SAMPLES = 31", summary)
        self.assertIn("while (retained.size > MAX_RETAINED_SAMPLES) retained.removeFirst()", summary)
        self.assertIn('"BoundedRobustPetrolSummary.MAX_RETAINED_SAMPLES"', registry)
        self.assertIn("ScientificConstantClass.RESOURCE_BUDGET", registry)

    def test_old_history_without_robust_payload_has_explicit_legacy_fallback(self):
        summary = SUMMARY.read_text("utf-8")
        memory = MEMORY.read_text("utf-8")
        self.assertIn("if (raw == null)", summary)
        self.assertIn("fallback?.isFinite() == true", summary)
        self.assertIn("BoundedRobustPetrolSummary.fromJson(raw.optJSONObject(\"petrol_robust\"), petrolMean)", memory)


if __name__ == "__main__":
    unittest.main()
