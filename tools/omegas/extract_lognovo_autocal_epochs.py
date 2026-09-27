#!/usr/bin/env python3
"""Extract the three post-reset native AutoMatch epochs from PortmonLOGNOVO.LOG.

Passive/offline only. The source is the original ProgBase Portmon capture. The
output keeps exact request/response bytes and enough surrounding transactions to
prove an ECU-side epoch rollover without treating an inferred OMEGAS formula as
an oracle.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from scripts.omegas.portmon_parser import Transaction, parse

RAW_SHA256 = "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64"
ZIP_SHA256 = "6879fa2a7931d22c207cd7fa47dffb59e1df0fe1de216e34e3f11e0c08cc1c17"
PROGBASE_SHA256 = "8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4"

REQ = {
    "STATUS":"48 0B 53","COUNT":"09 74 01 7E","MUL":"29 61 01 8B",
    "GPREV_T":"29 5D 01 87","GPREV_M":"29 5E 01 88",
    "GCUR_T":"29 5F 01 89","GCUR_M":"29 60 01 8A","NGAS":"29 5C 01 86",
    "PETR_T":"29 62 01 8C","PETR_M":"29 63 01 8D","NPETR":"29 5B 01 85",
    "AXIS":"29 4B 01 75","PRESS":"29 4C 01 76","MAX":"0A 65 01 02 72",
    "ENABLE":"09 4A 01 54","CAL":"29 72 01 9C","RESET_ALL":"02 24 04 04 2E",
    "MANUAL_AUTOMATCH":"02 24 04 08 32","MUL_ACT_EE":"29 58 01 82",
    "MUL_PREV_EE":"29 59 01 83","MUL_UPD_CALL_CNTR_EE":"09 5A 01 64",
}

def sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024*1024), b""): h.update(block)
    return h.hexdigest()

def payload(tx: Transaction) -> bytes:
    request=bytes.fromhex(tx.request_hex); response=bytes.fromhex(tx.response_hex)
    if not response.startswith(request): raise ValueError(f"response does not echo request at sequence {tx.sequence}")
    suffix=response[len(request):]
    if len(suffix)<3 or suffix[0]!=0x53: raise ValueError(f"non-ACK response at sequence {tx.sequence}")
    size=suffix[1]
    if len(suffix)!=size+3: raise ValueError(f"invalid payload size at sequence {tx.sequence}")
    return suffix[2:2+size]

def u16(tx: Transaction, signed: bool=False) -> list[int]:
    data=payload(tx)
    if len(data)%2: raise ValueError(f"odd u16 payload at sequence {tx.sequence}")
    return [int.from_bytes(data[i:i+2],"little",signed=signed) for i in range(0,len(data),2)]

def scalar(tx: Transaction) -> int:
    data=payload(tx)
    if len(data)==1:return data[0]
    if len(data)==2:return int.from_bytes(data,"little")
    raise ValueError(f"non-scalar payload at sequence {tx.sequence}")

def status_count(tx: Transaction) -> int:
    data=payload(tx)
    if len(data)!=14: raise ValueError(f"unexpected compact status length at sequence {tx.sequence}")
    return data[13]

def packed(tx: Transaction) -> dict:
    return {"sequence":tx.sequence,"sourceWriteLine":tx.request_index,"request":tx.request_hex,"response":tx.response_hex}

def last_before(rows, sequence, request, predicate=lambda _:True):
    matches=[tx for tx in rows if tx.sequence<sequence and tx.request_hex==request and predicate(tx)]
    if not matches: raise ValueError(f"no {request} before {sequence}")
    return matches[-1]

def first_after(rows, sequence, request, predicate=lambda _:True):
    matches=[tx for tx in rows if tx.sequence>sequence and tx.request_hex==request and predicate(tx)]
    if not matches: raise ValueError(f"no {request} after {sequence}")
    return matches[0]

def build(source: Path) -> dict:
    if sha256(source)!=RAW_SHA256: raise ValueError("source SHA-256 does not match canonical PortmonLOGNOVO.LOG")
    rows=list(parse(source))
    reset_rows=[tx for tx in rows if tx.request_hex==REQ["RESET_ALL"]]
    if len(reset_rows)!=1: raise ValueError(f"expected exactly one reset-all frame, got {len(reset_rows)}")
    reset=reset_rows[0]
    mul_rows=[tx for tx in rows if tx.sequence>reset.sequence and tx.request_hex==REQ["MUL"]]
    transitions=[]; previous=None
    for tx in mul_rows:
        if previous is not None and u16(tx)!=u16(previous): transitions.append((previous,tx))
        previous=tx
    if len(transitions)!=3: raise ValueError(f"expected three post-reset MUL transitions, got {len(transitions)}")
    epochs=[]
    for epoch_index,(mul_before,mul_after) in enumerate(transitions,start=1):
        before_count,after_count=epoch_index-1,epoch_index
        status_before=last_before(rows,mul_after.sequence,REQ["STATUS"],lambda tx:status_count(tx)==before_count)
        status_after=first_after(rows,status_before.sequence,REQ["STATUS"],lambda tx:status_count(tx)==after_count)
        count_after=first_after(rows,mul_after.sequence,REQ["COUNT"],lambda tx:scalar(tx)==after_count)
        gprev_reads=[tx for tx in rows if tx.sequence<mul_after.sequence and tx.request_hex==REQ["GPREV_T"]]
        rollover_t=None
        for left,right in zip(gprev_reads,gprev_reads[1:]):
            if right.sequence>mul_before.sequence and u16(left)!=u16(right): rollover_t=right
        if rollover_t is None: raise ValueError(f"no GPREV rollover for epoch {epoch_index}")
        rollover_m=first_after(rows,rollover_t.sequence-1,REQ["GPREV_M"])
        current_before_t=last_before(rows,rollover_t.sequence,REQ["GCUR_T"],lambda tx:any(u16(tx)))
        current_before_m=last_before(rows,rollover_t.sequence,REQ["GCUR_M"],lambda tx:any(u16(tx,signed=True)))
        counters_before=last_before(rows,rollover_t.sequence,REQ["NGAS"],lambda tx:any(u16(tx)))
        current_after_t=first_after(rows,rollover_t.sequence,REQ["GCUR_T"])
        current_after_m=first_after(rows,rollover_t.sequence,REQ["GCUR_M"])
        counters_after=first_after(rows,rollover_t.sequence,REQ["NGAS"])
        epochs.append({
            "epoch":epoch_index,"autoMatchCountBefore":before_count,"autoMatchCountAfter":after_count,
            "statusBefore":packed(status_before),
            "gasCurrentBefore":{"time":packed(current_before_t),"map":packed(current_before_m),"counter":packed(counters_before)},
            "mulBefore":packed(mul_before),"statusAfter":packed(status_after),
            "petrolContext":{
                "time":packed(last_before(rows,mul_after.sequence,REQ["PETR_T"])),
                "map":packed(last_before(rows,mul_after.sequence,REQ["PETR_M"])),
                "counter":packed(last_before(rows,mul_after.sequence,REQ["NPETR"])),
            },
            "gasPreviousAfterRollover":{"time":packed(rollover_t),"map":packed(rollover_m)},
            "gasCurrentAfterRollover":{"time":packed(current_after_t),"map":packed(current_after_m),"counter":packed(counters_after)},
            "mulAfter":packed(mul_after),"explicitCountAfter":packed(count_after),
        })
    first_epoch_seq=transitions[0][1].sequence
    static={key:packed(last_before(rows,first_epoch_seq,REQ[key])) for key in ("AXIS","PRESS","MAX","ENABLE","CAL","COUNT")}
    ee_pre={}; ee_base={}
    for key in ("MUL_ACT_EE","MUL_PREV_EE","MUL_UPD_CALL_CNTR_EE"):
        ee_pre[key]=packed(last_before(rows,reset.sequence,REQ[key]))
        ee_base[key]=packed(last_before(rows,first_epoch_seq,REQ[key]))
    return {
        "schema":"omegas.mp48.lognovo-autocal-epochs.v1","classification":"ORIGINAL_DERIVED",
        "description":"Exact original-byte brackets for the three ECU-native AutoMatch epochs after Reset All in PortmonLOGNOVO.LOG.",
        "provenance":{
            "sourceFamily":"Lognovo original / ProgBase original","sourceRawName":source.name,
            "sourceRawBytes":source.stat().st_size,"sourceRawSha256":RAW_SHA256,
            "sourceZipName":"PortmonLOGNOVO (1)(2).zip","sourceZipSha256":ZIP_SHA256,
            "progBaseName":"Copy of ProgBase (3).exe","progBaseVersion":"4.2.0.6","progBaseSha256":PROGBASE_SHA256,
            "extractor":"tools/omegas/extract_lognovo_autocal_epochs.py",
            "scientificUse":"ECU-native AutoMatch epoch/rollover/MUL behavior. No OMEGAS-generated values are an oracle.",
            "manualAutoMatchFrame":REQ["MANUAL_AUTOMATCH"],
            "manualAutoMatchFrameCount":sum(tx.request_hex==REQ["MANUAL_AUTOMATCH"] for tx in rows),
            "resetAllFrameCount":len(reset_rows),
        },
        "resetAll":packed(reset),"staticConfigurationBeforeEpochs":static,
        "eepromEvidence":{
            "preReset":ee_pre,"postResetBaseline":ee_base,
            "note":"MUL_ACT_EE, MUL_PREV_EE and MUL_UPD_CALL_CNTR_EE prove persistent multiplier history/call-count state; exact firmware update formula remains unresolved.",
        },
        "epochs":epochs,
    }

def main() -> int:
    parser=argparse.ArgumentParser(); parser.add_argument("input",type=Path); parser.add_argument("output",type=Path)
    args=parser.parse_args(); result=build(args.input); args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({"output":str(args.output),"epochs":len(result["epochs"])},indent=2)); return 0

if __name__=="__main__": raise SystemExit(main())
