from statistics import mean

from adaptive_fast_learning_harness import (
    HARNESS_MODE,
    HARNESS_TIMEOUT,
    UNCERTAINTY_SWEEP_PCT,
    leave_one_session_out,
    rolling_holdout,
    synthetic_sessions,
)

# This is a synthetic falsification gate, not a physical calibration claim.
# The permitted P90 delta is an acceptance tolerance for this test corpus only;
# it is not a production ECU/scientific constant.
MAX_SYNTHETIC_P90_REGRESSION_PCT = 0.06
MIN_FRAME_REDUCTION_FRACTION = 0.25
MIN_INTERVAL_COVERAGE = 0.90


def _aggregate(folds):
    adaptive = [item[2] for item in folds]
    baseline = [item[3] for item in folds]
    return {
        "adaptive_frames": mean(item.mean_frames for item in adaptive),
        "baseline_frames": mean(item.mean_frames for item in baseline),
        "adaptive_time_ms": mean(item.mean_time_ms for item in adaptive),
        "baseline_time_ms": mean(item.mean_time_ms for item in baseline),
        "adaptive_false_action": mean(item.false_action_rate for item in adaptive),
        "baseline_false_action": mean(item.false_action_rate for item in baseline),
        "adaptive_interval_coverage": mean(item.interval_coverage for item in adaptive),
        "baseline_interval_coverage": mean(item.interval_coverage for item in baseline),
        "adaptive_coverage": mean(item.coverage for item in adaptive),
        "baseline_coverage": mean(item.coverage for item in baseline),
        "adaptive_p90": max(item.p90_ae_pct for item in adaptive),
        "baseline_p90": max(item.p90_ae_pct for item in baseline),
        "adaptive_work": sum(item.work_count for item in adaptive),
        "baseline_work": sum(item.work_count for item in baseline),
        "timeouts_ignored": sum(item.timeouts_ignored for item in adaptive),
    }


def _assert_fast_without_material_risk_regression(result):
    assert result["adaptive_coverage"] >= result["baseline_coverage"]
    assert result["adaptive_false_action"] <= result["baseline_false_action"]
    assert result["adaptive_interval_coverage"] >= MIN_INTERVAL_COVERAGE
    assert result["adaptive_interval_coverage"] >= result["baseline_interval_coverage"] - 0.05
    assert result["adaptive_p90"] <= result["baseline_p90"] + MAX_SYNTHETIC_P90_REGRESSION_PCT
    assert result["adaptive_frames"] <= result["baseline_frames"] * (1.0 - MIN_FRAME_REDUCTION_FRACTION)
    assert result["adaptive_time_ms"] < result["baseline_time_ms"]
    assert result["adaptive_work"] < result["baseline_work"]


def test_102a_leave_one_session_out_freezes_train_policy_before_each_holdout():
    fixtures = synthetic_sessions()
    folds = leave_one_session_out(fixtures)
    assert len(folds) == len(fixtures)
    for policy, holdout, adaptive, baseline in folds:
        assert holdout.session_id not in policy.trained_on
        assert policy.max_half_width_pct in UNCERTAINTY_SWEEP_PCT
        assert adaptive.decided == 1
        assert baseline.decided == 1
    _assert_fast_without_material_risk_regression(_aggregate(folds))


def test_102a_rolling_holdout_repeats_the_same_frozen_policy_rule():
    folds = rolling_holdout(synthetic_sessions(), minimum_training=4)
    assert folds
    for policy, holdout, adaptive, baseline in folds:
        assert holdout.session_id not in policy.trained_on
        assert adaptive.decided == baseline.decided == 1
    _assert_fast_without_material_risk_regression(_aggregate(folds))


def test_102a_harness_timeout_is_not_counted_as_scientific_data():
    fixtures = synthetic_sessions()
    assert any(
        item.status == HARNESS_TIMEOUT
        for fixture in fixtures
        for item in fixture.observations
    )
    folds = leave_one_session_out(fixtures)
    result = _aggregate(folds)
    assert result["timeouts_ignored"] >= 1
    # A timeout is tracked as a harness event only; it cannot increase work/data count.
    valid_observations = sum(
        1
        for fixture in fixtures
        for item in fixture.observations
        if item.status != HARNESS_TIMEOUT and item.error_pct is not None
    )
    assert result["adaptive_work"] <= valid_observations


def test_102a_harness_is_reproducible_and_prefix_vectorized():
    assert HARNESS_MODE == "PREFIX_VECTORIZED_DETERMINISTIC_SYNTHETIC"
    first = leave_one_session_out(synthetic_sessions())
    second = leave_one_session_out(synthetic_sessions())
    assert first == second
