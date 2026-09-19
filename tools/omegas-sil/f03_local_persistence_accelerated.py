#!/usr/bin/env python3
from __future__ import annotations
import argparse, json
from pathlib import Path
import numpy as np
import pandas as pd

CACHE=r"C:\Users\hugov\AppData\Local\AgentRed\cache\omegas-sil-v2\canonical_frames.csv.gz"
COLS=["rpm","petrol_ms","fuel","map_bar","session","sequence"]
CFGS=[(80,.01),(100,.015),(150,.02),(200,.025),(250,.03),(300,.04),(400,.05),(500,.06),(700,.08),(900,.10)]

def load():
    d=pd.read_csv(CACHE,usecols=COLS)
    for c in ["rpm","petrol_ms","map_bar","sequence"]:
        d[c]=pd.to_numeric(d[c],errors="coerce")
    d=d[d.rpm.between(500,6500)&d.map_bar.between(.1,1.2)&d.petrol_ms.between(.7,30)].copy()
    return d.sort_values(["session","sequence"]).reset_index(drop=True)

def ref(train,test):
    tr=train[train.fuel=="PETROL"].copy()
    tr["rb"]=(tr.rpm/180).round().astype(int); tr["mb"]=(tr.map_bar/.025).round().astype(int)
    tab=tr.groupby(["rb","mb"]).petrol_ms.median()
    mp=tr.groupby("mb").petrol_ms.median()
    glob=float(tr.petrol_ms.median())
    out=np.empty(len(test),dtype=float)
    for j,r in enumerate(test.itertuples()):
        rb=int(round(r.rpm/180)); mb=int(round(r.map_bar/.025))
        v=tab.get((rb,mb),np.nan)
        if not np.isfinite(v): v=mp.get(mb,glob)
        out[j]=float(v)
    return out

def cng(d,maxn=7000):
    parts=[]
    for s in d.session.unique():
        te=d[(d.session==s)&(d.fuel=="CNG")].copy()
        tr=d[d.session!=s]
        if len(te)<100 or len(tr[tr.fuel=="PETROL"])<500: continue
        if len(te)>maxn:
            te=te.iloc[np.linspace(0,len(te)-1,maxn).astype(int)].copy()
        rr=ref(tr,te)
        te["err"]=(te.petrol_ms.to_numpy(float)/rr-1)*100
        te=te[np.isfinite(te.err)&(te.err.abs()<60)]
        parts.append(te)
    return pd.concat(parts,ignore_index=True) if parts else pd.DataFrame()

def metrics(vals):
    a=np.abs(np.asarray(vals,float)); a=a[np.isfinite(a)]
    return {
        "n":int(len(a)),
        "mae":float(a.mean()) if len(a) else None,
        "p90":float(np.quantile(a,.9)) if len(a) else None,
        "within4":float((a<=4).mean()*100) if len(a) else None,
    }

def run_variant(x,idx):
    rr,mm=CFGS[idx]
    errors=[]
    matched=0
    total=0
    for _,g in x.groupby("session",sort=False):
        rpm=g.rpm.to_numpy(float); mp=g.map_bar.to_numpy(float); er=g.err.to_numpy(float)
        n=len(g)
        for j in range(1,n):
            start=max(0,j-300)
            # Exact same prior-window semantics as the slow f03, but vectorized in NumPy.
            mask=(np.abs(rpm[start:j]-rpm[j])<=rr)&(np.abs(mp[start:j]-mp[j])<=mm)
            total+=1
            if np.any(mask):
                pred=float(np.median(er[start:j][mask]))
                errors.append(er[j]-pred)
                matched+=1
    return {
        "variant":f"local_r{rr}_m{mm}",
        "rpm_radius":rr,
        "map_radius":mm,
        "metrics":metrics(errors),
        "matched":matched,
        "eligible":total,
        "coverage_pct":100.0*matched/max(total,1),
        "implementation":"exact prior-300 local median, NumPy accelerated",
    }

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--variant",type=int,choices=range(10),required=True)
    ap.add_argument("--out",default="artifact.json")
    a=ap.parse_args()
    d=load(); x=cng(d); result=run_variant(x,a.variant)
    Path(a.out).write_text(json.dumps(result,indent=2),encoding="utf-8")
    print(json.dumps(result))
if __name__=="__main__":
    main()
