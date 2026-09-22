import importlib.util
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location("semantic_targets",ROOT/"atlas/battle/semantic_targets.py")
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)

PAYLOAD={
    "classes":{
        "TAutoCalDM":{
            "published_methods":[{"name":"AUTO_CAL_ENABLEGetData","va":0x512B8C,"va_hex":"0x00512b8c"}],
            "fields":[{"name":"MUL_ACT","offset":0xA0,"offset_hex":"0x0000a0","type":"TypeIndex(0)"}],
        },
        "TAutoCalUI":{
            "published_methods":[{"name":"ActionAutoMatchExecute","va":0x5189B4,"va_hex":"0x005189b4"}],
            "fields":[{"name":"PetrolCurve","offset":0x200,"offset_hex":"0x000200","type":"TLineSeries"}],
        },
    },
    "event_bindings":[{"resource":"TAUTOCALUI","object":"ActionAutoMatch","object_class":"TAction","event":"OnExecute","handler":"ActionAutoMatchExecute","resolved_method":True}],
    "action_bindings":[{"resource":"TAUTOCALUI","object":"ButtonAutoMatch","action":"ActionAutoMatch","resolved_object":True}],
    "serial_objects":[{"resource":"TAUTOCALDM","object":"MUL_ACT","object_class":"TAebVector","serial_code":353,"serial_hex":"0x0161","file_section":"AutoCal","file_key":"MulAct"}],
    "visual_objects":[{"resource":"TAUTOCALUI","object":"PetrolCurve","object_class":"TLineSeries"}],
    "object_field_links":[{"resource":"TAUTOCALUI","object":"PetrolCurve","field":"PetrolCurve"}],
}

class SemanticTargetsTest(unittest.TestCase):
    def test_builds_closure_targets(self):
        rows=mod.build(PAYLOAD)
        kinds={x["kind"] for x in rows}
        self.assertTrue({"method","field","field-use","event","action","serial","visual"}.issubset(kinds))
        self.assertTrue(any(x["target"]=="TAutoCalDM::MUL_ACT@0x0000a0" and x["kind"]=="field-use" for x in rows))

    def test_links_event_handler_and_action(self):
        method=mod.find_method_target(PAYLOAD,"ActionAutoMatchExecute")
        self.assertIsNotNone(method)
        event=mod.find_event_for_action(PAYLOAD,"ActionAutoMatch")
        self.assertIsNotNone(event)
        self.assertEqual(event["meta"]["handler"],"ActionAutoMatchExecute")

if __name__=="__main__":unittest.main()
