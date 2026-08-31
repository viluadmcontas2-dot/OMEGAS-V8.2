from pathlib import Path
import unittest

from lab.red_blend.calibration_chronology import audit_clock_bridge
from lab.red_blend.causal_science import load_adjustment_fixture
from lab.red_blend.real_corpus import load_governed_fixture


EPISODE_PARTS = Path("tests/fixtures/science/episodes")
EPISODE_INDEX = EPISODE_PARTS / "index.json"
MANIFEST = Path("tests/fixtures/science/omegas_corpus_20260828_manifest.json")
K_HISTORY = Path("tests/fixtures/science/k_history/confirmed_map_k_20260818.json")


class CalibrationChronologyTest(unittest.TestCase):
    def test_real_derivatives_detect_alignment_but_fail_closed_without_clock_provenance(self):
        episodes = load_governed_fixture(EPISODE_PARTS, EPISODE_INDEX)
        fixture = load_adjustment_fixture(K_HISTORY)
        audit = audit_clock_bridge(MANIFEST, episodes, fixture)

        self.assertEqual("DEFER_CLOCK_PROVENANCE_MISSING", audit.status)
        self.assertEqual(1708, audit.episode_count)
        self.assertEqual(11, audit.intervention_count)
        self.assertEqual(15, audit.pre_session_count)
        self.assertEqual(3, audit.post_session_count)
        self.assertTrue(audit.interventions_inside_observed_gap)
        self.assertTrue(audit.session_episode_invariants_proven)
        self.assertFalse(audit.common_clock_proven)
        self.assertFalse(audit.actionable)

    def test_declared_labels_cannot_replace_matching_provenance_contract(self):
        episodes = load_governed_fixture(EPISODE_PARTS, EPISODE_INDEX)
        fixture = load_adjustment_fixture(K_HISTORY)
        audit = audit_clock_bridge(
            MANIFEST,
            episodes,
            fixture,
            declared_clock_domain="UNIX_EPOCH_MS",
        )
        self.assertEqual("DEFER_CLOCK_PROVENANCE_MISSING", audit.status)
        self.assertFalse(audit.common_clock_proven)


if __name__ == "__main__":
    unittest.main()
