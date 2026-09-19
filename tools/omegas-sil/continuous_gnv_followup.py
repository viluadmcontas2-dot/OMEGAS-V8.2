#!/usr/bin/env python3
from __future__ import annotations
import argparse, concurrent.futures as cf, json
from pathlib import Path
import numpy as np, pandas as pd

CACHE=r"C:\Users\hugov\AppData\Local\AgentRed\cache\omegas-sil-v2\canonical_frames.csv.gz"
COLS=["rpm","petrol_ms","fuel","map_bar","session","sequence","dmap","drpm","gas_pressure_raw","gas_temp_raw"]

def load():
 d=pd.read_csv(CACHE,usecols=COLS);d=d[d.rpm.between(500,6500)&d.map_bar.between(.1,1.2)&d.petrol_ms.between(.7,30)].copy()
 return d.sort_values(["session","sequence"]).reset_index(drop=True)

def ref(train,test):
 tr=train[train.fuel=="PETROL"].copy();tr["rb"]=(tr.rpm/180).round().astype(int);tr["mb"]=(tr.map_bar/.025).round().astype(int)
 tab=tr.groupby(["rb","mb"]).petrol_ms.median();mp=tr.assign(mb=(tr.map_bar/.025).round().astype(int)).groupby("mb").petrol_ms.median();glob=float(tr.petrol_ms.median())
 out=[]
 for r in test.itertuples():
  k=(int(round(r.rpm/180)),int(round(r.map_bar/.025)));v=tab.get(k,np.nan)
  if not np.isfinite(v):v=mp.get(k[1],glob)
  out.append(float(v))
 return np.asarray(out)

def cng(d,maxn=7000):
 parts=[]
 for s in d.session.unique():
  te=d[(d.session==s)&(d.fuel=="CNG")].copy();tr=d[d.session!=s]
  if len(te)<100 or len(tr[tr.fuel=="PETROL"])<500:continue
  if len(te)>maxn:te=te.iloc[np.linspace(0,len(te)-1,maxn).astype(int)].copy()
  rr=ref(tr,te);te["err"]=(te.petrol_ms.to_numpy(float)/rr-1)*100
  te=te[np.isfinite(te.err)&(te.err.abs()<60)];parts.append(te)
 return pd.concat(parts,ignore_index=True) if parts else pd.DataFrame()

def met(e):
 a=np.abs(np.asarray(e,float));a=a[np.isfinite(a)]
 return {"n":len(a),"mae":float(a.mean()) if len(a) else None,"p90":float(np.quantile(a,.9)) if len(a) else None,"within4":float((a<=4).mean()*100) if len(a) else None}

def team1(x,i):
 h=[1,2,3,5,10,20,40,80,160,320][i];es=[]
 for _,g in x.groupby("session"):
  y=g.err.to_numpy(float)
  if len(y)>h:es.extend(y[h:]-y[:-h])
 return {"variant":f"horizon_{h}","h":h,"delta_error":met(es)}

def team2(x,i):
 n=[1,2,3,5,8,12,20,30,50,80][i];es=[]
 for _,g in x.groupby("session"):
  y=g.err.to_numpy(float)
  for j in range(n,len(y)):es.append(y[j]-np.median(y[j-n:j]))
 return {"variant":f"recent_median_{n}","metrics":met(es)}

def team3(x,i):
 cfg=[(80,.01),(100,.015),(150,.02),(200,.025),(250,.03),(300,.04),(400,.05),(500,.06),(700,.08),(900,.1)][i];rr,mm=cfg;es=[]
 for _,g in x.groupby("session"):
  hist=[]
  for row in g.itertuples():
   cand=[z for z in hist[-300:] if abs(z[0]-row.rpm)<=rr and abs(z[1]-row.map_bar)<=mm]
   if cand:es.append(row.err-np.median([z[2] for z in cand]))
   hist.append((row.rpm,row.map_bar,row.err))
 return {"variant":f"local_r{rr}_m{mm}","metrics":met(es)}

def team4(x,i):
 frac=[.002,.005,.01,.02,.03,.05,.08,.1,.15,.2][i];es=[];sizes=[]
 for _,g in x.groupby("session"):
  g=g.reset_index(drop=True);cut=max(5,int(frac*len(g)))
  if len(g)<cut+20:continue
  bias=float(np.median(g.err.iloc[:cut]));es.extend(g.err.iloc[cut:]-bias);sizes.append(cut)
 return {"variant":f"global_bias_{frac}","train_frames_med":float(np.median(sizes)) if sizes else None,"metrics":met(es)}

def team5(x,i):
 frac=[.01,.02,.03,.05,.08,.1,.15,.2,.25,.3][i];es_curve=[];es_map=[]
 for _,g in x.groupby("session"):
  g=g.reset_index(drop=True);cut=max(15,int(frac*len(g)))
  if len(g)<cut+50:continue
  tr=g.iloc[:cut].copy();te=g.iloc[cut:].copy();tr["pb"]=(tr.petrol_ms/.75).round().astype(int);curve=tr.groupby("pb").err.median();glob=float(tr.err.median())
  tr["base"]=tr.pb.map(curve).fillna(glob);tr["rb"]=(tr.rpm/500).round().astype(int);tr["res"]=tr.err-tr.base;local=tr.groupby(["rb","pb"]).res.median()
  for r in te.itertuples():
   pb=int(round(r.petrol_ms/.75));cv=float(curve.get(pb,glob));es_curve.append(r.err-cv);es_map.append(r.err-(cv+float(local.get((int(round(r.rpm/500)),pb),0))))
 return {"variant":f"curve_vs_map_{frac}","curve":met(es_curve),"curve_plus_map":met(es_map)}

def team6(x,i):
 n=[5,10,20,40,80,160,320,640,1000,1500][i];es=[]
 for _,g in x.groupby("session"):
  y=g.err.to_numpy(float)
  if len(y)<=n+1:continue
  bias=float(np.median(y[:n]));es.extend(y[n:]-bias)
 return {"variant":f"session_bias_{n}","metrics":met(es)}

def team7(x,i):
 rng=np.random.default_rng(700+i);drop=[0,.02,.05,.08,.1,.15,.2,.25,.3,.4][i];es=[]
 for _,g in x.groupby("session"):
  y=g.err.to_numpy(float);keep=rng.random(len(y))>drop;y=y[keep]
  for j in range(5,len(y)):es.append(y[j]-np.median(y[max(0,j-5):j]))
 return {"variant":f"drop_{drop}","metrics":met(es)}

def team8(x,i):
 rng=np.random.default_rng(800+i);delays=[0,1,2,3,5,8,12,20,30,50][i];steps=[]
 for _ in range(3000):
  gain=rng.uniform(.35,1.8);err=rng.uniform(-18,18);hist=[err];ghat=1.;n=0
  probe=2*np.sign(err if abs(err)>.1 else 1);after=err-gain*probe+rng.normal(0,.4);hist.append(after);ghat=np.clip((err-after)/probe,.25,2.5);err=after;n=1
  while abs(err)>4 and n<10:
   obs=hist[max(0,len(hist)-1-delays)] if delays<len(hist) else hist[0]
   dk=np.clip(obs/ghat,-5,5);err=err-gain*dk+rng.normal(0,.4);hist.append(err);n+=1
  steps.append(n if abs(err)<=4 else 11)
 return {"variant":f"ident_delay_{delays}","mean_steps":float(np.mean(steps)),"p90":float(np.quantile(steps,.9)),"fail_pct":float((np.array(steps)>10).mean()*100)}

def team9(x,i):
 rng=np.random.default_rng(900+i);caps=[1,1.5,2,2.5,3,3.5,4,4.5,5,6][i];steps=[];peak=[]
 for _ in range(3000):
  gain=rng.uniform(.35,1.8);err=rng.uniform(-20,20);mx=abs(err);gh=1.;probe=2*np.sign(err if abs(err)>.1 else 1);aft=err-gain*probe+rng.normal(0,.35);gh=np.clip((err-aft)/probe,.25,2.5);err=aft;n=1
  while abs(err)>4 and n<10:
   dk=np.clip(err/gh,-caps,caps);err-=gain*dk;err+=rng.normal(0,.35);mx=max(mx,abs(err));n+=1
  steps.append(n if abs(err)<=4 else 11);peak.append(mx)
 return {"variant":f"stepcap_{caps}","mean_steps":float(np.mean(steps)),"fail_pct":float((np.array(steps)>10).mean()*100),"peak":float(np.mean(peak))}

def team10(x,i):
 # score architectures by speed vs robustness: global only, curve, curve+map at varying training fraction
 frac=[.005,.01,.02,.03,.05,.08,.1,.15,.2,.25][i];errs=[];sizes=[]
 for _,g in x.groupby("session"):
  g=g.reset_index(drop=True);cut=max(8,int(frac*len(g)))
  if len(g)<cut+30:continue
  tr=g.iloc[:cut];te=g.iloc[cut:];bias=float(np.median(tr.err));errs.extend(te.err-bias);sizes.append(cut)
 m=met(errs);score=(m["mae"] or 99)+.25*(m["p90"] or 99)+.03*(np.median(sizes) if sizes else 999)/10
 return {"variant":f"speed_score_{frac}","train_frames_med":float(np.median(sizes)) if sizes else None,"metrics":m,"score":float(score)}

FUN=[team1,team2,team3,team4,team5,team6,team7,team8,team9,team10]

def main():
 ap=argparse.ArgumentParser();ap.add_argument("--team",type=int,choices=range(1,11),required=True);ap.add_argument("--out",default="artifacts");a=ap.parse_args()
 d=load();x=cng(d)
 fn=FUN[a.team-1]
 with cf.ThreadPoolExecutor(max_workers=10) as ex:res=list(ex.map(lambda i:fn(x,i),range(10)))
 def score(r):
  if "score" in r:return r["score"]
  if "metrics" in r:return r["metrics"].get("mae",999) or 999
  if "delta_error" in r:return r["delta_error"].get("mae",999) or 999
  if "curve_plus_map" in r:return r["curve_plus_map"].get("mae",999) or 999
  if "mean_steps" in r:return r["mean_steps"]+.1*r.get("fail_pct",0)
  return 999
 rank=sorted(res,key=score);out={"team":a.team,"logical_workers":10,"results":res,"champion":rank[0],"runner_up":rank[1]}
 Path(a.out).mkdir(parents=True,exist_ok=True);Path(a.out,f"f{a.team:02d}.json").write_text(json.dumps(out,indent=2),encoding="utf-8")
 print(json.dumps({"team":a.team,"champion":rank[0]}))
if __name__=="__main__":main()
