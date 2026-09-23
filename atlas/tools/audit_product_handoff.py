#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path


def example_receipt() -> dict:
    return {
        "schema": "omegas.atlas.product-handoff-receipt.v1",
        "science_authority": "OmegasAtlas",
        "atlas": {
            "status": "CLOSED",
            "semantic": "1417/1417",
            "behavior": "14/14",
        },
        "consumer_runtime": {
            "canonical_replay_status": "PROVEN",
            "semantic_drift_conflicts": 0,
        },
        "ui": {
            "derives_scientific_truth": False,
            "typed_state_only": True,
            "technical_detail_on_demand": True,
            "visible_failure_and_recovery": True,
        },
        "runtime": {
            "route_churn_changes_science": False,
            "screen_lifecycle_owns_serial_io": False,
            "single_store_router_scheduler": True,
        },
        "writes": {
            "preview_before_commit": True,
            "ack_readback_required": True,
            "recovery_defined": True,
            "stable_intent_id": True,
        },
    }


def audit(receipt: dict) -> dict:
    violations: list[str] = []

    if receipt.get("science_authority") != "OmegasAtlas":
        violations.append("PRODUCT_CANNOT_OVERRIDE_ATLAS_SCIENCE")

    atlas = receipt.get("atlas", {})
    if (
        atlas.get("status") != "CLOSED"
        or atlas.get("semantic") != "1417/1417"
        or atlas.get("behavior") != "14/14"
    ):
        violations.append("ATLAS_NATIVE_CLOSURE_NOT_CLOSED")

    consumer = receipt.get("consumer_runtime", {})
    if consumer.get("canonical_replay_status") != "PROVEN":
        violations.append("CANONICAL_REPLAY_NOT_PROVEN")
    if int(consumer.get("semantic_drift_conflicts", 0) or 0) != 0:
        violations.append("UNRESOLVED_CONSUMER_SEMANTIC_DRIFT")

    ui = receipt.get("ui", {})
    if bool(ui.get("derives_scientific_truth")):
        violations.append("UI_PARALLEL_SCIENCE_AUTHORITY")

    runtime = receipt.get("runtime", {})
    if bool(runtime.get("route_churn_changes_science")):
        violations.append("ROUTE_CHURN_CHANGES_SCIENCE")
    if bool(runtime.get("screen_lifecycle_owns_serial_io")):
        violations.append("SCREEN_LIFECYCLE_OWNS_SERIAL_IO")

    writes = receipt.get("writes", {})
    if not bool(writes.get("preview_before_commit")):
        violations.append("WRITE_WITHOUT_PREVIEW")
    if not bool(writes.get("ack_readback_required")):
        violations.append("WRITE_WITHOUT_ACK_READBACK")
    if not bool(writes.get("recovery_defined")):
        violations.append("WRITE_WITHOUT_RECOVERY")

    return {
        "schema": "omegas.atlas.product-handoff-audit.v1",
        "status": "PASS" if not violations else "FAIL",
        "violations": violations,
        "science_authority": receipt.get("science_authority"),
        "rule": (
            "Atlas scientific closure is immutable authority for native semantics; "
            "consumer runtime and UI may only project or implement that truth."
        ),
    }


def current_repo_receipt(root: Path) -> dict:
    closure=json.loads((root/"atlas/reports/final-closure.json").read_text(encoding="utf-8"))
    replay=json.loads((root/"atlas/proofs/consumer/amarelo-lognovo-replay.json").read_text(encoding="utf-8"))
    drift=json.loads((root/"atlas/proofs/consumer/verde-action-map-drift.json").read_text(encoding="utf-8"))
    receipt=example_receipt()
    receipt["atlas"]={
        "status":closure["status"],
        "semantic":f'{closure["closure"]["proven"]}/{closure["closure"]["targets_total"]}',
        "behavior":f'{closure["closure"]["behavior_gates_proven"]}/{closure["closure"]["behavior_gates_required"]}',
    }
    receipt["consumer_runtime"]={
        "canonical_replay_status":replay["status"],
        "semantic_drift_conflicts":len(drift.get("conflicts",[])),
    }
    return receipt


def main() -> None:
    ap=argparse.ArgumentParser()
    ap.add_argument("--receipt",type=Path)
    ap.add_argument("--current-repo",action="store_true")
    ap.add_argument("--out",type=Path)
    args=ap.parse_args()

    if args.current_repo:
        root=Path(__file__).resolve().parents[2]
        receipt=current_repo_receipt(root)
    elif args.receipt:
        receipt=json.loads(args.receipt.read_text(encoding="utf-8"))
    else:
        receipt=example_receipt()

    result=audit(receipt)
    rendered=json.dumps({"receipt":receipt,"audit":result},indent=2,ensure_ascii=False)+"\n"
    if args.out:
        args.out.parent.mkdir(parents=True,exist_ok=True)
        args.out.write_text(rendered,encoding="utf-8")
    print(rendered,end="")


if __name__=="__main__":
    main()
