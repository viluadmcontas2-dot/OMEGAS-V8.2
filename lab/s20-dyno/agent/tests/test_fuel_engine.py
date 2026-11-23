import unittest, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from fuel_engine import FuelEngine

def row(src,ms,**values):
 return {"v":1,"source":src,"monoNs":ms*1_000_000,"t":ms,"values":values}
def cycle(e,at,mode,stft,ect=75,map=55,rpm=1800):
 e.ingest(row("mp48",at,fuelByte=mode,rpm=rpm,mapBar=map/100))
 for j,(k,v) in enumerate([("rpm",rpm),("mapKpa",map),("ect",ect),("tps",20),("speed",30),("ltft",0),("stft",stft)]):
  e.ingest(row("obd",at+j*20,**{k:v,"pid":k}))

class EngineTests(unittest.TestCase):
 def test_transition_is_not_petrol_ground_truth(self):
  e=FuelEngine()
  for i in range(20):cycle(e,i*900,0x88,3)
  a=e.report();self.assertEqual(a["labelCounts"]["gasolina"],0);self.assertEqual(a["labelCounts"]["GNV"],0)
  self.assertEqual(a["obdOnly"]["decision"],"SEM_MODELO_VALIDADO")
 def test_confirmed_both_modes_produce_controlled_pairs(self):
  e=FuelEngine()
  for i in range(25):cycle(e,i*900,0x80,1)
  for i in range(25):cycle(e,(i+28)*900,0x90,12,ect=77)
  a=e.report()
  self.assertGreater(a["labelCounts"]["gasolina"],5);self.assertGreater(a["labelCounts"]["GNV"],5)
  self.assertGreater(a["controlled"]["matchedPairs"],3)
  self.assertGreater(a["controlled"]["medianDeltaStftGnvMinusPetrol"],5)
 def test_ect_confounds_cannot_compare(self):
  e=FuelEngine()
  for i in range(20):cycle(e,i*900,0x80,-7,ect=35)
  for i in range(20):cycle(e,(i+25)*900,0x90,13,ect=90)
  self.assertEqual(e.report()["controlled"]["matchedPairs"],0)
 def test_no_label_no_fuel_claim(self):
  e=FuelEngine()
  for i in range(50):e.ingest(row("obd",i*900,stft=20))
  self.assertEqual(e.report()["obdOnly"]["decision"],"SEM_MODELO_VALIDADO")
 def test_abrupt_stft_but_not_fuel(self):
  e=FuelEngine()
  for i in range(16):cycle(e,i*900,0x90,-1)
  for i in range(16):cycle(e,(i+18)*900,0x90,-22)
  self.assertTrue(any(x["kind"]=="STFT_ABRUPTO" for x in e.report()["events"]))
  self.assertEqual(e.report()["obdOnly"]["decision"],"SEM_MODELO_VALIDADO")
 def test_runtime_json_rules(self):
  e=FuelEngine(rules={"stability_ms":1500,"rpm_tolerance":250,"map_tolerance":10,"ect_tolerance":5,"tps_tolerance":8})
  for i in range(8):cycle(e,i*800,0x80,2)
  self.assertGreater(e.report()["labelCounts"]["gasolina"],0)

if __name__=="__main__":unittest.main()

[executed on device: S20-FE (abd78d23-57fd-4034-814b-4a26d96e02ff)]