"""Session-independence diagnostics for WU-006 G3.

Repeated local observations estimate within-session precision. Independent
session support is measured separately. Offline replay only.
"""
from __future__ import annotations
from collections import defaultdict
from dataclasses import dataclass
import math, statistics
from typing import Any, Mapping, Sequence
from lab.red_blend.local_science import MultimodalityDecision, MultimodalityPolicy, detect_multimodality

@dataclass(frozen=True)
class SessionMean:
    session_key:str;count:int;mean:float
@dataclass(frozen=True)
class SessionVarianceDecomposition:
    session_count:int;total_count:int;grand_mean:float;within_variance:float;between_session_variance:float;icc:float;effective_group_size:float;session_means:tuple[SessionMean,...];method_label:str="UNEQUAL_ONE_WAY_RANDOM_EFFECTS_MOMENTS"
@dataclass(frozen=True)
class LosoFold:
    held_out_session:str;train_session_count:int;observed_mean:float;predicted_mean:float;abs_relative_error:float
@dataclass(frozen=True)
class LeaveOneSessionOutReport:
    session_count:int;median_abs_relative_error:float;p90_abs_relative_error:float;max_abs_relative_error:float;folds:tuple[LosoFold,...];predictor_label:str="SESSION_BALANCED_MEAN"
@dataclass(frozen=True)
class SessionMixtureAttribution:
    pooled:MultimodalityDecision;session_centered:MultimodalityDecision;bic_gain_drop:float;separation_drop_sigma:float;interpretation:str;policy_label:str="LAB_HEURISTIC"
@dataclass(frozen=True)
class RealSessionRegionAudit:
    rpm_bin:int;map_bin:int;count:int;session_count:int;decomposition:SessionVarianceDecomposition|None;loso:LeaveOneSessionOutReport|None;mixture_attribution:SessionMixtureAttribution|None;independent_status:str
@dataclass(frozen=True)
class RealSessionAuditReport:
    fuel:str;total_fuel_episodes:int;region_count:int;session_audited_regions:int;insufficient_independent_regions:int;regions:tuple[RealSessionRegionAudit,...];claim_scope:str="SESSION_INDEPENDENCE_DIAGNOSTIC_NOT_PRODUCTION"

def _validated_groups(groups:Mapping[str,Sequence[float]],minimum_sessions:int=2):
    if len(groups)<minimum_sessions:raise ValueError(f"at least {minimum_sessions} independent sessions are required")
    out={}
    for key in sorted(groups):
        if not isinstance(key,str) or not key:raise ValueError("session keys must be non-empty strings")
        vals=[float(x) for x in groups[key]]
        if not vals or not all(math.isfinite(x) for x in vals):raise ValueError("each session must contain finite samples")
        out[key]=vals
    return out

def _quantile(values,q):
    o=sorted(float(x) for x in values)
    if not o:raise ValueError("quantile requires values")
    p=(len(o)-1)*q;lo=math.floor(p);hi=math.ceil(p)
    return o[lo] if lo==hi else o[lo]*(hi-p)+o[hi]*(p-lo)

def decompose_sessions(groups:Mapping[str,Sequence[float]])->SessionVarianceDecomposition:
    clean=_validated_groups(groups);means=tuple(SessionMean(k,len(v),statistics.fmean(v)) for k,v in clean.items());k=len(means);n=sum(x.count for x in means)
    if n<=k:
        grand=statistics.fmean(x.mean for x in means);between=statistics.variance(x.mean for x in means) if k>1 else 0.0
        return SessionVarianceDecomposition(k,n,grand,0.0,max(0.0,between),1.0 if between>0 else 0.0,1.0,means)
    grand=sum(x.count*x.mean for x in means)/n;ssw=sum(sum((v-x.mean)**2 for v in clean[x.session_key]) for x in means);msw=ssw/(n-k);ssb=sum(x.count*(x.mean-grand)**2 for x in means);msb=ssb/(k-1);sum_n2=sum(x.count*x.count for x in means);n0=(n-sum_n2/n)/(k-1);tau=max(0.0,(msb-msw)/n0) if n0>0 else 0.0;den=tau+msw;icc=tau/den if den>0 else 0.0
    return SessionVarianceDecomposition(k,n,grand,msw,tau,min(1.0,max(0.0,icc)),n0,means)

def leave_one_session_out(groups:Mapping[str,Sequence[float]])->LeaveOneSessionOutReport:
    clean=_validated_groups(groups,3);means={k:statistics.fmean(v) for k,v in clean.items()};folds=[]
    for held in sorted(means):
        train=[means[k] for k in sorted(means) if k!=held];pred=statistics.fmean(train);obs=means[held];err=abs(pred-obs)/abs(obs) if abs(obs)>1e-12 else math.inf;folds.append(LosoFold(held,len(train),obs,pred,err))
    errors=[f.abs_relative_error for f in folds]
    return LeaveOneSessionOutReport(len(folds),statistics.median(errors),_quantile(errors,.9),max(errors),tuple(folds))

def attribute_session_mixture(groups:Mapping[str,Sequence[float]],policy:MultimodalityPolicy=MultimodalityPolicy())->SessionMixtureAttribution:
    clean=_validated_groups(groups);pooled=[x for k in sorted(clean) for x in clean[k]];grand=statistics.fmean(pooled);centered=[]
    for k in sorted(clean):
        vals=clean[k];m=statistics.fmean(vals);centered.extend((x-m)+grand for x in vals)
    p=detect_multimodality(pooled,policy);c=detect_multimodality(centered,policy)
    if not p.is_multimodal and not c.is_multimodal:interp="NO_STRONG_POOLED_MULTIMODALITY"
    elif p.is_multimodal and not c.is_multimodal:interp="SESSION_OFFSETS_DOMINANT_CANDIDATE"
    elif p.is_multimodal and c.is_multimodal:interp="WITHIN_SESSION_REGIME_CANDIDATE"
    else:interp="MIXTURE_ATTRIBUTION_AMBIGUOUS"
    return SessionMixtureAttribution(p,c,p.bic_gain-c.bic_gain,p.separation_sigma-c.separation_sigma,interp)

def audit_real_session_regions(episodes:Sequence[dict[str,Any]],*,fuel:str,min_samples:int=4,min_independent_sessions:int=3,policy:MultimodalityPolicy=MultimodalityPolicy())->RealSessionAuditReport:
    if fuel not in {"GASOLINA","GNV"}:raise ValueError("fuel must be GASOLINA or GNV")
    if min_samples<2:raise ValueError("min_samples must be >= 2")
    if min_independent_sessions<3:raise ValueError("min_independent_sessions must be >= 3")
    selected=[e for e in episodes if e.get("fuel")==fuel];regions=defaultdict(list)
    for e in selected:
        try:key=(int(e["rpm_bin"]),int(e["map_bin"]));float(e["petrol_ms"]);sk=str(e["session_key"])
        except (KeyError,TypeError,ValueError) as exc:raise ValueError("episode missing session-science fields") from exc
        if not sk:raise ValueError("episode has invalid session key")
        regions[key].append(e)
    audits=[]
    for (rb,mb),g in sorted(regions.items()):
        if len(g)<min_samples:continue
        sg=defaultdict(list)
        for e in g:sg[str(e["session_key"])].append(float(e["petrol_ms"]))
        sc=len(sg);decomp=decompose_sessions(sg) if sc>=2 else None;attrib=attribute_session_mixture(sg,policy) if sc>=2 else None;loso=leave_one_session_out(sg) if sc>=min_independent_sessions else None;status="SESSION_AUDITED" if loso else "INSUFFICIENT_INDEPENDENT_SESSIONS"
        audits.append(RealSessionRegionAudit(rb,mb,len(g),sc,decomp,loso,attrib,status))
    audits.sort(key=lambda x:(-x.count,x.rpm_bin,x.map_bin));audited=sum(x.independent_status=="SESSION_AUDITED" for x in audits)
    return RealSessionAuditReport(fuel,len(selected),len(audits),audited,len(audits)-audited,tuple(audits))
