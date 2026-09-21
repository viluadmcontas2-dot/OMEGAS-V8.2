#!/usr/bin/env python3
import argparse, json, os, subprocess, sys, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def run(cmd):
    started=time.time()
    p=subprocess.run(cmd,cwd=ROOT,text=True,capture_output=True)
    return p.returncode, time.time()-started, (p.stdout+"\n"+p.stderr)[-20000:]

ap=argparse.ArgumentParser()
ap.add_argument("--id",required=True)
ap.add_argument("--kind",required=True)
ap.add_argument("--target",required=True)
ap.add_argument("--receipt",required=True)
args=ap.parse_args()
if args.kind=="python":
    cmd=["python3","-B",args.target]
elif args.kind=="node":
    cmd=["node","--test",args.target]
elif args.kind=="jvm":
    cmd=["./gradlew","testDebugUnitTest","--tests",args.target,"--stacktrace"]
elif args.kind=="jvm_node":
    prerequisite = ["./gradlew","testDebugUnitTest","--tests","com.omegas.prohub.LearningLatencyContractTest","--stacktrace"]
    rc1,seconds1,out1=run(prerequisite)
    if rc1 != 0:
        rc,seconds,out=rc1,seconds1,out1
    else:
        rc2,seconds2,out2=run(["node","--test",args.target])
        rc,seconds,out=rc2,seconds1+seconds2,out1+"\n--- JVM->NODE ---\n"+out2
else:
    raise SystemExit(f"unknown kind {args.kind}")
if args.kind!="jvm_node":
    rc,seconds,out=run(cmd)
receipt={
    "id":args.id,
    "kind":args.kind,
    "target":args.target,
    "sha":os.environ.get("GITHUB_SHA",""),
    "status":"PASS" if rc==0 else "RED",
    "exit_code":rc,
    "duration_s":round(seconds,3),
    "tail":out,
}
path=Path(args.receipt); path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps(receipt,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
print(json.dumps({k:v for k,v in receipt.items() if k!="tail"},indent=2,ensure_ascii=False))
sys.exit(0 if rc==0 else 1)
