from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
HELPER_PATH = ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningEvidenceDimensions.kt"
ASSEMBLER = (ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningUiSnapshotAssembler.kt").read_text(encoding="utf-8")


class RedLearningConfidenceContractTest(unittest.TestCase):
    def test_ui_science_projection_enriches_regions_with_evidence_dimensions(self):
        self.assertTrue(HELPER_PATH.exists())
        helper = HELPER_PATH.read_text(encoding="utf-8")
        self.assertIn('"precision_within_visit"', helper)
        self.assertIn('"effective_evidence_mass"', helper)
        self.assertIn('"independent_visits"', helper)
        self.assertIn("LearningEvidenceDimensions.enrichRegions", ASSEMBLER)

    def test_dimensions_reuse_existing_authoritative_observables(self):
        helper = HELPER_PATH.read_text(encoding="utf-8")
        self.assertIn('region.optDouble("quality", 0.0)', helper)
        self.assertIn('region.optDouble("weight",', helper)
        self.assertIn('region.optInt("visit_count"', helper)

    def test_projection_does_not_invent_stage_or_threshold(self):
        helper = HELPER_PATH.read_text(encoding="utf-8")
        self.assertNotIn('put("stage"', helper)
        self.assertNotIn("confidenceStage", helper)
        self.assertNotIn("acceptedVisits", helper)
        self.assertNotIn("confirmedVisits", helper)


if __name__ == "__main__":
    unittest.main()
