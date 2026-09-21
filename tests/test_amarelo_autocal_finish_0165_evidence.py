import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-finish-0165-v1.json")


class AutoCalFinish0165EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_rtti_resolves_finish_fields(self):
        rtti = self.data["rtti"]
        self.assertEqual(rtti["DM+0x78"]["name"], "VECT_AUTOCAL_U8_1")
        self.assertEqual(rtti["DM+0x7C"]["name"], "VECT_AUTOCAL_U8_2")
        self.assertEqual(rtti["DM+0x7C"]["alias"], "MAX_AUTOMATCH")
        self.assertEqual(rtti["DM+0xC8"]["name"], "VECT_AUTOCAL_U8_0")
        self.assertEqual(rtti["DM+0xCC"]["name"], "NUM_ATUOMATCH_EXECUTED")
        self.assertEqual(rtti["DM+0xCC"]["serialCode"], "0x0174")

    def test_finish_static_path_is_separate_from_raw_observation(self):
        conclusions = self.data["conclusions"]
        self.assertEqual(conclusions["finish_sets_num_automatch_executed_to_max_automatch"], "PROVEN_STATIC")
        self.assertEqual(conclusions["old_u8_1_to_u8_0_interpretation"], "FALSIFIED_BY_RTTI_BYTE_LAYOUT_AND_DIRECT_ASSEMBLY")
        self.assertEqual(conclusions["finish_path_can_reach_serial_commit_when_connected"], "PROVEN_STATIC")
        self.assertEqual(conclusions["finish_0x0174_write_observed_in_supplied_raw"], "NO")
        self.assertEqual(conclusions["exact_ecu_side_effect_after_write"], "UNKNOWN")
        self.assertEqual(conclusions["levels_related"], "FALSIFIED")

    def test_0165_wire_family_is_indexed_zero_one_two(self):
        wire = self.data["wire_observation"]["PortmonLOGNOVO"]
        self.assertEqual(wire["address_0x0165_reads"], 9)
        expected = {
            "index0": "0A 65 01 00 70",
            "index1": "0A 65 01 01 71",
            "index2": "0A 65 01 02 72",
        }
        for key, request in expected.items():
            self.assertEqual(wire["requests"][key]["request"], request)
            self.assertEqual(wire["requests"][key]["count"], 3)
            self.assertEqual(wire["requests"][key]["response_payload_hex"], "03")

    def test_subindex_confidence_is_not_overstated(self):
        mapping = self.data["subindex_mapping"]
        self.assertEqual(mapping["VECT_AUTOCAL_U8_1"]["status"], "PROVEN")
        self.assertEqual(mapping["VECT_AUTOCAL_U8_1"]["index"], 1)
        self.assertEqual(mapping["VECT_AUTOCAL_U8_2"]["status"], "PROVEN")
        self.assertEqual(mapping["VECT_AUTOCAL_U8_2"]["index"], 2)
        self.assertEqual(mapping["VECT_AUTOCAL_U8_0"]["status"], "PROVEN_RESOURCE_WIRE_MAPPING")
        self.assertEqual(mapping["VECT_AUTOCAL_U8_0"]["index"], 0)
        inventory = self.data["dfm"]["serial_code_0x0165_component_inventory"]
        self.assertEqual(inventory["count"], 3)
        self.assertEqual(inventory["status"], "PROVEN_RESOURCE")


if __name__ == "__main__":
    unittest.main()
