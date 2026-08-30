from pathlib import Path
import unittest

from lab.red_blend.geometric_field import (
    GeometricPolicy,
    evaluate_nested_geometric_field,
    predict_local_affine,
)
from lab.red_blend.real_corpus import load_governed_fixture


def ep(session, order, rpm, map_bar, petrol_ms, window_count=1):
    return {
        "session_key": session,
        "order": order,
        "fuel": "GASOLINA",
        "rpm": float(rpm),
        "map_bar": float(map_bar),
        "petrol_ms": float(petrol_ms),
        "start_ms": 0,
        "window_count": int(window_count),
    }


class GeometricFieldTest(unittest.TestCase):
    def test_local_plane_recovers_physical_slopes_and_preserves_two_kinds_of_support(self):
        # Tinj = 2 + 0.001*RPM + 4*MAP.
        training = [
            ep("dense", 0, 800, 0.30, 4.00, 100),
            ep("dense", 1, 960, 0.30, 4.16, 50),
            ep("other", 2, 800, 0.34, 4.16, 25),
            ep("other", 3, 960, 0.34, 4.32, 10),
        ]
        target = ep("future", 4, 880, 0.32, 4.16)

        result = predict_local_affine(
            training,
            target,
            GeometricPolicy(radius=4.0, nearest=16, ridge=1e-12),
        )

        self.assertIsNotNone(result)
        self.assertAlmostEqual(result.predicted_ms, 4.16, places=6)
        self.assertAlmostEqual(result.slope_ms_per_rpm, 0.001, places=6)
        self.assertAlmostEqual(result.slope_ms_per_map_bar, 4.0, places=6)
        self.assertEqual(result.raw_support_count, 185)
        self.assertEqual(result.independent_session_count, 2)
        self.assertLess(result.max_training_order, target["order"])

    def test_future_or_same_order_evidence_is_never_used(self):
        training = [
            ep("past", 0, 800, 0.30, 4.0),
            ep("past", 1, 960, 0.30, 4.2),
            ep("past", 2, 800, 0.34, 4.2),
            ep("future", 5, 880, 0.32, 99.0, 1000),
        ]
        target = ep("target", 5, 880, 0.32, 4.2)

        result = predict_local_affine(training, target, GeometricPolicy(radius=4.0, nearest=16, ridge=1e-6))

        self.assertIsNotNone(result)
        self.assertLess(result.predicted_ms, 10.0)
        self.assertEqual(result.max_training_order, 2)

    def test_real_nested_gate_is_deterministic_leak_free_and_fail_closed(self):
        parts = Path("tests/fixtures/science/episodes")
        episodes = load_governed_fixture(parts, parts / "index.json")

        first = evaluate_nested_geometric_field(episodes)
        second = evaluate_nested_geometric_field(list(reversed(episodes)))

        self.assertEqual(first, second)
        self.assertEqual(first.leakage_violations, 0)
        self.assertGreater(first.holdout_target_count, 20)
        self.assertIn(first.decision, {"PROMOTE", "DEFER"})
        if first.decision == "PROMOTE":
            self.assertLessEqual(first.candidate_common.median_abs_relative_error, first.anchor_common.median_abs_relative_error)
            self.assertLessEqual(first.candidate_common.p90_abs_relative_error, first.anchor_common.p90_abs_relative_error)
            self.assertLessEqual(first.candidate_common.p95_abs_relative_error, first.anchor_common.p95_abs_relative_error)
        self.assertFalse(first.auto_write_ecu)


if __name__ == "__main__":
    unittest.main()
