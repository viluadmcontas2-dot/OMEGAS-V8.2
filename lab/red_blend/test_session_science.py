import random
import unittest

from lab.red_blend.session_science import (
    attribute_session_mixture,
    decompose_sessions,
    leave_one_session_out,
)


def make_sessions(means, sigma=0.03, n=80, seed=1):
    rng = random.Random(seed)
    return {
        f"s{i}": [rng.gauss(mean, sigma) for _ in range(n)]
        for i, mean in enumerate(means)
    }


class SessionScienceTest(unittest.TestCase):
    def test_same_mean_sessions_have_low_between_session_drift_and_small_loso(self):
        groups = make_sessions([4.0] * 6, sigma=0.04, n=80, seed=11)
        decomposition = decompose_sessions(groups)
        loso = leave_one_session_out(groups)
        self.assertEqual(decomposition.session_count, 6)
        self.assertEqual(decomposition.total_count, 480)
        self.assertLess(decomposition.icc, 0.15)
        self.assertLess(loso.median_abs_relative_error, 0.01)
        self.assertLess(loso.p90_abs_relative_error, 0.015)

    def test_shifted_tight_sessions_expose_between_session_drift_and_loso_penalty(self):
        stable = make_sessions([4.0] * 5, sigma=0.02, n=100, seed=22)
        shifted = make_sessions([3.80, 3.90, 4.00, 4.10, 4.20], sigma=0.02, n=100, seed=22)
        stable_d = decompose_sessions(stable)
        shifted_d = decompose_sessions(shifted)
        stable_loso = leave_one_session_out(stable)
        shifted_loso = leave_one_session_out(shifted)
        self.assertGreater(shifted_d.between_session_variance, shifted_d.within_variance)
        self.assertGreater(shifted_d.icc, 0.80)
        self.assertGreater(shifted_loso.median_abs_relative_error, 0.01)
        self.assertGreater(
            shifted_loso.median_abs_relative_error,
            stable_loso.median_abs_relative_error * 4.0,
        )
        self.assertGreater(shifted_d.between_session_variance, stable_d.between_session_variance)

    def test_session_offset_pseudobimodality_collapses_after_session_centering(self):
        groups = make_sessions([3.80, 3.80, 3.80, 4.20, 4.20, 4.20], sigma=0.025, n=100, seed=33)
        attribution = attribute_session_mixture(groups)
        self.assertTrue(attribution.pooled.is_multimodal)
        self.assertFalse(attribution.session_centered.is_multimodal)
        self.assertGreater(attribution.bic_gain_drop, 20.0)
        self.assertEqual(attribution.interpretation, "SESSION_OFFSETS_DOMINANT_CANDIDATE")

    def test_true_within_session_bimodality_survives_session_centering(self):
        rng = random.Random(44)
        groups = {}
        for i in range(6):
            groups[f"s{i}"] = (
                [rng.gauss(3.80, 0.02) for _ in range(60)]
                + [rng.gauss(4.20, 0.02) for _ in range(60)]
            )
        attribution = attribute_session_mixture(groups)
        self.assertTrue(attribution.pooled.is_multimodal)
        self.assertTrue(attribution.session_centered.is_multimodal)
        self.assertEqual(attribution.interpretation, "WITHIN_SESSION_REGIME_CANDIDATE")

    def test_loso_requires_three_independent_sessions(self):
        groups = make_sessions([4.0, 4.1], sigma=0.02, n=20, seed=55)
        with self.assertRaisesRegex(ValueError, "at least three independent sessions"):
            leave_one_session_out(groups)

    def test_decomposition_is_order_deterministic(self):
        groups = make_sessions([3.9, 4.0, 4.1, 4.0], sigma=0.03, n=40, seed=66)
        reversed_groups = dict(reversed(list(groups.items())))
        self.assertEqual(decompose_sessions(groups), decompose_sessions(reversed_groups))
        self.assertEqual(leave_one_session_out(groups), leave_one_session_out(reversed_groups))
        self.assertEqual(attribute_session_mixture(groups), attribute_session_mixture(reversed_groups))


if __name__ == "__main__":
    unittest.main()
