#!/usr/bin/env python3
"""Experimentos naturais do histórico: cada gravação de Curva K é um antes/depois.

Para cada gravação K_FACTOR confirmada, compara leituras estáveis em GNV antes e
depois (mesma sessão, mesmo RPM±150 e MAP±0,02). Com a ECU em laço fechado, o
tempo de gasolina no GNV deve mudar de -Δln K(t): ganho medido = Δln t_gnv / (-Δln K).

Achado no histórico (75 sessões, 2026-09-24..10-01): quase todos os pares são em
marcha lenta (~870 rpm) e estão misturados com gravações de Mapa K, resets e
AutoCal; a lenta ora compensa, ora nem aplica a Curva K. Conclusão: a lenta é um
regime à parte e o aprendizado de ganho exige experimentos registrados com
contexto e medidos em condução estável (rpm ≥ 1000).
"""
import glob, gzip, json, math, statistics as st, sys

import blind_telemetry_test as bt

WINDOW_MS = 10 * 60 * 1000
SETTLE_MS = 5000


def k_at(t, axis, factors):
    from refined_oracle import interp
    return interp(t, axis, factors)


def experiments(corpus_dir):
    out = []
    for path in sorted(glob.glob(corpus_dir + "/*.json.gz")):
        d = json.load(gzip.open(path, "rt"))
        writes = [w for w in d.get("kFactorWrites", []) if w.get("data", {}).get("points")]
        if not writes:
            continue
        tel = d["telemetry"]
        for w in writes:
            t0 = w["recordedAtMs"]
            curve = w["data"].get("curve") or {}
            if len(curve.get("factorsRaw", [])) != 30:
                continue
            axis = [v / 512 for v in curve["axisRaw"]]
            new = [v / 16384 for v in curve["factorsRaw"]]
            old = new[:]
            for p in w["data"]["points"]:
                old[p["index"]] = p["currentRaw"] / 16384
            nxt = min([x["recordedAtMs"] for x in writes if x["recordedAtMs"] > t0] + [10 ** 18])
            pre = bt.stable_frames([f for f in tel if t0 - WINDOW_MS <= f["t"] < t0], "GNV")
            post = bt.stable_frames([f for f in tel if t0 + SETTLE_MS <= f["t"] < min(nxt, t0 + WINDOW_MS)], "GNV")
            for q in post:
                m = sorted(p["t"] for p in pre if abs(p["rpm"] - q["rpm"]) <= 150 and abs(p["map"] - q["map"]) <= 0.02)
                if len(m) < 2:
                    continue
                tb = m[len(m) // 2]
                predicted = -math.log(k_at(tb, axis, new) / k_at(tb, axis, old))
                measured = math.log(q["t"] / tb)
                out.append({"session": path.split("/")[-1][:30], "t": tb, "rpm": q["rpm"], "map": q["map"],
                            "pred": predicted, "meas": measured})
    return out


if __name__ == "__main__":
    rows = experiments(sys.argv[1])
    print("pares", len(rows), "sessões", len({r["session"] for r in rows}))
    for lo, hi in ((2, 3), (3, 4.5), (4.5, 6), (6, 7.5), (7.5, 9), (9, 12), (12, 22)):
        sel = [r for r in rows if lo <= r["t"] < hi and abs(r["pred"]) > 0.02]
        if len(sel) < 5:
            continue
        g = sum(r["meas"] * r["pred"] for r in sel) / sum(r["pred"] ** 2 for r in sel)
        ratios = [r["meas"] / r["pred"] for r in sel]
        print(f"{lo:4.1f}-{hi:4.1f} ms  n={len(sel):4d}  sessões={len({r['session'] for r in sel})}  ganho={g:.2f}  mediana razão={st.median(ratios):.2f}")
