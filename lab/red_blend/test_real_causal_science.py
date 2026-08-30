from pathlib import Path
import unittest

from lab.red_blend.causal_science import audit_real_causal_support

EPISODE_PARTS = Path("tests/fixtures/science/episodes")
EPISODE_INDEX = EPISODE_PARTS / "index.json"
K_HISTORY = Path("tests/fixtures/science/k_history/confirmed_map_k_20260818.json")


class RealCausalScienceTest(unittest.TestCase):
    def test_real_causal_audit_fails_closed_without_proven_common_timebase(self):
        audit = audit_real_causal_support(
            EPISODE_PARTS,
            EPISODE_INDEX,
            K_HISTORY,
        )
        self.assertEqual(133, audit.cell_event_count)
        self.assertEqual(11, audit.intervention_count)
        self.assertEqual(0, audit.leakage_violations)
        self.assertEqual(0, audit.comparable_interventions)
        self.assertEqual(11, audit.abstentions)
        self.assertEqual("INSUFFICIENT_CAUSAL_OUTCOME_SUPPORT", audit.status)
        self.assertEqual("UNPROVEN_COMMON_TIMEBASE", audit.reason)
        self.assertIsNone(audit.p_improve)
        self.assertFalse(audit.actionable)

    def test_real_causal_audit_is_deterministic(self):
        first = audit_real_causal_support(EPISODE_PARTS, EPISODE_INDEX, K_HISTORY)
        second = audit_real_causal_support(EPISODE_PARTS, EPISODE_INDEX, K_HISTORY)
        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()
