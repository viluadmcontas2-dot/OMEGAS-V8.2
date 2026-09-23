import json, tempfile, unittest
from pathlib import Path
import importlib.util

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location("behavior_gate",ROOT/"atlas/battle/behavior_gate.py")
GATE=importlib.util.module_from_spec(spec);spec.loader.exec_module(GATE)

class BehaviorGateTest(unittest.TestCase):
    def test_repo_contract_is_fail_closed_and_reset_k_is_proven(self):
        result=GATE.evaluate(ROOT)
        self.assertGreater(result["required"],1)
        self.assertFalse(result["complete"])
        rows={x["id"]:x for x in result["gates"]}
        self.assertEqual(rows["action.reset_k_factor_effect"]["status"],"PROVEN")
        self.assertEqual(rows["action.reset_gas_effect"]["status"],"OPEN")

if __name__=="__main__":
    unittest.main()
