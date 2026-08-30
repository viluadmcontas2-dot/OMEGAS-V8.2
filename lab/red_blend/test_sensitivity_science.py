import unittest

from lab.red_blend.causal_science import RealCausalAudit
from lab.red_blend.sensitivity_science import calibrate_map_k_sensitivity


class SensitivityScienceTest(unittest.TestCase):
    def test_sensitivity_is_blocked_when_real_causal_outcomes_are_insufficient(self):
        audit = RealCausalAudit(
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
        result = calibrate_map_k_sensitivity(audit, intervention_effects=[])
        self.assertEqual("BLOCKED_BY_INSUFFICIENT_CAUSAL_OUTCOME_SUPPORT", result.status)
        self.assertIsNone(result.sensitivity)
        self.assertIsNone(result.p_improve)
        self.assertFalse(result.actionable)
        self.assertEqual(0, result.independent_effect_count)

    def test_cell_count_can_never_substitute_for_independent_intervention_effects(self):
        audit = RealCausalAudit(
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
        result = calibrate_map_k_sensitivity(audit, intervention_effects=[])
        self.assertNotEqual(133, result.independent_effect_count)
        self.assertNotEqual(11, result.independent_effect_count)


if __name__ == "__main__":
    unittest.main()
