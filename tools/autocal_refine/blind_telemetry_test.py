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
        # média de 3 leituras: remove o zigue-zague que existe também na gasolina
        out.append({
            "ts": cur["t"],  # instante da leitura (quadro do meio), como o EquivalenceLedger.Obs
            "rpm": sum(f["rpm"] for f in (prev, cur, nxt)) / 3,
            "map": sum(f["load_bar"] for f in (prev, cur, nxt)) / 3,
            "t": sum(f["petrol_ms"] for f in (prev, cur, nxt)) / 3,
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


def telemetry_pairs(telemetry, until_ms=None, rpm_tol=RPM_TOL, map_tol=MAP_TOL):
    """Pares (t_gasolina mediano no mesmo RPM×MAP, t_no_GNV) de leituras estáveis."""
    petrol = cap_cells(stable_frames(telemetry, "GASOLINA"))
    gas = cap_cells(stable_frames(telemetry, "GNV", until_ms))
    out = []
    for g in gas:
        matches = sorted(p["t"] for p in petrol if abs(p["rpm"] - g["rpm"]) <= rpm_tol and abs(p["map"] - g["map"]) <= map_tol)
        if len(matches) >= 2:
            out.append((matches[len(matches) // 2], g["t"]))
    return out


# Regras de produção do EquivalenceLedger.pairs() (o que chega ao motor do refino).
# Dono, 2026-10-11: "a curva deve aprender tudo e saber onde ajustar o quê": todas as rotações (lenta incluída);
# só os grupos coluna do Mapa K × faixa de t separados como LOCAIS saem da curva (EquivalenceAttribution.kt).
PETROL_FREEZE_SAMPLES = 8  # a gasolina própria de cada região RPM×MAP congela madura
RPM_BINS = [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500]  # KMapPhysicalAxes.rpmBins()
BANDS = [(3.0, 4.5), (4.5, 6.0), (6.0, 7.5), (7.5, 9.0), (9.0, 12.0)]  # EquivalenceLedger.BANDS
ENTER_LOG = math.log(1.05)
EXIT_LOG = math.log(1.04)
MIN_PAIRS = 8
MIN_VISITS = 3
VISIT_SPREAD_LOG = math.log(1.03)
MIN_OTHER_PAIRS = 8
SAME_T_MS = 0.5


def _cell(o):
    return (math.floor(int(o["rpm"]) / 150), math.floor(int(o["map"] * 1000) / 20))


def _upper_median(values):
    values = sorted(values)
    return values[len(values) // 2] if values else None


def kmap_column(rpm):
    """Coluna do Mapa K mais próxima (empate fica com a de menor RPM)."""
    best = 0
    for i in range(1, len(RPM_BINS)):
        if abs(rpm - RPM_BINS[i]) < abs(rpm - RPM_BINS[best]):
            best = i
    return best


def band_of(petrol_ms):
    for i, (lo, hi) in enumerate(BANDS):
        if lo <= petrol_ms < hi:
            return i
    return -1


def attribution(pairs, latched=frozenset()):
    """Oráculo de EquivalenceAttribution.classify: pairs = [{tp, tg, rpm, ts}] com gasolina própria.
    Devolve {(coluna, faixa): {"local": bool, "reason": str, "pairs": n, "visits": v, "residual": r|None}}."""
    members = []
    for p in pairs:
        if p["tp"] <= 0 or p["tg"] <= 0:
            continue
        b = band_of(p["tp"])
        col = kmap_column(p["rpm"])
        members.append({**p, "col": col, "key": (col, b) if b >= 0 else None, "err": math.log(p["tg"] / p["tp"])})
    visit, previous = 0, None
    for m in sorted(members, key=lambda x: x["ts"]):
        if m["key"] != previous:
            visit += 1
        m["visit"] = visit
        previous = m["key"]
    by_key = {}
    for m in members:
        if m["key"] is not None:
            by_key.setdefault(m["key"], []).append(m)
    stats = []
    for key, group in by_key.items():
        by_visit = {}
        for m in group:
            by_visit.setdefault(m["visit"], []).append(m["err"])
        visits = [by_visit[k] for k in sorted(by_visit)]
        stats.append({"key": key, "n": len(group), "tp": _upper_median([m["tp"] for m in group]),
                      "err": _upper_median([m["err"] for m in group]), "visits": [_upper_median(v) for v in visits]})

    def others_of(st, excluded):
        return [m["err"] for m in members if m["col"] != st["key"][0] and abs(m["tp"] - st["tp"]) <= SAME_T_MS
                and (m["key"] is None or m["key"] not in excluded)]

    initial = {}
    for st in stats:
        ref = _upper_median(others_of(st, set()))
        initial[st["key"]] = abs(st["err"] - ref) if ref is not None else -1.0
    ordered = sorted(stats, key=lambda st: (0 if st["key"] in latched else 1, -initial[st["key"]], st["key"][0], st["key"][1]))
    excluded, out = set(), {}
    for st in ordered:
        others = others_of(st, excluded)
        ref = _upper_median(others)
        residual = None if ref is None else st["err"] - ref
        vis = [] if ref is None else [v - ref for v in st["visits"]]
        centre = _upper_median(vis)
        stable = centre is not None and (all(v > 0 for v in vis) or all(v < 0 for v in vis)) and \
            _upper_median([abs(v - centre) for v in vis]) <= VISIT_SPREAD_LOG
        threshold = EXIT_LOG if st["key"] in latched else ENTER_LOG
        if st["n"] < MIN_PAIRS:
            reason = "POUCOS_PARES"
        elif len(st["visits"]) < MIN_VISITS:
            reason = "POUCAS_VISITAS"
        elif len(others) < MIN_OTHER_PAIRS or residual is None:
            reason = "SEM_OUTRA_COLUNA_NO_MESMO_T"
        elif not stable:
            reason = "VISITAS_INSTAVEIS"
        elif abs(residual) <= threshold:
            reason = "IGUAL_AS_OUTRAS_COLUNAS"
        else:
            reason = "LOCAL"
        if reason == "LOCAL" or (residual is not None and abs(residual) > threshold):
            excluded.add(st["key"])
        out[st["key"]] = {"local": reason == "LOCAL", "reason": reason, "pairs": st["n"], "visits": len(st["visits"]), "residual": residual}
    return out


def own_pairs(telemetry, rpm_tol=RPM_TOL, map_tol=MAP_TOL):
    """Todos os casamentos com gasolina própria (= allPairs()): gasolina congelada em PETROL_FREEZE_SAMPLES leituras
    por região (na ordem de chegada), GNV com o teto por região, todas as rotações."""
    counts = {}
    petrol = []
    for o in stable_frames(telemetry, "GASOLINA"):
        key = _cell(o)
        if counts.get(key, 0) >= PETROL_FREEZE_SAMPLES:
            continue
        counts[key] = counts.get(key, 0) + 1
        petrol.append(o)
    out = []
    for g in cap_cells(stable_frames(telemetry, "GNV")):
        matches = sorted(p["t"] for p in petrol if abs(p["rpm"] - g["rpm"]) <= rpm_tol and abs(p["map"] - g["map"]) <= map_tol)
        if len(matches) >= 2:
            out.append({"tp": matches[len(matches) // 2], "tg": g["t"], "rpm": g["rpm"], "ts": g["ts"]})
    return out


def production_pairs(telemetry, global_only=True, rpm_tol=RPM_TOL, map_tol=MAP_TOL):
    """Pares como o EquivalenceLedger produz: com global_only (= pairs(), sem referência da ECU), todas as rotações
    menos os grupos locais; sem (= allPairs()), todos os casamentos."""
    pairs = own_pairs(telemetry, rpm_tol, map_tol)
    if global_only:
        local = {key for key, g in attribution(pairs).items() if g["local"]}
        pairs = [p for p in pairs if not (band_of(p["tp"]) >= 0 and (kmap_column(p["rpm"]), band_of(p["tp"])) in local)]
    return [(p["tp"], p["tg"]) for p in pairs]


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
