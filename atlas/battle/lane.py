#!/usr/bin/env python3
from __future__ import annotations
import argparse, base64, hashlib, json, re, subprocess, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import semantic_targets

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

def escalate(summary,evidence=None,new_targets=None):
    return {"status":"ESCALATE","summary":summary,"claims":[],"evidence":evidence or [],"new_targets":new_targets or [],"contradictions":[]}

def occurrences(data:bytes, needle:bytes, limit=64):
    out=[]; pos=0
    while True:
        pos=data.find(needle,pos)
        if pos<0 or len(out)>=limit:return out
        out.append(pos);pos+=max(1,len(needle))

def semantic_payload(indices:Path):
    p=indices/"ghidra/autocal-semantics.json"
    if not p.is_file(): p=indices/"undelphi/autocal-semantics.json"
    if not p.is_file(): raise RuntimeError("semantic index missing")
    return semantic_targets.load(p)

def semantic_entry(indices:Path,kind,target):
    p=semantic_payload(indices)
    for x in semantic_targets.build(p):
        if x["kind"]==kind and x["target"]==target:return p,x
    return p,None

def function_for_va(indices:Path,va:int):
    fs=functions(indices)
    exact=f"{va:08x}"
    if exact in fs:return fs[exact]
    for f in fs.values():
        if int(f["min"],16)<=va<=int(f["max"],16):return f
    return None

def ghidra_function_evidence(indices:Path,f):
    ev={"kind":"ghidra-function","entry":f["entry"],"distance":f["distance"],"min":f["min"],"max":f["max"],"name":f["name"],"boundary":f["boundary"],"callers":f["callers"],"callees":f["callees"]}
    dp=indices/"ghidra/decomp"/(f["entry"]+".c")
    if dp.is_file():
        raw=dp.read_bytes();ev["decompile_sha256"]=sha256_bytes(raw);ev["decompile_bytes"]=len(raw);ev["decompile_path"]=str(dp)
    return ev

def ghidra_fn(indices,target):
    fs=functions(indices); f=fs.get(target.lower())
    if not f:return escalate("function absent from Ghidra AutoCal graph")
    # Callers/callees are preserved as evidence, but are not recursively promoted by
    # mechanical connectivity alone. Semantic lanes are responsible for discovering
    # producer/consumer dependencies that become new obligations.
    return prove("Ghidra resolved native function and graph neighborhood",[{"kind":"native-function","subject":f["entry"],"value":True}],[ghidra_function_evidence(indices,f)])

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
    needle=bytes.fromhex(target); hits=occurrences(binary_path.read_bytes(),needle)
    if not hits:return escalate("exact wire frame is not literal in executable; construction path requires escalation")
    return prove("exact wire frame bytes occur literally in executable",[{"kind":"literal-frame","subject":target,"value":True}],[{"kind":"raw-bytes","file_offsets":hits}])

def delphi_method(indices,target):
    _,item=semantic_entry(indices,"method",target)
    if not item:return escalate("method absent from Delphi semantic index")
    return prove("Delphi RTTI/VMT metadata identifies published method",[{"kind":"published-method","subject":target,"value":True}],[{"kind":"delphi-method","metadata":item["meta"]}])

def ghidra_method(indices,target):
    _,item=semantic_entry(indices,"method",target)
    if not item:return escalate("method absent from semantic catalog")
    f=function_for_va(indices,int(item["meta"]["va"]))
    if not f:return escalate("published method VA is outside current Ghidra AutoCal graph")
    return prove("published Delphi method resolves to Ghidra native function",[{"kind":"method-native-function","subject":target,"value":f["entry"]}],[ghidra_function_evidence(indices,f)],[{"kind":"function","target":f["entry"]}])

def delphi_field(indices,target):
    _,item=semantic_entry(indices,"field",target)
    if not item:return escalate("field absent from Delphi semantic index")
    return prove("Delphi metadata identifies field layout",[{"kind":"field-layout","subject":target,"value":True}],[{"kind":"delphi-field","metadata":item["meta"]}])

def raw_field_name(indices,binary_path,target):
    _,item=semantic_entry(indices,"field",target)
    if not item:return escalate("field absent from semantic catalog")
    name=item["meta"]["name"]; data=binary_path.read_bytes()
    ah=occurrences(data,name.encode("ascii","ignore")); uh=occurrences(data,name.encode("utf-16le"))
    if not ah and not uh:return escalate("field name not independently visible in canonical raw bytes")
    return prove("canonical binary independently contains the Delphi field name",[{"kind":"field-name-present","subject":target,"value":True}],[{"kind":"raw-field-name","ascii_offsets":ah,"utf16le_offsets":uh}])

def ghidra_field_use(indices,target):
    _,item=semantic_entry(indices,"field-use",target)
    if not item:return escalate("field-use target absent from semantic catalog")
    name=item["meta"]["name"]; off=int(item["meta"]["offset"]); patterns=[re.compile(rf"\b{re.escape(name)}\b",re.I),re.compile(rf"0x{off:x}\b",re.I)]
    hits=[]
    for p in sorted((indices/"ghidra/decomp").glob("*.c")):
        for lineno,line in enumerate(p.read_text(encoding="utf-8",errors="replace").splitlines(),1):
            if any(rx.search(line) for rx in patterns):
                hits.append({"function":p.stem,"line":lineno,"text":line[:500]})
                if len(hits)>=64:break
        if len(hits)>=64:break
    if not hits:return escalate("Ghidra decompiler has no direct field-name/offset signal in current corpus")
    nts=[{"kind":"function","target":h["function"]} for h in hits[:16]]
    return prove("Ghidra decompiler exposes field-use signal in reachable AutoCal code",[{"kind":"field-use-signal","subject":target,"value":True}],[{"kind":"ghidra-field-use","hits":hits}],nts)

def capstone_field_use(indices,binary_path,target):
    import pefile
    from capstone import Cs, CS_ARCH_X86, CS_MODE_32, CS_MODE_64
    from capstone.x86 import X86_OP_MEM
    _,item=semantic_entry(indices,"field-use",target)
    if not item:return escalate("field-use target absent from semantic catalog")
    wanted=int(item["meta"]["offset"])
    pe=pefile.PE(str(binary_path),fast_load=False);image=int(pe.OPTIONAL_HEADER.ImageBase);data=binary_path.read_bytes()
    mode=CS_MODE_64 if int(pe.FILE_HEADER.Machine)==0x8664 else CS_MODE_32
    md=Cs(CS_ARCH_X86,mode);md.detail=True
    def va_to_off(va):
        rva=va-image
        for s in pe.sections:
            a=int(s.VirtualAddress);size=max(int(s.Misc_VirtualSize),int(s.SizeOfRawData))
            if a<=rva<a+size:return int(s.PointerToRawData)+(rva-a)
        return None
    hits=[]
    for f in functions(indices).values():
        start=int(f["min"],16);end=int(f["max"],16)+1;fo=va_to_off(start)
        if fo is None:continue
        raw=data[fo:fo+max(1,end-start)]
        for ins in md.disasm(raw,start):
            for op in ins.operands:
                if op.type==X86_OP_MEM and int(op.mem.disp)==wanted:
                    hits.append({"function":f["entry"],"address":f"{ins.address:08x}","mnemonic":ins.mnemonic,"op_str":ins.op_str})
                    break
            if len(hits)>=64:break
        if len(hits)>=64:break
    if not hits:return escalate("Capstone found no memory displacement matching the field offset in reachable AutoCal functions")
    nts=[{"kind":"function","target":h["function"]} for h in hits[:16]]
    return prove("Capstone independently finds field-offset memory accesses in reachable AutoCal code",[{"kind":"field-memory-access","subject":target,"value":True}],[{"kind":"capstone-field-use","offset":wanted,"hits":hits}],nts)

def delphi_event(indices,target):
    payload,item=semantic_entry(indices,"event",target)
    if not item:return escalate("event binding absent from semantic index")
    method=semantic_targets.find_method_for_event(payload,item["meta"])
    nts=[{"kind":"method","target":method["target"]}] if method else []
    return prove("DFM/Delphi metadata binds UI event to handler",[{"kind":"event-handler","subject":target,"value":item["meta"]["handler"]}],[{"kind":"delphi-event","metadata":item["meta"]}],nts)

def ghidra_event(indices,target):
    payload,item=semantic_entry(indices,"event",target)
    if not item:return escalate("event binding absent from semantic index")
    method=semantic_targets.find_method_target(payload,item["meta"]["handler"])
    if not method:return escalate("event handler has no unique published Delphi method")
    r=ghidra_method(indices,method["target"])
    if r["status"]!="PROVEN":return escalate("event handler did not resolve to native Ghidra function",r.get("evidence"),[{"kind":"method","target":method["target"]}])
    return prove("UI event handler resolves to native Ghidra code",[{"kind":"event-native-handler","subject":target,"value":method["meta"]["va_hex"]}],r["evidence"],[{"kind":"method","target":method["target"]}]+r.get("new_targets",[]))

def delphi_action(indices,target):
    payload,item=semantic_entry(indices,"action",target)
    if not item:return escalate("action binding absent from semantic index")
    ev=semantic_targets.find_event_for_action(payload,item["meta"]["action"])
    nts=[{"kind":"event","target":ev["target"]}] if ev else []
    return prove("DFM binds control to action object",[{"kind":"control-action","subject":target,"value":item["meta"]["action"]}],[{"kind":"delphi-action","metadata":item["meta"]}],nts)

def ghidra_action(indices,target):
    payload,item=semantic_entry(indices,"action",target)
    if not item:return escalate("action binding absent from semantic index")
    ev=semantic_targets.find_event_for_action(payload,item["meta"]["action"])
    if not ev:return escalate("action object has no unique executable event binding")
    method=semantic_targets.find_method_for_event(payload,ev["meta"])
    if not method:return escalate("action handler has no unique published method")
    r=ghidra_method(indices,method["target"])
    if r["status"]!="PROVEN":return escalate("action handler did not resolve to native Ghidra function",r.get("evidence"))
    return prove("control action resolves through OnExecute to native code",[{"kind":"action-native-handler","subject":target,"value":method["meta"]["va_hex"]}],r["evidence"],[{"kind":"event","target":ev["target"]},{"kind":"method","target":method["target"]}]+r.get("new_targets",[]))

def delphi_serial(indices,target):
    _,item=semantic_entry(indices,"serial",target)
    if not item:return escalate("serial object absent from Delphi resource index")
    return prove("Delphi resource binds AutoCal object to serial code",[{"kind":"serial-binding","subject":target,"value":item["meta"]["serial_hex"]}],[{"kind":"delphi-serial","metadata":item["meta"]}])

def portmon_object(indices,target):
    _,item=semantic_entry(indices,"serial",target)
    if not item:return escalate("serial target absent from semantic catalog")
    code=int(item["meta"]["serial_code"]); needle=code.to_bytes(2,"little")
    idx=json.loads((indices/"portmon/portmon-index.json").read_text(encoding="utf-8"))
    hits=[]
    for row in idx.get("request_catalog",[]):
        try:b=bytes.fromhex(row["frame"])
        except ValueError:continue
        if len(b)>=3 and b[1:3]==needle:
            hits.append(row)
    if not hits:return escalate("serial code was not observed at the object-address position in recorded requests")
    return prove("Portmon independently observes the serial object address on wire",[{"kind":"serial-object-observed","subject":target,"value":True}],[{"kind":"portmon-object-position","little_endian":" ".join(f"{x:02X}" for x in needle),"hits":hits[:64],"clock_semantics":idx["clock_semantics"]}])

def delphi_visual(indices,target):
    _,item=semantic_entry(indices,"visual",target)
    if not item:return escalate("visual object absent from Delphi resource index")
    return prove("Delphi resource identifies AutoCal render object",[{"kind":"visual-object","subject":target,"value":True}],[{"kind":"delphi-visual","metadata":item["meta"]}])

def ghidra_visual(indices,target):
    _,item=semantic_entry(indices,"visual",target)
    if not item:return escalate("visual target absent from semantic catalog")
    name=item["meta"]["object"];hits=[]
    for line in (indices/"ghidra/strings.tsv").read_text(encoding="utf-8",errors="replace").splitlines():
        q=line.split("\t")
        if len(q)<2:continue
        try:s=base64.b64decode(q[1]).decode("utf-8","replace")
        except Exception:continue
        refs=q[2].split(";") if len(q)>2 and q[2] else []
        if name.lower() in s.lower() and refs:hits.append({"address":q[0],"text":s[:500],"refs":refs})
    if not hits:return escalate("Ghidra found no code-referenced defined string for visual object")
    nts=[{"kind":"function","target":r} for h in hits for r in h["refs"][:8]]
    return prove("Ghidra independently links visual object name to code references",[{"kind":"visual-code-reference","subject":target,"value":True}],[{"kind":"ghidra-visual","hits":hits[:32]}],nts[:32])

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
        elif a.driver=="delphi-method":r=delphi_method(a.indices,a.target)
        elif a.driver=="ghidra-method":r=ghidra_method(a.indices,a.target)
        elif a.driver=="delphi-field":r=delphi_field(a.indices,a.target)
        elif a.driver=="raw-field-name":r=raw_field_name(a.indices,bp,a.target)
        elif a.driver=="ghidra-field-use":r=ghidra_field_use(a.indices,a.target)
        elif a.driver=="capstone-field-use":r=capstone_field_use(a.indices,bp,a.target)
        elif a.driver=="delphi-event":r=delphi_event(a.indices,a.target)
        elif a.driver=="ghidra-event":r=ghidra_event(a.indices,a.target)
        elif a.driver=="delphi-action":r=delphi_action(a.indices,a.target)
        elif a.driver=="ghidra-action":r=ghidra_action(a.indices,a.target)
        elif a.driver=="delphi-serial":r=delphi_serial(a.indices,a.target)
        elif a.driver=="portmon-object":r=portmon_object(a.indices,a.target)
        elif a.driver=="delphi-visual":r=delphi_visual(a.indices,a.target)
        elif a.driver=="ghidra-visual":r=ghidra_visual(a.indices,a.target)
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
