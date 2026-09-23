import json, tempfile, unittest
from pathlib import Path
import importlib.util

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location("behavior_gate",ROOT/"atlas/battle/behavior_gate.py")
GATE=importlib.util.module_from_spec(spec);spec.loader.exec_module(GATE)

class BehaviorGateTest(unittest.TestCase):
    def test_repo_contract_requires_all_behavior_proofs_and_is_now_closed(self):
        result=GATE.evaluate(ROOT)
        self.assertEqual(result["required"],14)
        self.assertTrue(result["complete"], result)
        self.assertEqual(result["proven"],14)
        self.assertEqual(result["open"],[])
        rows={x["id"]:x for x in result["gates"]}
        self.assertEqual(rows["action.reset_k_factor_effect"]["status"],"PROVEN")
        self.assertEqual(rows["action.reset_gas_effect"]["status"],"PROVEN")
        self.assertEqual(rows["scheduler.refresh_cadence"]["status"],"PROVEN")

if __name__=="__main__":
    unittest.main()
