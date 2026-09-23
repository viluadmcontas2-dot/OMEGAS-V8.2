import importlib.util, json, tempfile, unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]

def load(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path)
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod);return mod

LANE=load("battle_lane","atlas/battle/lane.py")
RECON=load("battle_reconcile","atlas/battle/RECON.py")
STATIC=load("battle_static","atlas/battle/static_index.py")
UNDELPHI=load("undelphi_seeds","atlas/battle/undelphi_seeds.py")

class BattleContractTest(unittest.TestCase):
    def test_unknown_is_not_a_terminal_state(self):
        self.assertNotIn("UNKNOWN",LANE.ALLOWED)
        self.assertEqual(LANE.ALLOWED,{"PROVEN","REFUTED","ESCALATE","BROKEN"})

    def test_target_keys_are_stable(self):
        self.assertEqual(RECON.key("function","00401000"),"function|00401000")
        self.assertEqual(RECON.lane_id(2,"ghidra-fn","function","00401000"),RECON.lane_id(2,"ghidra-fn","function","00401000"))

    def test_reusable_lane_ids_change_with_engine(self):
        self.assertNotEqual(RECON.lane_id(2,"ghidra-fn","function","00401000"),RECON.lane_id(2,"objdump-fn","function","00401000"))

    def test_method_can_close_with_delphi_plus_capstone(self):
        t={"kind":"method","target":"X","drivers":{"delphi-method":{"status":"PROVEN"},"capstone-method":{"status":"PROVEN"}},"new_targets":[]}
        self.assertEqual(RECON.target_status({"targets":{}},t),"PROVEN")

    def test_event_closes_when_delphi_binding_and_method_dependency_are_proven(self):
        dep={"kind":"method","target":"M","status":"PROVEN","drivers":{},"new_targets":[]}
        t={"kind":"event","target":"E","drivers":{"delphi-event":{"status":"PROVEN"}},"new_targets":["method|M"]}
        self.assertEqual(RECON.target_status({"targets":{"method|M":dep}},t),"PROVEN")

    def test_action_closes_when_delphi_binding_and_event_dependency_are_proven(self):
        dep={"kind":"event","target":"E","status":"PROVEN","drivers":{},"new_targets":[]}
        t={"kind":"action","target":"A","drivers":{"delphi-action":{"status":"PROVEN"}},"new_targets":["event|E"]}
        self.assertEqual(RECON.target_status({"targets":{"event|E":dep}},t),"PROVEN")

    def test_serial_can_close_without_portmon_when_raw_resource_agrees(self):
        t={"kind":"serial","target":"S","drivers":{"delphi-serial":{"status":"PROVEN"},"pe-serial-resource":{"status":"PROVEN"},"portmon-object":{"status":"ESCALATE"}},"new_targets":[]}
        self.assertEqual(RECON.target_status({"targets":{}},t),"PROVEN")

    def test_function_alias_closes_when_exact_published_method_is_proven(self):
        method_key="method|TAutoCalUI::ActionResetGasExecute@0x005189cc"
        state={"targets":{method_key:{"kind":"method","target":"TAutoCalUI::ActionResetGasExecute@0x005189cc","status":"PROVEN","drivers":{},"new_targets":[]}}}
        t={"kind":"function","target":"005189cc","status":"ESCALATE","drivers":{"ghidra-fn":{"status":"ESCALATE"},"objdump-fn":{"status":"ESCALATE"},"capstone-fn":{"status":"ESCALATE"}},"origins":[method_key],"new_targets":[]}
        self.assertEqual(RECON.target_status(state,t),"PROVEN")

    def test_owner_field_use_global_tokens_match_real_delphi_names(self):
        src=(ROOT/"atlas/battle/lane.py").read_text(encoding="utf-8")
        self.assertIn('"TAutoCalDM":["PTR__AutoCalDM_"]',src)
        self.assertIn('"TAutoCalDM_EE":["PTR__AutoCalDM_EE_"]',src)
        self.assertIn('"pe-dfm-field-binding"',src)

    def test_owner_field_use_is_composite_class_bound_proof(self):
        t={"kind":"field-use","target":"F","drivers":{"owner-field-use":{"status":"PROVEN","evidence":[{"hits":[{"function":"0051b910","source":"owner-global-ghidra"}]}]}},"new_targets":[]}
        self.assertEqual(RECON.target_status({"targets":{}},t),"PROVEN")

    def test_field_use_requires_same_function_overlap(self):
        t={"kind":"field-use","target":"F","drivers":{
            "ghidra-field-use":{"status":"PROVEN","evidence":[{"hits":[{"function":"005162f8"}]}]},
            "capstone-field-use":{"status":"PROVEN","evidence":[{"hits":[{"function":"005162f8"}]}]},
        },"new_targets":[]}
        self.assertEqual(RECON.target_status({"targets":{}},t),"PROVEN")
        t["drivers"]["capstone-field-use"]["evidence"][0]["hits"][0]["function"]="00401000"
        self.assertEqual(RECON.target_status({"targets":{}},t),"ESCALATE")

    def test_undelphi_exact_method_parser(self):
        sample = """  TAutoCalUI — size=1260B, vmt=0xa9ee00, ptrsize=4B
    published methods (2):
      0x005187a0 ActionAutoCalRifExecute
      0x005189b4 ActionAutoMatchExecute
    virtual methods (78):
"""
        classes, methods = UNDELPHI.extract(sample)
        self.assertEqual(classes["TAutoCalUI"]["size"], 1260)
        self.assertEqual([m["name"] for m in methods], ["ActionAutoCalRifExecute", "ActionAutoMatchExecute"])
        self.assertEqual([m["va_hex"] for m in methods], ["0x005187a0", "0x005189b4"])

    def test_objdump_xref_parser_never_counts_instruction_address(self):
        self.assertEqual(STATIC.operand_hex_values("  513596:\t54 \tpush esp"), set())
        self.assertEqual(STATIC.operand_hex_values("  401000:\ta1 96 35 51 00\tmov eax,ds:0x513596"),{0x513596})

if __name__=="__main__":unittest.main()


class PseudoFunctionFilteringTest(unittest.TestCase):
    def test_pseudo_vcl_stream_is_not_promoted_as_function(self):
        t={
            "drivers":{
                "owner-field-use":{
                    "evidence":[{"hits":[{"function":"<vcl-stream>"},{"function":"0051a070"}]}]
                }
            }
        }
        self.assertEqual(RECON.field_consumer_functions(t), {"0051a070"})

    def test_drop_existing_pseudo_function_target(self):
        state={
            "targets":{
                "function|<vcl-stream>":{"kind":"function","target":"<vcl-stream>","new_targets":[]},
                "function|0051a070":{"kind":"function","target":"0051a070","new_targets":[]},
                "field-use|x":{"kind":"field-use","target":"x","new_targets":["function|<vcl-stream>","function|0051a070"]},
            }
        }
        removed=RECON.drop_pseudo_function_targets(state)
        self.assertEqual(removed, ["function|<vcl-stream>"])
        self.assertNotIn("function|<vcl-stream>", state["targets"])
        self.assertEqual(state["targets"]["field-use|x"]["new_targets"], ["function|0051a070"])


class ComponentFieldUseClosureTest(unittest.TestCase):
    def test_component_field_use_can_close_framework_managed_field(self):
        t={
            "kind":"field-use",
            "target":"TAutoCalDM::X@0x000010",
            "drivers":{"component-field-use":{"status":"PROVEN"}},
            "new_targets":[],
        }
        self.assertEqual(RECON.target_status({"targets":{}},t),"PROVEN")
        self.assertIn("component-field-use",RECON.DRIVER_CHOICES["field-use"])
