"""Promotion-readiness audit for a calibration-aware RED ablation.

The module does not manufacture a candidate from hashes.  A calibration state
is usable only when its chronology and physical contents are explicit.  Until
then the deployed RED prediction is the exact fallback.
"""
from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any, Mapping, Sequence

from lab.red_blend.calibration_chronology import ClockBridgeAudit
from lab.red_blend.causal_science import AdjustmentFixture


@dataclass(frozen=True)
class CalibrationAblationAudit:
    status: str
    apparent_post_map_states: int
    apparent_post_sessions: int
    apparent_post_episodes: int
    explicit_map_states: int
    explicit_curve_states: int
    candidate_supported: int
    red_median_error: float
    red_p90_error: float
    red_p95_error: float
    candidate_median_error: float | None
    candidate_p90_error: float | None
    candidate_p95_error: float | None
    promotion_allowed: bool
    red_fallback_preserved: bool
    android_changed: bool = False
    p_improve_proven: bool = False
    claim_scope: str = "OFFLINE_PREDICTOR_PROMOTION_GATE"


def _payload(source: Path | Mapping[str, Any]) -> Mapping[str, Any]:
    if isinstance(source, Mapping):
        return source
    return json.loads(Path(source).read_text(encoding="utf-8"))


def audit_calibration_ablation_readiness(
    report_source: Path | Mapping[str, Any],
    episodes: Sequence[Mapping[str, Any]],
    fixture: AdjustmentFixture,
    clock: ClockBridgeAudit,
) -> CalibrationAblationAudit:
    report = _payload(report_source)
    baseline = report.get("gasoline_walk_forward") or {}
    required = (
        "median_abs_relative_error",
        "p90_abs_relative_error",
        "p95_abs_relative_error",
    )
    if any(key not in baseline for key in required):
        raise ValueError("corpus report lacks governed RED metrics")
    if not fixture.adjustments:
        raise ValueError("calibration ablation requires confirmed adjustments")

    last = max(item.ended_at_ms for item in fixture.adjustments)
    apparent = [item for item in episodes if int(item["start_ms"]) > last]
    apparent_sessions = {str(item["session_key"]) for item in apparent}
    # The aggregate fixture establishes the final hash but omits the full 12x12
    # state and contains no 30-point Curve K state.  Even a future clock proof
    # would therefore be insufficient for the requested curve+map ablation.
    apparent_states = {fixture.adjustments[-1].final_map_hash} if apparent else set()
    has_detailed_map_state = fixture.schema.endswith("-detailed-v1")
    explicit_map_states = int(clock.common_clock_proven and has_detailed_map_state)
    explicit_curve_states = 0
    ready = explicit_map_states >= 2 and explicit_curve_states >= 2

    return CalibrationAblationAudit(
        status=(
            "READY_FOR_BLIND_ABLATION"
            if ready
            else "DEFER_INSUFFICIENT_EXPLICIT_CALIBRATION_STATES"
        ),
        apparent_post_map_states=len(apparent_states),
        apparent_post_sessions=len(apparent_sessions),
        apparent_post_episodes=len(apparent),
        explicit_map_states=explicit_map_states,
        explicit_curve_states=explicit_curve_states,
        candidate_supported=0,
        red_median_error=float(baseline[required[0]]),
        red_p90_error=float(baseline[required[1]]),
        red_p95_error=float(baseline[required[2]]),
        candidate_median_error=None,
        candidate_p90_error=None,
        candidate_p95_error=None,
        promotion_allowed=False,
        red_fallback_preserved=True,
    )
