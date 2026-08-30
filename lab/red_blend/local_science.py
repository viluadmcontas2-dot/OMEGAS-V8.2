"""Deterministic offline local-distribution science for RED V8.2 Blend.

This module is deliberately dependency-free and outside the Android runtime.
Thresholds in MultimodalityPolicy are LAB_HEURISTIC only, never production authority.
"""
from __future__ import annotations

from dataclasses import dataclass
import math
import random
import statistics
from typing import Sequence


_VARIANCE_FLOOR = 1e-12


@dataclass(frozen=True)
class DistributionSummary:
    count: int
    mean: float
    median: float
    std: float
    mad: float
    p10: float
    p90: float
    cv: float


@dataclass(frozen=True)
class BootstrapInterval:
    low: float
    estimate: float
    high: float
    draws: int
    seed: int


@dataclass(frozen=True)
class GaussianFit:
    mean: float
    variance: float
    log_likelihood: float
    bic: float


@dataclass(frozen=True)
class GaussianComponent:
    weight: float
    mean: float
    variance: float


@dataclass(frozen=True)
class GaussianMixtureFit:
    components: tuple[GaussianComponent, GaussianComponent]
    log_likelihood: float
    bic: float
    iterations: int
    converged: bool


@dataclass(frozen=True)
class MultimodalityPolicy:
    bic_gain_min: float = 10.0
    min_component_weight: float = 0.15
    separation_sigma_min: float = 2.5


@dataclass(frozen=True)
class MultimodalityDecision:
    is_multimodal: bool
    bic_gain: float
    min_component_weight: float
    separation_sigma: float
    one: GaussianFit
    two: GaussianMixtureFit
    policy_label: str = "LAB_HEURISTIC"


def _validated(samples: Sequence[float]) -> list[float]:
    values = [float(x) for x in samples]
    if len(values) < 2:
        raise ValueError("at least two finite samples are required")
    if not all(math.isfinite(x) for x in values):
        raise ValueError("all samples must be finite")
    return values


def _quantile(sorted_values: Sequence[float], q: float) -> float:
    if not 0.0 <= q <= 1.0:
        raise ValueError("q must be in [0, 1]")
    n = len(sorted_values)
    if n == 1:
        return float(sorted_values[0])
    index = (n - 1) * q
    lo = int(math.floor(index))
    hi = int(math.ceil(index))
    if lo == hi:
        return float(sorted_values[lo])
    fraction = index - lo
    return float(sorted_values[lo] * (1.0 - fraction) + sorted_values[hi] * fraction)


def summarize_distribution(samples: Sequence[float]) -> DistributionSummary:
    values = _validated(samples)
    ordered = sorted(values)
    mean = statistics.fmean(values)
    median = statistics.median(ordered)
    std = statistics.stdev(values)
    deviations = sorted(abs(x - median) for x in values)
    mad = statistics.median(deviations)
    cv = std / abs(mean) if abs(mean) > 1e-12 else math.inf
    return DistributionSummary(
        count=len(values),
        mean=mean,
        median=median,
        std=std,
        mad=mad,
        p10=_quantile(ordered, 0.10),
        p90=_quantile(ordered, 0.90),
        cv=cv,
    )


def bootstrap_mean_interval(
    samples: Sequence[float],
    draws: int = 2000,
    seed: int = 0,
    alpha: float = 0.05,
) -> BootstrapInterval:
    values = _validated(samples)
    if draws < 100:
        raise ValueError("draws must be >= 100")
    if not 0.0 < alpha < 1.0:
        raise ValueError("alpha must be in (0, 1)")
    rng = random.Random(seed)
    n = len(values)
    means = []
    for _ in range(draws):
        total = 0.0
        for _ in range(n):
            total += values[rng.randrange(n)]
        means.append(total / n)
    means.sort()
    return BootstrapInterval(
        low=_quantile(means, alpha / 2.0),
        estimate=statistics.fmean(values),
        high=_quantile(means, 1.0 - alpha / 2.0),
        draws=draws,
        seed=seed,
    )


def _gaussian_log_pdf(x: float, mean: float, variance: float) -> float:
    variance = max(float(variance), _VARIANCE_FLOOR)
    return -0.5 * (math.log(2.0 * math.pi * variance) + ((x - mean) ** 2) / variance)


def fit_gaussian(samples: Sequence[float]) -> GaussianFit:
    values = _validated(samples)
    n = len(values)
    mean = statistics.fmean(values)
    variance = max(sum((x - mean) ** 2 for x in values) / n, _VARIANCE_FLOOR)
    log_likelihood = sum(_gaussian_log_pdf(x, mean, variance) for x in values)
    bic = 2.0 * math.log(n) - 2.0 * log_likelihood
    return GaussianFit(mean=mean, variance=variance, log_likelihood=log_likelihood, bic=bic)


def _logsumexp2(a: float, b: float) -> float:
    m = max(a, b)
    return m + math.log(math.exp(a - m) + math.exp(b - m))


def _degenerate_mixture(one: GaussianFit, n: int) -> GaussianMixtureFit:
    component = GaussianComponent(weight=0.5, mean=one.mean, variance=one.variance)
    bic = 5.0 * math.log(n) - 2.0 * one.log_likelihood
    return GaussianMixtureFit(
        components=(component, component),
        log_likelihood=one.log_likelihood,
        bic=bic,
        iterations=0,
        converged=False,
    )


def fit_gmm2(
    samples: Sequence[float],
    max_iterations: int = 200,
    tolerance: float = 1e-9,
) -> GaussianMixtureFit:
    values = _validated(samples)
    if max_iterations < 1:
        raise ValueError("max_iterations must be >= 1")
    if tolerance <= 0.0 or not math.isfinite(tolerance):
        raise ValueError("tolerance must be finite and > 0")

    n = len(values)
    ordered = sorted(values)
    one = fit_gaussian(values)
    means = [_quantile(ordered, 0.25), _quantile(ordered, 0.75)]
    variances = [one.variance, one.variance]
    weights = [0.5, 0.5]
    previous = -math.inf
    converged = False
    final_ll = -math.inf
    iterations = 0

    for iteration in range(1, max_iterations + 1):
        resp0: list[float] = []
        resp1: list[float] = []
        log_likelihood = 0.0

        for x in values:
            l0 = math.log(max(weights[0], 1e-300)) + _gaussian_log_pdf(x, means[0], variances[0])
            l1 = math.log(max(weights[1], 1e-300)) + _gaussian_log_pdf(x, means[1], variances[1])
            denom = _logsumexp2(l0, l1)
            r0 = math.exp(l0 - denom)
            resp0.append(r0)
            resp1.append(1.0 - r0)
            log_likelihood += denom

        mass0 = sum(resp0)
        mass1 = sum(resp1)
        if mass0 < 1e-9 or mass1 < 1e-9:
            return _degenerate_mixture(one, n)

        new_means = [
            sum(r * x for r, x in zip(resp0, values)) / mass0,
            sum(r * x for r, x in zip(resp1, values)) / mass1,
        ]
        new_variances = [
            max(
                sum(r * (x - new_means[0]) ** 2 for r, x in zip(resp0, values)) / mass0,
                _VARIANCE_FLOOR,
            ),
            max(
                sum(r * (x - new_means[1]) ** 2 for r, x in zip(resp1, values)) / mass1,
                _VARIANCE_FLOOR,
            ),
        ]
        new_weights = [mass0 / n, mass1 / n]

        means = new_means
        variances = new_variances
        weights = new_weights
        final_ll = log_likelihood
        iterations = iteration

        if math.isfinite(previous) and abs(log_likelihood - previous) <= tolerance * (1.0 + abs(previous)):
            converged = True
            break
        previous = log_likelihood

    final_ll = 0.0
    for x in values:
        l0 = math.log(max(weights[0], 1e-300)) + _gaussian_log_pdf(x, means[0], variances[0])
        l1 = math.log(max(weights[1], 1e-300)) + _gaussian_log_pdf(x, means[1], variances[1])
        final_ll += _logsumexp2(l0, l1)

    components = [
        GaussianComponent(weight=weights[0], mean=means[0], variance=variances[0]),
        GaussianComponent(weight=weights[1], mean=means[1], variance=variances[1]),
    ]
    components.sort(key=lambda component: component.mean)
    bic = 5.0 * math.log(n) - 2.0 * final_ll
    return GaussianMixtureFit(
        components=(components[0], components[1]),
        log_likelihood=final_ll,
        bic=bic,
        iterations=iterations,
        converged=converged,
    )


def detect_multimodality(
    samples: Sequence[float],
    policy: MultimodalityPolicy = MultimodalityPolicy(),
) -> MultimodalityDecision:
    if policy.bic_gain_min < 0.0:
        raise ValueError("bic_gain_min must be >= 0")
    if not 0.0 < policy.min_component_weight <= 0.5:
        raise ValueError("min_component_weight must be in (0, 0.5]")
    if policy.separation_sigma_min < 0.0:
        raise ValueError("separation_sigma_min must be >= 0")

    one = fit_gaussian(samples)
    two = fit_gmm2(samples)
    c1, c2 = two.components
    bic_gain = one.bic - two.bic
    min_weight = min(c1.weight, c2.weight)
    pooled_sigma = math.sqrt(max((c1.variance + c2.variance) / 2.0, _VARIANCE_FLOOR))
    separation = abs(c2.mean - c1.mean) / pooled_sigma

    is_multimodal = (
        two.converged
        and bic_gain >= policy.bic_gain_min
        and min_weight >= policy.min_component_weight
        and separation >= policy.separation_sigma_min
    )
    return MultimodalityDecision(
        is_multimodal=is_multimodal,
        bic_gain=bic_gain,
        min_component_weight=min_weight,
        separation_sigma=separation,
        one=one,
        two=two,
    )
