"""Blind chronological walk-forward lab for RED V8.2 Science Blend.

Offline scientific instrumentation only. RPM x MAP is the physical geometry;
local evidence mass is preserved separately from independent-session support.
No Android or ECU authority lives here.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import asdict, dataclass
import json
import math
from pathlib import Path
import statistics
from typing import Any, Mapping, Sequence


@dataclass(frozen=True)
class Prediction:
    predicted_ms: float
    raw_support_count: int
    independent_session_count: int
    max_training_order: int
    method: str


@dataclass(frozen=True)
class PredictorMetrics:
    supported: int
    coverage: float
    median_abs_relative_error: float
    p90_abs_relative_error: float
    p95_abs_relative_error: float
    max_abs_relative_error: float
    median_independent_sessions: float


@dataclass(frozen=True)
class WalkForwardComparison:
    tested_future_episodes: int
    leakage_violations: int
    metrics: Mapping[str, PredictorMetrics]
    claim_scope: str = "BLIND_WALK_FORWARD_OFFLINE_NOT_PRODUCTION"


def _f(episode: Mapping[str, Any], key: str) -> float:
    value = float(episode[key])
    if not math.isfinite(value):
        raise ValueError(f"non-finite episode field: {key}")
    return value


def _order(episode: Mapping[str, Any]) -> int:
    value = episode.get("order")
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError("episode order must be integer")
    return value


def _session(episode: Mapping[str, Any]) -> str:
    value = str(episode.get("session_key") or "")
    if not value:
        raise ValueError("episode session_key required")
    return value


def _evidence_mass(episode: Mapping[str, Any]) -> int:
    value = episode.get("window_count", 1)
    if isinstance(value, bool):
        raise ValueError("window_count must be positive integer")
    try:
        count = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError("window_count must be positive integer") from exc
    if count < 1:
        raise ValueError("window_count must be positive integer")
    return count


def _eligible_training(training: Sequence[Mapping[str, Any]], target: Mapping[str, Any]) -> list[Mapping[str, Any]]:
    target_order = _order(target)
    out = []
    for episode in training:
        if episode.get("fuel", "GASOLINA") != "GASOLINA":
            continue
        if _order(episode) < target_order:
            out.append(episode)
    return sorted(out, key=lambda e: (_order(e), _session(e), int(e.get("start_ms", 0))))


def _distance(episode: Mapping[str, Any], target: Mapping[str, Any], *, rpm_scale: float = 80.0, map_scale: float = 0.02) -> float:
    if rpm_scale <= 0.0 or map_scale <= 0.0:
        raise ValueError("geometry scales must be positive")
    dr = (_f(episode, "rpm") - _f(target, "rpm")) / rpm_scale
    dm = (_f(episode, "map_bar") - _f(target, "map_bar")) / map_scale
    return math.hypot(dr, dm)


def _prediction_from_weighted(
    weighted: Sequence[tuple[float, Mapping[str, Any]]],
    *,
    method: str,
) -> Prediction | None:
    if not weighted:
        return None
    denom = sum(weight for weight, _ in weighted)
    if denom <= 0.0 or not math.isfinite(denom):
        return None
    predicted = sum(weight * _f(ep, "petrol_ms") for weight, ep in weighted) / denom
    return Prediction(
        predicted_ms=predicted,
        raw_support_count=sum(_evidence_mass(ep) for _, ep in weighted),
        independent_session_count=len({_session(ep) for _, ep in weighted}),
        max_training_order=max(_order(ep) for _, ep in weighted),
        method=method,
    )


def predict_wu006_neighbor_baseline(
    training: Sequence[Mapping[str, Any]],
    target: Mapping[str, Any],
) -> Prediction | None:
    """Exact independent WU-006 ruler: radius 1.5, nearest 16, inverse distance."""
    candidates = []
    for episode in _eligible_training(training, target):
        distance = _distance(episode, target)
        if distance <= 1.5:
            candidates.append((distance, episode))
    candidates.sort(key=lambda pair: (pair[0], _order(pair[1]), int(pair[1].get("start_ms", 0)), _session(pair[1])))
    nearest = candidates[:16]
    weighted = [(1.0 / (0.25 + distance), episode) for distance, episode in nearest]
    return _prediction_from_weighted(weighted, method="wu006_neighbor_baseline")


def predict_pooled_gaussian(
    training: Sequence[Mapping[str, Any]],
    target: Mapping[str, Any],
    *,
    radius: float = 3.0,
) -> Prediction | None:
    """Local estimator where repeated valid local evidence retains its mass."""
    if radius <= 0.0:
        raise ValueError("radius must be positive")
    weighted = []
    for episode in _eligible_training(training, target):
        distance = _distance(episode, target)
        if distance > radius:
            continue
        spatial = math.exp(-0.5 * distance * distance)
        weight = spatial * _evidence_mass(episode)
        if weight > 0.0:
            weighted.append((weight, episode))
    return _prediction_from_weighted(weighted, method="pooled_gaussian")


def predict_session_balanced_gaussian(
    training: Sequence[Mapping[str, Any]],
    target: Mapping[str, Any],
    *,
    radius: float = 3.0,
) -> Prediction | None:
    """Estimate each prior session locally, then give each session one transfer vote.

    Dense repetition is still used to estimate the local mean *inside* a session,
    but duplicating a session cannot manufacture additional independent sessions.
    """
    if radius <= 0.0:
        raise ValueError("radius must be positive")
    grouped: dict[str, list[tuple[float, Mapping[str, Any]]]] = defaultdict(list)
    for episode in _eligible_training(training, target):
        distance = _distance(episode, target)
        if distance > radius:
            continue
        spatial = math.exp(-0.5 * distance * distance)
        weight = spatial * _evidence_mass(episode)
        if weight > 0.0:
            grouped[_session(episode)].append((weight, episode))
    if not grouped:
        return None

    session_predictions: list[float] = []
    raw_support = 0
    max_order = -1
    for session_key in sorted(grouped):
        items = grouped[session_key]
        denom = sum(weight for weight, _ in items)
        if denom <= 0.0:
            continue
        session_predictions.append(sum(weight * _f(ep, "petrol_ms") for weight, ep in items) / denom)
        raw_support += sum(_evidence_mass(ep) for _, ep in items)
        max_order = max(max_order, max(_order(ep) for _, ep in items))
    if not session_predictions:
        return None
    return Prediction(
        predicted_ms=statistics.fmean(session_predictions),
        raw_support_count=raw_support,
        independent_session_count=len(session_predictions),
        max_training_order=max_order,
        method="session_balanced_gaussian",
    )


def _quantile(values: Sequence[float], q: float) -> float:
    ordered = sorted(float(v) for v in values)
    if not ordered:
        raise ValueError("quantile requires values")
    pos = (len(ordered) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return ordered[lo]
    frac = pos - lo
    return ordered[lo] * (1.0 - frac) + ordered[hi] * frac


def _metrics(errors: Sequence[float], sessions: Sequence[int], tested: int) -> PredictorMetrics:
    if not errors:
        raise ValueError("predictor has no supported folds")
    return PredictorMetrics(
        supported=len(errors),
        coverage=len(errors) / tested,
        median_abs_relative_error=statistics.median(errors),
        p90_abs_relative_error=_quantile(errors, 0.90),
        p95_abs_relative_error=_quantile(errors, 0.95),
        max_abs_relative_error=max(errors),
        median_independent_sessions=statistics.median(sessions),
    )


def compare_gasoline_walk_forward(episodes: Sequence[Mapping[str, Any]]) -> WalkForwardComparison:
    gas = [dict(e) for e in episodes if e.get("fuel") == "GASOLINA"]
    gas.sort(key=lambda e: (_order(e), int(e.get("start_ms", 0)), _session(e)))
    predictors = {
        "wu006_neighbor_baseline": predict_wu006_neighbor_baseline,
        "pooled_gaussian": predict_pooled_gaussian,
        "session_balanced_gaussian": predict_session_balanced_gaussian,
    }
    errors: dict[str, list[float]] = {name: [] for name in predictors}
    independent_sessions: dict[str, list[int]] = {name: [] for name in predictors}
    tested = 0
    leakage = 0

    for target in gas:
        if not any(_order(train) < _order(target) for train in gas):
            continue
        tested += 1
        target_ms = _f(target, "petrol_ms")
        if abs(target_ms) <= 1e-12:
            continue
        for name, predictor in predictors.items():
            prediction = predictor(gas, target)
            if prediction is None:
                continue
            if prediction.max_training_order >= _order(target):
                leakage += 1
            error = abs(prediction.predicted_ms - target_ms) / abs(target_ms)
            errors[name].append(error)
            independent_sessions[name].append(prediction.independent_session_count)

    if tested < 1:
        raise ValueError("walk-forward requires at least one future gasoline episode")
    metrics = {
        name: _metrics(errors[name], independent_sessions[name], tested)
        for name in predictors
        if errors[name]
    }
    return WalkForwardComparison(
        tested_future_episodes=tested,
        leakage_violations=leakage,
        metrics=metrics,
    )


def main() -> int:
    from lab.red_blend.real_corpus import load_governed_fixture

    parser = argparse.ArgumentParser()
    parser.add_argument("parts_dir", type=Path)
    parser.add_argument("index", type=Path)
    args = parser.parse_args()
    report = compare_gasoline_walk_forward(load_governed_fixture(args.parts_dir, args.index))
    payload = {
        "claim_scope": report.claim_scope,
        "tested_future_episodes": report.tested_future_episodes,
        "leakage_violations": report.leakage_violations,
        "metrics": {name: asdict(metric) for name, metric in sorted(report.metrics.items())},
    }
    print(json.dumps(payload, sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
