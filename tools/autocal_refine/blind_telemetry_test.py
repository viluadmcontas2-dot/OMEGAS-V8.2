#!/usr/bin/env python3
"""Teste cego: a telemetria (que o motor NÃO usa) julga a curva.

Para cada leitura estável em GNV, busca leituras estáveis em gasolina no mesmo
RPM e MAP (sessão inteira). A razão T_gnv/T_gasolina diz o K que a condução real
pede naquele ponto: K_alvo(T_gas) = K_atual(T_gnv) · T_gnv/T_gas. Compara o erro
da curva atual e da curva refinada contra esse alvo, por faixa de ms.
"""
import datetime
import gzip
import json
import math
import sys
from pathlib import Path

import refined_oracle as oracle

REAL = Path(__file__).resolve().parents[2] / "fixtures/autocal/real"
RPM_TOL = 150
STABLE_MS_SPREAD = 0.10
VISIT_GAP_MS = 3000    # = EvidencePairs.VISIT_GAP_MS (bloco de janelas sobrepostas)
EPISODE_BAND_FACTOR = 100000
MAP_TOL = 0.02


def stable_frames(telemetry, fuel, until_ms=None):
    out = []
    for prev, cur, nxt in zip(telemetry, telemetry[1:], telemetry[2:]):
        if until_ms is not None and cur["t"] > until_ms:
            break
        if not (prev["fuel"] == cur["fuel"] == nxt["fuel"] == fuel):
            continue
        if any(not f["petrol_ms"] or f["petrol_ms"] < 1.0 or not f["load_bar"] or not f["rpm"] for f in (prev, cur, nxt)):
            continue
        if nxt["t"] - prev["t"] > 1200:
            continue
        if max(f["rpm"] for f in (prev, cur, nxt)) - min(f["rpm"] for f in (prev, cur, nxt)) > 150:
            continue
        if max(f["load_bar"] for f in (prev, cur, nxt)) - min(f["load_bar"] for f in (prev, cur, nxt)) > 0.03:
            continue
        # ms que pula (8↔9 ms) não é estado estável: a média de 3 esconderia o salto (EquivalenceLedger.STABLE_MS_SPREAD)
        ms = [f["petrol_ms"] for f in (prev, cur, nxt)]
        if max(ms) - min(ms) > STABLE_MS_SPREAD * (sum(ms) / 3):
            continue
        # média de 3 leituras: remove o zigue-zague que existe também na gasolina
        out.append({
            "rpm": sum(f["rpm"] for f in (prev, cur, nxt)) / 3,
            "map": sum(f["load_bar"] for f in (prev, cur, nxt)) / 3,
            "t": sum(f["petrol_ms"] for f in (prev, cur, nxt)) / 3,
            "at": cur["t"],
        })
    return out


CELL_CAP = 30


def cap_cells(obs, cell_cap=CELL_CAP):
    """Mesma retenção do EquivalenceLedger: cada região RPM×MAP (150 rpm × 0,02 bar) guarda
    só as cell_cap leituras mais recentes; a ordem de chegada é preservada."""
    import math
    from collections import deque
    cells = {}
    for seq, o in enumerate(obs):
        key = (math.floor(int(o["rpm"]) / 150), math.floor(int(o["map"] * 1000) / 20))
        q = cells.setdefault(key, deque())
        q.append((seq, o))
        if len(q) > cell_cap:
            q.popleft()
    return [o for _, o in sorted((item for q in cells.values() for item in q), key=lambda x: x[0])]


EPISODE_GAP_MS = VISIT_GAP_MS   # trecho guardado na leitura (diagnóstico); o portão usa as visitas por faixa (visit_ids)


def tag_episodes(obs, gap_ms=EPISODE_GAP_MS):
    """Episódio = trecho de leituras estáveis seguidas; lacuna > gap_ms abre o próximo (espelho do EquivalenceLedger)."""
    episode, last = -1, None
    for o in obs:
        if last is None or o["at"] - last > gap_ms:
            episode += 1
        o["episode"] = episode
        last = o["at"]
    return obs


def visit_ids(items):
    """Id de episódio de cada par = (faixa+1)·100000 + visita: dentro da faixa do livro,
    a lacuna segue VISIT_GAP_MS (hoje 3 s), espelho de EvidencePairs.withVisitIds."""
    bands = {}
    for i, (tp, _at) in enumerate(items):
        b = oracle.ledger_band(tp)
        bands.setdefault(-1 if b is None else b, []).append(i)
    ids = [0] * len(items)
    for band, members in bands.items():
        visit, last = -1, None
        for i in sorted(members, key=lambda k: items[k][1]):
            if last is None or items[i][1] - last >= VISIT_GAP_MS:
                visit += 1
            ids[i] = (band + 1) * EPISODE_BAND_FACTOR + visit
            last = items[i][1]
    return ids


def telemetry_pairs(telemetry, until_ms=None, rpm_tol=RPM_TOL, map_tol=MAP_TOL, with_episodes=False, petrol_until=False):
    """Pares (t_gasolina mediano no mesmo RPM×MAP, t_no_GNV) de leituras estáveis.
    Com with_episodes devolve também o id de episódio de cada par (paralelo)."""
    petrol = cap_cells(stable_frames(telemetry, "GASOLINA", until_ms if petrol_until else None))
    gas = cap_cells(tag_episodes(stable_frames(telemetry, "GNV", until_ms)))
    out, times = [], []
    for g in gas:
        matches = sorted(p["t"] for p in petrol if abs(p["rpm"] - g["rpm"]) <= rpm_tol and abs(p["map"] - g["map"]) <= map_tol)
        if len(matches) >= 2:
            out.append((matches[len(matches) // 2], g["t"]))
            times.append((matches[len(matches) // 2], g["at"]))
    return (out, visit_ids(times)) if with_episodes else out


def blind_targets(fixture, sequence):
    with gzip.open(REAL / f"{fixture}.json.gz", "rt", encoding="utf-8") as handle:
        data = json.load(handle)
    snap = next(s for s in data["snapshots"] if s["sequence"] == sequence)
    until = None
    if data["kFactorWrites"]:
        until = datetime.datetime.fromisoformat(data["kFactorWrites"][0]["recordedAtUtc"].replace("Z", "+00:00")).timestamp() * 1000
    petrol = stable_frames(data["telemetry"], "GASOLINA")
    gas = stable_frames(data["telemetry"], "GNV", until)
    result = oracle.refine(snap)
    axis = result["axisMs"]
    current = [v / oracle.Q14 for v in result["currentRaw"]]
    refined = [v / oracle.Q14 for v in result["refinedRaw"]]
    rows = []
    for g in gas:
        matches = [p for p in petrol if abs(p["rpm"] - g["rpm"]) <= RPM_TOL and abs(p["map"] - g["map"]) <= MAP_TOL]
        if len(matches) < 2:
            continue
        tp = sorted(m["t"] for m in matches)[len(matches) // 2]
        target = oracle.interp(g["t"], axis, current) * g["t"] / tp
        rows.append({
            "tp": tp,
            "target": target,
            "errCurrent": math.log(oracle.interp(tp, axis, current) / target),
            "errRefined": math.log(oracle.interp(tp, axis, refined) / target),
        })
    return rows


def summarize(rows, bins=((1.5, 3.0), (3.0, 4.5), (4.5, 6.0), (6.0, 7.5), (7.5, 9.0), (9.0, 12.0))):
    def rms(values):
        return math.sqrt(sum(v * v for v in values) / len(values)) if values else None
    out = []
    for lo, hi in bins:
        sel = [r for r in rows if lo <= r["tp"] < hi]
        if sel:
            out.append((lo, hi, len(sel), rms([r["errCurrent"] for r in sel]), rms([r["errRefined"] for r in sel]),
                        sum(r["errCurrent"] for r in sel) / len(sel), sum(r["errRefined"] for r in sel) / len(sel)))
    total = (len(rows), rms([r["errCurrent"] for r in rows]), rms([r["errRefined"] for r in rows]))
    return out, total


if __name__ == "__main__":
    fixture = sys.argv[1] if len(sys.argv) > 1 else "ref_2026-10-01_1719"
    sequence = int(sys.argv[2]) if len(sys.argv) > 2 else 95
    by_bin, total = summarize(blind_targets(fixture, sequence))
    print("faixa ms   n   erro atual  erro refinada  viés atual  viés refinada")
    for lo, hi, n, ec, er, bc, br in by_bin:
        print(f"{lo:4.1f}-{hi:4.1f} {n:4d}   {ec*100:6.1f}%     {er*100:6.1f}%      {bc*100:+6.1f}%     {br*100:+6.1f}%")
    print(f"total n={total[0]}  atual={total[1]*100:.1f}%  refinada={total[2]*100:.1f}%")
