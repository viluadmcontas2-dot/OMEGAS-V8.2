import importlib.util, json, tempfile, unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
SPEC=importlib.util.spec_from_file_location("reconcile",ROOT/"atlas/tools/reconcile.py")
MOD=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(MOD)

class AtlasContractTest(unittest.TestCase):
    def test_terminal_unknown_is_not_allowed(self):
        self.assertNotIn("UNKNOWN",MOD.ALLOWED)
        self.assertEqual(MOD.ALLOWED,{"PROVEN","REFUTED","ESCALATE","BROKEN"})

    def test_receipt_reader_ignores_non_receipt_json(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)
            (p/"x.json").write_text(json.dumps({"schema":"other","status":"UNKNOWN"}),encoding="utf-8")
            self.assertEqual(MOD.read_receipts(p),[])

    def test_write_json_creates_missing_parent_directories(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/"nested"/"atlas-out"/"result.json"
            MOD.write_json(p,{"ok":True})
            self.assertTrue(p.is_file())
            self.assertEqual(json.loads(p.read_text(encoding="utf-8")),{"ok":True})

if __name__=="__main__":
    unittest.main()
