import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-acquired-zones-v1.json")


class AutoCalAcquiredZonesEvidenceTest(unittest.TestCase):
    def test_dual_raw_capture_contract(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.assertEqual(data["schema"], "omegas.amarelo.autocal-acquired-zones.v1")
        self.assertEqual(data["fields"]["ACQUIRED_ZONES_PETROL"]["payload_length"], 4)
        self.assertEqual(data["fields"]["ACQUIRED_ZONES_GAS"]["payload_length"], 4)
        self.assertEqual(data["progbase_consumer"]["logical_zone_count"], 4)
        self.assertEqual(data["progbase_consumer"]["status"], "PROVEN")

        for capture in data["captures"].values():
            for address in ("0x016F", "0x0170"):
                rows = capture[address]["first_occurrence_by_unique_payload"]
                self.assertGreater(capture[address]["count"], 0)
                self.assertTrue(rows)
                for row in rows:
                    payload = row["payload"]
                    self.assertEqual(len(payload), 4)
                    self.assertTrue(all(value in (0, 1) for value in payload))

        conclusions = data["conclusions"]
        self.assertFalse(conclusions["monotonic_progress"])
        self.assertEqual(
            conclusions["percent_semantics"],
            "PROHIBITED_WITHOUT_SEPARATE_EVIDENCE",
        )
        self.assertEqual(conclusions["element_order_semantics"], "UNKNOWN")

    def test_captures_contain_reset_or_regression_evidence(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        autocal_gas = [
            row["payload"]
            for row in data["captures"]["PortmonAUTOCAL"]["0x0170"]["first_occurrence_by_unique_payload"]
        ]
        lognovo_petrol = [
            row["payload"]
            for row in data["captures"]["PortmonLOGNOVO"]["0x016F"]["first_occurrence_by_unique_payload"]
        ]
        self.assertIn([1, 1, 1, 1], autocal_gas)
        self.assertIn([1, 0, 0, 1], autocal_gas)
        self.assertIn([1, 1, 1, 1], lognovo_petrol)
        self.assertIn([0, 0, 0, 0], lognovo_petrol)


if __name__ == "__main__":
    unittest.main()


class AutoCalAcquiredZoneOwnershipEvidenceTest(unittest.TestCase):
    def test_supplied_captures_only_read_zone_vectors(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        ownership = data["wire_ownership"]
        for capture in ("PortmonAUTOCAL", "PortmonLOGNOVO"):
            for address in ("0x016F", "0x0170"):
                row = ownership[capture][address]
                self.assertGreater(row["read_vector_commands"], 0)
                self.assertEqual(row["non_read_commands"], 0)

    def test_zone_flags_are_ecu_owned_not_host_maturity_formula(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        conclusions = data["conclusions"]
        self.assertEqual(conclusions["host_writes_acquired_zone_vectors_in_supplied_captures"], "FALSIFIED")
        self.assertEqual(conclusions["ecu_or_firmware_side_zone_flag_ownership"], "PROVEN_FOR_SUPPLIED_CAPTURES")
        self.assertEqual(conclusions["zone_flag_transition_formula"], "UNKNOWN")
        self.assertEqual(conclusions["host_maturity_threshold_is_zone_flag_formula"], "FALSIFIED")
