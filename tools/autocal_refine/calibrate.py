#!/usr/bin/env python3
"""Calibração funcional (não estética) das constantes do motor refinado.

λ (rigidez) é escolhido por validação cruzada nas sessões reais: tira-se uma
faixa de GNV madura, refina-se sem ela e mede-se o erro ao prever o K que essa
faixa pede. Uma curva que só "fica bonita" perderia aqui; uma curva que captura
a forma real ganha. E_MAX é validado pelo teste cego de telemetria
(blind_telemetry_test.py): a telemetria em gasolina no mesmo RPM×MAP julga a curva.

    python3 tools/autocal_refine/calibrate.py
"""
import copy
import gzip
import json
import math
from pathlib import Path

import blind_telemetry_test as blind
import refined_oracle as oracle

REAL = Path(__file__).resolve().parents[2] / "fixtures/autocal/real"
CV_CASES = [
    ("ref_2026-10-01_1719", 95),
    ("automatch_2026-10-01_1301", 53),
    ("automatch_2026-10-01_1301", 2262),
    ("gnv_only_2026-09-30_0931", 37),
    ("gnv_only_2026-09-30_0931", 1034),
    ("gnv_only_2026-09-30_0931", 2336),
]


def snapshot(name, sequence):
    with gzip.open(REAL / f"{name}.json.gz", "rt", encoding="utf-8") as handle:
        for snap in json.load(handle)["snapshots"]:
            if snap["sequence"] == sequence:
                return snap
    raise KeyError((name, sequence))


def held_out_error(lam=None, no_correction=False):
    """Erro RMS (fração) ao prever faixas de GNV maduras omitidas."""
    previous = oracle.LAMBDA
    if lam is not None:
        oracle.LAMBDA = lam
    try:
        errors = []
        for name, sequence in CV_CASES:
            snap = snapshot(name, sequence)
            full = oracle.refine(snap)
            for target in full["targets"]:
                if target["w"] < oracle.BAND_MATURE_COUNT / float(oracle.BAND_FULL_COUNT):
                    continue
                reduced = copy.deepcopy(snap)
                fields = {f["key"]: f for f in reduced["fields"]}
                maps = [v / oracle.MAP_COUNTS_PER_BAR for v in fields["MNFLD_PRESS_BUF_GAS"]["rawValues"]]
                band = min(range(oracle.BAND_COUNT), key=lambda i: abs(maps[i] - target["map"]))
                if abs(maps[band] - target["map"]) > 1e-6:
                    continue
                fields["NUM_BUF_UPD_GAS"]["rawValues"][band] = 0
                result = oracle.refine(reduced)
                if result["mode"] != "EQUIVALENCE":
                    continue
                source = full["currentRaw"] if no_correction else result["refinedRaw"]
                k = [v / oracle.Q14 for v in source]
                predicted = math.log(oracle.interp(target["tp"], full["axisMs"], k))
                errors.append((predicted - target["y"]) ** 2)
        return math.exp(math.sqrt(sum(errors) / len(errors))) - 1.0, len(errors)
    finally:
        oracle.LAMBDA = previous


BLIND_CASES = [("ref_2026-10-01_1719", 95), ("automatch_2026-10-01_1301", 53)]


def blind_errors(e_max=None):
    """Erro RMS (fração) da curva atual e da refinada contra a telemetria em gasolina."""
    previous = oracle.E_MAX
    if e_max is not None:
        oracle.E_MAX = e_max
    try:
        out = []
        for name, sequence in BLIND_CASES:
            _, total = blind.summarize(blind.blind_targets(name, sequence))
            out.append((name, total[0], total[1], total[2]))
        return out
    finally:
        oracle.E_MAX = previous


if __name__ == "__main__":
    print("sem correção:", held_out_error(no_correction=True))
    for lam in (0.0, 0.02, 0.1, 0.3, 1.0, 3.0, 10.0):
        print(f"λ={lam}:", held_out_error(lam))
    for e_max in (0.35, 0.6, 1.0, 9.0):
        print(f"E_MAX={e_max}:", [(n, c, round(a, 4), round(r, 4)) for n, c, a, r in blind_errors(e_max)])
