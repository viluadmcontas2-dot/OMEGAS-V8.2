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
    "method":["delphi-method","ghidra-method"],
    "field":["delphi-field","raw-field-name"],
    "field-use":["ghidra-field-use","capstone-field-use"],
    "event":["delphi-event","ghidra-event"],
    "action":["delphi-action","ghidra-action"],
    "serial":["delphi-serial","portmon-object"],
    "visual":["delphi-visual","ghidra-visual"],
}
REQUIRED={
    "method":{"delphi-method","ghidra-method"},
    "field":{"delphi-field","raw-field-name"},
    "field-use":{"ghidra-field-use","capstone-field-use"},
    "event":{"delphi-event","ghidra-event"},
    "action":{"delphi-action","ghidra-action"},
    "serial":{"delphi-serial","portmon-object"},
    "visual":{"delphi-visual","ghidra-visual"},
}

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
    if kind not in DRIVER_CHOICES: return None
    k=key(kind,target)
    t=state["targets"].setdefault(k,{"kind":kind,"target":target,"drivers":{},"status":"ESCALATE","new_targets":[],"origins":[]})
    if origin and origin not in t["origins"]:t["origins"].append(origin)
    if metadata and "metadata" not in t:t["metadata"]=metadata
    return t

def recompute_status(t):
    statuses=t["drivers"]
    if any(x["status"]=="BROKEN" for x in statuses.values()):return "BROKEN"
    proven={d for d,x in statuses.items() if x["status"]=="PROVEN"}
    if t["kind"]=="function":
        return "PROVEN" if len(proven & {"ghidra-fn","objdump-fn","capstone-fn"})>=2 else "ESCALATE"
    if t["kind"]=="symbol":
        return "PROVEN" if len(proven & {"raw-symbol","undelphi-symbol","ghidra-string"})>=2 else "ESCALATE"
    if t["kind"]=="frame":
        return "PROVEN" if "portmon-frame" in proven else "ESCALATE"
    req=REQUIRED.get(t["kind"])
    if req is not None:
        return "PROVEN" if req.issubset(proven) else "ESCALATE"
    return "ESCALATE"

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--wave",type=int,required=True);ap.add_argument("--receipts",type=Path,required=True);ap.add_argument("--indices",type=Path,required=True);ap.add_argument("--previous",type=Path);ap.add_argument("--state",type=Path,required=True);ap.add_argument("--matrix",type=Path,required=True)
    a=ap.parse_args()
    if a.previous and a.previous.is_file(): state=json.loads(a.previous.read_text(encoding="utf-8"))
    else: state={"schema":"omegas.atlas.battle-state.v2","targets":{},"attempt_fingerprints":[]}
    state["schema"]="omegas.atlas.battle-state.v2";state["wave"]=a.wave
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
            "status":r["status"],
            "summary":r.get("summary",""),
            "claims":r.get("claims",[]),
            "evidence":r.get("evidence",[]),
            "contradictions":r.get("contradictions",[]),
            "attempt_fingerprint":fp,
        }
        for n in r.get("new_targets",[]):
            if not isinstance(n,dict) or not n.get("kind") or not n.get("target"):continue
            nk=key(n["kind"],n["target"])
            if nk not in t["new_targets"]:t["new_targets"].append(nk)
            ensure_target(state,n["kind"],n["target"],origin=k,metadata={k:v for k,v in n.items() if k not in {"kind","target"}})
    state["attempt_fingerprints"]=sorted(fps)

    for t in state["targets"].values():t["status"]=recompute_status(t)

    sem_payload=semantic_targets.load(a.indices/"ghidra/autocal-semantics.json")
    sem_catalog=semantic_targets.build(sem_payload)
    sem_map={(x["kind"],x["target"]):x for x in sem_catalog}

    lanes=[]
    def schedule(t,driver):
        if len(lanes)>=256:return False
        if driver in t["drivers"]:return False
        lanes.append({"id":lane_id(a.wave+1,driver,t["kind"],t["target"]),"kind":t["kind"],"target":t["target"],"driver":driver});return True

    # 1) Existing unresolved/discovered targets get a genuinely different method first.
    for _,t in sorted(state["targets"].items()):
        if t["status"]!="ESCALATE":continue
        for d in DRIVER_CHOICES.get(t["kind"],[]):
            if d not in t["drivers"]:
                schedule(t,d);break

    # 2) Admit the semantic model itself as battle targets. This turns DFM/RTTI facts into closure work.
    for item in sem_catalog:
        if len(lanes)>=254:break
        k=key(item["kind"],item["target"])
        if k in state["targets"]:continue
        t=ensure_target(state,item["kind"],item["target"],origin="semantic-index",metadata=item["meta"])
        for d in DRIVER_CHOICES[item["kind"]]:
            if len(lanes)>=254:break
            schedule(t,d)

    # 3) Expand the entire exported native graph.
    all_funcs=funcs(a.indices/"ghidra/functions.tsv")
    all_funcs.sort(key=lambda x:(x["distance"],x["entry"]))
    for f in all_funcs:
        if len(lanes)>=254:break
        k=key("function",f["entry"])
        if k in state["targets"]:continue
        t=ensure_target(state,"function",f["entry"],origin="ghidra-graph",metadata={"distance":f["distance"]})
        schedule(t,"ghidra-fn");schedule(t,"objdump-fn")

    # 4) Expand configured symbols and Portmon candidates.
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

    ghidra_meta=json.loads((a.indices/"ghidra/meta.json").read_text(encoding="utf-8"))
    for t in state["targets"].values():t["status"]=recompute_status(t)
    counts=collections.Counter(t["status"] for t in state["targets"].values())
    graph_entries={f["entry"].lower() for f in all_funcs}
    seen_entries={t["target"].lower() for t in state["targets"].values() if t["kind"]=="function"}
    unseen_graph=len(graph_entries-seen_entries)
    stalled=[
        k for k,t in state["targets"].items()
        if t["status"]=="ESCALATE"
        and DRIVER_CHOICES.get(t["kind"])
        and all(d in t["drivers"] for d in DRIVER_CHOICES[t["kind"]])
    ]

    semantic_keys={key(x["kind"],x["target"]) for x in sem_catalog}
    semantic_missing=sorted(semantic_keys-set(state["targets"]))
    semantic_open=sorted(k for k in semantic_keys if k in state["targets"] and state["targets"][k]["status"]!="PROVEN")
    unresolved_bindings=int(sem_payload.get("counts",{}).get("unresolved_event_bindings",0))+int(sem_payload.get("counts",{}).get("unresolved_action_bindings",0))
    semantic_closure=(not semantic_missing and not semantic_open and unresolved_bindings==0)

    broken=sum(1 for t in state["targets"].values() if t["status"]=="BROKEN")
    state["coverage"]={
        "graph_functions_total":len(graph_entries),
        "graph_functions_unseen":unseen_graph,
        "targets_total":len(state["targets"]),
        "status_counts":dict(counts),
        "broken":broken,
        "stalled_targets":len(stalled),
        "function_cap":ghidra_meta.get("function_cap"),
        "function_cap_hit":bool(ghidra_meta.get("function_cap_hit",False)),
        "decompile_cap":ghidra_meta.get("decompile_cap"),
        "decompile_cap_hit":bool(ghidra_meta.get("decompile_cap_hit",False)),
        "semantic_targets_total":len(semantic_keys),
        "semantic_targets_missing":len(semantic_missing),
        "semantic_targets_open":len(semantic_open),
        "unresolved_semantic_bindings":unresolved_bindings,
        "semantic_index_counts":sem_payload.get("counts",{}),
    }
    state["semantic_closure"]=semantic_closure
    state["semantic_missing_keys"]=semantic_missing[:200]
    state["semantic_open_keys"]=semantic_open[:200]
    state["next_lane_count"]=len(lanes)
    state["has_next"]=bool(lanes)
    state["stalled_keys"]=stalled[:200]

    a.state.parent.mkdir(parents=True,exist_ok=True);a.matrix.parent.mkdir(parents=True,exist_ok=True)
    a.state.write_text(json.dumps(state,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    a.matrix.write_text(json.dumps({"include":lanes},separators=(",",":"))+"\n",encoding="utf-8")
    print(json.dumps({"wave":a.wave,"receipts":len(rows),"next_lanes":len(lanes),"semantic_closure":semantic_closure,"coverage":state["coverage"]},indent=2))
    if broken:raise SystemExit("BROKEN battle receipt present")

if __name__=="__main__":main()
