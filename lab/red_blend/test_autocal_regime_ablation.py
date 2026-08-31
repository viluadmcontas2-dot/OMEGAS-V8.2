from pathlib import Path
import unittest

from lab.red_blend.autocal_regime_ablation import audit_autocal_regime_readiness


CHECKPOINT = Path("evidence/red_blend/full_corpus/f3-f5-checkpoint.json")


class AutoCalRegimeAblationTest(unittest.TestCase):
    def test_real_checkpoint_preserves_dimensions_and_defers_regime_ablation(self):
        audit = audit_autocal_regime_readiness(CHECKPOINT)

        self.assertEqual("DEFER_NO_TEMPORALLY_COHERENT_18_ZONE_SUPPORT", audit.status)
        self.assertEqual(18, audit.acquisition_zone_count)
        self.assertEqual(30, audit.curve_point_count)
        self.assertEqual(12, audit.map_rows)
        self.assertEqual(12, audit.map_columns)
        self.assertEqual(144, audit.map_cells)
        self.assertEqual(12, audit.snapshot_count)
        self.assertEqual(12, audit.partial_snapshot_count)
        self.assertEqual(0, audit.temporal_coherent_snapshot_count)
        self.assertEqual(0, audit.telemetry_aligned_zone_snapshots)
        self.assertEqual(
            (
                "GAS_MNFLD_PRESS_RV",
                "MUL_ACT",
                "PETR_INJ_TBP",
                "PETR_MNFLD_PRESS_RV",
            ),
            audit.thirty_point_fields,
        )
        self.assertEqual("UNKNOWN_PENDING_PROTOCOL_PROOF", audit.thirty_point_field_status)
        self.assertFalse(audit.corruption_claim_allowed)
        self.assertFalse(audit.promotion_allowed)
        self.assertFalse(audit.android_changed)


if __name__ == "__main__":
    unittest.main()
