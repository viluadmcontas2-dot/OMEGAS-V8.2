from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt"


class RedSciencePublicationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = STORE.read_text(encoding="utf-8")

    def test_store_owns_one_science_publication_gate(self):
        self.assertIn("private val sciencePublicationGate = SciencePublicationGate()", self.source)
        self.assertNotIn("lastRepresentedWindowEndByFuel", self.source)

    def test_coalesced_windows_do_not_mutate_learning_memory(self):
        self.assertIn('"SCIENCE_PUBLICATION_COALESCED"', self.source)
        self.assertIn("sample = null", self.source)
        self.assertIn("learningEligible = false", self.source)

    def test_only_published_science_requests_sidecar_persistence(self):
        self.assertIn("if (prepared.sample != null) persistEvidenceState()", self.source)
        self.assertNotIn("if (source != null) persistEvidenceState()", self.source)

    def test_physical_boundaries_reset_publication_history(self):
        self.assertIn("sciencePublicationGate.reset()", self.source)
        for marker in ("ENGINE_OFF", "CUTOFF", "FUEL_TRANSITION", "FUEL_STABLE", "TELEMETRY_GAP", "WINDOW_TIMEOUT"):
            self.assertIn(f'"{marker}"', self.source)


if __name__ == "__main__":
    unittest.main()
