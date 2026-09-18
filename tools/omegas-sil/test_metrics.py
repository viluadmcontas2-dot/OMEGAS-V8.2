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
    def test_exact_plus_and_minus_four_percent_are_acceptable(self):
        self.assertAlmostEqual(4.0, relative_error_pct(10.0, 10.4), places=9)
        self.assertAlmostEqual(4.0, relative_error_pct(10.0, 9.6), places=9)
        self.assertTrue(within_tolerance(10.0, 10.4))
        self.assertTrue(within_tolerance(10.0, 9.6))

    def test_just_outside_four_percent_is_not_acceptable(self):
        self.assertFalse(within_tolerance(10.0, 10.4001))
        self.assertFalse(within_tolerance(10.0, 9.5999))

    def test_invalid_observed_values_do_not_become_successes(self):
        self.assertTrue(math.isnan(relative_error_pct(0.0, 1.0)))
        self.assertFalse(within_tolerance(0.0, 1.0))

    def test_zero_correction_is_the_optimization_target(self):
        self.assertAlmostEqual(0.0, correction_pct(10.0, 10.0), places=9)
        self.assertAlmostEqual(4.0, correction_pct(10.0, 10.4), places=9)
        self.assertAlmostEqual(-5.0, correction_pct(10.0, 9.5), places=9)

    def test_summary_tracks_preferred_35_and_acceptable_4_percent_bands(self):
        summary = summarize_reference_errors([
            (10.0, 10.35),  # +3.5%
            (10.0, 9.60),   # -4.0%
            (10.0, 10.8),   # +8.0%
            (20.0, 19.8),   # -1.0%
        ])
        self.assertEqual(4, summary["count"])
        self.assertEqual(3, summary["within_tolerance"])
        self.assertAlmostEqual(0.75, summary["within_tolerance_rate"], places=9)
        self.assertEqual(2, summary["within_preferred"])
        self.assertAlmostEqual(0.50, summary["within_preferred_rate"], places=9)
        self.assertAlmostEqual(4.125, summary["mean_abs_correction_pct"], places=9)
        self.assertAlmostEqual(1.625, summary["mean_signed_correction_pct"], places=9)
        self.assertAlmostEqual(0.0, summary["target_correction_pct"], places=9)
        self.assertIn("median_abs_correction_pct", summary)
        self.assertIn("p90_abs_correction_pct", summary)
        self.assertIn("p99_abs_correction_pct", summary)
        self.assertIn("mae_ms", summary)


if __name__ == "__main__":
    unittest.main()
