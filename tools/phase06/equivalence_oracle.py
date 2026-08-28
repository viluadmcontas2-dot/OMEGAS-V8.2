#!/usr/bin/env python3
"""OMEGAS Phase 06 offline oracle.

Uses semantic OMEGAS session logs only. Portmon byte captures are deliberately
excluded unless separately decoded. The runtime app never imports this module.
"""
from __future__ import annotations

import argparse
import json
import math
import random
import statistics
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import Iterable

RPM_SCALE = 80.0
MAP_SCALE = 0.02
MAX_NEIGHBORS = 16
MAX_NORMALIZED_DISTANCE = 1.5


def load_manifest(path: Path) -> list[dict]:
    root = json.loads(Path(path).read_text(encoding="utf-8"))
    by_id: dict[str, dict] = {}
    for row in root.get("sessions", []):
        session_id = str(row.get("session_id", "")).strip()
        if not session_id:
            continue
        by_id.setdefault(session_id, dict(row))
    return [by_id[key] for key in sorted(by_id)]


def split_by_session(rows: Iterable[dict], held_out_session: str):
    train, test = [], []
    for row in rows:
        (test if row.get("session_id") == held_out_session else train).append(row)
    return train, test


def percentile(values: Iterable[float], fraction: float) -> float:
    ordered = sorted(float(x) for x in values)
    if not ordered:
        return float("nan")
    q = max(0.0, min(1.0, float(fraction)))
    pos = (len(ordered) - 1) * q
    low = int(math.floor(pos))
    high = int(math.ceil(pos))
    if low == high:
        return ordered[low]
    w = pos - low
    return ordered[low] * (1.0 - w) + ordered[high] * w


def bootstrap_interval(values: Iterable[float], seed: int, iterations: int = 20_000):
    values = [float(x) for x in values]
    if not values:
        return [float("nan")] * 3
    rng = random.Random(seed)
    estimates = []
    for _ in range(iterations):
        sample = [values[rng.randrange(len(values))] for _ in values]
        estimates.append(statistics.median(sample))
    return [percentile(estimates, 0.025), percentile(estimates, 0.50), percentile(estimates, 0.975)]


def _iter_telemetry(zip_path: Path):
    with zipfile.ZipFile(zip_path) as archive:
        for name in archive.namelist():
            if not name.endswith(".jsonl"):
                continue
            with archive.open(name) as stream:
                for raw in stream:
                    if b'"type":"telemetry"' not in raw:
                        continue
                    try:
                        event = json.loads(raw)
                    except Exception:
                        continue
                    data = event.get("data") or {}
                    fuel = data.get("fuel")
                    if fuel not in {"GASOLINA", "GNV", "CNG"}:
                        continue
                    rpm = data.get("rpm")
                    map_bar = data.get("load_bar")
                    tinj = data.get("petrol_ms")
                    if not all(isinstance(x, (int, float)) for x in (rpm, map_bar, tinj)):
                        continue
                    if not (math.isfinite(rpm) and math.isfinite(map_bar) and math.isfinite(tinj)) or tinj <= 0:
                        continue
                    sample = data.get("sample") or {}
                    yield {
                        "fuel": fuel,
                        "state": data.get("sample_state") or sample.get("state"),
                        "reason": data.get("sample_reason") or sample.get("reason") or "",
                        "rpm": float(rpm),
                        "map_bar": float(map_bar),
                        "tinj_ms": float(tinj),
                        "base_plausible": bool(data.get("base_plausible", data.get("plausible", True))),
                    }


def load_corpus(manifest_path: Path, corpus_root: Path) -> list[dict]:
    rows: list[dict] = []
    for entry in load_manifest(manifest_path):
        source = corpus_root / entry["file"]
        if not source.is_file():
            continue
        for row in _iter_telemetry(source):
            row["session_id"] = entry["session_id"]
            rows.append(row)
    return rows


def _cell(rpm: float, map_bar: float):
    return int(math.floor(rpm / RPM_SCALE)), int(math.floor(map_bar / MAP_SCALE))


def _index(points: list[dict]):
    buckets: dict[tuple[int, int], list[dict]] = defaultdict(list)
    for point in points:
        buckets[_cell(point["rpm"], point["map_bar"])].append(point)
    return buckets


def _nearest_candidates(index, rpm: float, map_bar: float, radius_cells: int = 2):
    cr, cm = _cell(rpm, map_bar)
    candidates = []
    for dr in range(-radius_cells, radius_cells + 1):
        for dm in range(-radius_cells, radius_cells + 1):
            for row in index.get((cr + dr, cm + dm), ()):
                nr = (row["rpm"] - rpm) / RPM_SCALE
                nm = (row["map_bar"] - map_bar) / MAP_SCALE
                distance = math.sqrt(nr * nr + nm * nm)
                candidates.append((distance, row))
    candidates.sort(key=lambda item: item[0])
    return candidates[:MAX_NEIGHBORS]


def predict_reference(index, rpm: float, map_bar: float):
    candidates = _nearest_candidates(index, rpm, map_bar)
    if not candidates or candidates[0][0] > MAX_NORMALIZED_DISTANCE:
        return None
    usable = [(d, row) for d, row in candidates if d <= MAX_NORMALIZED_DISTANCE]
    weights = [math.exp(-0.5 * d * d) for d, _ in usable]
    total = sum(weights)
    if total <= 0:
        return None
    prediction = sum(w * row["tinj_ms"] for w, (_, row) in zip(weights, usable)) / total
    return prediction, usable[0][0], len(usable)


def evaluate_holdout(rows: list[dict]):
    accepted_petrol = [r for r in rows if r["fuel"] == "GASOLINA" and r["state"] == "SAMPLE_ACCEPTED"]
    sessions = sorted({r["session_id"] for r in accepted_petrol})
    errors = []
    session_p90 = []
    total = 0
    covered = 0
    for session_id in sessions:
        train, test = split_by_session(accepted_petrol, session_id)
        if not train or not test:
            continue
        index = _index(train)
        local_errors = []
        total += len(test)
        for row in test:
            predicted = predict_reference(index, row["rpm"], row["map_bar"])
            if predicted is None:
                continue
            covered += 1
            error = abs(predicted[0] - row["tinj_ms"]) / row["tinj_ms"]
            errors.append(error)
            local_errors.append(error)
        if local_errors:
            session_p90.append(percentile(local_errors, 0.90))
    return {
        "evaluated": total,
        "supported": covered,
        "coverage": covered / total if total else 0.0,
        "median_error_fraction": percentile(errors, 0.50),
        "p90_error_fraction": percentile(errors, 0.90),
        "p95_error_fraction": percentile(errors, 0.95),
        "session_p90_bootstrap_95ci": bootstrap_interval(session_p90, seed=8206),
    }


def empirical_noise(rows: list[dict]):
    by_session: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        if row["fuel"] == "GASOLINA" and row["state"] == "SAMPLE_ACCEPTED":
            by_session[row["session_id"]].append(row)
    pairs = []
    for sequence in by_session.values():
        previous = None
        for row in sequence:
            if previous is not None and abs(row["rpm"] - previous["rpm"]) <= 40.0 and abs(row["map_bar"] - previous["map_bar"]) <= 0.01:
                center = max(1e-12, (row["tinj_ms"] + previous["tinj_ms"]) / 2.0)
                pairs.append(abs(row["tinj_ms"] - previous["tinj_ms"]) / center)
            previous = row
    return {
        "pair_count": len(pairs),
        "median_fraction": percentile(pairs, 0.50),
        "p90_fraction": percentile(pairs, 0.90),
        "p95_fraction": percentile(pairs, 0.95),
    }


def rejected_coherence(rows: list[dict]):
    by_session: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        by_session[row["session_id"]].append(row)
    result = {}
    accum: dict[str, list[float]] = defaultdict(list)
    for sequence in by_session.values():
        accepted = [r for r in sequence if r["fuel"] in {"GNV", "CNG"} and r["state"] == "SAMPLE_ACCEPTED"]
        if not accepted:
            continue
        index = _index(accepted)
        for row in sequence:
            if row["fuel"] not in {"GNV", "CNG"} or row["state"] != "SAMPLE_REJECTED":
                continue
            candidates = _nearest_candidates(index, row["rpm"], row["map_bar"], radius_cells=2)
            if not candidates or candidates[0][0] > 1.0:
                continue
            nearest = candidates[0][1]
            accum[row["reason"]].append(abs(row["tinj_ms"] - nearest["tinj_ms"]) / row["tinj_ms"])
    for reason, values in sorted(accum.items(), key=lambda item: (-len(item[1]), item[0])):
        result[reason] = {
            "supported_rejections": len(values),
            "median_tinj_divergence_fraction": percentile(values, 0.50),
            "within_3_percent": sum(x <= 0.03 for x in values) / len(values),
            "within_5_percent": sum(x <= 0.05 for x in values) / len(values),
        }
    return result


def build_report(manifest_path: Path, corpus_root: Path):
    rows = load_corpus(manifest_path, corpus_root)
    return {
        "schema": "omegas-phase06-equivalence-oracle-v1",
        "authority": "OFFLINE_VALIDATION_ONLY",
        "runtime_model": {
            "rpm_scale": RPM_SCALE,
            "map_scale_bar": MAP_SCALE,
            "max_neighbors": MAX_NEIGHBORS,
            "max_normalized_distance": MAX_NORMALIZED_DISTANCE,
        },
        "corpus": {
            "sessions": len({r["session_id"] for r in rows}),
            "telemetry_rows": len(rows),
        },
        "petrol_reference_holdout": evaluate_holdout(rows),
        "empirical_short_term_tinj_noise": empirical_noise(rows),
        "rejected_evidence_coherence": rejected_coherence(rows),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--corpus-root", type=Path, default=Path("."))
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    report = build_report(args.manifest, args.corpus_root)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
