#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import struct
from pathlib import Path

TPF0=b"TPF0"
RT_RCDATA=10

class DfmError(RuntimeError):
    pass

class Reader:
    def __init__(self,data:bytes,pos:int=0):
        self.data=data; self.pos=pos
    def remaining(self): return len(self.data)-self.pos
    def need(self,n):
        if n<0 or self.pos+n>len(self.data): raise DfmError(f"truncated DFM at 0x{self.pos:x}, need {n}")
    def take(self,n):
        self.need(n); b=self.data[self.pos:self.pos+n]; self.pos+=n; return b
    def u8(self): return self.take(1)[0]
    def i8(self): return struct.unpack("<b",self.take(1))[0]
    def i16(self): return struct.unpack("<h",self.take(2))[0]
    def i32(self): return struct.unpack("<i",self.take(4))[0]
    def i64(self): return struct.unpack("<q",self.take(8))[0]
    def f32(self): return struct.unpack("<f",self.take(4))[0]
    def f64(self): return struct.unpack("<d",self.take(8))[0]
    def peek(self):
        self.need(1); return self.data[self.pos]
    def shortstr(self):
        n=self.u8(); return self.take(n).decode("latin1","replace")
    def longstr(self,encoding="latin1",wide=False):
        n=self.i32()
        if n<0 or n>self.remaining(): raise DfmError(f"invalid string length {n} at 0x{self.pos:x}")
        if wide:
            size=n*2; self.need(size); return self.take(size).decode("utf-16le","replace")
        return self.take(n).decode(encoding,"replace")

def _number(v):
    if isinstance(v,(int,float)): return v
    raise DfmError(f"expected numeric prefix value, got {type(v).__name__}")

def parse_value(r:Reader):
    tag=r.u8()
    if tag==0: return None
    if tag==1:
        out=[]
        while r.peek()!=0: out.append(parse_value(r))
        r.u8(); return out
    if tag==2: return r.i8()
    if tag==3: return r.i16()
    if tag==4: return r.i32()
    if tag==5: return {"extended_hex":r.take(10).hex()}
    if tag==6: return r.shortstr()
    if tag==7: return {"ident":r.shortstr()}
    if tag==8: return False
    if tag==9: return True
    if tag==10:
        n=r.i32()
        if n<0: raise DfmError(f"negative binary length {n}")
        return {"binary_sha256":hashlib.sha256(r.take(n)).hexdigest(),"bytes":n}
    if tag==11:
        out=[]
        while True:
            x=r.shortstr()
            if not x: return {"set":out}
            out.append(x)
    if tag==12: return r.longstr()
    if tag==13: return {"nil":True}
    if tag==14:
        items=[]
        while r.peek()!=0:
            marker=r.u8()
            if marker!=1: raise DfmError(f"unexpected collection marker {marker} at 0x{r.pos-1:x}")
            props={}
            while True:
                name=r.shortstr()
                if not name: break
                props[name]=parse_value(r)
            items.append(props)
        r.u8(); return {"collection":items}
    if tag==15: return r.f32()
    if tag==16: return {"currency_raw":r.i64()}
    if tag==17: return r.f64()
    if tag==18: return r.longstr(wide=True)
    if tag==19: return r.i64()
    if tag==20: return r.longstr(encoding="utf-8")
    if tag==21: return r.f64()
    raise DfmError(f"unsupported TValueType {tag} at 0x{r.pos-1:x}")

def parse_object(r:Reader,depth=0):
    if depth>256: raise DfmError("component nesting too deep")
    flags=0; child_pos=None
    if r.peek() & 0xF0 == 0xF0:
        prefix=r.u8(); flags=prefix & 0x0F
        if flags & 0x02:
            child_pos=int(_number(parse_value(r)))
    class_name=r.shortstr()
    name=r.shortstr()
    if not class_name:
        raise DfmError(f"empty class name at 0x{r.pos:x}")
    props={}
    while True:
        prop=r.shortstr()
        if not prop: break
        props[prop]=parse_value(r)
    children=[]
    while r.remaining()>0:
        if r.peek()==0:
            r.u8(); break
        children.append(parse_object(r,depth+1))
    return {"class":class_name,"name":name,"flags":flags,"child_pos":child_pos,"properties":props,"children":children}

def parse_tpf0(data:bytes):
    start=data.find(TPF0)
    if start<0: raise DfmError("TPF0 signature absent")
    r=Reader(data,start+4)
    root=parse_object(r)
    root["_tpf0_offset"]=start
    return root

def _entry_name(entry):
    if getattr(entry,"name",None) is not None: return str(entry.name)
    return str(getattr(entry.struct,"Id",""))

def extract_rcdata(binary:Path,resource_name:str):
    import pefile
    pe=pefile.PE(str(binary),fast_load=False)
    pe.parse_data_directories(directories=[pefile.DIRECTORY_ENTRY["IMAGE_DIRECTORY_ENTRY_RESOURCE"]])
    root=getattr(pe,"DIRECTORY_ENTRY_RESOURCE",None)
    if root is None: raise DfmError("PE has no resource directory")
    for type_entry in root.entries:
        type_id=getattr(type_entry.struct,"Id",None)
        if type_id!=RT_RCDATA: continue
        for name_entry in type_entry.directory.entries:
            name=_entry_name(name_entry)
            if name.upper()!=resource_name.upper(): continue
            langs=name_entry.directory.entries
            if not langs: raise DfmError(f"resource {resource_name} has no language entry")
            data_entry=langs[0].data.struct
            rva=int(data_entry.OffsetToData); size=int(data_entry.Size)
            blob=pe.get_data(rva,size)
            if len(blob)!=size: raise DfmError(f"short resource {resource_name}: {len(blob)} != {size}")
            return blob,{"resource":name,"rva":rva,"size":size,"sha256":hashlib.sha256(blob).hexdigest()}
    raise DfmError(f"RT_RCDATA resource {resource_name} not found")

def walk(node,path=""):
    here=f"{path}/{node['name'] or node['class']}" if path else (node["name"] or node["class"])
    yield here,node
    for child in node["children"]:
        yield from walk(child,here)

def inspect_object(binary:Path,resource_name:str,object_name:str):
    blob,meta=extract_rcdata(binary,resource_name)
    root=parse_tpf0(blob)
    matches=[(path,node) for path,node in walk(root) if node["name"].lower()==object_name.lower()]
    if len(matches)!=1:
        raise DfmError(f"expected one {object_name} in {resource_name}, found {len(matches)}")
    path,node=matches[0]
    return {"resource":meta,"path":path,"class":node["class"],"name":node["name"],"properties":node["properties"],"flags":node["flags"]}

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--binary",type=Path,required=True)
    ap.add_argument("--resource",required=True)
    ap.add_argument("--object",required=True)
    ap.add_argument("--expect-class")
    ap.add_argument("--property")
    ap.add_argument("--expect-int",type=lambda x:int(x,0))
    args=ap.parse_args()
    fact=inspect_object(args.binary,args.resource,args.object)
    if args.expect_class and fact["class"].lower()!=args.expect_class.lower():
        raise SystemExit(f"class mismatch: {fact['class']} != {args.expect_class}")
    if args.property:
        if args.property not in fact["properties"]: raise SystemExit(f"property absent: {args.property}")
        value=fact["properties"][args.property]
        if args.expect_int is not None and value!=args.expect_int:
            raise SystemExit(f"property mismatch: {args.property}={value!r} expected={args.expect_int}")
    print(json.dumps(fact,indent=2,ensure_ascii=False,default=str))

if __name__=="__main__":
    main()
