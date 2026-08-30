import unittest

from lab.red_blend.causal_science import CausalResult, RealCausalAudit
from lab.red_blend.p_improve_science import calibrate_p_improve
from lab.red_blend.sensitivity_science import SensitivityResult


class PImproveScienceTest(unittest.TestCase):
    def _blocked_audit(self):
        return RealCausalAudit(
            status="INSUFFICIENT_CAUSAL_OUTCOME_SUPPORT",
            reason="UNPROVEN_COMMON_TIMEBASE",
            reasons=("CLOCK_DOMAIN_UNPROVEN",),
            cell_event_count=133,
            intervention_count=11,
            comparable_interventions=0,
            abstentions=11,
            leakage_violations=0,
            episode_count=1708,
        )

    def _blocked_sensitivity(self):
        return SensitivityResult(
            status="BLOCKED_BY_INSUFFICIENT_CAUSAL_OUTCOME_SUPPORT",
            sensitivity=None,
            independent_effect_count=0,
        )

    def test_p_improve_stays_null_without_governed_causal_outcome_support(self):
        result = calibrate_p_improve(
            self._blocked_audit(),
            self._blocked_sensitivity(),
            held_out_effects=[],
        )
        self.assertEqual("BLOCKED_BY_UNCALIBRATED_CAUSAL_OUTCOMES", result.status)
        self.assertIsNone(result.p_improve)
        self.assertFalse(result.actionable)
        self.assertEqual(0, result.independent_outcome_count)

    def test_synthetic_effects_cannot_substitute_for_governed_held_out_causal_outcomes(self):
        synthetic = CausalResult(
            status="COMPARABLE_EFFECT_ESTIMATE",
            effect_abs_error_delta=-0.10,
            pre_median_abs_error=0.20,
            post_median_abs_error=0.10,
            comparable_pair_count=4,
        )
        result = calibrate_p_improve(
            self._blocked_audit(),
            self._blocked_sensitivity(),
            held_out_effects=[synthetic],
        )
        self.assertIsNone(result.p_improve)
        self.assertFalse(result.actionable)
        self.assertEqual(0, result.independent_outcome_count)


if __name__ == "__main__":
    unittest.main()
