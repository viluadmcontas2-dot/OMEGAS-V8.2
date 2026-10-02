#!/usr/bin/env python3
"""Oráculo de referência da Equivalência Refinada OMEGAS (Curva K / MUL_ACT).

Implementação pura em Python (sem numpy) usada para:
  * calibrar e congelar as constantes do motor Kotlin `AutoMatchRefinedEngine`;
  * gerar vetores de paridade Kotlin↔Python;
  * reproduzir o AutoMatch nativo observado na ECU (evidência de 2026-10-01 16:10Z).

Princípios (ver docs/workunits/OMEGAS-WU-006.md):
  1. Evidência por banda vem dos buffers nativos (tempo médio, MAP médio, contagem).
  2. T(MAP) é fisicamente não decrescente: ajuste isotônico ponderado com rejeição
     robusta de banda outlier.
  3. Equivalência exata: na banda com suporte nos dois combustíveis,
     K_alvo(T_p) = K_atual(T_g) · T_g / T_p.
  4. Ajuste Whittaker robusto em log K sobre u = ln(t) dos 30 nós do eixo.
  5. Trava de coerência: |Δ ln K / Δ ln t| ≤ E_MAX entre nós vizinhos (evita o
     ciclo-limite de laço fechado que gera trancos no GNV) e passo ≤ ±15%/execução.
  6. Ganho proporcional à evidência; sem evidência o ponto só recebe polimento de
     coerência; nada é gravado automaticamente.
"""
import math

POINT_COUNT = 30
BAND_COUNT = 18
AXIS_COUNTS_PER_MS = 512.0
MAP_COUNTS_PER_BAR = 1024.0
Q14 = 16384.0
MAX_RAW = 65535
MIN_FACTOR = 0.60
MAX_FACTOR = MAX_RAW / Q14

# Constantes congeladas (calibradas por tools/autocal_refine/calibrate.py).
BAND_FULL_COUNT = 6          # contagem que dá peso 1 à banda
BAND_MATURE_COUNT = 3        # CALIBRATION_VAL_1 MinBufUpd*Thd observado = 3
MIN_COMMON_MATURE = 4        # falha fechada abaixo disso
OUTLIER_MIN_LOG = 0.05       # rejeição mínima absoluta (≈5%) de banda fora da curva
OUTLIER_MAD_K = 3.0
LAMBDA = 0.1                 # rigidez (2ª derivada em u=ln t), escolhida por varredura
PRIOR_SUPPORTED = 0.05       # peso do K atual em nó com evidência
PRIOR_UNSUPPORTED = 1.0      # peso do K atual em nó sem evidência
EVIDENCE_REF = 0.5           # massa de evidência que libera ganho 1
MAX_STEP_LOG = math.log(1.15)
E_MAX = 0.35                 # elasticidade máxima |d ln K / d ln t|
IRLS_ITERATIONS = 6
TUKEY_C = 4.685
SMOOTH_TOLERANCE_LOG = 0.0025

NATIVE_MIN_RATIO = 0.75
NATIVE_MAX_RATIO = 1.20


# ---------------------------------------------------------------- utilidades

def fields_of(snapshot):
    return {f["key"]: f for f in snapshot["fields"]}


def raw(snapshot, key):
    field = fields_of(snapshot).get(key)
    if field is None or field.get("status") != "VALID":
        return None
    return list(field["rawValues"])


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
            f = (x - xs[i - 1]) / span
            return ys[i - 1] + (ys[i] - ys[i - 1]) * f
    return ys[-1]


def axis_weights(x, xs):
    """Pesos lineares (índice, peso) de x sobre o eixo xs; fora do eixo → borda."""
    if x <= xs[0]:
        return [(0, 1.0)]
    if x >= xs[-1]:
        return [(len(xs) - 1, 1.0)]
    for i in range(1, len(xs)):
        if x <= xs[i]:
            f = (x - xs[i - 1]) / (xs[i] - xs[i - 1])
            return [(i - 1, 1.0 - f), (i, f)]
    return [(len(xs) - 1, 1.0)]


def weighted_median(values, weights):
    pairs = sorted(zip(values, weights))
    total = sum(weights)
    acc = 0.0
    for value, weight in pairs:
        acc += weight
        if acc >= total / 2.0:
            return value
    return pairs[-1][0]


def solve(matrix, vector):
    """Gauss com pivotamento parcial (sistema pequeno, 30×30)."""
    n = len(vector)
    a = [row[:] + [vector[i]] for i, row in enumerate(matrix)]
    for col in range(n):
        pivot = max(range(col, n), key=lambda r: abs(a[r][col]))
        a[col], a[pivot] = a[pivot], a[col]
        p = a[col][col]
        if abs(p) < 1e-15:
            raise ValueError("sistema singular")
        for r in range(col + 1, n):
            factor = a[r][col] / p
            if factor:
                for c in range(col, n + 1):
                    a[r][c] -= factor * a[col][c]
    x = [0.0] * n
    for r in range(n - 1, -1, -1):
        x[r] = (a[r][n] - sum(a[r][c] * x[c] for c in range(r + 1, n))) / a[r][r]
    return x


# ------------------------------------------------------------ evidência

def pava(xs, ys, ws):
    """Regressão isotônica não decrescente ponderada (pool adjacent violators)."""
    blocks = []  # [soma_wy, soma_w, quantidade]
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


def band_points(time_raw, map_raw, counts):
    """(MAP bar, T ms, peso, contagem, banda) das bandas com dado."""
    points = []
    for band in range(BAND_COUNT):
        n = counts[band]
        if n <= 0 or time_raw[band] <= 0 or map_raw[band] <= 0:
            continue
        points.append({
            "band": band,
            "map": map_raw[band] / MAP_COUNTS_PER_BAR,
            "t": time_raw[band] / AXIS_COUNTS_PER_MS,
            "w": min(n, BAND_FULL_COUNT) / float(BAND_FULL_COUNT),
            "n": n,
        })
    points.sort(key=lambda p: p["map"])
    return points


def monotone_fit(points):
    """Ajuste isotônico em ln T com rejeição robusta de outliers.

    Retorna (pontos_aceitos_com_fit, rejeitados)."""
    accepted = list(points)
    rejected = []
    for _ in range(3):
        if len(accepted) < 2:
            break
        logs = [math.log(p["t"]) for p in accepted]
        fit = pava([p["map"] for p in accepted], logs, [p["w"] for p in accepted])
        residuals = [l - f for l, f in zip(logs, fit)]
        # resíduo em relação aos vizinhos (leave-one-out linear em MAP)
        loo = []
        for i, p in enumerate(accepted):
            if 0 < i < len(accepted) - 1:
                a, b = accepted[i - 1], accepted[i + 1]
                expected = interp(p["map"], [a["map"], b["map"]], [math.log(a["t"]), math.log(b["t"])])
                loo.append(math.log(p["t"]) - expected)
            else:
                loo.append(residuals[i])
        mad = weighted_median([abs(r) for r in loo], [p["w"] for p in accepted]) * 1.4826
        limit = max(OUTLIER_MIN_LOG, OUTLIER_MAD_K * mad)
        # Só é outlier a banda que foge dos vizinhos E viola a monotonicidade
        # (resíduo isotônico ≠ 0); um degrau físico monótono é preservado.
        candidates = [i for i in range(len(accepted)) if abs(loo[i]) > limit and abs(residuals[i]) > 1e-9]
        if not candidates:
            break
        worst = max(candidates, key=lambda i: abs(loo[i]))
        rejected.append(dict(accepted[worst], reason="OUTLIER_BANDA"))
        accepted.pop(worst)
    if accepted:
        logs = [math.log(p["t"]) for p in accepted]
        fit = pava([p["map"] for p in accepted], logs, [p["w"] for p in accepted])
        for p, f in zip(accepted, fit):
            p["fit_t"] = math.exp(f)
    return accepted, rejected


def fuel_curve(points):
    """Funções T(MAP) e peso(MAP) a partir do ajuste monótono."""
    return [p["map"] for p in points], [p["fit_t"] for p in points], [p["w"] for p in points]


def equivalence_targets(petrol, gas, axis_ms, k_old):
    """Pontos alvo (t_p, y=ln K_alvo, w) na faixa comum de MAP."""
    pm, pt, pw = fuel_curve(petrol)
    gm, gt, gw = fuel_curve(gas)
    lo, hi = max(pm[0], gm[0]), min(pm[-1], gm[-1])
    grid = sorted({m for m in pm + gm if lo <= m <= hi})
    targets = []
    for m in grid:
        tp = interp(m, pm, pt)
        tg = interp(m, gm, gt)
        wp = local_weight(m, pm, pw)
        wg = local_weight(m, gm, gw)
        w = min(wp, wg)
        if w <= 0 or tp <= 0 or tg <= 0:
            continue
        k_at_gas = interp(tg, axis_ms, k_old)
        targets.append({
            "map": m, "tp": tp, "tg": tg, "w": w,
            "ratio": tg / tp,
            "y": math.log(k_at_gas * tg / tp),
        })
    return targets


def local_weight(m, maps, weights):
    """Peso no MAP m: menor peso entre as bandas que o cercam (conservador)."""
    if m <= maps[0]:
        return weights[0] if abs(m - maps[0]) < 1e-9 else 0.0
    if m >= maps[-1]:
        return weights[-1] if abs(m - maps[-1]) < 1e-9 else 0.0
    for i in range(1, len(maps)):
        if m <= maps[i]:
            if abs(m - maps[i]) < 1e-9:
                return weights[i]
            return min(weights[i - 1], weights[i])
    return 0.0


# ---------------------------------------------------------------- ajuste

def second_difference_rows(u):
    rows = []
    for j in range(1, len(u) - 1):
        h0 = u[j] - u[j - 1]
        h1 = u[j + 1] - u[j]
        scale = 2.0 / (h0 + h1)
        row = [0.0] * len(u)
        row[j - 1] = scale / h0
        row[j] = -scale * (1.0 / h0 + 1.0 / h1)
        row[j + 1] = scale / h1
        # normaliza pela largura média do intervalo para que λ seja comparável
        mean_h = (h0 + h1) / 2.0
        rows.append([v * mean_h * mean_h for v in row])
    return rows


def whittaker(u, observations, prior, prior_weights, lam):
    """Minimiza Σ w(a·x−y)² + Σ μ(x−x0)² + λ Σ (D2 x)² com IRLS de Tukey."""
    n = len(u)
    d2 = second_difference_rows(u)
    obs_robust = [1.0] * len(observations)
    prior_robust = [1.0] * n
    x = prior[:]
    for _ in range(IRLS_ITERATIONS):
        m = [[0.0] * n for _ in range(n)]
        v = [0.0] * n
        for row in d2:
            nz = [(j, c) for j, c in enumerate(row) if c]
            for j, cj in nz:
                for k, ck in nz:
                    m[j][k] += lam * cj * ck
        for j in range(n):
            w = prior_weights[j] * prior_robust[j]
            m[j][j] += w
            v[j] += w * prior[j]
        for o, r in zip(observations, obs_robust):
            w = o["w"] * r
            for j, aj in o["a"]:
                v[j] += w * aj * o["y"]
                for k, ak in o["a"]:
                    m[j][k] += w * aj * ak
        x = solve(m, v)
        residuals = [o["y"] - sum(a * x[j] for j, a in o["a"]) for o in observations]
        prior_res = [prior[j] - x[j] for j in range(n)]
        pool = [abs(r) for r in residuals] + [abs(r) for r in prior_res]
        scale = max(sorted(pool)[len(pool) // 2] * 1.4826, 0.01)
        obs_robust = [tukey(r / (TUKEY_C * scale)) for r in residuals]
        prior_robust = [max(tukey(r / (TUKEY_C * scale)), 0.05) for r in prior_res]
    return x, obs_robust


def tukey(z):
    if abs(z) >= 1.0:
        return 0.0
    return (1.0 - z * z) ** 2


def coherence_feasible(x0, u, e):
    """Existe curva com |z−x0| ≤ passo e |Δz/Δu| ≤ e? (envelopes Lipschitz)."""
    n = len(x0)
    for j in range(n):
        hi = min(x0[k] + MAX_STEP_LOG + e * abs(u[j] - u[k]) for k in range(n))
        lo = max(x0[k] - MAX_STEP_LOG - e * abs(u[j] - u[k]) for k in range(n))
        if lo > hi + 1e-12:
            return False
    return True


def effective_elasticity(x0, u):
    """E_MAX quando viável; senão o menor limite viável (curva atual muito serrilhada
    para ser corrigida em uma execução dentro de ±15%)."""
    if coherence_feasible(x0, u, E_MAX):
        return E_MAX
    lo, hi = E_MAX, 8.0
    for _ in range(40):
        mid = (lo + hi) / 2.0
        if coherence_feasible(x0, u, mid):
            hi = mid
        else:
            lo = mid
    return hi


def enforce_coherence(z, x0, u, e):
    """Projeções alternadas (POCS): passo ≤ MAX_STEP_LOG e |Δz/Δu| ≤ e."""
    z = z[:]
    n = len(z)
    for _ in range(20000):
        changed = False
        for j in range(n):
            lo, hi = x0[j] - MAX_STEP_LOG, x0[j] + MAX_STEP_LOG
            if z[j] < lo - 1e-12 or z[j] > hi + 1e-12:
                z[j] = min(max(z[j], lo), hi)
                changed = True
        for j in range(n - 1):
            limit = e * (u[j + 1] - u[j])
            diff = z[j + 1] - z[j]
            if abs(diff) > limit + 1e-9:
                excess = (abs(diff) - limit) / 2.0
                sign = 1.0 if diff > 0 else -1.0
                z[j] += sign * excess
                z[j + 1] -= sign * excess
                changed = True
        if not changed:
            break
    return z


# --------------------------------------------------------------- métricas

def metrics(factors, axis_ms, lo=2, hi=22):
    logs = [math.log(f) for f in factors]
    u = [math.log(t) for t in axis_ms]
    steps = [abs(factors[j + 1] / factors[j] - 1.0) for j in range(lo, hi)]
    elastic = [abs((logs[j + 1] - logs[j]) / (u[j + 1] - u[j])) for j in range(lo, hi)]
    rough = sum((logs[j + 1] - 2 * logs[j] + logs[j - 1]) ** 2 for j in range(lo + 1, hi))
    sign_changes = 0
    last = 0
    for j in range(lo, hi):
        d = logs[j + 1] - logs[j]
        s = 0 if abs(d) < 0.005 else (1 if d > 0 else -1)
        if s and last and s != last:
            sign_changes += 1
        if s:
            last = s
    return {
        "maxNeighborStep": round(max(steps), 5),
        "maxElasticity": round(max(elastic), 4),
        "roughness": round(rough, 6),
        "slopeSignChanges": sign_changes,
    }


# ------------------------------------------------------------- AutoMatch nativo

def native_automatch_replica(snapshot):
    """Reprodução do AutoMatch nativo observado (ganho total, sem suavização).

    Evidência: sessão 2026-10-01 13:01 local, AutoMatch #1 às 16:10:32Z com
    MUL_ACT=1.0; o resultado da ECU iguala ponto a ponto (índices 4–29, ≤1 LSB após
    truncamento Q14) clamp(T_g(P_p(t))/t, 0,75, 1,20) usando as curvas RV.
    """
    axis = raw(snapshot, "PETR_INJ_TBP")
    prv = raw(snapshot, "PETR_MNFLD_PRESS_RV")
    grv = raw(snapshot, "GAS_MNFLD_PRESS_RV")
    k_raw = raw(snapshot, "MUL_ACT")
    out = []
    for i, t in enumerate(axis):
        tg = inverse_first(prv[i], axis, grv)
        if tg is None:
            out.append(None)
            continue
        ratio = min(max(tg / t, NATIVE_MIN_RATIO), NATIVE_MAX_RATIO)
        out.append(int(math.floor(k_raw[i] / Q14 * ratio * Q14)))
    last = None
    for i in range(len(out)):
        if out[i] is not None:
            last = out[i]
        elif last is not None:
            out[i] = last
    return out


def inverse_first(target, xs, ys):
    for i in range(len(xs) - 1):
        y0, y1 = ys[i], ys[i + 1]
        if y0 == y1 or (y0 == 0 and y1 == 0):
            continue
        if min(y0, y1) <= target <= max(y0, y1):
            return xs[i] + (xs[i + 1] - xs[i]) * (target - y0) / (y1 - y0)
    return None


# --------------------------------------------------------------- motor

def refine(snapshot):
    axis_raw = raw(snapshot, "PETR_INJ_TBP")
    k_raw = raw(snapshot, "MUL_ACT")
    if axis_raw is None or k_raw is None or len(axis_raw) != POINT_COUNT or len(k_raw) != POINT_COUNT:
        return {"available": False, "reason": "EIXO_OU_MUL_ACT_INDISPONIVEL"}
    axis_ms = [v / AXIS_COUNTS_PER_MS for v in axis_raw]
    if any(axis_ms[i + 1] <= axis_ms[i] for i in range(POINT_COUNT - 1)) or axis_ms[0] <= 0:
        return {"available": False, "reason": "EIXO_NAO_CRESCENTE"}
    k_old = [v / Q14 for v in k_raw]
    if any(k <= 0 for k in k_old):
        return {"available": False, "reason": "MUL_ACT_INVALIDO"}
    u = [math.log(t) for t in axis_ms]
    x0 = [math.log(k) for k in k_old]

    petrol_raw = [raw(snapshot, k) for k in ("PETR_INJ_TBUF", "MNFLD_PRESS_BUF", "NUM_BUF_UPD_PETR")]
    gas_raw = [raw(snapshot, k) for k in ("PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS")]
    targets, rejected, petrol, gas = [], [], [], []
    if all(v is not None for v in petrol_raw + gas_raw):
        petrol, rp = monotone_fit(band_points(*petrol_raw))
        gas, rg = monotone_fit(band_points(*gas_raw))
        rejected = [dict(r, fuel="GASOLINA") for r in rp] + [dict(r, fuel="GNV") for r in rg]
        if len(petrol) >= 2 and len(gas) >= 2:
            targets = equivalence_targets(petrol, gas, axis_ms, k_old)
    mature = [t for t in targets if t["w"] >= BAND_MATURE_COUNT / float(BAND_FULL_COUNT)]
    equivalence_available = len(mature) >= MIN_COMMON_MATURE

    observations = []
    if equivalence_available:
        for t in targets:
            observations.append({"a": axis_weights(t["tp"], axis_ms), "y": t["y"], "w": t["w"]})

    evidence = [0.0] * POINT_COUNT
    for o in observations:
        for j, a in o["a"]:
            evidence[j] += o["w"] * a
    spread = [0.0] * POINT_COUNT
    for j in range(POINT_COUNT):
        for k, kern in ((j - 1, 0.25), (j, 0.5), (j + 1, 0.25)):
            if 0 <= k < POINT_COUNT:
                spread[j] += kern * evidence[k]
    gain = [min(1.0, s / EVIDENCE_REF) for s in spread]
    # Peso do K atual cai continuamente com a evidência: o ganho proporcional
    # nasce do próprio balanço evidência × K atual, sem degraus entre nós.
    prior_w = [PRIOR_UNSUPPORTED * (1.0 - g) + PRIOR_SUPPORTED * g for g in gain]
    fitted, robust = whittaker(u, observations, x0, prior_w, LAMBDA)
    for t, r in zip(targets, robust):
        t["robustWeight"] = round(r, 4)
    e_eff = effective_elasticity(x0, u)
    final = enforce_coherence(fitted, x0, u, e_eff)

    out_raw = []
    origins = []
    for j in range(POINT_COUNT):
        factor = min(max(math.exp(final[j]), MIN_FACTOR), MAX_FACTOR)
        r = int(round(factor * Q14))
        r = min(max(r, 0), MAX_RAW)
        out_raw.append(r)
        if gain[j] >= 0.5:
            origins.append("MEASURED")
        elif gain[j] > 0:
            origins.append("BLENDED")
        elif abs(final[j] - x0[j]) > SMOOTH_TOLERANCE_LOG:
            origins.append("SMOOTHED")
        else:
            origins.append("HELD")
            out_raw[j] = k_raw[j]  # sem evidência e sem anomalia: preserva o valor gravado
    out_factors = [r / Q14 for r in out_raw]
    return {
        "available": True,
        "mode": "EQUIVALENCE" if equivalence_available else "POLISH",
        "equivalenceAvailable": equivalence_available,
        "reason": None if equivalence_available else "BANDAS_COMUNS_MADURAS_INSUFICIENTES",
        "matureCommonPoints": len(mature),
        "axisMs": axis_ms,
        "currentRaw": k_raw,
        "refinedRaw": out_raw,
        "origins": origins,
        "gain": [round(g, 4) for g in gain],
        "elasticityLimit": round(e_eff, 4),
        "needsAnotherPass": e_eff > E_MAX + 1e-9,
        "targets": targets,
        "rejectedBands": rejected,
        "metricsBefore": metrics(k_old, axis_ms),
        "metricsAfter": metrics(out_factors, axis_ms),
    }


if __name__ == "__main__":
    import gzip
    import json
    import sys
    data = json.load(gzip.open(sys.argv[1], "rt") if sys.argv[1].endswith(".gz") else open(sys.argv[1]))
    seq = int(sys.argv[2]) if len(sys.argv) > 2 else None
    for snap in data["snapshots"]:
        if seq is not None and snap["sequence"] != seq:
            continue
        result = refine(snap)
        print(snap["sequence"], result["mode"], result.get("reason"), result["matureCommonPoints"])
        print("  before", result["metricsBefore"])
        print("  after ", result["metricsAfter"])
        print("  K  old", [round(v / Q14, 3) for v in result["currentRaw"]])
        print("  K  new", [round(v / Q14, 3) for v in result["refinedRaw"]])
        print("  origin", "".join(o[0] for o in result["origins"]))
        for t in result["targets"]:
            print("   tgt map=%.3f tp=%.2f tg=%.2f ratio=%.3f w=%.2f rw=%s" % (t["map"], t["tp"], t["tg"], t["ratio"], t["w"], t.get("robustWeight")))
        for r in result["rejectedBands"]:
            print("   rejected", r["fuel"], r["band"], round(r["map"], 3), round(r["t"], 3))
