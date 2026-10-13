import unittest

from rpm_residual_replay import Pair
import rpm_residual_models as models


class RpmResidualModelsTest(unittest.TestCase):
    def test_whole_first_session_is_learned_before_second_is_predicted(self):
        pairs = []
        for i in range(20):
            pairs.append(Pair("session_1_car", 0, i, 850, 0.40, 5.0, 5.0))
        for i in range(10):
            pairs.append(Pair("session_2_car", 0, 100 + i, 850, 0.40, 5.0, 5.5))
        result = models.evaluate_pairs(pairs, include_river=False)
        self.assertEqual(["session_2_car"], sorted({row["session"] for row in result["per_session"]}))
        self.assertEqual(10, result["evaluated_pairs"])

    def test_rpm_bin_uses_only_matching_regime(self):
        training = [
            Pair("s1_car", 0, i, 850, 0.40, 5.0, 5.5) for i in range(10)
        ] + [
            Pair("s1_car", 0, 20 + i, 2500, 0.40, 5.0, 5.0) for i in range(20)
        ]
        query = Pair("s2_car", 0, 100, 850, 0.40, 5.0, 5.4)
        global_prediction = models.local_median_prediction(training, query, rpm_local=False)
        rpm_prediction = models.local_median_prediction(training, query, rpm_local=True)
        self.assertGreater(rpm_prediction, global_prediction)


if __name__ == "__main__":
    unittest.main()
