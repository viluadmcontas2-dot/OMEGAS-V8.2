import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-maturity-visibility-v1.json")


class AutoCalMaturityVisibilityEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_progbase_compares_native_counter_against_native_threshold(self):
        rule = self.data["progbase_static_rule"]
        self.assertEqual(rule["function"], "0x516F64")
        self.assertEqual(rule["comparison"], "threshold <= counter[index]")
        self.assertEqual(rule["status"], "PROVEN_STATIC")
        self.assertEqual(rule["gas"]["low_threshold"], "CALIBRATION_VAL_1[5]@DM+0xC0")
        self.assertEqual(rule["gas"]["normal_threshold"], "CALIBRATION_VAL_1[8]@DM+0xC0")
        self.assertEqual(rule["petrol"]["low_threshold"], "VECT_AUTOCAL_U8_1@DM+0x78 / serial 0x0165:1")
        self.assertEqual(rule["petrol"]["normal_threshold"], "CALIBRATION_VAL_1[2]@DM+0xC0")

    def test_observed_threshold_three_is_capture_evidence_not_product_constant(self):
        wire = self.data["wire_observation"]
        self.assertEqual(wire["CALIBRATION_VAL_1"]["payload"], [1,3,3,1,3,3,1,3,3,1])
        self.assertEqual(wire["CALIBRATION_VAL_1"]["used_indices"]["petrol_normal_2"], 3)
        self.assertEqual(wire["CALIBRATION_VAL_1"]["used_indices"]["gas_low_5"], 3)
        self.assertEqual(wire["CALIBRATION_VAL_1"]["used_indices"]["gas_normal_8"], 3)
        self.assertEqual(wire["VECT_AUTOCAL_U8_1"]["payload"], 3)
        self.assertTrue(self.data["product_contract"]["never_hardcode_observed_3"])

    def test_maturity_index_group_is_not_map_zone(self):
        contract = self.data["product_contract"]
        self.assertEqual(
            contract["maturity_detection_may_use_index_0_to_5_vs_6_to_17"],
            "PROVEN_FOR_THRESHOLD_SELECTION",
        )
        self.assertTrue(contract["map_region_must_not_be_inferred_from_buffer_index"])
        self.assertEqual(
            contract["map_region_source"],
            "native point MAP compared against MNFLD_PRESS_THD geometry",
        )

    def test_zone_flag_and_point_maturity_remain_separate(self):
        separation = self.data["separation"]
        self.assertEqual(separation["gas_point_buffer_can_change_before_zone_flag"], "PROVEN")
        self.assertTrue(separation["acquired_zone_flags_are_separate_ecu_objects"])
        self.assertTrue(separation["mature_polling_presentation_is_host_comparison_over_ecu_values"])


if __name__ == "__main__":
    unittest.main()
