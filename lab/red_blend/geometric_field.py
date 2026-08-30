"""Leak-free geometric RPM x MAP field for offline scientific evaluation.

The proven RED neighbor estimator remains the production anchor.  This module
fits a local affine plane only from earlier gasoline evidence, exposes physical
gradients, and decides promotion on a chronological holdout.  It has no Android
or ECU write authority.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass
import argparse
import json
import math
from pathlib import Path
import statistics
from typing import Any, Mapping, Sequence

from lab.red_blend.walk_forward import predict_wu006_neighbor_baseline


@dataclass(frozen=True)
class GeometricPolicy:
    radius: float = 1.5
    nearest: int = 8
    ridge: float = 1.0
    rpm_scale: float = 80.0
    map_scale: float = 0.02

    def __post_init__(self) -> None:
        if self.radius <= 0.0 or self.nearest < 3 or self.ridge < 0.0:
            raise ValueError("invalid geometric policy")
        if self.rpm_scale <= 0.0 or self.map_scale <= 0.0:
            raise ValueError("geometry scales must be positive")


@dataclass(frozen=True)
class GeometricPrediction:
    predicted_ms: float
    slope_ms_per_rpm: float
    slope_ms_per_map_bar: float
    raw_support_count: int
    independent_session_count: int
    selected_episode_count: int
    max_training_order: int
    nearest_distance: float
    method: str = "local_affine_rpm_map"


@dataclass(frozen=True)
class FieldMetrics:
    supported: int
    coverage: float
    median_abs_relative_error: float
    p90_abs_relative_error: float
    p95_abs_relative_error: float
    max_abs_relative_error: float


@dataclass(frozen=True)
class NestedGeometricReport:
    policy: GeometricPolicy
    calibration_max_order: int
    holdout_min_order: int
    holdout_target_count: int
    anchor_all: FieldMetrics
    anchor_common: FieldMetrics
    candidate_common: FieldMetrics
    leakage_violations: int
    decision: str
    decision_reason: str
    claim_scope: str = "GEOMETRIC_FIELD_OFFLINE_NOT_PRODUCTION"
    p_improve: None = None
    actionable: bool = False
    auto_write_ecu: bool = False


def _order(ep: Mapping[str, Any]) -> int:
    value = ep.get("order")
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError("episode order must be integer")
    return value


def _finite(ep: Mapping[str, Any], key: str) -> float:
    value = float(ep[key])
    if not math.isfinite(value):
        raise ValueError(f"non-finite episode field: {key}")
    return value


def _mass(ep: Mapping[str, Any]) -> int:
    value = ep.get("window_count", 1)
    if isinstance(value, bool):
        raise ValueError("window_count must be positive integer")
    count = int(value)
    if count < 1:
        raise ValueError("window_count must be positive integer")
    return count


def _session(ep: Mapping[str, Any]) -> str:
    value = str(ep.get("session_key") or "")
    if not value:
        raise ValueError("session_key required")
    return value


def _solve_3x3(matrix: list[list[float]], vector: list[float]) -> tuple[float, float, float] | None:
    augmented = [matrix[row][:] + [vector[row]] for row in range(3)]
    for column in range(3):
        pivot = max(range(column, 3), key=lambda row: abs(augmented[row][column]))
        if abs(augmented[pivot][column]) <= 1e-14:
            return None
        augmented[column], augmented[pivot] = augmented[pivot], augmented[column]
        divisor = augmented[column][column]
        augmented[column] = [value / divisor for value in augmented[column]]
        for row in range(3):
            if row == column:
                continue
            factor = augmented[row][column]
            augmented[row] = [
                augmented[row][index] - factor * augmented[column][index]
                for index in range(4)
            ]
    return tuple(augmented[row][3] for row in range(3))


def predict_local_affine(
    training: Sequence[Mapping[str, Any]],
    target: Mapping[str, Any],
    policy: GeometricPolicy = GeometricPolicy(),
) -> GeometricPrediction | None:
    """Fit Tinj at target from earlier local evidence only.

    Repeated frames retain their local mass through ``window_count``. Session
    count is reported independently and never substituted for local density.
    """
    target_order = _order(target)
    target_rpm = _finite(target, "rpm")
    target_map = _finite(target, "map_bar")
    candidates: list[tuple[float, Mapping[str, Any], float, float]] = []
    for episode in training:
        if episode.get("fuel", "GASOLINA") != "GASOLINA" or _order(episode) >= target_order:
            continue
        dr = (_finite(episode, "rpm") - target_rpm) / policy.rpm_scale
        dm = (_finite(episode, "map_bar") - target_map) / policy.map_scale
        distance = math.hypot(dr, dm)
        if distance <= policy.radius:
            candidates.append((distance, episode, dr, dm))
    candidates.sort(
        key=lambda item: (
            item[0], _order(item[1]), int(item[1].get("start_ms", 0)), _session(item[1])
        )
    )
    selected = candidates[: policy.nearest]
    if len(selected) < 3:
        return None

    normal = [[0.0] * 3 for _ in range(3)]
    response = [0.0] * 3
    for distance, episode, dr, dm in selected:
        features = (1.0, dr, dm)
        weight = math.exp(-0.5 * distance * distance) * _mass(episode)
        value = _finite(episode, "petrol_ms")
        for row in range(3):
            response[row] += weight * features[row] * value
            for column in range(3):
                normal[row][column] += weight * features[row] * features[column]
    # Do not penalize the local center. Ridge constrains only the two gradients.
    normal[1][1] += policy.ridge
    normal[2][2] += policy.ridge
    coefficients = _solve_3x3(normal, response)
    if coefficients is None or not all(math.isfinite(value) for value in coefficients):
        return None
    center, rpm_coefficient, map_coefficient = coefficients
    if center <= 0.0:
        return None
    return GeometricPrediction(
        predicted_ms=center,
        slope_ms_per_rpm=rpm_coefficient / policy.rpm_scale,
        slope_ms_per_map_bar=map_coefficient / policy.map_scale,
        raw_support_count=sum(_mass(episode) for _, episode, _, _ in selected),
        independent_session_count=len({_session(episode) for _, episode, _, _ in selected}),
        selected_episode_count=len(selected),
        max_training_order=max(_order(episode) for _, episode, _, _ in selected),
        nearest_distance=selected[0][0],
    )


def _quantile(values: Sequence[float], q: float) -> float:
    ordered = sorted(float(value) for value in values)
    if not ordered:
        raise ValueError("metrics require supported predictions")
    position = (len(ordered) - 1) * q
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    fraction = position - lower
    return ordered[lower] * (1.0 - fraction) + ordered[upper] * fraction


def _metrics(errors: Sequence[float], tested: int) -> FieldMetrics:
    if not errors or tested < 1:
        raise ValueError("metrics require predictions and targets")
    return FieldMetrics(
        supported=len(errors),
        coverage=len(errors) / tested,
        median_abs_relative_error=statistics.median(errors),
        p90_abs_relative_error=_quantile(errors, 0.90),
        p95_abs_relative_error=_quantile(errors, 0.95),
        max_abs_relative_error=max(errors),
    )


def evaluate_nested_geometric_field(
    episodes: Sequence[Mapping[str, Any]],
    *,
    calibration_fraction: float = 0.60,
    policy: GeometricPolicy = GeometricPolicy(),
) -> NestedGeometricReport:
    """Compare the fixed candidate with RED on a later untouched chronology."""
    if not 0.0 < calibration_fraction < 1.0:
        raise ValueError("calibration_fraction must be in (0,1)")
    gasoline = [dict(ep) for ep in episodes if ep.get("fuel") == "GASOLINA"]
    gasoline.sort(key=lambda ep: (_order(ep), int(ep.get("start_ms", 0)), _session(ep)))
    orders = sorted({_order(ep) for ep in gasoline})
    if len(orders) < 3:
        raise ValueError("nested evaluation requires three chronological orders")
    split = max(1, min(int(math.floor(len(orders) * calibration_fraction)), len(orders) - 1))
    calibration_max = orders[split - 1]
    holdout_min = orders[split]
    holdout = [ep for ep in gasoline if _order(ep) >= holdout_min]

    anchor_all_errors: list[float] = []
    anchor_common_errors: list[float] = []
    candidate_common_errors: list[float] = []
    leakage = 0
    for target in holdout:
        actual = _finite(target, "petrol_ms")
        if actual <= 0.0:
            continue
        anchor = predict_wu006_neighbor_baseline(gasoline, target)
        candidate = predict_local_affine(gasoline, target, policy)
        if anchor is not None:
            if anchor.max_training_order >= _order(target):
                leakage += 1
            anchor_error = abs(anchor.predicted_ms - actual) / actual
            anchor_all_errors.append(anchor_error)
        else:
            anchor_error = None
        if candidate is not None and candidate.max_training_order >= _order(target):
            leakage += 1
        if anchor_error is not None and candidate is not None:
            anchor_common_errors.append(anchor_error)
            candidate_common_errors.append(abs(candidate.predicted_ms - actual) / actual)

    tested = len(holdout)
    anchor_all = _metrics(anchor_all_errors, tested)
    anchor_common = _metrics(anchor_common_errors, tested)
    candidate_common = _metrics(candidate_common_errors, tested)
    non_worse = (
        candidate_common.median_abs_relative_error <= anchor_common.median_abs_relative_error
        and candidate_common.p90_abs_relative_error <= anchor_common.p90_abs_relative_error
        and candidate_common.p95_abs_relative_error <= anchor_common.p95_abs_relative_error
    )
    coverage_ok = candidate_common.coverage >= anchor_all.coverage * 0.95
    promote = leakage == 0 and non_worse and coverage_ok
    reason = (
        "blind median/P90/P95 and coverage gates passed"
        if promote
        else "candidate did not beat RED on every blind median/P90/P95 and coverage gate"
    )
    return NestedGeometricReport(
        policy=policy,
        calibration_max_order=calibration_max,
        holdout_min_order=holdout_min,
        holdout_target_count=tested,
        anchor_all=anchor_all,
        anchor_common=anchor_common,
        candidate_common=candidate_common,
        leakage_violations=leakage,
        decision="PROMOTE" if promote else "DEFER",
        decision_reason=reason,
    )


def main() -> int:
    from lab.red_blend.real_corpus import load_governed_fixture

    parser = argparse.ArgumentParser()
    parser.add_argument("parts_dir", type=Path)
    parser.add_argument("index", type=Path)
    args = parser.parse_args()
    report = evaluate_nested_geometric_field(load_governed_fixture(args.parts_dir, args.index))
    print(json.dumps(asdict(report), sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
