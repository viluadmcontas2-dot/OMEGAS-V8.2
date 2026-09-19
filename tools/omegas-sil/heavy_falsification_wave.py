#!/usr/bin/env python3
from __future__ import annotations
import argparse, concurrent.futures as cf, json, math
from pathlib import Path
import numpy as np, pandas as pd

CACHE=r"C:\Users\hugov\AppData\Local\AgentRed\cache\omegas-sil-v2\canonical_frames.csv.gz"
COLS=["rpm","petrol_ms","fuel","map_bar","session","sequence","dmap","drpm","gas_pressure_raw","gas_temp_raw","gas_ms"]

def load():
 d=pd.read_csv(CACHE,usecols=COLS)
 for c in ["rpm","petrol_ms","map_bar","sequence","dmap","drpm","gas_pressure_raw","gas_temp_raw","gas_ms"]:
  d[c]=pd.to_numeric(d[c],errors="coerce")
 d=d[d.rpm.between(500,6500)&d.map_bar.between(.1,1.2)&d.petrol_ms.between(.7,30)].copy()
 return d.sort_values(["session","sequence"]).reset_index(drop=True)

def metric(e):
 a=np.abs(np.asarray(e,float));a=a[np.isfinite(a)]
 if len(a)==0:return {"n":0,"mae":999.,"p90":999.,"within4":0.}
 return {"n":int(len(a)),"mae":float(a.mean()),"median":float(np.median(a)),
         "p90":float(np.quantile(a,.9)),"p99":float(np.quantile(a,.99)),
         "within4":float((a<=4).mean()*100),"worst":float(a.max())}

def gas_ref(train,test,rbin=180,mbin=.025):
 tr=train[train.fuel=="PETROL"].copy()
 tr["rb"]=(tr.rpm/rbin).round().astype(int);tr["mb"]=(tr.map_bar/mbin).round().astype(int)
 tab=tr.groupby(["rb","mb"]).petrol_ms.median()
 mp=tr.groupby("mb").petrol_ms.median();glob=float(tr.petrol_ms.median())
 out=[]
 for r in test.itertuples():
  rb=int(round(r.rpm/rbin));mb=int(round(r.map_bar/mbin))
  v=tab.get((rb,mb),np.nan)
  if not np.isfinite(v):v=mp.get(mb,glob)
  out.append(float(v))
 return np.asarray(out)

def cng(d,maxn=9000):
 parts=[]
 for s in d.session.unique():
  te=d[(d.session==s)&(d.fuel=="CNG")].copy();tr=d[d.session!=s]
  if len(te)<100 or len(tr[tr.fuel=="PETROL"])<500:continue
  if len(te)>maxn:te=te.iloc[np.linspace(0,len(te)-1,maxn).astype(int)].copy()
  rr=gas_ref(tr,te);te["ref"]=rr;te["err"]=(te.petrol_ms.to_numpy(float)/rr-1)*100
  te=te[np.isfinite(te.err)&(te.err.abs()<70)]
  parts.append(te)
 return pd.concat(parts,ignore_index=True) if parts else pd.DataFrame()

def strict_anchors(d):
 out=[]
 for s,g in d.groupby("session",sort=False):
  g=g.reset_index(drop=True)
  f=g.fuel.astype(str).to_numpy()
  for i in range(1,len(g)):
   if f[i]!="CNG" or f[i-1] not in ("PETROL","TRANSITION"):continue
   pre=g.iloc[max(0,i-12):i];post=g.iloc[i:min(len(g),i+12)]
   pre=pre[(pre.fuel=="PETROL")&pre.petrol_ms.between(.7,30)]
   post=post[(post.fuel=="CNG")&post.petrol_ms.between(.7,30)]
   if len(pre)<3 or len(post)<3:continue
   a=pre.iloc[-3:];b=post.iloc[:3]
   rg=abs(float(a.rpm.median())-float(b.rpm.median()));mg=abs(float(a.map_bar.median())-float(b.map_bar.median()))
   if rg<=50 and mg<=.02:
    pp=float(a.petrol_ms.median());cp=float(b.petrol_ms.median())
    out.append({"session":s,"sequence":int(b.sequence.iloc[0]),"rpm":float(b.rpm.median()),
                "map_bar":float(b.map_bar.median()),"petrol_ms":cp,"truth":(cp/pp-1)*100})
 return pd.DataFrame(out)

def wave1(d,i):
 # derivative-corrected gasoline surface: predict expected petrol_ms at current point using local plane.
 cfg=[(120,.015),(150,.02),(180,.025),(220,.03),(280,.035),(350,.04),(450,.05),(550,.06),(700,.08),(900,.1)][i]
 rb,mb=cfg;errs=[]
 for s in d.session.unique():
  tr=d[(d.session!=s)&(d.fuel=="PETROL")].copy();te=d[(d.session==s)&(d.fuel=="PETROL")].copy()
  if len(te)<100 or len(tr)<500:continue
  tr["rb"]=(tr.rpm/rb).round().astype(int);tr["mb"]=(tr.map_bar/mb).round().astype(int)
  groups={k:g for k,g in tr.groupby(["rb","mb"]) if len(g)>=5}
  glob=float(tr.petrol_ms.median())
  pred=[]
  for r in te.itertuples():
   k=(int(round(r.rpm/rb)),int(round(r.map_bar/mb)));g=groups.get(k)
   if g is None or len(g)<5:pred.append(glob);continue
   X=np.column_stack([np.ones(len(g)),(g.rpm-r.rpm)/rb,(g.map_bar-r.map_bar)/mb])
   b=np.linalg.lstsq(X,g.petrol_ms.to_numpy(float),rcond=None)[0];pred.append(float(b[0]))
  y=te.petrol_ms.to_numpy(float);errs.extend((np.asarray(pred)-y)/y*100)
 return {"variant":f"local_plane_r{rb}_m{mb}","metrics":metric(errs)}

def wave2(d,i):
 # adaptive drift/change-point estimator on continuous CNG correction
 x=cng(d);lams=[.005,.01,.02,.03,.05,.08,.12,.18,.25,.35];lam=lams[i];errs=[];changes=0
 for _,g in x.groupby("session"):
  state=None;mad=3.
  for e in g.err.to_numpy(float):
   if state is None:state=e;continue
   resid=e-state;errs.append(resid)
   mad=.95*mad+.05*abs(resid)
   if abs(resid)>max(4,2.5*mad):
    state=(1-.5)*state+.5*e;changes+=1
   else:state=(1-lam)*state+lam*e
 return {"variant":f"adaptive_drift_l{lam}","metrics":metric(errs),"change_points":changes}

def wave3(d,i):
 # Gaussian kernel residual on early CNG; no switching required.
 x=cng(d);cfg=[(100,.015,.25),(150,.02,.25),(200,.025,.5),(250,.03,.5),(300,.04,.5),
                (400,.05,.75),(500,.06,.75),(650,.08,1.0),(800,.1,1.0),(1000,.12,1.25)][i]
 br,bm,bp=cfg;errs=[];train=[]
 for _,g in x.groupby("session"):
  g=g.reset_index(drop=True);cut=max(30,int(.10*len(g)))
  if len(g)<cut+60:continue
  tr=g.iloc[:cut];te=g.iloc[cut:];train.append(cut);arr=tr[["rpm","map_bar","petrol_ms","err"]].to_numpy(float)
  for r in te.itertuples():
   dr=(arr[:,0]-r.rpm)/br;dm=(arr[:,1]-r.map_bar)/bm;dp=(arr[:,2]-r.petrol_ms)/bp
   dist=dr*dr+dm*dm+dp*dp;ix=np.argpartition(dist,min(20,len(dist)-1))[:20];w=np.exp(-.5*dist[ix])
   est=float(np.sum(arr[ix,3]*w)/max(w.sum(),1e-9));errs.append(r.err-est)
 return {"variant":f"gauss_{cfg}","train_med":float(np.median(train)) if train else None,"metrics":metric(errs)}

def wave4(d,i):
 # Hidden switch truth ONLY as external validation. Train observer before anchor, predict anchor correction.
 x=cng(d);a=strict_anchors(d);cfg=[1,2,3,5,8,13,21,34,55,89][i];errs=[];n=0
 for _,r in a.iterrows():
  g=x[(x.session==r.session)&(x.sequence<r.sequence)].sort_values("sequence")
  if len(g)<cfg:continue
  obs=float(np.median(g.err.iloc[-cfg:]))
  errs.append(obs-r.truth);n+=1
 return {"variant":f"hidden_anchor_recent_{cfg}","metrics":metric(errs),"anchors_evaluated":n,
         "switches_runtime_required":False,"switches_used_only_as_hidden_truth":True}

def wave5(d,i):
 # More realistic active-K simulation: first-order lag, delay, nonlinear plant gain, noise.
 rng=np.random.default_rng(5000+i)
 probes=[.5,1.,1.5,2.,2.5,3.,3.5,4.,4.5,5.];probe=probes[i]
 steps=[];fails=0;peaks=[]
 for _ in range(5000):
  g0=rng.uniform(.35,1.6);nonlin=rng.uniform(-.015,.015);tau=int(rng.integers(1,8));delay=int(rng.integers(0,5))
  err=rng.uniform(-20,20);target=err;hist=[err];peak=abs(err);n=0;gh=1.
  def plant_step(cur,dk):
   gain=max(.15,g0+nonlin*abs(cur));return cur-gain*dk+rng.normal(0,.45)
  dk=np.sign(err if abs(err)>.1 else 1)*probe
  target=plant_step(target,dk)
  before=err
  for _j in range(tau):err+=.45*(target-err)+rng.normal(0,.15);hist.append(err)
  after=err;est=(before-after)/dk if abs(dk)>.1 else 1
  gh=float(np.clip(est,.2,2.5)) if est>0 else 1.;n=1
  while abs(err)>4 and n<12:
   sensed=hist[-1-delay] if len(hist)>delay else hist[0]
   dk=float(np.clip(sensed/max(gh,.25),-4,4));target=plant_step(target,dk)
   for _j in range(tau):err+=.45*(target-err)+rng.normal(0,.15);hist.append(err)
   peak=max(peak,abs(err));n+=1
  steps.append(n);peaks.append(peak);fails+=int(abs(err)>4)
 return {"variant":f"realistic_probe_{probe}","mean_steps":float(np.mean(steps)),"p90_steps":float(np.quantile(steps,.9)),
         "failure_pct":fails/len(steps)*100,"mean_peak":float(np.mean(peaks)),"simulation_only":True}

FUN=[wave1,wave2,wave3,wave4,wave5]

def main():
 ap=argparse.ArgumentParser();ap.add_argument("--team",type=int,choices=range(1,6),required=True);ap.add_argument("--out",default="artifacts");a=ap.parse_args()
 d=load();fn=FUN[a.team-1]
 with cf.ThreadPoolExecutor(max_workers=10,thread_name_prefix=f"h{a.team}") as ex:
  res=list(ex.map(lambda i:fn(d,i),range(10)))
 def score(r):
  if "metrics" in r:return r["metrics"].get("mae",999)
  return r.get("mean_steps",999)+.2*r.get("failure_pct",0)
 ranked=sorted(res,key=score)
 out={"team":a.team,"logical_workers":10,"results":res,"champion":ranked[0],"runner_up":ranked[1]}
 Path(a.out).mkdir(parents=True,exist_ok=True);Path(a.out,f"h{a.team:02d}.json").write_text(json.dumps(out,indent=2),encoding="utf-8")
 print(json.dumps({"team":a.team,"champion":ranked[0]}))
if __name__=="__main__":main()
