import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AUTOCAL = ROOT / "tests" / "fixtures" / "portmon-autocal-cycle-v1.json"
LOGNOVO = ROOT / "tests" / "fixtures" / "portmon-lognovo-autocal-control-v1.json"

def checksum(*values: int) -> int:
    return sum(values) & 0xFF

def action_request(code: int) -> str:
    body = [0x02, 0x24, 0x04, code]
    return " ".join(f"{value:02X}" for value in [*body, checksum(*body)])

autocal = json.loads(AUTOCAL.read_text(encoding="utf-8"))
lognovo = json.loads(LOGNOVO.read_text(encoding="utf-8"))

assert autocal["sourceRawSha256"] == "4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b"
assert lognovo["sourceRawSha256"] == "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64"

# Binary-derived frame forms. Only action 4 is capture-proven in current corpus.
assert action_request(1) == "02 24 04 01 2B"
assert action_request(2) == "02 24 04 02 2C"
assert action_request(4) == "02 24 04 04 2E"
assert action_request(8) == "02 24 04 08 32"

assert autocal["commandCounts"].get(action_request(4)) == 1
assert action_request(1) not in autocal["commandCounts"]
assert action_request(2) not in autocal["commandCounts"]
assert action_request(8) not in autocal["commandCounts"]

reset = lognovo["evidence"]["RESET_ALL_ACTION"]
assert len(reset) == 1
assert reset[0]["request"] == action_request(4)
assert reset[0]["response"] == "02 24 04 04 2E 53 00 53"

# Native enable/disable and RPM constraint are byte-exact LOGNOVO facts.
assert lognovo["counts"]["ENABLE_AUTOCAL_0"] == 4
assert lognovo["counts"]["ENABLE_AUTOCAL_1"] == 4
assert lognovo["counts"]["READ_MAX_RPM_AUTOCAL"] == 3
for row in lognovo["evidence"]["READ_MAX_RPM_AUTOCAL"]:
    assert "53 02 B8 0B" in row["response"]  # little-endian 0x0BB8 = 3000 rpm

counts = []
for row in lognovo["evidence"]["READ_AUTOMATCH_COUNT"]:
    raw = bytes.fromhex(row["response"])
    # response = echoed request (4) + 53 + length(1) + value + checksum
    counts.append(raw[-2])
assert {0, 1, 2, 3}.issubset(set(counts))

# Connection negotiation is kept distinct from AutoCal action classification.
assert lognovo["counts"]["HANDSHAKE_3A"] == 4
assert lognovo["counts"]["HANDSHAKE_25"] == 4
assert lognovo["selectors"]["HANDSHAKE_3A"] != lognovo["selectors"]["RESET_ALL_ACTION"]

print("AMARELO_AUTOCAL_ACTION_BRIDGE_CONTRACT=PASS")
