from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Iterable


@dataclass(frozen=True)
class RiskObservation:
    order: int
    risk_score: float
    abs_relative_error: float


@dataclass(frozen=True)
class RiskCoveragePoint:
    coverage: float
    max_risk_score: float
    mean_abs_relative_error: float
    p90_abs_relative_error: float


@dataclass(frozen=True)
class RiskCoverageCurve:
    points: tuple[RiskCoveragePoint, ...]
    p_improve: None = None
    actionable: bool = False
    claim_scope: str = "EMPIRICAL_RISK_COVERAGE_ONLY"


def _validate(observation: RiskObservation) -> None:
    if not isinstance(observation.order, int):
        raise ValueError("order must be an integer")
    if observation.order < 0:
        raise ValueError("order must be non-negative")
    if not math.isfinite(observation.risk_score) or observation.risk_score < 0.0:
        raise ValueError("risk_score must be finite and non-negative")
    if not math.isfinite(observation.abs_relative_error) or observation.abs_relative_error < 0.0:
        raise ValueError("abs_relative_error must be finite and non-negative")


def _nearest_rank(values: list[float], quantile: float) -> float:
    ordered = sorted(values)
    rank = max(1, math.ceil(quantile * len(ordered)))
    return ordered[rank - 1]


def empirical_risk_coverage_curve(
    observations: Iterable[RiskObservation],
) -> RiskCoverageCurve:
    ordered = list(observations)
    if not ordered:
        raise ValueError("at least one observation is required")
    for observation in ordered:
        _validate(observation)

    ordered.sort(key=lambda observation: (observation.risk_score, observation.order))
    total = len(ordered)
    points: list[RiskCoveragePoint] = []
    errors: list[float] = []

    for index, observation in enumerate(ordered, start=1):
        errors.append(observation.abs_relative_error)
        points.append(
            RiskCoveragePoint(
                coverage=index / total,
                max_risk_score=observation.risk_score,
                mean_abs_relative_error=sum(errors) / len(errors),
                p90_abs_relative_error=_nearest_rank(errors, 0.90),
            )
        )

    return RiskCoverageCurve(points=tuple(points))
