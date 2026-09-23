#!/usr/bin/env python3
from __future__ import annotations

import copy
import json
from pathlib import Path

EXPECTED_UI_CONTRACTS={"OME-STATE-HUMAN-UI","UIUX-CUSTOMROM","UIUX-OMEGADEV"}


def _get_bool(container,key,violations,code,expected=True):
    if key not in container or bool(container.get(key)) is not expected:
        violations.append(code)


def audit(receipt: dict) -> dict:
    violations=[]

    if receipt.get("science_authority")!="OmegasAtlas":
        violations.append("PRODUCT_CANNOT_OVERRIDE_ATLAS_SCIENCE")

    roles=receipt.get("branch_roles",{})
    if roles.get("OmegasAtlas")!="SCIENTIFIC_AUTHORITY":
        violations.append("ATLAS_ROLE_NOT_CANONICAL")
    if roles.get("work/omegas-amarelo-foundation")!="REPLAY_RUNTIME_CHALLENGER":
        violations.append("AMARELO_ROLE_INVALID")
    if roles.get("OmegasVerde")!="PRODUCT_CONSUMER_AND_LEAD_SOURCE":
        violations.append("VERDE_ROLE_INVALID")

    governance=receipt.get("governance",{})
    classified=set(governance.get("classified_ui_contracts",[]))
    if not EXPECTED_UI_CONTRACTS.issubset(classified):
        violations.append("UIUX_BINDINGS_NOT_CLASSIFIED")
    if governance.get("transversal_gate")!="docs/contracts/transversal-pass-fail-gate.json":
        violations.append("TRANSVERSAL_GATE_NOT_BOUND")

    ui=receipt.get("ui",{})
    _get_bool(ui,"human_intent_first",violations,"HUMAN_INTENT_NOT_PRIMARY")
    _get_bool(ui,"projects_typed_state_only",violations,"UI_NOT_TYPED_STATE_PROJECTION")
    if ui.get("derives_scientific_truth") is not False:
        violations.append("UI_PARALLEL_SCIENCE_AUTHORITY")
    _get_bool(ui,"technical_detail_on_demand",violations,"TECHNICAL_DETAIL_ALWAYS_EXPOSED")
    _get_bool(ui,"normality_compact_problem_expands",violations,"PROBLEM_STATE_NOT_HIERARCHICAL")
    _get_bool(ui,"failure_recovery_visible",violations,"FAILURE_RECOVERY_NOT_VISIBLE")
    _get_bool(ui,"reversible_actions_show_rollback",violations,"ROLLBACK_NOT_VISIBLE")
    _get_bool(ui,"stable_intent_ids",violations,"MATERIAL_ACTION_WITHOUT_STABLE_INTENT")
    if ui.get("silent_clamp") is not False:
        violations.append("SILENT_CLAMP")
    if ui.get("fake_progress_percent") is not False:
        violations.append("FAKE_PROGRESS_PERCENT")

    runtime=receipt.get("runtime",{})
    if runtime.get("screen_lifecycle_owns_serial_io") is not False:
        violations.append("SCREEN_LIFECYCLE_OWNS_SERIAL_IO")
    if runtime.get("route_churn_changes_science") is not False:
        violations.append("ROUTE_CHURN_CHANGES_SCIENCE")
    _get_bool(runtime,"single_store_router_scheduler",violations,"DUPLICATE_UI_RUNTIME_AUTHORITY")

    replay=receipt.get("replay",{})
    _get_bool(replay,"canonical_bytes_verified",violations,"REPLAY_BYTES_NOT_CANONICAL")
    _get_bool(replay,"canonical_timing_verified",violations,"REPLAY_TIMING_NOT_CANONICAL")
    if replay.get("semantic_authority") is not False:
        violations.append("REPLAY_PROMOTED_TO_SEMANTIC_ORACLE")

    writes=receipt.get("writes",{})
    _get_bool(writes,"preview_before_commit",violations,"WRITE_WITHOUT_PREVIEW")
    _get_bool(writes,"ack_readback_required",violations,"WRITE_WITHOUT_ACK_READBACK")
    _get_bool(writes,"recovery_defined",violations,"WRITE_WITHOUT_RECOVERY")
    _get_bool(writes,"single_human_commit_point",violations,"WRITE_HUMAN_COMMIT_AMBIGUOUS")

    return {
        "schema":"omegas.atlas.product-handoff-audit.v1",
        "status":"PASS" if not violations else "FAIL",
        "violations":sorted(set(violations)),
        "rule":"Atlas science is immutable to consumer claims; runtime and UI/UX are downstream compatibility gates."
    }


def example_receipt() -> dict:
    return {
        "science_authority":"OmegasAtlas",
        "branch_roles":{
            "OmegasAtlas":"SCIENTIFIC_AUTHORITY",
            "work/omegas-amarelo-foundation":"REPLAY_RUNTIME_CHALLENGER",
            "OmegasVerde":"PRODUCT_CONSUMER_AND_LEAD_SOURCE",
        },
        "governance":{
            "transversal_gate":"docs/contracts/transversal-pass-fail-gate.json",
            "classified_ui_contracts":["OME-STATE-HUMAN-UI","UIUX-CUSTOMROM","UIUX-OMEGADEV"],
        },
        "ui":{
            "human_intent_first":True,
            "projects_typed_state_only":True,
            "derives_scientific_truth":False,
            "technical_detail_on_demand":True,
            "normality_compact_problem_expands":True,
            "failure_recovery_visible":True,
            "reversible_actions_show_rollback":True,
            "stable_intent_ids":True,
            "silent_clamp":False,
            "fake_progress_percent":False,
        },
        "runtime":{
            "screen_lifecycle_owns_serial_io":False,
            "route_churn_changes_science":False,
            "single_store_router_scheduler":True,
        },
        "replay":{
            "canonical_bytes_verified":True,
            "canonical_timing_verified":True,
            "semantic_authority":False,
        },
        "writes":{
            "preview_before_commit":True,
            "ack_readback_required":True,
            "recovery_defined":True,
            "single_human_commit_point":True,
        },
    }


def main():
    import argparse
    ap=argparse.ArgumentParser()
    ap.add_argument("receipt",type=Path)
    ap.add_argument("--out",type=Path)
    args=ap.parse_args()
    result=audit(json.loads(args.receipt.read_text(encoding="utf-8")))
    payload=json.dumps(result,indent=2,ensure_ascii=False)+"\n"
    if args.out:
        args.out.parent.mkdir(parents=True,exist_ok=True)
        args.out.write_text(payload,encoding="utf-8")
    print(payload,end="")
    raise SystemExit(0 if result["status"]=="PASS" else 2)


if __name__=="__main__":
    main()
