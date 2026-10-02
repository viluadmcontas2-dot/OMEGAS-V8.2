"""Benchmarks for the deterministic adaptive fast-learning harness (G3A / 102A)."""
import adaptive_fast_learning_harness as harness

SESSIONS = harness.synthetic_sessions()
POLICY = harness.train_policy(SESSIONS)


def test_prefix_moments(benchmark):
    result = benchmark(lambda: [harness.prefix_moments(session) for session in SESSIONS])
    assert len(result) == len(SESSIONS)


def test_evaluate_adaptive_policy(benchmark):
    metrics = benchmark(harness.evaluate, SESSIONS, POLICY)
    assert metrics.sessions == len(SESSIONS)


def test_evaluate_fixed_baseline(benchmark):
    metrics = benchmark(harness.evaluate, SESSIONS, None)
    assert metrics.sessions == len(SESSIONS)


def test_train_policy(benchmark):
    policy = benchmark(harness.train_policy, SESSIONS)
    assert policy.max_half_width_pct in harness.UNCERTAINTY_SWEEP_PCT


def test_leave_one_session_out(benchmark):
    folds = benchmark(harness.leave_one_session_out, SESSIONS)
    assert len(folds) == len(SESSIONS)


def test_rolling_holdout(benchmark):
    folds = benchmark(harness.rolling_holdout, SESSIONS)
    assert len(folds) == len(SESSIONS) - 4
