"""Fail-closed offline causal MAP_K contract for the RED V8.2 Science Blend.

Validates confirmed manual adjustment proof envelopes and estimates pre/post
error direction only when support is comparable. Never writes ECU, never
produces P(improve), and never marks an outcome actionable.
"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import math
import statistics
from typing import Any, Mapping, Sequence


class CausalEnvelopeError(ValueError):
    pass


@dataclass(frozen=True)
class KCellChange:
    row: int
    column: int
    before: int
    after: int
    readback: int


@dataclass(frozen=True)
class ConfirmedAdjustment:
    adjustment_key: str
    timestamp_ms: int
    final_map_hash: str
    cells: tuple[KCellChange, ...]


@dataclass(frozen=True)
class CausalEffect:
    status: str
    direction: str | None
    effect_relative: float | None
    context_overlap: float
    pre_median_error: float | None
    post_median_error: float | None
    p_improve: float | None = None
    actionable: bool = False


def _require_int(event: Mapping[str, Any], key: str) -> int:
    value = event.get(key)
    if isinstance(value, bool) or value is None:
        raise CausalEnvelopeError(f"missing integer field: {key}")
    try:
        return int(value)
    except (TypeError, ValueError) as exc:
        raise CausalEnvelopeError(f"invalid integer field: {key}") from exc


def _validate_event(event: Mapping[str, Any]):
    adjustment_id = str(event.get("adjustmentId") or "").strip()
    if not adjustment_id:
        raise CausalEnvelopeError("missing adjustment identity")
    if event.get("confirmed") is not True:
        raise CausalEnvelopeError("adjustment is not confirmed")
    if event.get("batchFinalized") is not True:
        raise CausalEnvelopeError("adjustment batch is not finalized")
    final_map_hash = str(event.get("finalMapHash") or "").strip()
    if not final_map_hash:
        raise CausalEnvelopeError("missing final map hash")

    row = _require_int(event, "row")
    column = _require_int(event, "column")
    before = _require_int(event, "before")
    after = _require_int(event, "after")
    readback = _require_int(event, "readback")
    timestamp_ms = _require_int(event, "timestampMs")
    if not 0 <= row < 12 or not 0 <= column < 12:
        raise CausalEnvelopeError("MAP_K cell outside writable 12x12 map")
    if any(not 0 <= value <= 255 for value in (before, after, readback)):
        raise CausalEnvelopeError("MAP_K raw value outside U8 range")
    if readback != after:
        raise CausalEnvelopeError("readback does not confirm requested MAP_K value")
    if timestamp_ms < 0:
        raise CausalEnvelopeError("negative intervention timestamp")

    key = hashlib.sha256(adjustment_id.encode("utf-8")).hexdigest()[:16]
    cell = KCellChange(row, column, before, after, readback)
    return key, timestamp_ms, final_map_hash, cell


def normalize_confirmed_adjustments(events: Sequence[Mapping[str, Any]]) -> list[ConfirmedAdjustment]:
    if not events:
        raise CausalEnvelopeError("no MAP_K history events")
    grouped: dict[str, dict[str, Any]] = {}
    for event in events:
        key, timestamp_ms, final_map_hash, cell = _validate_event(event)
        group = grouped.setdefault(key, {"timestamps": [], "final_map_hash": final_map_hash, "cells": {}})
        if group["final_map_hash"] != final_map_hash:
            raise CausalEnvelopeError("adjustment has inconsistent final map hashes")
        cell_key = (cell.row, cell.column)
        previous = group["cells"].get(cell_key)
        if previous is not None and previous != cell:
            raise CausalEnvelopeError("adjustment contains conflicting duplicate cell change")
        group["cells"][cell_key] = cell
        group["timestamps"].append(timestamp_ms)

    result = []
    for key, group in grouped.items():
        cells = tuple(group["cells"][cell_key] for cell_key in sorted(group["cells"]))
        result.append(ConfirmedAdjustment(key, min(group["timestamps"]), group["final_map_hash"], cells))
    result.sort(key=lambda item: (item.timestamp_ms, item.adjustment_key))
    return result


def _validated_errors(values: Sequence[float], label: str) -> list[float]:
    result = []
    for value in values:
        number = float(value)
        if not math.isfinite(number) or number < 0.0:
            raise ValueError(f"{label} errors must be finite and non-negative")
        result.append(number)
    return result


def evaluate_confirmed_adjustment(
    adjustment: ConfirmedAdjustment,
    *,
    pre_abs_relative_errors: Sequence[float],
    post_abs_relative_errors: Sequence[float],
    pre_context_keys: set[str],
    post_context_keys: set[str],
    min_observations: int = 3,
    min_context_overlap: float = 0.5,
) -> CausalEffect:
    if not isinstance(adjustment, ConfirmedAdjustment):
        raise TypeError("adjustment must be ConfirmedAdjustment")
    if min_observations < 1:
        raise ValueError("min_observations must be positive")
    if not 0.0 <= min_context_overlap <= 1.0:
        raise ValueError("min_context_overlap must be in [0,1]")

    pre = _validated_errors(pre_abs_relative_errors, "pre")
    post = _validated_errors(post_abs_relative_errors, "post")
    union = set(pre_context_keys) | set(post_context_keys)
    overlap = len(set(pre_context_keys) & set(post_context_keys)) / len(union) if union else 0.0

    if len(pre) < min_observations or len(post) < min_observations:
        return CausalEffect("ABSTAIN_INSUFFICIENT_SUPPORT", None, None, overlap, None, None)
    pre_median = statistics.median(pre)
    post_median = statistics.median(post)
    if overlap < min_context_overlap:
        return CausalEffect("ABSTAIN_INCOMPARABLE_CONTEXT", None, None, overlap, pre_median, post_median)

    effect = post_median - pre_median
    direction = "IMPROVED" if effect < 0.0 else "WORSENED" if effect > 0.0 else "NO_CHANGE"
    return CausalEffect("CAUSAL_DIRECTION_ESTIMATED_OFFLINE", direction, effect, overlap, pre_median, post_median)
