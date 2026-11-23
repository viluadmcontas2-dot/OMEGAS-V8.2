"""Durable read-only investigation memory. Models NEVER change evidence verdicts."""
from pathlib import Path
import sqlite3,json,hashlib,time

ALLOWED_STATES=frozenset({'OBSERVADO','EM_INVESTIGACAO','LACUNA'})
ALLOWED_IDS=frozenset({
 'sampling-cadence','rpm-cross-check','map-cross-check',
 'tps-rpm-response','mp48-state-94','fuel-pattern'
})

def build_research_brief(report):
    d=report.get('discovery') or {}
    known=[]
    for f in d.get('findings',[]):
        if not isinstance(f,dict) or f.get('id') not in ALLOWED_IDS:continue
        known.append({k:str(f.get(k,''))[:550] for k in
            ('id','title','state','evidence','nextStep')})
    return {
       'goal':'Encontrar padrões verificáveis e lacunas no Ômegas/ProgBase; priorize TPS→MAP/RPM e combustível',
       'facts':known[:8],
       'timing':{'throttle':{k:v for k,v in (d.get('throttle') or {}).items()
                     if k in ('throttleEvents','qualifiedResponses','resolutionUncertaintyMs','medianObservedRpmResponseMs','quality')},
                 'rpm':(d.get('synchronization') or {}).get('rpm'),
                 'map':(d.get('synchronization') or {}).get('map')},
       'fuel':{'reference':report.get('controlled'),'obdOnly':report.get('obdOnly')},
       'state94Frames':((d.get('states') or {}).get('0x94') or {}).get('count',0),
       'rules':{'noEcuWrites':True,'noCausalClaimsFromCorrelation':True,
           'noExactDelayBelowTpsResolution':True,
           'noAlcoholOrGnvGuessWithoutReference':True,
           'schema':'read-only advisory'}
    }

class ResearchJournal:
 def __init__(self,path):
  path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
  self.db=sqlite3.connect(path,timeout=4)
  self.db.execute('PRAGMA journal_mode=WAL')
  self.db.execute('''CREATE TABLE IF NOT EXISTS findings(
    id TEXT PRIMARY KEY, title TEXT, state TEXT, evidence TEXT,
    next_step TEXT, category TEXT, fingerprint TEXT, updated_ms INTEGER)''')
  self.db.execute('''CREATE TABLE IF NOT EXISTS revisions(
    seq INTEGER PRIMARY KEY AUTOINCREMENT, finding_id TEXT, old TEXT,
    revised TEXT, changed_ms INTEGER)''')
  self.db.execute('''CREATE TABLE IF NOT EXISTS ai_reviews(
    seq INTEGER PRIMARY KEY AUTOINCREMENT, finding_id TEXT, model TEXT,
    interpretation TEXT, status TEXT, created_ms INTEGER)''')
  self.db.commit()
 def close(self):self.db.close()
 def observe(self,findings,now_ms=None):
  stamp=int(now_ms if now_ms is not None else time.time()*1000)
  changes=0
  with self.db:
   for f in findings:
    if f.get('id') not in ALLOWED_IDS or f.get('state') not in ALLOWED_STATES:continue
    fid=f['id']
    normalized={k:str(f.get(k,''))[:1600] for k in
       ('id','title','state','evidence','nextStep','category')}
    fp=hashlib.sha256(json.dumps(normalized,sort_keys=True,ensure_ascii=False).encode()).hexdigest()
    old=self.db.execute('SELECT fingerprint,evidence FROM findings WHERE id=?',(fid,)).fetchone()
    if old and old[0]==fp:continue
    self.db.execute('''INSERT INTO findings(id,title,state,evidence,next_step,category,fingerprint,updated_ms)
      VALUES(?,?,?,?,?,?,?,?)
      ON CONFLICT(id) DO UPDATE SET
       title=excluded.title,state=excluded.state,evidence=excluded.evidence,
       next_step=excluded.next_step,category=excluded.category,
       fingerprint=excluded.fingerprint,updated_ms=excluded.updated_ms''',
      (fid,normalized['title'],normalized['state'],normalized['evidence'],
       normalized['nextStep'],normalized['category'],fp,stamp))
    self.db.execute('''INSERT INTO revisions(finding_id,old,revised,changed_ms)
       VALUES(?,?,?,?)''',(fid,old[1] if old else None,normalized['evidence'],stamp))
    changes+=1
  return changes
 def record_gemini(self,finding_id,interpretation,model,now_ms=None):
  if finding_id not in ALLOWED_IDS:return False
  if not isinstance(interpretation,str) or not interpretation.strip():return False
  stamp=int(now_ms if now_ms is not None else time.time()*1000)
  with self.db:
   self.db.execute('''INSERT INTO ai_reviews(finding_id,model,interpretation,status,created_ms)
    VALUES(?,?,?,?,?)''',(finding_id,str(model)[:80],interpretation[:2500],
                          'INTERPRETACAO_NAO_COMPROVADA',stamp))
  return True
 def get_findings(self):
  rows=self.db.execute('SELECT id,title,state,evidence,next_step,category,updated_ms FROM findings ORDER BY id').fetchall()
  return [{'id':i,'title':t,'state':s,'evidence':e,'nextStep':n,
           'category':c,'updatedAtEpochMs':ts} for i,t,s,e,n,c,ts in rows]
 def get_reviews(self,limit=10):
  rows=self.db.execute('SELECT finding_id,model,interpretation,status,created_ms FROM ai_reviews ORDER BY seq DESC LIMIT ?', (max(1,min(50,limit)),)).fetchall()
  return [{'findingId':i,'model':m,'text':t,'status':s,'atEpochMs':ts}
          for i,m,t,s,ts in rows]
 def stats(self):
  count=self.db.execute('SELECT COUNT(*) FROM revisions').fetchone()[0]
  reviews=self.db.execute('SELECT COUNT(*) FROM ai_reviews').fetchone()[0]
  return {'updates':count,'geminiReviews':reviews,'persistent':True}
