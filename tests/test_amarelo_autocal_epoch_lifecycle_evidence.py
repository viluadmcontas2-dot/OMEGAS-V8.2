import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-epoch-lifecycle-v1.json")


class AutoCalEpochLifecycleEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_every_measurable_epoch_changes_all_mul_act_nodes(self):
        life = self.data["observed_readback_lifecycle"]
        for capture in ("PortmonAUTOCAL", "PortmonLOGNOVO"):
            for row in life[capture]:
                self.assertEqual(row["mul_act_changed_nodes"], 30)

    def test_every_measurable_epoch_has_prev_and_counter_readback_changes(self):
        life = self.data["observed_readback_lifecycle"]
        for capture in ("PortmonAUTOCAL", "PortmonLOGNOVO"):
            for row in life[capture]:
                self.assertIsNotNone(row["prev_bulk_delta_ms"])
                self.assertIsNotNone(row["counter_reseed_delta_ms"])
                self.assertIsNotNone(row["mul_act_delta_ms"])

    def test_observed_readback_order_is_not_promoted_to_firmware_instruction_order(self):
        conclusions = self.data["conclusions"]
        self.assertEqual(conclusions["every_measurable_epoch_has_prev_bulk_replacement"], "PROVEN_DUAL_CAPTURE")
        self.assertEqual(conclusions["every_measurable_epoch_has_counter_reset_or_reseed"], "PROVEN_DUAL_CAPTURE")
        self.assertEqual(conclusions["every_measurable_epoch_has_full_mul_act_vector_change"], "PROVEN_DUAL_CAPTURE")
        self.assertEqual(conclusions["readback_order_repeats"], "PROVEN_OBSERVED_READBACK_ORDER")
        self.assertEqual(conclusions["firmware_instruction_order"], "UNKNOWN_DUE_ASYNC_POLLING")
        self.assertEqual(conclusions["exact_native_automatch_formula"], "UNKNOWN")

    def test_product_keeps_ecu_as_epoch_authority(self):
        contract = self.data["product_contract"]
        self.assertTrue(contract["ecu_epoch_counter_is_authority"])
        self.assertTrue(contract["render_current_and_previous_epoch_as_distinct_layers"])
        self.assertTrue(contract["show_curve_k_change_as_native_epoch_event"])
        self.assertTrue(contract["do_not_claim_host_computed_adjustment"])
        self.assertTrue(contract["after_epoch_transition_require_fresh_native_readback"])


if __name__ == "__main__":
    unittest.main()
