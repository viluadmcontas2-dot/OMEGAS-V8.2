import json
from pathlib import Path
import unittest

GRAPH = Path("docs/omegas-amarelo/evidence/WU-002-consumer-graph-v1.json")


class NativeConsumerGraphContractTest(unittest.TestCase):
    def test_graph_preserves_proven_and_unknown_edges(self):
        data = json.loads(GRAPH.read_text(encoding="utf-8"))
        self.assertEqual(data["schema"], "omegas.amarelo.autocal-consumer-graph.v1")
        edges = data["edges"]
        key = {(e["from"], e["to"]): e for e in edges}

        for action, code in [
            ("ActionResetPetrolExecute", 1),
            ("ActionResetGasExecute", 2),
            ("ActionResetAllExecute", 4),
            ("ActionAutoMatchExecute", 8),
        ]:
            edge = key[(action, "dispatcher:0x517568")]
            self.assertEqual(edge["status"], "PROVEN")
            self.assertEqual(edge["transform"], f"action_code={code}")

        finish_copy = key[(
            "MAX_AUTOMATCH@DM+0x7C",
            "NUM_AUTOMATCH_EXECUTED@DM+0xCC",
        )]
        self.assertEqual(finish_copy["status"], "PROVEN_RTTI_AND_STATIC")
        self.assertIn("setter 0x976CB8", finish_copy["transform"])

        finish = data["states"]["finish_autocal"]
        self.assertEqual(finish["source_field"]["semantic"], "VECT_AUTOCAL_U8_2 / MAX_AUTOMATCH")
        self.assertEqual(finish["source_field"]["serial_family"], "0x0165:2")
        self.assertEqual(finish["destination_field"]["semantic"], "NUM_ATUOMATCH_EXECUTED")
        self.assertEqual(finish["destination_field"]["serialCode"], "0x0174")
        self.assertEqual(finish["static_transform"], "NUM_AUTOMATCH_EXECUTED := MAX_AUTOMATCH")
        self.assertEqual(finish["status"], "PROVEN_STATIC_NOT_RAW_WRITE_OBSERVED")
        self.assertFalse(finish["destination_commit"]["raw_write_observed"])
        self.assertEqual(finish["exact_ecu_side_effect"], "UNKNOWN")

        states = data["states"]
        self.assertEqual(states["label_use_status"], "PROVEN")
        self.assertEqual(states["transition_status"], "UNKNOWN")
        self.assertEqual(states["role"], "refresh_dispatch_scheduler")
        self.assertEqual(states["scheduler_structure_status"], "PROVEN")
        self.assertEqual(states["ecu_state_semantics"], "UNKNOWN")
        scheduler = states["scheduler"]
        self.assertEqual(scheduler["table_count_field"], "DM+0x4C0")
        self.assertEqual(scheduler["cursor_field"], "DM+0x4C4")
        self.assertEqual(
            [(row["table_va"], row["entry_count"]) for row in scheduler["variants"]],
            [("0xA9EB98", 6), ("0xA9EBB4", 8)],
        )
        labels = [row["name"] for row in states["labels"]]
        self.assertIn("state_acquire_petrol_line", labels)
        self.assertIn("state_draw_gas_petrol_curve", labels)

        roles = {row["label"]: row for row in states["resolved_refresh_roles"]}
        self.assertEqual(roles["state_0"]["role"], "PetrolPoint + petrol maturity/polling")
        self.assertEqual(roles["state_1"]["role"], "GasPointPrev")
        self.assertEqual(roles["state_2"]["role"], "GasPoint + gas maturity/polling")
        self.assertEqual(roles["state_3"]["role"], "KLine = PETR_INJ_TBP x MUL_ACT")
        self.assertEqual(roles["state_4"]["role"], "acquisition areas / acquired zones")

        refs = states["reference_line_scheduler"]
        self.assertEqual(refs["petrol"]["semantic"], "PETR_MNFLD_PRESS_RV")
        self.assertEqual(refs["gas"]["semantic"], "GAS_MNFLD_PRESS_RV")
        self.assertEqual(refs["petrol"]["parity_byte"], "0xA9DA18")
        self.assertEqual(refs["gas"]["parity_byte"], "0xA9DA19")

        projection = states["curve_projection"]
        self.assertEqual(projection["common_x_semantic"], "PETR_INJ_TBP")
        self.assertEqual(projection["status"], "PROVEN")
        self.assertIn("not evidence of a new ECU calibration mutation", projection["authority_boundary"])


if __name__ == "__main__":
    unittest.main()
