#!/usr/bin/env python3
"""Oráculo independente do cérebro único (Fatia F4): Referência, Curva Própria, uso, estados e índice.

Escrito do zero a partir da spec (§1.1–§1.7), sem importar nada de tools/autocal_refine: é a segunda
implementação contra a qual o Kotlin (`com.omegas.prohub.equivalence`) é comparado nas sessões reais
(`fixtures/autocal/real/*.json.gz`). Só estados base: prova por ponto e experiência (tremor, quase-apagão)
ficam fora (dependem de relógio e de eventos que a fixture não tem).

Convenções que o Kotlin repete bit a bit:
  * mediana = ordenado[n // 2] (nunca a média dos dois do meio);
  * célula da Curva Própria = 0,02 bar, grade [0,10; 1,10) → 50 células;
  * leituras de condução: rpm >= 1000.
Uso: python3 equivalence_oracle.py <fixture.json.gz> [refSeq curveSeq]   (imprime JSON)
"""
import gzip
import json
import math
import sys

# ------------------------------------------------------------------ constantes
CELL_BAR = 0.02
GRID_MIN = 0.10
GRID_CELLS = 50
PRIOR_N0 = 3.0
DRIVING_MIN_RPM = 1000.0
MIN_TOL = 0.04
MAX_FRAME_DT_MS = 1000
MAX_AGE_SESSIONS = 10
CONF_MAX = MIN_TOL
MATURE_SAMPLES = 3
AXIS_MIN_MS = 3.0            # abaixo disso a telemetria é dominada por transiente/corte
REF_MIN_POINTS = 6
REF_MIN_SPAN = 0.20
REF_MARGIN = 0.03
LAMBDA = 0.3
IRLS = 6
TUKEY_C = 4.685
CELL_CAP = 30                # retenção por célula RPM×MAP do livro (150 rpm × 0,02 bar)
STATES_OUT = ("SEM_DADOS", "APRENDENDO", "MEDIDO")


# ------------------------------------------------------------------ utilidades
def median(values):
    s = sorted(values)
    return s[len(s) // 2]


def interp(x, xs, ys):
    if x <= xs[0]:
        return ys[0]
    if x >= xs[-1]:
        return ys[-1]
    for i in range(1, len(xs)):
        if x <= xs[i]:
            span = xs[i] - xs[i - 1]
            if span <= 0:
                return ys[i - 1]
            return ys[i - 1] + (ys[i] - ys[i - 1]) * (x - xs[i - 1]) / span
    return ys[-1]


def pava(ys, ws):
    blocks = []
    for y, w in zip(ys, ws):
        blocks.append([y * w, w, 1])
        while len(blocks) > 1 and blocks[-2][0] / blocks[-2][1] > blocks[-1][0] / blocks[-1][1]:
            b = blocks.pop()
            blocks[-1][0] += b[0]
            blocks[-1][1] += b[1]
            blocks[-1][2] += b[2]
    out = []
    for b in blocks:
        out.extend([b[0] / b[1]] * b[2])
    return out


def solve(a, v):
    n = len(v)
    m = [row[:] + [v[i]] for i, row in enumerate(a)]
    for c in range(n):
        p = max(range(c, n), key=lambda r: abs(m[r][c]))
        m[c], m[p] = m[p], m[c]
        piv = m[c][c]
        if abs(piv) < 1e-15:
            raise ValueError("sistema singular")
        for r in range(c + 1, n):
            f = m[r][c] / piv
            if f:
                for k in range(c, n + 1):
                    m[r][k] -= f * m[c][k]
    x = [0.0] * n
    for r in range(n - 1, -1, -1):
        x[r] = (m[r][n] - sum(m[r][k] * x[k] for k in range(r + 1, n))) / m[r][r]
    return x


def tukey(z):
    return 0.0 if abs(z) >= 1.0 else (1.0 - z * z) ** 2


def smooth(u, obs, prior_w):
    """Whittaker robusto (Tukey/IRLS) em x com prior 0: min Σ w(x_j−y)² + Σ pw x² + λ Σ (D2 x)².
    obs = [(j, y, w)]; D2 normalizada pelo quadrado do passo médio (grade uniforme → [1,−2,1])."""
    n = len(u)
    rows = []
    for j in range(1, n - 1):
        h0, h1 = u[j] - u[j - 1], u[j + 1] - u[j]
        sc, mh = 2.0 / (h0 + h1), (h0 + h1) / 2.0
        norm = mh * mh
        rows.append({j - 1: sc / h0 * norm, j: -sc * (1.0 / h0 + 1.0 / h1) * norm, j + 1: sc / h1 * norm})
    ro = [1.0] * len(obs)
    rp = [1.0] * n
    x = [0.0] * n
    for _ in range(IRLS):
        m = [[0.0] * n for _ in range(n)]
        v = [0.0] * n
        for row in rows:
            for j, cj in row.items():
                for k, ck in row.items():
                    m[j][k] += LAMBDA * cj * ck
        for j in range(n):
            m[j][j] += prior_w[j] * rp[j]
        for (j, y, w), r in zip(obs, ro):
            m[j][j] += w * r
            v[j] += w * r * y
        x = solve(m, v)
        res = [y - x[j] for (j, y, _w) in obs]
        pres = [-x[j] for j in range(n)]
        pool = sorted([abs(r) for r in res] + [abs(r) for r in pres])
        scale = max(pool[len(pool) // 2] * 1.4826, 0.01)
        ro = [tukey(r / (TUKEY_C * scale)) for r in res]
        rp = [max(tukey(r / (TUKEY_C * scale)), 0.05) for r in pres]
    return x


# ------------------------------------------------------------------ Referência
def snapshot_raw(snapshot, key):
    for f in snapshot.get("fields", []):
        if f.get("key") == key and f.get("status") == "VALID":
            return list(f["rawValues"])
    return None


def zone(index):
    return 0 if index <= 5 else 1 if index <= 9 else 2 if index <= 13 else 3


def reference_points(snapshot):
    """Pontos de gasolina que a ECU deu como adquiridos, limpos como o livro limpa a curva da ECU.
    [] se imatura (< 6 pontos ou faixa < 0,20 bar). Retorna [(map, ms, maturidade)]."""
    times = snapshot_raw(snapshot, "PETR_INJ_TBUF") or []
    maps = snapshot_raw(snapshot, "MNFLD_PRESS_BUF") or []
    counts = snapshot_raw(snapshot, "NUM_BUF_UPD_PETR")
    zones = snapshot_raw(snapshot, "ACQUIRED_ZONES_PETROL") or []
    if counts is None:
        return []
    raw = []
    for i in range(18):
        if i >= len(times) or i >= len(maps) or i >= len(counts):
            continue
        t_raw, m_raw, c = times[i], maps[i], counts[i]
        active = t_raw != 0 or m_raw != 0 or c > 0
        z = zones[zone(i)] if zone(i) < len(zones) else None
        if not (active and z == 1):
            continue
        ms, bar = t_raw / 512.0, m_raw / 1024.0
        if ms > 0 and bar > 0:
            raw.append((bar, ms, c))
    clean = [p for p in raw if 0.05 <= p[0] <= 2.5 and 1.0 <= p[1] <= 40.0]
    groups = {}
    for p in clean:
        groups.setdefault(round_half_up(p[0] * 1000), []).append(p)
    pts = []
    for g in groups.values():
        pts.append((sum(p[0] for p in g) / len(g), sum(p[1] for p in g) / len(g), max(p[2] for p in g)))
    pts.sort(key=lambda p: p[0])
    if len(pts) >= REF_MIN_POINTS and pts[-1][0] - pts[0][0] >= REF_MIN_SPAN:
        return pts
    return []


def round_half_up(x):
    return int(math.floor(x + 0.5))


def prior_at(ref, m):
    """ms da gasolina da Referência no MAP m: isotônica em ln (pesos = maturidade) + interpolação linear;
    plana até ±0,03 bar além das pontas; None além disso."""
    if not ref:
        return None
    maps = [p[0] for p in ref]
    fit = [math.exp(v) for v in pava([math.log(p[1]) for p in ref], [float(max(1, p[2])) for p in ref])]
    if m < maps[0] - REF_MARGIN or m > maps[-1] + REF_MARGIN:
        return None
    if m <= maps[0]:
        return fit[0]
    if m >= maps[-1]:
        return fit[-1]
    return interp(m, maps, fit)


# ------------------------------------------------------------------ livro (leituras estáveis)
def cell_of(m):
    j = math.floor((m - GRID_MIN) / CELL_BAR + 1e-9)
    return j if 0 <= j < GRID_CELLS else None


def center(j):
    return GRID_MIN + (j + 0.5) * CELL_BAR


def ledger_obs(frames):
    """Espelho de EquivalenceLedger.accept: janela de 3 quadros do mesmo combustível, ≤ 1,2 s,
    rpm ±150, map ±0,03; leitura = média dos 3; cada região RPM×MAP guarda as 30 mais recentes."""
    window = []
    lanes = {"GASOLINA": {}, "GNV": {}}
    seq = 0
    for f in frames:
        if f["fuel"] not in ("GASOLINA", "GNV") or f["rpm"] <= 0 or f["map"] <= 0 or f["petrol_ms"] < 1.0:
            window = []
            continue
        if window and window[-1]["fuel"] != f["fuel"]:
            window = []
        window.append(f)
        window = window[-3:]
        if len(window) < 3:
            continue
        a, c = window[0], window[-1]
        if c["t"] - a["t"] > 1200:
            continue
        if max(w["rpm"] for w in window) - min(w["rpm"] for w in window) > 150:
            continue
        if max(w["map"] for w in window) - min(w["map"] for w in window) > 0.03:
            continue
        o = (sum(w["rpm"] for w in window) / 3.0, sum(w["map"] for w in window) / 3.0,
             sum(w["petrol_ms"] for w in window) / 3.0)
        key = (math.floor(int(o[0]) / 150), math.floor(int(o[1] * 1000) / 20))
        q = lanes[f["fuel"]].setdefault(key, [])
        q.append((seq, o))
        seq += 1
        if len(q) > CELL_CAP:
            q.pop(0)
    out = {}
    for fuel, cells in lanes.items():
        items = sorted((it for q in cells.values() for it in q), key=lambda x: x[0])
        out[fuel] = [o for _, o in items]
    return out["GASOLINA"], out["GNV"]


# ------------------------------------------------------------------ Curva Própria
def own_curve(obs, ref):
    """50 células {mapBar, petrolMs|None, samples, dispersion, source, divergence|None}."""
    lns = [[] for _ in range(GRID_CELLS)]
    for rpm, mp, ms in obs:
        j = cell_of(mp)
        if rpm >= DRIVING_MIN_RPM and j is not None and ms > 0:
            lns[j].append(math.log(ms))
    n = [len(v) for v in lns]
    med = [median(v) if v else None for v in lns]
    disp = [0.0] * GRID_CELLS
    for j in range(GRID_CELLS):
        if n[j] >= 2:
            disp[j] = 1.4826 * median([abs(x - med[j]) for x in lns[j]])
    data = [j for j in range(GRID_CELLS) if n[j] > 0]
    mono = {}
    if data:
        fit = pava([med[j] for j in data], [float(n[j]) for j in data])
        mono = dict(zip(data, fit))
    prior = [prior_at(ref, center(j)) if ref else None for j in range(GRID_CELLS)]
    ln_t = [None] * GRID_CELLS
    dom = [j for j in range(GRID_CELLS) if prior[j] is not None]
    if dom:
        p = {j: math.log(prior[j]) for j in dom}
        obs_w = []
        for k, j in enumerate(dom):
            if n[j] > 0:
                obs_w.append((k, mono[j] - p[j], n[j] / (n[j] + PRIOR_N0)))
        pw = [PRIOR_N0 / (n[j] + PRIOR_N0) for j in dom]
        r = smooth([center(j) for j in dom], obs_w, pw)
        for k, j in enumerate(dom):
            ln_t[j] = p[j] + r[k]
        for j in data:
            if prior[j] is None:
                ln_t[j] = mono[j]
    elif len(data) >= 1:
        for j in data:
            ln_t[j] = mono[j]
        if len(data) >= 2:
            xs = [center(j) for j in data]
            ys = [mono[j] for j in data]
            for j in range(data[0], data[-1] + 1):
                if ln_t[j] is None:
                    ln_t[j] = interp(center(j), xs, ys)
    live = [j for j in range(GRID_CELLS) if ln_t[j] is not None]
    if live:
        fit = pava([ln_t[j] for j in live], [n[j] + PRIOR_N0 for j in live])
        for j, v in zip(live, fit):
            ln_t[j] = v
    cells = []
    for j in range(GRID_CELLS):
        t = math.exp(ln_t[j]) if ln_t[j] is not None else None
        if n[j] == 0:
            src = "REFERENCE" if (ref and t is not None) else "BLENDED"
        elif n[j] >= MATURE_SAMPLES and disp[j] <= MIN_TOL:
            src = "OWN"
        else:
            src = "BLENDED"
        pj = prior[j]
        div = (t / pj - 1.0) if (t is not None and pj is not None and n[j] > 0) else None
        cells.append({"mapBar": center(j), "petrolMs": t, "samples": n[j], "dispersion": disp[j],
                      "source": src, "divergence": div})
    return cells


def curve_at(cells, m):
    """Interpola entre centros de células com valor; None fora do primeiro/último."""
    pts = [(c["mapBar"], c["petrolMs"]) for c in cells if c["petrolMs"] is not None]
    if not pts or m < pts[0][0] or m > pts[-1][0]:
        return None
    return interp(m, [p[0] for p in pts], [p[1] for p in pts])


def map_for(cells, ms):
    """MAP da primeira travessia de ms pela curva (segmentos entre células com valor)."""
    pts = [(c["mapBar"], c["petrolMs"]) for c in cells if c["petrolMs"] is not None]
    for (m0, t0), (m1, t1) in zip(pts, pts[1:]):
        if t0 == t1:
            continue
        if min(t0, t1) <= ms <= max(t0, t1):
            return m0 + (m1 - m0) * (ms - t0) / (t1 - t0)
    return None


# ------------------------------------------------------------------ uso
def usage_cells(frames):
    """Soma de ms de condução por célula (qualquer sessão desta lista = uma sessão)."""
    cell_ms = [0.0] * GRID_CELLS
    last = None
    for f in frames:
        dt = 0 if last is None else min(max(f["t"] - last, 0), MAX_FRAME_DT_MS)
        last = f["t"]
        if f["fuel"] not in ("GASOLINA", "GNV") or f["rpm"] < DRIVING_MIN_RPM or f["map"] <= 0:
            continue
        j = cell_of(f["map"])
        if j is not None:
            cell_ms[j] += dt
    return cell_ms


def usage_by_point(cell_ms, axis_ms, own_petrol):
    out = [0.0] * len(axis_ms)
    total = 0.0
    for j, ms in enumerate(cell_ms):
        if ms <= 0:
            continue
        t = curve_at(own_petrol, center(j))
        if t is None or t <= 0:
            continue
        i = min(range(len(axis_ms)), key=lambda k: (abs(math.log(t) - math.log(axis_ms[k])), k))
        out[i] += ms
        total += ms
    return [v / total for v in out] if total > 0 else out


# ------------------------------------------------------------------ pontos e índice
POOL = 1
DISP_UNKNOWN = 0.10


def _span(j):
    return range(max(0, j - POOL), min(GRID_CELLS, j + POOL + 1))


def pooled(cells, j):
    """Amostras na célula e nas vizinhas (±1): a leitura do MAP já tem tremor de até 0,03 bar (> 1 célula)."""
    return sum(cells[k]["samples"] for k in _span(j))


def pooled_disp(cells, j):
    """Dispersão combinada (variância ponderada por n−1) das células vizinhas; sem par de leituras = desconhecida."""
    num = sum((cells[k]["samples"] - 1) * cells[k]["dispersion"] ** 2 for k in _span(j) if cells[k]["samples"] >= 2)
    den = sum(cells[k]["samples"] - 1 for k in _span(j) if cells[k]["samples"] >= 2)
    return math.sqrt(num / den) if den > 0 else DISP_UNKNOWN


def evaluate(axis_raw, k_raw, ref, petrol_obs, gas_obs, cell_ms):
    axis = [a / 512.0 for a in axis_raw]
    k = [v / 16384.0 for v in k_raw]
    own_p = own_curve(petrol_obs, ref)
    own_g = own_curve(gas_obs, ref)
    usage = usage_by_point(cell_ms, axis, own_p)
    points = []
    for i, t_p in enumerate(axis):
        m = map_for(own_p, t_p)
        j = cell_of(m) if m is not None else None
        t_g = curve_at(own_g, m) if m is not None else None
        mixture = None
        if t_g is not None:
            mixture = interp(t_g, axis, k) * t_g / t_p / k[i] - 1.0
        samples, disp = (pooled(own_g, j), max(pooled_disp(own_p, j), pooled_disp(own_g, j))) if j is not None else (0, 0.0)
        tol = max(MIN_TOL, 2.0 * disp)
        if t_p < AXIS_MIN_MS or m is None:
            state = "SEM_DADOS"
        elif samples < MATURE_SAMPLES or 1.96 * disp / math.sqrt(samples) > CONF_MAX:
            state = "APRENDENDO"
        elif mixture is None:
            state = "MEDIDO"
        elif abs(mixture) <= tol:
            state = "EQUIVALENTE"
        elif mixture > tol:
            state = "POBRE"
        else:
            state = "RICO"
        points.append({"index": i, "axisMs": t_p, "mixture": mixture, "tolerance": tol, "usage": usage[i],
                       "samples": samples, "state": state})
    index, coverage = index_of(points)
    return {"ownPetrol": own_p, "ownGas": own_g, "points": points, "index": index, "coverage": coverage}


def index_of(points):
    inc = [p for p in points if p["state"] not in STATES_OUT and p["mixture"] is not None]
    total = sum(p["usage"] for p in inc)
    if total <= 0:
        return None, len(inc)
    eq = sum(p["usage"] for p in inc if abs(p["mixture"]) <= p["tolerance"])
    return eq / total, len(inc)


# ------------------------------------------------------------------ replay
def load(path):
    with (gzip.open(path, "rt", encoding="utf-8") if str(path).endswith(".gz") else open(path, encoding="utf-8")) as h:
        return json.load(h)


def snapshot_by_seq(data, seq):
    return next(s for s in data["snapshots"] if s["sequence"] == seq)


def default_seqs(data):
    """Primeira aquisição madura de gasolina (Referência) e a última Curva K legível do arquivo."""
    ref_seq = next((s["sequence"] for s in data["snapshots"] if reference_points(s)), None)
    k_seq = next((s["sequence"] for s in reversed(data["snapshots"])
                  if snapshot_raw(s, "MUL_ACT") and snapshot_raw(s, "PETR_INJ_TBP")), None)
    return ref_seq, k_seq


def replay(data, ref_seq, k_seq):
    frames = [{"t": f["t"], "fuel": f.get("fuel") or "", "rpm": f.get("rpm") or 0.0,
               "map": f.get("load_bar") or 0.0, "petrol_ms": f.get("petrol_ms") or 0.0} for f in data["telemetry"]]
    ref = reference_points(snapshot_by_seq(data, ref_seq)) if ref_seq is not None else []
    snap = snapshot_by_seq(data, k_seq)
    petrol, gas = ledger_obs(frames)
    out = evaluate(snapshot_raw(snap, "PETR_INJ_TBP"), snapshot_raw(snap, "MUL_ACT"), ref, petrol, gas,
                   usage_cells(frames))
    out.update({"refSeq": ref_seq, "curveSeq": k_seq, "referencePoints": len(ref)})
    return out


def main(argv):
    data = load(argv[1])
    ref_seq, k_seq = default_seqs(data)
    if len(argv) >= 4:
        ref_seq, k_seq = int(argv[2]), int(argv[3])
    print(json.dumps(replay(data, ref_seq, k_seq)))


if __name__ == "__main__":
    main(sys.argv)
