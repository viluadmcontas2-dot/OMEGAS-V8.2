"""Fail-closed P(improve) gate for RED V8.2 Science Blend.

P(improve) is a causal probability. It remains unavailable until governed,
held-out intervention outcomes and calibrated MAP_K sensitivity exist. Synthetic
effects, confirmed cell-write counts, or empirical risk curves cannot substitute
for causal outcome calibration.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

from lab.red_blend.causal_science import CausalResult, RealCausalAudit
from lab.red_blend.sensitivity_science import SensitivityResult


@dataclass(frozen=True)
class PImproveResult:
    status: str
    p_improve: float | None
    independent_outcome_count: int
    actionable: bool = False
    claim_scope: str = "P_IMPROVE_CAUSAL_ONLY_FAIL_CLOSED"


def calibrate_p_improve(
    audit: RealCausalAudit,
    sensitivity: SensitivityResult,
    held_out_effects: Sequence[CausalResult],
) -> PImproveResult:
    if not isinstance(audit, RealCausalAudit):
        raise TypeError("audit must be RealCausalAudit")
    if not isinstance(sensitivity, SensitivityResult):
        raise TypeError("sensitivity must be SensitivityResult")

    # The current governed corpus has no proven common timebase linking outcome
    # episodes to the confirmed manual MAP_K intervention history. Until that
    # causal bridge and sensitivity are proven, no supplied effect sequence may
    # be promoted into a probability claim.
    if audit.status != "CAUSAL_OUTCOME_SUPPORT_PROVEN" or sensitivity.sensitivity is None:
        return PImproveResult(
            status="BLOCKED_BY_UNCALIBRATED_CAUSAL_OUTCOMES",
            p_improve=None,
            independent_outcome_count=0,
        )

    # A future positive path requires an explicit held-out causal calibration
    # contract. Do not infer it here merely because comparable effects exist.
    return PImproveResult(
        status="BLOCKED_BY_UNCALIBRATED_CAUSAL_OUTCOMES",
        p_improve=None,
        independent_outcome_count=0,
    )
