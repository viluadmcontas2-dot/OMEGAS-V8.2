#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

EXPECTED = {
    "MANUAL_AUTOMATCH": {"handler": "ActionAutoMatchExecute", "handlerVa": "0x005189B4", "mode": "0x08", "frame": "02 24 04 08 32"},
    "RESET_PETROL": {"handler": "ActionResetPetrolExecute", "handlerVa": "0x005189C0", "mode": "0x01", "frame": "02 24 04 01 2B"},
    "RESET_GAS": {"handler": "ActionResetGasExecute", "handlerVa": "0x005189CC", "mode": "0x02", "frame": "02 24 04 02 2C"},
    "RESET_ALL": {"handler": "ActionResetAllExecute", "handlerVa": "0x005189D8", "mode": "0x04", "frame": "02 24 04 04 2E"},
}


def audit(consumer: dict) -> dict:
    actual = {row.get("action"): row for row in consumer.get("actions", [])}
    separate = consumer.get("separateActions", {})
    conflicts = []
    matches = []

    for action, expected in EXPECTED.items():
        row = actual.get(action)
        if row is None:
            conflicts.append({"action": action, "reason": "MISSING", "expected": expected})
            continue
        material = {k: row.get(k) for k in ("handler", "handlerVa", "mode", "frame")}
        if material == expected:
            matches.append(action)
        else:
            conflicts.append({"action": action, "reason": "MISMATCH", "expected": expected, "actual": material})

    modify = separate.get("modifyMapRefs", {})
    expected_modify = {
        "handler": "ActionAutoCalRifExecute",
        "handlerVa": "0x005187A0",
        "mode": None,
        "frame": None,
    }
    material_modify = {k: modify.get(k) for k in ("handler", "handlerVa", "mode", "frame")}
    if material_modify == expected_modify:
        matches.append("MODIFY_MAP_REFS")
    else:
        conflicts.append({
            "action": "MODIFY_MAP_REFS",
            "reason": "MISMATCH",
            "expected": expected_modify,
            "actual": material_modify,
        })

    return {
        "schema": "omegas.atlas.consumer-action-drift.v1",
        "status": "CONVERGED" if not conflicts else "DRIFT_DETECTED",
        "matches": matches,
        "conflicts": conflicts,
        "rule": "Canonical EXE/log evidence remains authoritative; consumer fixtures are never promoted over Atlas proof.",
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--consumer-oracle", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    result = audit(json.loads(args.consumer_oracle.read_text(encoding="utf-8")))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
