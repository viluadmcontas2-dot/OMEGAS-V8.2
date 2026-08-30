import math
import unittest

from lab.red_blend.ood_falsification import assess_transfer_ood


class OodFalsificationTest(unittest.TestCase):
    def _training(self):
        return [
            {
                "fuel": "GASOLINA",
                "order": 1,
                "session_key": "s1",
                "start_ms": 10,
                "rpm": 1000.0,
                "map_bar": 0.40,
                "petrol_ms": 4.0,
                "window_count": 20,
            },
            {
                "fuel": "GASOLINA",
                "order": 2,
                "session_key": "s2",
                "start_ms": 20,
                "rpm": 1040.0,
                "map_bar": 0.41,
                "petrol_ms": 4.1,
                "window_count": 15,
            },
            {
                "fuel": "GASOLINA",
                "order": 3,
                "session_key": "s3",
                "start_ms": 30,
                "rpm": 980.0,
                "map_bar": 0.39,
                "petrol_ms": 3.9,
                "window_count": 10,
            },
        ]

    def _target(self, rpm=1010.0, map_bar=0.405):
        return {
            "fuel": "GASOLINA",
            "order": 4,
            "session_key": "holdout",
            "start_ms": 40,
            "rpm": rpm,
            "map_bar": map_bar,
            "petrol_ms": 4.05,
            "window_count": 1,
        }

    def test_near_supported_case_is_not_falsely_marked_ood(self):
        result = assess_transfer_ood(self._training(), self._target())
        self.assertEqual("IN_DISTRIBUTION_SUPPORTED", result.status)
        self.assertTrue(result.transfer_supported)
        self.assertIsNotNone(result.prediction)
        self.assertFalse(result.actionable)
        self.assertIsNone(result.p_improve)

    def test_target_outside_existing_neighbor_support_abstains(self):
        result = assess_transfer_ood(self._training(), self._target(rpm=2200.0, map_bar=0.80))
        self.assertEqual("ABSTAIN_OUTSIDE_PROVEN_NEIGHBOR_SUPPORT", result.status)
        self.assertFalse(result.transfer_supported)
        self.assertIsNone(result.prediction)

    def test_invalid_telemetry_fails_closed(self):
        result = assess_transfer_ood(self._training(), self._target(rpm=math.nan))
        self.assertEqual("ABSTAIN_INVALID_TELEMETRY", result.status)
        self.assertFalse(result.transfer_supported)

    def test_multimodal_or_ambiguous_local_regime_blocks_transfer_not_local_learning(self):
        result = assess_transfer_ood(
            self._training(),
            self._target(),
            local_regime_status="MULTIMODAL",
        )
        self.assertEqual("ABSTAIN_UNRESOLVED_LOCAL_REGIME", result.status)
        self.assertFalse(result.transfer_supported)
        self.assertEqual("LOCAL_EVIDENCE_RETAINED", result.local_evidence_policy)

    def test_calibration_epoch_drift_blocks_transfer(self):
        result = assess_transfer_ood(
            self._training(),
            self._target(),
            calibration_epoch_compatible=False,
        )
        self.assertEqual("ABSTAIN_CALIBRATION_EPOCH_DRIFT", result.status)
        self.assertFalse(result.transfer_supported)

    def test_sparse_independent_returns_block_transfer_even_when_local_neighbor_exists(self):
        result = assess_transfer_ood(
            self._training(),
            self._target(),
            independent_status="INSUFFICIENT_INDEPENDENT_SESSIONS",
        )
        self.assertEqual("ABSTAIN_INSUFFICIENT_TRANSFER_SUPPORT", result.status)
        self.assertFalse(result.transfer_supported)
        self.assertEqual("LOCAL_EVIDENCE_RETAINED", result.local_evidence_policy)


if __name__ == "__main__":
    unittest.main()
