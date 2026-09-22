#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, re, struct, subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
CANON=ROOT/"atlas/manifests/canonical-inputs.json"
SEEDS=ROOT/"atlas/manifests/autocal-seeds.json"

def sha256(path:Path)->str:
    h=hashlib.sha256()
    with path.open("rb") as f:
        for c in iter(lambda:f.read(1024*1024),b""): h.update(c)
    return h.hexdigest()

def find_all(data:bytes,needle:bytes):
    pos=0
    while True:
        pos=data.find(needle,pos)
        if pos<0:return
        yield pos
        pos+=max(1,len(needle))

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--out",type=Path,required=True); args=ap.parse_args()
    import pefile
    canon=json.loads(CANON.read_text(encoding="utf-8-sig"))
    seeds=json.loads(SEEDS.read_text(encoding="utf-8"))
    row=next(x for x in canon["inputs"] if x["kind"]=="executable")
    binary=ROOT/row["repo_path"]
    actual=sha256(binary)
    if actual.lower()!=row["sha256"].lower(): raise SystemExit("canonical binary hash mismatch")
    data=binary.read_bytes()
    pe=pefile.PE(str(binary),fast_load=False)
    image_base=int(pe.OPTIONAL_HEADER.ImageBase)

    sections=[]
    for s in pe.sections:
        name=s.Name.rstrip(b"\0").decode("ascii","replace")
        raw0=int(s.PointerToRawData); raw1=raw0+int(s.SizeOfRawData)
        va0=image_base+int(s.VirtualAddress)
        va1=va0+max(int(s.Misc_VirtualSize),int(s.SizeOfRawData))
        executable=bool(int(s.Characteristics)&0x20000000)
        sections.append({"name":name,"raw0":raw0,"raw1":raw1,"va0":va0,"va1":va1,"executable":executable})

    def off_to_va(off:int):
        for s in sections:
            if s["raw0"]<=off<s["raw1"]: return s["va0"]+(off-s["raw0"]),s["name"]
        if off<int(pe.OPTIONAL_HEADER.SizeOfHeaders): return image_base+off,"HEADERS"
        return None,None

    exec_ranges=[s for s in sections if s["executable"]]
    targets={}
    target_vas={}
    for item in seeds["symbols"]:
        term=item["term"]; hits=[]
        for enc,needle in (("ascii",term.encode("ascii","ignore")),("utf16le",term.encode("utf-16le"))):
            for off in find_all(data,needle):
                va,section=off_to_va(off)
                hit={"encoding":enc,"file_offset":off,"file_offset_hex":hex(off),"va":va,"va_hex":hex(va) if va is not None else None,"section":section}
                hits.append(hit)
                if va is not None: target_vas[va]=term
        candidates=[]
        for hit in hits:
            off=hit["file_offset"]; start=max(0,off-768); end=min(len(data),off+768)
            first=(start+3)&~3
            for p in range(first,max(first,end-3),4):
                value=struct.unpack_from("<I",data,p)[0]
                for s in exec_ranges:
                    if s["va0"]<=value<s["va1"]:
                        candidates.append({"va":value,"va_hex":hex(value),"pointer_file_offset":p,"pointer_file_offset_hex":hex(p),"distance":abs(p-off),"source_hit":hit["file_offset_hex"]})
                        break
        uniq={}
        for c in sorted(candidates,key=lambda x:(x["distance"],x["va"])):
            uniq.setdefault(c["va"],c)
        targets[term]={"kind":item["kind"],"provenance":item["provenance"],"hits":hits,"candidate_code_pointers":list(uniq.values())[:64],"objdump_xrefs":[]}

    # Independent GNU objdump pass: find direct references to known string VAs.
    proc=subprocess.run(["objdump","-d","-M","intel",str(binary)],capture_output=True,text=True,errors="replace")
    if proc.returncode!=0: raise SystemExit("objdump failed: "+proc.stderr[-1000:])
    addr_line=re.compile(r"^\s*([0-9a-fA-F]+):")
    hex_token=re.compile(r"(?:0x)?([0-9a-fA-F]{6,16})")
    xref_seed=set()
    for line in proc.stdout.splitlines():
        m=addr_line.match(line)
        if not m: continue
        insn_va=int(m.group(1),16)
        values=set()
        for tok in hex_token.findall(line):
            try: values.add(int(tok,16))
            except ValueError: pass
        matches=[v for v in values if v in target_vas]
        for v in matches:
            term=target_vas[v]
            targets[term]["objdump_xrefs"].append({"instruction_va":insn_va,"instruction_va_hex":hex(insn_va),"target_va":v,"target_va_hex":hex(v),"line":line[:500]})
            xref_seed.add(insn_va)

    seed_addrs=set(xref_seed)
    for t in targets.values():
        seed_addrs.update(c["va"] for c in t["candidate_code_pointers"])
    for lead in seeds.get("address_leads",[]):
        seed_addrs.add(int(lead["va"],16))

    out={
        "schema":"omegas.atlas.static-index.v1",
        "binary_sha256":actual,
        "machine":hex(int(pe.FILE_HEADER.Machine)),
        "image_base":image_base,"image_base_hex":hex(image_base),
        "sections":sections,"targets":targets,
        "seed_address_count":len(seed_addrs),
        "objdump_sha256":hashlib.sha256(proc.stdout.encode("utf-8","replace")).hexdigest()
    }
    args.out.mkdir(parents=True,exist_ok=True)
    (args.out/"static-index.json").write_text(json.dumps(out,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    with (args.out/"ghidra-seeds.txt").open("w",encoding="utf-8") as f:
        for a in sorted(seed_addrs): f.write(f"0x{a:08x}\n")
    with (args.out/"objdump-auto-xrefs.txt").open("w",encoding="utf-8") as f:
        for term,t in targets.items():
            for x in t["objdump_xrefs"]: f.write(f"{term}\t{x['line']}\n")
    print(json.dumps({"targets":len(targets),"seed_addresses":len(seed_addrs),"direct_objdump_xrefs":sum(len(x["objdump_xrefs"]) for x in targets.values())},indent=2))

if __name__=="__main__": main()
