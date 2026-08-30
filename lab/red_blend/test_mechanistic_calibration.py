import math
import unittest
from pathlib import Path

from lab.red_blend.mechanistic_calibration import (
    CalibrationObservation,
    build_assisted_suggestion,
    decompose_correction,
    simulate_governed_corpus,
)
from lab.red_blend.real_corpus import load_governed_fixture


class MechanisticCalibrationTests(unittest.TestCase):
    def test_photo_equivalence_pairs_define_exact_combined_multiplier(self):
        pairs = (
            (4.19, 3.49, -16.7),
            (4.50, 4.43, -1.7),
            (5.72, 5.14, -10.3),
            (5.21, 5.69, 9.3),
            (5.21, 7.06, 35.6),
        )
        for reference_ms, cng_ms, displayed_percent in pairs:
            observation = CalibrationObservation(
                reference_ms=reference_ms,
                cng_ms=cng_ms,
                rpm=900.0,
                map_bar=0.45,
                session_key="photo",
            )
            self.assertAlmostEqual(
                observation.combined_multiplier,
                cng_ms / reference_ms,
                places=12,
            )
            self.assertAlmostEqual(
                observation.error_percent,
                displayed_percent,
                delta=0.25,
            )

    def test_curve_and_map_components_recompose_without_double_counting(self):
        target = CalibrationObservation(5.0, 4.0, 850.0, 0.40, "s-target")
        support = (
            target,
            CalibrationObservation(5.0, 4.5, 1350.0, 0.50, "s-a"),
            CalibrationObservation(5.0, 4.6, 1850.0, 0.60, "s-b"),
            CalibrationObservation(5.0, 4.4, 2500.0, 0.70, "s-c"),
        )

        result = decompose_correction(target, support, petrol_bandwidth_ms=0.1)

        self.assertEqual(result.global_status, "SUPPORTED")
        self.assertGreaterEqual(result.independent_global_regions, 3)
        self.assertAlmostEqual(
            result.curve_multiplier * result.map_multiplier,
            target.combined_multiplier,
            places=12,
        )
        self.assertAlmostEqual(result.curve_multiplier, 0.90, places=12)
        self.assertAlmostEqual(result.map_multiplier, 0.80 / 0.90, places=12)

    def test_without_transversal_support_correction_remains_local(self):
        target = CalibrationObservation(4.19, 3.49, 888.0, 0.306, "photo-a")

        result = decompose_correction(target, (target,))

        self.assertEqual(result.global_status, "INSUFFICIENT_TRANSVERSAL_SUPPORT")
        self.assertEqual(result.curve_multiplier, 1.0)
        self.assertAlmostEqual(result.map_multiplier, 3.49 / 4.19, places=12)

    def test_assisted_suggestion_projects_known_curve_and_map_state(self):
        target = CalibrationObservation(5.0, 4.5, 850.0, 0.40, "s-target")
        support = (
            target,
            CalibrationObservation(5.0, 4.5, 1350.0, 0.50, "s-a"),
            CalibrationObservation(5.0, 4.5, 1850.0, 0.60, "s-b"),
        )

        suggestion = build_assisted_suggestion(
            target,
            support,
            current_curve_factor=1.60,
            current_map_value=150.0,
            maximum_component_step=0.05,
        )

        self.assertAlmostEqual(suggestion.ideal_effective_multiplier, 2.16, places=12)
        self.assertAlmostEqual(
            suggestion.ideal_curve_factor * suggestion.ideal_map_value / 100.0,
            suggestion.ideal_effective_multiplier,
            places=12,
        )
        self.assertLessEqual(abs(math.log(suggestion.step_curve_factor / 1.60)), 0.05)
        self.assertLessEqual(abs(math.log(suggestion.step_map_value / 150.0)), 0.05)
        self.assertFalse(suggestion.actionable)
        self.assertIsNone(suggestion.p_improve)

    def test_invalid_or_unpaired_observation_fails_closed(self):
        with self.assertRaises(ValueError):
            CalibrationObservation(0.0, 4.0, 850.0, 0.4, "s")
        with self.assertRaises(ValueError):
            CalibrationObservation(4.0, float("nan"), 850.0, 0.4, "s")

    def test_governed_corpus_simulation_is_blind_deterministic_and_non_actionable(self):
        parts = Path("tests/fixtures/science/episodes")
        episodes = load_governed_fixture(parts, parts / "index.json")

        first = simulate_governed_corpus(episodes)
        second = simulate_governed_corpus(list(reversed(episodes)))

        self.assertEqual(first, second)
        self.assertEqual(first.total_gnv_episodes, 1442)
        self.assertEqual(first.supported_pairs, 1273)
        self.assertEqual(first.leakage_violations, 0)
        self.assertEqual(first.independent_gnv_sessions, 17)
        self.assertEqual(
            first.supported_pairs,
            first.curve_supported_pairs + first.local_only_pairs,
        )
        self.assertGreater(first.curve_supported_pairs, 0)
        self.assertAlmostEqual(first.median_combined_multiplier, 0.9500182523092403)
        self.assertIsNone(first.p_improve)
        self.assertFalse(first.actionable)
        self.assertFalse(first.auto_write_ecu)


if __name__ == "__main__":
    unittest.main()
