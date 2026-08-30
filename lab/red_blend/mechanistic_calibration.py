"""Offline mechanistic calibration decomposition for one governed vehicle.

This module turns a paired gasoline-reference/CNG observation into a relative
calibration demand.  It never writes the ECU, never claims P(improve), and is
not imported by Android runtime code.
"""
from __future__ import annotations

from dataclasses import dataclass
import math
import statistics
from typing import Any, Mapping, Sequence


@dataclass(frozen=True)
class CalibrationObservation:
    reference_ms: float
    cng_ms: float
    rpm: float
    map_bar: float
    session_key: str
    evidence_mass: int = 1

    def __post_init__(self) -> None:
        numeric = (self.reference_ms, self.cng_ms, self.rpm, self.map_bar)
        if not all(math.isfinite(float(value)) for value in numeric):
            raise ValueError("calibration observation must be finite")
        if self.reference_ms <= 0.0 or self.cng_ms <= 0.0:
            raise ValueError("paired injection times must be positive")
        if self.rpm < 0.0 or self.map_bar < 0.0:
            raise ValueError("physical coordinate cannot be negative")
        if not str(self.session_key).strip():
            raise ValueError("session_key is required")
        if isinstance(self.evidence_mass, bool) or int(self.evidence_mass) < 1:
            raise ValueError("evidence_mass must be a positive integer")

    @property
    def combined_multiplier(self) -> float:
        return self.cng_ms / self.reference_ms

    @property
    def correction_log(self) -> float:
        return math.log(self.combined_multiplier)

    @property
    def error_percent(self) -> float:
        return 100.0 * (self.combined_multiplier - 1.0)


@dataclass(frozen=True)
class CorrectionDecomposition:
    combined_multiplier: float
    curve_multiplier: float
    map_multiplier: float
    global_log_correction: float
    local_log_residual: float
    global_status: str
    independent_global_regions: int
    independent_global_sessions: int


@dataclass(frozen=True)
class AssistedSuggestion:
    decomposition: CorrectionDecomposition
    ideal_effective_multiplier: float
    ideal_curve_factor: float
    ideal_map_value: float
    step_curve_factor: float
    step_map_value: float
    p_improve: None = None
    actionable: bool = False
    auto_write_ecu: bool = False


@dataclass(frozen=True)
class CorpusSimulationReport:
    total_gnv_episodes: int
    supported_pairs: int
    unsupported_pairs: int
    independent_gnv_sessions: int
    leakage_violations: int
    curve_supported_pairs: int
    local_only_pairs: int
    median_combined_multiplier: float
    p10_combined_multiplier: float
    p90_combined_multiplier: float
    evidence_weighted_median_multiplier: float
    reduce_demand_pairs: int
    deadband_pairs: int
    increase_demand_pairs: int
    claim_scope: str = "MECHANISTIC_ASU_SIMULATION_OFFLINE_NOT_PRODUCTION"
    p_improve: None = None
    actionable: bool = False
    auto_write_ecu: bool = False


def _region_key(observation: CalibrationObservation) -> tuple[int, int]:
    """A small deterministic independence cell, not an ECU map coordinate."""
    return (round(observation.rpm / 80.0), round(observation.map_bar / 0.02))


def _weighted_median(values: Sequence[tuple[float, int]]) -> float:
    if not values:
        raise ValueError("weighted median requires values")
    ordered = sorted((float(value), int(weight)) for value, weight in values)
    total = sum(weight for _, weight in ordered)
    threshold = total / 2.0
    accumulated = 0
    for value, weight in ordered:
        accumulated += weight
        if accumulated >= threshold:
            return value
    return ordered[-1][0]


def _transversal_centers(
    target: CalibrationObservation,
    observations: Sequence[CalibrationObservation],
    petrol_bandwidth_ms: float,
) -> tuple[list[float], int, int]:
    groups: dict[tuple[str, tuple[int, int]], list[tuple[float, int]]] = {}
    for observation in observations:
        if observation is target:
            continue
        if abs(observation.reference_ms - target.reference_ms) > petrol_bandwidth_ms:
            continue
        key = (observation.session_key, _region_key(observation))
        groups.setdefault(key, []).append(
            (observation.correction_log, observation.evidence_mass)
        )

    centers = [_weighted_median(values) for values in groups.values()]
    regions = {key[1] for key in groups}
    sessions = {key[0] for key in groups}
    return centers, len(regions), len(sessions)


def decompose_correction(
    target: CalibrationObservation,
    observations: Sequence[CalibrationObservation],
    *,
    petrol_bandwidth_ms: float = 0.50,
    minimum_global_regions: int = 3,
    minimum_global_sessions: int = 2,
) -> CorrectionDecomposition:
    if petrol_bandwidth_ms < 0.0:
        raise ValueError("petrol bandwidth cannot be negative")
    centers, region_count, session_count = _transversal_centers(
        target, observations, petrol_bandwidth_ms
    )
    supported = (
        region_count >= minimum_global_regions
        and session_count >= minimum_global_sessions
    )
    global_log = float(statistics.median(centers)) if supported else 0.0
    local_log = target.correction_log - global_log
    curve_multiplier = math.exp(global_log)
    map_multiplier = math.exp(local_log)
    return CorrectionDecomposition(
        combined_multiplier=target.combined_multiplier,
        curve_multiplier=curve_multiplier,
        map_multiplier=map_multiplier,
        global_log_correction=global_log,
        local_log_residual=local_log,
        global_status="SUPPORTED" if supported else "INSUFFICIENT_TRANSVERSAL_SUPPORT",
        independent_global_regions=region_count,
        independent_global_sessions=session_count,
    )


def _bounded_log_step(current: float, ideal: float, maximum: float) -> float:
    if current <= 0.0 or ideal <= 0.0:
        raise ValueError("calibration state must be positive")
    delta = math.log(ideal / current)
    bounded = max(-maximum, min(maximum, delta))
    return current * math.exp(bounded)


def build_assisted_suggestion(
    target: CalibrationObservation,
    observations: Sequence[CalibrationObservation],
    *,
    current_curve_factor: float,
    current_map_value: float,
    maximum_component_step: float = 0.05,
) -> AssistedSuggestion:
    if maximum_component_step <= 0.0:
        raise ValueError("maximum component step must be positive")
    decomposition = decompose_correction(target, observations)
    ideal_curve = current_curve_factor * decomposition.curve_multiplier
    ideal_map = current_map_value * decomposition.map_multiplier
    current_effective = current_curve_factor * current_map_value / 100.0
    return AssistedSuggestion(
        decomposition=decomposition,
        ideal_effective_multiplier=current_effective * target.combined_multiplier,
        ideal_curve_factor=ideal_curve,
        ideal_map_value=ideal_map,
        step_curve_factor=_bounded_log_step(
            current_curve_factor, ideal_curve, maximum_component_step
        ),
        step_map_value=_bounded_log_step(
            current_map_value, ideal_map, maximum_component_step
        ),
    )


def _quantile(values: Sequence[float], q: float) -> float:
    ordered = sorted(float(value) for value in values)
    if not ordered:
        raise ValueError("quantile requires values")
    position = (len(ordered) - 1) * q
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    fraction = position - lower
    return ordered[lower] * (1.0 - fraction) + ordered[upper] * fraction


def simulate_governed_corpus(
    episodes: Sequence[Mapping[str, Any]],
    *,
    deadband_fraction: float = 0.02,
) -> CorpusSimulationReport:
    """Build blind relative calibration demands from the governed WU-006 view.

    The proven RED neighbor baseline supplies gasoline references using only
    earlier gasoline sessions.  Earlier paired GNV observations may explain a
    transversal curve component; the remainder stays local.  No current ECU
    calibration is invented, so the output is relative and non-actionable.
    """
    if not 0.0 <= deadband_fraction < 1.0:
        raise ValueError("deadband_fraction outside [0, 1)")
    from lab.red_blend.walk_forward import predict_wu006_neighbor_baseline

    ordered = sorted(
        (dict(item) for item in episodes),
        key=lambda item: (
            int(item["order"]),
            int(item.get("start_ms", 0)),
            str(item["session_key"]),
            str(item.get("fuel", "")),
        ),
    )
    gnv = [item for item in ordered if item.get("fuel") == "GNV"]
    observations: list[CalibrationObservation] = []
    ratios: list[float] = []
    weighted_ratios: list[tuple[float, int]] = []
    curve_supported = 0
    leakage = 0
    reduce_count = 0
    deadband_count = 0
    increase_count = 0

    for episode in gnv:
        prediction = predict_wu006_neighbor_baseline(ordered, episode)
        if prediction is None:
            continue
        if prediction.max_training_order >= int(episode["order"]):
            leakage += 1
        observation = CalibrationObservation(
            reference_ms=prediction.predicted_ms,
            cng_ms=float(episode["petrol_ms"]),
            rpm=float(episode["rpm"]),
            map_bar=float(episode["map_bar"]),
            session_key=str(episode["session_key"]),
            evidence_mass=int(episode.get("window_count", 1)),
        )
        decomposition = decompose_correction(observation, observations)
        curve_supported += decomposition.global_status == "SUPPORTED"
        ratio = observation.combined_multiplier
        ratios.append(ratio)
        weighted_ratios.append((ratio, observation.evidence_mass))
        if ratio < 1.0 - deadband_fraction:
            reduce_count += 1
        elif ratio > 1.0 + deadband_fraction:
            increase_count += 1
        else:
            deadband_count += 1
        observations.append(observation)

    if not ratios:
        raise ValueError("governed corpus produced no comparable GNV pairs")
    return CorpusSimulationReport(
        total_gnv_episodes=len(gnv),
        supported_pairs=len(ratios),
        unsupported_pairs=len(gnv) - len(ratios),
        independent_gnv_sessions=len({item.session_key for item in observations}),
        leakage_violations=leakage,
        curve_supported_pairs=curve_supported,
        local_only_pairs=len(ratios) - curve_supported,
        median_combined_multiplier=float(statistics.median(ratios)),
        p10_combined_multiplier=_quantile(ratios, 0.10),
        p90_combined_multiplier=_quantile(ratios, 0.90),
        evidence_weighted_median_multiplier=math.exp(
            _weighted_median(
                [(math.log(ratio), weight) for ratio, weight in weighted_ratios]
            )
        ),
        reduce_demand_pairs=reduce_count,
        deadband_pairs=deadband_count,
        increase_demand_pairs=increase_count,
    )
