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

        finish_copy = key[("DM+0x7C", "DM+0xCC")]
        self.assertEqual(finish_copy["status"], "PROVEN")
        self.assertIn("setter 0x976CB8", finish_copy["transform"])

        self.assertEqual(
            key[("DM+0x7C", "VECT_AUTOCAL_U8_2_or_NUM_AUTOMATCH_EXECUTED")]["status"],
            "UNKNOWN",
        )
        self.assertEqual(
            key[("DM+0xCC", "VECT_AUTOCAL_U8_2_or_NUM_AUTOMATCH_EXECUTED")]["status"],
            "UNKNOWN",
        )
        self.assertEqual(data["states"]["status"], "INFERRED")
        self.assertIn("state_acquire_petrol_line", data["states"]["rtti_strings_observed"])
        self.assertIn("state_draw_gas_petrol_curve", data["states"]["rtti_strings_observed"])


if __name__ == "__main__":
    unittest.main()
