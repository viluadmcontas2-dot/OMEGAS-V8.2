#!/usr/bin/env python3
from __future__ import annotations
import argparse, collections, hashlib, json, re
from pathlib import Path

ALLOWED={"PROVEN","REFUTED","ESCALATE","BROKEN"}

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

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--wave",type=int,required=True);ap.add_argument("--receipts",type=Path,required=True);ap.add_argument("--indices",type=Path,required=True);ap.add_argument("--previous",type=Path);ap.add_argument("--state",type=Path,required=True);ap.add_argument("--matrix",type=Path,required=True)
    a=ap.parse_args()
    if a.previous and a.previous.is_file(): state=json.loads(a.previous.read_text(encoding="utf-8"))
    else: state={"schema":"omegas.atlas.battle-state.v1","targets":{},"attempt_fingerprints":[]}
    state["wave"]=a.wave
    rows=receipts(a.receipts)
    if not rows:raise SystemExit("no battle receipts")
    fps=set(state.get("attempt_fingerprints",[]))
    for r in rows:
        if r["status"] not in ALLOWED:raise SystemExit("forbidden status")
        fp=r["attempt_fingerprint"]
        if fp in fps:raise SystemExit("duplicate attempt fingerprint "+fp)
        fps.add(fp)
        k=key(r["kind"],r["target_id"])
        t=state["targets"].setdefault(k,{"kind":r["kind"],"target":r["target_id"],"drivers":{},"status":"ESCALATE","new_targets":[]})
        t["drivers"][r["driver"]]={"status":r["status"],"summary":r.get("summary","")}
        for n in r.get("new_targets",[]):
            if n not in t["new_targets"]:t["new_targets"].append(n)
    state["attempt_fingerprints"]=sorted(fps)

    broken=0
    for t in state["targets"].values():
        statuses=t["drivers"]
        if any(x["status"]=="BROKEN" for x in statuses.values()):
            t["status"]="BROKEN";broken+=1;continue
        proven={d for d,x in statuses.items() if x["status"]=="PROVEN"}
        if t["kind"]=="function":
            t["status"]="PROVEN" if len(proven & {"ghidra-fn","objdump-fn","capstone-fn"})>=2 else "ESCALATE"
        elif t["kind"]=="symbol":
            t["status"]="PROVEN" if len(proven & {"raw-symbol","undelphi-symbol","ghidra-string"})>=2 else "ESCALATE"
        elif t["kind"]=="frame":
            t["status"]="PROVEN" if "portmon-frame" in proven else "ESCALATE"
        else:t["status"]="ESCALATE"

    lanes=[]
    def schedule(t,driver):
        if len(lanes)>=256:return False
        if driver in t["drivers"]:return False
        lanes.append({"id":lane_id(a.wave+1,driver,t["kind"],t["target"]),"kind":t["kind"],"target":t["target"],"driver":driver});return True

    # Existing unresolved targets get a genuinely different method first.
    for k,t in sorted(state["targets"].items()):
        if t["status"]!="ESCALATE":continue
        choices={"function":["ghidra-fn","objdump-fn","capstone-fn"],"symbol":["raw-symbol","undelphi-symbol","ghidra-string"],"frame":["portmon-frame","binary-frame"]}.get(t["kind"],[])
        for d in choices:
            if d not in t["drivers"]:
                schedule(t,d);break

    # Expand the entire exported AutoCal native graph deterministically.
    all_funcs=funcs(a.indices/"ghidra/functions.tsv")
    all_funcs.sort(key=lambda x:(x["distance"],x["entry"]))
    for f in all_funcs:
        if len(lanes)>=254:break
        k=key("function",f["entry"])
        if k in state["targets"]:continue
        t={"kind":"function","target":f["entry"],"drivers":{},"status":"ESCALATE","new_targets":[]}
        state["targets"][k]=t
        schedule(t,"ghidra-fn");schedule(t,"objdump-fn")

    # Expand all configured symbols not yet seen.
    seed_path=Path(__file__).resolve().parents[2]/"atlas/manifests/autocal-seeds.json"
    for s in json.loads(seed_path.read_text(encoding="utf-8"))["symbols"]:
        if len(lanes)>=254:break
        k=key("symbol",s["term"])
        if k in state["targets"]:continue
        t={"kind":"symbol","target":s["term"],"drivers":{},"status":"ESCALATE","new_targets":[]};state["targets"][k]=t
        schedule(t,"raw-symbol");schedule(t,"undelphi-symbol")

    # Expand every materially enriched Portmon frame, bounded by corpus-derived candidate set.
    port=json.loads((a.indices/"portmon/portmon-index.json").read_text(encoding="utf-8"))
    for fr in port.get("candidates",[]):
        if len(lanes)>=254:break
        k=key("frame",fr["frame"])
        if k in state["targets"]:continue
        t={"kind":"frame","target":fr["frame"],"drivers":{},"status":"ESCALATE","new_targets":[]};state["targets"][k]=t
        schedule(t,"portmon-frame");schedule(t,"binary-frame")

    # Recompute coverage after scheduling: scheduled targets remain ESCALATE by design.
    ghidra_meta=json.loads((a.indices/"ghidra/meta.json").read_text(encoding="utf-8"))
    semantics_path=a.indices/"ghidra/autocal-semantics.json"
    semantics=json.loads(semantics_path.read_text(encoding="utf-8")) if semantics_path.is_file() else {"counts":{}}
    counts=collections.Counter(t["status"] for t in state["targets"].values())
    graph_entries={f["entry"].lower() for f in all_funcs}
    seen_entries={t["target"].lower() for t in state["targets"].values() if t["kind"]=="function"}
    unseen_graph=len(graph_entries-seen_entries)
    stalled=[k for k,t in state["targets"].items() if t["status"]=="ESCALATE" and all(d in t["drivers"] for d in ({"function":["ghidra-fn","objdump-fn","capstone-fn"],"symbol":["raw-symbol","undelphi-symbol","ghidra-string"],"frame":["portmon-frame","binary-frame"]}.get(t["kind"],[])))]
    state["coverage"]={"graph_functions_total":len(graph_entries),"graph_functions_unseen":unseen_graph,"targets_total":len(state["targets"]),"status_counts":dict(counts),"broken":broken,"stalled_targets":len(stalled),"function_cap":ghidra_meta.get("function_cap"),"function_cap_hit":bool(ghidra_meta.get("function_cap_hit",False)),"decompile_cap":ghidra_meta.get("decompile_cap"),"decompile_cap_hit":bool(ghidra_meta.get("decompile_cap_hit",False)),"semantic_index_counts":semantics.get("counts",{})}
    state["semantic_closure"]=False
    state["next_lane_count"]=len(lanes)
    state["has_next"]=bool(lanes)
    state["stalled_keys"]=stalled[:200]
    a.state.parent.mkdir(parents=True,exist_ok=True);a.matrix.parent.mkdir(parents=True,exist_ok=True)
    a.state.write_text(json.dumps(state,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    a.matrix.write_text(json.dumps({"include":lanes},separators=(",",":"))+"\n",encoding="utf-8")
    print(json.dumps({"wave":a.wave,"receipts":len(rows),"next_lanes":len(lanes),"coverage":state["coverage"]},indent=2))
    if broken:raise SystemExit("BROKEN battle receipt present")

if __name__=="__main__":main()
