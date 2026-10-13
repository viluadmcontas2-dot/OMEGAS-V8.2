import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools" / "autocal_refine"))

from rpm_residual_replay import Observation
import contextual_equivalence_replay as replay


class ContextualEquivalenceReplayTest(unittest.TestCase):
    @staticmethod
    def pairs(ratios_by_rpm):
        pairs = []
        for rpm, ratio in ratios_by_rpm.items():
            for visit in range(4):
                for sample in range(2):
                    pairs.append(replay.ContextPair(
                        "car", 0, f"gas_{rpm}", visit * 31_000 + sample,
                        rpm, .50, 5.0, 5.0 * ratio, 4, 0, 1,
                    ))
        return pairs

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

    def test_uniform_proportional_error_belongs_only_to_global_curve(self):
        groups = replay.hierarchical_groups(self.pairs({850: 1.10, 1850: 1.10, 3000: 1.10}))
        self.assertTrue(groups)
        self.assertTrue(all(row["priority"] == "GLOBAL_FIRST" for row in groups))
        self.assertTrue(all(abs(row["local_residual_percent"]) < 1e-9 for row in groups))
        self.assertTrue(all(not row["local_candidate"] for row in groups))

    def test_rpm_map_only_receives_error_left_after_global_curve(self):
        groups = replay.hierarchical_groups(self.pairs({850: 1.10, 1850: 1.0, 3000: 1.0}))
        low = next(row for row in groups if row["rpm_center"] == 850)
        self.assertAlmostEqual(0.0, low["global_error_percent"], places=8)
        self.assertAlmostEqual(10.0, low["local_residual_percent"], places=8)
        self.assertEqual("LOCAL_RESIDUAL", low["priority"])
        self.assertTrue(low["local_candidate"])

    def test_large_global_error_holds_local_candidate_until_curve_is_rechecked(self):
        groups = replay.hierarchical_groups(self.pairs({850: 1.155, 1850: 1.10, 3000: 1.10}))
        low = next(row for row in groups if row["rpm_center"] == 850)
        self.assertGreater(low["local_residual_percent"], 4.9)
        self.assertEqual("GLOBAL_FIRST", low["priority"])
        self.assertFalse(low["local_candidate"])

    def test_single_rpm_region_is_unknown_not_a_local_map(self):
        groups = replay.hierarchical_groups(self.pairs({850: 1.10}))
        self.assertTrue(groups)
        self.assertTrue(all(row["priority"] == "UNKNOWN" for row in groups))
        self.assertTrue(all(not row["local_candidate"] for row in groups))


if __name__ == "__main__":
    unittest.main()
