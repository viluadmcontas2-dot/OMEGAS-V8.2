#!/usr/bin/env python3
import argparse, json, os, subprocess, sys, time
from pathlib import Path
from global_reality_status import classify_failure

ROOT=Path(__file__).resolve().parents[2]

def run(cmd):
    started=time.time()
    p=subprocess.run(cmd,cwd=ROOT,text=True,capture_output=True)
    return p.returncode, time.time()-started, (p.stdout+"\n"+p.stderr)[-20000:]

def run_with_broken_retry(cmd):
    rc,seconds,out=run(cmd)
    status=classify_failure(rc,out)
    attempts=1
    retry_recovered=False
    if status=="BROKEN":
        time.sleep(2)
        rc2,seconds2,out2=run(cmd)
        status2=classify_failure(rc2,out2)
        out=out+"\n--- BROKEN_RETRY ---\n"+out2
        rc,seconds,status=rc2,seconds+seconds2,status2
        attempts=2
        retry_recovered=status=="PASS"
    return rc,seconds,out,status,attempts,retry_recovered

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
    cmd=None
else:
    raise SystemExit(f"unknown kind {args.kind}")

attempt_count=0
infra_retry_recovered=False
if args.kind=="jvm_node":
    prerequisite=["./gradlew","testDebugUnitTest","--tests","com.omegas.prohub.LearningLatencyContractTest","--stacktrace"]
    rc1,seconds1,out1,status1,attempts1,recovered1=run_with_broken_retry(prerequisite)
    attempt_count+=attempts1
    infra_retry_recovered=infra_retry_recovered or recovered1
    if status1!="PASS":
        rc,seconds,out,status=rc1,seconds1,out1,status1
    else:
        rc2,seconds2,out2,status2,attempts2,recovered2=run_with_broken_retry(["node","--test",args.target])
        attempt_count+=attempts2
        infra_retry_recovered=infra_retry_recovered or recovered2
        rc,seconds,out,status=rc2,seconds1+seconds2,out1+"\n--- JVM->NODE ---\n"+out2,status2
else:
    rc,seconds,out,status,attempt_count,infra_retry_recovered=run_with_broken_retry(cmd)

receipt={
    "id":args.id,
    "kind":args.kind,
    "target":args.target,
    "sha":os.environ.get("GITHUB_SHA",""),
    "status":status,
    "exit_code":rc,
    "attempt_count":attempt_count,
    "infra_retry_recovered":infra_retry_recovered,
    "duration_s":round(seconds,3),
    "tail":out,
}
path=Path(args.receipt); path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps(receipt,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
print(json.dumps({k:v for k,v in receipt.items() if k!="tail"},indent=2,ensure_ascii=False))
if status!="PASS":
    print(f"--- GLOBAL_REALITY_{status}_TAIL ---")
    print(out)
sys.exit(0 if status=="PASS" else 2 if status=="BROKEN" else 1)
