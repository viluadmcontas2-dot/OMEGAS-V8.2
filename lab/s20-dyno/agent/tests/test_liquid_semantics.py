import unittest, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from fuel_engine import FuelEngine

class LiquidSemantics(unittest.TestCase):
 def test_mp48_petrol_mode_does_not_claim_gasoline_vs_ethanol(self):
  e=FuelEngine()
  e.ingest({"source":"mp48","monoNs":1,"values":{"fuelByte":0x80}})
  result=e.report()
  self.assertEqual(result["mp48"]["confirmedMode"],"liquido")
  self.assertIn("liquido",result["labelCounts"])
  self.assertNotIn("gasolina",result["labelCounts"])
 def test_transition_does_not_claim_liquid_fuel(self):
  e=FuelEngine()
  e.ingest({"source":"mp48","monoNs":1,"values":{"fuelByte":0x88}})
  self.assertIsNone(e.report()["mp48"]["confirmedMode"])
