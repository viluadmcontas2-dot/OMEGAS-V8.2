from pathlib import Path
import unittest

from lab.red_blend.causal_science import (
    Outcome,
    evaluate_adjustment,
    group_adjustments,
    load_adjustment_fixture,
    normalize_confirmed_event,
)

FIXTURE = Path("tests/fixtures/science/k_history/confirmed_map_k_20260818.json")


def event(**overrides):
    base = {
        "timestamp_ms": 1000,
        "adjustment_key": "0123456789abcdef",
        "row": 4,
        "column": 4,
        "petrol_ms": 4.5,
        "rpm": 3000.0,
        "before": 142,
        "after": 149,
        "readback": 149,
        "confirmed": True,
        "batch_finalized": True,
        "final_map_hash": "a" * 64,
    }
    base.update(overrides)
    return base


def outcome(timestamp_ms, rpm, map_bar, gasoline_reference_ms, cng_petrol_ms):
    return Outcome(
        timestamp_ms=timestamp_ms,
        rpm=float(rpm),
        map_bar=float(map_bar),
        gasoline_reference_ms=float(gasoline_reference_ms),
        cng_petrol_ms=float(cng_petrol_ms),
    )


class CausalMapKScienceTest(unittest.TestCase):
    def test_governed_fixture_has_133_confirmed_cells_but_only_11_interventions(self):
        fixture = load_adjustment_fixture(FIXTURE)
        self.assertEqual(fixture.cell_event_count, 133)
        self.assertEqual(len(fixture.adjustments), 11)
        self.assertEqual(sum(a.cell_count for a in fixture.adjustments), 133)
        self.assertTrue(all(len(a.adjustment_key) == 16 for a in fixture.adjustments))
        self.assertTrue(all(len(a.proof_envelope_sha256) == 64 for a in fixture.adjustments))

    def test_normalizer_rejects_incomplete_or_unconfirmed_proof_envelope(self):
        bad = event()
        bad.pop("readback")
        with self.assertRaises(ValueError):
            normalize_confirmed_event(bad)
        with self.assertRaises(ValueError):
            normalize_confirmed_event(event(readback=148))
        with self.assertRaises(ValueError):
            normalize_confirmed_event(event(confirmed=False))
        with self.assertRaises(ValueError):
            normalize_confirmed_event(event(batch_finalized=False))
        with self.assertRaises(ValueError):
            normalize_confirmed_event(event(final_map_hash=""))
        with self.assertRaises(ValueError):
            normalize_confirmed_event(event(row=12))

    def test_cells_in_same_adjustment_are_one_independent_intervention(self):
        events = tuple(
            normalize_confirmed_event(event(timestamp_ms=1000 + i, row=i, column=3))
            for i in range(8)
        )
        adjustments = group_adjustments(events)
        self.assertEqual(len(adjustments), 1)
        self.assertEqual(adjustments[0].cell_count, 8)
        self.assertEqual(adjustments[0].started_at_ms, 1000)
        self.assertEqual(adjustments[0].ended_at_ms, 1007)

    def test_abstains_when_pre_and_post_support_are_not_physically_comparable(self):
        adjustment = group_adjustments((normalize_confirmed_event(event()),))[0]
        pre = [outcome(900, 1800, 0.40, 4.0, 4.4)]
        post = [outcome(1100, 3600, 0.90, 8.0, 8.1)]
        result = evaluate_adjustment(pre, post, adjustment)
        self.assertEqual(result.status, "ABSTAIN_INSUFFICIENT_COMPARABLE_PRE_POST")
        self.assertIsNone(result.effect_abs_error_delta)

    def test_synthetic_improvement_is_negative_change_in_absolute_equivalence_error(self):
        adjustment = group_adjustments((normalize_confirmed_event(event()),))[0]
        pre = [
            outcome(900, 3000, 0.55, 5.0, 5.60),
            outcome(950, 3010, 0.551, 5.0, 5.50),
        ]
        post = [
            outcome(1100, 2995, 0.552, 5.0, 5.20),
            outcome(1150, 3005, 0.550, 5.0, 5.15),
        ]
        result = evaluate_adjustment(pre, post, adjustment)
        self.assertEqual(result.status, "COMPARABLE_EFFECT_ESTIMATE")
        self.assertLess(result.effect_abs_error_delta, 0.0)

    def test_synthetic_worsening_is_positive_change_in_absolute_equivalence_error(self):
        adjustment = group_adjustments((normalize_confirmed_event(event()),))[0]
        pre = [
            outcome(900, 3000, 0.55, 5.0, 5.15),
            outcome(950, 3010, 0.551, 5.0, 5.20),
        ]
        post = [
            outcome(1100, 2995, 0.552, 5.0, 5.50),
            outcome(1150, 3005, 0.550, 5.0, 5.60),
        ]
        result = evaluate_adjustment(pre, post, adjustment)
        self.assertEqual(result.status, "COMPARABLE_EFFECT_ESTIMATE")
        self.assertGreater(result.effect_abs_error_delta, 0.0)


if __name__ == "__main__":
    unittest.main()
