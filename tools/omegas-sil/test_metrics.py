#!/usr/bin/env python3
from __future__ import annotations

import math
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from metrics import correction_pct, relative_error_pct, within_tolerance, summarize_reference_errors


class PracticalToleranceTest(unittest.TestCase):
    def test_exact_plus_and_minus_five_percent_are_acceptable(self):
        self.assertAlmostEqual(5.0, relative_error_pct(10.0, 10.5), places=9)
        self.assertAlmostEqual(5.0, relative_error_pct(10.0, 9.5), places=9)
        self.assertTrue(within_tolerance(10.0, 10.5))
        self.assertTrue(within_tolerance(10.0, 9.5))

    def test_just_outside_five_percent_is_not_acceptable(self):
        self.assertFalse(within_tolerance(10.0, 10.5001))
        self.assertFalse(within_tolerance(10.0, 9.4999))

    def test_invalid_observed_values_do_not_become_successes(self):
        self.assertTrue(math.isnan(relative_error_pct(0.0, 1.0)))
        self.assertFalse(within_tolerance(0.0, 1.0))

    def test_zero_correction_is_the_optimization_target(self):
        self.assertAlmostEqual(0.0, correction_pct(10.0, 10.0), places=9)
        self.assertAlmostEqual(4.0, correction_pct(10.0, 10.4), places=9)
        self.assertAlmostEqual(-5.0, correction_pct(10.0, 9.5), places=9)

    def test_summary_optimizes_toward_zero_while_five_percent_is_only_acceptance(self):
        summary = summarize_reference_errors([
            (10.0, 10.4),   # +4%
            (10.0, 9.5),    # -5%
            (10.0, 10.8),   # +8%
            (20.0, 19.8),   # -1%
        ])
        self.assertEqual(4, summary["count"])
        self.assertEqual(3, summary["within_5pct"])
        self.assertAlmostEqual(0.75, summary["within_5pct_rate"], places=9)
        self.assertAlmostEqual(4.5, summary["mean_abs_correction_pct"], places=9)
        self.assertAlmostEqual(1.5, summary["mean_signed_correction_pct"], places=9)
        self.assertAlmostEqual(0.0, summary["target_correction_pct"], places=9)
        self.assertIn("median_abs_correction_pct", summary)
        self.assertIn("p90_abs_correction_pct", summary)
        self.assertIn("p99_abs_correction_pct", summary)
        self.assertIn("mae_ms", summary)


if __name__ == "__main__":
    unittest.main()
