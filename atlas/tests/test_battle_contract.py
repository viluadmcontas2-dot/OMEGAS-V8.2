import importlib.util, json, tempfile, unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]

def load(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path)
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod);return mod

LANE=load("battle_lane","atlas/battle/lane.py")
RECON=load("battle_reconcile","atlas/battle/reconcile.py")
STATIC=load("battle_static","atlas/battle/static_index.py")

class BattleContractTest(unittest.TestCase):
    def test_unknown_is_not_a_terminal_state(self):
        self.assertNotIn("UNKNOWN",LANE.ALLOWED)
        self.assertEqual(LANE.ALLOWED,{"PROVEN","REFUTED","ESCALATE","BROKEN"})

    def test_target_keys_are_stable(self):
        self.assertEqual(RECON.key("function","00401000"),"function|00401000")
        self.assertEqual(RECON.lane_id(2,"ghidra-fn","function","00401000"),RECON.lane_id(2,"ghidra-fn","function","00401000"))

    def test_reusable_lane_ids_change_with_engine(self):
        self.assertNotEqual(RECON.lane_id(2,"ghidra-fn","function","00401000"),RECON.lane_id(2,"objdump-fn","function","00401000"))

    def test_objdump_xref_parser_never_counts_instruction_address(self):
        self.assertEqual(STATIC.operand_hex_values("  513596:\t54 \tpush esp"), set())
        self.assertEqual(
            STATIC.operand_hex_values("  401000:\ta1 96 35 51 00\tmov eax,ds:0x513596"),
            {0x513596},
        )

if __name__=="__main__":unittest.main()
