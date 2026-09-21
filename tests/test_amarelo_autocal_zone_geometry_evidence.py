import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-zone-geometry-v1.json")


class AutoCalZoneGeometryEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_progbase_uses_four_ordered_map_regions(self):
        projection = self.data["progbase_projection"]
        self.assertEqual(projection["logical_zone_count"], 4)
        self.assertEqual(projection["internal_cut_indices"], [5, 9, 13])
        self.assertEqual(projection["upper_endpoint_index"], 17)
        self.assertEqual(projection["status"], "PROVEN_STATIC")

    def test_dual_source_values_close_numeric_bounds_without_names(self):
        regions = self.data["observed_regions"]
        self.assertEqual(
            [(r["low_map_bar"], r["high_map_bar"]) for r in regions],
            [(0.0, 0.461), (0.461, 0.666), (0.666, 0.870), (0.870, 1.126)],
        )
        conclusions = self.data["conclusions"]
        self.assertEqual(conclusions["zone_indices_are_ordered_map_regions"], "PROVEN")
        self.assertEqual(conclusions["zone_zero_to_three_are_low_to_high_map_order"], "PROVEN")
        self.assertEqual(conclusions["semantic_names_for_regions"], "UNKNOWN")
        self.assertEqual(conclusions["zone_flags_are_percent_progress"], "FALSIFIED")

    def test_three_lognovo_reads_are_identical(self):
        for key in ("MNFLD_PRESS_THD", "PETR_INJ_TBP"):
            field = self.data["wire"][key]
            self.assertTrue(field["identical_across_three_reads"])
            self.assertEqual(len(field["observed_write_indices"]), 3)


if __name__ == "__main__":
    unittest.main()
