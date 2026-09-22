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
    published methods (2):
      0x005189b4  ActionAutoMatchExecute
      0x0051a998  ChartDataAfterDraw
    virtual methods (1):
  TAutoCalSettings — size=256B, vmt=0xa9dd00, ptrsize=4B
    fields (0):
    published methods (2):
      0x00513e68  FormClose
      0x00513da8  FormShow
    virtual methods (1):
  TAutoCalColorSettings — size=128B, vmt=0xa9ff00, ptrsize=4B
    fields (1):
      +0x000050  _GasPoint                            : TShape
    published methods (2):
      0x0051b000  _AcqusitionAreas0Click
      0x0051087c  ChartDataAfterDraw
    virtual methods (1):
  resource TAUTOCALSETTINGS → TAutoCalSettings:AutoCalSettings  (1 components)
        <object TAutoCalSettings:AutoCalSettings>
          OnClose = "FormClose"  → 0x513e68
          OnShow = "FormShow"  → 0x513da8
  resource TAUTOCALDM → TAutoCalDM:AutoCalDM  (1 components)
      <object TAebVector:PETR_INJ_TBP>
        SerialCode = 331
        FileSection = "AutoCal"
        FileKeyName = "PetrInjTBp"
  resource TAUTOCALUI → TAutoCalUI:AutoCalUI  (2 components)
        <object TButton:ButtonAutoMatch>
          Action = "ActionAutoMatch"
        <object TAction:ActionAutoMatch>
          Caption = "Manual automatch"
          OnExecute = "ActionAutoMatchExecute"
        <object TChart:ChartData>
          OnAfterDraw = "ChartDataAfterDraw"
  resource TAUTOCALCOLORSETTINGS → TAutoCalColorSettings:AutoCalColorSettings  (1 components)
        <object TShape:_GasPoint>
          OnClick = "_AcqusitionAreas0Click"
        <object TChart:ChartData>
          OnAfterDraw = "ChartDataAfterDraw"
"""

class SemanticIndexTest(unittest.TestCase):
    def test_clean_value_normalizes_annotated_handler(self):
        self.assertEqual(mod.clean_value('"FormClose"  → 0x513e68'),"FormClose")
        self.assertEqual(mod.clean_value('"FormShow" → 0x513da8'),"FormShow")

    def test_extracts_fields_serial_and_event_graph(self):
        x=mod.parse(SAMPLE)
        self.assertEqual(x["classes"]["TAutoCalDM"]["fields"][0]["name"],"PETR_INJ_TBP")
        self.assertEqual(x["serial_objects"][0]["serial_code"],331)
        self.assertEqual(x["resources"][0]["root_name"],"AutoCalSettings")
        auto_match_event=next(e for e in x["event_bindings"] if e["handler"]=="ActionAutoMatchExecute")
        self.assertTrue(auto_match_event["resolved_method"])
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
        chart_events=[e for e in x["event_bindings"] if e["handler"]=="ChartDataAfterDraw"]
        self.assertEqual(len(chart_events),2)
        self.assertTrue(all(e["resolved_method"] for e in chart_events))
        ui_chart=next(e for e in chart_events if e["resource"]=="TAUTOCALUI")
        color_chart=next(e for e in chart_events if e["resource"]=="TAUTOCALCOLORSETTINGS")
        self.assertEqual(ui_chart["resolved_method_target"]["class"],"TAutoCalUI")
        self.assertEqual(ui_chart["resolved_method_target"]["va_hex"],"0x0051a998")
        self.assertEqual(color_chart["resolved_method_target"]["class"],"TAutoCalColorSettings")
        self.assertEqual(color_chart["resolved_method_target"]["va_hex"],"0x0051087c")

if __name__=="__main__":unittest.main()
