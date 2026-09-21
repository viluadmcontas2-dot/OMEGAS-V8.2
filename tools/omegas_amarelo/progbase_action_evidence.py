from __future__ import annotations
import argparse, json, struct
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
FIX=ROOT/"tests"/"fixtures"

ACTIONS={"AUTO_MATCH_HANDLER":8,"RESET_PETROL_HANDLER":1,"RESET_GAS_HANDLER":2,"RESET_ALL_HANDLER":4}
DISPATCHER=0x00517568

def load():
    return json.loads((FIX/"progbase-autocal-byte-slices-v1.json").read_text())

def b(row): return bytes.fromhex(row["hex"])

def prove_handler(name):
    f=load(); row=f["slices"][name]; raw=b(row); va=int(row["va"],16)
    code=ACTIONS[name]
    assert raw[:3]==bytes((0x6A,code,0x50))
    assert raw[3]==0xE8
    rel=struct.unpack("<i",raw[4:8])[0]
    assert va+8+rel==DISPATCHER
    assert raw[8:12]==bytes.fromhex("83 C4 08 C3")
    return {"lane":name,"status":"PASS","action_code":code,"dispatcher":hex(DISPATCHER)}

def prove_bridge():
    raw=b(load()["slices"]["NATIVE_BRIDGE"])
    assert bytes.fromhex("C6 45 D8 04 8A 55 0C 88 55 D9") in raw
    assert bytes.fromhex("C7 45 D4 02 00 00 00") in raw
    assert bytes.fromhex("BA 24 00 00 00") in raw
    return {"lane":"bridge","status":"PASS","payload":"04 <action>","service":"0x24","length":2}

def prove_transport():
    raw=b(load()["slices"]["TRANSPORT_PREFIX"])
    assert bytes.fromhex("8B 4D CC 81 E1 FF 00 00 00 C1 E1 08 89 4D C4") in raw
    assert bytes.fromhex("8B 45 0C 83 38 07") in raw
    return {"lane":"transport","status":"PASS","header_rule":"service byte shifted into high byte; payload length participates in low byte"}

def frame(code):
    body=bytes((0x02,0x24,0x04,code))
    return body+bytes((sum(body)&0xff,))

def prove_frames():
    expected={1:"02 24 04 01 2B",2:"02 24 04 02 2C",4:"02 24 04 04 2E",8:"02 24 04 08 32"}
    got={k:frame(k).hex(" ").upper() for k in expected}
    assert got==expected
    return {"lane":"frames","status":"PASS","frames":got}

def prove_raw():
    auto=json.loads((FIX/"portmon-autocal-cycle-v1.json").read_text())
    log=json.loads((FIX/"portmon-lognovo-control-v1.json").read_text())
    needle="02 24 04 04 2E"
    auto_hits=[x for x in auto["transactions"] if x.get("request")==needle]
    log_hits=log["evidence"]["ACTION_CODE_4_FRAME_CANDIDATE"]
    assert auto_hits and log_hits and log_hits[0]["request"]==needle
    return {"lane":"dual-portmon-reset-all","status":"PASS","frame":needle,"autocal_hits":len(auto_hits),"lognovo_hits":len(log_hits)}

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--lane",required=True); a=ap.parse_args()
    lane=a.lane
    if lane in ACTIONS: out=prove_handler(lane)
    elif lane=="bridge": out=prove_bridge()
    elif lane=="transport": out=prove_transport()
    elif lane=="frames": out=prove_frames()
    elif lane=="dual-portmon": out=prove_raw()
    else: raise SystemExit("unknown lane")
    print(json.dumps(out,sort_keys=True))
if __name__=="__main__": main()
