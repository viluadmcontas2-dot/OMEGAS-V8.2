"""Replay determinístico do contrato OBD × MP48 da Fase 2."""

import json
from pathlib import Path
import unittest

from obd_mp48_contract import (
    Comparison, Condition, Epoch, Rejection, Sample, Source, direct_gnv_signal,
    fuel_state_for_obd, gasoline_advisory, is_duplicate, may_open_epoch,
    parallel_comparison, qualify, sensitivity_observation,
)


ROOT = Path(__file__).parent
FIXTURES = json.loads((ROOT / "fixtures" / "obd_mp48_replay.json").read_text())


class ObdMp48ContractTest(unittest.TestCase):
    def test_replay_qualification_fixtures(self):
        for fixture in FIXTURES:
            with self.subTest(fixture=fixture["name"]):
                result = qualify(fixture["sample"])
                self.assertEqual(fixture["accepted"], result.accepted)
                if not result.accepted:
                    self.assertEqual(fixture["rejection"], result.rejection.value)
                if "legacy_coverage" in fixture:
                    self.assertEqual(fixture["legacy_coverage"], result.legacy_coverage)

    def test_rejection_list_is_closed_and_explicit(self):
        self.assertEqual(12, len(Rejection))
        self.assertIn(Rejection.OPEN_LOOP, list(Rejection))

    def test_same_condition_is_not_counted_twice(self):
        condition = {"origin_device_id": "phone-obd", "condition_id": "c-18", "map_epoch_id": "m-4", "curve_epoch_id": "k-2"}
        seen = set()
        self.assertFalse(is_duplicate(condition, seen))
        self.assertTrue(is_duplicate(condition, seen))

    def test_direct_gnv_stft_never_uses_gasoline_as_an_operand(self):
        self.assertEqual({"display_stft": -15.0, "direction": "DECREASE_GNV_FUEL", "label": "TENDENCY_RICH"}, direct_gnv_signal(-15.0))
        self.assertEqual("GASOLINE_BASE_OUT_OF_NEUTRAL", gasoline_advisory(12.0))
        self.assertIsNone(gasoline_advisory(1.0))
        comparison = parallel_comparison(14.0, 12.0)
        self.assertEqual(Comparison(14.0, 12.0, "GASOLINE_BASE_OUT_OF_NEUTRAL"), comparison)
        self.assertFalse(hasattr(comparison, "stft_difference"))

    def test_models_keep_frames_conditions_and_epochs_separate(self):
        frame = Sample(Source.REMOTE, 1000, "GNV", 1850, 1842, 4.5, -15.0)
        condition = Condition("phone-obd", "c-18", "m-4", "k-2", Source.REMOTE, 12)
        epoch = Epoch("m-4", "map-hash", "curve-hash", 1000)
        self.assertEqual(Source.REMOTE, frame.source)
        self.assertEqual(12, condition.sample_count)
        self.assertEqual("map-hash", epoch.map_readback_hash)

    def test_epoch_requires_manual_confirmation_and_readback(self):
        self.assertFalse(may_open_epoch(False, True))
        self.assertFalse(may_open_epoch(True, False))
        self.assertTrue(may_open_epoch(True, True))

    def test_sensitivity_is_observed_not_a_fixed_gain(self):
        self.assertIsNone(sensitivity_observation(-15.0, -15.0, 0))
        self.assertAlmostEqual(8.8, sensitivity_observation(-15.0, -6.2, 1.0))

    def test_manual_fuel_declaration_is_explicit_when_mp48_is_unavailable(self):
        state = fuel_state_for_obd(mp48_present=False, mp48_fuel=None, manual_fuel="GASOLINA")
        self.assertEqual("GASOLINA", state.fuel)
        self.assertEqual("MANUAL_OPERATOR", state.source)
        self.assertFalse(state.can_qualify_map)

    def test_mp48_confirmation_overrides_the_manual_button(self):
        state = fuel_state_for_obd(mp48_present=True, mp48_fuel="GNV", manual_fuel="GASOLINA")
        self.assertEqual("GNV", state.fuel)
        self.assertEqual("MP48_CONFIRMED", state.source)
        self.assertTrue(state.can_qualify_map)


if __name__ == "__main__":
    unittest.main()
