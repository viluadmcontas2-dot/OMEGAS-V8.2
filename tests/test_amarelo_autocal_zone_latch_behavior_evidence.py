import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-zone-latch-behavior-v1.json")


class AutoCalZoneLatchBehaviorEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_live_map_visit_precedes_every_observed_activation_within_two_windows(self):
        corr = self.data["live_map_activation_correlation"]
        for capture in ("PortmonAUTOCAL", "PortmonLOGNOVO"):
            row = corr[capture]
            self.assertEqual(
                row["same_region_within_two_zone_poll_windows"],
                row["observed_0_to_1_activations"],
            )

    def test_all_observed_falling_edges_are_epoch_clears(self):
        falling = self.data["falling_edge_behavior"]
        for capture in ("PortmonAUTOCAL", "PortmonLOGNOVO"):
            row = falling[capture]
            self.assertEqual(row["falling_events"], 3)
            self.assertTrue(row["all_observed_after_vectors_zero"])
            self.assertTrue(all(v == [0, 0, 0, 0] for v in row["after_vectors"]))

    def test_semantics_do_not_overclaim_firmware_guard(self):
        conclusions = self.data["conclusions"]
        self.assertEqual(
            conclusions["zone_activation_is_associated_with_live_map_visit"],
            "PROVEN_OBSERVED_CORRELATION_DUAL_CAPTURE",
        )
        self.assertEqual(
            conclusions["flags_behave_as_latched_region_markers_within_acquisition_epoch"],
            "STRONGLY_SUPPORTED",
        )
        self.assertEqual(conclusions["live_map_visit_is_sufficient_firmware_set_guard"], "UNKNOWN")
        self.assertEqual(conclusions["exact_firmware_set_guard"], "UNKNOWN")
        self.assertEqual(conclusions["exact_firmware_clear_guard"], "UNKNOWN")
        self.assertEqual(conclusions["acquired_zone_flag_is_maturity_threshold"], "FALSIFIED")
        self.assertEqual(conclusions["acquired_zone_flag_is_percent_progress"], "FALSIFIED")

    def test_product_uses_ecu_flag_as_authority(self):
        contract = self.data["product_contract"]
        self.assertTrue(contract["never_render_as_percentage"])
        self.assertTrue(contract["never_recompute_flag_from_live_map"])
        self.assertTrue(contract["use_ecu_flag_as_authority"])


if __name__ == "__main__":
    unittest.main()


class AutoCalZoneSetGuardBoundaryTest(unittest.TestCase):
    def test_supplied_captures_falsify_simple_host_visible_set_guards(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        boundary = data["set_guard_observation_boundary"]
        self.assertEqual(boundary["PortmonAUTOCAL"]["same_region_current_window_frames"]["min"], 1)
        self.assertGreater(boundary["PortmonAUTOCAL"]["activations_without_current_region_point"], 0)
        self.assertGreater(boundary["PortmonAUTOCAL"]["activations_without_mature_region_point"], 0)
        conclusions = boundary["conclusions"]
        self.assertEqual(conclusions["fixed_live_dwell_guard_visible_to_host"], "FALSIFIED")
        self.assertEqual(conclusions["current_native_point_required_at_observed_activation"], "FALSIFIED")
        self.assertEqual(conclusions["mature_native_point_required_at_observed_activation"], "FALSIFIED")
        self.assertEqual(conclusions["exact_firmware_set_guard_recoverable_from_supplied_polling_cadence"], "NO")
