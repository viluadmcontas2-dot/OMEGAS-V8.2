#!/usr/bin/env python3
import argparse, hashlib, json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEXT_EXT = {".kt",".java",".js",".cjs",".mjs",".py",".html",".css",".md",".json",".yml",".yaml"}

TOKENS = [
    "MUL_ACT","AUTO_CAL_ENABLE","NUM_ATUOMATCH_EXECUTED","PETR_INJ_TBP",
    "MNFLD_PRESS_THD","PETR_MNFLD_PRESS_RV","GAS_MNFLD_PRESS_RV",
    "ACQUIRED_ZONES_PETROL","ACQUIRED_ZONES_GAS","sessionId","visitId",
    "effectiveVisits","effectiveSampleSize","bilinear","interpol","confidence",
    "residual","correction","suggestion","protocolTransaction","Mp48SerialScheduler",
    "stableComparisonError","currentBand","levelsRaw","telemetryAfter"
]

def read_text(path):
    return path.read_text(encoding="utf-8",errors="replace")

def candidate_files():
    roots=[ROOT/"app"/"src"/"main", ROOT/"app"/"src"/"test", ROOT/"tests"]
    for root in roots:
        if not root.exists():
            continue
        for p in root.rglob("*"):
            if p.is_file() and p.suffix.lower() in TEXT_EXT and p.stat().st_size <= 2_000_000:
                yield p

def declarations(text, suffix):
    patterns=[]
    if suffix in {".kt",".java"}:
        patterns=[r"\b(?:data\s+class|class|object|interface|enum\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)",
                  r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\("]
    elif suffix in {".js",".cjs",".mjs"}:
        patterns=[r"\bclass\s+([A-Za-z_$][A-Za-z0-9_$]*)",
                  r"\bfunction\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\(",
                  r"\b(?:const|let|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*(?:async\s*)?\("]
    elif suffix==".py":
        patterns=[r"^\s*(?:class|def)\s+([A-Za-z_][A-Za-z0-9_]*)",]
    out=[]
    for pat in patterns:
        out.extend(re.findall(pat,text,re.M))
    return list(dict.fromkeys(out))[:80]

def crossrefs(names, target):
    names=[n for n in names if len(n)>=5][:12]
    if not names:
        stem=target.stem
        names=[stem] if len(stem)>=5 else []
    rows=[]
    for p in candidate_files():
        if p==target:
            continue
        s=read_text(p)
        count=sum(s.count(n) for n in names)
        if count:
            rows.append({"path":str(p.relative_to(ROOT)),"count":count})
    rows.sort(key=lambda x:(-x["count"],x["path"]))
    return rows[:40]

def source_scan(target):
    p=ROOT/target
    if not p.is_file():
        return {"status":"BROKEN","summary":"target file missing","evidence":[target],"metrics":{}}
    text=read_text(p)
    decl=declarations(text,p.suffix.lower())
    token_counts={t:text.count(t) for t in TOKENS if t in text}
    imports=[line.strip() for line in text.splitlines() if re.match(r"\s*(?:import|require\(|from\s+|export\s+)",line)][:80]
    refs=crossrefs(decl,p)
    science_ui=[]
    if p.suffix.lower() in {".js",".cjs",".mjs"}:
        for t in ("median","MAD","residual","correction","interpol","confidence","suggestion"):
            if re.search(t,text,re.I):
                science_ui.append(t)
    return {
        "status":"INFO",
        "summary":f"source scan {target}: {len(decl)} declarations, {len(refs)} crossref files",
        "evidence":[target],
        "metrics":{
            "sha256":hashlib.sha256(p.read_bytes()).hexdigest(),
            "bytes":p.stat().st_size,
            "lines":len(text.splitlines()),
            "declarations":decl,
            "token_counts":token_counts,
            "imports":imports,
            "top_crossrefs":refs,
            "ui_science_tokens":science_ui
        }
    }

def symbol_scan(symbol):
    hits=[]
    definition_hits=[]
    definition_re=re.compile(r"\b(?:class|object|interface|fun|def|function|const|let|var)\s+"+re.escape(symbol)+r"\b")
    for p in candidate_files():
        s=read_text(p)
        n=s.count(symbol)
        if not n:
            continue
        rel=str(p.relative_to(ROOT))
        hits.append({"path":rel,"count":n})
        if definition_re.search(s):
            definition_hits.append(rel)
    hits.sort(key=lambda x:(-x["count"],x["path"]))
    return {
        "status":"INFO" if hits else "RED",
        "summary":f"symbol {symbol}: {sum(x['count'] for x in hits)} occurrences in {len(hits)} files",
        "evidence":[symbol],
        "metrics":{"definitions":definition_hits[:30],"top_hits":hits[:60],"files":len(hits)}
    }

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--driver",required=True,choices=["source-scan","symbol-scan"])
    ap.add_argument("--target",required=True)
    ap.add_argument("--lane",required=True)
    ap.add_argument("--receipt",required=True)
    args=ap.parse_args()
    try:
        result=source_scan(args.target) if args.driver=="source-scan" else symbol_scan(args.target)
    except Exception as e:
        result={"status":"BROKEN","summary":f"{type(e).__name__}: {e}","evidence":[],"metrics":{}}
    result.update({"lane":args.lane,"driver":args.driver,"target":args.target})
    out=ROOT/args.receipt
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    if result["status"]=="BROKEN":
        raise SystemExit(2)

if __name__=="__main__":
    main()
