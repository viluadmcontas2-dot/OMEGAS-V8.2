#!/usr/bin/env python3
"""Global-first challengers for the order-independent contextual replay.

The native curve receives the proportional MAP x Tpet error first.  Models in
this file predict only the remainder conditioned on RPM x MAP.  River is a
shadow challenger and never authorizes or writes an ECU calibration.
"""

from __future__ import annotations

import argparse
import csv
import json
import statistics
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from contextual_equivalence_replay import ContextPair
from rpm_residual_replay import rpm_column


MIN_TRAIN = 16
MIN_LOCAL = 8
MIN_VISITS = 3
VISIT_MS = 30_000


@dataclass(frozen=True)
class ResidualPoint:
    pair: ContextPair
    residual_log: float


def global_prediction(training: Sequence[ContextPair], query: ContextPair) -> float | None:
    """Robust global Curve K term near the same native Tpet position."""
    comparable = [pair for pair in training
                  if pair.lineage == query.lineage
                  and pair.epoch == query.epoch
                  and abs(pair.petrol_ref_ms - query.petrol_ref_ms) <= 0.5]
    if not comparable:
        return None
    # Equal regime weighting prevents a high-density local cluster from being
    # mistaken for the fast global correction.
    by_regime: dict[int, list[float]] = defaultdict(list)
    for pair in comparable:
        by_regime[rpm_column(pair.rpm)].append(pair.error_log)
    return statistics.median(statistics.median(values) for values in by_regime.values())


def training_residuals(training: Sequence[ContextPair]) -> list[ResidualPoint]:
    out = []
    for pair in training:
        baseline = global_prediction(training, pair)
        if baseline is not None:
            out.append(ResidualPoint(pair, pair.error_log - baseline))
    return out


def sparse_local_prediction(training: Sequence[ResidualPoint], query: ContextPair) -> tuple[float, bool]:
    """Predict only inside an observed neighborhood; never fill all 144 cells."""
    local = [point for point in training
             if point.pair.lineage == query.lineage
             and point.pair.epoch == query.epoch
             and abs(point.pair.rpm - query.rpm) <= 300.0
             and abs(point.pair.map_bar - query.map_bar) <= 0.075
             and abs(point.pair.petrol_ref_ms - query.petrol_ref_ms) <= 0.75]
    visits = {(point.pair.gas_session, point.pair.gas_t // VISIT_MS) for point in local}
    if len(local) < MIN_LOCAL or len(visits) < MIN_VISITS:
        return 0.0, False
    return statistics.median(point.residual_log for point in local), True


def features(pair: ContextPair) -> dict[str, float]:
    rpm_k = pair.rpm / 1000.0
    return {
        "rpm_k": rpm_k,
        "map": pair.map_bar,
        "petrol_ref_10ms": pair.petrol_ref_ms / 10.0,
        "rpm_x_map": rpm_k * pair.map_bar,
    }


def new_river_model():
    try:
        from river import forest
    except ImportError:
        return None
    return forest.ARFRegressor(
        n_models=8,
        max_features="sqrt",
        grace_period=30,
        leaf_prediction="mean",
        seed=20261013,
    )


def evaluate_pairs(pairs: Sequence[ContextPair], include_river: bool = True) -> dict:
    scopes: dict[tuple[str, int], list[ContextPair]] = defaultdict(list)
    for pair in pairs:
        scopes[(pair.lineage, pair.epoch)].append(pair)

    all_errors: dict[str, list[float]] = defaultdict(list)
    per_session = []
    unsupported = 0
    for (lineage, epoch), members in sorted(scopes.items()):
        by_session: dict[str, list[ContextPair]] = defaultdict(list)
        for pair in members:
            by_session[pair.gas_session].append(pair)
        sessions = sorted(by_session, key=lambda session: min(pair.gas_t for pair in by_session[session]))
        training: list[ContextPair] = []
        for session in sessions:
            test = by_session[session]
            if len(training) >= MIN_TRAIN:
                residual_points = training_residuals(training)
                river_model = new_river_model() if include_river else None
                if river_model is not None:
                    for point in sorted(residual_points, key=lambda item: item.pair.gas_t):
                        river_model.learn_one(features(point.pair), point.residual_log)
                errors: dict[str, list[float]] = defaultdict(list)
                for query in test:
                    global_error = global_prediction(training, query)
                    if global_error is None:
                        unsupported += 1
                        continue
                    target = query.error_log - global_error
                    sparse, supported = sparse_local_prediction(residual_points, query)
                    predictions = {
                        "GLOBAL_ONLY": 0.0,
                        "SPARSE_RPM_MAP": sparse if supported else 0.0,
                    }
                    if river_model is not None:
                        predictions["RIVER_RESIDUAL_SHADOW"] = river_model.predict_one(features(query)) or 0.0
                    for name, prediction in predictions.items():
                        error = abs(target - prediction)
                        errors[name].append(error)
                        all_errors[name].append(error)
                for name, values in errors.items():
                    if values:
                        per_session.append({
                            "lineage": lineage,
                            "epoch": epoch,
                            "session": session,
                            "model": name,
                            "pairs": len(values),
                            "median_abs_log_percent": statistics.median(values) * 100.0,
                            "mean_abs_log_percent": statistics.fmean(values) * 100.0,
                        })
            # The complete held session becomes historical only after prediction.
            training.extend(test)

    summary = {}
    baseline_by_session = {
        (row["lineage"], row["epoch"], row["session"]): row["median_abs_log_percent"]
        for row in per_session if row["model"] == "GLOBAL_ONLY"
    }
    for name, values in sorted(all_errors.items()):
        ordered = sorted(values)
        rows = [row for row in per_session if row["model"] == name]
        summary[name] = {
            "pairs": len(values),
            "sessions": len(rows),
            "median_abs_log_percent": statistics.median(values) * 100.0,
            "mean_abs_log_percent": statistics.fmean(values) * 100.0,
            "p90_abs_log_percent": ordered[min(len(ordered) - 1, int(.9 * len(ordered)))] * 100.0,
            "session_wins_vs_global_only": sum(
                row["median_abs_log_percent"]
                < baseline_by_session[(row["lineage"], row["epoch"], row["session"])]
                for row in rows if name != "GLOBAL_ONLY"
            ),
        }
    return {
        "schema": "omegas-hierarchical-residual-model-comparison-v1",
        "target": "ln(Tpet_GNV/Tpet_gasoline) minus native global curve term",
        "validation": "chronological full-physical-session holdout inside calibration epoch",
        "unsupportedPairs": unsupported,
        "summary": summary,
        "perSession": per_session,
    }


def read_pairs(path: Path) -> list[ContextPair]:
    out = []
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            out.append(ContextPair(
                row["lineage"], int(row["epoch"]), row["gas_session"], int(row["gas_t"]),
                float(row["rpm"]), float(row["map_bar"]), float(row["petrol_ref_ms"]),
                float(row["gas_petrol_ms"]), int(row["petrol_neighbors"]),
                int(row["petrol_t_min"]), int(row["petrol_t_max"]),
            ))
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("pairs", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = evaluate_pairs(read_pairs(args.pairs))
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "perSession"}, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
