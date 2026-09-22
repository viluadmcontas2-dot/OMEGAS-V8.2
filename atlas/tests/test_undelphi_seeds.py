import importlib.util
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location("undelphi_seeds",ROOT/"atlas/battle/undelphi_seeds.py")
mod=importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

SAMPLE="""undelphi v0.3.2 diagnostic preamble
random header before first class
  TAutoCalUI — size=1260B, vmt=0xa9ee00
    published methods (1):
      0x005189b4  ActionAutoMatchExecute
    virtual methods (1):
  TAutoCalColorSettings — size=128B, vmt=0xa9ff00
    published methods (1):
      0x0051b000  _AcqusitionAreas0Click
    virtual methods (1):
  TUnrelated — size=64B, vmt=0xa90000
    published methods (1):
      0x00400000  IgnoreMe
    virtual methods (1):
"""

class UndelphiSeedsTest(unittest.TestCase):
    def test_preamble_is_safe_and_all_autocal_classes_are_discovered(self):
        classes,methods=mod.extract(SAMPLE)
        self.assertIn("TAutoCalUI",classes)
        self.assertIn("TAutoCalColorSettings",classes)
        self.assertNotIn("TUnrelated",classes)
        names={m["name"] for m in methods}
        self.assertIn("ActionAutoMatchExecute",names)
        self.assertIn("_AcqusitionAreas0Click",names)
        self.assertNotIn("IgnoreMe",names)

if __name__=="__main__":
    unittest.main()
