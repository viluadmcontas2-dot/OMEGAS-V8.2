#!/usr/bin/env python3
"""Red/green: identificar o sinal do Mapa K, removendo deriva na região controle."""
import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools" / "map_k"))
from map_k_probe import ControlledTrial, estimate_controlled_gain, proportional_target_percent


def trial(idx, pct, beta=0.8, baseline=1.08, drift=1.02, **kwargs):
    before, after = 160, round(160 * pct)
    effect = beta * math.log(after / before)
    return ControlledTrial(
        session_id=f"intervencao-{idx}",
        target_before_ratio=baseline,
        target_after_ratio=baseline * drift * math.exp(effect),
        control_before_ratio=1.0,
        control_after_ratio=drift,
        map_raw_before=before, map_raw_after=after, **kwargs
    )


class ControlledTrialsTest(unittest.TestCase):
    def test_opposite_steps_with_negative_control_recover_beta(self):
        observations = [trial(i, p) for i, p in enumerate((1.05, 0.95, 1.06, 0.94))]
        gain = estimate_controlled_gain(observations)
        self.assertIsNotNone(gain)
        self.assertAlmostEqual(gain.elasticity, 0.8, places=7)
        self.assertLess(proportional_target_percent(1.08, gain), 0)

    def test_one_observation_cannot_identify_sensitivity(self):
        self.assertIsNone(estimate_controlled_gain([trial(1, 1.05)]))

    def test_same_sign_steps_rejected_even_with_four_sessions(self):
        self.assertIsNone(estimate_controlled_gain([trial(i, 1.05) for i in range(4)]))

    def test_changed_curve_or_unmatched_state_rejected(self):
        data = [trial(i, p) for i, p in enumerate((1.05, 0.95, 1.06, 0.94))]
        data[2] = trial(2, 1.06, curve_stable=False)
        self.assertIsNone(estimate_controlled_gain(data))
        data[2] = trial(2, 1.06, matched=False)
        self.assertIsNone(estimate_controlled_gain(data))

    def test_outlier_and_disagreeing_sign_rejected_by_heldout(self):
        data = [trial(i, p) for i, p in enumerate((1.05, 0.95, 1.06, 0.94))]
        data[3] = trial(3, 0.94, beta=-0.8)
        self.assertIsNone(estimate_controlled_gain(data))

    def test_insufficient_distinct_sessions_rejected(self):
        data = [trial(1, p) for p in (1.05, 0.95, 1.06, 0.94)]
        self.assertIsNone(estimate_controlled_gain(data))


if __name__ == "__main__":
    unittest.main()
