"""Deterministic offline local-distribution science reused by WU-006.

Dependency-free and outside Android runtime. Multimodality thresholds are
LAB_HEURISTIC only, never production authority.
"""
from __future__ import annotations
from dataclasses import dataclass
import math, random, statistics
from typing import Sequence

_VARIANCE_FLOOR = 1e-12

@dataclass(frozen=True)
class DistributionSummary:
    count:int; mean:float; median:float; std:float; mad:float; p10:float; p90:float; cv:float
@dataclass(frozen=True)
class BootstrapInterval:
    low:float; estimate:float; high:float; draws:int; seed:int
@dataclass(frozen=True)
class GaussianFit:
    mean:float; variance:float; log_likelihood:float; bic:float
@dataclass(frozen=True)
class GaussianComponent:
    weight:float; mean:float; variance:float
@dataclass(frozen=True)
class GaussianMixtureFit:
    components:tuple[GaussianComponent,GaussianComponent]; log_likelihood:float; bic:float; iterations:int; converged:bool
@dataclass(frozen=True)
class MultimodalityPolicy:
    bic_gain_min:float=10.0; min_component_weight:float=0.15; separation_sigma_min:float=2.5
@dataclass(frozen=True)
class MultimodalityDecision:
    is_multimodal:bool; bic_gain:float; min_component_weight:float; separation_sigma:float; one:GaussianFit; two:GaussianMixtureFit; policy_label:str="LAB_HEURISTIC"

def _validated(samples:Sequence[float])->list[float]:
    values=[float(x) for x in samples]
    if len(values)<2: raise ValueError("at least two finite samples are required")
    if not all(math.isfinite(x) for x in values): raise ValueError("all samples must be finite")
    return values

def _quantile(sorted_values:Sequence[float],q:float)->float:
    if not 0.0<=q<=1.0: raise ValueError("q must be in [0, 1]")
    n=len(sorted_values)
    if n==1:return float(sorted_values[0])
    index=(n-1)*q;lo=int(math.floor(index));hi=int(math.ceil(index))
    if lo==hi:return float(sorted_values[lo])
    f=index-lo;return float(sorted_values[lo]*(1-f)+sorted_values[hi]*f)

def summarize_distribution(samples:Sequence[float])->DistributionSummary:
    v=_validated(samples);o=sorted(v);mean=statistics.fmean(v);median=statistics.median(o);std=statistics.stdev(v)
    dev=sorted(abs(x-median) for x in v);mad=statistics.median(dev);cv=std/abs(mean) if abs(mean)>1e-12 else math.inf
    return DistributionSummary(len(v),mean,median,std,mad,_quantile(o,.10),_quantile(o,.90),cv)

def bootstrap_mean_interval(samples:Sequence[float],draws:int=2000,seed:int=0,alpha:float=.05)->BootstrapInterval:
    v=_validated(samples)
    if draws<100:raise ValueError("draws must be >= 100")
    if not 0<alpha<1:raise ValueError("alpha must be in (0, 1)")
    rng=random.Random(seed);n=len(v);means=[]
    for _ in range(draws):means.append(sum(v[rng.randrange(n)] for _ in range(n))/n)
    means.sort();return BootstrapInterval(_quantile(means,alpha/2),statistics.fmean(v),_quantile(means,1-alpha/2),draws,seed)

def _gaussian_log_pdf(x,mean,variance):
    variance=max(float(variance),_VARIANCE_FLOOR);return -.5*(math.log(2*math.pi*variance)+((x-mean)**2)/variance)

def fit_gaussian(samples):
    v=_validated(samples);n=len(v);mean=statistics.fmean(v);variance=max(sum((x-mean)**2 for x in v)/n,_VARIANCE_FLOOR)
    ll=sum(_gaussian_log_pdf(x,mean,variance) for x in v);return GaussianFit(mean,variance,ll,2*math.log(n)-2*ll)

def _logsumexp2(a,b):m=max(a,b);return m+math.log(math.exp(a-m)+math.exp(b-m))
def _degenerate_mixture(one,n):
    c=GaussianComponent(.5,one.mean,one.variance);return GaussianMixtureFit((c,c),one.log_likelihood,5*math.log(n)-2*one.log_likelihood,0,False)

def fit_gmm2(samples,max_iterations:int=200,tolerance:float=1e-9):
    v=_validated(samples)
    if max_iterations<1:raise ValueError("max_iterations must be >= 1")
    if tolerance<=0 or not math.isfinite(tolerance):raise ValueError("tolerance must be finite and > 0")
    n=len(v);o=sorted(v);one=fit_gaussian(v);means=[_quantile(o,.25),_quantile(o,.75)];variances=[one.variance,one.variance];weights=[.5,.5];previous=-math.inf;converged=False;iterations=0
    for iteration in range(1,max_iterations+1):
        r0s=[];r1s=[];ll=0.0
        for x in v:
            l0=math.log(max(weights[0],1e-300))+_gaussian_log_pdf(x,means[0],variances[0]);l1=math.log(max(weights[1],1e-300))+_gaussian_log_pdf(x,means[1],variances[1]);den=_logsumexp2(l0,l1);r0=math.exp(l0-den);r0s.append(r0);r1s.append(1-r0);ll+=den
        m0=sum(r0s);m1=sum(r1s)
        if m0<1e-9 or m1<1e-9:return _degenerate_mixture(one,n)
        new_means=[sum(r*x for r,x in zip(r0s,v))/m0,sum(r*x for r,x in zip(r1s,v))/m1]
        new_vars=[max(sum(r*(x-new_means[0])**2 for r,x in zip(r0s,v))/m0,_VARIANCE_FLOOR),max(sum(r*(x-new_means[1])**2 for r,x in zip(r1s,v))/m1,_VARIANCE_FLOOR)]
        means,variances,weights=new_means,new_vars,[m0/n,m1/n];iterations=iteration
        if math.isfinite(previous) and abs(ll-previous)<=tolerance*(1+abs(previous)):converged=True;break
        previous=ll
    final_ll=0.0
    for x in v:
        l0=math.log(max(weights[0],1e-300))+_gaussian_log_pdf(x,means[0],variances[0]);l1=math.log(max(weights[1],1e-300))+_gaussian_log_pdf(x,means[1],variances[1]);final_ll+=_logsumexp2(l0,l1)
    cs=[GaussianComponent(weights[0],means[0],variances[0]),GaussianComponent(weights[1],means[1],variances[1])];cs.sort(key=lambda c:c.mean)
    return GaussianMixtureFit((cs[0],cs[1]),final_ll,5*math.log(n)-2*final_ll,iterations,converged)

def detect_multimodality(samples,policy:MultimodalityPolicy=MultimodalityPolicy()):
    if policy.bic_gain_min<0:raise ValueError("bic_gain_min must be >= 0")
    if not 0<policy.min_component_weight<=.5:raise ValueError("min_component_weight must be in (0, 0.5]")
    if policy.separation_sigma_min<0:raise ValueError("separation_sigma_min must be >= 0")
    one=fit_gaussian(samples);two=fit_gmm2(samples);c1,c2=two.components;bic_gain=one.bic-two.bic;min_weight=min(c1.weight,c2.weight);pooled=math.sqrt(max((c1.variance+c2.variance)/2,_VARIANCE_FLOOR));sep=abs(c2.mean-c1.mean)/pooled
    return MultimodalityDecision(two.converged and bic_gain>=policy.bic_gain_min and min_weight>=policy.min_component_weight and sep>=policy.separation_sigma_min,bic_gain,min_weight,sep,one,two)
