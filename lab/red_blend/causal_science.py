"""Deterministic offline causal MAP_K laboratory.

The independent unit is the confirmed manual adjustment/batch, never the cell
write count.  This module is analysis-only: no Android runtime dependency, no
ECU writer, no actionability, and no P(improve) claim.
"""
from __future__ import annotations

from dataclasses import dataclass
import json
import math
import statistics
from pathlib import Path
from typing import Any, Mapping, Sequence


@dataclass(frozen=True)
class ConfirmedKCellEvent:
    timestamp_ms: int
    adjustment_key: str
    row: int
    column: int
    petrol_ms: float
    rpm: float
    before: int
    after: int
    readback: int
    final_map_hash: str


@dataclass(frozen=True)
class Adjustment:
    adjustment_key: str
    cell_count: int
    started_at_ms: int
    ended_at_ms: int
    final_map_hash: str
    delta_median: float
    rpm_min: float
    rpm_max: float
    petrol_ms_min: float
    petrol_ms_max: float
    proof_envelope_sha256: str = ""


@dataclass(frozen=True)
class AdjustmentFixture:
    schema: str
    source_content_sha256: str
    axis_schema: str
    axis_lock_sha256: str
    cell_event_count: int
    adjustments: tuple[Adjustment, ...]


@dataclass(frozen=True)
class Outcome:
    timestamp_ms: int
    rpm: float
    map_bar: float
    gasoline_reference_ms: float
    cng_petrol_ms: float

    @property
    def error_fraction(self) -> float:
        if not math.isfinite(self.gasoline_reference_ms) or self.gasoline_reference_ms <= 0.0:
            raise ValueError("gasoline reference must be finite and positive")
        if not math.isfinite(self.cng_petrol_ms):
            raise ValueError("CNG petrol command must be finite")
        return (self.cng_petrol_ms - self.gasoline_reference_ms) / self.gasoline_reference_ms

    @property
    def absolute_error(self) -> float:
        return abs(self.error_fraction)


@dataclass(frozen=True)
class CausalResult:
    status: str
    effect_abs_error_delta: float | None
    pre_median_abs_error: float | None
    post_median_abs_error: float | None
    comparable_pair_count: int
    p_improve: float | None = None
    actionable: bool = False


def _as_int(event: Mapping[str, Any], key: str) -> int:
    value = event.get(key)
    if isinstance(value, bool) or value is None:
        raise ValueError(f"missing integer field: {key}")
    try:
        return int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"invalid integer field: {key}") from exc


def _as_float(event: Mapping[str, Any], key: str) -> float:
    value = event.get(key)
    if value is None:
        raise ValueError(f"missing numeric field: {key}")
    try:
        result = float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"invalid numeric field: {key}") from exc
    if not math.isfinite(result):
        raise ValueError(f"non-finite numeric field: {key}")
    return result


def normalize_confirmed_event(event: Mapping[str, Any]) -> ConfirmedKCellEvent:
    key = str(event.get("adjustment_key") or "").strip()
    final_hash = str(event.get("final_map_hash") or "").strip()
    if len(key) != 16 or any(c not in "0123456789abcdef" for c in key.lower()):
        raise ValueError("adjustment_key must be a 16-hex privacy-safe identity")
    if event.get("confirmed") is not True:
        raise ValueError("K event is not confirmed")
    if event.get("batch_finalized") is not True:
        raise ValueError("K event batch is not finalized")
    if not final_hash:
        raise ValueError("missing final map hash")

    row = _as_int(event, "row")
    column = _as_int(event, "column")
    before = _as_int(event, "before")
    after = _as_int(event, "after")
    readback = _as_int(event, "readback")
    if not 0 <= row < 12 or not 0 <= column < 12:
        raise ValueError("K history cell outside writable map")
    if readback != after:
        raise ValueError("readback does not match confirmed after value")
    if any(not 0 <= value <= 255 for value in (before, after, readback)):
        raise ValueError("K raw value outside U8 range")

    return ConfirmedKCellEvent(
        timestamp_ms=_as_int(event, "timestamp_ms"),
        adjustment_key=key.lower(),
        row=row,
        column=column,
        petrol_ms=_as_float(event, "petrol_ms"),
        rpm=_as_float(event, "rpm"),
        before=before,
        after=after,
        readback=readback,
        final_map_hash=final_hash,
    )


def group_adjustments(events: Sequence[ConfirmedKCellEvent]) -> tuple[Adjustment, ...]:
    groups: dict[str, list[ConfirmedKCellEvent]] = {}
    for event in events:
        groups.setdefault(event.adjustment_key, []).append(event)
    result = []
    for key, cells in groups.items():
        cells = sorted(cells, key=lambda item: (item.timestamp_ms, item.row, item.column))
        hashes = {item.final_map_hash for item in cells}
        if len(hashes) != 1:
            raise ValueError("one adjustment cannot have multiple final map hashes")
        coordinates = [(item.row, item.column) for item in cells]
        if len(coordinates) != len(set(coordinates)):
            raise ValueError("duplicate cell coordinate inside one adjustment")
        result.append(
            Adjustment(
                adjustment_key=key,
                cell_count=len(cells),
                started_at_ms=cells[0].timestamp_ms,
                ended_at_ms=cells[-1].timestamp_ms,
                final_map_hash=cells[0].final_map_hash,
                delta_median=float(statistics.median(item.after - item.before for item in cells)),
                rpm_min=min(item.rpm for item in cells),
                rpm_max=max(item.rpm for item in cells),
                petrol_ms_min=min(item.petrol_ms for item in cells),
                petrol_ms_max=max(item.petrol_ms for item in cells),
            )
        )
    return tuple(sorted(result, key=lambda item: (item.started_at_ms, item.adjustment_key)))


def load_adjustment_fixture(path: Path) -> AdjustmentFixture:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if payload.get("schema") != "omegas-confirmed-map-k-adjustments-v1":
        raise ValueError("unsupported MAP_K fixture schema")
    source_hash = str(payload.get("source_content_sha256") or "")
    axis_hash = str(payload.get("axis_lock_sha256") or "")
    if len(source_hash) != 64 or len(axis_hash) != 64:
        raise ValueError("fixture source/axis hashes must be SHA-256")

    adjustments = []
    for row in payload.get("adjustments") or []:
        key = str(row.get("adjustment_key") or "")
        proof_hash = str(row.get("proof_envelope_sha256") or "")
        final_hash = str(row.get("final_map_hash") or "")
        if len(key) != 16 or len(proof_hash) != 64 or len(final_hash) != 64:
            raise ValueError("invalid privacy-safe adjustment proof identity")
        adjustments.append(
            Adjustment(
                adjustment_key=key,
                cell_count=int(row["cell_count"]),
                started_at_ms=int(row["started_at_ms"]),
                ended_at_ms=int(row["ended_at_ms"]),
                final_map_hash=final_hash,
                delta_median=float(row["delta_median"]),
                rpm_min=float(row["rpm_min"]),
                rpm_max=float(row["rpm_max"]),
                petrol_ms_min=float(row["petrol_ms_min"]),
                petrol_ms_max=float(row["petrol_ms_max"]),
                proof_envelope_sha256=proof_hash,
            )
        )
    fixture = AdjustmentFixture(
        schema=payload["schema"],
        source_content_sha256=source_hash,
        axis_schema=str(payload.get("axis_schema") or ""),
        axis_lock_sha256=axis_hash,
        cell_event_count=int(payload.get("cell_event_count") or 0),
        adjustments=tuple(sorted(adjustments, key=lambda item: (item.started_at_ms, item.adjustment_key))),
    )
    if len(fixture.adjustments) != int(payload.get("intervention_count") or -1):
        raise ValueError("fixture intervention count mismatch")
    if sum(item.cell_count for item in fixture.adjustments) != fixture.cell_event_count:
        raise ValueError("fixture cell-event count mismatch")
    return fixture


def _comparable(a: Outcome, b: Outcome, *, rpm_tolerance: float = 80.0, map_tolerance: float = 0.02) -> bool:
    return abs(a.rpm - b.rpm) <= rpm_tolerance and abs(a.map_bar - b.map_bar) <= map_tolerance


def evaluate_adjustment(
    pre: Sequence[Outcome],
    post: Sequence[Outcome],
    adjustment: Adjustment,
) -> CausalResult:
    if not isinstance(adjustment, Adjustment):
        raise TypeError("adjustment must be Adjustment")
    comparable_pre: set[int] = set()
    comparable_post: set[int] = set()
    pair_count = 0
    for i, before in enumerate(pre):
        if before.timestamp_ms > adjustment.ended_at_ms:
            continue
        for j, after in enumerate(post):
            if after.timestamp_ms < adjustment.started_at_ms:
                continue
            if _comparable(before, after):
                comparable_pre.add(i)
                comparable_post.add(j)
                pair_count += 1
    if not comparable_pre or not comparable_post:
        return CausalResult(
            "ABSTAIN_INSUFFICIENT_COMPARABLE_PRE_POST", None, None, None, pair_count
        )

    pre_median = float(statistics.median(pre[i].absolute_error for i in sorted(comparable_pre)))
    post_median = float(statistics.median(post[j].absolute_error for j in sorted(comparable_post)))
    return CausalResult(
        "COMPARABLE_EFFECT_ESTIMATE",
        post_median - pre_median,
        pre_median,
        post_median,
        pair_count,
    )
