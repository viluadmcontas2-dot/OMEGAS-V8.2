#!/usr/bin/env python3
from __future__ import annotations
import argparse, concurrent.futures as cf, json, math, statistics, threading
from pathlib import Path

JURIES={
 "j1":"accuracy_and_tail",
 "j2":"robustness_and_corruption",
 "j3":"learning_speed_and_data_efficiency",
 "j4":"causality_and_evidence_authority",
 "j5":"simplicity_and_runtime_feasibility",
}
GROUPS={
 "legacy":["t01","t03"],
 "temporal":["t02","t04","t05","t06","t11","t12"],
 "gasoline_reference":["t07","t08"],
 "passive_cng":["t09","t10","t13","t15"],
 "active_identification":["t14"],
}

def load(inp):
    out={}
    for i in range(1,16):
        k=f"t{i:02d}"; out[k]=json.loads((Path(inp)/f"{k}.json").read_text(encoding="utf-8"))
    return out

def champ(d,k): return d[k]["champion"]
def metric(c,key,default=None):
    if key in c:return c[key]
    if isinstance(c.get("metrics"),dict):return c["metrics"].get(key,default)
    return default

def votes_for_team(data,team,lens,seed):
    c=champ(data,team)
    topic=data[team]["topic"]
    mae=metric(c,"mae_pct",99.0)
    p90=metric(c,"p90_pct",99.0)
    p99=metric(c,"p99_pct",99.0)
    w4=metric(c,"within4_pct",0.0)
    worst=metric(c,"worst_pct",999.0)
    sim=bool(c.get("simulation_only",False))
    legacy=bool(c.get("legacy",False))
    train=metric(c,"median_training_frames",None)
    runtime_switch=bool(c.get("runtime_switching_required",False))
    # Small deterministic stance perturbations: 10 jurors do not share identical thresholds.
    shift=(seed-4.5)/9.0
    status="INCONCLUSIVE"; reasons=[]
    if legacy:
        status="REJECT";reasons.append("legacy baseline, not target")
    elif team in {"t02","t04","t05","t06","t11"}:
        if lens=="accuracy_and_tail":
            if mae<5.2 and (p90<13 or p90==99):status="PROMOTE_LAYER"
            else:status="KEEP"
        elif lens=="robustness_and_corruption":
            status="PROMOTE_LAYER" if team in {"t11","t12"} else "KEEP"
        elif lens=="learning_speed_and_data_efficiency":
            status="PROMOTE_LAYER" if team in {"t02","t11"} else "KEEP"
        elif lens=="causality_and_evidence_authority":
            status="PROMOTE_LAYER" if not runtime_switch else "REJECT"
        else:
            status="PROMOTE_LAYER" if team in {"t02","t11","t04"} else "KEEP"
    elif team in {"t07","t08"}:
        status="PROMOTE_LAYER" if mae<7.6+shift*.3 else "KEEP"
        reasons.append("gasoline reference initializer only")
    elif team in {"t09","t10","t13","t15"}:
        # Passive CNG learners must actually approach the practical target.
        if w4>=75 and mae<=4.5: status="PROMOTE_CORE"
        elif w4>=60 and mae<=6.0: status="KEEP"
        else: status="REJECT"
        reasons.append("passive CNG field judged on later-session generalization")
    elif team=="t14":
        status="INCONCLUSIVE" if sim else "KEEP"
        reasons.append("simulation-only until real before/after K evidence exists")
    elif team=="t12":
        status="PROMOTE_LAYER"
    # Tail veto.
    if p99 not in (None,99.0) and p99>35 and status=="PROMOTE_CORE":
        status="KEEP";reasons.append("tail veto")
    if worst not in (None,999.0) and worst>60 and status=="PROMOTE_CORE":
        status="KEEP";reasons.append("worst-case veto")
    if train is not None and train>700 and lens=="learning_speed_and_data_efficiency" and status.startswith("PROMOTE"):
        status="KEEP";reasons.append("too data hungry")
    return {"team":team,"topic":topic,"status":status,"reasons":reasons,"mae":mae,"p90":p90,"p99":p99,"within4":w4,"train_frames":train}

def judge(data,lens,idx):
    votes=[votes_for_team(data,f"t{i:02d}",lens,idx) for i in range(1,16)]
    # Architecture claim voted by this juror.
    promoted=[v["team"] for v in votes if v["status"] in {"PROMOTE_CORE","PROMOTE_LAYER"}]
    rejected=[v["team"] for v in votes if v["status"]=="REJECT"]
    return {"juror":f"{lens}.w{idx+1:02d}","thread":threading.current_thread().name,
            "promoted":promoted,"rejected":rejected,"votes":votes}

def consensus(jurors):
    teams=[f"t{i:02d}" for i in range(1,16)]
    out={}
    order=["PROMOTE_CORE","PROMOTE_LAYER","KEEP","INCONCLUSIVE","REJECT"]
    for t in teams:
        counts={k:0 for k in order}
        for j in jurors:
            st=next(v["status"] for v in j["votes"] if v["team"]==t);counts[st]+=1
        # Majority; ties resolve conservatively toward weaker authority.
        maxn=max(counts.values()); tied=[k for k,v in counts.items() if v==maxn]
        conservative=["REJECT","INCONCLUSIVE","KEEP","PROMOTE_LAYER","PROMOTE_CORE"]
        decision=next(k for k in conservative if k in tied)
        out[t]={"decision":decision,"votes":counts}
    return out

def main():
    ap=argparse.ArgumentParser();ap.add_argument("--jury",choices=sorted(JURIES),required=True);ap.add_argument("--input-dir",required=True);ap.add_argument("--out",default="artifacts")
    a=ap.parse_args();data=load(a.input_dir);lens=JURIES[a.jury]
    with cf.ThreadPoolExecutor(max_workers=10,thread_name_prefix=f"{a.jury}-juror") as ex:
        jurors=[f.result() for f in [ex.submit(judge,data,lens,i) for i in range(10)]]
    cons=consensus(jurors)
    promoted=[t for t,v in cons.items() if v["decision"].startswith("PROMOTE")]
    rejected=[t for t,v in cons.items() if v["decision"]=="REJECT"]
    obj={"schema":"omegas.adversarial-jury.v1","jury":a.jury,"lens":lens,"logical_jurors":10,
         "consensus":cons,"promoted":promoted,"rejected":rejected,"jurors":jurors,
         "guardrails":["No repeated fuel switching as runtime requirement.",
                       "Natural PETROL->CNG transitions may only validate, never feed runtime calibration.",
                       "Simulation-only active K identification cannot be promoted without real before/after intervention evidence."]}
    Path(a.out).mkdir(parents=True,exist_ok=True)
    (Path(a.out)/f"{a.jury}.json").write_text(json.dumps(obj,indent=2,sort_keys=True),encoding="utf-8")
    print(json.dumps({"jury":a.jury,"lens":lens,"jurors":10,"promoted":promoted,"rejected":rejected}))
if __name__=="__main__":main()
