#!/usr/bin/env python3
"""Investigação OFFLINE da causalidade MAP_K × equivalência.

NÃO é parte do APK. NÃO envia comandos, NÃO lê dispositivo, NÃO gera lote de escrita.
O ganho do Mapa K não pode ser presumido da escala de exibição 0..255 nem de MUL_ACT.
O ganho hipotético abaixo só pode ser usado em ensaios sintéticos supervisionados.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import io
import json
import math
from pathlib import Path
import zipfile


@dataclass(frozen=True)
class ControlledGain:
    """Sensibilidade d ln(erro de equivalência) / d ln(valor MAP_K), não Q14."""
    elasticity: float
    independent_sessions: int
    sign_consistent: bool = True
    k_curve_unchanged: bool = True
    holdout_improved: bool = True


def controlled_gain(elasticity, sessions, consistent=True, *,
                    k_curve_unchanged=True, holdout_improved=True):
    """Construtor exclusivo do laboratório, NÃO estima nem certifica dados reais."""
    return ControlledGain(elasticity, sessions, consistent, k_curve_unchanged, holdout_improved)


def proportional_target_percent(ratio, gain: ControlledGain | None, *,
                                damping=0.5, max_step_pct=5.0):
    """Protótipo matemático: erro ln(Tp_GNV/Tp_gasolina), sinal conforme beta medido.

    delta(ln MAP_K) = - damping * ln(ratio) / beta.
    Sem três sessões controladas independentes e holdout, ABSTER-SE (None).
    Não confundir percentagem de MAP_K com percentagem de mistura/gás.
    """
    if not math.isfinite(ratio) or ratio <= 0 or gain is None:
        return None
    beta = gain.elasticity
    if (not math.isfinite(beta) or abs(beta) < 0.05
        or gain.independent_sessions < 3 or not gain.sign_consistent
        or not gain.k_curve_unchanged or not gain.holdout_improved
        or not 0 < damping <= 0.5 or not 0 < max_step_pct <= 5):
        return None
    delta_log = -damping * math.log(ratio) / beta
    return max(-max_step_pct, min(max_step_pct, 100.0 * math.expm1(
        max(-1.0, min(1.0, delta_log)))))


@dataclass(frozen=True)
class ControlledTrial:
    """Uma intervenção manual confirmada com região CONTROLE não alterada.

    Todos os pares vêm de situações equivalentes ANTES/DEPOIS nos dois
    combustíveis. matched/stable/episodes DEVEM ser aferidos do log original.
    """
    session_id: str
    target_before_ratio: float
    target_after_ratio: float
    control_before_ratio: float
    control_after_ratio: float
    map_raw_before: int
    map_raw_after: int
    matched: bool = True
    curve_stable: bool = True
    automatch_stable: bool = True
    independent_episodes: int = 2


def estimate_controlled_gain(trials: list[ControlledTrial]) -> ControlledGain | None:
    """Diferenças-em-diferenças em ln, mediana robusta e holdout leave-one-out.

    São necessários >=4 ensaios independentes e mudanças MAP_K em ambas
    direções. Não extrapola para regiões não ensaiadas. Modelo OFFLINE.
    """
    from statistics import median

    if len(trials) < 4 or len({t.session_id for t in trials}) != len(trials):
        return None
    effects = []
    signs = set()
    for t in trials:
        vals = (t.target_before_ratio, t.target_after_ratio,
                t.control_before_ratio, t.control_after_ratio)
        if (not t.matched or not t.curve_stable or not t.automatch_stable
            or t.independent_episodes < 2 or
            any(not math.isfinite(v) or v <= 0 for v in vals)
            or not 1 <= t.map_raw_before <= 255 or not 1 <= t.map_raw_after <= 255
            or t.map_raw_before == t.map_raw_after):
            return None
        x = math.log(t.map_raw_after / t.map_raw_before)
        # Subtrai a deriva comum de uma região não tocada da MESMA sessão.
        y = (math.log(t.target_after_ratio / t.target_before_ratio)
             - math.log(t.control_after_ratio / t.control_before_ratio))
        beta_i = y / x
        if not math.isfinite(beta_i) or abs(beta_i) < 0.05:
            return None
        signs.add(1 if x > 0 else -1)
        effects.append((x, y, beta_i))
    if len(signs) < 2:
        return None
    betas = [b for _, _, b in effects]
    beta = median(betas)
    if abs(beta) < 0.05 or any(b * beta <= 0 for b in betas):
        return None
    # Cada sessão precisa ser prevista pelo conjunto das OUTRAS sessões.
    prediction_errors = []
    baseline_errors = []
    for i, (x, y, _) in enumerate(effects):
        fitted = median([b for j, (_, _, b) in enumerate(effects) if j != i])
        err = abs(y - fitted * x)
        if err > max(0.01, 0.35 * abs(y)):
            return None
        prediction_errors.append(err)
        baseline_errors.append(abs(y))
    if sum(prediction_errors) >= 0.5 * sum(baseline_errors):
        return None
    return ControlledGain(beta, len(trials))


def _eligible(event):
    if event.get("type") != "telemetry":
        return None
    d = event.get("data") or {}
    if d.get("fuel") not in ("GASOLINA", "GNV") or d.get("sample_state") != "SAMPLE_ACCEPTED":
        return None
    rpm, ms, load = (d.get(key) for key in ("rpm", "petrol_ms", "load_bar"))
    if not all(isinstance(v, (int, float)) and math.isfinite(v) for v in (rpm, ms, load)):
        return None
    # Fronteira de condução já usada por EquivalenceLedger (lenta tem modelo próprio).
    if rpm < 1200 or ms <= 0 or not 0 < load <= 1.15:
        return None
    return d["fuel"]


def audit_events(events):
    """Inventário temporal de evidência. NUNCA declara efeito causal identificado.

    Primeira intervenção MAP_K confirmada por readback; segmento posterior termina
    na próxima alteração de MUL_ACT/MAP_K ou AutoMatch, que muda o tratamento.
    """
    ordered = sorted(events, key=lambda o: int(o.get("sequence", 0)))
    confirmed = [e for e in ordered if e.get("type") == "k_batch_confirmed"
                 and (e.get("data") or {}).get("calibrationType") == "MAP_K"
                 and (e.get("data") or {}).get("readbackValid") is True
                 and (e.get("data") or {}).get("humanConfirmed") is True
                 and any(c.get("current") != c.get("target")
                         for c in ((e.get("data") or {}).get("cells") or []))]
    counts = lambda: {"GNV": 0, "GASOLINA": 0}
    before, after = counts(), counts()
    kinds = ("k_factor_batch_confirmed", "k_batch_confirmed",
             "autocal_native_automatch_epoch", "autocal_native_calibration_epoch")
    later_curve = False
    if confirmed:
        first = confirmed[0]
        sequence = int(first.get("sequence", 0))
        mutations_before = [int(e.get("sequence", 0)) for e in ordered if
                            e.get("type") in kinds and int(e.get("sequence", 0)) < sequence]
        start = max(mutations_before) if mutations_before else -1
        after_mutations = [int(e.get("sequence", 0)) for e in ordered if
                           e.get("type") in kinds and int(e.get("sequence", 0)) > sequence]
        stop = min(after_mutations) if after_mutations else float("inf")
        later_curve = any(e.get("type") == "k_factor_batch_confirmed" and
                          int(e.get("sequence", 0)) > sequence for e in ordered)
        for e in ordered:
            fuel = _eligible(e)
            if fuel is None:
                continue
            n = int(e.get("sequence", 0))
            if start < n < sequence:
                before[fuel] += 1
            elif sequence < n < stop:
                after[fuel] += 1
    blockers = []
    if not confirmed:
        blockers.append("SEM_MAP_K_CONFIRMADO")
    else:
        for phase, sample in (("ANTES", before), ("DEPOIS", after)):
            for fuel in ("GASOLINA", "GNV"):
                if sample[fuel] < 12:
                    blockers.append(f"SEM_{fuel}_{phase}")
        # 12 quadros não são 12 visitas; exigir ensaios independentes por sessão.
        blockers.append("SEM_CONTROLE_CAUSAL_INDEPENDENTE")
    return {
        "status": "NAO_IDENTIFICADO", "interventions": len(confirmed),
        "before": before, "after": after,
        "curve_changed_after_map": later_curve,
        "map_cells": len((confirmed[0].get("data") or {}).get("cells") or []) if confirmed else 0,
        "blockers": blockers,
        "ecu_write_started": False,
    }


def _events_from_jsonl(raw):
    return [json.loads(line) for line in raw.splitlines() if line.strip()]


def inspect_archive(path: Path):
    """Também aceita a pasta ZIP externa contendo vários ZIPs de sessões."""
    if not zipfile.is_zipfile(path):
        return [{"name": path.name, **audit_events(_events_from_jsonl(path.read_bytes()))}]
    out = []
    with zipfile.ZipFile(path) as archive:
        for member in archive.namelist():
            if member.endswith(".zip"):
                with zipfile.ZipFile(io.BytesIO(archive.read(member))) as session:
                    for path_in in session.namelist():
                        if path_in.endswith(".jsonl"):
                            out.append({"name": Path(member).name,
                                        **audit_events(_events_from_jsonl(session.read(path_in)))})
            elif member.endswith(".jsonl"):
                out.append({"name": Path(member).name,
                            **audit_events(_events_from_jsonl(archive.read(member)))})
    return out


def main():
    p = argparse.ArgumentParser(description="Diagnóstico de MAP_K sem escrita na ECU")
    p.add_argument("session", type=Path, help="ZIP de sessão ou JSONL exportado")
    args = p.parse_args()
    print(json.dumps({"source": args.session.name, "sessions": inspect_archive(args.session)},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
