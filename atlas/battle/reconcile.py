#!/usr/bin/env python3
from __future__ import annotations
import argparse, collections, hashlib, json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import semantic_targets

ALLOWED={"PROVEN","REFUTED","ESCALATE","BROKEN"}
DRIVER_CHOICES={
    "function":["ghidra-fn","objdump-fn","capstone-fn"],
    "symbol":["raw-symbol","undelphi-symbol","ghidra-string"],
    "frame":["portmon-frame","binary-frame"],
    "method":["delphi-method","capstone-method","ghidra-method"],
    "field":["delphi-field","raw-field-name"],
    "field-use":["ghidra-field-use","capstone-field-use","owner-field-use"],
    "event":["delphi-event","ghidra-event"],
    "action":["delphi-action","ghidra-action"],
    "serial":["delphi-serial","pe-serial-resource","portmon-object"],
    "visual":["delphi-visual","pe-visual-resource","ghidra-visual"],
}
PRIMARY_DRIVERS={
    "method":["delphi-method","capstone-method"],
    "field":["delphi-field","raw-field-name"],
    "field-use":["ghidra-field-use","capstone-field-use"],
    "event":["delphi-event"],
    "action":["delphi-action"],
    "serial":["delphi-serial","pe-serial-resource"],
    "visual":["delphi-visual","pe-visual-resource"],
}
PRIORITY={"method":0,"serial":1,"visual":2,"event":3,"action":4,"field-use":5,"field":6,"function":7,"symbol":8,"frame":9}

def receipts(root):
    out=[]
    for p in sorted(Path(root).rglob("*.json")):
        try:r=json.loads(p.read_text(encoding="utf-8"))
        except Exception:continue
        if isinstance(r,dict) and r.get("schema")=="omegas.atlas.battle-receipt.v1":out.append(r)
    return out

def funcs(path):
    out=[]
    lines=path.read_text(encoding="utf-8",errors="replace").splitlines()
    for line in lines[1:]:
        p=line.split("\t")
        if len(p)>=8:out.append({"entry":p[0],"distance":int(p[1]),"boundary":p[5]=="true"})
    return out

def key(kind,target):return kind+"|"+target

def lane_id(wave,driver,kind,target):
    h=hashlib.sha1(key(kind,target).encode()).hexdigest()[:10]
    return f"w{wave}-{driver}-{h}"

def ensure_target(state,kind,target,origin=None,metadata=None):
    if kind not in DRIVER_CHOICES:return None
    k=key(kind,target)
    t=state["targets"].setdefault(k,{"kind":kind,"target":target,"drivers":{},"status":"ESCALATE","new_targets":[],"origins":[]})
    if origin and origin not in t["origins"]:t["origins"].append(origin)
    if metadata and "metadata" not in t:t["metadata"]=metadata
    return t

def driver_proven(t,name):
    return t.get("drivers",{}).get(name,{}).get("status")=="PROVEN"

def dep_targets(state,t,kind=None):
    out=[]
    for dep_key in t.get("new_targets",[]):
        dep=state["targets"].get(dep_key)
        if dep is not None and (kind is None or dep.get("kind")==kind):out.append(dep)
    return out

def dep_proven(state,t,kind):
    return any(x.get("status")=="PROVEN" for x in dep_targets(state,t,kind))

def evidence_functions(t,driver):
    out=set()
    d=t.get("drivers",{}).get(driver,{})
    for ev in d.get("evidence",[]):
        for hit in ev.get("hits",[]) if isinstance(ev,dict) else []:
            fn=str(hit.get("function","")).lower()
            if fn:out.add(fn)
    return out

def field_overlap(t):
    return evidence_functions(t,"ghidra-field-use") & evidence_functions(t,"capstone-field-use")

def proven_method_origins(state,t):
    out=[]
    for origin in t.get("origins",[]):
        if not origin.startswith("method|"):
            continue
        dep=state.get("targets",{}).get(origin)
        if dep is not None and dep.get("status")=="PROVEN":
            out.append(origin)
    return out

def field_consumer_functions(t):
    out=set(field_overlap(t))
    out |= evidence_functions(t,"owner-field-use")
    return out

def target_status(state,t):
    if any(x.get("status")=="BROKEN" for x in t.get("drivers",{}).values()):return "BROKEN"
    kind=t["kind"]
    if kind=="function":
        if proven_method_origins(state,t):
            return "PROVEN"
        p={d for d in ("ghidra-fn","objdump-fn","capstone-fn") if driver_proven(t,d)}
        return "PROVEN" if len(p)>=2 else "ESCALATE"
    if kind=="symbol":
        p={d for d in ("raw-symbol","undelphi-symbol","ghidra-string") if driver_proven(t,d)}
        return "PROVEN" if len(p)>=2 else "ESCALATE"
    if kind=="frame":
        return "PROVEN" if driver_proven(t,"portmon-frame") else "ESCALATE"
    if kind=="method":
        return "PROVEN" if driver_proven(t,"delphi-method") and (driver_proven(t,"capstone-method") or driver_proven(t,"ghidra-method")) else "ESCALATE"
    if kind=="field":
        return "PROVEN" if driver_proven(t,"delphi-field") and driver_proven(t,"raw-field-name") else "ESCALATE"
    if kind=="field-use":
        overlap = driver_proven(t,"ghidra-field-use") and driver_proven(t,"capstone-field-use") and bool(field_overlap(t))
        owner = driver_proven(t,"owner-field-use")
        return "PROVEN" if overlap or owner else "ESCALATE"
    if kind=="event":
        return "PROVEN" if driver_proven(t,"delphi-event") and (driver_proven(t,"ghidra-event") or dep_proven(state,t,"method")) else "ESCALATE"
    if kind=="action":
        return "PROVEN" if driver_proven(t,"delphi-action") and (driver_proven(t,"ghidra-action") or dep_proven(state,t,"event")) else "ESCALATE"
    if kind=="serial":
        return "PROVEN" if driver_proven(t,"delphi-serial") and (driver_proven(t,"pe-serial-resource") or driver_proven(t,"portmon-object")) else "ESCALATE"
    if kind=="visual":
        return "PROVEN" if driver_proven(t,"delphi-visual") and driver_proven(t,"pe-visual-resource") else "ESCALATE"
    return "ESCALATE"

def recompute_all(state):
    for _ in range(12):
        changed=False
        for t in state["targets"].values():
            aliases=proven_method_origins(state,t) if t.get("kind")=="function" else []
            if aliases:
                t["resolution"]={"kind":"published-method-alias","origins":aliases}
            elif t.get("resolution",{}).get("kind")=="published-method-alias":
                t.pop("resolution",None)
            s=target_status(state,t)
            if t.get("status")!=s:
                t["status"]=s;changed=True
        if not changed:break

def promote_confirmed_field_consumers(state):
    added=0
    for k,t in list(state["targets"].items()):
        if t.get("kind")!="field-use" or t.get("status")!="PROVEN":continue
        for fn in sorted(field_consumer_functions(t)):
            before=key("function",fn) in state["targets"]
            ensure_target(state,"function",fn,origin=k,metadata={"reason":"cross-engine-field-use"})
            if not before:added+=1
    return added

def waiting_on_dependency(state,t):
    deps=dep_targets(state,t)
    return any(x.get("status")=="ESCALATE" for x in deps)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--wave",type=int,required=True);ap.add_argument("--receipts",type=Path,required=True);ap.add_argument("--indices",type=Path,required=True);ap.add_argument("--previous",type=Path);ap.add_argument("--state",type=Path,required=True);ap.add_argument("--matrix",type=Path,required=True)
    a=ap.parse_args()
    if a.previous and a.previous.is_file():state=json.loads(a.previous.read_text(encoding="utf-8"))
    else:state={"schema":"omegas.atlas.battle-state.v3","targets":{},"attempt_fingerprints":[]}
    state["schema"]="omegas.atlas.battle-state.v3";state["wave"]=a.wave
    rows=receipts(a.receipts)
    if not rows:raise SystemExit("no battle receipts")
    fps=set(state.get("attempt_fingerprints",[]))

    for r in rows:
        if r["status"] not in ALLOWED:raise SystemExit("forbidden status")
        fp=r["attempt_fingerprint"]
        if fp in fps:raise SystemExit("duplicate attempt fingerprint "+fp)
        fps.add(fp)
        k=key(r["kind"],r["target_id"])
        t=ensure_target(state,r["kind"],r["target_id"],origin="receipt")
        if t is None:raise SystemExit("receipt uses unsupported kind "+r["kind"])
        t["drivers"][r["driver"]]={
            "status":r["status"],"summary":r.get("summary",""),"claims":r.get("claims",[]),
            "evidence":r.get("evidence",[]),"contradictions":r.get("contradictions",[]),
            "attempt_fingerprint":fp,
        }
        for n in r.get("new_targets",[]):
            if not isinstance(n,dict) or not n.get("kind") or not n.get("target"):continue
            nk=key(n["kind"],n["target"])
            if nk not in t["new_targets"]:t["new_targets"].append(nk)
            ensure_target(state,n["kind"],n["target"],origin=k,metadata={x:y for x,y in n.items() if x not in {"kind","target"}})
    state["attempt_fingerprints"]=sorted(fps)

    recompute_all(state)
    promoted=promote_confirmed_field_consumers(state)
    if promoted:recompute_all(state)

    sem_payload=semantic_targets.load(a.indices/"ghidra/autocal-semantics.json")
    sem_catalog=semantic_targets.build(sem_payload)

    lanes=[]
    def schedule(t,driver):
        if len(lanes)>=256:return False
        if driver in t["drivers"]:return False
        lanes.append({"id":lane_id(a.wave+1,driver,t["kind"],t["target"]),"kind":t["kind"],"target":t["target"],"driver":driver})
        return True

    # 1) Resolve existing semantic obligations before exploring mechanical dependencies.
    unresolved=sorted(
        (t for t in state["targets"].values() if t["status"]=="ESCALATE"),
        key=lambda t:(PRIORITY.get(t["kind"],99),t["target"])
    )
    for t in unresolved:
        if len(lanes)>=254:break
        # Parent events/actions wait for their explicit method/event dependency instead of spawning noise.
        if t["kind"] in {"event","action"} and waiting_on_dependency(state,t):
            continue
        for d in DRIVER_CHOICES.get(t["kind"],[]):
            if d not in t["drivers"]:
                schedule(t,d);break

    # 2) Admit the remaining semantic model in semantic priority order.
    for item in sem_catalog:
        if len(lanes)>=254:break
        k=key(item["kind"],item["target"])
        if k in state["targets"]:continue
        t=ensure_target(state,item["kind"],item["target"],origin="semantic-index",metadata=item["meta"])
        for d in PRIMARY_DRIVERS.get(item["kind"],[]):
            if len(lanes)>=254:break
            schedule(t,d)

    # 3) Native graph remains a catalog. Only cross-engine semantic evidence promotes functions.
    all_funcs=funcs(a.indices/"ghidra/functions.tsv")
    all_funcs.sort(key=lambda x:(x["distance"],x["entry"]))

    # 4) Use spare capacity for configured symbol/protocol challengers.
    seed_path=Path(__file__).resolve().parents[2]/"atlas/manifests/autocal-seeds.json"
    for s in json.loads(seed_path.read_text(encoding="utf-8"))["symbols"]:
        if len(lanes)>=254:break
        k=key("symbol",s["term"])
        if k in state["targets"]:continue
        t=ensure_target(state,"symbol",s["term"],origin="seed-manifest")
        schedule(t,"raw-symbol");schedule(t,"undelphi-symbol")
    port=json.loads((a.indices/"portmon/portmon-index.json").read_text(encoding="utf-8"))
    for fr in port.get("candidates",[]):
        if len(lanes)>=254:break
        k=key("frame",fr["frame"])
        if k in state["targets"]:continue
        t=ensure_target(state,"frame",fr["frame"],origin="portmon-differential")
        schedule(t,"portmon-frame");schedule(t,"binary-frame")

    recompute_all(state)
    ghidra_meta=json.loads((a.indices/"ghidra/meta.json").read_text(encoding="utf-8"))
    counts=collections.Counter(t["status"] for t in state["targets"].values())
    graph_entries={f["entry"].lower() for f in all_funcs}
    seen_entries={t["target"].lower() for t in state["targets"].values() if t["kind"]=="function"}
    unseen_graph=len(graph_entries-seen_entries)

    stalled=[]
    for k,t in state["targets"].items():
        if t["status"]!="ESCALATE":continue
        choices=DRIVER_CHOICES.get(t["kind"],[])
        if choices and all(d in t["drivers"] for d in choices) and not waiting_on_dependency(state,t):
            stalled.append(k)

    semantic_keys={key(x["kind"],x["target"]) for x in sem_catalog}
    semantic_missing=sorted(semantic_keys-set(state["targets"]))
    semantic_open=sorted(k for k in semantic_keys if k in state["targets"] and state["targets"][k]["status"]!="PROVEN")
    unresolved_bindings=int(sem_payload.get("counts",{}).get("unresolved_event_bindings",0))+int(sem_payload.get("counts",{}).get("unresolved_action_bindings",0))
    semantic_closure=(not semantic_missing and not semantic_open and unresolved_bindings==0)

    broken=sum(1 for t in state["targets"].values() if t["status"]=="BROKEN")
    state["coverage"]={
        "graph_functions_total":len(graph_entries),"graph_functions_unseen":unseen_graph,
        "targets_total":len(state["targets"]),"status_counts":dict(counts),"broken":broken,
        "stalled_targets":len(stalled),"confirmed_field_consumers_promoted":promoted,
        "function_cap":ghidra_meta.get("function_cap"),"function_cap_hit":bool(ghidra_meta.get("function_cap_hit",False)),
        "decompile_cap":ghidra_meta.get("decompile_cap"),"decompile_cap_hit":bool(ghidra_meta.get("decompile_cap_hit",False)),
        "semantic_targets_total":len(semantic_keys),"semantic_targets_missing":len(semantic_missing),
        "semantic_targets_open":len(semantic_open),"unresolved_semantic_bindings":unresolved_bindings,
        "semantic_index_counts":sem_payload.get("counts",{}),
    }
    state["semantic_closure"]=semantic_closure
    state["semantic_missing_keys"]=semantic_missing[:200]
    state["semantic_open_keys"]=semantic_open[:200]
    state["next_lane_count"]=len(lanes);state["has_next"]=bool(lanes);state["stalled_keys"]=stalled[:200]

    a.state.parent.mkdir(parents=True,exist_ok=True);a.matrix.parent.mkdir(parents=True,exist_ok=True)
    a.state.write_text(json.dumps(state,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    a.matrix.write_text(json.dumps({"include":lanes},separators=(",",":"))+"\n",encoding="utf-8")
    print(json.dumps({"wave":a.wave,"receipts":len(rows),"next_lanes":len(lanes),"semantic_closure":semantic_closure,"coverage":state["coverage"]},indent=2))
    if broken:raise SystemExit("BROKEN battle receipt present")
    if stalled and not lanes:raise SystemExit(f"challenger exhaustion on {len(stalled)} semantic targets")

if __name__=="__main__":main()
