import importlib.util, unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location("semantic_index",ROOT/"atlas/battle/semantic_index.py")
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)

SAMPLE="""  TAutoCalDM — size=268B, vmt=0xa9c944, ptrsize=4B
    fields (1):
      +0x000070  PETR_INJ_TBP                         : TypeIndex(0)
    published methods (1):
      0x00512b8c  AUTO_CAL_ENABLEGetData
    virtual methods (1):
  TAutoCalUI — size=1260B, vmt=0xa9ee00, ptrsize=4B
    fields (2):
      +0x0002e8  ButtonAutoMatch                      : TypeIndex(4)
      +0x00034c  ActionAutoMatch                      : TypeIndex(11)
    published methods (1):
      0x005189b4  ActionAutoMatchExecute
    virtual methods (1):
  TAutoCalColorSettings — size=128B, vmt=0xa9ff00, ptrsize=4B
    fields (1):
      +0x000050  _GasPoint                            : TShape
    published methods (1):
      0x0051b000  _AcqusitionAreas0Click
    virtual methods (1):
  resource TAUTOCALSETTINGS TAutoCalSettings:AutoCalSettings  (1 components)
        <object TAutoCalSettings:AutoCalSettings>
          OnClose = "FormClose"  → 0x513e68
          OnShow = "FormShow"  → 0x513da8
  resource TAUTOCALDM TAutoCalDM:AutoCalDM  (1 components)
      <object TAebVector:PETR_INJ_TBP>
        SerialCode = 331
        FileSection = "AutoCal"
        FileKeyName = "PetrInjTBp"
  resource TAUTOCALUI TAutoCalUI:AutoCalUI  (2 components)
        <object TButton:ButtonAutoMatch>
          Action = "ActionAutoMatch"
        <object TAction:ActionAutoMatch>
          Caption = "Manual automatch"
          OnExecute = "ActionAutoMatchExecute"
  resource TAUTOCALCOLORSETTINGS TAutoCalColorSettings:AutoCalColorSettings  (1 components)
        <object TShape:_GasPoint>
          OnClick = "_AcqusitionAreas0Click"
"""

class SemanticIndexTest(unittest.TestCase):
    def test_extracts_fields_serial_and_event_graph(self):
        x=mod.parse(SAMPLE)
        self.assertEqual(x["classes"]["TAutoCalDM"]["fields"][0]["name"],"PETR_INJ_TBP")
        self.assertEqual(x["serial_objects"][0]["serial_code"],331)
        self.assertTrue(x["event_bindings"][0]["resolved_method"])
        self.assertTrue(x["action_bindings"][0]["resolved_object"])
        self.assertEqual(x["counts"]["unresolved_event_bindings"],0)
        self.assertEqual(x["counts"]["unresolved_action_bindings"],0)
        self.assertTrue(any(e["object"]=="PETR_INJ_TBP" for e in x["object_field_links"]))
        self.assertIn("TAutoCalColorSettings",x["classes"])
        color_event=next(e for e in x["event_bindings"] if e["object"]=="_GasPoint")
        self.assertTrue(color_event["resolved_method"])
        self.assertEqual(color_event["handler_candidates"][0]["class"],"TAutoCalColorSettings")
        settings_close=next(e for e in x["event_bindings"] if e["object"]=="AutoCalSettings" and e["event"]=="OnClose")
        settings_show=next(e for e in x["event_bindings"] if e["object"]=="AutoCalSettings" and e["event"]=="OnShow")
        self.assertEqual(settings_close["handler"],"FormClose")
        self.assertEqual(settings_show["handler"],"FormShow")

if __name__=="__main__":unittest.main()
