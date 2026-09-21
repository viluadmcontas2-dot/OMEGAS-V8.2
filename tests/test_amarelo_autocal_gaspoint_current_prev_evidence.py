import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-gaspoint-current-prev-v1.json")


class AutoCalGasPointCurrentPrevEvidenceTest(unittest.TestCase):
    def test_dual_capture_proves_separate_native_current_and_previous_buffers(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.assertEqual(data["schema"], "omegas.amarelo.autocal-gaspoint-current-prev-v1")
        conclusions = data["conclusions"]
        self.assertEqual(conclusions["separate_native_current_and_previous_buffers"], "PROVEN")
        self.assertEqual(conclusions["ui_may_render_both_as_distinct_native_layers"], "PROVEN")
        self.assertEqual(conclusions["previous_buffer_is_always_immediate_prior_current"], "FALSIFIED")
        self.assertEqual(conclusions["firmware_reason_for_previous_buffer"], "UNKNOWN")
        self.assertEqual(
            conclusions["ui_animation_old_to_new_every_refresh"],
            "PROHIBITED_WITHOUT_SEPARATE_EVIDENCE",
        )

        for capture in ("PortmonAUTOCAL", "PortmonLOGNOVO"):
            stats = data["dual_capture_observation"][capture]
            self.assertGreater(stats["complete_ordered_quartet_cycles"], 0)
            self.assertGreater(stats["current_point_changed_index_events"], 0)
            self.assertGreater(stats["prev_equals_immediate_prior_current_events"], 0)
            self.assertLess(stats["ratio"], 0.5)

    def test_all_four_native_consumers_are_locked(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        expected = {
            "PETR_INJ_TBUF_GAS_PREV": ("0x015D", "GasPointPrev.x"),
            "MNFLD_PRESS_BUF_GAS_PREV": ("0x015E", "GasPointPrev.y"),
            "PETR_INJ_TBUF_GAS": ("0x015F", "GasPoint.x"),
            "MNFLD_PRESS_BUF_GAS": ("0x0160", "GasPoint.y"),
        }
        for key, (address, role) in expected.items():
            field = data["fields"][key]
            self.assertEqual(field["address"], address)
            self.assertEqual(field["role"], role)
            self.assertEqual(field["status"], "PROVEN")


if __name__ == "__main__":
    unittest.main()
