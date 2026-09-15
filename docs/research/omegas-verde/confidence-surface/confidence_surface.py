"""VERDE-CONFIDENCE-OFFLINE-001 / #47. Frozen preholdout selection and external audit.
Source authority: remote OmegasVerde. Cache is derived evidence, not authority.
No runtime imports; no ECU, network or app mutation.
"""
import argparse, hashlib, json, pathlib, platform, sys, datetime
import numpy as np
import pandas as pd
import scipy
from scipy.stats import beta, spearmanr
from scipy.spatial import cKDTree

BASE=pathlib.Path(__file__).parent
CACHE=BASE/'cache'
OUT=BASE/'results'
COEFFICIENTS=[2.14396620,.34083653,1.87748601,-.66741389,-.54659976,1.24856920,-.32307503,1.27193350,2.00071259,.47671656,-.25959427]
FEATURES=['distance','sources','sign','spread','maturity']
CONFIG=dict(rpm_scale=240.,map_scale=.06,neighbors=60,ridge=.001,radius=2.5,
            grid_rpm=100.,grid_map=.025,spread_scale_ms=.25,maturity_visits=4,
            min_rpm=600,max_rpm=4500,min_map=.2,max_map=1.05,min_water=40,
            source_target=4,prior_initial_S=1.,external_S=1.065612,
            minimum_TRUST_sources=3,minimum_TRUST_sign_posterior=.8,
            absolute_error_ms=.30,direction_deadband_ms=.12,
            threshold_pairs=[[.5,.7],[.6,.8],[.7,.9]],
            rng_seed=470015,bootstrap_repetitions=2000)
WEIGHTS={'balanced':[1,1,1,1,1],'consensus':[1,1,2,2,1],'minimum':[1,1,1,1,1]}
CLASSES=['TRUST','PROVISIONAL','WAIT']

def sha(p):
 h=hashlib.sha256()
 with open(p,'rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def dump(p,x):
 p.write_text(json.dumps(x,ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')
def csv(name,rows):
 pd.DataFrame(rows).to_csv(OUT/name,index=False,float_format='%.12g')
def f2(rpm,mapbar):
 d,a,b,c,e,f,g,h,i,j,k=COEFFICIENTS;x=np.log(np.asarray(mapbar)/.4);z=np.log(np.asarray(rpm)/1800)
 return d+np.exp(a+b*x+c*z+e*x*x+f*z*z+g*x*z+h*x**3+i*z**3+j*x*x*z+k*x*z*z)
def prepare(prefix):
 raw=pd.read_csv(CACHE/(prefix+'_frames.csv.gz'),low_memory=False)
 if prefix=='preholdout':
  assert not raw.session.str.contains('21-25').any()
  assert raw.ts.max()<1789345500000
 x=raw[(raw.fuel=='GASOLINA') & raw.rpm.between(CONFIG['min_rpm'],CONFIG['max_rpm']) &
       raw['map'].between(CONFIG['min_map'],CONFIG['max_map']) & raw.petrol.between(.7,30) &
       (raw.water>=CONFIG['min_water']) & (raw.state=='SAMPLE_ACCEPTED') & (raw.plausible!=False)].copy()
 # Coalesce each real session / measured region; no frame-weighted evaluation.
 x['rb']=np.floor(x.rpm/CONFIG['grid_rpm']).astype(int)
 x['mb']=np.floor(x['map']/CONFIG['grid_map']).astype(int)
 x['visit']=(x.ts//5000).astype(int)
 x['ratio']=x.petrol/f2(x.rpm,x['map'])
 scales=x.groupby('session').ratio.median().to_dict()
 # Owner-provided preholdout carry-forward estimate, never external oracle.
 for s in scales:
  if '2026-09-13_19-12' in s:scales[s]=CONFIG['external_S']
 rows=[]
 for (s,rb,mb),g in x.groupby(['session','rb','mb'],sort=True):
  # Representative coordinates are an ACTUAL observed gasoline frame (nearest medoid).
  rr=g.rpm.median();mm=g['map'].median()
  ix=((g.rpm-rr)**2/240**2+(g['map']-mm)**2/.06**2).idxmin()
  p=g.loc[ix];target=float(g.petrol.median())
  rows.append(dict(session=s,rb=int(rb),mb=int(mb),rpm=float(p.rpm),map=float(p['map']),
              actual=target,frames=len(g),visits=int(g.visit.nunique()),S=scales[s],
              residual=target-float(scales[s]*f2(p.rpm,p['map'])),first_ts=float(g.ts.min())))
 return raw,x,pd.DataFrame(rows),scales

def support(groups,q):
 estimates=[];nearest=float('inf');counts=[];cells=[]
 for sid,g,tree in groups:
  coord=np.array([q[0]/240,q[1]/.06]);dist,idx=tree.query(coord,k=min(60,len(g)))
  dist=np.atleast_1d(dist);idx=np.atleast_1d(idx);nearest=min(nearest,float(dist[0]))
  ok=dist<=2.5;dist=dist[ok];idx=idx[ok]
  if not len(idx):continue
  a=g.iloc[idx];u=(a.rpm.to_numpy()-q[0])/240;v=(a['map'].to_numpy()-q[1])/.06
  w=np.exp(-.5*dist**2);y=a.residual.to_numpy()
  if len(idx)>=6:
   X=np.column_stack([np.ones(len(u)),u,v,u*u,v*v,u*v])
   penalty=np.diag([0,.001,.001,.001,.001,.001])
   b=np.linalg.solve(X.T@(w[:,None]*X)+penalty,X.T@(w*y));est=float(b[0])
  else:est=float(np.average(y,weights=w))
  estimates.append(est);counts.append(min(float(a.visits.sum()),4));cells.append(len(a))
 n=len(estimates)
 if n:
  pos=int(np.sum(np.array(estimates)>0));neg=int(np.sum(np.array(estimates)<0))
  # Exact zeros are neutral, not evidence for either sign.
  signed=pos+neg;k=max(pos,neg)
  posterior=float(beta.sf(.5,k+.5,signed-k+.5)) if signed else .5
  med=float(np.median(estimates));mad=float(1.4826*np.median(np.abs(np.array(estimates)-med)))
  maturity=float(np.mean(counts)/4)
 else:pos=neg=0;posterior=.5;med=0.;mad=0.;maturity=0.
 return dict(distance=float(np.exp(-.5*nearest**2)),sources=min(n/4,1.),
             sign=max(0.,2*posterior-1),spread=float(np.exp(-mad/.25)) if n else 0.,
             maturity=maturity,nearest_scaled_distance=nearest if np.isfinite(nearest) else 1e6,
             n_sources=n,positive=pos,negative=neg,sign_posterior=posterior,
             mad_ms=mad,predicted_residual=med,support_cells=sum(cells))

def groups_for(df):
 return [(s,g.reset_index(drop=True),cKDTree(np.column_stack([g.rpm/240,g['map']/.06])))
         for s,g in df.groupby('session',sort=True)]
def predictions(regions,scales,external=None):
 rows=[];sessions=sorted(scales)
 targets=regions if external is None else external
 for sid,g in targets.groupby('session',sort=True):
  train=regions[regions.session!=sid] if external is None else regions
  groups=groups_for(train)
  prior=CONFIG['external_S'] if external is not None else (scales[sessions[sessions.index(sid)-1]] if sessions.index(sid) else 1.)
  for row in g.to_dict('records'):
   ep=support(groups,(row['rpm'],row['map']))
   base=float(prior*f2(row['rpm'],row['map']));pred=base+ep['predicted_residual']
   rows.append({**row,**ep,'S_carry':prior,'predicted':pred,'error':pred-row['actual'],
                'abs_error':abs(pred-row['actual']),'true_residual':row['actual']-base})
 return pd.DataFrame(rows)
def scores(df,family,removed=None):
 use=[i for i,f in enumerate(FEATURES) if f!=removed]
 a=np.clip(df[[FEATURES[i] for i in use]].to_numpy(float),0,1)
 if family=='minimum':return a.min(axis=1)
 return np.exp(np.average(np.log(np.maximum(a,1e-15)),axis=1,weights=np.array(WEIGHTS[family])[use]))
def classify(df,score,pt,tt,removed=None):
 # Eligibility remains separate from reference availability.
 trust=(score>=tt)
 if removed!='sources':trust &= df.n_sources.to_numpy()>=3
 if removed!='sign':trust &= df.sign_posterior.to_numpy()>=.8
 provisional=(score>=pt)
 if removed!='sources':provisional &= df.n_sources.to_numpy()>=2
 return np.where(trust,'TRUST',np.where(provisional,'PROVISIONAL','WAIT'))
def metrics(df,ae=.3,db=.12):
 n=len(df)
 if not n:return dict(N=0,sessions=0,safe_rate=None,MAE=None,P90=None,direction_N=0,direction_accuracy=None,jeffreys_low=None,jeffreys_high=None,session_macro_safe=None)
 directed=np.abs(df.true_residual.to_numpy())>db
 correct=np.sign(df.true_residual.to_numpy())==np.sign(df.predicted_residual.to_numpy())
 safe=(df.abs_error.to_numpy()<=ae)&(~directed|correct);k=int(safe.sum())
 macro=pd.DataFrame({'s':df.session.to_numpy(),'safe':safe}).groupby('s').safe.mean().mean()
 return dict(N=n,sessions=int(df.session.nunique()),safe_rate=float(safe.mean()),MAE=float(df.abs_error.mean()),
             P90=float(df.abs_error.quantile(.9)),direction_N=int(directed.sum()),
             direction_accuracy=float(correct[directed].mean()) if directed.any() else None,
             jeffreys_low=float(beta.ppf(.025,k+.5,n-k+.5)),jeffreys_high=float(beta.ppf(.975,k+.5,n-k+.5)),
             session_macro_safe=float(macro))
def summaries(df,phase,ae=.3,db=.12):
 return [{'phase':phase,'class':c,'coverage':float((df['class']==c).mean()),**metrics(df[df['class']==c],ae,db)} for c in CLASSES]
def reliability(df,phase):
 out=[]
 for lo in np.arange(0,1,.1):
  g=df[(df.score>=lo)&(df.score<lo+.1+ (1e-10 if lo>.89 else 0))]
  out.append(dict(phase=phase,low=float(lo),high=float(lo+.1),**metrics(g)))
 return out
def sensitivity(df,phase):
 out=[]
 for ae in [.20,.25,.30,.35,.40]:
  for db in [.08,.10,.12,.15,.20]:
   out.extend([{**r,'absolute_error_ms':ae,'deadband_ms':db} for r in summaries(df,phase,ae,db)])
 return out
def cluster_bootstrap(df):
 # Cluster resampling by source; region Jeffreys intervals are descriptive only.
 rng=np.random.default_rng(CONFIG['rng_seed']);sessions=np.array(sorted(df.session.unique()));deltas=[]
 for _ in range(CONFIG['bootstrap_repetitions']):
  parts=[df[df.session==s] for s in rng.choice(sessions,len(sessions),replace=True)]
  q=pd.concat(parts);t=metrics(q[q['class']=='TRUST']);w=metrics(q[q['class']=='WAIT'])
  if t['N'] and w['N']:deltas.append(t['safe_rate']-w['safe_rate'])
 if not deltas:return {'status':'no comparable class support'}
 return dict(replicates=len(deltas),safe_gap_low=float(np.quantile(deltas,.025)),safe_gap_high=float(np.quantile(deltas,.975)),prob_gap_positive=float(np.mean(np.array(deltas)>0)))
def audit(regions):
 groups=groups_for(regions);rows=[]
 for r in [1900,2100,2300,2500]:
  rows.append(dict(rpm=r,map=.775,**support(groups,(r,.775))))
 return pd.DataFrame(rows)
def freeze():
 OUT.mkdir(exist_ok=True)
 assert not (OUT/'frozen_gate.json').exists(), 'immutable freeze already exists'
 raw,use,regions,scales=prepare('preholdout')
 csv('corpus_regions.csv',regions);csv('source_scales.csv',[{'session':s,'S':v} for s,v in scales.items()])
 pred=predictions(regions,scales)
 choices=[]
 for family in WEIGHTS:
  score=scores(pred,family)
  for pt,tt in CONFIG['threshold_pairs']:
   labels=classify(pred,score,pt,tt);d=pred.assign(score=score,**{'class':labels})
   m={c:metrics(d[d['class']==c]) for c in CLASSES}
   valid=all(m[c]['N']>=10 and m[c]['sessions']>=2 for c in CLASSES)
   gap=m['TRUST']['safe_rate']-m['WAIT']['safe_rate'] if m['TRUST']['N'] and m['WAIT']['N'] else -1.
   # Fixed lexicographic selection: valid class support, macro TRUST safety, gap, lower MAE.
   key=(valid,m['TRUST']['session_macro_safe'] or -1.,gap,-(m['TRUST']['MAE'] or 1e6))
   choices.append((key,family,pt,tt))
   for c in CLASSES:csvrow=dict(family=family,provisional_threshold=pt,trust_threshold=tt,valid_support=valid,**{'class':c},**m[c]); 
   # Accumulate all class metrics explicitly.
 if not choices:raise RuntimeError('no candidates')
 selected=max(choices,key=lambda v:v[0]);_,family,pt,tt=selected
 selection=[]
 for key,f,p,t in choices:
  d=pred.assign(score=scores(pred,f));d['class']=classify(d,d.score.to_numpy(),p,t)
  for row in summaries(d,'preholdout'):
   selection.append(dict(family=f,provisional_threshold=p,trust_threshold=t,selected=(f,p,t)==(family,pt,tt),valid_support=bool(key[0]),**row))
 csv('gate_selection.csv',selection)
 pred['score']=scores(pred,family);pred['class']=classify(pred,pred.score.to_numpy(),pt,tt)
 csv('preholdout_loso.csv',pred)
 csv('preholdout_classes.csv',summaries(pred,'preholdout'))
 ab=[]
 for removed in [None]+FEATURES:
  d=pred.copy();d['score']=scores(d,family,removed);d['class']=classify(d,d.score.to_numpy(),pt,tt,removed)
  ab.extend([dict(removed=removed or 'none',**row) for row in summaries(d,'ablation')])
 # Permute epistemic features together between regions, preserving predictions and outcomes.
 shuffled=pred.copy();rng=np.random.default_rng(CONFIG['rng_seed']);ix=rng.permutation(len(pred))
 cols=FEATURES+['n_sources','sign_posterior']
 shuffled[cols]=pred.iloc[ix][cols].to_numpy()
 shuffled['score']=scores(shuffled,family);shuffled['class']=classify(shuffled,shuffled.score.to_numpy(),pt,tt)
 ab.extend([dict(removed='adversarial_feature_permutation',**row) for row in summaries(shuffled,'ablation')])
 csv('feature_ablation.csv',ab)
 csv('sensitivity.csv',sensitivity(pred,'preholdout'));csv('reliability.csv',reliability(pred,'preholdout'))
 a=audit(regions);a['score']=scores(a,family);a['class']=classify(a,a.score.to_numpy(),pt,tt);csv('map_0775_audit.csv',a)
 inp=json.loads((CACHE/'preholdout_inputs.json').read_text(encoding='utf-8'))
 metadata=[]
 for s,g in raw.groupby('session'):
  accepted=use[use.session==s]
  gas=g[g.fuel=='GASOLINA'];gnv=g[g.fuel=='GNV']
  metadata.append(dict(session=s,frames_total=len(g),gasoline_frames=len(gas),gnv_frames=len(gnv),useful_gasoline_frames=len(accepted),
     gasoline_regions=int(len(regions[regions.session==s])),gnv_regions=int(gnv.assign(rb=np.floor(gnv.rpm/100),mb=np.floor(gnv['map']/.025)).groupby(['rb','mb']).ngroups),
     train=bool(len(accepted)),excluded_reason=None if len(accepted) else 'no eligible gasoline evidence'))
 inputs={'files':inp,'sessions':metadata,'cache_sha256':sha(CACHE/'preholdout_frames.csv.gz')}
 dump(OUT/'preholdout_manifest.json',inputs)
 gate=dict(workunit='VERDE-CONFIDENCE-OFFLINE-001',issue=47,family=family,weights=WEIGHTS[family],features=FEATURES,
   provisional_threshold=pt,trust_threshold=tt,config=CONFIG,f2_coefficients=COEFFICIENTS,
   formula='balanced/consensus: exp(sum(w_i*ln(max(c_i,1e-15)))/sum(w_i)); minimum: min(c_i)',
   components={'distance':'exp(-nearest_real_scaled_distance^2/2)','sources':'min(n_sources/4,1)',
     'sign':'max(0,2*P(Beta(max(n_pos,n_neg)+.5,min(n_pos,n_neg)+.5)>.5)-1)',
     'spread':'exp(-1.4826*MAD(session residual estimates)/.25)','maturity':'mean_over_sources(min(nearby distinct 5-second region visits,4)/4)'},
   authority='TRUST if score>=trust threshold AND sources>=3 AND sign posterior>=.8; else PROVISIONAL if score>=provisional threshold AND sources>=2; otherwise WAIT',
   local_reference='median of session-local quadratic estimates (240 rpm,.06 bar,60 neighbors,radius 2.5,ridge .001,unpenalized intercept); weighted mean for fewer than 6 regional observations; 0 if none',
   scale_policy='Each source normalized by its accepted-gasoline median petrol/F2; target uses previous source S (initial 1). Owner frozen S19=1.065612 is carried to external; no external normalization.',
   selection_rule='max lexicographic(valid all classes>=10 regions and >=2 sources, TRUST session-macro safe-rate, TRUST minus WAIT safe-rate, negative TRUST MAE); no external data',
   frozen_at_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
   versions={'python':platform.python_version(),'numpy':np.__version__,'pandas':pd.__version__,'scipy':scipy.__version__},
   inputs=inputs,script_sha256=sha(pathlib.Path(__file__)),cache_builder_sha256=sha(BASE/'build_cache.py'),
   preholdout_metrics=summaries(pred,'preholdout'),cluster_bootstrap=cluster_bootstrap(pred),
   limitations=['Prior briefing exposed partial external outcomes: file-level isolation, not absolute human blinding.',
    'Historical frame acceptance criteria came from recorder versions; no new runtime eligibility model.',
    'S estimates can be biased by visited regions; local residual direction is relative to carried prior.',
    'Region Jeffreys intervals do not establish independent observations; source bootstrap used for LOSO.',
    'LOSO supports static cross-session transfer, not fully prospective chronological training.',
    'Named raw/Portmon bundles and imported learned surfaces excluded; only top-level native session exports with real telemetry included.',
    'Fallback weighted mean for <6 regional observations is declared; never counted as observation.'])
 gate['preholdout_output_hashes']={p.name:sha(p) for p in sorted(OUT.iterdir()) if p.is_file()}
 dump(OUT/'frozen_gate.json',gate)
 (OUT/'frozen_gate.sha256').write_text(sha(OUT/'frozen_gate.json')+'\n',encoding='ascii')
 print(json.dumps({'family':family,'thresholds':[pt,tt],'metrics':gate['preholdout_metrics'],'bootstrap':gate['cluster_bootstrap'],'frozen_hash':sha(OUT/'frozen_gate.json')},indent=2))

def evaluate():
 gate=json.loads((OUT/'frozen_gate.json').read_text(encoding='utf-8'))
 assert sha(OUT/'frozen_gate.json')==(OUT/'frozen_gate.sha256').read_text().strip()
 assert sha(pathlib.Path(__file__))==gate['script_sha256'],'source changed since freeze'
 assert sha(CACHE/'preholdout_frames.csv.gz')==gate['inputs']['cache_sha256']
 assert not (OUT/'holdout_21_25.csv').exists(),'external evaluation already recorded'
 _,_,regions,scales=prepare('preholdout');_,_,hold,_=prepare('holdout')
 d=predictions(regions,scales,external=hold);family=gate['family'];pt=gate['provisional_threshold'];tt=gate['trust_threshold']
 d['score']=scores(d,family);d['class']=classify(d,d.score.to_numpy(),pt,tt)
 csv('holdout_21_25.csv',d);csv('holdout_classes.csv',summaries(d,'holdout'))
 pre=pd.read_csv(OUT/'preholdout_loso.csv')
 csv('sensitivity.csv',sensitivity(pre,'preholdout')+sensitivity(d,'holdout'))
 csv('reliability.csv',reliability(pre,'preholdout')+reliability(d,'holdout'))
 a=pd.read_csv(OUT/'map_0775_audit.csv')
 for ix,row in a.iterrows():
  near=d[(np.abs(d.rpm-row.rpm)<=100)&(np.abs(d['map']-.775)<=.025)]
  a.loc[ix,'holdout_N']=len(near)
  a.loc[ix,'holdout_true_residual_median']=float(near.true_residual.median()) if len(near) else np.nan
  a.loc[ix,'holdout_MAE']=float(near.abs_error.mean()) if len(near) else np.nan
 csv('map_0775_audit.csv',a)
 dump(OUT/'holdout_receipt.json',dict(evaluated_at_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),frozen_gate_sha256=sha(OUT/'frozen_gate.json'),
       input_files=json.loads((CACHE/'holdout_inputs.json').read_text()),holdout_cache_sha256=sha(CACHE/'holdout_frames.csv.gz'),metrics=summaries(d,'holdout')))
 print(json.dumps(summaries(d,'holdout'),indent=2))

def check():
 assert abs(float(f2(1800,.4))-(2.14396620+np.exp(.34083653)))<1e-12
 assert support([], (1900,.775))['sources']==0
 a=support([], (1900,.775)); assert a['predicted_residual']==0 and a['sign']==0
 df=pd.DataFrame([dict(**a)])
 for f in WEIGHTS:
  assert classify(df,scores(df,f),.5,.7)[0]=='WAIT'
 assert beta.sf(.5,4.5,.5)>beta.sf(.5,3.5,1.5)>beta.sf(.5,2.5,1.5)
 q=pd.DataFrame([{**a,'distance':1,'sources':1,'sign':1,'spread':1,'maturity':1,'n_sources':1,'sign_posterior':1}])
 assert classify(q,scores(q,'balanced'),.5,.7)[0]=='WAIT'
 q.n_sources=4;q.sign_posterior=.7
 assert classify(q,scores(q,'balanced'),.5,.7)[0]!='TRUST'
 print('6 scientific invariant checks PASS')

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('command',choices=['freeze','evaluate','check']);args=p.parse_args()
 {'freeze':freeze,'evaluate':evaluate,'check':check}[args.command]()
