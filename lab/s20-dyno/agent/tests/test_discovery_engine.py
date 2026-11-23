import unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from discovery_engine import DiscoveryEngine

def row(source,ms,**values):
    return {'source':source,'monoNs':ms*1_000_000,'t':ms,'values':values}

class DiscoveryTests(unittest.TestCase):
 def test_cross_source_rpm_and_map_have_matched_count_and_units(self):
    e=DiscoveryEngine()
    for m in range(0,10001,100):
      e.ingest(row('mp48',m,rpm=1800,mapBar=0.48,fuelByte=0x90))
    for m in range(0,10001,1000):
      e.ingest(row('obd',m+30,rpm=1808))
      e.ingest(row('obd',m+70,mapKpa=49.0))
    r=e.report()
    self.assertGreater(r['synchronization']['rpm']['pairs'],8)
    self.assertAlmostEqual(r['synchronization']['rpm']['medianAbsDiff'],8,delta=.1)
    self.assertAlmostEqual(r['synchronization']['map']['medianAbsDiff'],1,delta=.1)
    self.assertLess(r['synchronization']['rpm']['medianTimestampGapMs'],150)
 def test_throttle_change_creates_qualified_response_interval(self):
    e=DiscoveryEngine()
    for ms in range(0,10000,100):
      rpm=1300 if ms<3400 else 1490
      e.ingest(row('mp48',ms,rpm=rpm,mapBar=.41,fuelByte=0x90))
    for ms,tps in [(0,14),(1000,14),(2000,14),(3000,44),(4000,46),(5000,45)]:
      e.ingest(row('obd',ms,tps=tps))
    r=e.report()
    q=r['throttle']
    self.assertGreaterEqual(q['throttleEvents'],1)
    self.assertGreaterEqual(q['qualifiedResponses'],1)
    self.assertGreaterEqual(q['medianObservedRpmResponseMs'],300)
    self.assertLessEqual(q['medianObservedRpmResponseMs'],700)
    self.assertGreaterEqual(q['resolutionUncertaintyMs'],900)
    self.assertNotEqual(q['quality'],'LATENCIA_EXATA')
 def test_no_assumed_causality_without_rpm_change(self):
    e=DiscoveryEngine()
    for ms in range(0,9000,100):e.ingest(row('mp48',ms,rpm=1100,mapBar=.4))
    for ms,v in [(0,14),(1000,14),(2000,14),(3000,50),(4000,49)]:
      e.ingest(row('obd',ms,tps=v))
    r=e.report()
    self.assertEqual(r['throttle']['qualifiedResponses'],0)
    self.assertGreater(r['throttle']['throttleEvents'],0)
 def test_no_fabricated_lag_with_sparse_sensor(self):
    e=DiscoveryEngine()
    for ms in [0,4000,8000,12000]:
      e.ingest(row('obd',ms,tps=14 if ms<8000 else 55))
      e.ingest(row('mp48',ms,rpm=1200 if ms<9000 else 1450,mapBar=.4))
    r=e.report()
    self.assertEqual(r['throttle']['qualifiedResponses'],0)
    self.assertIn('insuficiente',r['throttle']['quality'].lower())
 def test_learning_cards_include_evidence_and_next_step_not_llm_certainty(self):
    e=DiscoveryEngine()
    for ms in range(0,11000,100):e.ingest(row('mp48',ms,rpm=1450,mapBar=.45,fuelByte=0x90))
    for ms in range(0,11000,1000):e.ingest(row('obd',ms+30,rpm=1452))
    r=e.report()
    self.assertTrue(r['findings'])
    for f in r['findings']:
      self.assertIn(f['state'],('OBSERVADO','EM_INVESTIGACAO','LACUNA'))
      self.assertTrue(f.get('evidence'))
      self.assertTrue(f.get('nextStep'))
      self.assertTrue(f.get('title'))
 def test_94_is_only_observed_state_not_causal_diagnosis(self):
    e=DiscoveryEngine()
    for ms,mode in [(100,0x90),(200,0x94),(300,0x90)]:
      e.ingest(row('mp48',ms,rpm=780,mapBar=.32,fuelByte=mode))
    r=e.report()
    self.assertEqual(r['states']['0x94']['count'],1)
    matching=[f for f in r['findings'] if f['id']=='mp48-state-94']
    self.assertTrue(matching)
    self.assertEqual(matching[0]['state'],'EM_INVESTIGACAO')
 def test_monotonic_clock_reset_does_not_mix_vehicles_sessions(self):
    e=DiscoveryEngine()
    e.ingest(row('mp48',100000,rpm=2000,mapBar=.4))
    e.ingest(row('obd',100100,rpm=2000))
    e.ingest(row('mp48',100,rpm=1200,mapBar=.4))
    e.ingest(row('obd',200,rpm=1200))
    self.assertLessEqual(e.report()['synchronization']['rpm']['pairs'],1)

if __name__=='__main__':unittest.main()
