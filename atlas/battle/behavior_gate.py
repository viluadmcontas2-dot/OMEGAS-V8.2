#!/usr/bin/env python3
from __future__ import annotations
import json
from pathlib import Path

CANONICAL_BINARY_SHA256="8a2d297c8c21ff3b4f7a47f7fe64593b0fec9014dd938bd91022dc0c68ac36f4"

def evaluate(root: Path) -> dict:
    manifest_path=root/"atlas/manifests/behavior-closure.json"
    manifest=json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("binary_sha256","").lower()!=CANONICAL_BINARY_SHA256:
        raise RuntimeError("behavior manifest canonical binary hash mismatch")
    rows=[]
    for gate in manifest.get("gates",[]):
        proof_path=root/gate["proof"]
        status="OPEN"
        reason="proof file missing"
        proof=None
        if proof_path.is_file():
            try:
                proof=json.loads(proof_path.read_text(encoding="utf-8"))
                if proof.get("gate_id")!=gate["id"]:
                    reason="gate_id mismatch"
                elif proof.get("binary_sha256","").lower()!=CANONICAL_BINARY_SHA256:
                    reason="binary hash mismatch"
                elif proof.get("status")!="PROVEN":
                    reason=f"proof status={proof.get('status')!r}"
                elif not proof.get("evidence"):
                    reason="proof has no evidence"
                else:
                    status="PROVEN";reason=""
            except Exception as exc:
                reason=f"invalid proof: {type(exc).__name__}: {exc}"
        rows.append({
            "id":gate["id"],
            "category":gate["category"],
            "required":bool(gate.get("required",True)),
            "proof":gate["proof"],
            "status":status,
            "reason":reason,
        })
    required=[x for x in rows if x["required"]]
    proven=[x for x in required if x["status"]=="PROVEN"]
    return {
        "schema":"omegas.atlas.behavior-closure-result.v1",
        "complete":len(required)>0 and len(proven)==len(required),
        "required":len(required),
        "proven":len(proven),
        "open":len(required)-len(proven),
        "gates":rows,
    }

if __name__=="__main__":
    root=Path(__file__).resolve().parents[2]
    print(json.dumps(evaluate(root),indent=2))
