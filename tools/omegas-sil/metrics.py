#!/usr/bin/env python3
from __future__ import annotations

import math
from statistics import mean
from typing import Iterable

PRACTICAL_TOLERANCE_PCT = 5.0


def correction_pct(observed_ms: float, predicted_ms: float) -> float:
    """Signed correction/error in percent. Scientific optimum is exactly 0%."""
    if not math.isfinite(observed_ms) or observed_ms <= 0.0:
        return math.nan
    if not math.isfinite(predicted_ms):
        return math.nan
    return (predicted_ms - observed_ms) / observed_ms * 100.0


def relative_error_pct(observed_ms: float, predicted_ms: float) -> float:
    correction = correction_pct(observed_ms, predicted_ms)
    return abs(correction) if math.isfinite(correction) else math.nan


def within_tolerance(
    observed_ms: float,
    predicted_ms: float,
    tolerance_pct: float = PRACTICAL_TOLERANCE_PCT,
) -> bool:
    error = relative_error_pct(observed_ms, predicted_ms)
    return math.isfinite(error) and error <= tolerance_pct + 1e-12


def _quantile(values: list[float], q: float) -> float:
    if not values:
        return math.nan
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    pos = (len(ordered) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return ordered[lo]
    fraction = pos - lo
    return ordered[lo] * (1.0 - fraction) + ordered[hi] * fraction


def summarize_reference_errors(
    pairs: Iterable[tuple[float, float]],
    tolerance_pct: float = PRACTICAL_TOLERANCE_PCT,
) -> dict:
    usable: list[tuple[float, float, float]] = []
    for observed, predicted in pairs:
        correction = correction_pct(observed, predicted)
        if not math.isfinite(correction):
            continue
        usable.append((observed, predicted, correction))

    signed_corrections = [correction for _, _, correction in usable]
    abs_corrections = [abs(correction) for correction in signed_corrections]
    abs_errors = [abs(predicted - observed) for observed, predicted, _ in usable]
    within = sum(abs(correction) <= tolerance_pct + 1e-12 for correction in signed_corrections)
    count = len(usable)
    return {
        "target_correction_pct": 0.0,
        "tolerance_pct": tolerance_pct,
        "count": count,
        "within_5pct": within,
        "within_5pct_rate": within / count if count else 0.0,
        "mean_signed_correction_pct": mean(signed_corrections) if signed_corrections else math.nan,
        "mean_abs_correction_pct": mean(abs_corrections) if abs_corrections else math.nan,
        "median_abs_correction_pct": _quantile(abs_corrections, 0.50),
        "p90_abs_correction_pct": _quantile(abs_corrections, 0.90),
        "p99_abs_correction_pct": _quantile(abs_corrections, 0.99),
        "mae_ms": mean(abs_errors) if abs_errors else math.nan,
        "p90_abs_error_ms": _quantile(abs_errors, 0.90),
        "p99_abs_error_ms": _quantile(abs_errors, 0.99),
        "max_abs_error_ms": max(abs_errors) if abs_errors else math.nan,
    }
