from pathlib import Path
import unittest

from lab.red_blend.hybrid_science import (
    HybridRiskPolicy,
    calibrate_disagreement_policy,
    evaluate_nested_hybrid,
    predict_risk_gated_hybrid,
)
from lab.red_blend.real_corpus import load_governed_fixture


def ep(session, order, rpm, map_bar, petrol_ms, start_ms=0, window_count=1):
    return {
        "session_key": session,
        "order": order,
        "fuel": "GASOLINA",
        "rpm": float(rpm),
        "map_bar": float(map_bar),
        "petrol_ms": float(petrol_ms),
        "rpm_bin": int(rpm // 160),
        "map_bin": int(map_bar // 0.04),
        "start_ms": int(start_ms),
        "window_count": int(window_count),
    }


class HybridScienceTest(unittest.TestCase):
    def test_anchor_neighbor_always_wins_when_available(self):
        training = [
            ep("s0", 0, 1800, 0.50, 4.00),
            ep("s1", 1, 1805, 0.501, 4.05),
            ep("s2", 2, 1810, 0.502, 4.10),
        ]
        target = ep("future", 3, 1802, 0.501, 4.04)
        policy = HybridRiskPolicy(min_independent_sessions=3, max_model_disagreement=0.000001)
        prediction = predict_risk_gated_hybrid(training, target, policy)
        self.assertIsNotNone(prediction)
        self.assertEqual(prediction.method, "hybrid_anchor_neighbor")

    def test_fallback_abstains_without_three_independent_sessions(self):
        training = [
            ep("s0", 0, 2200, 0.60, 6.0),
            ep("s1", 1, 2200, 0.60, 6.1),
        ]
        target = ep("future", 2, 2380, 0.64, 6.3)
        policy = HybridRiskPolicy(min_independent_sessions=3, max_model_disagreement=1.0)
        self.assertIsNone(predict_risk_gated_hybrid(training, target, policy))

    def test_fallback_accepts_when_models_agree_and_sessions_are_independent(self):
        training = [
            ep("s0", 0, 2200, 0.60, 6.00, window_count=80),
            ep("s1", 1, 2200, 0.60, 6.04, window_count=40),
            ep("s2", 2, 2200, 0.60, 6.02, window_count=20),
        ]
        target = ep("future", 3, 2380, 0.64, 6.2)
        policy = HybridRiskPolicy(min_independent_sessions=3, max_model_disagreement=0.02)
        prediction = predict_risk_gated_hybrid(training, target, policy)
        self.assertIsNotNone(prediction)
        self.assertEqual(prediction.method, "hybrid_session_gaussian_fallback")
        self.assertEqual(prediction.independent_session_count, 3)

    def test_calibration_uses_only_past_prefix_and_returns_finite_disagreement_limit(self):
        episodes = []
        for order in range(8):
            for s in range(3):
                episodes.append(ep(f"s{s}-{order}", order, 1800 + order * 20, 0.45 + order * 0.005, 4.0 + order * 0.03 + s * 0.01))
        calibration = calibrate_disagreement_policy(episodes, calibration_fraction=0.6, disagreement_quantile=0.90)
        self.assertGreaterEqual(calibration.calibration_target_count, 1)
        self.assertLess(calibration.max_calibration_order, calibration.min_holdout_order)
        self.assertGreaterEqual(calibration.policy.max_model_disagreement, 0.0)
        self.assertLess(calibration.policy.max_model_disagreement, 1.0)

    def test_real_nested_hybrid_is_deterministic_leak_free_and_never_loses_anchor_coverage(self):
        episodes = load_governed_fixture(
            Path("tests/fixtures/science/episodes"),
            Path("tests/fixtures/science/episodes/index.json"),
        )
        r1 = evaluate_nested_hybrid(episodes)
        r2 = evaluate_nested_hybrid(list(reversed(episodes)))
        self.assertEqual(r1, r2)
        self.assertEqual(r1.leakage_violations, 0)
        self.assertGreater(r1.holdout_target_count, 20)
        self.assertGreaterEqual(r1.hybrid_metrics.coverage, r1.anchor_metrics.coverage)
        self.assertGreater(r1.calibration.policy.max_model_disagreement, 0.0)


if __name__ == "__main__":
    unittest.main()
