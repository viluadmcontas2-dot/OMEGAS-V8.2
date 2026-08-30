import unittest

from lab.red_blend.causal_map_k import (
    CausalEnvelopeError,
    evaluate_confirmed_adjustment,
    normalize_confirmed_adjustments,
)


def _event(*, adjustment_id="adj-1", row=2, column=3, before=100, after=104,
           readback=104, confirmed=True, finalized=True, final_map_hash="map-hash-a",
           timestamp_ms=1_000):
    return {
        "adjustmentId": adjustment_id,
        "row": row,
        "column": column,
        "before": before,
        "after": after,
        "readback": readback,
        "confirmed": confirmed,
        "batchFinalized": finalized,
        "finalMapHash": final_map_hash,
        "timestampMs": timestamp_ms,
    }


class CausalMapKContractTest(unittest.TestCase):
    def test_normalizer_requires_full_ack_readback_proof_envelope(self):
        broken = _event(readback=103)
        with self.assertRaises(CausalEnvelopeError):
            normalize_confirmed_adjustments([broken])

    def test_normalizer_groups_cells_by_manual_adjustment_identity(self):
        events = [
            _event(row=2, column=3),
            _event(row=2, column=4, before=101, after=105, readback=105),
            _event(
                adjustment_id="adj-2",
                row=7,
                column=8,
                before=96,
                after=94,
                readback=94,
                final_map_hash="map-hash-b",
                timestamp_ms=2_000,
            ),
        ]
        grouped = normalize_confirmed_adjustments(events)
        self.assertEqual(2, len(grouped))
        self.assertEqual([2, 1], [len(item.cells) for item in grouped])
        self.assertEqual({"map-hash-a", "map-hash-b"}, {item.final_map_hash for item in grouped})
        self.assertTrue(all(item.adjustment_key for item in grouped))

    def test_adjustment_abstains_when_pre_post_support_is_not_comparable(self):
        adjustment = normalize_confirmed_adjustments([_event()])[0]
        result = evaluate_confirmed_adjustment(
            adjustment,
            pre_abs_relative_errors=[0.10, 0.09, 0.11],
            post_abs_relative_errors=[0.05, 0.04, 0.06],
            pre_context_keys={"r5m10", "r5m11"},
            post_context_keys={"r5m12", "r5m13"},
            min_observations=3,
            min_context_overlap=0.5,
        )
        self.assertEqual("ABSTAIN_INCOMPARABLE_CONTEXT", result.status)
        self.assertIsNone(result.effect_relative)
        self.assertIsNone(result.p_improve)

    def test_adjustment_estimates_direction_only_on_frozen_comparable_support(self):
        adjustment = normalize_confirmed_adjustments([_event()])[0]
        result = evaluate_confirmed_adjustment(
            adjustment,
            pre_abs_relative_errors=[0.10, 0.09, 0.11, 0.10],
            post_abs_relative_errors=[0.05, 0.04, 0.06, 0.05],
            pre_context_keys={"r5m10", "r5m11", "r6m10"},
            post_context_keys={"r5m10", "r5m11", "r6m10"},
            min_observations=3,
            min_context_overlap=0.5,
        )
        self.assertEqual("CAUSAL_DIRECTION_ESTIMATED_OFFLINE", result.status)
        self.assertLess(result.effect_relative, 0.0)
        self.assertEqual("IMPROVED", result.direction)
        self.assertIsNone(result.p_improve)
        self.assertFalse(result.actionable)


if __name__ == "__main__":
    unittest.main()
