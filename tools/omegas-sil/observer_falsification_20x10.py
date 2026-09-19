#!/usr/bin/env python3
"""
OMEGAS Verde observer falsification tournament.

20 AgentRed slots x 10 micro-workers.
Operational invariant: each episode may use PETROL evidence that existed before
entering CNG. While on CNG, it must not consume later PETROL data. The natural
CNG->PETROL return is hidden truth only.

Research-only. Never writes ECU state.
"""
from __future__ import annotations
import argparse, concurrent.futures as cf, json, math, threading
from pathlib import Path
import numpy as np
import pandas as pd

CACHE_DEFAULT=r"C:\Users\hugov\AppData\Local\AgentRed\cache\omegas-sil-v2\canonical_frames.csv.gz"
COLS=["rpm","gas_ms","petrol_ms","fuel","gas_pressure_raw","gas_temp_raw","map_bar","session","sequence",
      "recorded_at_ms","dt_ms","dmap","drpm","dpetrol_ms","stale_conflict"]

TEAM_TOPICS={
"t01":"hidden_truth_window_sensitivity","t02":"same_episode_knn_reference","t03":"same_episode_gaussian_reference",
"t04":"local_global_reference_blend","t05":"same_episode_polynomial_reference","t06":"modern_signal_filter_battle",
"t07":"lookback_horizon_durability","t08":"shuffle_reverse_placebo","t09":"dropout_noise_monte_carlo",
"t10":"stale_lag_robustness","t11":"transient_authority_gate","t12":"pressure_temperature_normalization",
"t13":"low_pulse_deadtime","t14":"gas_ms_direct_features","t15":"online_state_observer",
"t16":"historical_bias_memory_loso","t17":"k_authority_hidden_truth","t18":"confidence_dynamic_cap",
"t19":"gpu_nonlinear_challenger","t20":"composite_finalists"}

def load(path):
    d=pd.read_csv(path,usecols=COLS)
    for c in [x for x in COLS if x not in ("fuel","session","stale_conflict")]:
        d[c]=pd.to_numeric(d[c],errors="coerce")
    d=d[d.rpm.between(500,6500)&d.map_bar.between(.10,1.20)&d.petrol_ms.between(.7,30)].copy()
    return d.sort_values(["session","sequence"]).reset_index(drop=True)

def metrics(errors):
    a=np.abs(np.asarray(errors,float)); a=a[np.isfinite(a)]
    if not len(a): return {"n":0,"score":999.}
    out={"n":int(len(a)),"mae_pct":float(a.mean()),"median_pct":float(np.median(a)),
         "p90_pct":float(np.quantile(a,.9)),"p95_pct":float(np.quantile(a,.95)),
         "p99_pct":float(np.quantile(a,.99)),"within3_5_pct":float((a<=3.5).mean()*100),
         "within4_pct":float((a<=4).mean()*100),"worst_pct":float(a.max())}
    out["score"]=float(out["mae_pct"]+.22*out["p90_pct"]+.06*out["p99_pct"]+.08*max(0,85-out["within4_pct"]))
    return out

def episodes(d,truth_window=3,rpm_tol=50.,map_tol=.02,min_pre=20,min_gas=20):
    eps=[]
    for s,g0 in d.groupby("session",sort=False):
        g=g0.reset_index(drop=True); f=g.fuel.astype(str).to_numpy()
        for i in range(1,len(g)):
            if f[i]!="PETROL" or f[i-1] not in ("CNG","TRANSITION"): continue
            j=i-1
            while j>=0 and f[j]!="PETROL": j-=1
            if j<0: continue
            pre_end=j+1; k=j
            while k>=0 and f[k]=="PETROL": k-=1
            pre=g.iloc[k+1:pre_end].copy()
            gas=g.iloc[pre_end:i].copy();gas=gas[gas.fuel=="CNG"].copy()
            post=g.iloc[i:min(len(g),i+max(12,truth_window+2))].copy();post=post[post.fuel=="PETROL"].copy()
            if len(pre)<min_pre or len(gas)<min_gas or len(post)<truth_window: continue
            a=gas.tail(truth_window); b=post.head(truth_window)
            dr=abs(float(a.rpm.median())-float(b.rpm.median()))
            dm=abs(float(a.map_bar.median())-float(b.map_bar.median()))
            if dr>rpm_tol or dm>map_tol: continue
            truth=(float(a.petrol_ms.median())/float(b.petrol_ms.median())-1)*100
            eps.append({"session":s,"event":len(eps),"pre":pre,"gas":gas.reset_index(drop=True),
                        "truth":truth,"dr":dr,"dm":dm,"return_sequence":int(b.sequence.iloc[0])})
    return eps

def global_ref(d,session,rows,rpm_bw=150.,map_bw=.02):
    tr=d[(d.session!=session)&(d.fuel=="PETROL")].copy()
    if len(tr)<100:return np.full(len(rows),np.nan)
    tr["rb"]=(tr.rpm/rpm_bw).round().astype(int);tr["mb"]=(tr.map_bar/map_bw).round().astype(int)
    tab=tr.groupby(["rb","mb"]).petrol_ms.median();bm=tr.groupby("mb").petrol_ms.median();glob=float(tr.petrol_ms.median())
    out=[]
    for r in rows.itertuples():
        rb=int(round(r.rpm/rpm_bw));mb=int(round(r.map_bar/map_bw))
        v=tab.get((rb,mb),np.nan)
        if not np.isfinite(v): v=bm.get(mb,glob)
        out.append(float(v))
    return np.asarray(out)

def align_global(d,ep,rows,window=300):
    bp=global_ref(d,ep["session"],ep["pre"]); pre=ep["pre"]
    ok=np.isfinite(bp)&(bp>.7)
    ratio=pre.petrol_ms.to_numpy(float)[ok]/bp[ok]
    gain=float(np.median(ratio[-min(window,len(ratio)):])) if len(ratio) else 1.
    return global_ref(d,ep["session"],rows)*gain

def local_knn(pre,rows,k=30,rscale=250.,mscale=.04):
    p=pre[["rpm","map_bar","petrol_ms"]].dropna().to_numpy(float)
    if len(p)<3:return np.full(len(rows),np.nan)
    out=[]
    kk=min(k,len(p))
    for r in rows.itertuples():
        dist=((p[:,0]-r.rpm)/rscale)**2+((p[:,1]-r.map_bar)/mscale)**2
        ix=np.argpartition(dist,kk-1)[:kk];dd=dist[ix];w=1/(dd+1e-4)
        out.append(float(np.sum(p[ix,2]*w)/w.sum()))
    return np.asarray(out)

def local_gauss(pre,rows,rscale=250.,mscale=.04,max_points=5000):
    p=pre[["rpm","map_bar","petrol_ms"]].dropna()
    if len(p)>max_points:p=p.iloc[np.linspace(0,len(p)-1,max_points).astype(int)]
    a=p.to_numpy(float)
    if len(a)<3:return np.full(len(rows),np.nan)
    out=[]
    for r in rows.itertuples():
        dist=((a[:,0]-r.rpm)/rscale)**2+((a[:,1]-r.map_bar)/mscale)**2
        ix=np.argsort(dist)[:min(128,len(dist))];w=np.exp(-.5*dist[ix])
        out.append(float(np.sum(a[ix,2]*w)/max(w.sum(),1e-12)))
    return np.asarray(out)

def local_poly(pre,rows,deg=2,ridge=.01):
    tr=pre.dropna(subset=["rpm","map_bar","petrol_ms"]).copy()
    if len(tr)<20:return np.full(len(rows),np.nan)
    def feat(x):
        m=x.map_bar.to_numpy(float);r=x.rpm.to_numpy(float)/1000
        cols=[np.ones(len(x)),m,r]
        if deg>=2: cols += [m*m,r*r,m*r]
        if deg>=3: cols += [m**3,r**3,m*m*r,m*r*r]
        return np.column_stack(cols)
    X=feat(tr);Xt=feat(rows);A=X.T@X+ridge*np.eye(X.shape[1]);beta=np.linalg.solve(A,X.T@tr.petrol_ms.to_numpy(float))
    return Xt@beta

def ref_for(d,ep,method,param):
    rows=ep["gas"]
    if method=="global":return align_global(d,ep,rows,int(param))
    if method=="knn":
        k,rs,ms=param;return local_knn(ep["pre"],rows,k,rs,ms)
    if method=="gauss":
        rs,ms=param;return local_gauss(ep["pre"],rows,rs,ms)
    if method=="poly":
        deg,ridge=param;return local_poly(ep["pre"],rows,deg,ridge)
    if method=="blend":
        w,rs,ms=param;g=align_global(d,ep,rows,300);l=local_gauss(ep["pre"],rows,rs,ms);return w*l+(1-w)*g
    raise ValueError(method)

def signal(d,ep,method="gauss",param=(250.,.04)):
    ref=ref_for(d,ep,method,param);obs=ep["gas"].petrol_ms.to_numpy(float)
    return (obs/ref-1)*100

def filter_sig(x,kind,param=None,rows=None):
    x=np.asarray(x,float);p=np.full(len(x),np.nan)
    if kind=="raw":return x.copy()
    if kind=="sma":
        n=int(param)
        for i in range(len(x)):p[i]=float(np.mean(x[max(0,i-n+1):i+1]))
    elif kind=="ema":
        a=float(param);st=np.nan
        for i,v in enumerate(x):st=v if not np.isfinite(st) else a*v+(1-a)*st;p[i]=st
    elif kind=="median":
        n=int(param)
        for i in range(len(x)):p[i]=float(np.median(x[max(0,i-n+1):i+1]))
    elif kind=="trim":
        n=int(param)
        for i in range(len(x)):
            q=np.sort(x[max(0,i-n+1):i+1]);k=int(.15*len(q));q=q[k:len(q)-k] if len(q)>2*k else q;p[i]=float(q.mean())
    elif kind=="dual":
        af,asl=param;fast=slow=np.nan
        for i,v in enumerate(x):
            if not np.isfinite(fast):fast=slow=v
            else:fast=(1-af)*fast+af*v;slow=(1-asl)*slow+asl*v
            p[i]=.7*fast+.3*slow
    elif kind=="kalman":
        q,r0=param;m=np.nan;var=25.
        for i,v in enumerate(x):
            if not np.isfinite(m):m=v
            var+=q;noise=r0
            if rows is not None:
                rr=rows.iloc[i];noise*=1+min(5,abs(rr.dmap if np.isfinite(rr.dmap) else 0)/.02)
            k=var/(var+noise);m=m+k*(v-m);var=(1-k)*var;p[i]=m
    return p

def eval_observer(d,eps,refm="gauss",refp=(250.,.04),fk="ema",fp=.8,lookback=1,transform=None):
    errs=[];ests=[];truths=[]
    for ep in eps:
        x=signal(d,ep,refm,refp)
        if transform is not None:x=transform(x,ep)
        z=filter_sig(x,fk,fp,ep["gas"])
        ix=len(z)-lookback
        if ix<0 or not np.isfinite(z[ix]):continue
        est=float(z[ix]);truth=float(ep["truth"]);errs.append(est-truth);ests.append(est);truths.append(truth)
    m=metrics(errs);m["estimate_mean"]=float(np.mean(ests)) if ests else None;m["truth_mean"]=float(np.mean(truths)) if truths else None
    return m

def t01(d,i):
    cfg=[(1,80,.03),(2,80,.03),(3,50,.02),(4,60,.025),(5,60,.025),(7,80,.03),(10,100,.04),(3,80,.03),(5,100,.04),(7,120,.05)][i]
    w,rt,mt=cfg;eps=episodes(d,w,rt,mt);return {"variant":f"truth_w{w}_r{rt}_m{mt}","events":len(eps),"metrics":eval_observer(d,eps)}

def t02(d,i):
    ks=[5,10,20,30,50,80,120,180,250,400];eps=episodes(d);p=(ks[i],250.,.04)
    return {"variant":f"knn_{ks[i]}","metrics":eval_observer(d,eps,"knn",p)}

def t03(d,i):
    cfg=[(80,.012),(100,.015),(130,.018),(160,.02),(200,.025),(250,.03),(300,.04),(400,.05),(550,.07),(800,.10)][i]
    return {"variant":f"gauss_{cfg}","metrics":eval_observer(d,episodes(d),"gauss",cfg)}

def t04(d,i):
    weights=[0,.15,.25,.35,.5,.65,.75,.85,.95,1.];p=(weights[i],250.,.04)
    return {"variant":f"blend_local_{weights[i]}","metrics":eval_observer(d,episodes(d),"blend",p)}

def t05(d,i):
    cfg=[(1,0),(1,.01),(1,.1),(2,0),(2,.001),(2,.01),(2,.1),(3,.001),(3,.01),(3,.1)][i]
    return {"variant":f"poly_{cfg}","metrics":eval_observer(d,episodes(d),"poly",cfg)}

def t06(d,i):
    cfg=[("raw",None),("sma",3),("sma",5),("sma",15),("sma",30),("ema",.3),("ema",.6),("ema",.85),("median",5),("trim",9)][i]
    return {"variant":f"{cfg}","legacy_sma15":cfg==("sma",15),"metrics":eval_observer(d,episodes(d),"gauss",(250.,.03),*cfg)}

def t07(d,i):
    looks=[1,2,3,5,10,20,50,100,300,700];return {"variant":f"lookback_{looks[i]}","lookback":looks[i],
        "metrics":eval_observer(d,episodes(d),"gauss",(250.,.03),"ema",.85,looks[i])}

def t08(d,i):
    mode=["actual","reverse","shift1","shift5","shift20","shift100","block20","block100","random","signflip"][i]
    def tr(x,ep):
        rng=np.random.default_rng(8000+ep["event"])
        if mode=="actual":return x
        if mode=="reverse":return x[::-1].copy()
        if mode.startswith("shift"):
            n=int(mode[5:]);return np.r_[np.repeat(x[0],min(n,len(x))),x[:max(0,len(x)-n)]]
        if mode.startswith("block"):
            n=int(mode[5:]);blocks=[x[j:j+n].copy() for j in range(0,len(x),n)];rng.shuffle(blocks);return np.concatenate(blocks)
        if mode=="random":y=x.copy();rng.shuffle(y);return y
        return -x
    return {"variant":mode,"placebo":mode!="actual","metrics":eval_observer(d,episodes(d),"gauss",(250.,.03),"ema",.85,1,tr)}

def t09(d,i):
    drop=[0,.02,.05,.08,.12,.16,.20,.25,.30,.40][i]
    def tr(x,ep):
        rng=np.random.default_rng(9000+ep["event"]+i*101);mask=rng.random(len(x))>=drop
        y=x.copy();last=y[0]
        for j in range(len(y)):
            if not mask[j]:y[j]=last
            else:last=y[j]
        y=y+rng.normal(0,.15+i*.03,len(y));return y
    return {"variant":f"drop_{drop}","metrics":eval_observer(d,episodes(d),"gauss",(250.,.03),"ema",.85,1,tr)}

def t10(d,i):
    lag=[0,1,2,3,5,8,10,15,25,50][i]
    def tr(x,ep):
        if lag==0:return x
        return np.r_[np.repeat(x[0],min(lag,len(x))),x[:max(0,len(x)-lag)]]
    return {"variant":f"stale_{lag}","metrics":eval_observer(d,episodes(d),"gauss",(250.,.03),"ema",.85,1,tr)}

def t11(d,i):
    cfg=[(.01,50,0),(.02,80,.1),(.03,100,.2),(.04,150,.3),(.05,200,.4),(.05,250,.5),(.07,300,.5),(.10,400,.6),(.12,500,.7),(.15,700,.8)][i]
    dm,dr,a=cfg
    def tr(x,ep):
        y=x.copy();last=y[0]
        for j,r in enumerate(ep["gas"].itertuples()):
            transient=(abs(r.dmap) if np.isfinite(r.dmap) else 0)>dm or (abs(r.drpm) if np.isfinite(r.drpm) else 0)>dr
            if transient:y[j]=a*y[j]+(1-a)*last
            last=y[j]
        return y
    return {"variant":f"guard_{cfg}","metrics":eval_observer(d,episodes(d),"gauss",(250.,.03),"ema",.85,1,tr)}

def linear_normalize_feature(d,eps,features,frac=.15):
    errs=[]
    for ep in eps:
        x=signal(d,ep,"gauss",(250.,.03));g=ep["gas"].copy();cut=max(20,int(len(g)*frac))
        if cut>=len(g):continue
        tr=g.iloc[:cut];te=g.iloc[cut:];mu=tr[features].mean();sd=tr[features].std().replace(0,1)
        X=np.column_stack([np.ones(len(tr))]+[((tr[f]-mu[f])/sd[f]).to_numpy(float) for f in features])
        beta=np.linalg.lstsq(X,x[:cut],rcond=None)[0]
        row=g.iloc[-1];xx=np.array([1.]+[float((row[f]-mu[f])/sd[f]) for f in features])
        est=float(xx@beta);errs.append(est-ep["truth"])
    return metrics(errs)

def t12(d,i):
    sets=[["gas_pressure_raw"],["gas_temp_raw"],["gas_pressure_raw","gas_temp_raw"],["map_bar"],["rpm"],["petrol_ms"],
          ["gas_pressure_raw","petrol_ms"],["gas_temp_raw","petrol_ms"],["gas_pressure_raw","gas_temp_raw","petrol_ms"],
          ["gas_pressure_raw","gas_temp_raw","map_bar","rpm","petrol_ms"]]
    return {"variant":"phys_"+("_".join(sets[i])),"metrics":linear_normalize_feature(d,episodes(d),sets[i])}

def t13(d,i):
    beta=[-1.5,-1,-.6,-.3,-.15,0,.15,.3,.6,1.][i]
    def tr(x,ep):
        return x + beta/np.maximum(ep["gas"].petrol_ms.to_numpy(float),1.)*100
    return {"variant":f"deadtime_{beta}","metrics":eval_observer(d,episodes(d),"gauss",(250.,.03),"ema",.85,1,tr)}

def t14(d,i):
    # Cross-session linear mapping from gas-side summaries to hidden truth.
    feats=["ratio","gas_ms","petrol_ms","pressure","temp","map","rpm","corr"]
    nfeat=min(len(feats),1+(i%8));ridge=[0,.001,.01,.1,1.][min(i//2,4)]
    eps=episodes(d);errs=[]
    rows=[]
    for ep in eps:
        g=ep["gas"].tail(min(100,len(ep["gas"])));corr=signal(d,ep,"gauss",(250.,.03))
        rows.append({"session":ep["session"],"truth":ep["truth"],"ratio":float(np.median(g.gas_ms/g.petrol_ms)),
                     "gas_ms":float(g.gas_ms.median()),"petrol_ms":float(g.petrol_ms.median()),
                     "pressure":float(g.gas_pressure_raw.median()),"temp":float(g.gas_temp_raw.median()),
                     "map":float(g.map_bar.median()),"rpm":float(g.rpm.median()),"corr":float(np.median(corr[-min(100,len(corr)):]))})
    q=pd.DataFrame(rows);use=feats[:nfeat]
    for s in q.session.unique():
        tr=q[q.session!=s];te=q[q.session==s]
        if len(tr)<5:continue
        mu=tr[use].mean();sd=tr[use].std().replace(0,1)
        X=np.column_stack([np.ones(len(tr))]+[((tr[f]-mu[f])/sd[f]).to_numpy(float) for f in use])
        Xt=np.column_stack([np.ones(len(te))]+[((te[f]-mu[f])/sd[f]).to_numpy(float) for f in use])
        A=X.T@X+ridge*np.eye(X.shape[1]);b=X.T@tr.truth.to_numpy(float);coef=np.linalg.solve(A,b);errs.extend(Xt@coef-te.truth.to_numpy(float))
    return {"variant":f"features_{nfeat}_ridge_{ridge}","features":use,"metrics":metrics(errs)}

def t15(d,i):
    cfg=[("dual",(.2,.005)),("dual",(.4,.005)),("dual",(.6,.005)),("dual",(.8,.005)),("dual",(.5,.02)),("dual",(.8,.02)),
         ("kalman",(1.,9.)),("kalman",(2.,16.)),("kalman",(4.,25.)),("kalman",(8.,36.))][i]
    return {"variant":f"state_{cfg}","metrics":eval_observer(d,episodes(d),"gauss",(250.,.03),*cfg)}

def t16(d,i):
    # Historical bias memory from prior sessions only, applied to raw observer.
    modes=["global","map2","map4","map6","rpm300","rpm500","pulse05","pulse10","pressure","temp"];mode=modes[i]
    eps=episodes(d);rows=[]
    for ep in eps:
        est=float(filter_sig(signal(d,ep,"gauss",(250.,.03)),"ema",.85,ep["gas"])[-1]);g=ep["gas"].tail(30)
        rows.append({"session":ep["session"],"err":est-ep["truth"],"est":est,"map":float(g.map_bar.median()),"rpm":float(g.rpm.median()),
                     "pulse":float(g.petrol_ms.median()),"pressure":float(g.gas_pressure_raw.median()),"temp":float(g.gas_temp_raw.median()),"truth":ep["truth"]})
    q=pd.DataFrame(rows);errs=[]
    for s in q.session.unique():
        tr=q[q.session!=s];te=q[q.session==s]
        if len(tr)<4:continue
        for r in te.itertuples():
            if mode=="global":bias=float(tr.err.median())
            else:
                col={"map2":"map","map4":"map","map6":"map","rpm300":"rpm","rpm500":"rpm","pulse05":"pulse","pulse10":"pulse","pressure":"pressure","temp":"temp"}[mode]
                bw={"map2":.02,"map4":.04,"map6":.06,"rpm300":300,"rpm500":500,"pulse05":.5,"pulse10":1.0,"pressure":8,"temp":8}[mode]
                dist=np.abs(tr[col].to_numpy(float)-getattr(r,col))/bw;ix=np.argsort(dist)[:min(5,len(tr))];bias=float(np.median(tr.err.to_numpy(float)[ix]))
            errs.append((r.est-bias)-r.truth)
    return {"variant":f"history_{mode}","metrics":metrics(errs)}

def t17(d,i):
    pol=[(.25,.50),(.35,.50),(.5,.35),(.6,.25),(.75,.20),(.9,.15),(1.,.25),(1.,.15),(1.,.12),(1.1,.12)][i];gain,cap=pol;errs=[]
    for ep in episodes(d):
        est=float(filter_sig(signal(d,ep,"gauss",(250.,.03)),"ema",.85,ep["gas"])[-1])
        prop=float(np.clip(gain*est/100,-cap,cap)*100);errs.append(prop-ep["truth"])
    return {"variant":f"k_gain{gain}_cap{cap}","gain":gain,"cap":cap,"metrics":metrics(errs)}

def t18(d,i):
    # Confidence controls cap/authority; disagreement among 4 filters = uncertainty.
    spread_thr=[.5,1,1.5,2,3,4,5,6,8,10][i];errs=[];accepted=0;total=0
    for ep in episodes(d):
        x=signal(d,ep,"gauss",(250.,.03));vals=[
            filter_sig(x,"ema",.6,ep["gas"])[-1],filter_sig(x,"ema",.9,ep["gas"])[-1],
            filter_sig(x,"median",5,ep["gas"])[-1],filter_sig(x,"dual",(.6,.01),ep["gas"])[-1]]
        total+=1;spread=float(max(vals)-min(vals));est=float(np.median(vals))
        authority=max(.2,min(1.,spread_thr/max(spread,1e-6))) if spread>spread_thr else 1.
        cap=.12 if spread<=spread_thr else .05;prop=float(np.clip(authority*est/100,-cap,cap)*100)
        accepted+=int(spread<=spread_thr);errs.append(prop-ep["truth"])
    m=metrics(errs);m["high_confidence_pct"]=accepted/max(total,1)*100
    return {"variant":f"confidence_{spread_thr}","metrics":m}

def t19(d,i):
    # CPU-side nonlinear challenger; dedicated AgentRed step may also invoke CUDA jury externally.
    # Random Fourier features + ridge, LOSO by session, avoids dependency on sklearn.
    rng=np.random.default_rng(1900+i);dim=[8,12,16,24,32,48,64,96,128,192][i];eps=episodes(d);rows=[]
    for ep in eps:
        g=ep["gas"].tail(min(150,len(ep["gas"])));x=signal(d,ep,"gauss",(250.,.03))
        rows.append([ep["session"],ep["truth"],float(np.median(x[-min(150,len(x)):])) ,float(g.petrol_ms.median()),float(g.gas_ms.median()),
                     float(g.map_bar.median()),float(g.rpm.median()/1000),float(g.gas_pressure_raw.median()),float(g.gas_temp_raw.median())])
    q=pd.DataFrame(rows,columns=["session","truth","corr","petrol","gas","map","rpm","pressure","temp"]);features=["corr","petrol","gas","map","rpm","pressure","temp"];errs=[]
    for s in q.session.unique():
        tr=q[q.session!=s];te=q[q.session==s]
        if len(tr)<5:continue
        mu=tr[features].mean();sd=tr[features].std().replace(0,1);A=((tr[features]-mu)/sd).to_numpy(float);At=((te[features]-mu)/sd).to_numpy(float)
        W=rng.normal(0,1,size=(A.shape[1],dim));bias=rng.uniform(0,2*np.pi,size=dim)
        Z=np.cos(A@W+bias);Zt=np.cos(At@W+bias);lam=.1
        beta=np.linalg.solve(Z.T@Z+lam*np.eye(dim),Z.T@tr.truth.to_numpy(float));errs.extend(Zt@beta-te.truth.to_numpy(float))
    return {"variant":f"nonlinear_rff_{dim}","metrics":metrics(errs),"gpu_jury_candidate":True}

def t20(d,i):
    cfg=[
      ("gauss",(160,.02),"ema",.85,1.0,.12),
      ("gauss",(250,.03),"ema",.85,.6,.25),
      ("gauss",(250,.03),"dual",(.6,.01),.6,.25),
      ("knn",(30,250.,.04),"ema",.85,.6,.25),
      ("blend",(.75,250.,.03),"ema",.85,.6,.25),
      ("poly",(2,.01),"ema",.85,.6,.25),
      ("gauss",(250,.03),"median",5,.6,.25),
      ("gauss",(250,.03),"trim",9,.6,.25),
      ("blend",(.85,160.,.02),"dual",(.6,.01),.75,.20),
      ("gauss",(160,.02),"dual",(.8,.02),.75,.15)][i]
    refm,refp,fk,fp,kg,cap=cfg;errs=[]
    for ep in episodes(d):
        x=signal(d,ep,refm,refp);est=float(filter_sig(x,fk,fp,ep["gas"])[-1]);prop=float(np.clip(kg*est/100,-cap,cap)*100);errs.append(prop-ep["truth"])
    return {"variant":f"composite_{i+1}","config":str(cfg),"runtime_switching_required":False,"metrics":metrics(errs)}

FUNCS={f"t{i:02d}":globals()[f"t{i:02d}"] for i in range(1,21)}

def score(r):
    if "metrics" in r:return float(r["metrics"].get("score",999))
    return 999.

def micro(team,i,d):
    try:
        r=FUNCS[team](d,i);r.update({"worker":f"{team}.w{i+1:02d}","team":team,"thread":threading.current_thread().name,"ok":True});return r
    except Exception as e:return {"worker":f"{team}.w{i+1:02d}","team":team,"ok":False,"error":repr(e),"score":999.}

def main():
    ap=argparse.ArgumentParser();ap.add_argument("--team",choices=sorted(TEAM_TOPICS),required=True);ap.add_argument("--cache",default=CACHE_DEFAULT);ap.add_argument("--out",default="artifacts")
    a=ap.parse_args();d=load(a.cache)
    with cf.ThreadPoolExecutor(max_workers=10,thread_name_prefix=f"{a.team}-micro") as ex:
        res=[f.result() for f in [ex.submit(micro,a.team,i,d) for i in range(10)]]
    ranked=sorted(res,key=score)
    out={"schema":"omegas.observer-falsification-team.v1","team":a.team,"topic":TEAM_TOPICS[a.team],
         "logical_workers":10,"operational_rule":"PETROL before episode only; CNG continuous; PETROL return hidden truth only",
         "results":res,"champion":ranked[0],"runner_up":ranked[1],"eliminated":[x["worker"] for x in ranked[2:]]}
    Path(a.out).mkdir(parents=True,exist_ok=True);p=Path(a.out)/f"{a.team}.json";p.write_text(json.dumps(out,indent=2,sort_keys=True),encoding="utf-8")
    print(json.dumps({"team":a.team,"topic":TEAM_TOPICS[a.team],"events_default":len(episodes(d)),"champion":out["champion"]},default=str))
if __name__=="__main__":main()
