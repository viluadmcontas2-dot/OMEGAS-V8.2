import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/v7/runtime/V7SessionRuntime.kt"
BLUE = ROOT / "app/src/main/java/com/omegas/prohub/blue/BlueCausalEngine.kt"
BLUE_AUTOCAL = ROOT / "app/src/main/java/com/omegas/prohub/blue/BlueAutoCalAdapter.kt"
COMPAT = ROOT / "app/src/main/java/com/omegas/v7/runtime/BlueEquivalenceCompatibility.kt"
CODEC = ROOT / "app/src/main/java/com/omegas/v7/runtime/V7SessionSnapshotCodec.kt"
LEARNING_UI = ROOT / "app/src/main/assets/ui/screens/learning.js"
LIVE_BUDGET = ROOT / "tests/ui/live-tracing-budget.test.cjs"


class LearningConsolidationContract(unittest.TestCase):
    def setUp(self):
        self.runtime = RUNTIME.read_text("utf-8")
        self.blue = BLUE.read_text("utf-8")
        self.blue_autocal = BLUE_AUTOCAL.read_text("utf-8")
        self.compat = COMPAT.read_text("utf-8")
        self.codec = CODEC.read_text("utf-8")
        self.learning_ui = LEARNING_UI.read_text("utf-8")
        self.live_budget = LIVE_BUDGET.read_text("utf-8")

    def test_one_blue_engine_owns_equivalence_and_v7_is_only_alias(self):
        self.assertIn("class BlueCausalEngine", self.blue)
        self.assertIn("typealias V7EquivalenceEngine = BlueCausalEngine", self.compat)
        self.assertFalse((ROOT / "app/src/main/java/com/omegas/v7/runtime/V7EquivalenceEngine.kt").exists())

    def test_first_cng_comparison_is_immutable_and_uses_physical_time(self):
        self.assertIn("private fun immutableByVisit", self.runtime)
        self.assertIn("current.any { it.visitId == incoming.visitId }", self.runtime)
        self.assertIn("createdAtMs = cng.collectedAtMs", self.blue)
        self.assertIn("alreadyCompared", self.blue)
        self.assertIn("filterNot { it.visitId in alreadyCompared }", self.blue)

    def test_petrol_reference_is_immediate_quality_evidence_not_visit_count(self):
        self.assertIn("fun petrolReference", self.blue)
        self.assertIn("maximumReferenceBursts", self.blue)
        self.assertIn("values[values.size / 2]", self.blue)
        self.assertNotIn("VisitConfidence", self.blue)
        self.assertFalse((ROOT / "app/src/main/java/com/omegas/prohub/learning/VisitConfidence.kt").exists())

    def test_calibration_state_isolation_is_explicit(self):
        self.assertIn("fun calibrationState", self.blue)
        self.assertIn("cngRevision == revision", self.blue)
        self.assertIn("state.activeCngEvidence()", self.blue)

    def test_causal_gain_and_log_ratio_are_single_math_path(self):
        self.assertIn("fun cngErrorLog", self.blue)
        self.assertIn("ln(petrolOnCngMs / petrolReferenceMs)", self.blue)
        self.assertIn("fun actuatorGain", self.blue)
        self.assertIn("-(afterErrorLog - beforeErrorLog) / deltaLnK", self.blue)
        self.assertIn("fun correctionMultiplier", self.blue)

    def test_autocal_consumes_blue_engine_without_automatic_write(self):
        self.assertIn("BlueCausalEngine", self.blue_autocal)
        self.assertIn("engine.cngErrorLog", self.blue_autocal)
        self.assertIn("engine.correctionMultiplier", self.blue_autocal)
        self.assertIn("automaticWrite = false", self.blue_autocal)
        for legacy in ("AutoMatchV5Engine", "AutoMatchResidualPlanner", "AutoMatchKFactorDraft"):
            self.assertNotIn(legacy, self.blue_autocal)

    def test_schema_remains_readable_during_blue_migration(self):
        self.assertIn('private const val SCHEMA = "OMEGAS_V7_SESSION_6"', self.codec)
        for schema in ("OMEGAS_V7_SESSION_5", "OMEGAS_V7_SESSION_4", "OMEGAS_V7_SESSION_3", "OMEGAS_V7_SESSION_2"):
            self.assertIn(schema, self.codec)

    def test_live_tracing_visual_remains_removed(self):
        self.assertIn("aprendizado rapido nao persegue pesos bilineares no DOM", self.live_budget)
        self.assertIn("doesNotMatch(appSource, /\\.setTrace", self.live_budget)
        self.assertIn("function renderLightLiveContext", self.live_budget)
        self.assertNotIn(".setTrace(", self.learning_ui)


if __name__ == "__main__":
    unittest.main()
