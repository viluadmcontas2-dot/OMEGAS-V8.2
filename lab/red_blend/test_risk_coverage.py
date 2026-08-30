import unittest

from lab.red_blend.risk_coverage import RiskObservation, empirical_risk_coverage_curve


class RiskCoverageTest(unittest.TestCase):
    def test_curve_is_deterministic_and_orders_by_predeclared_risk_score(self):
        observations = [
            RiskObservation(order=4, risk_score=0.30, abs_relative_error=0.12),
            RiskObservation(order=2, risk_score=0.10, abs_relative_error=0.02),
            RiskObservation(order=3, risk_score=0.20, abs_relative_error=0.05),
            RiskObservation(order=5, risk_score=0.40, abs_relative_error=0.20),
        ]
        first = empirical_risk_coverage_curve(observations)
        second = empirical_risk_coverage_curve(list(reversed(observations)))
        self.assertEqual(first, second)
        self.assertEqual([0.25, 0.50, 0.75, 1.00], [point.coverage for point in first.points])
        self.assertEqual([0.10, 0.20, 0.30, 0.40], [point.max_risk_score for point in first.points])

    def test_lower_risk_prefix_can_have_lower_empirical_tail_without_claiming_probability(self):
        observations = [
            RiskObservation(order=1, risk_score=0.01, abs_relative_error=0.01),
            RiskObservation(order=2, risk_score=0.02, abs_relative_error=0.02),
            RiskObservation(order=3, risk_score=0.20, abs_relative_error=0.10),
            RiskObservation(order=4, risk_score=0.30, abs_relative_error=0.20),
        ]
        curve = empirical_risk_coverage_curve(observations)
        self.assertLess(curve.points[1].p90_abs_relative_error, curve.points[-1].p90_abs_relative_error)
        self.assertIsNone(curve.p_improve)
        self.assertFalse(curve.actionable)
        self.assertEqual("EMPIRICAL_RISK_COVERAGE_ONLY", curve.claim_scope)

    def test_nonfinite_or_negative_inputs_fail_closed(self):
        with self.assertRaises(ValueError):
            empirical_risk_coverage_curve([
                RiskObservation(order=1, risk_score=-0.1, abs_relative_error=0.02)
            ])


if __name__ == "__main__":
    unittest.main()
