import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/v7/runtime/V7SessionRuntime.kt"
STABILITY = ROOT / "app/src/main/java/com/omegas/v7/runtime/LearningStabilityV7.kt"
EQUIVALENCE = ROOT / "app/src/main/java/com/omegas/v7/runtime/V7EquivalenceEngine.kt"
COORDINATOR = ROOT / "app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt"
CODEC = ROOT / "app/src/main/java/com/omegas/v7/runtime/V7SessionSnapshotCodec.kt"
LEARNING_UI = ROOT / "app/src/main/assets/ui/screens/learning.js"
LIVE_BUDGET = ROOT / "tests/ui/live-tracing-budget.test.cjs"


class LearningConsolidationContract(unittest.TestCase):
    def setUp(self):
        self.runtime = RUNTIME.read_text("utf-8")
        self.stability = STABILITY.read_text("utf-8")
        self.equivalence = EQUIVALENCE.read_text("utf-8")
        self.coordinator = COORDINATOR.read_text("utf-8")
        self.codec = CODEC.read_text("utf-8")
        self.learning_ui = LEARNING_UI.read_text("utf-8")
        self.live_budget = LIVE_BUDGET.read_text("utf-8")

    def test_visit_and_first_comparison_are_immutable_and_use_physical_time(self):
        self.assertIn("private fun immutableByVisit", self.runtime)
        self.assertIn("current.any { it.visitId == incoming.visitId }", self.runtime)
        self.assertNotIn("incoming.quality > existing.quality", self.runtime)
        self.assertIn("createdAtMs = cng.collectedAtMs", self.equivalence)
        self.assertIn("alreadyCompared", self.equivalence)
        self.assertIn("filterNot { it.visitId in alreadyCompared }", self.equivalence)

    def test_native_stability_has_consolidated_and_revalidation_states(self):
        for marker in (
            "NO_EVIDENCE",
            "LEARNING",
            "CONSOLIDATED",
            "REVALIDATING",
            "confirmedVisits",
            "directionConsensusMinimum",
            "comparisonMaximumMadMs",
            "equivalenceDeadbandPercent",
        ):
            self.assertIn(marker, self.stability)
        self.assertIn("sortedWith(compareBy<Observation> { it.collectedAtMs }.thenBy { it.visitId })", self.stability)

    def test_suggestions_are_stable_by_generation_and_blocked_while_revalidating(self):
        for marker in (
            "stabilityGeneration",
            "stabilityState",
            "consolidatedErrorPercent",
            "recentErrorPercent",
            "existing.stabilityGeneration == stability.generation",
        ):
            self.assertIn(marker, self.runtime)
        self.assertIn("lifecycle = SuggestionLifecycleV7.OBSERVING", self.runtime)
        self.assertIn("LearningStabilityStateV7.REVALIDATING", self.runtime)
        self.assertIn("stability.rpmBandCount >= 2", self.runtime)
        self.assertIn("stability.mapBandCount >= 2", self.runtime)

    def test_schema_is_backward_compatible_and_persists_stability(self):
        self.assertIn('private const val SCHEMA = "OMEGAS_V7_SESSION_7"', self.codec)
        for schema in (
            "OMEGAS_V7_SESSION_6",
            "OMEGAS_V7_SESSION_5",
            "OMEGAS_V7_SESSION_4",
            "OMEGAS_V7_SESSION_3",
            "OMEGAS_V7_SESSION_2",
        ):
            self.assertIn(schema, self.codec)
        for marker in (
            "item.stabilityGeneration",
            "item.stabilityState",
            "item.consolidatedErrorPercent",
            "item.recentErrorPercent",
        ):
            self.assertIn(marker, self.codec)

    def test_coordinator_exposes_native_stability_without_new_writer(self):
        self.assertIn('put("learningStability", LearningStabilityJsonV7.from(active))', self.coordinator)
        self.assertIn('put("stabilityState", value.stabilityState)', self.coordinator)
        self.assertIn('put("consolidatedErrorPercent"', self.coordinator)
        self.assertIn('put("recentErrorPercent"', self.coordinator)

    def test_learning_ui_prefers_consolidated_and_keeps_recent_as_detail(self):
        self.assertIn("state.calibrationState?.learningStability?.map", self.learning_ui)
        self.assertIn("consolidatedErrorPercent", self.learning_ui)
        self.assertIn("recentErrorPercent", self.learning_ui)
        self.assertIn("persistentMapSuggestions(state)", self.learning_ui)
        self.assertIn("revalidando", self.learning_ui.lower())
        self.assertIn("Editar esta célula", self.learning_ui)
        self.assertNotIn(".setTrace(", self.learning_ui)

    def test_live_tracing_visual_remains_removed(self):
        self.assertIn("aprendizado rapido nao persegue pesos bilineares no DOM", self.live_budget)
        self.assertIn("doesNotMatch(appSource, /\\.setTrace", self.live_budget)
        self.assertIn("function renderLightLiveContext", self.live_budget)


if __name__ == "__main__":
    unittest.main()
