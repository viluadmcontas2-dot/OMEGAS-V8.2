#!/usr/bin/env python3
from __future__ import annotations
import argparse, concurrent.futures as cf, json
from pathlib import Path
import numpy as np, pandas as pd

CACHE=r"C:\Users\hugov\AppData\Local\AgentRed\cache\omegas-sil-v2\canonical_frames.csv.gz"
AUTO=r"C:\ProgramData\AgentRed\jobs\641-omegas-native-automatch-interior-fit-20260919-a\automatch_interior.json"
COLS=["rpm","petrol_ms","fuel","map_bar","session","sequence","dmap","drpm","stale_conflict"]

def load():
 d=pd.read_csv(CACHE,usecols=COLS)
 for c in ["rpm","petrol_ms","map_bar","sequence","dmap","drpm"]:d[c]=pd.to_numeric(d[c],errors="coerce")
 return d[d.rpm.between(500,6500)&d.map_bar.between(.1,1.2)&d.petrol_ms.between(.7,30)].sort_values(["session","sequence"]).reset_index(drop=True)

def ref(train,test):
 tr=train[train.fuel=="PETROL"].copy();tr["rb"]=(tr.rpm/180).round().astype(int);tr["mb"]=(tr.map_bar/.025).round().astype(int)
 tab=tr.groupby(["rb","mb"]).petrol_ms.median();mp=tr.groupby("mb").petrol_ms.median();glob=float(tr.petrol_ms.median())
 out=[]
 for r in test.itertuples():
  rb=int(round(r.rpm/180));mb=int(round(r.map_bar/.025));v=tab.get((rb,mb),np.nan)
  if not np.isfinite(v):v=mp.get(mb,glob)
  out.append(float(v))
 return np.asarray(out)

def cng(d):
 ps=[]
 for s in d.session.unique():
  te=d[(d.session==s)&(d.fuel=="CNG")].copy();tr=d[d.session!=s]
  if len(te)<100 or len(tr[tr.fuel=="PETROL"])<500:continue
  if len(te)>9000:te=te.iloc[np.linspace(0,len(te)-1,9000).astype(int)].copy()
  rr=ref(tr,te);te["err"]=(te.petrol_ms.to_numpy(float)/rr-1)*100
  te=te[np.isfinite(te.err)&(te.err.abs()<70)];ps.append(te)
 return pd.concat(ps,ignore_index=True)

def met(e):
 a=np.abs(np.asarray(e,float));a=a[np.isfinite(a)]
 if not len(a):return {"n":0,"mae":999.,"p90":999.,"within4":0.}
 return {"n":int(len(a)),"mae":float(a.mean()),"median":float(np.median(a)),"p90":float(np.quantile(a,.9)),
         "p99":float(np.quantile(a,.99)),"within4":float((a<=4).mean()*100),"worst":float(a.max())}

def local_predict(g,rpm_r,map_r,max_hist=300,stat="median",decay=None,min_support=1,guard=None):
 hist=[];errs=[];used=0
 for r in g.itertuples():
  trans=False
  if guard:
   dm,dr=guard;trans=(abs(r.dmap) if np.isfinite(r.dmap) else 0)>dm or (abs(r.drpm) if np.isfinite(r.drpm) else 0)>dr
  cand=[z for z in hist[-max_hist:] if abs(z[0]-r.rpm)<=rpm_r and abs(z[1]-r.map_bar)<=map_r]
  if len(cand)>=min_support and not trans:
   vals=np.asarray([z[2] for z in cand],float)
   if decay is not None:
    age=np.arange(len(vals)-1,-1,-1);w=np.exp(-decay*age);pred=float(np.sum(vals*w)/w.sum())
   elif stat=="mean":pred=float(vals.mean())
   elif stat=="trim":
    q=np.sort(vals);k=int(.15*len(q));q=q[k:len(q)-k] if len(q)-2*k>0 else q;pred=float(q.mean())
   elif stat=="huber":
    m=float(np.median(vals));s=max(1e-6,float(np.median(np.abs(vals-m)))*1.4826);u=(vals-m)/(1.5*s);w=np.where(abs(u)<=1,1,1/np.abs(u));pred=float(np.sum(vals*w)/w.sum())
   else:pred=float(np.median(vals))
   errs.append(r.err-pred);used+=1
  hist.append((r.rpm,r.map_bar,r.err))
 return met(errs),used

def t1(x,i):
 rr=[20,30,40,50,60,80,100,120,150,200][i];es=[]
 for _,g in x.groupby("session"):
  m,_=local_predict(g,rr,.01);es.extend([] if m["n"]==0 else []) # aggregate below manually
 # rerun aggregate exactly
 E=[]
 for _,g in x.groupby("session"):
  hist=[]
  for r in g.itertuples():
   c=[z for z in hist[-300:] if abs(z[0]-r.rpm)<=rr and abs(z[1]-r.map_bar)<=.01]
   if c:E.append(r.err-np.median([z[2] for z in c]))
   hist.append((r.rpm,r.map_bar,r.err))
 return {"variant":f"rpm_radius_{rr}","metrics":met(E)}

def t2(x,i):
 mr=[.003,.005,.0075,.01,.0125,.015,.02,.025,.03,.04][i];E=[]
 for _,g in x.groupby("session"):
  hist=[]
  for r in g.itertuples():
   c=[z for z in hist[-300:] if abs(z[0]-r.rpm)<=80 and abs(z[1]-r.map_bar)<=mr]
   if c:E.append(r.err-np.median([z[2] for z in c]))
   hist.append((r.rpm,r.map_bar,r.err))
 return {"variant":f"map_radius_{mr}","metrics":met(E)}

def t3(x,i):
 histn=[20,40,80,120,200,300,500,800,1200,2000][i];E=[]
 for _,g in x.groupby("session"):
  hist=[]
  for r in g.itertuples():
   c=[z for z in hist[-histn:] if abs(z[0]-r.rpm)<=80 and abs(z[1]-r.map_bar)<=.01]
   if c:E.append(r.err-np.median([z[2] for z in c]))
   hist.append((r.rpm,r.map_bar,r.err))
 return {"variant":f"history_{histn}","metrics":met(E)}

def t4(x,i):
 decays=[0,.002,.005,.01,.02,.03,.05,.08,.12,.2];de=decays[i];E=[]
 for _,g in x.groupby("session"):
  hist=[]
  for r in g.itertuples():
   c=[z for z in hist[-500:] if abs(z[0]-r.rpm)<=80 and abs(z[1]-r.map_bar)<=.01]
   if c:
    vals=np.asarray([z[2] for z in c]);age=np.arange(len(vals)-1,-1,-1)
    p=float(np.sum(vals*np.exp(-de*age))/np.exp(-de*age).sum()) if de else float(np.mean(vals))
    E.append(r.err-p)
   hist.append((r.rpm,r.map_bar,r.err))
 return {"variant":f"decay_{de}","metrics":met(E)}

def t5(x,i):
 stats=["median","mean","trim","huber","median","trim","huber","median","trim","huber"];mins=[1,1,1,1,2,2,2,3,3,5];st=stats[i];ms=mins[i];E=[]
 for _,g in x.groupby("session"):
  hist=[]
  for r in g.itertuples():
   c=[z for z in hist[-300:] if abs(z[0]-r.rpm)<=80 and abs(z[1]-r.map_bar)<=.01]
   if len(c)>=ms:
    vals=np.asarray([z[2] for z in c],float)
    if st=="mean":p=float(vals.mean())
    elif st=="trim":
     q=np.sort(vals);k=int(.15*len(q));q=q[k:len(q)-k] if len(q)-2*k>0 else q;p=float(q.mean())
    elif st=="huber":
     m=float(np.median(vals));s=max(1e-6,float(np.median(np.abs(vals-m)))*1.4826);u=(vals-m)/(1.5*s);w=np.where(abs(u)<=1,1,1/np.abs(u));p=float(np.sum(vals*w)/w.sum())
    else:p=float(np.median(vals))
    E.append(r.err-p)
   hist.append((r.rpm,r.map_bar,r.err))
 return {"variant":f"{st}_support{ms}","metrics":met(E)}

def t6(x,i):
 guards=[(.01,50),(.015,80),(.02,100),(.025,120),(.03,150),(.04,200),(.05,250),(.07,300),(.10,400),(.15,600)];gd=guards[i];E=[];skip=0;total=0
 for _,g in x.groupby("session"):
  hist=[]
  for r in g.itertuples():
   total+=1;trans=(abs(r.dmap) if np.isfinite(r.dmap) else 0)>gd[0] or (abs(r.drpm) if np.isfinite(r.drpm) else 0)>gd[1]
   c=[z for z in hist[-300:] if abs(z[0]-r.rpm)<=80 and abs(z[1]-r.map_bar)<=.01]
   if c and not trans:E.append(r.err-np.median([z[2] for z in c]))
   elif trans:skip+=1
   hist.append((r.rpm,r.map_bar,r.err))
 return {"variant":f"guard_{gd}","metrics":met(E),"skipped_pct":skip/max(total,1)*100}

def t7(x,i):
 mins=[1,2,3,4,5,8,12,16,24,32];ms=mins[i];E=[];eligible=0;total=0
 for _,g in x.groupby("session"):
  hist=[]
  for r in g.itertuples():
   total+=1;c=[z for z in hist[-500:] if abs(z[0]-r.rpm)<=80 and abs(z[1]-r.map_bar)<=.01]
   if len(c)>=ms:E.append(r.err-np.median([z[2] for z in c]));eligible+=1
   hist.append((r.rpm,r.map_bar,r.err))
 return {"variant":f"support_{ms}","metrics":met(E),"coverage_pct":eligible/max(total,1)*100}

def t8(x,i):
 rng=np.random.default_rng(8000+i);drops=[0,.02,.05,.08,.1,.15,.2,.25,.3,.4];drop=drops[i];E=[]
 for _,g0 in x.groupby("session"):
  g=g0[rng.random(len(g0))>drop]
  hist=[]
  for r in g.itertuples():
   c=[z for z in hist[-300:] if abs(z[0]-r.rpm)<=80 and abs(z[1]-r.map_bar)<=.01]
   if c:E.append(r.err-np.median([z[2] for z in c]))
   hist.append((r.rpm,r.map_bar,r.err))
 return {"variant":f"drop_{drop}","metrics":met(E)}

def t9(x,i):
 # carry-over prior vs reset: blend previous-session local/global prior with current local memory.
 weights=[0,.05,.1,.15,.2,.3,.4,.5,.7,1.];w=weights[i];E=[];prior=[]
 sessions=list(x.session.unique())
 for s in sessions:
  g=x[x.session==s];global_prior=float(np.median(prior)) if prior else 0.;hist=[]
  for r in g.itertuples():
   c=[z for z in hist[-300:] if abs(z[0]-r.rpm)<=80 and abs(z[1]-r.map_bar)<=.01]
   if c:
    local=float(np.median([z[2] for z in c]));p=(1-w)*local+w*global_prior;E.append(r.err-p)
   hist.append((r.rpm,r.map_bar,r.err));prior.append(r.err)
 return {"variant":f"carry_{w}","metrics":met(E)}

def t10(x,i):
 data=json.loads(Path(AUTO).read_text(encoding="utf-8"));gains=[.35,.5,.65,.8,.9,1.,1.05,1.1,1.2,1.3];gain=gains[i];caps=[.05,.08,.1,.12,.15,.2,.25]
 best=None
 for cap in caps:
  es=[]
  for row in data["rows"]:
   ratio=np.asarray(row["ratio"],float);step=np.asarray(row["step"],float);ix=np.asarray(row["valid_indices"],int)
   pred=1+np.clip(gain*(ratio-1),-cap,cap);es.extend(np.abs(pred[ix]-step[ix]))
  mae=float(np.mean(es))
  if best is None or mae<best["mae"]:best={"gain":gain,"cap":cap,"mae":mae}
 return {"variant":f"kpolicy_gain_{gain}","native_fit":best}

FUN=[t1,t2,t3,t4,t5,t6,t7,t8,t9,t10]
def main():
 ap=argparse.ArgumentParser();ap.add_argument("--team",type=int,choices=range(1,11),required=True);ap.add_argument("--out",default="artifacts");a=ap.parse_args()
 d=load();x=cng(d);fn=FUN[a.team-1]
 with cf.ThreadPoolExecutor(max_workers=10) as ex:res=list(ex.map(lambda i:fn(x,i),range(10)))
 def score(r):
  if "metrics" in r:return r["metrics"]["mae"]+.02*max(0,50-r.get("coverage_pct",100))
  if "native_fit" in r:return r["native_fit"]["mae"]
  return 999
 rank=sorted(res,key=score);out={"team":a.team,"logical_workers":10,"results":res,"champion":rank[0],"runner_up":rank[1]}
 Path(a.out).mkdir(parents=True,exist_ok=True);Path(a.out,f"l{a.team:02d}.json").write_text(json.dumps(out,indent=2),encoding="utf-8")
 print(json.dumps({"team":a.team,"champion":rank[0]}))
if __name__=="__main__":main()
