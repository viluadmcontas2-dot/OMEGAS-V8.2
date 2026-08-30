"""Offline session-independence diagnostics for RED V8.2 Science Blend.

This module separates local within-session precision from persistence across
independent sessions. It is a falsification/replay tool only and has no Android
or ECU authority.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import dataclass
import json
import math
from pathlib import Path
import statistics
from typing import Any, Mapping, Sequence

from lab.red_blend.local_science import (
    MultimodalityDecision,
    MultimodalityPolicy,
    detect_multimodality,
)


@dataclass(frozen=True)
class SessionMean:
    session_key: str
    count: int
    mean: float


@dataclass(frozen=True)
class SessionVarianceDecomposition:
    session_count: int
    total_count: int
    grand_mean: float
    within_variance: float
    between_session_variance: float
    icc: float
    effective_group_size: float
    session_means: tuple[SessionMean, ...]
    method_label: str = "UNEQUAL_ONE_WAY_RANDOM_EFFECTS_MOMENTS"


@dataclass(frozen=True)
class LosoFold:
    held_out_session: str
    train_session_count: int
    observed_mean: float
    predicted_mean: float
    abs_relative_error: float


@dataclass(frozen=True)
class LeaveOneSessionOutReport:
    session_count: int
    median_abs_relative_error: float
    p90_abs_relative_error: float
    max_abs_relative_error: float
    folds: tuple[LosoFold, ...]
    predictor_label: str = "SESSION_BALANCED_MEAN"


@dataclass(frozen=True)
class SessionMixtureAttribution:
    pooled: MultimodalityDecision
    session_centered: MultimodalityDecision
    bic_gain_drop: float
    separation_drop_sigma: float
    interpretation: str
    policy_label: str = "LAB_HEURISTIC"


@dataclass(frozen=True)
class RealSessionRegionAudit:
    rpm_bin: int
    map_bin: int
    count: int
    session_count: int
    decomposition: SessionVarianceDecomposition | None
    loso: LeaveOneSessionOutReport | None
    mixture_attribution: SessionMixtureAttribution | None
    independent_status: str


@dataclass(frozen=True)
class RealSessionAuditReport:
    fuel: str
    total_fuel_episodes: int
    region_count: int
    session_audited_regions: int
    insufficient_independent_regions: int
    regions: tuple[RealSessionRegionAudit, ...]
    claim_scope: str = "SESSION_INDEPENDENCE_DIAGNOSTIC_NOT_PRODUCTION"


def _validated_groups(groups: Mapping[str, Sequence[float]], *, minimum_sessions: int = 2) -> dict[str, list[float]]:
    if len(groups) < minimum_sessions:
        raise ValueError(f"at least {minimum_sessions} independent sessions are required")
    result: dict[str, list[float]] = {}
    for key in sorted(groups):
        if not isinstance(key, str) or not key:
            raise ValueError("session keys must be non-empty strings")
        values = [float(x) for x in groups[key]]
        if not values:
            raise ValueError("each session must contain at least one sample")
        if not all(math.isfinite(x) for x in values):
            raise ValueError("all session samples must be finite")
        result[key] = values
    return result


def _quantile(values: Sequence[float], q: float) -> float:
    ordered = sorted(float(x) for x in values)
    if not ordered:
        raise ValueError("quantile requires values")
    index = (len(ordered) - 1) * q
    lo = int(math.floor(index))
    hi = int(math.ceil(index))
    if lo == hi:
        return ordered[lo]
    fraction = index - lo
    return ordered[lo] * (1.0 - fraction) + ordered[hi] * fraction


def decompose_sessions(groups: Mapping[str, Sequence[float]]) -> SessionVarianceDecomposition:
    """Unequal-size one-way random-effects moments decomposition.

    Repeated local observations remain useful for MSW/local precision. The
    between-session component is estimated separately so frame count cannot
    silently become independent-session evidence.
    """
    clean = _validated_groups(groups, minimum_sessions=2)
    session_means = tuple(
        SessionMean(key, len(values), statistics.fmean(values))
        for key, values in clean.items()
    )
    k = len(session_means)
    n = sum(item.count for item in session_means)
    if n <= k:
        # Every group is a singleton: no within-session replication exists.
        grand = statistics.fmean(item.mean for item in session_means)
        between = statistics.variance(item.mean for item in session_means) if k > 1 else 0.0
        return SessionVarianceDecomposition(
            session_count=k,
            total_count=n,
            grand_mean=grand,
            within_variance=0.0,
            between_session_variance=max(0.0, between),
            icc=1.0 if between > 0.0 else 0.0,
            effective_group_size=1.0,
            session_means=session_means,
        )

    grand = sum(item.count * item.mean for item in session_means) / n
    ssw = 0.0
    for item in session_means:
        values = clean[item.session_key]
        ssw += sum((x - item.mean) ** 2 for x in values)
    msw = ssw / (n - k)

    ssb = sum(item.count * (item.mean - grand) ** 2 for item in session_means)
    msb = ssb / (k - 1)
    sum_n2 = sum(item.count * item.count for item in session_means)
    n0 = (n - (sum_n2 / n)) / (k - 1)
    tau2 = max(0.0, (msb - msw) / n0) if n0 > 0.0 else 0.0
    denom = tau2 + msw
    icc = tau2 / denom if denom > 0.0 else 0.0
    return SessionVarianceDecomposition(
        session_count=k,
        total_count=n,
        grand_mean=grand,
        within_variance=msw,
        between_session_variance=tau2,
        icc=min(1.0, max(0.0, icc)),
        effective_group_size=n0,
        session_means=session_means,
    )


def leave_one_session_out(groups: Mapping[str, Sequence[float]]) -> LeaveOneSessionOutReport:
    """Predict each unseen session mean from a session-balanced training mean."""
    clean = _validated_groups(groups, minimum_sessions=3)
    means = {key: statistics.fmean(values) for key, values in clean.items()}
    folds: list[LosoFold] = []
    for held_out in sorted(means):
        train_means = [means[key] for key in sorted(means) if key != held_out]
        predicted = statistics.fmean(train_means)
        observed = means[held_out]
        denominator = abs(observed)
        error = abs(predicted - observed) / denominator if denominator > 1e-12 else math.inf
        folds.append(
            LosoFold(
                held_out_session=held_out,
                train_session_count=len(train_means),
                observed_mean=observed,
                predicted_mean=predicted,
                abs_relative_error=error,
            )
        )
    errors = [fold.abs_relative_error for fold in folds]
    return LeaveOneSessionOutReport(
        session_count=len(folds),
        median_abs_relative_error=statistics.median(errors),
        p90_abs_relative_error=_quantile(errors, 0.90),
        max_abs_relative_error=max(errors),
        folds=tuple(folds),
    )


def attribute_session_mixture(
    groups: Mapping[str, Sequence[float]],
    policy: MultimodalityPolicy = MultimodalityPolicy(),
) -> SessionMixtureAttribution:
    """Test whether pooled GMM structure survives removal of session offsets."""
    clean = _validated_groups(groups, minimum_sessions=2)
    pooled = [x for key in sorted(clean) for x in clean[key]]
    if len(pooled) < 2:
        raise ValueError("at least two pooled samples are required")
    grand = statistics.fmean(pooled)
    centered: list[float] = []
    for key in sorted(clean):
        values = clean[key]
        session_mean = statistics.fmean(values)
        centered.extend((x - session_mean) + grand for x in values)

    pooled_decision = detect_multimodality(pooled, policy)
    centered_decision = detect_multimodality(centered, policy)
    if not pooled_decision.is_multimodal and not centered_decision.is_multimodal:
        interpretation = "NO_STRONG_POOLED_MULTIMODALITY"
    elif pooled_decision.is_multimodal and not centered_decision.is_multimodal:
        interpretation = "SESSION_OFFSETS_DOMINANT_CANDIDATE"
    elif pooled_decision.is_multimodal and centered_decision.is_multimodal:
        interpretation = "WITHIN_SESSION_REGIME_CANDIDATE"
    else:
        interpretation = "MIXTURE_ATTRIBUTION_AMBIGUOUS"

    return SessionMixtureAttribution(
        pooled=pooled_decision,
        session_centered=centered_decision,
        bic_gain_drop=pooled_decision.bic_gain - centered_decision.bic_gain,
        separation_drop_sigma=pooled_decision.separation_sigma - centered_decision.separation_sigma,
        interpretation=interpretation,
    )


def audit_real_session_regions(
    episodes: Sequence[dict[str, Any]],
    *,
    fuel: str,
    min_samples: int = 4,
    min_independent_sessions: int = 3,
    policy: MultimodalityPolicy = MultimodalityPolicy(),
) -> RealSessionAuditReport:
    if fuel not in {"GASOLINA", "GNV"}:
        raise ValueError("fuel must be GASOLINA or GNV")
    if min_samples < 2:
        raise ValueError("min_samples must be >= 2")
    if min_independent_sessions < 3:
        raise ValueError("min_independent_sessions must be >= 3")

    selected = [e for e in episodes if e.get("fuel") == fuel]
    regions: dict[tuple[int, int], list[dict[str, Any]]] = defaultdict(list)
    for episode in selected:
        try:
            rpm_bin = int(episode["rpm_bin"])
            map_bin = int(episode["map_bin"])
            value = float(episode["petrol_ms"])
            session_key = str(episode["session_key"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError("episode missing session-science fields") from exc
        if not math.isfinite(value) or not session_key:
            raise ValueError("episode has invalid session-science value")
        regions[(rpm_bin, map_bin)].append(episode)

    audits: list[RealSessionRegionAudit] = []
    for (rpm_bin, map_bin), group in sorted(regions.items()):
        if len(group) < min_samples:
            continue
        session_groups: dict[str, list[float]] = defaultdict(list)
        for episode in group:
            session_groups[str(episode["session_key"])].append(float(episode["petrol_ms"]))
        session_count = len(session_groups)

        decomposition = decompose_sessions(session_groups) if session_count >= 2 else None
        attribution = attribute_session_mixture(session_groups, policy) if session_count >= 2 else None
        if session_count >= min_independent_sessions:
            loso = leave_one_session_out(session_groups)
            status = "SESSION_AUDITED"
        else:
            loso = None
            status = "INSUFFICIENT_INDEPENDENT_SESSIONS"
        audits.append(
            RealSessionRegionAudit(
                rpm_bin=rpm_bin,
                map_bin=map_bin,
                count=len(group),
                session_count=session_count,
                decomposition=decomposition,
                loso=loso,
                mixture_attribution=attribution,
                independent_status=status,
            )
        )

    audits.sort(key=lambda item: (-item.count, item.rpm_bin, item.map_bin))
    audited = sum(1 for item in audits if item.independent_status == "SESSION_AUDITED")
    return RealSessionAuditReport(
        fuel=fuel,
        total_fuel_episodes=len(selected),
        region_count=len(audits),
        session_audited_regions=audited,
        insufficient_independent_regions=len(audits) - audited,
        regions=tuple(audits),
    )


def _compact_region(region: RealSessionRegionAudit) -> dict[str, Any]:
    decomposition = region.decomposition
    loso = region.loso
    attribution = region.mixture_attribution
    return {
        "rpm_bin": region.rpm_bin,
        "map_bin": region.map_bin,
        "count": region.count,
        "session_count": region.session_count,
        "independent_status": region.independent_status,
        "within_variance": decomposition.within_variance if decomposition else None,
        "between_session_variance": decomposition.between_session_variance if decomposition else None,
        "icc": decomposition.icc if decomposition else None,
        "loso_median_abs_relative_error": loso.median_abs_relative_error if loso else None,
        "loso_p90_abs_relative_error": loso.p90_abs_relative_error if loso else None,
        "pooled_bic_gain": attribution.pooled.bic_gain if attribution else None,
        "centered_bic_gain": attribution.session_centered.bic_gain if attribution else None,
        "bic_gain_drop": attribution.bic_gain_drop if attribution else None,
        "mixture_interpretation": attribution.interpretation if attribution else None,
    }


def main() -> int:
    from lab.red_blend.real_corpus import load_governed_fixture

    parser = argparse.ArgumentParser()
    parser.add_argument("parts_dir", type=Path)
    parser.add_argument("index", type=Path)
    parser.add_argument("--fuel", choices=["GASOLINA", "GNV"], required=True)
    parser.add_argument("--min-samples", type=int, default=4)
    parser.add_argument("--min-independent-sessions", type=int, default=3)
    parser.add_argument("--top", type=int, default=20)
    args = parser.parse_args()

    episodes = load_governed_fixture(args.parts_dir, args.index)
    report = audit_real_session_regions(
        episodes,
        fuel=args.fuel,
        min_samples=args.min_samples,
        min_independent_sessions=args.min_independent_sessions,
    )
    payload = {
        "fuel": report.fuel,
        "total_fuel_episodes": report.total_fuel_episodes,
        "region_count": report.region_count,
        "session_audited_regions": report.session_audited_regions,
        "insufficient_independent_regions": report.insufficient_independent_regions,
        "claim_scope": report.claim_scope,
        "regions": [_compact_region(region) for region in report.regions[: max(0, args.top)]],
    }
    print(json.dumps(payload, sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
