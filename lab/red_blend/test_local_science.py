import math
import random
import unittest

from lab.red_blend.local_science import (
    MultimodalityPolicy,
    bootstrap_mean_interval,
    detect_multimodality,
    fit_gaussian,
    fit_gmm2,
    summarize_distribution,
)


def gaussian_samples(seed: int, mean: float, sigma: float, n: int) -> list[float]:
    rng = random.Random(seed)
    return [rng.gauss(mean, sigma) for _ in range(n)]


class LocalScienceTest(unittest.TestCase):
    def test_summary_tracks_dense_stable_local_region(self):
        samples = gaussian_samples(11, 2.68, 0.03, 1200)
        summary = summarize_distribution(samples)
        self.assertEqual(summary.count, 1200)
        self.assertAlmostEqual(summary.mean, 2.68, delta=0.006)
        self.assertLess(summary.cv, 0.02)
        self.assertLess(summary.p90 - summary.p10, 0.09)

    def test_seeded_bootstrap_is_reproducible_and_tightens_with_more_samples(self):
        small = gaussian_samples(22, 2.68, 0.05, 40)
        large = gaussian_samples(22, 2.68, 0.05, 800)
        a = bootstrap_mean_interval(small, draws=1200, seed=7, alpha=0.05)
        b = bootstrap_mean_interval(small, draws=1200, seed=7, alpha=0.05)
        large_ci = bootstrap_mean_interval(large, draws=1200, seed=7, alpha=0.05)
        self.assertEqual(a, b)
        self.assertLess(large_ci.high - large_ci.low, a.high - a.low)

    def test_single_gaussian_wins_for_unimodal_region(self):
        samples = gaussian_samples(33, 2.70, 0.035, 800)
        one = fit_gaussian(samples)
        two = fit_gmm2(samples)
        self.assertLess(one.bic, two.bic)
        decision = detect_multimodality(samples, MultimodalityPolicy())
        self.assertFalse(decision.is_multimodal)

    def test_two_component_mixture_wins_for_separated_balanced_modes(self):
        samples = (
            gaussian_samples(44, 2.55, 0.025, 450)
            + gaussian_samples(45, 2.85, 0.030, 450)
        )
        decision = detect_multimodality(samples, MultimodalityPolicy())
        self.assertTrue(decision.is_multimodal)
        self.assertGreater(decision.bic_gain, 10.0)
        self.assertGreater(decision.separation_sigma, 2.5)
        self.assertGreaterEqual(decision.min_component_weight, 0.15)

    def test_tiny_outlier_cluster_does_not_become_second_regime(self):
        samples = (
            gaussian_samples(55, 2.70, 0.035, 970)
            + gaussian_samples(56, 3.10, 0.010, 30)
        )
        decision = detect_multimodality(samples, MultimodalityPolicy(min_component_weight=0.10))
        self.assertFalse(decision.is_multimodal)
        self.assertLess(decision.min_component_weight, 0.10)

    def test_results_are_finite_for_low_variance_region(self):
        samples = [2.7000 + (i % 3) * 1e-7 for i in range(300)]
        one = fit_gaussian(samples)
        two = fit_gmm2(samples)
        self.assertTrue(math.isfinite(one.log_likelihood))
        self.assertTrue(math.isfinite(two.log_likelihood))
        self.assertTrue(math.isfinite(one.bic))
        self.assertTrue(math.isfinite(two.bic))


if __name__ == "__main__":
    unittest.main()
