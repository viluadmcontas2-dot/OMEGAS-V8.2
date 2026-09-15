"""Read preholdout session exports once; content-addressed compact telemetry cache."""
import argparse, hashlib, json, pathlib, zipfile, io
import pandas as pd

ROOT=pathlib.Path('G:/Meu Drive/OMEGAS')
BASE=pathlib.Path(__file__).parent
CACHE=BASE/'cache'
HOLD='OMEGAS_Sessao_2026-09-13_21-25.zip'
CUTOFF=1789345500000  # 2026-09-14 00:25 UTC; temporal embargo
def sha(p):
 h=hashlib.sha256()
 with open(p,'rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def dump(p,x):p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def parse_member(z,m,sid):
 rows=[]; interventions=[]; types={}; bad=0
 with z.open(m) as f:
  for line in f:
   # Avoid decoding large snapshots except compact K state fingerprints.
   try:o=json.loads(line)
   except (ValueError,UnicodeError):bad+=1;continue
   t=o.get('type','');types[t]=types.get(t,0)+1;d=o.get('data',{}); ts=o.get('recordedAtMs',0)
   if t=='telemetry' and isinstance(d,dict):
    rows.append(dict(session=sid,seq=o.get('sequence'),ts=ts,frame=d.get('captured_elapsed_ms',d.get('last_frame_at',ts)),rpm=d.get('rpm'),map=d.get('load_bar'),petrol=d.get('petrol_ms'),raw=d.get('petrol_raw'),fuel=d.get('fuel'),water=d.get('water_c'),eligible=(d.get('sample') or {}).get('learning_eligible'),state=d.get('sample_state'),schema=d.get('telemetry_scale_schema','legacy'),plausible=d.get('base_plausible',d.get('plausible',True))))
   elif t=='full_snapshot' and isinstance(d,dict):
    for key in ['k_write','k_factor']:
     v=d.get(key,{})
     if isinstance(v,dict) and v.get('state'):
      item={'kind':key,'state':v['state'],'updatedAt':v.get('updatedAt'),'message':v.get('message','')[:180]}
      if item not in interventions:interventions.append(item)
 return rows,{'event_types':types,'malformed':bad,'k_state_observations':interventions}
def run(holdout=False):
 CACHE.mkdir(parents=True,exist_ok=True)
 if holdout:
  assert (BASE/'results/frozen_gate.json').exists(), 'freeze required'
  names=[HOLD]
 else:
  names=sorted(p.name for p in ROOT.glob('*.zip') if p.name!=HOLD and (p.name.startswith('OMEGAS_Sessao_') or p.name in ['gasolina casa ponto.zip','itaigara.zip','lauro.zip','quarta.zip','stels.zip','unknow.zip','sessao gnv barra.zip']))
 records=[]; allframes=[]; used_members=set(); ziphash={}
 for name in names:
  p=ROOT/name
  if not holdout:assert '21-25' not in name
  digest=sha(p);rec={'file':name,'sha256':digest,'role':'holdout' if holdout else 'preholdout'}
  if digest in ziphash:
   rec.update(duplicate_of=ziphash[digest]);records.append(rec);continue
  ziphash[digest]=name
  with zipfile.ZipFile(p) as z:
   if 'manifest.json' not in z.namelist():
    rec['excluded']='no top-level session manifest';records.append(rec);continue
   meta=json.loads(z.read('manifest.json'));sid=meta['sessionId'];created=meta.get('createdAtMs',0)
   rec.update(session=sid,createdAtUtc=meta.get('createdAtUtc'),createdAtMs=created,members=[])
   if not holdout and (not created or created>=CUTOFF or '21-25' in sid):raise RuntimeError('temporal embargo '+name)
   for m in sorted(z.namelist()):
    if not (m.startswith('events_') and m.endswith('.jsonl')):continue
    info=z.getinfo(m); key=hashlib.sha256(f'{sid}:{m}:{info.CRC}:{info.file_size}'.encode()).hexdigest()
    cache=CACHE/(key+'.csv.gz');audit=CACHE/(key+'.json')
    rec['members'].append({'name':m,'crc32':info.CRC,'bytes':info.file_size,'cache_key':key})
    if key in used_members:continue
    used_members.add(key)
    if not cache.exists():
     rows,stats=parse_member(z,m,sid)
     pd.DataFrame(rows,columns=['session','seq','ts','frame','rpm','map','petrol','raw','fuel','water','eligible','state','schema','plausible']).to_csv(cache,index=False,compression={'method':'gzip','mtime':0})
     dump(audit,stats)
    rec['members'][-1]['cache_sha256']=sha(cache)
    allframes.append(pd.read_csv(cache))
   records.append(rec)
  print(name, sid, flush=True)
 frames=pd.concat(allframes,ignore_index=True).drop_duplicates(['session','frame','ts','rpm','map','petrol','fuel'])
 # Exports of the same real acquisition are one source, never independent sessions.
 frames=frames.sort_values(['session','ts','seq']).drop_duplicates(['session','frame'],keep='first')
 if not holdout:assert frames.ts.max()<CUTOFF
 prefix='holdout' if holdout else 'preholdout'
 frames.to_csv(CACHE/(prefix+'_frames.csv.gz'),index=False,compression={'method':'gzip','mtime':0})
 dump(CACHE/(prefix+'_inputs.json'),records)
 print(frames.groupby(['session','fuel']).size().to_string(),flush=True)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--holdout',action='store_true');a=p.parse_args();run(a.holdout)

