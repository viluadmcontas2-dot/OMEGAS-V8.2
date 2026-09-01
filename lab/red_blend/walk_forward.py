"""Blind chronological walk-forward for WU-006 G4. Offline science only."""
from __future__ import annotations
from collections import defaultdict
from dataclasses import dataclass
import math, statistics
from typing import Any, Mapping, Sequence

@dataclass(frozen=True)
class Prediction:
    predicted_ms:float;raw_support_count:int;independent_session_count:int;max_training_order:int;method:str
@dataclass(frozen=True)
class PredictorMetrics:
    supported:int;coverage:float;median_abs_relative_error:float;p90_abs_relative_error:float;p95_abs_relative_error:float;max_abs_relative_error:float;median_independent_sessions:float
@dataclass(frozen=True)
class WalkForwardComparison:
    tested_future_episodes:int;leakage_violations:int;metrics:Mapping[str,PredictorMetrics];claim_scope:str="BLIND_WALK_FORWARD_OFFLINE_NOT_PRODUCTION"

def _f(e,key):
    v=float(e[key])
    if not math.isfinite(v):raise ValueError(f"non-finite episode field: {key}")
    return v

def _order(e):
    v=e.get("order")
    if isinstance(v,bool) or not isinstance(v,int):raise ValueError("episode order must be integer")
    return v

def _session(e):
    v=str(e.get("session_key") or "")
    if not v:raise ValueError("episode session_key required")
    return v

def _mass(e):
    v=e.get("window_count",1)
    if isinstance(v,bool):raise ValueError("window_count must be positive integer")
    try:n=int(v)
    except (TypeError,ValueError) as exc:raise ValueError("window_count must be positive integer") from exc
    if n<1:raise ValueError("window_count must be positive integer")
    return n

def _eligible(training,target):
    to=_order(target);return sorted([e for e in training if e.get("fuel","GASOLINA")=="GASOLINA" and _order(e)<to],key=lambda e:(_order(e),_session(e),int(e.get("start_ms",0))))
def _distance(e,t,rpm_scale=80.0,map_scale=.02):
    if rpm_scale<=0 or map_scale<=0:raise ValueError("geometry scales must be positive")
    return math.hypot((_f(e,"rpm")-_f(t,"rpm"))/rpm_scale,(_f(e,"map_bar")-_f(t,"map_bar"))/map_scale)
def _weighted(items,method):
    if not items:return None
    den=sum(w for w,_ in items)
    if den<=0 or not math.isfinite(den):return None
    return Prediction(sum(w*_f(e,"petrol_ms") for w,e in items)/den,sum(_mass(e) for _,e in items),len({_session(e) for _,e in items}),max(_order(e) for _,e in items),method)

def predict_wu006_neighbor_baseline(training:Sequence[Mapping[str,Any]],target:Mapping[str,Any])->Prediction|None:
    cs=[]
    for e in _eligible(training,target):
        d=_distance(e,target)
        if d<=1.5:cs.append((d,e))
    cs.sort(key=lambda p:(p[0],_order(p[1]),int(p[1].get("start_ms",0)),_session(p[1])))
    return _weighted([(1/(.25+d),e) for d,e in cs[:16]],"wu006_neighbor_baseline")

def predict_pooled_gaussian(training,target,*,radius=3.0):
    if radius<=0:raise ValueError("radius must be positive")
    items=[]
    for e in _eligible(training,target):
        d=_distance(e,target)
        if d<=radius:
            w=math.exp(-.5*d*d)*_mass(e)
            if w>0:items.append((w,e))
    return _weighted(items,"pooled_gaussian")

def predict_session_balanced_gaussian(training,target,*,radius=3.0):
    if radius<=0:raise ValueError("radius must be positive")
    groups=defaultdict(list)
    for e in _eligible(training,target):
        d=_distance(e,target)
        if d<=radius:
            w=math.exp(-.5*d*d)*_mass(e)
            if w>0:groups[_session(e)].append((w,e))
    if not groups:return None
    preds=[];raw=0;max_order=-1
    for sk in sorted(groups):
        items=groups[sk];den=sum(w for w,_ in items)
        if den<=0:continue
        preds.append(sum(w*_f(e,"petrol_ms") for w,e in items)/den);raw+=sum(_mass(e) for _,e in items);max_order=max(max_order,max(_order(e) for _,e in items))
    if not preds:return None
    return Prediction(statistics.fmean(preds),raw,len(preds),max_order,"session_balanced_gaussian")

def _q(values,q):
    o=sorted(float(v) for v in values)
    if not o:raise ValueError("quantile requires values")
    p=(len(o)-1)*q;lo=math.floor(p);hi=math.ceil(p);return o[lo] if lo==hi else o[lo]*(1-(p-lo))+o[hi]*(p-lo)
def _metrics(errors,sessions,tested):
    if not errors:raise ValueError("predictor has no supported folds")
    return PredictorMetrics(len(errors),len(errors)/tested,statistics.median(errors),_q(errors,.9),_q(errors,.95),max(errors),statistics.median(sessions))

def compare_gasoline_walk_forward(episodes:Sequence[Mapping[str,Any]])->WalkForwardComparison:
    gas=[dict(e) for e in episodes if e.get("fuel")=="GASOLINA"];gas.sort(key=lambda e:(_order(e),int(e.get("start_ms",0)),_session(e)))
    ps={"wu006_neighbor_baseline":predict_wu006_neighbor_baseline,"pooled_gaussian":predict_pooled_gaussian,"session_balanced_gaussian":predict_session_balanced_gaussian};errors={k:[] for k in ps};sessions={k:[] for k in ps};tested=0;leakage=0
    for target in gas:
        if not any(_order(tr)<_order(target) for tr in gas):continue
        tested+=1;actual=_f(target,"petrol_ms")
        if abs(actual)<=1e-12:continue
        for name,p in ps.items():
            pred=p(gas,target)
            if pred is None:continue
            if pred.max_training_order>=_order(target):leakage+=1
            errors[name].append(abs(pred.predicted_ms-actual)/abs(actual));sessions[name].append(pred.independent_session_count)
    if tested<1:raise ValueError("walk-forward requires future gasoline episode")
    return WalkForwardComparison(tested,leakage,{k:_metrics(errors[k],sessions[k],tested) for k in ps if errors[k]})
