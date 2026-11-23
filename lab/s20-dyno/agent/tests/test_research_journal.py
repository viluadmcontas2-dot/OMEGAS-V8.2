import unittest,tempfile,sys,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from research_journal import ResearchJournal, build_research_brief

FINDINGS=[
 {'id':'rpm-cross-check','title':'RPM','state':'OBSERVADO','evidence':'145 pares; 6 RPM',
  'nextStep':'Comparar por marcha lenta','category':'telemetria'},
 {'id':'tps-rpm-response','title':'TPS','state':'EM_INVESTIGACAO','evidence':'20 eventos, 15 respostas',
  'nextStep':'Conferir carga','category':'telemetria'}]

class JournalTests(unittest.TestCase):
 def test_upsert_deduplicates_and_persists(self):
  with tempfile.TemporaryDirectory() as d:
   db=Path(d)/'evidence.sqlite3'
   j=ResearchJournal(db)
   self.assertEqual(j.observe(FINDINGS,now_ms=100),2)
   self.assertEqual(j.observe(FINDINGS,now_ms=200),0)
   self.assertEqual(len(j.get_findings()),2)
   j.close()
   k=ResearchJournal(db)
   self.assertEqual(len(k.get_findings()),2)
   self.assertEqual(k.stats()['updates'],2)
   k.close()
 def test_new_evidence_preserves_old_version_in_history(self):
  with tempfile.TemporaryDirectory() as d:
   j=ResearchJournal(Path(d)/'db.sqlite3')
   j.observe(FINDINGS,100)
   changed=[dict(FINDINGS[0],evidence='250 pares; 5 RPM'),FINDINGS[1]]
   self.assertEqual(j.observe(changed,200),1)
   self.assertEqual(j.stats()['updates'],3)
   self.assertEqual(j.get_findings()[0]['evidence'],'250 pares; 5 RPM')
   j.close()
 def test_ai_review_is_separate_and_cannot_mark_verified(self):
  with tempfile.TemporaryDirectory() as d:
   j=ResearchJournal(Path(d)/'db.sqlite3')
   j.observe(FINDINGS,100)
   j.record_gemini('tps-rpm-response','Suggest compare modes; not proof',model='gemini-X',now_ms=300)
   a=j.get_reviews()
   self.assertEqual(a[0]['findingId'],'tps-rpm-response')
   self.assertEqual(a[0]['status'],'INTERPRETACAO_NAO_COMPROVADA')
   self.assertEqual(j.get_findings()[1]['state'],'EM_INVESTIGACAO')
   j.close()
 def test_safe_brief_contains_only_aggregates(self):
  r={'discovery':{'findings':FINDINGS,
     'synchronization':{'rpm':{'pairs':145,'medianAbsDiff':6}},
     'throttle':{'throttleEvents':20,'qualifiedResponses':15,'resolutionUncertaintyMs':960},
     'states':{'0x94':{'count':30}}},
     'controlled':{'matchedPairs':20},'obdOnly':{'decision':'SEM_MODELO_VALIDADO'}}
  brief=build_research_brief(r)
  blob=json.dumps(brief)
  self.assertIn('tps-rpm-response',blob)
  self.assertNotIn('rawHex',blob)
  self.assertNotIn('apiKey',blob)
  self.assertTrue(brief['rules']['noEcuWrites'])

if __name__=='__main__':unittest.main()

class OriginalProtocolKnowledgeTests(unittest.TestCase):
 def test_brief_carries_source_mark_and_prohibits_invented_proof(self):
    from research_journal import build_research_brief
    knowledge=[{'id':'PROG-DELTA-RPM','source':'docs/progbase/protocolo/anti-apagao-quadros-original.md',
      'claim':'delta RPM signed 16 in response; channel unknown','mark':'OBS-COD',
      'unknown':'48 xx and firmware sample period'}]
    brief=build_research_brief({'discovery':{}},knowledge=knowledge)
    self.assertEqual(brief['originalReferences'][0]['id'],'PROG-DELTA-RPM')
    self.assertIn('channel unknown',brief['originalReferences'][0]['claim'])
    self.assertTrue(brief['rules']['noEcuWrites'])
