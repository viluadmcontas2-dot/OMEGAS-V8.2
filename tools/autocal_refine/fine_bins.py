#!/usr/bin/env python3
"""Lote H: evidência em bins finos (54 = 3 × as 18 faixas), exibição/consumo em 18.

Espelho de `FineBins.kt` (agregação e JSON `bands18`) e do trecho do livro que gera os pares
(`EquivalenceLedger.computePairs`/`drivingPairs`). Puro, sem numpy.

Ideia do dono: a ECU colhe só 18 faixas; o app colhe ~3x mais fino para ter uma curva mais
consistente, mas mostra só 18. Cada faixa de 18 contém exatamente 3 bins finos.

Grade: 54 bins espaçados em ln(ms) de 3,0 a 12,0 ms (a faixa de trabalho do livro: TELEMETRY_MIN_MS
até a faixa de cauda). Razão geométrica 4^(1/54) = 1,0261 por bin (2,6%): menor que o passo mais fino do
eixo K de 30 pontos (≥ 4,9% entre 9,5 e 10 ms), logo ≥ 1,9× mais fino que o eixo e 3× mais fino que a ECU.
Log-espaçado porque o K é função de ln(ms) (Whittaker em u = ln t) e o erro de medida é relativo.
As bordas são arredondadas a 4 casas e escritas literalmente no Kotlin: nada de pow() divergindo entre
JVM e libm na fronteira de um bin.
"""
import bisect
import math

GRID_LO_MS = 3.0
GRID_HI_MS = 12.0
BAND_COUNT = 18
FINE_PER_BAND = 3
FINE_COUNT = BAND_COUNT * FINE_PER_BAND
FINE_EDGES = [round(GRID_LO_MS * (GRID_HI_MS / GRID_LO_MS) ** (k / FINE_COUNT), 4) for k in range(FINE_COUNT + 1)]
FINE_EDGES[0] = GRID_LO_MS
FINE_EDGES[-1] = GRID_HI_MS
#: Leituras recentes guardadas por bin (reservatório): memória limitada, estatística robusta.
RESERVOIR = 32
#: Episódios que uma faixa de 18 precisa ter (= AutoMatchRefinedEngine.MIN_BAND_EPISODES).
MIN_BAND_EPISODES = 1
#: Bin com menos pares que isto não é evidência (= AutoMatchRefinedEngine.BAND_MATURE_COUNT).
BIN_MATURE_COUNT = 3
CONF_EPISODES_FULL = 6.0
CONF_SAMPLES_FULL = 24.0
CONF_DISPERSION_REF = 0.08
CONF_EPISODES_UNKNOWN = 0.3
DISPERSION_UNKNOWN = 0.10
DRIVING_MIN_RPM = 1200.0  # = EquivalenceLedger.DRIVING_MIN_RPM (fronteira lenta × condução)
TELEMETRY_MIN_MS = 3.0


def fine_index(tp):
    """Bin fino do petrol de referência (ms), ou None fora de [3, 12)."""
    if not (GRID_LO_MS <= tp < GRID_HI_MS):
        return None
    return min(bisect.bisect_right(FINE_EDGES, tp) - 1, FINE_COUNT - 1)


def _median(values):
    ordered = sorted(values)
    return ordered[len(ordered) // 2]


def aggregate(pairs):
    """pairs = [(petrolRefMs, gasMs, episode, ecuRef)] em ordem de chegada → 54 bins (dict).

    n = total de pares do bin; estatística sobre os últimos RESERVOIR pares. Mediana = elemento n//2 do
    ordenado (convenção do livro). Vazio → medianLn None."""
    reservoirs = [[] for _ in range(FINE_COUNT)]
    counts = [0] * FINE_COUNT
    for tp, tg, episode, ecu in pairs:
        idx = fine_index(tp)
        if idx is None or not math.isfinite(tg) or not math.isfinite(tp) or tg <= 0 or tp <= 0:
            continue
        counts[idx] += 1
        reservoirs[idx].append((math.log(tg / tp), tp, episode, bool(ecu)))
        if len(reservoirs[idx]) > RESERVOIR:
            reservoirs[idx].pop(0)
    bins = []
    for i in range(FINE_COUNT):
        res = reservoirs[i]
        bin_ = {"index": i, "fromMs": FINE_EDGES[i], "toMs": FINE_EDGES[i + 1], "n": counts[i],
                "medianLn": None, "ratio": None, "dispersion": None, "episodes": None, "episodeIds": [],
                "ecuShare": 0.0, "petrolMs": None}
        if res:
            med = _median([r[0] for r in res])
            bin_["medianLn"] = med
            bin_["ratio"] = math.exp(med)
            if len(res) >= 2:
                bin_["dispersion"] = _median([abs(r[0] - med) for r in res]) * 1.4826
            known = all(r[2] >= 0 for r in res)
            bin_["episodeIds"] = sorted({r[2] for r in res}) if known else []
            bin_["episodes"] = len(bin_["episodeIds"]) if known else None
            bin_["ecuShare"] = sum(1 for r in res if r[3]) / len(res)
            bin_["petrolMs"] = _median([r[1] for r in res])
        bins.append(bin_)
    return bins


def band_of_bin(index):
    return index // FINE_PER_BAND


def band_summary(bins, band):
    """Agregado de uma faixa de 18: razão ponderada por amostras (em ln), episódios (união), dispersão, confiança."""
    members = bins[band * FINE_PER_BAND:(band + 1) * FINE_PER_BAND]
    samples = sum(b["n"] for b in members)
    out = {"index": band, "fromMs": members[0]["fromMs"], "toMs": members[-1]["toMs"], "samples": samples,
           "ratio": None, "episodes": None, "dispersion": None, "confidence": 0.0, "ecuShare": 0.0}
    if samples == 0:
        return out
    filled = [b for b in members if b["n"] > 0]
    out["ratio"] = math.exp(sum(b["n"] * b["medianLn"] for b in filled) / samples)
    known = all(b["episodes"] is not None for b in filled)
    if known:
        out["episodes"] = len({e for b in filled for e in b["episodeIds"]})
    disp = [(b["n"], b["dispersion"]) for b in filled if b["dispersion"] is not None]
    if disp:
        out["dispersion"] = sum(n * d for n, d in disp) / sum(n for n, _ in disp)
    out["ecuShare"] = sum(b["n"] * b["ecuShare"] for b in filled) / samples
    out["confidence"] = confidence(samples, out["episodes"], out["dispersion"])
    return out


def confidence(samples, episodes, dispersion):
    """0..1: trechos (episódios) × dispersão × quantidade. Sem episódios conhecidos vale pouco (0,3)."""
    if samples <= 0:
        return 0.0
    conf_e = CONF_EPISODES_UNKNOWN if episodes is None else min(1.0, episodes / CONF_EPISODES_FULL)
    conf_s = min(1.0, samples / CONF_SAMPLES_FULL)
    d = DISPERSION_UNKNOWN if dispersion is None else dispersion
    conf_d = 1.0 / (1.0 + (d / CONF_DISPERSION_REF) ** 2)
    return conf_e * conf_d * (0.5 + 0.5 * conf_s)


def r5(value):
    return None if value is None else round(value, 5)


def bands18_json(bins):
    """Lista de 18 dicts, na ordem das chaves do Kotlin (comparação textual no teste de paridade)."""
    out = []
    for band in range(BAND_COUNT):
        s = band_summary(bins, band)
        fine = []
        for b in bins[band * FINE_PER_BAND:(band + 1) * FINE_PER_BAND]:
            fine.append({"index": b["index"], "fromMs": b["fromMs"], "toMs": b["toMs"], "ratio": r5(b["ratio"]),
                         "samples": b["n"], "episodes": b["episodes"] if b["n"] else None,
                         "dispersion": r5(b["dispersion"]), "ecuShare": round(b["ecuShare"], 4),
                         "ownShare": round(1.0 - b["ecuShare"], 4) if b["n"] else 0.0,
                         "petrolMs": r5(b["petrolMs"])})
        out.append({"fromMs": s["fromMs"], "toMs": s["toMs"], "ratio": r5(s["ratio"]), "samples": s["samples"],
                    "episodes": s["episodes"], "confidence": round(s["confidence"], 4),
                    "dispersion": r5(s["dispersion"]), "ecuShare": round(s["ecuShare"], 4), "fineBins": fine})
    return out


def bin_confidence(b):
    return confidence(b["n"], b["episodes"], b["dispersion"])


def between_summary(bins, members, from_ms, to_ms):
    """Agregado de bins finos num intervalo: razão em ln com peso amostras × confiança do bin; nulo sem dado."""
    filled = [bins[i] for i in members if bins[i]["n"] > 0]
    samples = sum(b["n"] for b in filled)
    out = {"fromMs": from_ms, "toMs": to_ms, "samples": samples, "ratio": None, "episodes": None,
           "confidence": 0.0, "dispersion": None, "ecuShare": 0.0}
    if samples == 0:
        return out
    weights = [b["n"] * bin_confidence(b) for b in filled]
    total = sum(weights)
    if total <= 0:
        weights, total = [float(b["n"]) for b in filled], float(samples)
    out["ratio"] = math.exp(sum(w * b["medianLn"] for w, b in zip(weights, filled)) / total)
    if all(b["episodes"] is not None for b in filled):
        out["episodes"] = len({e for b in filled for e in b["episodeIds"]})
    disp = [(b["n"], b["dispersion"]) for b in filled if b["dispersion"] is not None]
    if disp:
        out["dispersion"] = sum(n * d for n, d in disp) / sum(n for n, _ in disp)
    out["ecuShare"] = sum(b["n"] * b["ecuShare"] for b in filled) / samples
    out["confidence"] = confidence(samples, out["episodes"], out["dispersion"])
    return out


def between_members():
    """Intervalos ENTRE os pontos da ECU: a ECU mostra 18 valores (o bin do meio de cada faixa, 3i+1). Entre dois
    pontos vizinhos ficam exatamente 2 bins finos (3i+2 e 3i+3); abaixo do primeiro ponto, o bin 0; acima do último, o 53.
    Retorna [(kind, from_index, to_index_exclusive)]."""
    out = [("open-low", 0, 1)]
    for i in range(BAND_COUNT - 1):
        out.append(("gap", 3 * i + 2, 3 * i + 4))
    out.append(("open-high", FINE_COUNT - 1, FINE_COUNT))
    return out


def between_json(bins):
    """Entradas entre os pontos da ECU (17 intervalos) + pontas abertas só quando têm evidência. Mesmo formato de bands18."""
    out = []
    for kind, a, b in between_members():
        members = list(range(a, b))
        s = between_summary(bins, members, bins[a]["fromMs"], bins[b - 1]["toMs"])
        if kind != "gap" and s["samples"] == 0:
            continue
        fine = [{"index": bins[i]["index"], "fromMs": bins[i]["fromMs"], "toMs": bins[i]["toMs"], "ratio": r5(bins[i]["ratio"]),
                 "samples": bins[i]["n"], "episodes": bins[i]["episodes"] if bins[i]["n"] else None,
                 "dispersion": r5(bins[i]["dispersion"]), "ecuShare": round(bins[i]["ecuShare"], 4),
                 "ownShare": round(1.0 - bins[i]["ecuShare"], 4) if bins[i]["n"] else 0.0, "petrolMs": r5(bins[i]["petrolMs"])}
                for i in members]
        out.append({"kind": kind, "fromMs": s["fromMs"], "toMs": s["toMs"], "ratio": r5(s["ratio"]), "samples": s["samples"],
                    "episodes": s["episodes"], "confidence": round(s["confidence"], 4), "dispersion": r5(s["dispersion"]),
                    "ecuShare": round(s["ecuShare"], 4), "fineBins": fine})
    return out


# ------------------------------------------------------- pares do livro (espelho do Kotlin)

STABLE_RPM = 150
MATCH_RPM = 150
MATCH_MAP = 0.02
ECU_REF_MIN_POINTS = 6
ECU_REF_MIN_SPAN_BAR = 0.20
ECU_REF_MARGIN_BAR = 0.03


def ecu_reference(points):
    """Espelho de EquivalenceLedger.setEcuPetrolReference: [(mapBar, ms)] → lista limpa ou []."""
    clean = {}
    for m, t in points:
        if 0.05 <= m <= 2.5 and 1.0 <= t <= 40.0:
            clean.setdefault(round(m * 1000), []).append((m, t))
    ref = sorted((sum(m for m, _ in g) / len(g), sum(t for _, t in g) / len(g)) for g in clean.values())
    if len(ref) >= ECU_REF_MIN_POINTS and ref[-1][0] - ref[0][0] >= ECU_REF_MIN_SPAN_BAR:
        return ref
    return []


def ecu_reference_at(map_bar, ref):
    if not ref or map_bar < ref[0][0] - ECU_REF_MARGIN_BAR or map_bar > ref[-1][0] + ECU_REF_MARGIN_BAR:
        return None
    if map_bar <= ref[0][0]:
        return ref[0][1]
    if map_bar >= ref[-1][0]:
        return ref[-1][1]
    for (m0, t0), (m1, t1) in zip(ref, ref[1:]):
        if m0 <= map_bar <= m1:
            return t0 + (t1 - t0) * (map_bar - m0) / (m1 - m0) if m1 > m0 else t0
    return None


def ecu_reference_from_snapshot(snapshot):
    """Pontos maduros dos buffers de gasolina do snapshot (MAP×ms, contagem ≥ 3): faz as vezes da curva da ECU."""
    fields = {f["key"]: f for f in snapshot["fields"] if f.get("status") == "VALID"}
    try:
        t = fields["PETR_INJ_TBUF"]["rawValues"]
        m = fields["MNFLD_PRESS_BUF"]["rawValues"]
        c = fields["NUM_BUF_UPD_PETR"]["rawValues"]
    except KeyError:
        return []
    return ecu_reference([(m[i] / 1024.0, t[i] / 512.0) for i in range(18) if c[i] >= BIN_MATURE_COUNT and t[i] > 0 and m[i] > 0])


def ledger_pairs(telemetry, gas_from=None, until_ms=None, ecu_ref=None):
    """Pares de condução (petrolRefMs, gasMs, episódio, ecuRef, rpm, map) como EquivalenceLedger.drivingPairs().

    A gasolina vale a sessão até [until_ms]; o GNV só dentro da época [gas_from, until_ms] (a curva K muda → o
    livro descarta o GNV anterior). Retenção por célula e episódios como o livro."""
    import blind_telemetry_test as blind
    petrol = blind.cap_cells(blind.stable_frames(telemetry, "GASOLINA", until_ms))
    gas_tel = [f for f in telemetry if gas_from is None or f["t"] >= gas_from]
    gas = blind.cap_cells(blind.tag_episodes(blind.stable_frames(gas_tel, "GNV", until_ms)))
    out = []
    for g in gas:
        matches = sorted(p["t"] for p in petrol if abs(p["rpm"] - g["rpm"]) <= MATCH_RPM and abs(p["map"] - g["map"]) <= MATCH_MAP
                         and (p["rpm"] >= DRIVING_MIN_RPM) == (g["rpm"] >= DRIVING_MIN_RPM))
        if len(matches) >= 2:
            tp, ecu = matches[len(matches) // 2], False
        else:
            tp = ecu_reference_at(g["map"], ecu_ref or [])
            ecu = True
            if tp is None:
                continue
        if g["rpm"] >= DRIVING_MIN_RPM and tp >= TELEMETRY_MIN_MS:
            out.append((tp, g["t"], g["at"], ecu, g["rpm"], g["map"]))
    # episódio = visita à faixa (lacuna >= 60 s entre pares da mesma faixa), como EvidencePairs.withVisitIds
    ids = blind.visit_ids([(o[0], o[2]) for o in out])
    return [(o[0], o[1], i, o[3], o[4], o[5]) for o, i in zip(out, ids)]
