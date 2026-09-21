import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-point-context-v1.json")


class AutoCalPointContextEvidenceTest(unittest.TestCase):
    def test_point_activity_is_not_equivalent_to_zone_completion(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        conclusions = data["conclusions"]
        self.assertEqual(conclusions["gas_point_changes_can_occur_while_zone_flags_are_zero"], "PROVEN")
        self.assertEqual(conclusions["num_buf_upd_gas_exposes_finer_grained_activity_than_zone_flags"], "PROVEN_PARTIAL")
        self.assertEqual(conclusions["gas_point_change_requires_automatch_transition"], "FALSIFIED")
        self.assertEqual(conclusions["zone_flag_is_percent_complete"], "FALSIFIED")
        self.assertEqual(conclusions["firmware_threshold_from_counter_to_zone_flag"], "UNKNOWN")

    def test_ui_contract_keeps_points_visible_before_zone_flags(self):
        contract = json.loads(FIXTURE.read_text(encoding="utf-8"))["ui_contract"]
        self.assertTrue(contract["render_current_native_points_before_zone_flag_completion"])
        self.assertTrue(contract["use_zone_flags_as_context_not_progress_percent"])
        self.assertTrue(contract["do_not_gate_point_visibility_on_zone_flag"])
        self.assertTrue(contract["do_not_claim_counter_threshold_without_firmware_proof"])

    def test_dual_capture_contains_zero_zone_point_changes(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        for capture in ("PortmonAUTOCAL", "PortmonLOGNOVO"):
            examples = data["observations"][capture]["examples"]
            self.assertTrue(any(
                example.get("acquired_zones_gas") == [0, 0, 0, 0]
                and bool(example.get("changed_indices"))
                for example in examples
            ))


if __name__ == "__main__":
    unittest.main()
