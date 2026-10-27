import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/prohub/session/SessionRuntime.kt"
STABILITY = ROOT / "app/src/main/java/com/omegas/prohub/session/LearningStability.kt"
EQUIVALENCE = ROOT / "app/src/main/java/com/omegas/prohub/session/SessionEquivalenceEngine.kt"
COORDINATOR = ROOT / "app/src/main/java/com/omegas/prohub/calibration/CalibrationCoordinator.kt"
CODEC = ROOT / "app/src/main/java/com/omegas/prohub/session/SessionSnapshotCodec.kt"


class LearningConsolidationContract(unittest.TestCase):
    def setUp(self):
        self.runtime = RUNTIME.read_text("utf-8")
        self.stability = STABILITY.read_text("utf-8")
        self.equivalence = EQUIVALENCE.read_text("utf-8")
        self.coordinator = COORDINATOR.read_text("utf-8")
        self.codec = CODEC.read_text("utf-8")

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
        self.assertIn("lifecycle = SuggestionLifecycle.OBSERVING", self.runtime)
        self.assertIn("LearningStabilityState.REVALIDATING", self.runtime)
        self.assertIn("stability.rpmBandCount >= 2", self.runtime)
        self.assertIn("stability.mapBandCount >= 2", self.runtime)

    def test_schema_is_backward_compatible_and_persists_stability(self):
        self.assertIn('private const val SCHEMA = "OMEGAS_V7_SESSION_6"', self.codec)
        for schema in (
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
        self.assertIn('put("learningStability", LearningStabilityJson.from(active))', self.coordinator)
        self.assertIn('put("stabilityState", value.stabilityState)', self.coordinator)
        self.assertIn('put("consolidatedErrorPercent"', self.coordinator)
        self.assertIn('put("recentErrorPercent"', self.coordinator)


if __name__ == "__main__":
    unittest.main()
