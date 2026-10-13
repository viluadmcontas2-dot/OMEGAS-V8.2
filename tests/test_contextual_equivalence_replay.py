import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools" / "autocal_refine"))

from rpm_residual_replay import Observation
import contextual_equivalence_replay as replay


class ContextualEquivalenceReplayTest(unittest.TestCase):
    def test_order_is_irrelevant(self):
        observations = [
            Observation(100, "GNV", 1500, .40, 5.5, 0, "a_car"),
            Observation(300, "GASOLINA", 1510, .405, 5.0, 0, "b_car"),
        ]
        pairs = replay.symmetric_pairs(observations)
        self.assertEqual(1, len(pairs))
        self.assertTrue(pairs[0].reference_after_gas)
        self.assertAlmostEqual(1.1, pairs[0].ratio)

    def test_matching_uses_rpm_and_map_not_tpet(self):
        observations = [
            Observation(100, "GASOLINA", 1500, .40, 3.0, 0, "a_car"),
            Observation(200, "GASOLINA", 2200, .40, 8.0, 0, "a_car"),
            Observation(300, "GNV", 1510, .40, 7.0, 0, "a_car"),
        ]
        pair = replay.symmetric_pairs(observations)[0]
        self.assertAlmostEqual(3.0, pair.petrol_ref_ms)

    def test_epoch_prevents_cross_calibration_match(self):
        observations = [
            Observation(100, "GASOLINA", 1500, .40, 5.0, 0, "a_car"),
            Observation(200, "GNV", 1500, .40, 5.5, 1, "a_car"),
        ]
        self.assertEqual([], replay.symmetric_pairs(observations))

    def test_rpm_map_model_beats_map_only_when_regimes_repeat_by_session(self):
        observations = []
        for session, offset in (("a_car", 0), ("b_car", 1000)):
            for index in range(10):
                observations.append(Observation(offset + index, "GNV", 900, .50, 5.0, 0, session))
                observations.append(Observation(offset + 100 + index, "GNV", 1900, .50, 3.0, 0, session))
        result = replay.compare_context_models(observations)["GNV"]
        self.assertLess(result["RPM_MAP"]["median_abs_log_percent"], result["MAP"]["median_abs_log_percent"])


if __name__ == "__main__":
    unittest.main()
