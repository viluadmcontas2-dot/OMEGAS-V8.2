#!/usr/bin/env python3
"""Replay offline do "Export to K" do ProgBase 4.2.0.6 com dados reais.

Reproduz, sem tocar em porta serial, USB ou ECU, o algoritmo desmontado de
`ActionExportToKExecute` (0x00518514 -> 0x00512624, 0x00512708, 0x0051280C,
0x005127B0) descrito em
`docs/incidents/2026-10-03-curva-k-perde-efeito-ao-pausar-ou-desconectar.md`:

* referência de cada linha do Mapa K em ms = bruto(TEMPI_PER_K) x BASE_TEMPI_GLOBALE x 1e-6;
* a linha só muda se `PETR_INJ_TBP[0] <= x <= PETR_INJ_TBP[ultimo]`;
* fator = interpolação linear de `MUL_ACT` entre os dois pontos do eixo que cercam x;
* cada uma das 12 colunas vira `clamp(trunc(celula x fator), 0, 255)`;
* a rotina termina com `MUL_ACT.ResetDefault(true)`: a curva volta a 1,0.

Nada aqui autoriza escrita em ECU. É a prova offline que falta antes de qualquer
"Consolidar Curva K no Mapa K" no app.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from fractions import Fraction
from pathlib import Path

ROWS = 12
COLS = 12
AXIS_POINTS = 30
Q14 = 16384
AXIS_DIVISOR = 512


def row_reference_ms(raw: int, base: int) -> float:
    """Getter 0x0042EE14: bruto x BASE_TEMPI_GLOBALE x 1e-6."""
    return raw * base * 1e-6


def bracket(axis_ms: list[float], x: float) -> tuple[int, int] | None:
    """0x00512708: None fora de [eixo[0], eixo[-1]]; senão o primeiro par que cerca x."""
    if x < axis_ms[0] or x > axis_ms[-1]:
        return None
    for index in range(len(axis_ms) - 1):
        if axis_ms[index] <= x <= axis_ms[index + 1]:
            return index, index + 1
    return None


def interpolate(x: float, x0: float, x1: float, y0: float, y1: float, *, strict: bool = True) -> float:
    """0x0051280C. O ProgBase devolve 0,0 quando x1 == x0; `strict` recusa em vez de zerar."""
    if x1 == x0:
        if strict:
            raise ValueError("eixo PETR_INJ_TBP com pontos repetidos: interpolação indefinida")
        return 0.0
    return y0 + (y1 - y0) * (x - x0) / (x1 - x0)


def export_to_k(
    kmap: list[list[int]],
    axis_ms: list[float],
    mul_act: list[float],
    row_ms: list[float],
    *,
    strict: bool = True,
) -> dict:
    if len(kmap) < ROWS or any(len(row) < COLS for row in kmap[:ROWS]):
        raise ValueError("Mapa K precisa de ao menos 12 linhas x 12 colunas")
    if len(axis_ms) != AXIS_POINTS or len(mul_act) != AXIS_POINTS:
        raise ValueError("eixo e MUL_ACT precisam de 30 pontos")
    if len(row_ms) != ROWS:
        raise ValueError("12 referências de linha são necessárias")
    new_map = [list(row) for row in kmap]
    rows = []
    changed = low = high = 0
    for r in range(ROWS):
        found = bracket(axis_ms, row_ms[r])
        entry = {"row": r, "refMs": row_ms[r], "factor": None, "skipped": found is None}
        if found is not None:
            lower, upper = found
            factor = interpolate(row_ms[r], axis_ms[lower], axis_ms[upper], mul_act[lower], mul_act[upper], strict=strict)
            entry.update(factor=factor, lower=lower, upper=upper)
            for c in range(COLS):
                raw = math.trunc(kmap[r][c] * factor)
                value = 0 if raw <= 0 else 255 if raw >= 256 else raw
                low += raw <= 0 < kmap[r][c]
                high += raw >= 256
                changed += value != kmap[r][c]
                new_map[r][c] = value
        rows.append(entry)
    return {
        "newMap": new_map,
        "rows": rows,
        "changedCells": changed,
        "saturatedLow": low,
        "saturatedHigh": high,
        "skippedRows": [row["row"] for row in rows if row["skipped"]],
        "curveAfter": [1.0] * AXIS_POINTS,
    }


def export_to_k_exact(
    kmap: list[list[int]],
    axis_raw: list[int],
    mul_raw: list[int],
    row_raw: list[int],
    base: int,
) -> list[list[int]]:
    """Mesma regra em racionais exatos, para medir o risco de ponto flutuante (x87 vs double)."""
    axis = [Fraction(v, AXIS_DIVISOR) for v in axis_raw]
    mul = [Fraction(v, Q14) for v in mul_raw]
    out = [list(row) for row in kmap]
    for r in range(ROWS):
        x = Fraction(row_raw[r] * base, 1_000_000)
        if x < axis[0] or x > axis[-1]:
            continue
        for index in range(len(axis) - 1):
            if axis[index] <= x <= axis[index + 1]:
                factor = mul[index] + (mul[index + 1] - mul[index]) * (x - axis[index]) / (axis[index + 1] - axis[index])
                break
        for c in range(COLS):
            raw = math.trunc(kmap[r][c] * factor)
            out[r][c] = 0 if raw <= 0 else 255 if raw >= 256 else raw
    return out


def _u16_le(tokens: list[str]) -> list[int]:
    return [int(tokens[i], 16) | (int(tokens[i + 1], 16) << 8) for i in range(0, len(tokens) - 1, 2)]


def load_from_portmon(log_path: Path, kmap_group: int = 0, curve_epoch: int = 0) -> dict:
    ROOT = Path(__file__).resolve().parents[2]
    sys.path.insert(0, str(ROOT))
    from scripts.omegas.portmon_parser import parse  # noqa: E402

    transactions = list(parse(log_path))

    def payload(item) -> str:
        return item.response_hex[len(item.request_hex) + 1 :]

    first = lambda prefix: next(item for item in transactions if item.request_hex.startswith(prefix))  # noqa: E731
    base = _u16_le(payload(first("09 79 00")).split()[2:])[0]
    row_raw = _u16_le(payload(first("29 37 00")).split()[2:])
    axis_raw = _u16_le(payload(first("29 4B 01")).split()[2:])
    groups: dict[int, dict[int, list[int]]] = {}
    for item in transactions:
        if item.request_hex.startswith("2A 54 00"):
            row = int(item.request_hex.split()[3], 16)
            groups.setdefault(item.sequence - row, {})[row] = [int(t, 16) for t in payload(item).split()[2:14]]
    group_seqs = sorted(groups)
    epochs, previous = [], None
    for item in transactions:
        if item.request_hex == "29 61 01 8B" and payload(item) != previous:
            previous = payload(item)
            epochs.append((item.sequence, _u16_le(previous.split()[2:])))
    seq, mul_raw = epochs[curve_epoch]
    return {
        "base": base,
        "rowRaw": row_raw,
        "axisRaw": axis_raw,
        "mulRaw": mul_raw,
        "mulSeq": seq,
        "kmapSeq": group_seqs[kmap_group],
        "kmap": [groups[group_seqs[kmap_group]][r] for r in range(13)],
        "kmapGroups": group_seqs,
        "curveEpochs": [e[0] for e in epochs],
    }


def replay(log_path: Path, kmap_group: int, curve_epoch: int) -> dict:
    data = load_from_portmon(log_path, kmap_group, curve_epoch)
    row_ms = [row_reference_ms(v, data["base"]) for v in data["rowRaw"]]
    axis_ms = [v / AXIS_DIVISOR for v in data["axisRaw"]]
    mul = [v / Q14 for v in data["mulRaw"]]
    result = export_to_k(data["kmap"], axis_ms, mul, row_ms)
    exact = export_to_k_exact(data["kmap"], data["axisRaw"], data["mulRaw"], data["rowRaw"], data["base"])
    mismatches = sum(
        1 for r in range(ROWS) for c in range(COLS) if exact[r][c] != result["newMap"][r][c]
    )
    again = export_to_k(result["newMap"], axis_ms, [1.0] * AXIS_POINTS, row_ms)
    return {
        "schema": "omegas-export-to-k-replay-v1",
        "source": {"file": log_path.name, "kmapSeq": data["kmapSeq"], "curveSeq": data["mulSeq"],
                   "kmapGroups": data["kmapGroups"], "curveEpochs": data["curveEpochs"]},
        "baseTempiGlobale": data["base"],
        "rowMs": row_ms,
        "axisMs": axis_ms,
        "curve": mul,
        "before": data["kmap"][:ROWS],
        "after": result["newMap"][:ROWS],
        "row13Untouched": result["newMap"][12] == data["kmap"][12] if len(result["newMap"]) > 12 else None,
        "rows": result["rows"],
        "changedCells": result["changedCells"],
        "saturatedLow": result["saturatedLow"],
        "saturatedHigh": result["saturatedHigh"],
        "skippedRows": result["skippedRows"],
        "floatVsExactMismatches": mismatches,
        "secondExportAfterResetChangesCells": again["changedCells"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("log", type=Path, help="PortmonLOGNOVO.LOG")
    parser.add_argument("--kmap-group", type=int, default=0, help="índice da leitura do Mapa K (0 = mapa original do carro)")
    parser.add_argument("--curve-epoch", type=int, default=0, help="índice da época de MUL_ACT (0 = curva aprendida)")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    report = replay(args.log, args.kmap_group, args.curve_epoch)
    if args.json:
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return 0
    print(f"Mapa K: leitura seq {report['source']['kmapSeq']} • curva: seq {report['source']['curveSeq']} • BASE_TEMPI_GLOBALE {report['baseTempiGlobale']}")
    print("linha  ref(ms)  fator   antes -> depois (primeira e última coluna)")
    for entry, before, after in zip(report["rows"], report["before"], report["after"]):
        factor = "pulada" if entry["skipped"] else f"{entry['factor']:.4f}"
        print(f"{entry['row']:4d}  {entry['refMs']:7.3f}  {factor:>6s}   {before[0]:3d}->{after[0]:3d}   {before[-1]:3d}->{after[-1]:3d}")
    print(f"células alteradas: {report['changedCells']} de 144 • saturadas 0: {report['saturatedLow']} • saturadas 255: {report['saturatedHigh']}")
    print(f"linhas puladas: {report['skippedRows']} • 13ª linha intacta: {report['row13Untouched']}")
    print(f"divergência ponto flutuante x racional exato: {report['floatVsExactMismatches']} células")
    print(f"segundo export com curva em 1,0 altera: {report['secondExportAfterResetChangesCells']} células")
    return 0


if __name__ == "__main__":
    sys.exit(main())
