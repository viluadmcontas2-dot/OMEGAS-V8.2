#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PORTMON = ROOT / "tests/fixtures/portmon-autocal-cycle-v1.json"
CONFIG = ROOT / "tests/fixtures/autocal-forensic-plan-v2.json"

def build_lanes():
    portmon = json.loads(PORTMON.read_text(encoding="utf-8"))
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    lanes = []
    seen = set()

    # High-information workers are deliberately first because GitHub creates
    # matrix jobs in matrix order. Then prioritize rare/secondary protocol
    # transactions before the abundant 48 01 49 live stream.
    for item in config["meta_lanes"]:
        lane = dict(item)
        lane["os"] = "ubuntu-latest"
        lane["sequence"] = 0
        assert lane["id"] not in seen
        seen.add(lane["id"])
        lanes.append(lane)

    ordered_transactions = sorted(
        portmon["transactions"],
        key=lambda tx: (tx["request"] == "48 01 49", float(tx["at_ms"]), int(tx["sequence"])),
    )
    for tx in ordered_transactions:
        seq = int(tx["sequence"])
        lane = {
            "id": f"tx_{seq:06d}",
            "kind": "python",
            "category": "transaction",
            "os": "ubuntu-latest",
            "sequence": seq,
        }
        assert lane["id"] not in seen
        seen.add(lane["id"])
        lanes.append(lane)
    assert len(portmon["transactions"]) == 243, len(portmon["transactions"])
    assert len(config["meta_lanes"]) == 9, len(config["meta_lanes"])
    assert len(lanes) == config["matrix_target"] == 252, len(lanes)
    assert len(lanes) <= config["policy"]["matrix_cap"]
    return lanes

if __name__ == "__main__":
    print(json.dumps({"include": build_lanes()}, separators=(",", ":")))
