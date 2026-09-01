import math
from pathlib import Path
import unittest

from lab.red_blend.real_corpus import load_governed_fixture
from lab.red_blend.walk_forward import compare_gasoline_walk_forward, predict_pooled_gaussian, predict_session_balanced_gaussian

def ep(session, order, rpm, map_bar, petrol_ms, start_ms=0):
    return {
        "session_key": session, "order": order, "fuel": "GASOLINA",
        "rpm": float(rpm), "map_bar": float(map_bar), "petrol_ms": float(petrol_ms),
        "rpm_bin": int(rpm // 160), "map_bin": int(map_bar // 0.04),
        "start_ms": int(start_ms),
    }

class BlindWalkForwardTest(unittest.TestCase):
    def test_pooled_gaussian_preserves_dense_exact_local_evidence(self):
        training = [ep("dense", 0, 3200, 0.55, 5.00, i) for i in range(200)]
        training += [ep("sparse-a", 1, 3200, 0.55, 5.80, 1000), ep("sparse-b", 2, 3200, 0.55, 5.80, 2000)]
        target = ep("future", 3, 3200, 0.55, 5.00, 3000)
        pooled = predict_pooled_gaussian(training, target)
        balanced = predict_session_balanced_gaussian(training, target)
        self.assertIsNotNone(pooled)
        self.assertIsNotNone(balanced)
        self.assertLess(abs(pooled.predicted_ms - 5.00), abs(balanced.predicted_ms - 5.00))
        self.assertGreater(pooled.raw_support_count, balanced.independent_session_count)

    def test_session_balanced_prediction_is_invariant_to_duplicate_density_within_session(self):
        base = [ep("s0", 0, 1800, 0.50, 4.0), ep("s1", 1, 1800, 0.50, 4.4)]
        duplicated = [ep("s0", 0, 1800, 0.50, 4.0, i) for i in range(100)] + [ep("s1", 1, 1800, 0.50, 4.4)]
        target = ep("future", 2, 1800, 0.50, 4.2)
        p1 = predict_session_balanced_gaussian(base, target)
        p2 = predict_session_balanced_gaussian(duplicated, target)
        self.assertAlmostEqual(p1.predicted_ms, p2.predicted_ms, places=12)
        self.assertEqual(p1.independent_session_count, p2.independent_session_count)
        self.assertGreater(p2.raw_support_count, p1.raw_support_count)

    def test_future_data_cannot_change_prediction_for_earlier_target(self):
        training = [ep("s0", 0, 2200, 0.60, 6.0), ep("s1", 1, 2200, 0.60, 6.1)]
        target = ep("target", 2, 2200, 0.60, 6.05)
        future = ep("future", 99, 2200, 0.60, 20.0)
        before = predict_pooled_gaussian(training, target)
        after = predict_pooled_gaussian(training + [future], target)
        self.assertAlmostEqual(before.predicted_ms, after.predicted_ms, places=12)
        self.assertEqual(before.max_training_order, 1)
        self.assertEqual(after.max_training_order, 1)

    def test_real_fixture_walk_forward_is_blind_deterministic_and_reports_all_candidates(self):
        episodes = load_governed_fixture(Path("tests/fixtures/science/episodes"), Path("tests/fixtures/science/episodes/index.json"))
        r1 = compare_gasoline_walk_forward(episodes)
        r2 = compare_gasoline_walk_forward(list(reversed(episodes)))
        self.assertEqual(r1, r2)
        self.assertGreaterEqual(r1.tested_future_episodes, 200)
        self.assertEqual(r1.leakage_violations, 0)
        self.assertIn("wu006_neighbor_baseline", r1.metrics)
        self.assertIn("pooled_gaussian", r1.metrics)
        self.assertIn("session_balanced_gaussian", r1.metrics)
        baseline = r1.metrics["wu006_neighbor_baseline"]
        self.assertGreater(baseline.coverage, 0.80)
        self.assertLess(baseline.coverage, 0.95)
        self.assertLess(baseline.median_abs_relative_error, 0.03)
        for metric in r1.metrics.values():
            self.assertGreater(metric.supported, 0)
            self.assertTrue(math.isfinite(metric.median_abs_relative_error))
            self.assertTrue(math.isfinite(metric.p90_abs_relative_error))
            self.assertTrue(math.isfinite(metric.p95_abs_relative_error))

if __name__ == "__main__":
    unittest.main()
