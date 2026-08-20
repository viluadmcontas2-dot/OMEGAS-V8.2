from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
MEMORY = (ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt").read_text(encoding="utf-8")


class RedLearningConfidenceContractTest(unittest.TestCase):
    def test_region_stage_uses_effective_weight_not_raw_window_count(self):
        self.assertRegex(MEMORY, r"fun stage\(\): String = confidenceStage\(weight,")
        self.assertNotRegex(MEMORY, r"fun stage\(\): String = confidenceStage\(sampleCount\.toDouble\(\)")

    def test_confidence_sample_mass_uses_weight(self):
        self.assertIn("weight / tolerance.confidenceSampleTarget.toDouble()", MEMORY)

    def test_region_json_separates_three_evidence_dimensions(self):
        self.assertIn('.put("precision_within_visit", precisionWithinVisit())', MEMORY)
        self.assertIn('.put("effective_evidence_mass", weight)', MEMORY)
        self.assertIn('.put("independent_visits", visitCount)', MEMORY)

    def test_precision_is_bounded_and_does_not_increment_visits(self):
        precision_body = re.search(r"fun precisionWithinVisit\(\): Double \{(?P<body>.*?)\n    \}", MEMORY, re.S)
        self.assertIsNotNone(precision_body)
        body = precision_body.group("body")
        self.assertIn("coerceIn(0.0, 1.0)", body)
        self.assertNotIn("visitCount =", body)
        self.assertNotIn("visits +=", body)


if __name__ == "__main__":
    unittest.main()
