import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-point-buffer-ownership-v1.json")


class AutoCalPointBufferOwnershipEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_supplied_captures_only_read_native_point_buffers(self):
        access = self.data["wire_access"]
        for capture in ("PortmonAUTOCAL", "PortmonLOGNOVO"):
            for address in ("0x015D", "0x015E", "0x015F", "0x0160"):
                row = access[capture][address]
                self.assertEqual(row["non_read_commands"], 0)
                self.assertGreater(row["commands"]["0x29_read_vector"], 0)

    def test_buffers_change_without_host_write(self):
        mutation = self.data["observed_mutation"]
        self.assertGreater(mutation["PortmonAUTOCAL"]["gas_current_changed_index_events"], 0)
        self.assertGreater(mutation["PortmonLOGNOVO"]["gas_current_changed_index_events"], 0)
        conclusions = self.data["conclusions"]
        self.assertEqual(conclusions["host_writes_point_buffers_in_supplied_captures"], "FALSIFIED")
        self.assertEqual(conclusions["point_buffers_change_without_host_write"], "PROVEN")
        self.assertEqual(conclusions["ecu_or_firmware_side_point_update_ownership"], "PROVEN_FOR_SUPPLIED_CAPTURES")
        self.assertEqual(conclusions["exact_firmware_update_formula"], "UNKNOWN")


if __name__ == "__main__":
    unittest.main()
