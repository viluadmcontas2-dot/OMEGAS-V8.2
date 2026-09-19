#!/usr/bin/env python3
"""
OMEGAS Verde survivor tournament — 15 AgentRed teams x 10 micro-workers.

Operational rule:
  Runtime model learns/aligned gasoline BEFORE gas and then stays on CNG.
  Natural CNG->PETROL returns are hidden validation truth only.
  No hypothesis is allowed to require repeated fuel switching in production.

Research-only; never writes ECU state.
"""
from __future__ import annotations
import argparse, concurrent.futures as cf, json, math, threading
from pathlib import Path
import numpy as np
import pandas as pd

CACHE_DEFAULT=r"C:\Users\hugov\AppData\Local\AgentRed\cache\omegas-sil-v2\canonical_frames.csv.gz"
AUTOMATCH_DEFAULT=r"C:\ProgramData\AgentRed\jobs\641-omegas-native-automatch-interior-fit-20260919-a\automatch_interior.json"
COLS=["rpm","gas_ms","petrol_ms","fuel","gas_pressure_raw","gas_temp_raw","map_bar","session","sequence",
      "recorded_at_ms","dt_ms","dmap","drpm","dpetrol_ms","stale_conflict"]

TEAMS={
 "t01":"initial_petrol_alignment_window",
 "t02":"initial_petrol_alignment_model",
 "t03":"continuous_correction_filter",
 "t04":"adaptive_dual_rate_filter",
 "t05":"early_cng_bias_learning",
 "t06":"pressure_temperature_residual",
 "t07":"low_pulse_deadtime_residual",
 "t08":"transient_guard_on_correction",
 "t09":"k_delta_policy_hidden_truth",
 "t10":"native_automatch_policy_fit",
 "t11":"active_k_closed_loop_sim",
 "t12":"monte_carlo_signal_corruption",
 "t13":"time_to_usable_correction",
 "t14":"ensemble_confidence_gating",
 "t15":"composite_architecture_finalists",
}

def load(path):
    d=pd.read_csv(path,usecols=COLS)
    for c in ["rpm","gas_ms","petrol_ms","gas_pressure_raw","gas_temp_raw","map_bar","sequence","recorded_at_ms","dt_ms","dmap","drpm","dpetrol_ms"]:
        d[c]=pd.to_numeric(d[c],errors="coerce")
    d=d[d.rpm.between(500,6500)&d.map_bar.between(.10,1.20)&d.petrol_ms.between(.7,30)].copy()
    return d.sort_values(["session","sequence"]).reset_index(drop=True)

def abs_metrics(e):
    a=np.abs(np.asarray(e,float));a=a[np.isfinite(a)]
    if not len(a): return {"n":0,"score":999.}
    out={"n":int(len(a)),"mae_pct":float(a.mean()),"median_pct":float(np.median(a)),
         "p90_pct":float(np.quantile(a,.9)),"p95_pct":float(np.quantile(a,.95)),
         "p99_pct":float(np.quantile(a,.99)),"within3_5_pct":float((a<=3.5).mean()*100),
         "within4_pct":float((a<=4).mean()*100),"worst_pct":float(a.max())}
    out["score"]=float(out["mae_pct"]+.20*out["p90_pct"]+.05*out["p99_pct"]+.08*max(0,85-out["within4_pct"]))
    return out

def sample_even(g,n=8000):
    if len(g)<=n:return g
    return g.iloc[np.linspace(0,len(g)-1,n).astype(int)].copy()

def global_reference(train,test,rpm_bw=150.,map_bw=.02):
    tr=train[(train.fuel=="PETROL")&train.petrol_ms.between(.7,30)].copy()
    if len(tr)<100:return np.full(len(test),np.nan)
    tr["rb"]=(tr.rpm/rpm_bw).round().astype(int);tr["mb"]=(tr.map_bar/map_bw).round().astype(int)
    cell=tr.groupby(["rb","mb"]).petrol_ms.median();by_map=tr.groupby("mb").petrol_ms.median();glob=float(tr.petrol_ms.median())
    out=[]
    for r in test.itertuples():
        rb=int(round(r.rpm/rpm_bw));mb=int(round(r.map_bar/map_bw))
        v=cell.get((rb,mb),np.nan)
        if not np.isfinite(v):v=by_map.get(mb,glob)
        out.append(float(v))
    return np.asarray(out)

def initial_petrol(g):
    f=g.fuel.astype(str).to_numpy()
    ix=next((i for i,x in enumerate(f) if x=="CNG"),None)
    if ix is None:return g.iloc[0:0],None
    return g.iloc[:ix][g.iloc[:ix].fuel=="PETROL"].copy(),ix

def fit_alignment(method,pre,base,param=None):
    ok=np.isfinite(base)&(base>.7)&pre.petrol_ms.to_numpy(float).__gt__(.7)
    if ok.sum()<8:return {"kind":"gain","gain":1.}
    y=pre.petrol_ms.to_numpy(float)[ok];b=base[ok];ratio=y/b
    if method=="gain":
        n=int(param or len(ratio));r=ratio[-min(n,len(ratio)):]
        return {"kind":"gain","gain":float(np.median(r))}
    if method=="trim_gain":
        n=int(param or len(ratio));r=np.sort(ratio[-min(n,len(ratio)):]);k=int(.15*len(r));r=r[k:len(r)-k] if len(r)>2*k else r
        return {"kind":"gain","gain":float(r.mean())}
    if method=="offset":
        n=int(param or len(y));return {"kind":"offset","offset":float(np.median((y-b)[-min(n,len(y)):]))}
    if method=="affine":
        n=int(param or len(y));yy=y[-min(n,len(y)):];bb=b[-min(n,len(b)):]
        X=np.column_stack([bb,np.ones(len(bb))]);a,c=np.linalg.lstsq(X,yy,rcond=None)[0]
        return {"kind":"affine","gain":float(np.clip(a,.5,1.5)),"offset":float(np.clip(c,-2,2))}
    if method=="map_gain":
        bw=float(param);z=pre.iloc[np.where(ok)[0]].copy();z["bin"]=(z.map_bar/bw).round().astype(int);z["ratio"]=ratio
        tab=z.groupby("bin").ratio.median().to_dict()
        return {"kind":"map_gain","bw":bw,"table":tab,"fallback":float(np.median(ratio))}
    if method=="rpm_map_gain":
        rb,mb=param;z=pre.iloc[np.where(ok)[0]].copy();z["rbin"]=(z.rpm/rb).round().astype(int);z["mbin"]=(z.map_bar/mb).round().astype(int);z["ratio"]=ratio
        tab=z.groupby(["rbin","mbin"]).ratio.median().to_dict()
        return {"kind":"rpm_map_gain","rb":rb,"mb":mb,"table":tab,"fallback":float(np.median(ratio))}
    return {"kind":"gain","gain":float(np.median(ratio))}

def apply_alignment(model,base,rows):
    if model["kind"]=="gain":return base*model["gain"]
    if model["kind"]=="offset":return base+model["offset"]
    if model["kind"]=="affine":return base*model["gain"]+model["offset"]
    if model["kind"]=="map_gain":
        return np.array([base[i]*float(model["table"].get(int(round(r.map_bar/model["bw"])),model["fallback"])) for i,r in enumerate(rows.itertuples())])
    if model["kind"]=="rpm_map_gain":
        return np.array([base[i]*float(model["table"].get((int(round(r.rpm/model["rb"])),int(round(r.map_bar/model["mb"]))),model["fallback"])) for i,r in enumerate(rows.itertuples())])
    return base

def strict_returns(g):
    rows=[];f=g.fuel.astype(str).to_numpy()
    for i in range(1,len(g)):
        if f[i]!="PETROL" or f[i-1] not in ("CNG","TRANSITION"):continue
        pre=g.iloc[max(0,i-12):i];post=g.iloc[i:min(len(g),i+12)]
        pre=pre[(pre.fuel=="CNG")&pre.petrol_ms.between(.7,30)]
        post=post[(post.fuel=="PETROL")&post.petrol_ms.between(.7,30)]
        if len(pre)<3 or len(post)<3:continue
        a=pre.iloc[-3:];b=post.iloc[:3]
        rg=abs(float(a.rpm.median())-float(b.rpm.median()));mg=abs(float(a.map_bar.median())-float(b.map_bar.median()))
        if rg>50 or mg>.02:continue
        rows.append({"return_index":i,"sequence":int(a.sequence.iloc[-1]),"truth":(float(a.petrol_ms.median())/float(b.petrol_ms.median())-1)*100})
    return rows

def session_contexts(d,align_method="gain",align_param=100,rpm_bw=150.,map_bw=.02):
    out=[]
    for s,g0 in d.groupby("session",sort=False):
        g=g0.reset_index(drop=True)
        anchors=strict_returns(g)
        if not anchors:continue
        pre,first_cng=initial_petrol(g)
        if len(pre)<20:continue
        train=d[d.session!=s]
        base_pre=global_reference(train,pre,rpm_bw,map_bw)
        align=fit_alignment(align_method,pre,base_pre,align_param)
        # Freeze session alignment after initial petrol; later petrol returns are validation only.
        cng=g[g.fuel=="CNG"].copy()
        base_cng=global_reference(train,cng,rpm_bw,map_bw)
        ref_cng=apply_alignment(align,base_cng,cng)
        cng["corr_signal"]=(cng.petrol_ms.to_numpy(float)/ref_cng-1)*100
        out.append({"session":s,"g":g,"pre":pre,"cng":cng,"anchors":anchors,"align":align,"train":train})
    return out

def filter_signal(values,kind,param=None,rows=None):
    x=np.asarray(values,float);p=np.full(len(x),np.nan)
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
        af,asl=param;fast=0.;slow=0.;init=False
        for i,v in enumerate(x):
            if not init:fast=v;slow=v;init=True
            else:slow=(1-asl)*slow+asl*v;fast=(1-af)*fast+af*v
            p[i]=.65*fast+.35*slow
    elif kind=="kalman":
        q,r0=param;m=0.;var=25.;init=False
        for i,v in enumerate(x):
            if not init:m=v;init=True
            var+=q;noise=r0
            if rows is not None:
                rr=rows.iloc[i];noise*=1+min(4,abs(rr.dmap if np.isfinite(rr.dmap) else 0)/.02)
            k=var/(var+noise);m=m+k*(v-m);var=(1-k)*var;p[i]=m
    return p

def anchor_estimates(ctx,filter_kind="raw",filter_param=None,early_bias_frac=0.,transient=None):
    g=ctx["g"];cng=ctx["cng"].copy()
    if cng.empty:return []
    # optional early-CNG bias is learned only from earliest gas frames, not from hidden returns.
    sig=cng.corr_signal.to_numpy(float)
    if early_bias_frac>0:
        k=max(5,int(len(sig)*early_bias_frac));bias=float(np.median(sig[:k]));sig=sig-bias
    if transient is not None:
        dm,dr,authority=transient;clean=sig.copy();last=0.
        for i,r in enumerate(cng.itertuples()):
            tr=(abs(r.dmap) if np.isfinite(r.dmap) else 0)>dm or (abs(r.drpm) if np.isfinite(r.drpm) else 0)>dr
            if tr:clean[i]=authority*sig[i]+(1-authority)*last
            last=clean[i]
        sig=clean
    filt=filter_signal(sig,filter_kind,filter_param,cng)
    seq=cng.sequence.to_numpy()
    out=[]
    for a in ctx["anchors"]:
        eligible=np.where(seq<=a["sequence"])[0]
        if not len(eligible):continue
        ix=int(eligible[-1]);est=float(filt[ix])
        if np.isfinite(est):out.append({"truth":a["truth"],"estimate":est,"error":est-a["truth"],"frames_seen":ix+1})
    return out

def alignment_postpetrol_error(d,method,param):
    errs=[]
    for s,g0 in d.groupby("session"):
        g=g0.reset_index(drop=True);pre,first=initial_petrol(g)
        if first is None or len(pre)<20:continue
        post=g.iloc[first+1:];post=post[post.fuel=="PETROL"].copy()
        if len(post)<20:continue
        train=d[d.session!=s];bp=global_reference(train,pre);model=fit_alignment(method,pre,bp,param)
        bt=global_reference(train,post);pred=apply_alignment(model,bt,post)
        errs.extend((pred-post.petrol_ms.to_numpy(float))/post.petrol_ms.to_numpy(float)*100)
    return abs_metrics(errs)

def return_score(d,align_method,align_param,fkind="raw",fparam=None,early=0.,transient=None):
    es=[]
    for ctx in session_contexts(d,align_method,align_param):
        es.extend(x["error"] for x in anchor_estimates(ctx,fkind,fparam,early,transient))
    return abs_metrics(es)

def native_rows(path):
    try:return json.loads(Path(path).read_text(encoding="utf-8"))["rows"]
    except Exception:return []

def native_policy_score(rows,gain,cap):
    es=[]
    for row in rows:
        ratio=np.asarray(row["ratio"],float);old=np.ones_like(ratio) if "old" not in row else np.asarray(row["old"],float)
        step=np.asarray(row["step"],float);target=np.clip(1+gain*(ratio-1),1-cap,1+cap)
        es.extend(step-target)
    return float(np.mean(np.abs(es))) if es else 999.

def t01(d,i,auto):
    ns=[20,35,50,75,100,150,250,400,800,999999];n=ns[i]
    m=alignment_postpetrol_error(d,"gain",n);return {"variant":f"gain_last_{n}","metrics":m}

def t02(d,i,auto):
    cfg=[("gain",100),("gain",999999),("trim_gain",100),("trim_gain",999999),("offset",100),("offset",999999),("affine",100),("affine",999999),("map_gain",.04),("rpm_map_gain",(300,.04))][i]
    return {"variant":f"{cfg[0]}_{cfg[1]}","metrics":alignment_postpetrol_error(d,*cfg)}

def t03(d,i,auto):
    cfg=[("raw",None),("sma",3),("sma",5),("sma",15),("sma",30),("ema",.2),("ema",.5),("ema",.8),("ema",.95),("median",5)][i]
    return {"variant":f"{cfg[0]}_{cfg[1]}","metrics":return_score(d,"gain",100,*cfg)}

def t04(d,i,auto):
    cfg=[("dual",(.3,.005)),("dual",(.5,.005)),("dual",(.7,.005)),("dual",(.9,.005)),("dual",(.5,.02)),("dual",(.8,.02)),
         ("kalman",(1.,9.)),("kalman",(2.,9.)),("kalman",(4.,16.)),("kalman",(8.,25.))][i]
    return {"variant":f"{cfg[0]}_{cfg[1]}","metrics":return_score(d,"gain",100,*cfg)}

def t05(d,i,auto):
    fr=[.002,.005,.01,.02,.03,.05,.08,.10,.15,.20][i]
    return {"variant":f"early_bias_{fr}","fraction":fr,"metrics":return_score(d,"gain",100,"ema",.8,early=fr)}

def t06(d,i,auto):
    # Early-CNG linear physical residual model evaluated only at hidden returns.
    feats=[["gas_pressure_raw"],["gas_temp_raw"],["gas_pressure_raw","gas_temp_raw"],["petrol_ms"],["map_bar"],["rpm"],
           ["petrol_ms","gas_pressure_raw"],["petrol_ms","gas_temp_raw"],["map_bar","gas_pressure_raw","gas_temp_raw"],
           ["petrol_ms","rpm","map_bar","gas_pressure_raw","gas_temp_raw"]][i]
    errors=[]
    for ctx in session_contexts(d,"gain",100):
        c=ctx["cng"].copy()
        if len(c)<80:continue
        cut=max(20,int(.05*len(c)));tr=c.iloc[:cut].copy()
        y=tr.corr_signal.to_numpy(float);mu=tr[feats].mean();sd=tr[feats].std().replace(0,1)
        X=np.column_stack([np.ones(len(tr))]+[((tr[f]-mu[f])/sd[f]).to_numpy(float) for f in feats])
        beta=np.linalg.lstsq(X,y,rcond=None)[0]
        for a in ctx["anchors"]:
            row=c[c.sequence<=a["sequence"]].tail(1)
            if row.empty:continue
            Xt=np.array([1.]+[float((row[f].iloc[0]-mu[f])/sd[f]) for f in feats]);est=float(Xt@beta);errors.append(est-a["truth"])
    return {"variant":"phys_"+("_".join(feats)),"metrics":abs_metrics(errors)}

def t07(d,i,auto):
    # Low pulse/deadtime-aware correction: add beta/petrol_ms to aligned signal.
    betas=[-2,-1.5,-1,-.5,-.25,0,.25,.5,1,1.5];beta=betas[i];errors=[]
    for ctx in session_contexts(d,"gain",100):
        c=ctx["cng"].copy();sig=c.corr_signal.to_numpy(float)+beta/np.maximum(c.petrol_ms.to_numpy(float),1.)*100
        filt=filter_signal(sig,"ema",.8,c);seq=c.sequence.to_numpy()
        for a in ctx["anchors"]:
            ix=np.where(seq<=a["sequence"])[0]
            if len(ix):errors.append(float(filt[ix[-1]])-a["truth"])
    return {"variant":f"deadtime_beta_{beta}","metrics":abs_metrics(errors)}

def t08(d,i,auto):
    cfg=[(.01,50,0),(.02,80,.1),(.03,100,.2),(.04,150,.3),(.05,200,.4),(.05,250,.5),(.07,300,.5),(.10,400,.6),(.12,500,.7),(.15,600,.8)][i]
    return {"variant":f"guard_{cfg}","metrics":return_score(d,"gain",100,"ema",.8,transient=cfg)}

def t09(d,i,auto):
    # Does modern estimated correction map to hidden direct correction better with native-like authority?
    policies=[("fixed35",.35,.50),("gain60",.60,.25),("gain80",.80,.25),("gain100",1.,.25),("gain100c12",1.,.12),
              ("gain100c05",1.,.05),("gain120c25",1.2,.25),("gain100c20",1.,.20),("gain90c15",.9,.15),("gain110c15",1.1,.15)]
    name,gain,cap=policies[i];errs=[]
    for ctx in session_contexts(d,"gain",100):
        for x in anchor_estimates(ctx,"ema",.8):
            est=x["estimate"]/100;truth=x["truth"]/100
            proposal=np.clip(gain*est,-cap,cap);errs.append((proposal-truth)*100)
    return {"variant":name,"gain":gain,"cap":cap,"metrics":abs_metrics(errs)}

def t10(d,i,auto):
    policies=[(.35,.25),(.6,.25),(.8,.25),(1.,.25),(1.,.20),(1.,.15),(1.,.12),(1.,.08),(1.,.05),(1.2,.05)]
    g,c=policies[i];return {"variant":f"native_gain{g}_cap{c}","native_mae":native_policy_score(native_rows(auto),g,c)}

def t11(d,i,auto):
    # Closed-loop simulation with plant gains centered on real native fit ~1.0.
    rng=np.random.default_rng(1100+i);policies=[(.35,.25),(.6,.25),(.8,.25),(1.,.25),(1.,.20),(1.,.15),(1.,.12),(1.,.08),(1.,.05),(1.1,.12)]
    pg,cap=policies[i];steps=[];fail=0;peak=[]
    observed=np.array([1.0,.995,1.205])
    for _ in range(3000):
        plant=float(np.clip(rng.choice(observed)+rng.normal(0,.08),.6,1.4));e=float(rng.uniform(-20,20));mx=abs(e);n=0
        while abs(e)>4 and n<10:
            delta=float(np.clip(pg*e/100,-cap,cap))*100
            e=e-plant*delta+rng.normal(0,.35);mx=max(mx,abs(e));n+=1
        steps.append(n);peak.append(mx);fail+=int(abs(e)>4)
    return {"variant":f"closed_gain{pg}_cap{cap}","mean_steps":float(np.mean(steps)),"p90_steps":float(np.quantile(steps,.9)),
            "failure_pct":fail/len(steps)*100,"mean_peak_error":float(np.mean(peak)),"simulation_only":True}

def t12(d,i,auto):
    rng=np.random.default_rng(12000+i);drops=[0,.02,.05,.08,.12,.16,.20,.25,.30,.35];drop=drops[i];errors=[]
    for ctx in session_contexts(d,"gain",100):
        c=ctx["cng"].copy()
        if len(c)<20:continue
        keep=rng.random(len(c))>drop;c2=c[keep].copy()
        if c2.empty:continue
        sig=c2.corr_signal.to_numpy(float)*(1+rng.normal(0,.003,len(c2)))
        filt=filter_signal(sig,"ema",.8,c2);seq=c2.sequence.to_numpy()
        for a in ctx["anchors"]:
            ix=np.where(seq<=a["sequence"])[0]
            if len(ix):errors.append(float(filt[ix[-1]])-a["truth"])
    return {"variant":f"drop_{drop}","metrics":abs_metrics(errors)}

def t13(d,i,auto):
    cfg=[("raw",None),("sma",3),("sma",5),("sma",15),("ema",.5),("ema",.8),("ema",.95),("median",5),("dual",(.7,.01)),("kalman",(2.,9.))][i]
    firsts=[]
    for ctx in session_contexts(d,"gain",100):
        c=ctx["cng"].copy();sig=filter_signal(c.corr_signal.to_numpy(float),cfg[0],cfg[1],c);seq=c.sequence.to_numpy()
        for a in ctx["anchors"]:
            end=np.where(seq<=a["sequence"])[0]
            if not len(end):continue
            truth=a["truth"];ok=np.abs(sig[:end[-1]+1]-truth)<=4
            found=None
            for k in range(20,len(ok)+1):
                w=ok[max(0,k-100):k]
                if len(w)>=20 and w.mean()>=.8:found=k;break
            if found is not None:firsts.append(found)
    return {"variant":f"converge_{cfg[0]}_{cfg[1]}","n":len(firsts),"median_frames":float(np.median(firsts)) if firsts else None,
            "p90_frames":float(np.quantile(firsts,.9)) if firsts else None}

def t14(d,i,auto):
    # Ensemble: only trust updates when filters agree.
    spreads=[.5,1.,1.5,2.,3.,4.,5.,6.,8.,10.];thr=spreads[i];errs=[];coverage=0;total=0
    for ctx in session_contexts(d,"gain",100):
        c=ctx["cng"].copy();x=c.corr_signal.to_numpy(float)
        fs=[filter_signal(x,"ema",.5,c),filter_signal(x,"ema",.8,c),filter_signal(x,"median",5,c),filter_signal(x,"dual",(.7,.01),c)]
        seq=c.sequence.to_numpy()
        for a in ctx["anchors"]:
            ix=np.where(seq<=a["sequence"])[0];total+=1
            if not len(ix):continue
            vals=np.array([f[ix[-1]] for f in fs]);spread=float(vals.max()-vals.min())
            if spread<=thr:coverage+=1;errs.append(float(np.median(vals))-a["truth"])
    m=abs_metrics(errs);m["coverage_pct"]=coverage/max(total,1)*100
    return {"variant":f"ensemble_spread_{thr}","metrics":m}

def t15(d,i,auto):
    configs=[
      ("gain",100,"raw",None,0.,None,1.,.25),
      ("gain",100,"ema",.8,0.,None,1.,.25),
      ("trim_gain",100,"ema",.8,0.,None,1.,.25),
      ("gain",50,"ema",.8,0.,(.05,250,.5),1.,.25),
      ("gain",100,"dual",(.7,.01),0.,(.05,250,.5),1.,.25),
      ("gain",100,"ema",.8,.01,(.05,250,.5),1.,.20),
      ("gain",100,"ema",.8,.02,(.05,250,.5),1.,.15),
      ("affine",100,"ema",.8,0.,(.05,250,.5),1.,.20),
      ("map_gain",.04,"ema",.8,0.,(.05,250,.5),1.,.20),
      ("rpm_map_gain",(300,.04),"dual",(.7,.01),.01,(.05,250,.5),1.,.15),
    ]
    am,ap,fk,fp,early,guard,kg,kcap=configs[i];errs=[];frames=[]
    for ctx in session_contexts(d,am,ap):
        for x in anchor_estimates(ctx,fk,fp,early,guard):
            proposal=np.clip(kg*x["estimate"]/100,-kcap,kcap)*100
            errs.append(proposal-x["truth"]);frames.append(x["frames_seen"])
    m=abs_metrics(errs);m["median_frames_seen"]=float(np.median(frames)) if frames else None
    return {"variant":f"composite_{i+1}","config":str(configs[i]),"metrics":m,"runtime_switching_required":False}

FUNCS={"t01":t01,"t02":t02,"t03":t03,"t04":t04,"t05":t05,"t06":t06,"t07":t07,"t08":t08,
       "t09":t09,"t10":t10,"t11":t11,"t12":t12,"t13":t13,"t14":t14,"t15":t15}

def score(r):
    if "metrics" in r:return float(r["metrics"].get("score",999))
    if "native_mae" in r:return float(r["native_mae"])*100
    if "mean_steps" in r:return float(r["mean_steps"])+.1*float(r.get("failure_pct",0))
    if r.get("median_frames") is not None:return float(r["median_frames"])/100
    return 999.

def micro(team,i,d,auto):
    try:
        r=FUNCS[team](d,i,auto);r.update({"worker":f"{team}.w{i+1:02d}","team":team,"thread":threading.current_thread().name,"ok":True});return r
    except Exception as e:return {"worker":f"{team}.w{i+1:02d}","team":team,"ok":False,"error":repr(e),"score":999.}

def main():
    ap=argparse.ArgumentParser();ap.add_argument("--team",choices=sorted(TEAMS),required=True);ap.add_argument("--cache",default=CACHE_DEFAULT);ap.add_argument("--automatch",default=AUTOMATCH_DEFAULT);ap.add_argument("--out",default="artifacts")
    a=ap.parse_args();d=load(a.cache)
    with cf.ThreadPoolExecutor(max_workers=10,thread_name_prefix=f"{a.team}-micro") as ex:
        results=[f.result() for f in [ex.submit(micro,a.team,i,d,a.automatch) for i in range(10)]]
    ranked=sorted(results,key=score)
    out={"schema":"omegas.survivor-team.v1","team":a.team,"topic":TEAMS[a.team],"logical_workers":10,
         "operational_rule":"learn/align petrol once, stay CNG; natural returns are hidden validation only",
         "results":results,"champion":ranked[0],"runner_up":ranked[1],"eliminated":[x["worker"] for x in ranked[2:]]}
    Path(a.out).mkdir(parents=True,exist_ok=True);p=Path(a.out)/f"{a.team}.json";p.write_text(json.dumps(out,indent=2,sort_keys=True),encoding="utf-8")
    print(json.dumps({"team":a.team,"topic":TEAMS[a.team],"workers":10,"champion":out["champion"]},default=str))
if __name__=="__main__":main()
