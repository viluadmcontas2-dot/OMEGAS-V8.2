import json
from pathlib import Path
from statistics import median

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tests" / "fixtures" / "portmon-autocal-cycle-v1.json"
LEGACY = ROOT / "tests" / "fixtures" / "portmon-autocal-real-sample.json"

assert FIXTURE.is_file(), "corpus compacto real do ProgBase/Portmon deve existir"

fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
legacy = json.loads(LEGACY.read_text(encoding="utf-8"))

assert fixture["schema"] == "omegas.mp48.portmon-replay.v1"
assert fixture["sourceRawSha256"] == legacy["originalSha256"]
assert fixture["sourceRawSha256"] == "4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b"
assert fixture["observedWriteCount"] >= 36000
assert fixture["observedUniqueCommands"] == 21
assert len(fixture["transactions"]) >= 200

counts = fixture["commandCounts"]
assert counts["48 01 49"] >= 21000
for command in (
    "29 5B 01 85", "29 5C 01 86", "29 5D 01 87", "29 5E 01 88",
    "29 5F 01 89", "29 60 01 8A", "29 61 01 8B", "29 62 01 8C",
    "29 63 01 8D", "29 6F 01 99", "29 70 01 9A", "29 8D 01 B7",
    "29 8E 01 B8", "48 0B 53",
):
    assert counts[command] > 0, f"comando real AutoCal ausente: {command}"

represented = {row["request"] for row in fixture["transactions"]}
assert set(counts).issubset(represented), "fixture compacta deve representar todos os comandos observados"

for row in fixture["transactions"]:
    assert row["response"], f"resposta real ausente na sequência {row['sequence']}"
    assert row["response"].upper().startswith(row["request"].upper() + " "), (
        f"resposta não ecoa request na sequência {row['sequence']}"
    )

live = [row["at_ms"] for row in fixture["transactions"][:180] if row["request"] == "48 01 49"]
deltas = [b - a for a, b in zip(live, live[1:]) if 0 < (b - a) < 200]
assert len(deltas) >= 80
assert 35.0 <= median(deltas) <= 65.0, (
    f"cadência viva derivada do ProgBase inesperada: mediana={median(deltas):.2f} ms"
)

print("REAL_MP48_REPLAY_FIXTURE_CONTRACT=PASS")
