#!/usr/bin/env python3
"""Lote H: validação cruzada grosso × fino nas sessões reais (decide FINE_BINS_ENABLED).

Para cada (sessão, snapshot) com evidência de condução suficiente:
  * deixa-uma-faixa-de-fora (LOBO): tira todos os pares de uma das 18 faixas e prevê a Curva K proposta nessa faixa;
  * deixa-um-episódio-de-fora (LOEO): tira todos os pares de um episódio e prevê a Curva K nele;
  * erro = RMS de ln K previsto − ln K pedido por cada par retido (alvo = K(t_gnv)·t_gnv/t_gas, blind_telemetry_test);
  * aspereza (roughness) e mudanças de sinal da inclinação da curva proposta com todos os dados;
  * reversões de sinal entre snapshots consecutivos e nº de propostas (mesma conta de test_refined_autocal_hysteresis).
"grosso" = caminho de produção até o Lote G (pares → portões por faixa grossa → alvos por par).
"fino"   = 54 bins, portões por faixa de 18, perfil suavizado → alvos nos nós do eixo.

Uso: python3 tools/autocal_refine/fine_bins_cv.py [--json] [--min-pairs 30]
"""
import datetime
import gzip
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fine_bins as fb  # noqa: E402
import refined_oracle as oracle  # noqa: E402

REAL = Path(__file__).resolve().parents[2] / "fixtures/autocal/real"
SESSIONS = ("automatch_2026-10-01_1301", "ref_2026-10-01_1719", "gnv_only_2026-09-30_0931")
BUFFER_KEYS = ("PETR_INJ_TBUF", "MNFLD_PRESS_BUF", "NUM_BUF_UPD_PETR", "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS", "NUM_BUF_UPD_GAS")


def load(name):
    with gzip.open(REAL / f"{name}.json.gz", "rt", encoding="utf-8") as handle:
        return json.load(handle)


def write_times(data):
    return sorted(datetime.datetime.fromisoformat(w["recordedAtUtc"].replace("Z", "+00:00")).timestamp() * 1000
                  for w in data["kFactorWrites"])


def epoch_start(data, at_ms):
    prior = [t for t in write_times(data) if t <= at_ms]
    return prior[-1] if prior else None


def without_buffers(snapshot):
    return dict(snapshot, fields=[f for f in snapshot["fields"] if f["key"] not in BUFFER_KEYS])


def setups(min_pairs):
    out = []
    for name in SESSIONS:
        data = load(name)
        for snap in data["snapshots"]:
            if snap.get("temporalCoherent") is False:
                continue
            at = snap["capturedAtMs"]
            ref = fb.ecu_reference_from_snapshot(snap)
            pairs = fb.ledger_pairs(data["telemetry"], epoch_start(data, at), at, ref)
            if len(pairs) >= min_pairs:
                out.append({"session": name, "seq": snap["sequence"], "snap": snap, "pairs": pairs})
    return out


def propose(variant, snap, pairs, hold=oracle.HOLD_MIN_STEP_LOG, scale=None):
    if variant == "coarse":
        return oracle.refine(snap, telemetry_pairs=[(p[0], p[1]) for p in pairs], telemetry_episodes=[p[2] for p in pairs],
                             hold_log=hold, point_gain_scale=scale)
    bins = fb.aggregate([(p[0], p[1], p[2], p[3]) for p in pairs])
    return oracle.refine(snap, hold_log=hold, point_gain_scale=scale, fine_bins=bins)


def truth_and_pred(result, held):
    """Erros ln K (previsto − pedido) da curva proposta (ou da atual, sem proposta) nos pares retidos."""
    axis = result["axisMs"]
    current = [v / oracle.Q14 for v in result["currentRaw"]]
    refined = [v / oracle.Q14 for v in result["refinedRaw"]]
    errs, base = [], []
    for tp, tg, *_ in held:
        y = math.log(oracle.interp(tg, axis, current) * tg / tp)
        errs.append(math.log(oracle.interp(tp, axis, refined)) - y)
        base.append(math.log(oracle.interp(tp, axis, current)) - y)
    return errs, base


def rms(values):
    return math.sqrt(sum(v * v for v in values) / len(values)) if values else None


def fold_error(variant, snap, pairs, group_of):
    """RMS pooled sobre todas as dobras. group_of(pair) → chave da dobra."""
    keys = sorted({group_of(p) for p in pairs}, key=str)
    errs, base, folds, proposing = [], [], 0, 0
    for key in keys:
        held = [p for p in pairs if group_of(p) == key]
        train = [p for p in pairs if group_of(p) != key]
        result = propose(variant, snap, train)
        if not result.get("available"):
            continue
        e, b = truth_and_pred(result, held)
        errs += e
        base += b
        folds += 1
        proposing += 1 if result["mode"] == "EQUIVALENCE" and result["refinedRaw"] != result["currentRaw"] else 0
    return rms(errs), rms(base), len(errs), folds, proposing


def band_of(pair):
    idx = fb.fine_index(pair[0])
    return None if idx is None else fb.band_of_bin(idx)


def sequence_metrics(variant, data, hold=oracle.HOLD_MIN_STEP_LOG, gain=True):
    """Reversões de sinal e nº de passos propostos ao longo dos snapshots consecutivos (por época)."""
    passes = [0] * 30
    last = [0.0] * 30
    epoch = None
    reversals = steps = 0
    for snap in data["snapshots"]:
        if snap.get("temporalCoherent") is False:
            continue
        at = snap["capturedAtMs"]
        ep = epoch_start(data, at)
        if ep != epoch:
            epoch, passes, last = ep, [0] * 30, [0.0] * 30
        pairs = fb.ledger_pairs(data["telemetry"], ep, at, fb.ecu_reference_from_snapshot(snap))
        scale = [oracle.pass_gain(p) for p in passes] if gain else None
        result = propose(variant, snap, pairs, hold, scale)
        if result["mode"] != "EQUIVALENCE":
            continue
        for j in range(30):
            if result["origins"][j] == "HELD" or result["refinedRaw"][j] == result["currentRaw"][j]:
                continue
            delta = math.log(result["refinedRaw"][j] / result["currentRaw"][j])
            passes[j] += 1
            steps += 1
            if last[j] and (delta > 0) != (last[j] > 0):
                reversals += 1
            last[j] = delta
    return reversals, steps


def evaluate(min_pairs=30, with_buffers=True):
    rows = []
    for s in setups(min_pairs):
        snap = s["snap"] if with_buffers else without_buffers(s["snap"])
        row = {"session": s["session"], "seq": s["seq"], "pairs": len(s["pairs"]),
               "episodes": len({p[2] for p in s["pairs"]})}
        for variant in ("coarse", "fine"):
            full = propose(variant, snap, s["pairs"])
            row[variant] = {
                "mode": full["mode"],
                "roughness": (full.get("metricsAfter") or {}).get("roughness"),
                "signChanges": (full.get("metricsAfter") or {}).get("slopeSignChanges"),
                "moved": sum(1 for a, b in zip(full.get("refinedRaw", []), full.get("currentRaw", [])) if a != b),
            }
            row[variant]["lobo"] = fold_error(variant, snap, s["pairs"], band_of)
            row[variant]["loeo"] = fold_error(variant, snap, s["pairs"], lambda p: p[2])
        rows.append(row)
    return rows


def pct(v):
    return "  n/a " if v is None else f"{(math.exp(v) - 1) * 100:5.1f}%"


def report(rows, title):
    print(f"\n== {title} ==")
    print("sessão/seq            pares ep | LOBO  base  grosso  fino  | LOEO  base  grosso  fino  | rugos. g/f | mud.sinal g/f | pontos g/f")
    tot = {"lobo": {"coarse": [0.0, 0], "fine": [0.0, 0], "base": [0.0, 0]}, "loeo": {"coarse": [0.0, 0], "fine": [0.0, 0], "base": [0.0, 0]}}
    for r in rows:
        c, f = r["coarse"], r["fine"]
        line = f"{r['session'][:14]:14s}#{r['seq']:<5d} {r['pairs']:4d} {r['episodes']:2d} |"
        for kind in ("lobo", "loeo"):
            ce, cb, cn, _, _ = c[kind]
            fe, _, fn, _, _ = f[kind]
            line += f" {cn:4d} {pct(cb)} {pct(ce)} {pct(fe)} |"
            for key, e, n in (("coarse", ce, cn), ("fine", fe, fn), ("base", cb, cn)):
                if e is not None:
                    tot[kind][key][0] += e * e * n
                    tot[kind][key][1] += n
        line += f" {c['roughness']}/{f['roughness']} | {c['signChanges']}/{f['signChanges']} | {c['moved']}/{f['moved']}"
        print(line)
    for kind in ("lobo", "loeo"):
        parts = {k: (math.sqrt(v[0] / v[1]) if v[1] else None) for k, v in tot[kind].items()}
        print(f"  TOTAL {kind.upper()}: base {pct(parts['base'])}  grosso {pct(parts['coarse'])}  fino {pct(parts['fine'])}")
    return tot


if __name__ == "__main__":
    min_pairs = 30
    if "--min-pairs" in sys.argv:
        min_pairs = int(sys.argv[sys.argv.index("--min-pairs") + 1])
    rows_full = evaluate(min_pairs, True)
    report(rows_full, "snapshot real completo (buffers nativos + condução)")
    rows_tel = evaluate(min_pairs, False)
    report(rows_tel, "só condução (sem buffers nativos)")
    print("\nsequência de snapshots (reversões de sinal, passos propostos):")
    for name in SESSIONS:
        data = load(name)
        print(f"  {name}: grosso {sequence_metrics('coarse', data)}  fino {sequence_metrics('fine', data)}")
    if "--json" in sys.argv:
        print(json.dumps({"full": rows_full, "telemetryOnly": rows_tel}, default=str))
