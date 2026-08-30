"""Fail-closed MAP_K sensitivity calibration for the RED V8.2 Science Blend.

Sensitivity is a causal quantity. It remains unavailable until governed real
intervention outcomes are comparable; confirmed cell writes or intervention
counts are never substituted for independent effect observations.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

from lab.red_blend.causal_science import CausalResult, RealCausalAudit


@dataclass(frozen=True)
class SensitivityResult:
    status: str
    sensitivity: float | None
    independent_effect_count: int
    p_improve: float | None = None
    actionable: bool = False


def calibrate_map_k_sensitivity(
    audit: RealCausalAudit,
    intervention_effects: Sequence[CausalResult],
) -> SensitivityResult:
    """Calibrate only from independently linked real intervention outcomes.

    Even a list of comparable effect estimates is insufficient by itself for a
    sensitivity coefficient because the governed link between MAP_K intervention
    magnitude and real outcome magnitude must also be present. The current gate
    therefore has two explicit fail-closed states.
    """
    if not isinstance(audit, RealCausalAudit):
        raise TypeError("audit must be RealCausalAudit")

    effects = [
        item
        for item in intervention_effects
        if isinstance(item, CausalResult)
        and item.status == "COMPARABLE_EFFECT_ESTIMATE"
        and item.effect_abs_error_delta is not None
    ]

    if audit.status != "CAUSAL_OUTCOME_SUPPORT_PROVEN" or audit.comparable_interventions <= 0:
        return SensitivityResult(
            status="BLOCKED_BY_INSUFFICIENT_CAUSAL_OUTCOME_SUPPORT",
            sensitivity=None,
            independent_effect_count=0,
        )

    return SensitivityResult(
        status="BLOCKED_BY_UNCALIBRATED_INTERVENTION_MAGNITUDE_LINK",
        sensitivity=None,
        independent_effect_count=len(effects),
    )
