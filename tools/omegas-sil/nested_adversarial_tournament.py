#!/usr/bin/env python3
"""
OMEGAS Verde 15x10 adversarial tournament.

Topology:
  15 AgentRed slots (teams) x 10 in-slot logical workers each = 150 workers.

Hard rule:
  Runtime calibration hypotheses MUST NOT depend on repeated PETROL<->CNG switching.
  Natural fuel transitions are allowed only as hidden validation truth.

The harness is research-only. It never writes ECU state.
"""
from __future__ import annotations
import argparse, concurrent.futures as cf, json, math, os, threading
from pathlib import Path
import numpy as np
import pandas as pd

CACHE_DEFAULT = r"C:\Users\hugov\AppData\Local\AgentRed\cache\omegas-sil-v2\canonical_frames.csv.gz"
COLS=["rpm","gas_ms","petrol_ms","fuel","gas_pressure_raw","gas_temp_raw","map_bar","session","sequence",
      "recorded_at_ms","dt_ms","dmap","drpm","dpetrol_ms","stale_conflict"]

_CTX = {}

TEAMS = {
 "t01":"legacy_temporal_baselines",
 "t02":"exponential_temporal_filters",
 "t03":"robust_temporal_filters",
 "t04":"last_ratio_gain",
 "t05":"dual_rate_gain",
 "t06":"state_space_gain",
 "t07":"gasoline_reference_surface",
 "t08":"map_rpm_formula_discovery",
 "t09":"gnv_residual_coordinates",
 "t10":"physical_residual_compensation",
 "t11":"transient_guard_and_shock",
 "t12":"monte_carlo_corruption",
 "t13":"fast_continuous_gnv_learning",
 "t14":"active_k_system_identification",
 "t15":"curve_map_decomposition",
}

def load_cache(path):
    d=pd.read_csv(path,usecols=COLS)
    for c in ["rpm","gas_ms","petrol_ms","gas_pressure_raw","gas_temp_raw","map_bar","sequence","recorded_at_ms","dt_ms","dmap","drpm","dpetrol_ms"]:
        d[c]=pd.to_numeric(d[c],errors="coerce")
    d=d[d.rpm.between(500,6500)&d.map_bar.between(.10,1.20)&d.petrol_ms.between(.7,30)].copy()
    return d.sort_values(["session","sequence"]).reset_index(drop=True)

def metric_from_percent_errors(e):
    a=np.abs(np.asarray(e,float));a=a[np.isfinite(a)]
    if len(a)==0:return {"n":0,"score":999.}
    out={"n":int(len(a)),"mae_pct":float(a.mean()),"median_pct":float(np.median(a)),
         "p90_pct":float(np.quantile(a,.9)),"p95_pct":float(np.quantile(a,.95)),
         "p99_pct":float(np.quantile(a,.99)),"within3_5_pct":float((a<=3.5).mean()*100),
         "within4_pct":float((a<=4).mean()*100),"worst_pct":float(a.max())}
    out["score"]=float(out["mae_pct"]+.20*out["p90_pct"]+.06*out["p99_pct"]+.08*max(0,85-out["within4_pct"]))
    return out

def metric_yhat(y,p):
    y=np.asarray(y,float);p=np.asarray(p,float)
    ok=np.isfinite(y)&np.isfinite(p)&(y>.05)
    if not ok.any():return metric_from_percent_errors([])
    return metric_from_percent_errors((p[ok]-y[ok])/y[ok]*100)

def sample_even(g,max_n=6000):
    if len(g)<=max_n:return g
    idx=np.linspace(0,len(g)-1,max_n).astype(int)
    return g.iloc[idx].copy()

def gasoline_ref(train,test,rpm_bw=150.,map_bw=.02):
    tr=train[(train.fuel=="PETROL")&train.petrol_ms.between(.7,30)].copy()
    if len(tr)<100:return np.full(len(test),np.nan)
    tr["rb"]=(tr.rpm/rpm_bw).round().astype(int);tr["mb"]=(tr.map_bar/map_bw).round().astype(int)
    tab=tr.groupby(["rb","mb"]).petrol_ms.median()
    bymap=tr.groupby("mb").petrol_ms.median();glob=float(tr.petrol_ms.median())
    vals=[]
    for r in test.itertuples():
        rb=int(round(r.rpm/rpm_bw));mb=int(round(r.map_bar/map_bw))
        v=tab.get((rb,mb),np.nan)
        if not np.isfinite(v):v=bymap.get(mb,glob)
        vals.append(float(v))
    return np.asarray(vals)

def petrol_holdouts(d,max_n=5000):
    for s in d.session.unique():
        te=d[(d.session==s)&(d.fuel=="PETROL")].copy()
        tr=d[d.session!=s]
        if len(te)>=50 and len(tr[tr.fuel=="PETROL"])>=500:
            yield s,tr,sample_even(te,max_n)

def continuous_cng_error(d,max_per_session=8000):
    parts=[]
    for s in d.session.unique():
        te=d[(d.session==s)&(d.fuel=="CNG")].copy()
        tr=d[d.session!=s]
        if len(te)<80 or len(tr[tr.fuel=="PETROL"])<500:continue
        te=sample_even(te,max_per_session)
        ref=gasoline_ref(tr,te)
        z=te.copy()
        z["base_petrol_ms"]=ref
        z["corr_pct"]=(z.petrol_ms.to_numpy(float)/ref-1)*100
        z=z[np.isfinite(z.corr_pct)&(z.corr_pct.abs()<80)]
        parts.append(z)
    return pd.concat(parts,ignore_index=True) if parts else pd.DataFrame()

def strict_hidden_anchors(d):
    rows=[]
    for s,g in d.groupby("session",sort=False):
        g=g.reset_index(drop=True);fuel=g.fuel.astype(str).to_numpy()
        for i in range(1,len(g)):
            if fuel[i]!="CNG" or fuel[i-1] not in ("PETROL","TRANSITION"):continue
            pre=g.iloc[max(0,i-12):i];post=g.iloc[i:min(len(g),i+12)]
            pre=pre[(pre.fuel=="PETROL")&pre.petrol_ms.between(.7,30)]
            post=post[(post.fuel=="CNG")&post.petrol_ms.between(.7,30)]
            if len(pre)<3 or len(post)<3:continue
            a=pre.iloc[-3:];b=post.iloc[:3]
            rg=abs(float(a.rpm.median())-float(b.rpm.median()))
            mg=abs(float(a.map_bar.median())-float(b.map_bar.median()))
            if rg>50 or mg>.02:continue
            pp=float(a.petrol_ms.median());cp=float(b.petrol_ms.median())
            rows.append({"session":s,"sequence":int(b.sequence.iloc[0]),"rpm":float(b.rpm.median()),
                         "map_bar":float(b.map_bar.median()),"petrol_ms":cp,
                         "truth_corr_pct":(cp/pp-1)*100})
    return pd.DataFrame(rows)

def temporal_series(d):
    return d[d.fuel=="PETROL"].copy()

def temporal_predict(g,kind,param):
    y=g.petrol_ms.to_numpy(float);p=np.full(len(y),np.nan)
    if kind=="sma":
        n=int(param)
        for i in range(len(y)):
            if i>=2:p[i]=float(np.mean(y[max(0,i-n):i]))
    elif kind=="ema":
        a=float(param);state=np.nan
        for i,v in enumerate(y):
            p[i]=state;state=v if not np.isfinite(state) else a*v+(1-a)*state
    elif kind=="median":
        n=int(param)
        for i in range(len(y)):
            if i>=3:p[i]=float(np.median(y[max(0,i-n):i]))
    elif kind=="trim":
        n=int(param)
        for i in range(len(y)):
            if i>=4:
                a=np.sort(y[max(0,i-n):i]);k=max(0,int(.15*len(a)));q=a[k:len(a)-k] if len(a)-2*k>0 else a
                p[i]=float(q.mean())
    return p

def t01(d,i):
    windows=[2,3,5,8,10,15,20,30,45,60];n=windows[i]
    es=[]
    for g in _CTX["temporal_sessions"]:
        g=sample_even(g,5000);m=metric_yhat(g.petrol_ms,temporal_predict(g,"sma",n));es.append(m["mae_pct"])
    return {"variant":f"SMA_{n}","legacy":True,"mae_pct":float(np.mean(es)),"note":"historical baseline to beat"}

def t02(d,i):
    alphas=[.03,.05,.08,.12,.18,.25,.35,.5,.7,.9];a=alphas[i];es=[]
    for g in _CTX["temporal_sessions"]:
        g=sample_even(g,5000);es.append(metric_yhat(g.petrol_ms,temporal_predict(g,"ema",a))["mae_pct"])
    return {"variant":f"EMA_{a}","mae_pct":float(np.mean(es))}

def t03(d,i):
    cfg=[("median",3),("median",5),("median",9),("median",15),("median",25),
         ("trim",5),("trim",9),("trim",15),("trim",25),("trim",35)][i];es=[]
    for g in _CTX["temporal_sessions"]:
        g=sample_even(g,5000);es.append(metric_yhat(g.petrol_ms,temporal_predict(g,*cfg))["mae_pct"])
    return {"variant":f"{cfg[0]}_{cfg[1]}","mae_pct":float(np.mean(es))}

def gain_holdout(d,mode,cfg):
    fold=[]
    for s,te,base in _CTX["gain_folds"]:
        y=te.petrol_ms.to_numpy(float);pred=[];gf=1.;gs=1.;var=.01
        for j,r in enumerate(te.itertuples()):
            pred.append(base[j]*gf*gs)
            if not np.isfinite(base[j]) or base[j]<=.7:continue
            obs=y[j]/base[j]
            if mode=="last":
                a,clip=cfg;z=float(np.clip(obs,1-clip,1+clip));gf=(1-a)*gf+a*z
            elif mode=="dual":
                af,asl,clip=cfg;z=float(np.clip(obs,1-clip,1+clip));gs=(1-asl)*gs+asl*z;res=z/max(gs,1e-6);gf=(1-af)*gf+af*res
            elif mode=="kalman":
                q,r0=cfg;var+=q;noise=r0*(1+min(5,abs(r.dmap if np.isfinite(r.dmap) else 0)/.02));k=var/(var+noise);gf=gf+k*(obs-gf);var=(1-k)*var
        fold.append(metric_yhat(y,pred))
    mae=float(np.mean([x["mae_pct"] for x in fold]));p90=float(np.mean([x["p90_pct"] for x in fold]))
    w4=float(np.mean([x["within4_pct"] for x in fold]))
    return {"mae_pct":mae,"p90_pct":p90,"within4_pct":w4,"score":mae+.2*p90+.08*max(0,85-w4)}

def t04(d,i):
    alphas=[.2,.35,.5,.65,.8,.9,1.,1.,1.,1.];clips=[.5,.5,.5,.5,.5,.5,.5,.35,.25,.15]
    x=gain_holdout(d,"last",(alphas[i],clips[i]));return {"variant":f"last_a{alphas[i]}_c{clips[i]}",**x}

def t05(d,i):
    cfgs=[(.3,.005,.5),(.5,.005,.5),(.7,.005,.5),(.9,.005,.5),(.5,.01,.4),(.7,.01,.4),(.9,.01,.4),(.7,.02,.3),(.9,.02,.3),(1.,.02,.25)]
    cfg=cfgs[i];return {"variant":f"dual_{cfg}",**gain_holdout(d,"dual",cfg)}

def t06(d,i):
    qs=[.0001,.0003,.0007,.001,.002,.004,.007,.01,.02,.04];rs=[.003,.003,.004,.005,.006,.008,.01,.015,.02,.03]
    cfg=(qs[i],rs[i]);return {"variant":f"kalman_{cfg}",**gain_holdout(d,"kalman",cfg)}

def t07(d,i):
    cfgs=[(80,.012),(100,.015),(120,.018),(150,.02),(180,.025),(220,.03),(280,.035),(350,.04),(450,.05),(600,.07)]
    rb,mb=cfgs[i];errs=[]
    for s,tr,te in petrol_holdouts(d,3000):
        p=gasoline_ref(tr,te,rb,mb);errs.append(metric_yhat(te.petrol_ms,p)["mae_pct"])
    return {"variant":f"grid_r{rb}_m{mb}","mae_pct":float(np.mean(errs))}

def formula_features(df,degree,logmap=False):
    m=np.clip(df.map_bar.to_numpy(float),.05,None);r=df.rpm.to_numpy(float)/1000
    x=[np.ones(len(df)),m,r]
    if logmap:x.append(np.log(m))
    if degree>=2:x += [m*m,r*r,m*r]
    if degree>=3:x += [m**3,r**3,m*m*r,m*r*r]
    if degree>=4:x += [m**4,r**4,m**3*r,m*r**3]
    return np.column_stack(x)

def t08(d,i):
    cfg=[(1,False),(1,True),(2,False),(2,True),(3,False),(3,True),(4,False),(4,True),(3,False),(4,False)][i]
    deg,logmap=cfg;ridge=[0,0,0,0,0,0,.01,.01,.1,1.][i];errs=[]
    for s,tr,te in petrol_holdouts(d,3000):
        tr=sample_even(tr[tr.fuel=="PETROL"],12000);X=formula_features(tr,deg,logmap);Xt=formula_features(te,deg,logmap)
        A=X.T@X+ridge*np.eye(X.shape[1]);b=X.T@tr.petrol_ms.to_numpy(float);beta=np.linalg.solve(A,b)
        errs.append(metric_yhat(te.petrol_ms,Xt@beta)["mae_pct"])
    return {"variant":f"poly_d{deg}_log{logmap}_r{ridge}","mae_pct":float(np.mean(errs))}

def residual_field_model(train,test,coord,bin_scale):
    tr=train.copy()
    if coord=="petrol":
        tr["a"]=(tr.petrol_ms/bin_scale[0]).round().astype(int);tab=tr.groupby("a").corr_pct.median();glob=float(tr.corr_pct.median())
        return np.array([float(tab.get(int(round(r.petrol_ms/bin_scale[0])),glob)) for r in test.itertuples()])
    if coord=="map":
        tr["a"]=(tr.map_bar/bin_scale[0]).round().astype(int);tab=tr.groupby("a").corr_pct.median();glob=float(tr.corr_pct.median())
        return np.array([float(tab.get(int(round(r.map_bar/bin_scale[0])),glob)) for r in test.itertuples()])
    if coord=="rpm_petrol":
        tr["a"]=(tr.rpm/bin_scale[0]).round().astype(int);tr["b"]=(tr.petrol_ms/bin_scale[1]).round().astype(int)
        tab=tr.groupby(["a","b"]).corr_pct.median();glob=float(tr.corr_pct.median())
        return np.array([float(tab.get((int(round(r.rpm/bin_scale[0])),int(round(r.petrol_ms/bin_scale[1]))),glob)) for r in test.itertuples()])
    tr["a"]=(tr.rpm/bin_scale[0]).round().astype(int);tr["b"]=(tr.map_bar/bin_scale[1]).round().astype(int)
    tab=tr.groupby(["a","b"]).corr_pct.median();glob=float(tr.corr_pct.median())
    return np.array([float(tab.get((int(round(r.rpm/bin_scale[0])),int(round(r.map_bar/bin_scale[1]))),glob)) for r in test.itertuples()])

def t09(d,i):
    x=_CTX["cng_error"]
    cfg=[("petrol",(.25,)),("petrol",(.5,)),("petrol",(1.,)),("map",(.02,)),("map",(.04,)),
         ("rpm_petrol",(150,.25)),("rpm_petrol",(300,.5)),("rpm_petrol",(500,1.)),("rpm_map",(200,.02)),("rpm_map",(400,.04))][i]
    errs=[]
    for s,g in x.groupby("session"):
        g=g.sort_values("sequence");cut=max(30,int(.15*len(g)))
        if len(g)<=cut+30:continue
        p=residual_field_model(g.iloc[:cut],g.iloc[cut:],cfg[0],cfg[1]);errs.extend(g.iloc[cut:].corr_pct.to_numpy(float)-p)
    return {"variant":f"{cfg[0]}_{cfg[1]}","metrics":metric_from_percent_errors(errs)}

def t10(d,i):
    x=_CTX["cng_error"]
    feature_sets=[
      ["petrol_ms"],["map_bar"],["rpm"],["gas_pressure_raw"],["gas_temp_raw"],
      ["gas_pressure_raw","gas_temp_raw"],["petrol_ms","gas_pressure_raw"],["map_bar","gas_pressure_raw"],
      ["petrol_ms","rpm","gas_pressure_raw","gas_temp_raw"],["petrol_ms","rpm","map_bar","gas_pressure_raw","gas_temp_raw","gas_ms"]]
    feats=feature_sets[i];errs=[]
    for s,g in x.groupby("session"):
        g=g.sort_values("sequence");cut=max(50,int(.2*len(g)))
        if len(g)<=cut+40:continue
        tr=g.iloc[:cut];te=g.iloc[cut:];mu=tr[feats].mean();sd=tr[feats].std().replace(0,1)
        X=np.column_stack([np.ones(len(tr))]+[((tr[f]-mu[f])/sd[f]).to_numpy(float) for f in feats])
        Xt=np.column_stack([np.ones(len(te))]+[((te[f]-mu[f])/sd[f]).to_numpy(float) for f in feats])
        beta=np.linalg.lstsq(X,tr.corr_pct.to_numpy(float),rcond=None)[0];errs.extend(te.corr_pct.to_numpy(float)-Xt@beta)
    return {"variant":"phys_"+("_".join(feats)),"metrics":metric_from_percent_errors(errs)}

def t11(d,i):
    cfgs=[(.01,50,0),(.015,80,.1),(.02,100,.2),(.025,120,.25),(.03,150,.3),(.04,200,.4),(.05,250,.5),(.07,300,.6),(.10,400,.7),(.15,600,.8)]
    dm,dr,fb=cfgs[i];fold=[]
    for s,tr,te in petrol_holdouts(d,4000):
        base=gasoline_ref(tr,te);y=te.petrol_ms.to_numpy(float);gain=1.;p=[]
        for j,r in enumerate(te.itertuples()):
            p.append(base[j]*gain)
            if base[j]>.7:
                obs=y[j]/base[j]
                transient=(abs(r.dmap) if np.isfinite(r.dmap) else 0)>dm or (abs(r.drpm) if np.isfinite(r.drpm) else 0)>dr
                a=fb if transient else 1.0;gain=(1-a)*gain+a*np.clip(obs,.7,1.3)
        fold.append(metric_yhat(y,p))
    return {"variant":f"guard_m{dm}_r{dr}_fb{fb}","mae_pct":float(np.mean([z["mae_pct"] for z in fold])),
            "p90_pct":float(np.mean([z["p90_pct"] for z in fold])),"within4_pct":float(np.mean([z["within4_pct"] for z in fold]))}

def t12(d,i):
    rng=np.random.default_rng(1200+i);sessions=_CTX["mc_sessions"]
    drop=[0,.03,.05,.08,.10,.12,.15,.18,.22,.28][i];noise=[0,.001,.002,.003,.004,.005,.006,.008,.01,.015][i]
    methods={"sma15":[],"ema35":[],"last":[],"median5":[]}
    for _ in range(40):
        x=sessions[int(rng.integers(0,len(sessions)))];keep=rng.random(len(x))>drop;y=x.petrol_ms.to_numpy(float)[keep]
        if len(y)<20:continue
        noisy=y*(1+rng.normal(0,noise,len(y)))
        preds={}
        preds["sma15"]=np.array([np.nan if j<3 else noisy[max(0,j-15):j].mean() for j in range(len(y))])
        st=np.nan;p=[]
        for v in noisy:p.append(st);st=v if not np.isfinite(st) else .35*v+.65*st
        preds["ema35"]=np.asarray(p)
        preds["last"]=np.r_[np.nan,noisy[:-1]]
        preds["median5"]=np.array([np.nan if j<3 else np.median(noisy[max(0,j-5):j]) for j in range(len(y))])
        for k,pred in preds.items():methods[k].append(metric_yhat(y,pred)["mae_pct"])
    return {"variant":f"drop{drop}_noise{noise}","methods":{k:{"mean_mae":float(np.mean(v)),"p90_mae":float(np.quantile(v,.9))} for k,v in methods.items()}}

def t13(d,i):
    x=_CTX["cng_error"];fracs=[.005,.01,.02,.03,.05,.08,.10,.15,.20,.30];frac=fracs[i];errs=[];frames=[];sessions=0
    for s,g in x.groupby("session"):
        g=g.sort_values("sequence").reset_index(drop=True);cut=max(12,int(len(g)*frac))
        if len(g)<=cut+50:continue
        tr=g.iloc[:cut];te=g.iloc[cut:];pred=residual_field_model(tr,te,"rpm_petrol",(300,.5))
        errs.extend(te.corr_pct.to_numpy(float)-pred);frames.append(cut);sessions+=1
    return {"variant":f"early_{frac:.3f}","learn_fraction":frac,"median_training_frames":float(np.median(frames)) if frames else None,
            "sessions":sessions,"metrics":metric_from_percent_errors(errs),"runtime_switching_required":False}

def t14(d,i):
    # Simulation-only active system identification. No automatic writer implied.
    rng=np.random.default_rng(14000+i)
    probe_mags=[.5,1.,1.5,2.,2.5,3.,3.5,4.,4.5,5.];probe=probe_mags[i];steps=[];fails=0;peaks=[]
    for _ in range(2500):
        gain=float(rng.uniform(.35,1.8));err=float(rng.uniform(-20,20));peak=abs(err)
        delta=np.sign(err if abs(err)>.1 else 1)*probe
        before=err;after=err-gain*delta+rng.normal(0,.35);ghat=(before-after)/delta if abs(delta)>.1 else 1
        if not np.isfinite(ghat) or ghat<.15:ghat=1.
        err=after;n=1;peak=max(peak,abs(err))
        while abs(err)>4 and n<10:
            dk=float(np.clip(err/max(ghat,.25),-5,5))
            err=err-gain*dk+rng.normal(0,.35);peak=max(peak,abs(err));n+=1
        steps.append(n);peaks.append(peak);fails+=int(abs(err)>4)
    return {"variant":f"probe_{probe}pct","simulation_only":True,"human_confirmation_required":True,
            "mean_steps":float(np.mean(steps)),"p90_steps":float(np.quantile(steps,.9)),
            "failure_pct":fails/len(steps)*100,"mean_peak_error":float(np.mean(peaks))}

def t15(d,i):
    x=_CTX["cng_error"];cfgs=[(.02,150,.25),(.03,200,.25),(.05,250,.25),(.08,300,.5),(.10,300,.5),
       (.10,400,.5),(.15,300,.5),(.15,500,1.),(.20,400,.5),(.25,500,1.)]
    frac,rpb,pb=cfgs[i];errs=[];frames=[]
    for s,g in x.groupby("session"):
        g=g.sort_values("sequence").reset_index(drop=True);cut=max(20,int(frac*len(g)))
        if len(g)<=cut+50:continue
        tr=g.iloc[:cut].copy();te=g.iloc[cut:].copy()
        # Curve K first: correction versus petrol_ms. Map K only residual RPM x petrol.
        tr["pb"]=(tr.petrol_ms/pb).round().astype(int);curve=tr.groupby("pb").corr_pct.median();glob=float(tr.corr_pct.median())
        tr["curve"]=tr.pb.map(curve).fillna(glob);tr["rb"]=(tr.rpm/rpb).round().astype(int);tr["res"]=tr.corr_pct-tr["curve"]
        local=tr.groupby(["rb","pb"]).res.median()
        pred=[]
        for r in te.itertuples():
            b=int(round(r.petrol_ms/pb));rr=int(round(r.rpm/rpb));cv=float(curve.get(b,glob));pred.append(cv+float(local.get((rr,b),0.)))
        errs.extend(te.corr_pct.to_numpy(float)-np.asarray(pred));frames.append(cut)
    return {"variant":f"curve_map_f{frac}_r{rpb}_p{pb}","median_training_frames":float(np.median(frames)) if frames else None,
            "metrics":metric_from_percent_errors(errs),"architecture":"global Curve-K first, residual Map-K second","runtime_switching_required":False}

FUNCS={"t01":t01,"t02":t02,"t03":t03,"t04":t04,"t05":t05,"t06":t06,"t07":t07,"t08":t08,
       "t09":t09,"t10":t10,"t11":t11,"t12":t12,"t13":t13,"t14":t14,"t15":t15}

def result_score(r):
    if "metrics" in r:return float(r["metrics"].get("score",999))
    if "score" in r:return float(r["score"])
    if "mae_pct" in r:return float(r["mae_pct"])
    if "mean_steps" in r:return float(r["mean_steps"])+.1*float(r.get("failure_pct",0))
    if "methods" in r:return min(float(v["mean_mae"]) for v in r["methods"].values())
    return 999.

def worker(team,idx,d):
    name=f"{team}.w{idx+1:02d}"
    try:
        r=FUNCS[team](d,idx);r.update({"worker":name,"team":team,"thread":threading.current_thread().name,"ok":True})
        return r
    except Exception as e:
        return {"worker":name,"team":team,"ok":False,"error":repr(e),"score":999.}

def prepare_context(team,d):
    _CTX.clear()
    if team in {"t01","t02","t03"}:
        _CTX["temporal_sessions"]=[sample_even(g,5000) for _,g in temporal_series(d).groupby("session")]
    if team in {"t04","t05","t06","t11"}:
        folds=[]
        for s,tr,te in petrol_holdouts(d,4000):
            folds.append((s,te,gasoline_ref(tr,te)))
        _CTX["gain_folds"]=folds
    if team=="t12":
        _CTX["mc_sessions"]=[sample_even(x,3500) for _,x in temporal_series(d).groupby("session") if len(x)>100]
    if team in {"t09","t10","t13","t15"}:
        _CTX["cng_error"]=continuous_cng_error(d,7000)

def main():
    ap=argparse.ArgumentParser();ap.add_argument("--team",choices=sorted(TEAMS),required=True);ap.add_argument("--cache",default=CACHE_DEFAULT);ap.add_argument("--out",default="artifacts")
    a=ap.parse_args();d=load_cache(a.cache);prepare_context(a.team,d)
    with cf.ThreadPoolExecutor(max_workers=10,thread_name_prefix=f"{a.team}-micro") as ex:
        fut=[ex.submit(worker,a.team,i,d) for i in range(10)]
        results=[f.result() for f in fut]
    ranked=sorted(results,key=result_score)
    out={"schema":"omegas.adversarial-team.v1","team":a.team,"topic":TEAMS[a.team],"logical_workers":10,
         "runtime_switching_required":False,"natural_switches_policy":"hidden validation only",
         "results":results,"champion":ranked[0],"runner_up":ranked[1],"eliminated":[r["worker"] for r in ranked[2:]],
         "notes":["ProgBase/SMA is a legacy baseline, never the target architecture.",
                  "All K-writing concepts remain research/manual-confirmation only in this harness."]}
    Path(a.out).mkdir(parents=True,exist_ok=True)
    p=Path(a.out)/f"{a.team}.json";p.write_text(json.dumps(out,indent=2,sort_keys=True),encoding="utf-8")
    print(json.dumps({"team":a.team,"topic":TEAMS[a.team],"workers":10,"champion":out["champion"]},default=str))
if __name__=="__main__":main()
