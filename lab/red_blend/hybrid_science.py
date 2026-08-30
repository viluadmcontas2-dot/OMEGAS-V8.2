"""Risk-gated hybrid for RED V8.2 Science Blend.

Offline scientific evaluation only. The proven neighbor ruler remains the
anchor. Gaussian estimators may only add coverage as a calibrated fallback;
they never replace an available anchor and they never authorize ECU writes.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import json
import math
from pathlib import Path
import statistics
from typing import Any, Mapping, Sequence

from lab.red_blend.walk_forward import (
    Prediction,
    PredictorMetrics,
    predict_pooled_gaussian,
    predict_session_balanced_gaussian,
    predict_wu006_neighbor_baseline,
)


@dataclass(frozen=True)
class HybridRiskPolicy:
    min_independent_sessions: int
    max_model_disagreement: float

    def __post_init__(self) -> None:
        if self.min_independent_sessions < 1:
            raise ValueError("min_independent_sessions must be positive")
        if not math.isfinite(self.max_model_disagreement) or self.max_model_disagreement < 0.0:
            raise ValueError("max_model_disagreement must be finite and non-negative")


@dataclass(frozen=True)
class HybridCalibration:
    policy: HybridRiskPolicy
    calibration_target_count: int
    disagreement_observation_count: int
    max_calibration_order: int
    min_holdout_order: int
    disagreement_quantile: float
    claim_scope: str = "PAST_PREFIX_CALIBRATION_ONLY"


@dataclass(frozen=True)
class NestedHybridReport:
    calibration: HybridCalibration
    holdout_target_count: int
    anchor_metrics: PredictorMetrics
    hybrid_metrics: PredictorMetrics
    leakage_violations: int
    fallback_accepts: int
    fallback_abstains: int
    claim_scope: str = "NESTED_CHRONOLOGICAL_HYBRID_OFFLINE_NOT_PRODUCTION"


def _order(episode: Mapping[str, Any]) -> int:
    value = episode.get("order")
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError("episode order must be integer")
    return value


def _petrol_ms(episode: Mapping[str, Any]) -> float:
    value = float(episode["petrol_ms"])
    if not math.isfinite(value):
        raise ValueError("petrol_ms must be finite")
    return value


def _chronological_gasoline(episodes: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    gas = [dict(e) for e in episodes if e.get("fuel") == "GASOLINA"]
    gas.sort(key=lambda e: (_order(e), int(e.get("start_ms", 0)), str(e.get("session_key") or "")))
    return gas


def _relative_disagreement(a: float, b: float) -> float:
    denom = max((abs(a) + abs(b)) * 0.5, 1e-12)
    return abs(a - b) / denom


def _quantile(values: Sequence[float], q: float) -> float:
    if not 0.0 <= q <= 1.0:
        raise ValueError("quantile must be in [0,1]")
    ordered = sorted(float(v) for v in values)
    if not ordered:
        raise ValueError("quantile requires observations")
    pos = (len(ordered) - 1) * q
    lo = int(math.floor(pos))
    hi = int(math.ceil(pos))
    if lo == hi:
        return ordered[lo]
    frac = pos - lo
    return ordered[lo] * (1.0 - frac) + ordered[hi] * frac


def _metrics(errors: Sequence[float], sessions: Sequence[int], tested: int) -> PredictorMetrics:
    if tested < 1:
        raise ValueError("tested must be positive")
    if not errors:
        raise ValueError("predictor has no supported holdout targets")
    return PredictorMetrics(
        supported=len(errors),
        coverage=len(errors) / tested,
        median_abs_relative_error=statistics.median(errors),
        p90_abs_relative_error=_quantile(errors, 0.90),
        p95_abs_relative_error=_quantile(errors, 0.95),
        max_abs_relative_error=max(errors),
        median_independent_sessions=statistics.median(sessions),
    )


def predict_risk_gated_hybrid(
    training: Sequence[Mapping[str, Any]],
    target: Mapping[str, Any],
    policy: HybridRiskPolicy,
) -> Prediction | None:
    """Anchor first; calibrated Gaussian fallback second; otherwise abstain."""
    anchor = predict_wu006_neighbor_baseline(training, target)
    if anchor is not None:
        return Prediction(
            predicted_ms=anchor.predicted_ms,
            raw_support_count=anchor.raw_support_count,
            independent_session_count=anchor.independent_session_count,
            max_training_order=anchor.max_training_order,
            method="hybrid_anchor_neighbor",
        )

    pooled = predict_pooled_gaussian(training, target)
    balanced = predict_session_balanced_gaussian(training, target)
    if pooled is None or balanced is None:
        return None
    if balanced.independent_session_count < policy.min_independent_sessions:
        return None
    disagreement = _relative_disagreement(pooled.predicted_ms, balanced.predicted_ms)
    if disagreement > policy.max_model_disagreement:
        return None

    return Prediction(
        predicted_ms=balanced.predicted_ms,
        raw_support_count=balanced.raw_support_count,
        independent_session_count=balanced.independent_session_count,
        max_training_order=max(pooled.max_training_order, balanced.max_training_order),
        method="hybrid_session_gaussian_fallback",
    )


def calibrate_disagreement_policy(
    episodes: Sequence[Mapping[str, Any]],
    *,
    calibration_fraction: float = 0.60,
    disagreement_quantile: float = 0.90,
    min_independent_sessions: int = 3,
) -> HybridCalibration:
    """Calibrate model agreement on a chronological prefix only.

    The split is performed on unique order values so every calibration target
    is strictly earlier than every holdout target. The holdout never tunes the
    disagreement threshold.
    """
    if not 0.0 < calibration_fraction < 1.0:
        raise ValueError("calibration_fraction must be in (0,1)")
    if not 0.0 < disagreement_quantile <= 1.0:
        raise ValueError("disagreement_quantile must be in (0,1]")
    if min_independent_sessions < 1:
        raise ValueError("min_independent_sessions must be positive")

    gas = _chronological_gasoline(episodes)
    unique_orders = sorted({_order(e) for e in gas})
    if len(unique_orders) < 3:
        raise ValueError("nested calibration requires at least three chronological orders")

    split = int(math.floor(len(unique_orders) * calibration_fraction))
    split = max(1, min(split, len(unique_orders) - 1))
    calibration_orders = set(unique_orders[:split])
    max_calibration_order = unique_orders[split - 1]
    min_holdout_order = unique_orders[split]

    disagreements: list[float] = []
    calibration_targets = 0
    for target in gas:
        target_order = _order(target)
        if target_order not in calibration_orders:
            continue
        # The earliest order has no past and is not a predictive calibration target.
        if not any(_order(train) < target_order for train in gas):
            continue
        calibration_targets += 1
        pooled = predict_pooled_gaussian(gas, target)
        balanced = predict_session_balanced_gaussian(gas, target)
        if pooled is None or balanced is None:
            continue
        if balanced.independent_session_count < min_independent_sessions:
            continue
        disagreements.append(_relative_disagreement(pooled.predicted_ms, balanced.predicted_ms))

    if not disagreements:
        raise ValueError("past prefix produced no independent model-agreement observations")

    threshold = _quantile(disagreements, disagreement_quantile)
    # Preserve a strictly positive numerical gate when all observed models agree
    # to floating-point precision; this is not a data-tuned performance floor.
    threshold = max(threshold, 1e-12)
    return HybridCalibration(
        policy=HybridRiskPolicy(
            min_independent_sessions=min_independent_sessions,
            max_model_disagreement=threshold,
        ),
        calibration_target_count=calibration_targets,
        disagreement_observation_count=len(disagreements),
        max_calibration_order=max_calibration_order,
        min_holdout_order=min_holdout_order,
        disagreement_quantile=disagreement_quantile,
    )


def evaluate_nested_hybrid(
    episodes: Sequence[Mapping[str, Any]],
    *,
    calibration_fraction: float = 0.60,
    disagreement_quantile: float = 0.90,
    min_independent_sessions: int = 3,
) -> NestedHybridReport:
    gas = _chronological_gasoline(episodes)
    calibration = calibrate_disagreement_policy(
        gas,
        calibration_fraction=calibration_fraction,
        disagreement_quantile=disagreement_quantile,
        min_independent_sessions=min_independent_sessions,
    )

    holdout = [e for e in gas if _order(e) >= calibration.min_holdout_order]
    if not holdout:
        raise ValueError("nested evaluation has no holdout targets")

    anchor_errors: list[float] = []
    anchor_sessions: list[int] = []
    hybrid_errors: list[float] = []
    hybrid_sessions: list[int] = []
    leakage = 0
    fallback_accepts = 0
    fallback_abstains = 0

    for target in holdout:
        target_ms = _petrol_ms(target)
        if abs(target_ms) <= 1e-12:
            continue
        anchor = predict_wu006_neighbor_baseline(gas, target)
        hybrid = predict_risk_gated_hybrid(gas, target, calibration.policy)

        if anchor is not None:
            if anchor.max_training_order >= _order(target):
                leakage += 1
            anchor_errors.append(abs(anchor.predicted_ms - target_ms) / abs(target_ms))
            anchor_sessions.append(anchor.independent_session_count)

        if hybrid is not None:
            if hybrid.max_training_order >= _order(target):
                leakage += 1
            hybrid_errors.append(abs(hybrid.predicted_ms - target_ms) / abs(target_ms))
            hybrid_sessions.append(hybrid.independent_session_count)
            if hybrid.method == "hybrid_session_gaussian_fallback":
                fallback_accepts += 1
        elif anchor is None:
            fallback_abstains += 1

    tested = len(holdout)
    return NestedHybridReport(
        calibration=calibration,
        holdout_target_count=tested,
        anchor_metrics=_metrics(anchor_errors, anchor_sessions, tested),
        hybrid_metrics=_metrics(hybrid_errors, hybrid_sessions, tested),
        leakage_violations=leakage,
        fallback_accepts=fallback_accepts,
        fallback_abstains=fallback_abstains,
    )


def main() -> int:
    from lab.red_blend.real_corpus import load_governed_fixture

    parser = argparse.ArgumentParser()
    parser.add_argument("parts_dir", type=Path)
    parser.add_argument("index", type=Path)
    parser.add_argument("--calibration-fraction", type=float, default=0.60)
    parser.add_argument("--disagreement-quantile", type=float, default=0.90)
    parser.add_argument("--min-independent-sessions", type=int, default=3)
    args = parser.parse_args()

    report = evaluate_nested_hybrid(
        load_governed_fixture(args.parts_dir, args.index),
        calibration_fraction=args.calibration_fraction,
        disagreement_quantile=args.disagreement_quantile,
        min_independent_sessions=args.min_independent_sessions,
    )
    payload = {
        "claim_scope": report.claim_scope,
        "calibration": {
            **asdict(report.calibration),
            "policy": asdict(report.calibration.policy),
        },
        "holdout_target_count": report.holdout_target_count,
        "anchor_metrics": asdict(report.anchor_metrics),
        "hybrid_metrics": asdict(report.hybrid_metrics),
        "leakage_violations": report.leakage_violations,
        "fallback_accepts": report.fallback_accepts,
        "fallback_abstains": report.fallback_abstains,
    }
    print(json.dumps(payload, sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
