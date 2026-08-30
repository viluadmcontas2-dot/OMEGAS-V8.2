"""Fail-closed transfer/OOD gate for RED V8.2 Science Blend.

This module governs only whether offline transfer is scientifically supportable.
It never suppresses valid local RPM x MAP evidence, never authorizes ECU writes,
and never manufactures P(improve). Neighbor support reuses the already-proven
WU-006 baseline geometry instead of introducing another distance threshold.
"""
from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Any, Mapping, Sequence

from lab.red_blend.walk_forward import Prediction, predict_wu006_neighbor_baseline


@dataclass(frozen=True)
class OodDecision:
    status: str
    transfer_supported: bool
    prediction: Prediction | None
    local_evidence_policy: str = "LOCAL_EVIDENCE_RETAINED"
    p_improve: float | None = None
    actionable: bool = False
    claim_scope: str = "OOD_TRANSFER_FALSIFICATION_OFFLINE_NOT_PRODUCTION"


_BLOCKED_LOCAL_REGIMES = frozenset(
    {
        "MULTIMODAL",
        "AMBIGUOUS_MIXTURE_SIGNAL",
        "WITHIN_SESSION_REGIME_CANDIDATE",
    }
)


def _target_is_valid(target: Mapping[str, Any]) -> bool:
    try:
        values = (
            float(target["rpm"]),
            float(target["map_bar"]),
            float(target["petrol_ms"]),
        )
    except (KeyError, TypeError, ValueError):
        return False
    if not all(math.isfinite(value) for value in values):
        return False
    order = target.get("order")
    if isinstance(order, bool) or not isinstance(order, int):
        return False
    if not str(target.get("session_key") or ""):
        return False
    return True


def _decision(status: str, *, prediction: Prediction | None = None) -> OodDecision:
    supported = prediction is not None and status == "IN_DISTRIBUTION_SUPPORTED"
    return OodDecision(
        status=status,
        transfer_supported=supported,
        prediction=prediction,
    )


def assess_transfer_ood(
    training: Sequence[Mapping[str, Any]],
    target: Mapping[str, Any],
    *,
    local_regime_status: str = "UNIMODAL_SUPPORTED",
    independent_status: str = "SESSION_AUDITED",
    calibration_epoch_compatible: bool = True,
) -> OodDecision:
    """Falsify unsupported transfer while retaining all valid local evidence."""
    if not isinstance(target, Mapping) or not _target_is_valid(target):
        return _decision("ABSTAIN_INVALID_TELEMETRY")

    if local_regime_status in _BLOCKED_LOCAL_REGIMES:
        return _decision("ABSTAIN_UNRESOLVED_LOCAL_REGIME")

    if calibration_epoch_compatible is not True:
        return _decision("ABSTAIN_CALIBRATION_EPOCH_DRIFT")

    if independent_status == "INSUFFICIENT_INDEPENDENT_SESSIONS":
        return _decision("ABSTAIN_INSUFFICIENT_TRANSFER_SUPPORT")

    try:
        prediction = predict_wu006_neighbor_baseline(training, target)
    except (KeyError, TypeError, ValueError, OverflowError):
        return _decision("ABSTAIN_INVALID_TELEMETRY")

    if prediction is None:
        return _decision("ABSTAIN_OUTSIDE_PROVEN_NEIGHBOR_SUPPORT")

    return _decision("IN_DISTRIBUTION_SUPPORTED", prediction=prediction)
