"""Deterministic synthetic harness for G3A / 102A.

This is a falsifier, not physical vehicle evidence.  It evaluates the adaptive
stopping policy against the legacy fixed-N/fixed-visit baseline using prefix
moments computed once per session (PREFIX_VECTORIZED).  Policy parameters are
selected only on the training fold and become immutable before holdout.
"""
from __future__ import annotations

from dataclasses import dataclass
from math import ceil, sqrt
from statistics import median
from typing import Iterable

HARNESS_MODE = "PREFIX_VECTORIZED_DETERMINISTIC_SYNTHETIC"
HARNESS_TIMEOUT = "HARNESS_TIMEOUT"
FIXED_FRAMES = 8
FIXED_VISITS = 3
MIN_ADAPTIVE_FRAMES = 3
DECISION_MARGIN_PCT = 0.20
UNCERTAINTY_SWEEP_PCT = (0.30, 0.40, 0.50, 0.60)


@dataclass(frozen=True)
class Observation:
    error_pct: float | None
    visit_id: str
    elapsed_ms: int
    status: str = "DATA"


@dataclass(frozen=True)
class SessionFixture:
    session_id: str
    truth_error_pct: float
    observations: tuple[Observation, ...]


@dataclass(frozen=True)
class FrozenPolicy:
    max_half_width_pct: float
    min_frames: int = MIN_ADAPTIVE_FRAMES
    decision_margin_pct: float = DECISION_MARGIN_PCT
    trained_on: tuple[str, ...] = ()


@dataclass(frozen=True)
class Decision:
    frames_processed: int
    elapsed_ms: int
    estimate_pct: float
    half_width_pct: float
    visits_seen: int


@dataclass(frozen=True)
class Metrics:
    sessions: int
    decided: int
    coverage: float
    mean_frames: float
    mean_time_ms: float
    medae_pct: float
    p90_ae_pct: float
    false_action_rate: float
    interval_coverage: float
    work_count: int
    timeouts_ignored: int


def _valid(observations: Iterable[Observation]) -> list[Observation]:
    return [
        item for item in observations
        if item.status != HARNESS_TIMEOUT and item.error_pct is not None
    ]


def prefix_moments(session: SessionFixture) -> tuple[tuple[int, float, float, int, int], ...]:
    """Return (n, mean, 95%-half-width, visits, elapsed) for every valid prefix."""
    values = _valid(session.observations)
    total = 0.0
    total_sq = 0.0
    visits: set[str] = set()
    rows: list[tuple[int, float, float, int, int]] = []
    for index, item in enumerate(values, start=1):
        value = float(item.error_pct)
        total += value
        total_sq += value * value
        visits.add(item.visit_id)
        mean = total / index
        if index < 2:
            half_width = float("inf")
        else:
            variance = max(0.0, (total_sq - total * total / index) / (index - 1))
            half_width = 1.96 * sqrt(variance / index)
        rows.append((index, mean, half_width, len(visits), item.elapsed_ms))
    return tuple(rows)


def adaptive_decision(session: SessionFixture, policy: FrozenPolicy) -> Decision | None:
    for n, mean, half_width, visits, elapsed_ms in prefix_moments(session):
        sign_is_resolved = abs(mean) > half_width + policy.decision_margin_pct
        if n >= policy.min_frames and half_width <= policy.max_half_width_pct and sign_is_resolved:
            return Decision(n, elapsed_ms, mean, half_width, visits)
    return None


def fixed_decision(session: SessionFixture) -> Decision | None:
    for n, mean, half_width, visits, elapsed_ms in prefix_moments(session):
        if n >= FIXED_FRAMES and visits >= FIXED_VISITS:
            return Decision(n, elapsed_ms, mean, half_width, visits)
    return None


def evaluate(sessions: Iterable[SessionFixture], policy: FrozenPolicy | None) -> Metrics:
    fixtures = tuple(sessions)
    decisions: list[tuple[SessionFixture, Decision]] = []
    timeouts_ignored = 0
    for session in fixtures:
        timeouts_ignored += sum(1 for item in session.observations if item.status == HARNESS_TIMEOUT)
        decision = adaptive_decision(session, policy) if policy is not None else fixed_decision(session)
        if decision is not None:
            decisions.append((session, decision))
    if not decisions:
        return Metrics(len(fixtures), 0, 0.0, float("inf"), float("inf"), float("inf"), float("inf"), 1.0, 0.0, 0, timeouts_ignored)

    absolute_errors = sorted(abs(decision.estimate_pct - session.truth_error_pct) for session, decision in decisions)
    false_actions = sum(
        1 for session, decision in decisions
        if (decision.estimate_pct > 0.0) != (session.truth_error_pct > 0.0)
    )
    covered = sum(
        1 for session, decision in decisions
        if decision.estimate_pct - decision.half_width_pct <= session.truth_error_pct <= decision.estimate_pct + decision.half_width_pct
    )
    p90_index = max(0, ceil(0.90 * len(absolute_errors)) - 1)
    return Metrics(
        sessions=len(fixtures),
        decided=len(decisions),
        coverage=len(decisions) / len(fixtures),
        mean_frames=sum(decision.frames_processed for _, decision in decisions) / len(decisions),
        mean_time_ms=sum(decision.elapsed_ms for _, decision in decisions) / len(decisions),
        medae_pct=median(absolute_errors),
        p90_ae_pct=absolute_errors[p90_index],
        false_action_rate=false_actions / len(decisions),
        interval_coverage=covered / len(decisions),
        work_count=sum(decision.frames_processed for _, decision in decisions),
        timeouts_ignored=timeouts_ignored,
    )


def train_policy(training: Iterable[SessionFixture]) -> FrozenPolicy:
    train = tuple(training)
    best: tuple[float, float] | None = None
    for uncertainty in UNCERTAINTY_SWEEP_PCT:
        candidate = FrozenPolicy(uncertainty, trained_on=tuple(item.session_id for item in train))
        metrics = evaluate(train, candidate)
        # Training-only objective: favor fewer frames, but make wrong direction,
        # abstention and poor interval coverage expensive enough to dominate speed.
        score = (
            metrics.mean_frames
            + 100.0 * metrics.false_action_rate
            + 25.0 * (1.0 - metrics.coverage)
            + 10.0 * max(0.0, 0.90 - metrics.interval_coverage)
            + metrics.p90_ae_pct
        )
        key = (score, uncertainty)
        if best is None or key < best:
            best = key
    assert best is not None
    return FrozenPolicy(best[1], trained_on=tuple(item.session_id for item in train))


def leave_one_session_out(fixtures: Iterable[SessionFixture]) -> tuple[tuple[FrozenPolicy, SessionFixture, Metrics, Metrics], ...]:
    sessions = tuple(fixtures)
    folds = []
    for index, holdout in enumerate(sessions):
        training = sessions[:index] + sessions[index + 1 :]
        policy = train_policy(training)
        frozen_before = policy
        adaptive = evaluate((holdout,), policy)
        assert policy == frozen_before, "holdout must never mutate the frozen training policy"
        baseline = evaluate((holdout,), None)
        folds.append((policy, holdout, adaptive, baseline))
    return tuple(folds)


def rolling_holdout(fixtures: Iterable[SessionFixture], minimum_training: int = 4) -> tuple[tuple[FrozenPolicy, SessionFixture, Metrics, Metrics], ...]:
    sessions = tuple(fixtures)
    folds = []
    for index in range(minimum_training, len(sessions)):
        training = sessions[:index]
        holdout = sessions[index]
        policy = train_policy(training)
        frozen_before = policy
        adaptive = evaluate((holdout,), policy)
        assert policy == frozen_before
        baseline = evaluate((holdout,), None)
        folds.append((policy, holdout, adaptive, baseline))
    return tuple(folds)


def synthetic_sessions() -> tuple[SessionFixture, ...]:
    """Synthetic-only fixtures with stable, mild, noisy and near-zero regimes."""
    def make(session_id: str, truth: float, noise: tuple[float, ...], timeout_at: int | None = None) -> SessionFixture:
        rows: list[Observation] = []
        for index, delta in enumerate(noise):
            if timeout_at is not None and index == timeout_at:
                rows.append(Observation(None, f"v{1 + index // 2}", (index + 1) * 200, HARNESS_TIMEOUT))
            rows.append(Observation(truth + delta, f"v{1 + index // 2}", (index + 1) * 200))
        return SessionFixture(session_id, truth, tuple(rows))

    return (
        make("stable-pos", 4.0, (0.10, -0.10, 0.05, -0.05, 0.08, -0.08, 0.04, -0.04, 0.02, -0.02)),
        make("stable-neg", -3.0, (0.12, -0.08, 0.06, -0.04, 0.03, -0.03, 0.02, -0.02, 0.01, -0.01)),
        make("mild-pos", 2.5, (0.40, -0.30, 0.20, -0.20, 0.15, -0.10, 0.10, -0.05, 0.04, -0.02)),
        make("mild-neg", -2.2, (0.35, -0.25, 0.20, -0.15, 0.10, -0.08, 0.06, -0.04, 0.02, -0.01), timeout_at=2),
        make("noisy-pos", 3.5, (1.20, -0.90, 0.70, -0.60, 0.50, -0.40, 0.35, -0.30, 0.25, -0.20, 0.15, -0.10)),
        make("noisy-neg", -4.0, (1.10, -1.00, 0.80, -0.70, 0.60, -0.50, 0.40, -0.35, 0.30, -0.25, 0.20, -0.15)),
        make("near-zero-pos", 0.9, (0.30, -0.20, 0.15, -0.10, 0.08, -0.06, 0.05, -0.04, 0.03, -0.02)),
        make("strong-pos", 6.0, (0.20, 0.10, -0.10, 0.05, -0.05, 0.04, -0.04, 0.03, -0.03, 0.02)),
    )
