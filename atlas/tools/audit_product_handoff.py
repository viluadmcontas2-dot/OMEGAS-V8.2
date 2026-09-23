#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

EXPECTED_UI_CONTRACTS={"OME-STATE-HUMAN-UI","UIUX-CUSTOMROM","UIUX-OMEGADEV"}


def example_receipt() -> dict:
    return {
        "schema":"omegas.atlas.product-handoff-receipt.v1",
        "science_authority":"OmegasAtlas",
        "branch_roles":{
            "OmegasAtlas":"SCIENTIFIC_AUTHORITY",
            "work/omegas-amarelo-foundation":"REPLAY_RUNTIME_CHALLENGER",
            "OmegasVerde":"PRODUCT_CONSUMER_AND_LEAD_SOURCE",
        },
        "atlas":{"status":"CLOSED","semantic":"1417/1417","behavior":"14/14"},
        "consumer_runtime":{
            "canonical_replay_status":"PROVEN",
            "semantic_drift_conflicts":0,
            "semantic_authority":False,
        },
        "governance":{
            "transversal_gate":"docs/contracts/transversal-pass-fail-gate.json",
            "classified_ui_contracts":["OME-STATE-HUMAN-UI","UIUX-CUSTOMROM","UIUX-OMEGADEV"],
            "contract_registry_resolved":True,
            "global_ledger_loaded":True,
            "transversal_status":"PASS",
            "audit_independence_model":"PROVENANCE_BASED",
            "auditor_normative_writes":0,
            "meta_audit_distinct":True,
        },
        "ui":{
            "human_intent_first":True,
            "projects_typed_state_only":True,
            "derives_scientific_truth":False,
            "technical_detail_on_demand":True,
            "normality_compact_problem_expands":True,
            "visible_failure_and_recovery":True,
            "reversible_actions_show_rollback":True,
            "stable_intent_ids":True,
            "silent_clamp":False,
            "fake_progress_percent":False,
        },
        "runtime":{
            "route_churn_changes_science":False,
            "screen_lifecycle_owns_serial_io":False,
            "single_store_router_scheduler":True,
        },
        "writes":{
            "preview_before_commit":True,
            "ack_readback_required":True,
            "recovery_defined":True,
            "single_human_commit_point":True,
        },
    }


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

    atlas=receipt.get("atlas",{})
    if atlas.get("status")!="CLOSED" or atlas.get("semantic")!="1417/1417" or atlas.get("behavior")!="14/14":
        violations.append("ATLAS_NATIVE_CLOSURE_NOT_CLOSED")

    consumer=receipt.get("consumer_runtime",{})
    if consumer.get("canonical_replay_status")!="PROVEN":
        violations.append("CANONICAL_REPLAY_NOT_PROVEN")
    if int(consumer.get("semantic_drift_conflicts",0) or 0)!=0:
        violations.append("UNRESOLVED_CONSUMER_SEMANTIC_DRIFT")
    if consumer.get("semantic_authority") is not False:
        violations.append("REPLAY_PROMOTED_TO_SEMANTIC_ORACLE")

    governance=receipt.get("governance",{})
    if governance.get("transversal_gate")!="docs/contracts/transversal-pass-fail-gate.json":
        violations.append("TRANSVERSAL_GATE_NOT_BOUND")
    if not EXPECTED_UI_CONTRACTS.issubset(set(governance.get("classified_ui_contracts",[]))):
        violations.append("UIUX_BINDINGS_NOT_CLASSIFIED")
    if governance.get("contract_registry_resolved") is not True:
        violations.append("LIVE_CONTRACT_REGISTRY_NOT_RESOLVED")
    if governance.get("global_ledger_loaded") is not True:
        violations.append("GLOBAL_LEDGER_NOT_LOADED")
    if governance.get("transversal_status")!="PASS":
        violations.append("TRANSVERSAL_GATE_NOT_PASS")
    if governance.get("audit_independence_model")!="PROVENANCE_BASED":
        violations.append("AUDIT_NOT_PROVENANCE_INDEPENDENT")
    if governance.get("auditor_normative_writes")!=0:
        violations.append("AUDITOR_MUTATED_NORMATIVE_TARGET")
    if governance.get("meta_audit_distinct") is not True:
        violations.append("META_AUDIT_NOT_DISTINCT")

    ui=receipt.get("ui",{})
    if ui.get("human_intent_first") is not True:
        violations.append("HUMAN_INTENT_NOT_PRIMARY")
    if ui.get("projects_typed_state_only") is not True:
        violations.append("UI_NOT_TYPED_STATE_PROJECTION")
    if ui.get("derives_scientific_truth") is not False:
        violations.append("UI_PARALLEL_SCIENCE_AUTHORITY")
    if ui.get("technical_detail_on_demand") is not True:
        violations.append("TECHNICAL_DETAIL_ALWAYS_EXPOSED")
    if ui.get("normality_compact_problem_expands") is not True:
        violations.append("PROBLEM_STATE_NOT_HIERARCHICAL")
    if ui.get("visible_failure_and_recovery") is not True:
        violations.append("FAILURE_RECOVERY_NOT_VISIBLE")
    if ui.get("reversible_actions_show_rollback") is not True:
        violations.append("ROLLBACK_NOT_VISIBLE")
    if ui.get("stable_intent_ids") is not True:
        violations.append("MATERIAL_ACTION_WITHOUT_STABLE_INTENT")
    if ui.get("silent_clamp") is not False:
        violations.append("SILENT_CLAMP")
    if ui.get("fake_progress_percent") is not False:
        violations.append("FAKE_PROGRESS_PERCENT")

    runtime=receipt.get("runtime",{})
    if runtime.get("route_churn_changes_science") is not False:
        violations.append("ROUTE_CHURN_CHANGES_SCIENCE")
    if runtime.get("screen_lifecycle_owns_serial_io") is not False:
        violations.append("SCREEN_LIFECYCLE_OWNS_SERIAL_IO")
    if runtime.get("single_store_router_scheduler") is not True:
        violations.append("DUPLICATE_UI_RUNTIME_AUTHORITY")

    writes=receipt.get("writes",{})
    if writes.get("preview_before_commit") is not True:
        violations.append("WRITE_WITHOUT_PREVIEW")
    if writes.get("ack_readback_required") is not True:
        violations.append("WRITE_WITHOUT_ACK_READBACK")
    if writes.get("recovery_defined") is not True:
        violations.append("WRITE_WITHOUT_RECOVERY")
    if writes.get("single_human_commit_point") is not True:
        violations.append("WRITE_HUMAN_COMMIT_AMBIGUOUS")

    return {
        "schema":"omegas.atlas.product-handoff-audit.v1",
        "status":"PASS" if not violations else "FAIL",
        "violations":sorted(set(violations)),
        "science_authority":receipt.get("science_authority"),
        "rule":"Atlas scientific closure remains native authority; replay/runtime/product/UI are downstream gates only.",
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
    receipt["consumer_runtime"]["canonical_replay_status"]=replay["status"]
    receipt["consumer_runtime"]["semantic_drift_conflicts"]=len(drift.get("conflicts",[]))
    # Repository files cannot establish live governance/auditor provenance.
    # Those facts must come from the real downstream handoff audit.
    receipt["governance"].update({
        "contract_registry_resolved": False,
        "global_ledger_loaded": False,
        "transversal_status": "NOT_EVALUATED",
        "audit_independence_model": "NOT_EVALUATED",
        "auditor_normative_writes": -1,
        "meta_audit_distinct": False,
    })
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
    raise SystemExit(0 if result["status"]=="PASS" else 2)


if __name__=="__main__":
    main()
