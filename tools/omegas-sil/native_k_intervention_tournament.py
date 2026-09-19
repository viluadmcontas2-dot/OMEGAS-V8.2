#!/usr/bin/env python3
"""Adversarial reverse-engineering tournament for real native AutoMatch K interventions.

Uses the exact evidence.json produced by AgentRed #588, whose expected SHA-256 is
hard-gated below. Three real AutoMatch interventions are reconstructed. Each team
runs ten variants. Fitted variants are evaluated leave-one-cycle-out so no cycle
validates itself.

This is evidence analysis only. It does not write the ECU.
"""
from __future__ import annotations
import argparse, concurrent.futures as cf, hashlib, json, math, threading
from pathlib import Path
import numpy as np

EXPECTED_SHA="848e913c6970415c52731b524d66c997d86c17baad5f9da5bbcce7f21a5b3aa2"
DEFAULT_EVIDENCE=r"C:\ProgramData\AgentRed\jobs\588-omegas-native-autocal_fields-20260918-a\evidence.json"
X=np.array([256,512,768,1024,1280,1536,1792,2048,2304,2560,2816,3072,3328,3584,3840,4096,4352,4608,4864,5120,5632,6144,6656,7168,7680,8192,8704,9216,10240,11264],float)

TEAMS={
 "k01":"fixed_authority_baselines",
 "k02":"linear_gain_and_cap",
 "k03":"power_log_gain",
 "k04":"leave_one_cycle_global_gain",
 "k05":"piecewise_axis_gain",
 "k06":"dynamic_cap",
 "k07":"interior_gate_sensitivity",
 "k08":"curve_smoothing",
 "k09":"inverse_direction_falsification",
 "k10":"cycle_normalization",
 "k11":"robust_fit_loss",
 "k12":"monte_carlo_curve_noise",
 "k13":"band_dropout_robustness",
 "k14":"sign_and_overshoot_safety",
 "k15":"bayesian_gain_posterior",
}
_CTX={}

def sha256(p):
    h=hashlib.sha256()
    with open(p,"rb") as f:
        for b in iter(lambda:f.read(1<<20),b""):h.update(b)
    return h.hexdigest()

def load_cycles(path):
    if sha256(path)!=EXPECTED_SHA: raise RuntimeError("EVIDENCE_HASH_MISMATCH")
    s=json.load(open(path,encoding="utf-8-sig"))["result"]["summary"]
    def st(f):return s.get(f,{}).get("changes",[])
    def prior(f,tx):
        a=[e for e in st(f) if e["tx"]<=tx]
        return a[-1] if a else None
    rows=[]
    for ae in st("NUM_AUTOMATCH_EXECUTED"):
        if not ae.get("vals") or ae["vals"][0]<=0:continue
        tx=int(ae["tx"]);n=int(ae["vals"][0])
        muls=[e for e in st("MUL_ACT") if e["tx"]<tx]
        if len(muls)<2:continue
        old=np.asarray(muls[-2]["vals"],float)/16384.;new=np.asarray(muls[-1]["vals"],float)/16384.
        step=new/old
        pc=prior("PETR_MNFLD_PRESS_RV",tx);gc=prior("GAS_MNFLD_PRESS_RV",tx)
        if not(pc and gc):continue
        py=np.asarray(pc["vals"],float);gy=np.asarray(gc["vals"],float)
        order=np.argsort(gy);gys=gy[order];xs=X[order]
        uq=[];ux=[]
        for yy in sorted(set(gys.tolist())):
            vals=[xs[j] for j,v in enumerate(gys) if v==yy]
            uq.append(yy);ux.append(sum(vals)/len(vals))
        uq=np.asarray(uq);ux=np.asarray(ux)
        ge=np.interp(py,uq,ux)
        if len(uq)>=2:
            lo=py<uq[0];hi=py>uq[-1]
            ge[lo]=ux[0]+(py[lo]-uq[0])*(ux[1]-ux[0])/(uq[1]-uq[0])
            ge[hi]=ux[-1]+(py[hi]-uq[-1])*(ux[-1]-ux[-2])/(uq[-1]-uq[-2])
        ratio=ge/X
        strict=(py>=uq[1])&(py<=uq[-2])&(X>=1024)&(X<=8192)&(step>.6)&(step<1.5)&np.isfinite(ratio)&(ratio>0)
        # Current/previous native buffers and maturity counts are available before the event.
        def arr(f):
            e=prior(f,tx)
            return np.asarray(e["vals"],float) if e and e.get("vals") else None
        rows.append({
          "n":n,"tx":tx,"old":old,"new":new,"step":step,"py":py,"gy":gy,"ge":ge,"ratio":ratio,
          "strict":strict,
          "pbuf":arr("PETR_INJ_TBUF"),"gbuf":arr("PETR_INJ_TBUF_GAS"),
          "gbuf_prev":arr("PETR_INJ_TBUF_GAS_PREV"),"pcount":arr("NUM_BUF_UPD_PETR"),"gcount":arr("NUM_BUF_UPD_GAS")
        })
    if len(rows)!=3:raise RuntimeError(f"EXPECTED_3_CYCLES_GOT_{len(rows)}")
    return rows

def flatten(cycles,maskfn=lambda c:c["strict"]):
    rs=[];ys=[];cycles_id=[];axes=[]
    for c in cycles:
        m=maskfn(c)&np.isfinite(c["ratio"])&np.isfinite(c["step"])
        rs.extend(c["ratio"][m]);ys.extend(c["step"][m]);cycles_id.extend([c["n"]]*int(m.sum()));axes.extend(X[m])
    return np.asarray(rs),np.asarray(ys),np.asarray(cycles_id),np.asarray(axes)

def met(y,p):
    y=np.asarray(y,float);p=np.asarray(p,float);ok=np.isfinite(y)&np.isfinite(p)
    y=y[ok];p=p[ok]
    if not len(y):return {"n":0,"score":999.}
    e=np.abs(p-y)*100
    dir_true=np.sign(y-1);dir_pred=np.sign(p-1)
    out={"n":int(len(y)),"mae_step_pct":float(e.mean()),"median_step_pct":float(np.median(e)),
         "p90_step_pct":float(np.quantile(e,.9)),"p99_step_pct":float(np.quantile(e,.99)),
         "within2pct":float((e<=2).mean()*100),"within5pct":float((e<=5).mean()*100),
         "sign_accuracy_pct":float((dir_true==dir_pred).mean()*100),
         "max_abs_step_error_pct":float(e.max())}
    out["score"]=out["mae_step_pct"]+.20*out["p90_step_pct"]+.08*max(0,95-out["sign_accuracy_pct"])+.05*out["max_abs_step_error_pct"]
    return out

def predict_linear(r,g,cap=None):
    d=g*(r-1)
    if cap is not None:d=np.clip(d,-cap,cap)
    return 1+d

def predict_power(r,g,cap=None):
    p=np.power(np.clip(r,.05,20),g)
    if cap is not None:p=1+np.clip(p-1,-cap,cap)
    return p

def fit_gain(train_r,train_y,kind="linear",cap=None,loss="mae"):
    best=(1e9,None)
    for g in np.linspace(0,1.8,361):
        p=predict_linear(train_r,g,cap) if kind=="linear" else predict_power(train_r,g,cap)
        e=np.abs(p-train_y)
        val=float(np.mean(e) if loss=="mae" else np.median(e) if loss=="median" else np.mean(np.minimum(e,0.05)**2))
        if val<best[0]:best=(val,float(g))
    return best[1]

def loco(cycles,pred_builder):
    all_y=[];all_p=[];by=[]
    for held in [1,2,3]:
        tr=[c for c in cycles if c["n"]!=held];te=[c for c in cycles if c["n"]==held]
        p,y,meta=pred_builder(tr,te)
        all_y.extend(y);all_p.extend(p);by.append({"held":held,**met(y,p),**meta})
    return {"aggregate":met(all_y,all_p),"folds":by}

def k01(cycles,i):
    gs=[.20,.35,.50,.65,.80,1.0,1.2,1.4,.5,.35];caps=[None,None,None,None,None,None,None,None,.12,.12]
    r,y,_,_=flatten(cycles);g=gs[i];cap=caps[i];return {"variant":f"fixed_g{g}_cap{cap}","metrics":met(y,predict_linear(r,g,cap)),"fixed35":g==.35 and cap is None}

def k02(cycles,i):
    caps=[.03,.05,.08,.10,.12,.15,.20,.25,.30,None];cap=caps[i]
    def pb(tr,te):
        r,y,_,_=flatten(tr);g=fit_gain(r,y,"linear",cap)
        rt,yt,_,_=flatten(te);return predict_linear(rt,g,cap),yt,{"gain":g,"cap":cap}
    return {"variant":f"loco_linear_cap{cap}",**loco(cycles,pb)}

def k03(cycles,i):
    caps=[.03,.05,.08,.10,.12,.15,.20,.25,.30,None];cap=caps[i]
    def pb(tr,te):
        r,y,_,_=flatten(tr);g=fit_gain(r,y,"power",cap)
        rt,yt,_,_=flatten(te);return predict_power(rt,g,cap),yt,{"gain":g,"cap":cap}
    return {"variant":f"loco_power_cap{cap}",**loco(cycles,pb)}

def k04(cycles,i):
    # Robust shared gains under different training band selections.
    lo=[1024,1280,1536,1792,2048,2304,2560,3072,3584,4096][i]
    def mask(c):return c["strict"]&(X>=lo)
    def pb(tr,te):
        r,y,_,_=flatten(tr,mask);g=fit_gain(r,y,"linear",.20)
        rt,yt,_,_=flatten(te);return predict_linear(rt,g,.20),yt,{"gain":g,"train_axis_min":lo}
    return {"variant":f"shared_gain_axismin{lo}",**loco(cycles,pb)}

def k05(cycles,i):
    splits=[2048,2560,3072,3584,4096,4608,5120,5632,6144,6656];split=splits[i]
    def pb(tr,te):
        r,y,_,a=flatten(tr);low=a<=split;g1=fit_gain(r[low],y[low],"linear",.20) if low.sum()>2 else 1.;g2=fit_gain(r[~low],y[~low],"linear",.20) if (~low).sum()>2 else g1
        rt,yt,_,at=flatten(te);p=np.where(at<=split,predict_linear(rt,g1,.20),predict_linear(rt,g2,.20))
        return p,yt,{"g_low":g1,"g_high":g2,"split":split}
    return {"variant":f"piecewise_{split}",**loco(cycles,pb)}

def k06(cycles,i):
    slopes=[.25,.5,.75,1.,1.25,1.5,2.,2.5,3.,4.];s=slopes[i]
    def dyn(r):return np.minimum(.30,.03+s*np.abs(r-1))
    def pb(tr,te):
        r,y,_,_=flatten(tr);g=fit_gain(r,y,"linear",None)
        rt,yt,_,_=flatten(te);d=g*(rt-1);cap=dyn(rt);p=1+np.clip(d,-cap,cap)
        return p,yt,{"gain":g,"cap_slope":s}
    return {"variant":f"dynamic_cap_{s}",**loco(cycles,pb)}

def k07(cycles,i):
    trims=[0,1,2,3,4,5,6,7,8,9];trim=trims[i]
    def mask(c):
        m=c["strict"].copy();idx=np.where(m)[0]
        if trim and len(idx)>2*trim:
            m[idx[:trim]]=False;m[idx[-trim:]]=False
        return m
    r,y,_,_=flatten(cycles,mask);g=fit_gain(r,y,"linear",.20);return {"variant":f"interior_trim_{trim}","gain":g,"metrics":met(y,predict_linear(r,g,.20)),"n":len(y)}

def smooth(v,w):
    if w<=1:return v.copy()
    p=w//2;z=np.pad(v,(p,p),mode="edge");return np.convolve(z,np.ones(w)/w,mode="valid")[:len(v)]

def k08(cycles,i):
    wins=[1,3,5,7,9,11,13,15,17,19];w=wins[i];cc=[]
    for c in cycles:
        z=dict(c);z["ratio"]=smooth(c["ratio"],w);cc.append(z)
    def pb(tr,te):
        r,y,_,_=flatten(tr);g=fit_gain(r,y,"linear",.20);rt,yt,_,_=flatten(te);return predict_linear(rt,g,.20),yt,{"gain":g,"smooth":w}
    return {"variant":f"ratio_smooth_{w}",**loco(cc,pb)}

def k09(cycles,i):
    powers=[-1.5,-1.2,-1.,-.8,-.5,.5,.8,1.,1.2,1.5];pwr=powers[i];r,y,_,_=flatten(cycles)
    pred=np.power(np.clip(r,.05,20),pwr)
    return {"variant":f"direction_power_{pwr}","metrics":met(y,pred),"expected_direction":pwr>0}

def k10(cycles,i):
    norms=["none","center","median","low","mid","high","logcenter","logmedian","zcenter","zmiddle"];mode=norms[i]
    def transform(r):
        r=r.copy()
        if mode=="none":return r
        if mode in ("center","median","low","mid","high"):
            if mode=="center":k=float(np.mean(r))
            elif mode=="median":k=float(np.median(r))
            elif mode=="low":k=float(np.median(r[:max(1,len(r)//3)]))
            elif mode=="mid":k=float(np.median(r[len(r)//3:2*len(r)//3]))
            else:k=float(np.median(r[2*len(r)//3:]))
            return r/max(k,1e-6)
        lr=np.log(np.clip(r,.05,20))
        if mode=="logcenter":return np.exp(lr-lr.mean())
        if mode=="logmedian":return np.exp(lr-np.median(lr))
        scale=max(np.std(lr),1e-6);offset=lr.mean() if mode=="zcenter" else np.median(lr)
        return np.exp((lr-offset)/scale*.1)
    cc=[]
    for c in cycles:
        z=dict(c);z["ratio"]=transform(c["ratio"]);cc.append(z)
    def pb(tr,te):
        r,y,_,_=flatten(tr);g=fit_gain(r,y,"linear",.20);rt,yt,_,_=flatten(te);return predict_linear(rt,g,.20),yt,{"gain":g,"norm":mode}
    return {"variant":f"norm_{mode}",**loco(cc,pb)}

def k11(cycles,i):
    losses=["mae","median","huber","mae","median","huber","mae","median","huber","mae"];caps=[.05,.05,.05,.12,.12,.12,.20,.20,.20,None]
    loss=losses[i];cap=caps[i]
    def pb(tr,te):
        r,y,_,_=flatten(tr);g=fit_gain(r,y,"linear",cap,loss);rt,yt,_,_=flatten(te);return predict_linear(rt,g,cap),yt,{"gain":g,"loss":loss,"cap":cap}
    return {"variant":f"robust_{loss}_{cap}",**loco(cycles,pb)}

def k12(cycles,i):
    rng=np.random.default_rng(1200+i);noise=[.001,.002,.003,.005,.008,.01,.015,.02,.03,.05][i];errs=[]
    r,y,_,_=flatten(cycles);baseg=fit_gain(r,y,"linear",.20)
    for _ in range(200):
        rn=r*np.exp(rng.normal(0,noise,len(r)))
        p=predict_linear(rn,baseg,.20);errs.extend((p-y).tolist())
    return {"variant":f"ratio_noise_{noise}","gain":baseg,"metrics":met(np.zeros(len(errs)),np.asarray(errs)),"note":"error distribution under perturbation"}

def k13(cycles,i):
    rng=np.random.default_rng(1300+i);drop=[.05,.1,.15,.2,.25,.3,.35,.4,.5,.6][i];fold=[]
    for held in [1,2,3]:
        tr=[c for c in cycles if c["n"]!=held];te=[c for c in cycles if c["n"]==held]
        r,y,_,_=flatten(tr);keep=rng.random(len(r))>drop
        g=fit_gain(r[keep],y[keep],"linear",.20);rt,yt,_,_=flatten(te);fold.append(met(yt,predict_linear(rt,g,.20)))
    return {"variant":f"train_band_drop_{drop}","metrics":{"mae_step_pct":float(np.mean([z["mae_step_pct"] for z in fold])),"p90_step_pct":float(np.mean([z["p90_step_pct"] for z in fold])),"sign_accuracy_pct":float(np.mean([z["sign_accuracy_pct"] for z in fold]))}}

def k14(cycles,i):
    cap=[.02,.03,.04,.05,.06,.08,.10,.12,.15,.20][i];r,y,_,_=flatten(cycles);g=fit_gain(r,y,"linear",cap);p=predict_linear(r,g,cap)
    m=met(y,p);m["overshoot_gt_cap_pct"]=float((np.abs(p-1)>cap+1e-9).mean()*100)
    return {"variant":f"safety_cap_{cap}","gain":g,"metrics":m}

def k15(cycles,i):
    priors=[(.35,.5),(.5,.5),(.7,.5),(1.,.5),(1.2,.5),(.35,.2),(.7,.2),(1.,.2),(1.,.1),(1.,1.)];mu0,var0=priors[i]
    folds=[]
    for held in [1,2,3]:
        tr=[c for c in cycles if c["n"]!=held];te=[c for c in cycles if c["n"]==held]
        r,y,_,_=flatten(tr);x=r-1;z=y-1
        obs_var=.01
        prec=1/var0 + float(np.sum(x*x))/obs_var
        mu=(mu0/var0 + float(np.sum(x*z))/obs_var)/prec
        rt,yt,_,_=flatten(te);fold.append(met(yt,predict_linear(rt,mu,.20)))
    return {"variant":f"posterior_mu{mu0}_v{var0}","metrics":{"mae_step_pct":float(np.mean([z["mae_step_pct"] for z in folds])),"p90_step_pct":float(np.mean([z["p90_step_pct"] for z in folds])),"sign_accuracy_pct":float(np.mean([z["sign_accuracy_pct"] for z in folds]))}}

FUNCS={f"k{i:02d}":globals()[f"k{i:02d}"] for i in range(1,16)}

def score(r):
    m=r.get("aggregate") or r.get("metrics") or {}
    if "score" in m:return float(m["score"])
    return float(m.get("mae_step_pct",999))

def run_one(team,i,cycles):
    try:
        r=FUNCS[team](cycles,i);r.update({"worker":f"{team}.w{i+1:02d}","team":team,"ok":True,"thread":threading.current_thread().name});return r
    except Exception as e:return {"worker":f"{team}.w{i+1:02d}","team":team,"ok":False,"error":repr(e),"metrics":{"score":999}}

def main():
    ap=argparse.ArgumentParser();ap.add_argument("--team",choices=sorted(TEAMS),required=True);ap.add_argument("--evidence",default=DEFAULT_EVIDENCE);ap.add_argument("--out",default="artifacts")
    a=ap.parse_args();cycles=load_cycles(a.evidence)
    with cf.ThreadPoolExecutor(max_workers=10,thread_name_prefix=f"{a.team}-native") as ex:
        rows=[f.result() for f in [ex.submit(run_one,a.team,i,cycles) for i in range(10)]]
    ranked=sorted(rows,key=score)
    obj={"schema":"omegas.native-k-tournament.v1","team":a.team,"topic":TEAMS[a.team],"logical_workers":10,
         "evidence_sha256":EXPECTED_SHA,"real_native_automatch_cycles":3,"ecu_write_performed":False,
         "results":rows,"champion":ranked[0],"runner_up":ranked[1],
         "guardrails":["Real native historical interventions only.","Leave-one-cycle-out wherever parameters are fitted.","No automatic ECU writer."]}
    Path(a.out).mkdir(parents=True,exist_ok=True);(Path(a.out)/f"{a.team}.json").write_text(json.dumps(obj,indent=2,sort_keys=True),encoding="utf-8")
    print(json.dumps({"team":a.team,"workers":10,"champion":ranked[0]},default=str))
if __name__=="__main__":main()
