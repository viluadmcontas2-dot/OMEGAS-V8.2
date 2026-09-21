import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-action-wire-v1.json")


class NativeAutoCalActionWireEvidenceTest(unittest.TestCase):
    def test_native_action_wire_evidence_contract(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.assertEqual(data["schema"], "omegas.amarelo.autocal-action-wire.v1")
        self.assertEqual(data["binary"]["bridge_va"], "0x512280")
        self.assertEqual(data["binary"]["dispatcher_va"], "0x517568")
        self.assertEqual({row["action"] for row in data["binary"]["dispatcher_calls"]}, {1, 2, 4, 8})

        actions = {row["code"]: row for row in data["actions"]}
        self.assertEqual(set(actions), {1, 2, 4, 8})
        for code, row in actions.items():
            raw = bytes.fromhex(row["request"])
            self.assertEqual(raw[:3], bytes([0x02, 0x24, 0x04]))
            self.assertEqual(raw[3], code)
            self.assertEqual(raw[-1], sum(raw[:-1]) & 0xFF)
            self.assertEqual(row["classification"], "PROVEN")

        self.assertTrue(actions[4]["raw_observed"])
        self.assertEqual(len(actions[4]["observations"]), 2)
        self.assertTrue(all(not actions[c]["raw_observed"] for c in (1, 2, 8)))

        scan = data["checksum_scan"]
        self.assertEqual(scan["portmon_autocal"]["writes"], scan["portmon_autocal"]["additive_ok"])
        self.assertEqual(scan["portmon_lognovo"]["writes"] - scan["portmon_lognovo"]["additive_ok"], 2)
        self.assertEqual({e["bytes"] for e in scan["portmon_lognovo"]["exceptions"]}, {"00"})


if __name__ == "__main__":
    unittest.main()
