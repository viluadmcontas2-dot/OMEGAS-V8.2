#!/usr/bin/env python3
"""Relatório passivo das fronteiras de sessão no PortmonLOGNOVO.LOG.

Responde, só com a fiação real do ProgBase, às perguntas do incidente
`docs/incidents/2026-10-03-curva-k-perde-efeito-ao-pausar-ou-desconectar.md`:

1. que frames não-leitura o ProgBase envia (sessão, AutoCal, Mapa K, eixos,
   calibração clássica) e em que sequência;
2. o que acontece em volta de cada `12 4A 01 xx` (Enable/Disable AutoCal);
3. o que acontece em volta de cada desconexão (`01 12 00` + `00 01 01`);
4. se `MUL_ACT` permanece após `Reset All` e através das desconexões;
5. como o Mapa K lido muda entre as três leituras e quantas calibrações
   clássicas (`00 13`) ocorreram entre elas.

Nunca abre porta serial, USB ou rede. Entrada: o LOG do Portmon (ou .gz).
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from scripts.omegas.portmon_parser import parse  # noqa: E402

POLLS = {"48 01 49", "48 08 50", "29 1D 00 46", "09 21 00 2A", "00 14 14"}
READ_OPCODES = {"09", "0A", "29", "2A", "48"}
NEUTRAL_MUL_ACT = "53 3C " + " ".join(["00 40"] * 30)


def payload(transaction) -> str:
    return transaction.response_hex[len(transaction.request_hex) + 1 :]


def context(by_sequence, center: int, before: int, after: int):
    rows = []
    for sequence in range(center - before, center + after + 1):
        transaction = by_sequence.get(sequence)
        if transaction is None or transaction.request_hex in POLLS:
            continue
        rows.append(
            {
                "seq": sequence,
                "request": transaction.request_hex,
                "response": payload(transaction)[:48],
                "durationS": round(transaction.response_duration, 4),
            }
        )
    return rows


def build(log_path: Path) -> dict:
    transactions = list(parse(log_path))
    by_sequence = {item.sequence: item for item in transactions}

    commands = []
    for item in transactions:
        opcode = item.request_hex[:2]
        if opcode in READ_OPCODES or item.request_hex in POLLS:
            continue
        if item.request_hex.startswith(("14 54 00", "14 3D 00", "14 37 00")):
            continue  # blocos de escrita do Mapa K/eixos: contados abaixo
        commands.append({"seq": item.sequence, "request": item.request_hex, "response": payload(item)})

    map_cell_writes = sum(1 for item in transactions if item.request_hex.startswith("14 54 00"))
    axis_writes = sum(1 for item in transactions if item.request_hex.startswith(("14 3D 00", "14 37 00")))

    toggles = [item.sequence for item in transactions if item.request_hex.startswith("12 4A 01")]
    disconnects = [item.sequence for item in transactions if item.request_hex == "00 01 01"]
    end_programming = [item.sequence for item in transactions if item.request_hex == "01 12 00 13"]

    mul_act = [(item.sequence, payload(item)) for item in transactions if item.request_hex == "29 61 01 8B"]
    reset_all = [item.sequence for item in transactions if item.request_hex == "02 24 04 04 2E"]
    first_reset = reset_all[0] if reset_all else None
    after_reset = [entry for entry in mul_act if first_reset is not None and entry[0] > first_reset]
    non_neutral_after_reset = [seq for seq, value in after_reset if not value.startswith(NEUTRAL_MUL_ACT)]

    enable_reads = [
        {"seq": item.sequence, "value": payload(item)}
        for item in transactions
        if item.request_hex.startswith("09 4A 01")
    ]

    map_reads = defaultdict(dict)
    for item in transactions:
        if item.request_hex.startswith("2A 54 00"):
            row = int(item.request_hex.split()[3], 16)
            group = item.sequence - row
            map_reads[group][row] = payload(item)[6:].split()[:12]
    map_groups = []
    for group in sorted(map_reads):
        rows = map_reads[group]
        map_groups.append(
            {
                "firstSeq": group,
                "row00": rows.get(0),
                "row0C": rows.get(12),
                "distinctWritableValues": sorted({value for row, cells in rows.items() if row < 12 for value in cells}),
            }
        )

    classic_starts = [item.sequence for item in transactions if item.request_hex == "00 13 13"]
    boundaries = [group["firstSeq"] for group in map_groups]
    classic_between = []
    for start, end in zip(boundaries, boundaries[1:]):
        classic_between.append(
            {
                "fromSeq": start,
                "toSeq": end,
                "classicCalibrationStarts": [seq for seq in classic_starts if start < seq < end],
                "mapCellWrites": sum(
                    1 for item in transactions if start < item.sequence < end and item.request_hex.startswith("14 54 00")
                ),
            }
        )

    return {
        "schema": "omegas-portmon-session-boundary-report-v1",
        "source": {"file": log_path.name, "transactions": len(transactions)},
        "nonReadCommands": commands,
        "mapCellWrites": map_cell_writes,
        "axisWrites": axis_writes,
        "autoCalEnableWrites": {seq: context(by_sequence, seq, 6, 6) for seq in toggles},
        "disconnects": {seq: context(by_sequence, seq, 3, 4) for seq in disconnects},
        "endProgrammingFrames": end_programming,
        "autoCalEnableReads": enable_reads,
        "mulAct": {
            "reads": len(mul_act),
            "firstResetAllSeq": first_reset,
            "readsAfterResetAll": len(after_reset),
            "nonNeutralAfterResetAll": non_neutral_after_reset,
        },
        "mapKReads": map_groups,
        "classicCalibrationBetweenMapReads": classic_between,
        "requestCounter": dict(Counter(item.request_hex for item in transactions).most_common(8)),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("log", type=Path, help="PortmonLOGNOVO.LOG (ou .gz)")
    parser.add_argument("--json", action="store_true", help="imprime o relatório inteiro em JSON")
    args = parser.parse_args()
    report = build(args.log)
    if args.json:
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return 0
    print(f"transações: {report['source']['transactions']}")
    print(f"escritas de célula do Mapa K: {report['mapCellWrites']} • escritas de eixo: {report['axisWrites']}")
    print("frames não-leitura (fora dos blocos de Mapa K/eixos):")
    for command in report["nonReadCommands"]:
        print(f"  seq {command['seq']:6d}  {command['request']:28s} -> {command['response']}")
    print(f"01 12 00 antes de desconectar: seqs {report['endProgrammingFrames']}")
    print(f"leituras AUTO_CAL_ENABLE: {report['autoCalEnableReads']}")
    mul = report["mulAct"]
    print(
        "MUL_ACT: "
        f"{mul['reads']} leituras; Reset All em {mul['firstResetAllSeq']}; "
        f"{mul['readsAfterResetAll']} leituras depois; não neutras depois: {mul['nonNeutralAfterResetAll']}"
    )
    for group in report["mapKReads"]:
        print(f"Mapa K seq {group['firstSeq']}: linha 00 {group['row00']} • linha 0C {group['row0C']}")
    for span in report["classicCalibrationBetweenMapReads"]:
        print(
            f"entre {span['fromSeq']} e {span['toSeq']}: calibrações clássicas 00 13 = "
            f"{span['classicCalibrationStarts']} • escritas 14 54 00 = {span['mapCellWrites']}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
