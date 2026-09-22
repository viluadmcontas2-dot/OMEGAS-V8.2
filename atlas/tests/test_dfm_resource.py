import importlib.util
import struct
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location("dfm_resource",ROOT/"atlas/battle/dfm_resource.py")
DFM=importlib.util.module_from_spec(spec);spec.loader.exec_module(DFM)

def ss(s):
    b=s.encode("latin1"); return bytes([len(b)])+b

def intval(v):
    if -128<=v<=127: return bytes([2])+struct.pack("<b",v)
    if -32768<=v<=32767: return bytes([3])+struct.pack("<h",v)
    return bytes([4])+struct.pack("<i",v)

def obj(cls,name,props=None,children=None):
    out=bytearray(); out+=ss(cls); out+=ss(name)
    for k,v in (props or {}).items():
        out+=ss(k); out+=intval(v)
    out+=b"\x00"
    for child in children or []: out+=child
    out+=b"\x00"
    return bytes(out)

class DfmResourceTest(unittest.TestCase):
    def test_parses_nested_serial_and_visual_objects(self):
        raw=b"TPF0"+obj("TAutoCalDM","AutoCalDM",children=[
            obj("TAebValue","PETROL_POINT_2DELETE",{"SerialCode":0x016d}),
            obj("TPointSeries","PetrolPoint"),
        ])
        root=DFM.parse_tpf0(raw)
        nodes={n["name"]:n for _,n in DFM.walk(root)}
        self.assertEqual(nodes["PETROL_POINT_2DELETE"]["properties"]["SerialCode"],0x016d)
        self.assertEqual(nodes["PetrolPoint"]["class"],"TPointSeries")

    def test_reads_filer_prefix_child_position(self):
        child=bytes([0xF2])+intval(7)+obj("TButton","B")
        raw=b"TPF0"+obj("TForm","F",children=[child])
        root=DFM.parse_tpf0(raw)
        self.assertEqual(root["children"][0]["flags"],2)
        self.assertEqual(root["children"][0]["child_pos"],7)

if __name__=="__main__": unittest.main()
