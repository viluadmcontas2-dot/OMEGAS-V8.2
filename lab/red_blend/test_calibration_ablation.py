from pathlib import Path
import unittest

from lab.red_blend.calibration_ablation import audit_calibration_ablation_readiness
from lab.red_blend.calibration_chronology import audit_clock_bridge
from lab.red_blend.causal_science import load_adjustment_fixture
from lab.red_blend.real_corpus import load_governed_fixture


PARTS = Path("tests/fixtures/science/episodes")
INDEX = PARTS / "index.json"
MANIFEST = Path("tests/fixtures/science/omegas_corpus_20260828_manifest.json")
K_HISTORY = Path("tests/fixtures/science/k_history/confirmed_map_k_20260818.json")
CORPUS_REPORT = Path("tests/fixtures/science/omegas_corpus_20260828_report.json")


class CalibrationAblationTest(unittest.TestCase):
    def test_real_ablation_defers_and_preserves_red_when_states_are_not_explicit(self):
        episodes = load_governed_fixture(PARTS, INDEX)
        fixture = load_adjustment_fixture(K_HISTORY)
        clock = audit_clock_bridge(MANIFEST, episodes, fixture)
        audit = audit_calibration_ablation_readiness(
            CORPUS_REPORT, episodes, fixture, clock
        )

        self.assertEqual("DEFER_INSUFFICIENT_EXPLICIT_CALIBRATION_STATES", audit.status)
        self.assertEqual(1, audit.apparent_post_map_states)
        self.assertEqual(3, audit.apparent_post_sessions)
        self.assertEqual(549, audit.apparent_post_episodes)
        self.assertEqual(0, audit.explicit_map_states)
        self.assertEqual(0, audit.explicit_curve_states)
        self.assertEqual(0, audit.candidate_supported)
        self.assertAlmostEqual(0.01252769477538073, audit.red_median_error)
        self.assertAlmostEqual(0.05406177103407854, audit.red_p90_error)
        self.assertAlmostEqual(0.0801364710411562, audit.red_p95_error)
        self.assertIsNone(audit.candidate_median_error)
        self.assertFalse(audit.promotion_allowed)
        self.assertTrue(audit.red_fallback_preserved)
        self.assertFalse(audit.android_changed)


if __name__ == "__main__":
    unittest.main()
