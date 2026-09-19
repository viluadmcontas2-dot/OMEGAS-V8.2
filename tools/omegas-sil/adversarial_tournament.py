#!/usr/bin/env python3
"""OMEGAS Verde adversarial SIL tournament.

Research-only harness. It never writes ECU state and never treats fuel switching
as a runtime requirement. PETROL->CNG boundaries may be used only as hidden
validation truth. The operational hypothesis is:
learn gasoline reference -> remain on CNG -> infer correction continuously.
"""
from __future__ import annotations
import argparse, json, math, os
from pathlib import Path
import numpy as np
import pandas as pd

CACHE_DEFAULT = r"C:\Users\hugov\AppData\Local\AgentRed\cache\omegas-sil-v2\canonical_frames.csv.gz"

def load(path: str) -> pd.DataFrame:
    d=pd.read_csv(path)
    d=d[(d.rpm.between(500,6500))&(d.map_bar.between(.10,1.20))&(d.petrol_ms.between(.7,30))].copy()
    return d.sort_values(["session","sequence"]).reset_index(drop=True)

def metrics(y,p):
    y=np.asarray(y,float);p=np.asarray(p,float)
    ok=np.isfinite(y)&np.isfinite(p)&(y>0)
    y=y[ok];p=p[ok]
    if len(y)==0:return {"n":0}
    e=np.abs((p-y)/y)*100
    return {"n":int(len(e)),"mae_pct":float(e.mean()),"median_pct":float(np.median(e)),
            "p90_pct":float(np.quantile(e,.9)),"p95_pct":float(np.quantile(e,.95)),
            "p99_pct":float(np.quantile(e,.99)),"within3_5_pct":float((e<=3.5).mean()*100),
            "within4_pct":float((e<=4).mean()*100),"worst_pct":float(e.max())}

def abs_metrics(err):
    a=np.abs(np.asarray(err,float));a=a[np.isfinite(a)]
    if len(a)==0:return {"n":0}
    return {"n":int(len(a)),"mae_pct":float(a.mean()),"median_pct":float(np.median(a)),
            "p90_pct":float(np.quantile(a,.9)),"p95_pct":float(np.quantile(a,.95)),
            "p99_pct":float(np.quantile(a,.99)),"within3_5_pct":float((a<=3.5).mean()*100),
            "within4_pct":float((a<=4).mean()*100),"worst_pct":float(a.max())}

def base_reference(train: pd.DataFrame, test: pd.DataFrame):
    # Causal/held-out low-complexity gasoline reference: 2D weighted bins.
    tr=train[train.fuel=="PETROL"].copy()
    if len(tr)<100:return np.full(len(test),np.nan)
    rb=np.round(tr.rpm/150).astype(int); mb=np.round(tr.map_bar/.02).astype(int)
    tab=tr.assign(rb=rb,mb=mb).groupby(["rb","mb"]).petrol_ms.median()
    bymap=tr.assign(mb=mb).groupby("mb").petrol_ms.median()
    glob=float(tr.petrol_ms.median())
    vals=[]
    for r in test.itertuples():
        k=(int(round(r.rpm/150)),int(round(r.map_bar/.02)))
        v=tab.get(k,np.nan)
        if not np.isfinite(v):v=bymap.get(int(round(r.map_bar/.02)),glob)
        vals.append(float(v))
    return np.asarray(vals)

def heldout_petrol(d):
    for s in d.session.unique():
        te=d[(d.session==s)&(d.fuel=="PETROL")].copy()
        tr=d[d.session!=s]
        if len(te)>=30 and len(tr[tr.fuel=="PETROL"])>=200:
            yield s,tr,te

def save(out,name,obj):
    Path(out).mkdir(parents=True,exist_ok=True)
    p=Path(out)/f"{name}.json";p.write_text(json.dumps(obj,indent=2,sort_keys=True),encoding="utf-8")
    print(json.dumps({"worker":name,**obj},default=str))
    return p

def w01(d):
    g=d[d.fuel=="PETROL"].copy();res={}
    for n in [3,5,8,10,15,20,30,50]:
        pred=g.groupby("session").petrol_ms.transform(lambda s:s.shift(1).rolling(n,min_periods=max(2,n//3)).mean())
        res[str(n)]=metrics(g.petrol_ms,pred)
    return {"role":"LEGACY_BASELINE","claim":"ProgBase-like SMA is an old baseline to beat, not the target architecture","windows":res}

def w02(d):
    g=d[d.fuel=="PETROL"].copy();res={}
    for a in [.05,.1,.15,.2,.3,.4,.5,.65,.8]:
        yy=[];pp=[]
        for _,x in g.groupby("session"):
            state=np.nan
            for v in x.petrol_ms.to_numpy(float):
                yy.append(v);pp.append(state)
                state=v if not np.isfinite(state) else a*v+(1-a)*state
        res[str(a)]=metrics(yy,pp)
    return {"role":"EMA_CHALLENGER","claim":"adaptive exponential memory should reduce SMA lag","alphas":res}

def w03(d):
    g=d[d.fuel=="PETROL"].copy();res={}
    for n in [5,9,15,21]:
        med=g.groupby("session").petrol_ms.transform(lambda s:s.shift(1).rolling(n,min_periods=max(3,n//3)).median())
        res[f"median_{n}"]=metrics(g.petrol_ms,med)
        def trim(a):
            a=np.sort(np.asarray(a,float));k=max(0,int(len(a)*.15))
            return float(a[k:len(a)-k].mean()) if len(a)-2*k else float(a.mean())
        tr=g.groupby("session").petrol_ms.transform(lambda s:s.shift(1).rolling(n,min_periods=max(3,n//3)).apply(trim,raw=True))
        res[f"trim_{n}"]=metrics(g.petrol_ms,tr)
    return {"role":"OUTLIER_ADVERSARY","claim":"robust windows should beat mean under spikes","results":res}

def w04(d):
    all_y=[];all_p=[];reg={"fast":0,"mid":0,"stable":0}
    for _,x in d[d.fuel=="PETROL"].groupby("session"):
        hist=[]
        for r in x.itertuples():
            score=abs(getattr(r,"dmap",0) if np.isfinite(getattr(r,"dmap",np.nan)) else 0)/.02+abs(getattr(r,"drpm",0) if np.isfinite(getattr(r,"drpm",np.nan)) else 0)/80
            n=3 if score>1.2 else 8 if score>.35 else 20
            reg["fast" if n==3 else "mid" if n==8 else "stable"]+=1
            all_y.append(r.petrol_ms);all_p.append(float(np.mean(hist[-n:])) if len(hist)>=2 else np.nan);hist.append(r.petrol_ms)
    return {"role":"ADAPTIVE_WINDOW","claim":"short transient memory plus long stable memory beats fixed window","metrics":metrics(all_y,all_p),"regimes":reg}

def w05(d):
    folds=[]
    for s,tr,te in heldout_petrol(d):
        b=base_reference(tr,te);y=te.petrol_ms.to_numpy(float);pred=np.full(len(te),np.nan);gain=1.
        for i in range(len(te)):
            pred[i]=b[i]*gain
            if i<len(te)-1 and b[i]>.7:
                gain=y[i]/b[i]
        folds.append({"session":s,**metrics(y,pred)})
    return {"role":"LAST_RATIO","claim":"previous-frame multiplicative gain is the simple modern causal challenger","folds":folds,"aggregate_mae":float(np.mean([x["mae_pct"] for x in folds]))}

def w06(d):
    rows=[]
    for af in [.2,.4,.6,.8,1.0]:
      for asl in [.005,.01,.02,.05]:
        es=[]
        for s,tr,te in heldout_petrol(d):
            b=base_reference(tr,te);y=te.petrol_ms.to_numpy(float);gf=1.;gs=1.;p=[]
            for i in range(len(te)):
                p.append(b[i]*gs*gf)
                if b[i]>.7:
                    obs=y[i]/b[i]
                    gs=(1-asl)*gs+asl*obs
                    residual=obs/max(gs,1e-6)
                    gf=(1-af)*gf+af*residual
            es.append(metrics(y,p)["mae_pct"])
        if es:rows.append({"alpha_fast":af,"alpha_slow":asl,"mae_pct":float(np.mean(es))})
    return {"role":"FAST_SLOW","claim":"dual-rate gain should beat single last-ratio without any fuel switching","top":sorted(rows,key=lambda x:x["mae_pct"])[:20]}

def w07(d):
    rows=[]
    for q in [.0005,.001,.002,.005,.01,.02,.05]:
      es=[]
      for s,tr,te in heldout_petrol(d):
        b=base_reference(tr,te);y=te.petrol_ms.to_numpy(float);m=1.;v=.01;p=[]
        for i in range(len(te)):
            p.append(b[i]*m)
            v+=q
            if b[i]>.7:
                z=y[i]/b[i]; r=.0025 + .04*min(1.,abs(float(te.iloc[i].dmap))/.05 if np.isfinite(te.iloc[i].dmap) else 0)
                k=v/(v+r);m=m+k*(z-m);v=(1-k)*v
        es.append(metrics(y,p)["mae_pct"])
      if es:rows.append({"process_q":q,"mae_pct":float(np.mean(es))})
    return {"role":"KALMAN_GAIN","claim":"state-space gain estimator should smooth noise without SMA lag","results":rows}

def w08(d):
    rows=[]
    # Gaussian local reference, leave-one-session-out; intentionally expensive but bounded.
    for bw_r,bw_m in [(100,.015),(150,.02),(250,.03),(400,.05)]:
      errs=[]
      for s,tr,te in heldout_petrol(d):
        tr=tr[tr.fuel=="PETROL"].copy()
        # compact prototypes to avoid O(N^2)
        tr["rb"]=(tr.rpm/bw_r).round().astype(int);tr["mb"]=(tr.map_bar/bw_m).round().astype(int)
        pr=tr.groupby(["rb","mb"]).agg(rpm=("rpm","median"),map_bar=("map_bar","median"),petrol_ms=("petrol_ms","median")).reset_index()
        preds=[]
        for r in te.itertuples():
            dist=((pr.rpm-r.rpm)/bw_r)**2+((pr.map_bar-r.map_bar)/bw_m)**2
            ix=np.argsort(dist.to_numpy())[:12];dd=dist.iloc[ix].to_numpy();w=np.exp(-.5*dd)
            preds.append(float(np.sum(pr.petrol_ms.iloc[ix].to_numpy()*w)/max(w.sum(),1e-12)))
        errs.append(metrics(te.petrol_ms,preds)["mae_pct"])
      rows.append({"bw_rpm":bw_r,"bw_map":bw_m,"mae_pct":float(np.mean(errs))})
    return {"role":"GAUSSIAN_REFERENCE","claim":"modern local Gaussian reference should beat crude lookup without overfit","results":rows}

def w09(d):
    rows=[]
    for deg in [1,2,3,4]:
      es=[]
      for s,tr,te in heldout_petrol(d):
        tr=tr[tr.fuel=="PETROL"]; x=np.column_stack([np.ones(len(tr)),tr.map_bar,tr.rpm/1000])
        xt=np.column_stack([np.ones(len(te)),te.map_bar,te.rpm/1000])
        if deg>=2:
            x=np.column_stack([x,tr.map_bar**2,(tr.rpm/1000)**2,tr.map_bar*(tr.rpm/1000)])
            xt=np.column_stack([xt,te.map_bar**2,(te.rpm/1000)**2,te.map_bar*(te.rpm/1000)])
        if deg>=3:
            x=np.column_stack([x,tr.map_bar**3,(tr.rpm/1000)**3,(tr.map_bar**2)*(tr.rpm/1000),tr.map_bar*(tr.rpm/1000)**2])
            xt=np.column_stack([xt,te.map_bar**3,(te.rpm/1000)**3,(te.map_bar**2)*(te.rpm/1000),te.map_bar*(te.rpm/1000)**2])
        if deg>=4:
            x=np.column_stack([x,tr.map_bar**4,(tr.rpm/1000)**4])
            xt=np.column_stack([xt,te.map_bar**4,(te.rpm/1000)**4])
        beta=np.linalg.lstsq(x,tr.petrol_ms.to_numpy(float),rcond=None)[0]
        es.append(metrics(te.petrol_ms,xt@beta)["mae_pct"])
      rows.append({"degree":deg,"mae_pct":float(np.mean(es))})
    return {"role":"FORMULA_DISCOVERY","claim":"a compact MAP/RPM formula may initialize the gasoline surface globally","results":rows}

def cng_errors(d):
    rows=[]
    for s in d.session.unique():
        te=d[(d.session==s)&(d.fuel=="CNG")].copy();tr=d[d.session!=s]
        if len(te)<50 or len(tr[tr.fuel=="PETROL"])<200:continue
        ref=base_reference(tr,te);e=(te.petrol_ms.to_numpy(float)/ref-1)*100
        z=te.copy();z["corr"]=e;rows.append(z)
    return pd.concat(rows,ignore_index=True) if rows else pd.DataFrame()

def w10(d):
    x=cng_errors(d);out={}
    if x.empty:return {"role":"RESIDUAL_COORDINATES","status":"NO_DATA"}
    coords={
      "petrol_ms":pd.DataFrame({"a":(x.petrol_ms/.5).round()}),
      "rpm_petrol":pd.DataFrame({"a":(x.rpm/250).round(),"b":(x.petrol_ms/.5).round()}),
      "map":pd.DataFrame({"a":(x.map_bar/.04).round()}),
      "rpm_map":pd.DataFrame({"a":(x.rpm/250).round(),"b":(x.map_bar/.04).round()}),
    }
    for name,k in coords.items():
        z=x.copy()
        for col in k:z[col]=k[col].to_numpy()
        stats=z.groupby(list(k.columns)).corr.agg(["count","median"])
        # actual stability = median absolute deviation inside cells
        vals=[]
        for _,g in z.groupby(list(k.columns)):
            if len(g)>=8: vals.append(float(np.median(np.abs(g.corr-g.corr.median()))))
        out[name]={"cells":len(vals),"weighted_mad_pct":float(np.median(vals)) if vals else None}
    return {"role":"RESIDUAL_COORDINATES","claim":"find the smallest coordinate system that preserves stable GNV residuals","results":out}

def w11(d):
    x=cng_errors(d)
    if x.empty:return {"role":"PHYSICS_RESIDUAL","status":"NO_DATA"}
    stable=x[(x.dmap.abs().fillna(0)<=.02)&(x.drpm.abs().fillna(0)<=150)].copy()
    cols=["gas_pressure_raw","gas_temp_raw","petrol_ms","rpm","map_bar"]
    corr={c:float(stable.corr.corr(stable[c],method="spearman")) for c in cols}
    # linear incremental test, session holdout
    base=[];aug=[]
    for s in stable.session.unique():
        tr=stable[stable.session!=s];te=stable[stable.session==s]
        if len(tr)<100 or len(te)<20:continue
        b=float(tr.corr.median());base.extend(te.corr-b)
        X=np.column_stack([np.ones(len(tr)),tr.gas_pressure_raw,tr.gas_temp_raw,tr.petrol_ms,tr.rpm/1000,tr.map_bar])
        Xt=np.column_stack([np.ones(len(te)),te.gas_pressure_raw,te.gas_temp_raw,te.petrol_ms,te.rpm/1000,te.map_bar])
        beta=np.linalg.lstsq(X,tr.corr.to_numpy(float),rcond=None)[0];aug.extend(te.corr-Xt@beta)
    return {"role":"PRESSURE_TEMP_DEADTIME","claim":"physical compensators matter only if they reduce held-out residual","spearman":corr,"base":abs_metrics(base),"augmented":abs_metrics(aug)}

def w12(d):
    # Stress SMA15, last-ratio and EMA under drop/lag/noise on petrol truth.
    rng=np.random.default_rng(20260919);g=d[d.fuel=="PETROL"].copy();sessions=[x for _,x in g.groupby("session") if len(x)>80]
    configs=[("sma15",None),("ema",.35),("last",None)];res={k:[] for k,_ in configs}
    for seed in range(100):
      x=sessions[int(rng.integers(0,len(sessions)))].copy().reset_index(drop=True)
      keep=rng.random(len(x))>rng.uniform(0,.25);x=x[keep].reset_index(drop=True)
      y=x.petrol_ms.to_numpy(float); noisy=y*(1+rng.normal(0,rng.uniform(0,.01),len(y)))
      for name,a in configs:
        p=np.full(len(y),np.nan)
        if name=="sma15":
          for i in range(len(y)): 
            if i>=3:p[i]=float(np.mean(noisy[max(0,i-15):i]))
        elif name=="ema":
          s=np.nan
          for i,v in enumerate(noisy):
            p[i]=s;s=v if not np.isfinite(s) else a*v+(1-a)*s
        else:
          for i in range(1,len(y)):p[i]=noisy[i-1]
        res[name].append(metrics(y,p)["mae_pct"])
    return {"role":"MONTE_CARLO","claim":"winner must survive 100 randomized drop/noise trials","summary":{k:{"mean_mae":float(np.mean(v)),"p90_mae":float(np.quantile(v,.9)),"worst_mae":float(max(v))} for k,v in res.items()}}

def w13(d):
    # Last-ratio with transient authority reduction.
    rows=[]
    for dm in [.015,.025,.04,.06,.10]:
      for dr in [50,100,150,250,400]:
        for fallback in [0.,.25,.5,.75]:
          errs=[]
          for s,tr,te in heldout_petrol(d):
            b=base_reference(tr,te);y=te.petrol_ms.to_numpy(float);gain=1.;p=[]
            for i,r in enumerate(te.itertuples()):
              p.append(b[i]*gain)
              if b[i]>.7:
                obs=y[i]/b[i]
                trans=(abs(r.dmap) if np.isfinite(r.dmap) else 0)>dm or (abs(r.drpm) if np.isfinite(r.drpm) else 0)>dr
                authority=fallback if trans else 1.
                gain=(1-authority)*gain+authority*obs
            errs.append(metrics(y,p)["mae_pct"])
          rows.append({"dmap":dm,"drpm":dr,"fallback":fallback,"mae_pct":float(np.mean(errs))})
    return {"role":"TRANSIENT_ADVERSARY","claim":"fast gain must lose authority only where transients empirically demand it","top":sorted(rows,key=lambda x:x["mae_pct"])[:25]}

def w14(d):
    # Simulation only: can one small manual probe identify plant gain and accelerate K convergence?
    rng=np.random.default_rng(1401);res={}
    policies=["fixed35","full_clipped","identified_gain"]
    for pol in policies:
      times=[];overs=[]
      for _ in range(5000):
        g=float(rng.uniform(.35,1.8)); e=float(rng.uniform(-18,18));steps=0;mx=abs(e);ghat=1.
        if pol=="identified_gain":
            probe=np.sign(e)*2.0 if abs(e)>2 else 2.0
            before=e; after=e-g*probe+rng.normal(0,.35)
            est=(before-after)/probe
            if est>0.1:ghat=float(np.clip(est,.25,2.5))
            e=after;steps=1;mx=max(mx,abs(e))
        while abs(e)>4 and steps<12:
            if pol=="fixed35": dk=.35*e
            elif pol=="full_clipped": dk=np.clip(e,-5,5)
            else: dk=np.clip(e/max(ghat,.25),-5,5)
            e=e-g*dk+rng.normal(0,.35);steps+=1;mx=max(mx,abs(e))
        times.append(steps);overs.append(mx)
      res[pol]={"mean_steps":float(np.mean(times)),"p90_steps":float(np.quantile(times,.9)),"failure_12_steps_pct":float((np.array(times)>=12).mean()*100),"mean_peak_error":float(np.mean(overs))}
    return {"role":"ACTIVE_K_PROBE_SIM","claim":"simulation-only falsification of whether a small human-confirmed K probe can learn local plant gain faster than passive fixed authority","results":res,"warning":"NOT VEHICLE EVIDENCE"}

def w15(d):
    x=cng_errors(d)
    if x.empty:return {"role":"FAST_K_FIELD","status":"NO_DATA"}
    results=[]
    # Learn correction from earliest fraction of each CNG session; predict later same-session correction.
    for frac in [.01,.02,.05,.10,.20,.30,.50]:
      errs=[];covered=0
      for s,g in x.groupby("session"):
        g=g.sort_values("sequence").reset_index(drop=True)
        cut=max(20,int(len(g)*frac))
        if len(g)<cut+30:continue
        tr=g.iloc[:cut];te=g.iloc[cut:]
        glob=float(tr.corr.median())
        # global curve by petrol-time bins plus sparse residual rpm×petrol
        tr=tr.copy();tr["pb"]=(tr.petrol_ms/.5).round().astype(int);tr["rb"]=(tr.rpm/300).round().astype(int)
        curve=tr.groupby("pb").corr.median()
        cell=tr.assign(res=tr.corr-tr["pb"].map(curve).fillna(glob)).groupby(["rb","pb"]).res.median()
        pred=[]
        for r in te.itertuples():
            pb=int(round(r.petrol_ms/.5));rb=int(round(r.rpm/300))
            cg=float(curve.get(pb,glob));rr=float(cell.get((rb,pb),0.))
            pred.append(cg+rr)
        errs.extend(te.corr.to_numpy(float)-np.asarray(pred));covered+=len(te)
      m=abs_metrics(errs);m["fraction"]=frac;m["frames_tested"]=covered;results.append(m)
    return {"role":"FAST_K_FIELD","claim":"measure how little continuous CNG exposure is needed to predict later correction field without switching","results":results}

WORKERS={"w01":w01,"w02":w02,"w03":w03,"w04":w04,"w05":w05,"w06":w06,"w07":w07,"w08":w08,"w09":w09,"w10":w10,"w11":w11,"w12":w12,"w13":w13,"w14":w14,"w15":w15}

def main():
    ap=argparse.ArgumentParser();ap.add_argument("--worker",choices=WORKERS,required=True);ap.add_argument("--cache",default=CACHE_DEFAULT);ap.add_argument("--out",default="artifacts")
    a=ap.parse_args();d=load(a.cache);obj=WORKERS[a.worker](d);obj["worker_id"]=a.worker;obj["cache"]=a.cache;save(a.out,a.worker,obj)
if __name__=="__main__":main()
