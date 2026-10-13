import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools" / "autocal_refine"))

from contextual_equivalence_replay import ContextPair
import hierarchical_residual_models as models


def pair(session, t, rpm, map_bar, ratio, petrol_ms=5.0):
    return ContextPair(
        "car", 0, session, t, rpm, map_bar, petrol_ms,
        petrol_ms * ratio, 4, 0, 1,
    )


class HierarchicalResidualModelsTest(unittest.TestCase):
    def test_global_curve_removes_uniform_proportional_error(self):
        training = [
            pair("s1", index, rpm, .50, 1.10)
            for rpm in (850, 1850, 3000)
            for index in range(8)
        ]
        query = pair("s2", 100, 1850, .50, 1.10)
        global_error = models.global_prediction(training, query)
        self.assertIsNotNone(global_error)
        self.assertAlmostEqual(0.0, query.error_log - global_error, places=12)

    def test_sparse_local_model_does_not_fill_unobserved_map(self):
        training = [
            pair("s1", visit * 31_000 + sample, 850, .50, 1.10)
            for visit in range(4) for sample in range(2)
        ] + [
            pair("s1", visit * 31_000 + sample, 1850, .50, 1.0)
            for visit in range(4) for sample in range(2)
        ]
        residuals = models.training_residuals(training)
        close, supported = models.sparse_local_prediction(
            residuals, pair("s2", 200_000, 850, .50, 1.0)
        )
        far, far_supported = models.sparse_local_prediction(
            residuals, pair("s2", 200_000, 850, .90, 1.0)
        )
        self.assertTrue(supported)
        self.assertGreater(close, math.log(1.04))
        self.assertFalse(far_supported)
        self.assertEqual(0.0, far)

    def test_river_features_never_include_gnv_tpet_or_target_ratio(self):
        keys = set(models.features(pair("s", 0, 1500, .50, 1.25)))
        self.assertEqual({"rpm_k", "map", "petrol_ref_10ms", "rpm_x_map"}, keys)


if __name__ == "__main__":
    unittest.main()
