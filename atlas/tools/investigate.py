#!/usr/bin/env python3
import argparse, hashlib, json, os, struct, subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
MANIFEST=ROOT/"atlas/manifests/canonical-inputs.json"
ALLOWED={"PROVEN","REFUTED","ESCALATE","BROKEN"}

def sha256_path(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for c in iter(lambda:f.read(1024*1024),b""):
            h.update(c)
    return h.hexdigest()

def canonical_binary():
    m=json.loads(MANIFEST.read_text(encoding="utf-8-sig"))
    row=next(x for x in m["inputs"] if x["kind"]=="executable")
    p=ROOT/row["repo_path"]
    actual=sha256_path(p)
    if actual.lower()!=row["sha256"].lower():
        raise RuntimeError(f"canonical binary hash mismatch: {actual}")
    return p, actual

def find_all(data, needle):
    out=[]; pos=0
    while True:
        pos=data.find(needle,pos)
        if pos<0:return out
        out.append(pos);pos+=max(1,len(needle))

def claim_presence(target, engine, evidence):
    return {
        "status":"PROVEN",
        "claims":[{"kind":"presence","subject":target,"value":True}],
        "evidence":evidence,
        "dependencies":[],
        "new_targets":[],
        "contradictions":[],
        "summary":f"{engine} independently located {target}",
    }

def bytestring(binary,target):
    data=binary.read_bytes()
    evidence=[]
    for enc,needle in (("ascii",target.encode("ascii","ignore")),("utf16le",target.encode("utf-16le"))):
        for off in find_all(data,needle)[:64]:
            evidence.append({"kind":"raw-bytes","encoding":enc,"file_offset":off,"file_offset_hex":hex(off),"needle_hex":needle.hex()})
    if evidence:return claim_presence(target,"bytestring",evidence)
    return {"status":"ESCALATE","claims":[],"evidence":[],"dependencies":[],"new_targets":[],"contradictions":[],"summary":f"raw search did not locate {target}"}

def gnu_strings(binary,target):
    p=subprocess.run(["strings","-a","-t","x",str(binary)],capture_output=True,text=True,errors="replace")
    if p.returncode!=0:
        raise RuntimeError(f"strings failed rc={p.returncode}: {p.stderr[:500]}")
    hits=[]
    for line in p.stdout.splitlines():
        if target.lower() in line.lower():
            parts=line.strip().split(maxsplit=1)
            if parts:
                try: off=int(parts[0],16)
                except ValueError: continue
                hits.append({"kind":"gnu-strings","file_offset":off,"file_offset_hex":hex(off),"line":line[:300]})
    if hits:return claim_presence(target,"gnu-strings",hits[:64])
    return {"status":"ESCALATE","claims":[],"evidence":[],"dependencies":[],"new_targets":[],"contradictions":[],"summary":f"GNU strings did not locate {target}"}

def pe_map(binary,target):
    import pefile
    pe=pefile.PE(str(binary),fast_load=False)
    data=binary.read_bytes()
    hits=find_all(data,target.encode("ascii","ignore"))
    evidence=[]
    for off in hits[:64]:
        for s in pe.sections:
            start=s.PointerToRawData; end=start+s.SizeOfRawData
            if start<=off<end:
                rva=int(s.VirtualAddress)+(off-start)
                va=int(pe.OPTIONAL_HEADER.ImageBase)+rva
                evidence.append({
                    "kind":"pe-mapping","file_offset":off,"file_offset_hex":hex(off),
                    "section":s.Name.rstrip(b"\0").decode("ascii","replace"),
                    "rva":rva,"rva_hex":hex(rva),"va":va,"va_hex":hex(va),
                    "machine":hex(pe.FILE_HEADER.Machine),"image_base":hex(pe.OPTIONAL_HEADER.ImageBase),
                })
                break
    if evidence:return claim_presence(target,"pe-map",evidence)
    return {"status":"ESCALATE","claims":[],"evidence":[],"dependencies":[],"new_targets":[],"contradictions":[],"summary":f"PE mapping did not locate {target}"}

def undelphi(binary,target):
    exe=Path(os.environ.get("UNDELPHI_DUMP",".atlas-tools/dump"))
    if not exe.is_file():
        raise RuntimeError(f"undelphi dump missing: {exe}")
    p=subprocess.run([str(exe),str(binary)],capture_output=True,text=True,errors="replace",timeout=120)
    if p.returncode!=0:
        raise RuntimeError(f"undelphi failed rc={p.returncode}: {(p.stderr+p.stdout)[-1000:]}")
    lines=[line for line in p.stdout.splitlines() if target.lower() in line.lower()]
    if lines:
        return claim_presence(target,"undelphi",[{"kind":"undelphi-v0.3.2","line":x[:500]} for x in lines[:64]])
    return {"status":"ESCALATE","claims":[],"evidence":[{"kind":"undelphi-v0.3.2","note":"parser completed but target absent"}],"dependencies":[],"new_targets":[],"contradictions":[],"summary":f"undelphi did not expose {target}"}

def fixture(driver,target):
    if driver=="fixture-escalate":
        return {"status":"ESCALATE","claims":[],"evidence":[{"kind":"harness-fixture","phase":"needs-second-method"}],"dependencies":[],"new_targets":[],"contradictions":[],"summary":"intentional harness escalation"}
    if driver=="fixture-resolve":
        return {"status":"PROVEN","claims":[{"kind":"harness","subject":target,"value":"resolved"}],"evidence":[{"kind":"harness-fixture","oracle":"second-method"}],"dependencies":[],"new_targets":[],"contradictions":[],"summary":"harness escalation resolved by alternate method"}
    if driver=="fixture-a":
        return {"status":"PROVEN","claims":[{"kind":"harness-choice","subject":target,"value":"A"}],"evidence":[{"kind":"harness-fixture","engine":"A"}],"dependencies":[],"new_targets":[],"contradictions":[],"summary":"intentional side A"}
    if driver=="fixture-b":
        return {"status":"PROVEN","claims":[{"kind":"harness-choice","subject":target,"value":"B"}],"evidence":[{"kind":"harness-fixture","engine":"B"}],"dependencies":[],"new_targets":[],"contradictions":[],"summary":"intentional side B"}
    if driver=="fixture-challenger":
        return {"status":"PROVEN","claims":[{"kind":"harness-resolution","subject":target,"value":"A"}],"evidence":[{"kind":"harness-fixture","oracle":"challenger"}],"dependencies":[],"new_targets":[],"contradictions":[],"summary":"harness contradiction resolved by challenger"}
    raise ValueError(driver)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--driver",required=True)
    ap.add_argument("--target",required=True)
    ap.add_argument("--lane",required=True)
    ap.add_argument("--receipt",required=True)
    args=ap.parse_args()
    binary,binary_hash=canonical_binary()
    try:
        if args.driver.startswith("fixture-"):
            result=fixture(args.driver,args.target)
        elif args.driver=="bytestring":
            result=bytestring(binary,args.target)
        elif args.driver=="gnu-strings":
            result=gnu_strings(binary,args.target)
        elif args.driver=="pe-map":
            result=pe_map(binary,args.target)
        elif args.driver=="undelphi":
            result=undelphi(binary,args.target)
        else:
            raise ValueError(f"unsupported driver: {args.driver}")
    except Exception as e:
        result={"status":"BROKEN","claims":[],"evidence":[],"dependencies":[],"new_targets":[],"contradictions":[],"summary":f"{type(e).__name__}: {e}"}
    if result["status"] not in ALLOWED:
        raise SystemExit(f"forbidden status {result['status']}")
    fp=hashlib.sha256(json.dumps([binary_hash,args.target,args.driver],separators=(",",":")).encode()).hexdigest()
    result.update({
        "schema":"omegas.atlas.receipt.v1","campaign":"progbase-autocal",
        "binary_sha256":binary_hash,"lane":args.lane,"driver":args.driver,
        "target_id":args.target,"attempt_fingerprint":fp,
    })
    out=ROOT/args.receipt
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(json.dumps({"lane":args.lane,"driver":args.driver,"target":args.target,"status":result["status"]}))
    if result["status"]=="BROKEN":
        raise SystemExit(2)

if __name__=="__main__":
    main()
