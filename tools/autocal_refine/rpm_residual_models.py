#!/usr/bin/env python3
"""Session-holdout challengers for the Obsidian RPM residual replay."""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Sequence

from rpm_residual_replay import Pair, classify, lineage_of, petrol_band, rpm_column


MIN_TRAIN = 16
MIN_LOCAL = 8


def median(values):
    values = sorted(values)
    return values[len(values) // 2] if values else 0.0


def comparable(training: Sequence[Pair], query: Pair) -> list[Pair]:
    close = [pair for pair in training if abs(pair.petrol_ref_ms - query.petrol_ref_ms) <= 0.5]
    if len(close) >= MIN_LOCAL:
        return close
    band = petrol_band(query.petrol_ref_ms)
    same_band = [pair for pair in training if petrol_band(pair.petrol_ref_ms) == band]
    return same_band if same_band else list(training)


def local_median_prediction(training: Sequence[Pair], query: Pair, rpm_local: bool) -> float:
    base = comparable(training, query)
    if rpm_local:
        local = [pair for pair in base if rpm_column(pair.rpm) == rpm_column(query.rpm)]
        if len(local) >= MIN_LOCAL:
            base = local
    return median(pair.error_log for pair in base)


def continuous_prediction(training: Sequence[Pair], query: Pair) -> float:
    ranked = sorted(training, key=lambda pair:
        ((pair.rpm - query.rpm) / 500.0) ** 2
        + ((pair.petrol_ref_ms - query.petrol_ref_ms) / 0.5) ** 2
        + ((pair.map - query.map) / 0.05) ** 2)
    local = ranked[:64]
    if len(local) < MIN_LOCAL:
        return local_median_prediction(training, query, rpm_local=False)
    return median(pair.error_log for pair in local)


def features(pair: Pair) -> dict[str, float]:
    rpm = pair.rpm / 1000.0
    return {
        "rpm_k": rpm,
        "map": pair.map,
        "petrol_10ms": pair.petrol_ref_ms / 10.0,
        "rpm_x_map": rpm * pair.map,
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


def evaluate_pairs(pairs: Sequence[Pair], include_river: bool = True) -> dict:
    scopes: dict[tuple[str, int], list[Pair]] = defaultdict(list)
    for pair in pairs:
        scopes[(lineage_of(pair.session), pair.epoch)].append(pair)

    per_session = []
    all_errors: dict[str, list[float]] = defaultdict(list)
    for (lineage, epoch), scope_pairs in sorted(scopes.items()):
        by_session: dict[str, list[Pair]] = defaultdict(list)
        for pair in scope_pairs:
            by_session[pair.session].append(pair)
        sessions = sorted(by_session, key=lambda session: min(pair.t for pair in by_session[session]))
        training: list[Pair] = []
        river_model = new_river_model() if include_river else None
        for session in sessions:
            test = sorted(by_session[session], key=lambda pair: pair.t)
            if len(training) >= MIN_TRAIN:
                incumbent_groups = classify(training, scope=lineage)
                local_errors = {(group.column, group.band): group.error_log for group in incumbent_groups if group.local}
                errors: dict[str, list[float]] = defaultdict(list)
                for pair in test:
                    global_prediction = local_median_prediction(training, pair, rpm_local=False)
                    key = (rpm_column(pair.rpm), petrol_band(pair.petrol_ref_ms))
                    predictions = {
                        "ZERO": 0.0,
                        "GLOBAL_MEDIAN": global_prediction,
                        "INCUMBENT": local_errors.get(key, global_prediction),
                        "RPM_BIN_MEDIAN": local_median_prediction(training, pair, rpm_local=True),
                        "CONTINUOUS_RPM": continuous_prediction(training, pair),
                    }
                    if river_model is not None:
                        predictions["RIVER_ARF_SHADOW"] = river_model.predict_one(features(pair)) or 0.0
                    for name, prediction in predictions.items():
                        error = abs(pair.error_log - prediction)
                        errors[name].append(error)
                        all_errors[name].append(error)
                for name, values in errors.items():
                    per_session.append({
                        "lineage": lineage,
                        "epoch": epoch,
                        "session": session,
                        "model": name,
                        "pairs": len(values),
                        "median_abs_log_percent": median(values) * 100.0,
                        "mean_abs_log_percent": statistics.fmean(values) * 100.0,
                    })
            # Full-session holdout: the held session is learned only after every prediction.
            if river_model is not None:
                for pair in test:
                    river_model.learn_one(features(pair), pair.error_log)
            training.extend(test)

    session_models: dict[str, list[float]] = defaultdict(list)
    for row in per_session:
        session_models[row["model"]].append(row["median_abs_log_percent"])
    baseline_by_session = {
        (row["lineage"], row["epoch"], row["session"]): row["median_abs_log_percent"]
        for row in per_session if row["model"] == "GLOBAL_MEDIAN"
    }
    summary = {}
    for name, errors in sorted(all_errors.items()):
        ordered = sorted(errors)
        model_rows = [row for row in per_session if row["model"] == name]
        summary[name] = {
            "pairs": len(errors),
            "sessions": len(model_rows),
            "median_abs_log_percent": median(errors) * 100.0,
            "mean_abs_log_percent": statistics.fmean(errors) * 100.0,
            "p90_abs_log_percent": ordered[min(len(ordered) - 1, int(0.9 * len(ordered)))] * 100.0,
            "median_session_mae_percent": median(row["median_abs_log_percent"] for row in model_rows),
            "session_wins_vs_global": sum(
                row["median_abs_log_percent"] < baseline_by_session[(row["lineage"], row["epoch"], row["session"])]
                for row in model_rows if name != "GLOBAL_MEDIAN"
            ),
        }
    return {
        "schema": "omegas-obsidian-rpm-residual-model-comparison-v1",
        "validation": "chronological full-physical-session holdout inside calibration epoch",
        "evaluated_pairs": len(next(iter(all_errors.values()), [])),
        "evaluated_sessions": len({row["session"] for row in per_session}),
        "summary": summary,
        "per_session": per_session,
    }


def read_pairs(path: Path) -> list[Pair]:
    out = []
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            out.append(Pair(
                row["session"], int(row["epoch"]), int(row["t"]), float(row["rpm"]), float(row["map"]),
                float(row["petrol_ref_ms"]), float(row["gas_petrol_ms"]),
            ))
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("pairs", type=Path)
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--output-csv", required=True, type=Path)
    args = parser.parse_args()
    result = evaluate_pairs(read_pairs(args.pairs))
    args.output_json.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    rows = result["per_session"]
    with args.output_csv.open("w", newline="", encoding="utf-8") as handle:
        if rows:
            writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
            writer.writeheader()
            writer.writerows(rows)
    print(json.dumps({key: value for key, value in result.items() if key != "per_session"}, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
