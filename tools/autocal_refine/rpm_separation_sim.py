#!/usr/bin/env python3
"""Simulador AutoCal x Mapa K com trajetos reais (fixtures/autocal/real).

Planta um erro conhecido do GNV (curva por MAP + efeito só da lenta), deixa um
"AutoCal" que só conhece MAP aprender sobre o trajeto real (giro, MAP, combustível)
e compara estratégias de correção volta a volta. Passivo: não toca no app nem na ECU.

Modelo:
  P(rpm,map)        tempo de gasolina real (mediana dos quadros de gasolina das fixtures)
  T(rpm,map)        multiplicador de GNV necessário = c(map) * r(rpm)       (verdade plantada)
  D(rpm,map)        entregue = MUL(banda do map) * K(rpm, P)                (ECU + Mapa K)
  no GNV, a sonda corrige: tempo-gasolina-equivalente = P * T / D  (é o que a ECU grava na bolinha)
  erro de mistura (trim) = T / D - 1
"""
from __future__ import annotations

import gzip
import json
import math
import random
import statistics as st
from bisect import bisect_right
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIX = ROOT / "fixtures/autocal/real"
RPM_AX = [850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500]
MS_AX = [2.0, 2.5, 3.0, 3.5, 4.5, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0]
BAND_THD = [v / 1024 for v in [154, 256, 307, 358, 410, 461, 512, 563, 614, 666, 717, 768, 819, 870, 922, 973, 1024, 1126]]
IDLE = 1100
CAP = 0.04          # passo máximo por volta (margem do dono)
ALPHA = 0.15        # média móvel da bolinha nativa


def c_true(m):      # parte da CURVA (igual em todo giro)
    return 1.00 + 0.10 * (m - 0.55)


def r_true(rpm):    # parte do GIRO: GNV parado precisa de +9% (some até 1800 rpm)
    return 1.0 + 0.09 * max(0.0, min(1.0, (1800 - rpm) / (1800 - 850)))


def load():
    traj, pet = [], defaultdict(list)
    for f in sorted(FIX.glob("*.json.gz")):
        t = json.load(gzip.open(f))["telemetry"]
        for a, b, c in zip(t, t[1:], t[2:]):
            if b["fuel"] not in ("GNV", "GASOLINA") or not (a["fuel"] == b["fuel"] == c["fuel"]):
                continue
            if b["petrol_ms"] <= 0.7 or not (0.15 <= b["load_bar"] <= 1.0) or b["rpm"] < 600:
                continue
            if max(x["rpm"] for x in (a, b, c)) - min(x["rpm"] for x in (a, b, c)) > 150:
                continue
            traj.append((b["rpm"], b["load_bar"], b["fuel"]))
            if b["fuel"] == "GASOLINA":
                pet[(b["rpm"] // 250, round(b["load_bar"] / 0.05))].append(b["petrol_ms"])
    surf = {k: st.median(v) for k, v in pet.items() if len(v) >= 3}
    return traj, surf


def P(surf, rpm, m):
    k = (rpm // 250, round(m / 0.05))
    if k in surf:
        return surf[k]
    best = min(surf, key=lambda q: abs(q[0] - k[0]) * 1.0 + abs(q[1] - k[1]) * 1.5)
    return surf[best] * (m / max(0.05, best[1] * 0.05)) ** 0.9


def band(m):
    return max(0, min(15, bisect_right(BAND_THD, m) - 1))


def blend(ax, v):
    if v <= ax[0]:
        return [(0, 1.0)]
    if v >= ax[-1]:
        return [(len(ax) - 1, 1.0)]
    i = bisect_right(ax, v) - 1
    f = (v - ax[i]) / (ax[i + 1] - ax[i])
    return [(i, 1 - f), (i + 1, f)]


class Car:
    def __init__(self):
        self.mul = [1.0] * 16
        self.K = [[1.0] * 12 for _ in MS_AX]
        self.pts = {"GASOLINA": [None] * 16, "GNV": [None] * 16}   # [map, ms]

    def k(self, rpm, ms):
        return sum(wr * wc * self.K[r][c] for r, wr in blend(MS_AX, ms) for c, wc in blend(RPM_AX, rpm))

    def k_add_col(self, rpm, ms, ratio):
        for r, wr in blend(MS_AX, ms):
            for c, wc in blend(RPM_AX, rpm):
                self.K[r][c] *= ratio ** (wr * wc)


def lap(car, traj, surf, rng, log, automatch=True):
    stats = defaultdict(list)
    for rpm, m, fuel in traj:
        p = P(surf, rpm, m) * (1 + rng.gauss(0, 0.01))
        b = band(m)
        if fuel == "GASOLINA":
            x = p
        else:
            T = c_true(m) * r_true(rpm)
            D = car.mul[b] * car.k(rpm, p)
            x = p * T / D * (1 + rng.gauss(0, 0.015))
            trim = T / D - 1
            stats["idle" if rpm < IDLE else "drive"].append(abs(trim))
            log.append((rpm, m, p, x))
        pt = car.pts[fuel][b]
        car.pts[fuel][b] = [m, x] if pt is None else [pt[0] + ALPHA * (m - pt[0]), pt[1] + ALPHA * (x - pt[1])]
    # AutoMatch nativo: corrige MUL da banda pela razão das bolinhas (só conhece MAP)
    for b in (range(16) if automatch else []):
        g, q = car.pts["GNV"][b], car.pts["GASOLINA"][b]
        if g and q:
            car.mul[b] *= max(0.85, min(1.15, (g[1] / g[0]) / (q[1] / q[0])))
    return {k: 100 * st.mean(v) for k, v in stats.items()}


def off_curve(car, fuel="GNV"):
    pts = [(b, *car.pts[fuel][b]) for b in range(16) if car.pts[fuel][b]]
    out = []
    for i in range(1, len(pts) - 1):
        (_, m0, t0), (b, m, t), (_, m1, t1) = pts[i - 1], pts[i], pts[i + 1]
        exp = t0 + (t1 - t0) * (m - m0) / (m1 - m0) if m1 != m0 else t
        if abs(t / exp - 1) > 0.04:
            out.append((b, m, t, exp))
    return out


def strategy_delete(car, log, surf):
    for b, *_ in off_curve(car):
        car.pts["GNV"][b] = None


def strategy_align(car, log, surf):
    # "alinhar a bolinha à curva pelo Mapa K na coluna do giro onde ela nasceu"
    for b, m, t, exp in off_curve(car):
        near = [(r, p) for r, mm, p, _ in log if band(mm) == b]
        if not near:
            continue
        rpm = st.median(r for r, _ in near)
        ms = st.median(p for _, p in near)
        ratio = max(1 - CAP, min(1 + CAP, t / exp))     # bolinha alta = injetar mais GNV
        car.k_add_col(rpm, ms, ratio)


def strategy_separate(car, log, surf):
    # razão medida no MESMO giro+MAP: x/P = T/D. Separa log(T/D) = curva(map) + giro(rpm).
    cells = defaultdict(list)
    for rpm, m, p, x in log:
        cells[(min(range(12), key=lambda i: abs(RPM_AX[i] - rpm)), band(m))].append((math.log(x / p), p))
    obs = [(c, b, st.median(v for v, _ in vals), st.median(p for _, p in vals), len(vals))
           for (c, b), vals in cells.items() if len(vals) >= 5]
    if not obs:
        return
    cm, rr = defaultdict(float), defaultdict(float)
    for _ in range(50):
        for b in {o[1] for o in obs}:
            s = [(o[2] - rr[o[0]], o[4]) for o in obs if o[1] == b]
            cm[b] = sum(a * w for a, w in s) / sum(w for _, w in s)
        for c in {o[0] for o in obs}:
            s = [(o[2] - cm[o[1]], o[4]) for o in obs if o[0] == c]
            rr[c] = sum(a * w for a, w in s) / sum(w for _, w in s)
        ref = st.median(rr[c] for c in rr if RPM_AX[c] >= 1850) if any(RPM_AX[c] >= 1850 for c in rr) else 0.0
        for c in rr:
            rr[c] -= ref
        for b in cm:
            cm[b] += ref
    # O AutoMatch nativo é um evento; o Mapa K (giro x tempo) absorve o que a curva por MAP
    # não consegue: o erro medido no MESMO giro+MAP, amortecido e com teto de 4% por volta.
    for c, b, v, ms, n in obs:
        car.k_add_col(RPM_AX[c], ms, max(1 - CAP, min(1 + CAP, math.exp(0.7 * v))))
    car.split = {RPM_AX[c]: round(100 * (math.exp(v) - 1), 1) for c, v in sorted(rr.items())}


def run(name, fn, traj, surf, laps=8):
    rng = random.Random(7)
    car = Car()
    rows = []
    for i in range(laps):
        log = []
        e = lap(car, traj, surf, rng, log, automatch=(i == 0))
        oc = off_curve(car)
        rows.append((i + 1, e.get("idle", 0), e.get("drive", 0), len(oc), len(off_curve(car, "GASOLINA"))))
        if fn and i > 0:   # nunca corrigir K na mesma janela de um AutoMatch nativo
            fn(car, log, surf)
    print(f"\n== {name}")
    print("volta  erro_lenta%  erro_andando%  fora_da_curva GNV / gasolina")
    for r in rows:
        print(f"{r[0]:5d}  {r[1]:10.1f}  {r[2]:12.1f}  {r[3]:8d} / {r[4]}")
    col850 = st.mean(car.K[r][0] for r in range(12))
    if getattr(car, "split", None):
        print("efeito do giro separado (%, relativo a >=1850 rpm):", car.split)
    print(f"K médio coluna 850 rpm: {col850:.3f} | coluna 2500: {st.mean(car.K[r][3] for r in range(12)):.3f}")


def main():
    traj, surf = load()
    idle = sum(1 for r, _, _ in traj if r < IDLE)
    print(f"trajeto real: {len(traj)} quadros estáveis ({idle} em lenta), superfície de gasolina com {len(surf)} células")
    print("verdade plantada: curva +-10% por MAP; GNV parado precisa de +9% (só giro)")
    run("0) nada além do AutoMatch nativo", None, traj, surf)
    run("1) apagar bolinha fora da curva (detector do GPT)", strategy_delete, traj, surf)
    run("2) alinhar bolinha à curva via Mapa K (ideia inicial)", strategy_align, traj, surf)
    run("3) separar curva x giro x ruído (proposta)", strategy_separate, traj, surf)


if __name__ == "__main__":
    main()
