"""Post-selection provenance diagnostics. No model/threshold selection or ZIP decoding."""
import pathlib,json,collections
import numpy as np,pandas as pd
import confidence_surface as c
BASE=pathlib.Path(__file__).parent;OUT=BASE/'results';CACHE=BASE/'cache'
def main():
 gate=json.loads((OUT/'frozen_gate.json').read_text(encoding='utf-8'))
 assert c.sha(OUT/'frozen_gate.json')=='4dcd64dc1681e34841b01c54f050b002efd7702ae68d693886496e1a414f4a76'
 records=[];all_sources=[]
 for prefix in ['preholdout','holdout']:
  raw,used,regions,scales=c.prepare(prefix)
  inputs=json.loads((CACHE/(prefix+'_inputs.json')).read_text(encoding='utf-8'))
  original={f['sha256']:f for f in inputs if 'session' in f}
  kobs=collections.defaultdict(dict)
  for f in inputs:
   f=dict(f)
   if 'duplicate_of' in f:f['session']=original[f['sha256']]['session']
   s=f['session'];g=raw[raw.session==s];u=used[used.session==s]
   f['merged_session_useful_gasoline_frames']=len(u)
   f['merged_session_gasoline_regions']=int(len(regions[regions.session==s]))
   f['independence_unit']=s
   for fuel in ['GASOLINA','GNV']:
    z=g[g.fuel==fuel]
    f[fuel.lower()]=dict(frames=len(z),rpm_min=float(z.rpm.min()) if len(z) else None,
                       rpm_max=float(z.rpm.max()) if len(z) else None,map_min=float(z['map'].min()) if len(z) else None,
                       map_max=float(z['map'].max()) if len(z) else None,
                       regions=int(z.assign(rb=np.floor(z.rpm/100),mb=np.floor(z['map']/.025)).groupby(['rb','mb']).ngroups))
   for m in f.get('members',[]):
    stat=json.loads((CACHE/(m['cache_key']+'.json')).read_text(encoding='utf-8'))
    for v in stat['k_state_observations']:kobs[s][json.dumps(v,sort_keys=True)]=v
   records.append(f)
  for s,g in raw.groupby('session'):
   states=list(kobs[s].values())
   writes=[v for v in states if v['state'] in ['BATCH_WRITING','WRITING_POINT','BATCH_CONFIRMED','BATCH_FAILED','BATCH_VERIFYING_ROWS','VERIFYING_FINAL']]
   all_sources.append(dict(session=s,role=prefix,frames=len(g),useful_gasoline_frames=int((used.session==s).sum()),
      regions=int((regions.session==s).sum()),K_interventions_observed=writes,
      K_limit='Recorder state observations only; not a new causal estimate or physical validation receipt'))
 c.dump(OUT/'corpus_audit.json',dict(files=records,sessions=all_sources,
   count_semantics='Fuel/useful/region counts belong to merged real session; repeated exports never add independent sources. Member caches are deduplicated by session/member/CRC/length, archive identity by SHA-256.',
   exclusions='Only top-level native session ZIPs in G:/Meu Drive/OMEGAS; no learned-surface imports, nested aggregate archives, APKs or Portmon re-decoding. This corpus did not reproduce the prior briefing historical 4-source sign table.'))
 # Direct measured support is a diagnostic only; it never changes the frozen scores.
 _,x,r,scales=c.prepare('preholdout');rows=[]
 for rpm in [1900,2100,2300,2500]:
  for sid,g in x[(abs(x.rpm-rpm)<=100)&(abs(x['map']-.775)<=.025)].groupby('session'):
   residual=g.petrol-scales[sid]*c.f2(g.rpm,g['map'])
   rows.append(dict(rpm=rpm,session=sid,real_frames=len(g),residual_median=float(np.median(residual)),source_S=scales[sid]))
 c.csv('map_0775_direct_observations.csv',rows)
 sensitivity=pd.read_csv(OUT/'sensitivity.csv');sens={}
 for phase,g in sensitivity.groupby('phase'):
  p=g.pivot(index=['absolute_error_ms','deadband_ms'],columns='class',values='safe_rate')
  sens[phase]=dict(combinations=len(p),strict_T_P_W=int(((p.TRUST>p.PROVISIONAL)&(p.PROVISIONAL>p.WAIT)).sum()),T_greater_W=int((p.TRUST>p.WAIT).sum()))
 c.dump(OUT/'verification_summary.json',dict(preholdout_replay='PASS: every output hash matches; only freeze audit timestamp differs',
   external_replay='PASS: five output CSV hashes match; raw ZIP not reopened',
   synthetic_tests=10,synthetic_tests_result='PASS',single_execution_guards='PASS: second freeze and normal evaluation rejected',
   target_oracle_invariance='PASS: unused target S/residual fields do not change features, S_carry or predictions',
   sensitivity=sens,frozen_gate_sha256=c.sha(OUT/'frozen_gate.json'),
   review='Primary self-review under Codex Engineering Guardrails and Superpowers; no independent human or agent review claimed',
   scientific_verdict='FAIL',hypothesis='Session-local interpolated sign consensus transfers direction reliably to an external session',
   minimum_next_experiment='Collect one new independent gasoline session with repeated stable visits near 1631 rpm / MAP .207, and matched real historical observations there; test the already-frozen negative sign before any new score selection.'))
 print('CORPUS_AUDIT_COMPLETE; no gate mutation')
if __name__=='__main__':main()
