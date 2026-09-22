#!/usr/bin/env python3
from __future__ import annotations
import argparse, base64, hashlib, json, re, subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
CANON=ROOT/"atlas/manifests/canonical-inputs.json"
ALLOWED={"PROVEN","REFUTED","ESCALATE","BROKEN"}

def sha256_bytes(b:bytes): return hashlib.sha256(b).hexdigest()
def sha256_path(path:Path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for c in iter(lambda:f.read(1024*1024),b""):h.update(c)
    return h.hexdigest()

def binary():
    m=json.loads(CANON.read_text(encoding="utf-8-sig")); row=next(x for x in m["inputs"] if x["kind"]=="executable")
    p=ROOT/row["repo_path"]; h=sha256_path(p)
    if h.lower()!=row["sha256"].lower(): raise RuntimeError("canonical binary hash mismatch")
    return p,h

def functions(indices:Path):
    out={}
    lines=(indices/"ghidra/functions.tsv").read_text(encoding="utf-8",errors="replace").splitlines()
    for line in lines[1:]:
        p=line.split("\t")
        if len(p)<8:continue
        out[p[0].lower()]={"entry":p[0],"distance":int(p[1]),"min":p[2],"max":p[3],"name":p[4],"boundary":p[5]=="true","callers":[x for x in p[6].split(";") if x],"callees":[x for x in p[7].split(";") if x]}
    return out

def prove(summary,claims,evidence,new_targets=None):
    return {"status":"PROVEN","summary":summary,"claims":claims,"evidence":evidence,"new_targets":new_targets or [],"contradictions":[]}

def escalate(summary,evidence=None):
    return {"status":"ESCALATE","summary":summary,"claims":[],"evidence":evidence or [],"new_targets":[],"contradictions":[]}

def ghidra_fn(indices,target):
    fs=functions(indices); f=fs.get(target.lower())
    if not f:return escalate("function absent from Ghidra AutoCal graph")
    dp=indices/"ghidra/decomp"/(f["entry"]+".c")
    ev={"kind":"ghidra-function","entry":f["entry"],"distance":f["distance"],"min":f["min"],"max":f["max"],"name":f["name"],"boundary":f["boundary"],"callers":f["callers"],"callees":f["callees"]}
    if dp.is_file():
        raw=dp.read_bytes(); ev["decompile_sha256"]=sha256_bytes(raw); ev["decompile_bytes"]=len(raw); ev["decompile_path"]=str(dp)
    nts=[]
    for addr in (f["callers"]+f["callees"])[:64]:
        n=fs.get(addr.lower())
        if n is not None: nts.append({"kind":"function","target":n["entry"],"distance":n["distance"]})
    return prove("Ghidra resolved native function and graph neighborhood",[{"kind":"native-function","subject":f["entry"],"value":True}], [ev],nts)

def objdump_fn(indices,binary_path,target):
    fs=functions(indices); f=fs.get(target.lower())
    if not f:return escalate("function absent from graph boundary oracle")
    start=int(f["min"],16); stop=int(f["max"],16)+16
    p=subprocess.run(["objdump","-d","-M","intel",f"--start-address={start}",f"--stop-address={stop}",str(binary_path)],capture_output=True,text=True,errors="replace")
    if p.returncode!=0: raise RuntimeError("objdump failed: "+p.stderr[-1000:])
    ins=[x for x in p.stdout.splitlines() if re.match(r"^\s*[0-9a-fA-F]+:\s",x)]
    if not ins:return escalate("GNU objdump decoded no instructions in Ghidra function range")
    calls=[]
    for line in ins:
        if "\tcall" in line or " call " in line:
            m=re.search(r"\b([0-9a-fA-F]{6,16})\b",line.split("call",1)[-1])
            if m:calls.append(m.group(1).lower())
    blob="\n".join(ins).encode()
    ev={"kind":"gnu-objdump-range","entry":f["entry"],"instruction_count":len(ins),"sha256":sha256_bytes(blob),"direct_calls":calls[:128],"first_instructions":ins[:12]}
    return prove("GNU objdump independently decoded the native function range",[{"kind":"native-code-decodes","subject":f["entry"],"value":True}],[ev])

def capstone_fn(indices,binary_path,target):
    import pefile
    from capstone import Cs, CS_ARCH_X86, CS_MODE_32, CS_MODE_64
    fs=functions(indices); f=fs.get(target.lower())
    if not f:return escalate("function absent from graph boundary oracle")
    pe=pefile.PE(str(binary_path),fast_load=False); image=int(pe.OPTIONAL_HEADER.ImageBase)
    start=int(f["min"],16); end=int(f["max"],16)+1
    def va_to_off(va):
        rva=va-image
        for s in pe.sections:
            a=int(s.VirtualAddress); size=max(int(s.Misc_VirtualSize),int(s.SizeOfRawData))
            if a<=rva<a+size:return int(s.PointerToRawData)+(rva-a)
        return None
    off=va_to_off(start)
    if off is None:return escalate("Capstone could not map function VA to PE file offset")
    raw=binary_path.read_bytes()[off:off+max(1,end-start)]
    mode=CS_MODE_64 if int(pe.FILE_HEADER.Machine)==0x8664 else CS_MODE_32
    md=Cs(CS_ARCH_X86,mode)
    ins=list(md.disasm(raw,start))
    if not ins:return escalate("Capstone decoded no instruction at target")
    text="\n".join(f"{i.address:x}:{i.mnemonic} {i.op_str}" for i in ins)
    return prove("Capstone independently decoded target bytes",[{"kind":"native-code-decodes","subject":f["entry"],"value":True}],[{"kind":"capstone-range","instruction_count":len(ins),"sha256":sha256_bytes(text.encode()),"first_instructions":text.splitlines()[:12]}])

def raw_symbol(indices,target):
    idx=json.loads((indices/"static/static-index.json").read_text(encoding="utf-8"))
    t=idx["targets"].get(target)
    if not t or not t["hits"]:return escalate("symbol absent from direct canonical-byte index")
    return prove("canonical bytes contain target symbol",[{"kind":"symbol-present","subject":target,"value":True}],[{"kind":"static-index","hits":t["hits"][:16],"candidate_code_pointers":t["candidate_code_pointers"][:16],"objdump_xrefs":t["objdump_xrefs"][:16]}])

def undelphi_symbol(indices,target):
    p=indices/"undelphi/undelphi.txt"; lines=[]
    for line in p.read_text(encoding="utf-8",errors="replace").splitlines():
        if target.lower() in line.lower():lines.append(line[:800])
        if len(lines)>=32:break
    if not lines:return escalate("undelphi completed but did not expose target symbol")
    return prove("Delphi-specific parser independently exposed target symbol",[{"kind":"symbol-present","subject":target,"value":True}],[{"kind":"undelphi-v0.3.2","lines":lines}])

def ghidra_string(indices,target):
    hits=[]
    p=indices/"ghidra/strings.tsv"
    for line in p.read_text(encoding="utf-8",errors="replace").splitlines():
        q=line.split("\t")
        if len(q)<2:continue
        try:s=base64.b64decode(q[1]).decode("utf-8","replace")
        except Exception:continue
        if target.lower() in s.lower():hits.append({"address":q[0],"text":s[:500],"refs":q[2].split(";") if len(q)>2 and q[2] else []})
    if not hits:return escalate("Ghidra defined-string analysis did not expose target")
    return prove("Ghidra independently exposed target string and references",[{"kind":"symbol-present","subject":target,"value":True}],[{"kind":"ghidra-string","hits":hits[:32]}])

def portmon_frame(indices,target):
    idx=json.loads((indices/"portmon/portmon-index.json").read_text(encoding="utf-8"))
    rows=[x for x in idx.get("candidates",[]) if x["frame"]==target]
    rows += [x for x in idx.get("protocol_leads",[]) if x["frame"]==target and x.get("observed_autocal")]
    if not rows:return escalate("frame not observed in selected AutoCal corpus")
    row=rows[0]
    return prove("frame is directly observed in AutoCal Portmon corpus",[{"kind":"frame-observed","subject":target,"value":True}],[{"kind":"portmon","observation":row,"clock_semantics":idx["clock_semantics"]}])

def binary_frame(binary_path,target):
    needle=bytes.fromhex(target); data=binary_path.read_bytes(); hits=[]; pos=0
    while True:
        pos=data.find(needle,pos)
        if pos<0:break
        hits.append(pos);pos+=1
        if len(hits)>=64:break
    if not hits:return escalate("exact wire frame is not literal in executable; construction path requires escalation")
    return prove("exact wire frame bytes occur literally in executable",[{"kind":"literal-frame","subject":target,"value":True}],[{"kind":"raw-bytes","file_offsets":hits}])

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--indices",type=Path,required=True); ap.add_argument("--kind",required=True); ap.add_argument("--target",required=True); ap.add_argument("--driver",required=True); ap.add_argument("--lane",required=True); ap.add_argument("--receipt",type=Path,required=True)
    a=ap.parse_args(); bp,bh=binary()
    try:
        if a.driver=="ghidra-fn":r=ghidra_fn(a.indices,a.target)
        elif a.driver=="objdump-fn":r=objdump_fn(a.indices,bp,a.target)
        elif a.driver=="capstone-fn":r=capstone_fn(a.indices,bp,a.target)
        elif a.driver=="raw-symbol":r=raw_symbol(a.indices,a.target)
        elif a.driver=="undelphi-symbol":r=undelphi_symbol(a.indices,a.target)
        elif a.driver=="ghidra-string":r=ghidra_string(a.indices,a.target)
        elif a.driver=="portmon-frame":r=portmon_frame(a.indices,a.target)
        elif a.driver=="binary-frame":r=binary_frame(bp,a.target)
        else:raise ValueError("unsupported driver "+a.driver)
    except Exception as e:
        r={"status":"BROKEN","summary":f"{type(e).__name__}: {e}","claims":[],"evidence":[],"new_targets":[],"contradictions":[]}
    if r["status"] not in ALLOWED:raise SystemExit("forbidden status")
    fp=hashlib.sha256(json.dumps([bh,a.kind,a.target,a.driver],separators=(",",":")).encode()).hexdigest()
    r.update({"schema":"omegas.atlas.battle-receipt.v1","binary_sha256":bh,"kind":a.kind,"target_id":a.target,"driver":a.driver,"lane":a.lane,"attempt_fingerprint":fp})
    a.receipt.parent.mkdir(parents=True,exist_ok=True);a.receipt.write_text(json.dumps(r,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(json.dumps({"lane":a.lane,"kind":a.kind,"target":a.target,"driver":a.driver,"status":r["status"]}))
    if r["status"]=="BROKEN":raise SystemExit(2)

if __name__=="__main__":main()
