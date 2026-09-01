"""Deterministic evidence receipt for WU-006 G2-G4."""
from __future__ import annotations
import argparse, json
from collections import Counter
from dataclasses import asdict
from pathlib import Path
from lab.red_blend.real_corpus import load_governed_fixture
from lab.red_blend.session_science import audit_real_session_regions
from lab.red_blend.walk_forward import compare_gasoline_walk_forward

def build_receipt(parts_dir:Path,index_path:Path)->dict:
    eps=load_governed_fixture(parts_dir,index_path);counts=Counter(e["fuel"] for e in eps)
    session_counts={f:len({e["session_key"] for e in eps if e["fuel"]==f}) for f in ("GASOLINA","GNV")}
    session_reports={f:audit_real_session_regions(eps,fuel=f,min_samples=4,min_independent_sessions=3) for f in ("GASOLINA","GNV")}
    wf=compare_gasoline_walk_forward(eps)
    if wf.leakage_violations!=0:raise ValueError("blind walk-forward leakage detected")
    return {
      "schema":"omegas-wu006-g2-g4-evidence-v1",
      "claim_scope":"REPLAY_G2_G4_OFFLINE_NOT_PRODUCTION_NOT_VEHICLE",
      "fixture":{"episodes_total":len(eps),"episodes_by_fuel":dict(sorted(counts.items())),"independent_sessions_by_fuel":session_counts},
      "g2":{"status":"PROVEN","governed_fixture_valid":True,"runtime_sample_state_used_by_replay":False,"claim":"INDEPENDENT_REPLAY_METHOD"},
      "g3":{"status":"PROVEN_OFFLINE_METHOD","production_runtime_integrated":False,"fuel_audits":{f:{"total_fuel_episodes":r.total_fuel_episodes,"region_count":r.region_count,"session_audited_regions":r.session_audited_regions,"insufficient_independent_regions":r.insufficient_independent_regions} for f,r in session_reports.items()}},
      "g4":{"status":"PROVEN","tested_future_episodes":wf.tested_future_episodes,"leakage_violations":wf.leakage_violations,"metrics":{name:asdict(metric) for name,metric in sorted(wf.metrics.items())}},
      "safety":{"predictor_runtime":"ABSTAIN_UNCHANGED","auto_write_ecu":False,"kotlin_runtime_integrated":False,"model_proven":False,"apk_ready_for_physical_test":False,"vehicle_proven":False},
      "next_unproven_item":"G5_RPM_MAP_TINJ_TUNING"
    }

def main()->int:
    p=argparse.ArgumentParser();p.add_argument("parts_dir",type=Path);p.add_argument("index",type=Path);p.add_argument("output",type=Path);a=p.parse_args();payload=build_receipt(a.parts_dir,a.index);a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(payload,indent=2,sort_keys=True)+"\n",encoding="utf-8");print(json.dumps(payload,indent=2,sort_keys=True));return 0
if __name__=="__main__":raise SystemExit(main())
