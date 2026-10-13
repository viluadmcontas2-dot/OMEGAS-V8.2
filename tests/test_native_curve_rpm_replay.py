import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools" / "autocal_refine"))

import native_curve_rpm_replay as replay


def field(key, values):
    return {"key": key, "status": "VALID", "physicalValues": values}


def snapshot(t, petrol_counts, petrol_ms, petrol_map, maturity=None):
    return {
        "recordedAtMs": t,
        "type": "autocal_native_snapshot",
        "data": {
            "fields": [
                field("NUM_BUF_UPD_PETR", petrol_counts),
                field("PETR_INJ_TBUF", petrol_ms),
                field("MNFLD_PRESS_BUF", petrol_map),
                field("NUM_BUF_UPD_GAS", [0, 0]),
                field("PETR_INJ_TBUF_GAS", [0, 0]),
                field("MNFLD_PRESS_BUF_GAS", [0, 0]),
            ],
            "nativeMaturityEvents": maturity or [],
        },
    }


class NativeCurveRpmReplayTest(unittest.TestCase):
    def test_extracts_only_valid_snapshot_fields(self):
        data = snapshot(100, [1, 0], [3.0, 0], [0.4, 0])["data"]
        values = replay.snapshot_fields(data)
        self.assertEqual([1, 0], values["NUM_BUF_UPD_PETR"])

    def test_attributes_changed_band_only_to_prior_matching_telemetry(self):
        events = [
            snapshot(100, [1, 0], [3.0, 0], [0.4, 0]),
            {"recordedAtMs": 150, "type": "telemetry", "data": {
                "fuel": "GASOLINA", "rpm": 900, "load_bar": 0.41,
                "petrol_ms": 3.1, "plausible": True,
            }},
            {"recordedAtMs": 250, "type": "telemetry", "data": {
                "fuel": "GASOLINA", "rpm": 2200, "load_bar": 0.60,
                "petrol_ms": 3.1, "plausible": True,
            }},
            snapshot(300, [2, 0], [3.1, 0], [0.41, 0]),
        ]
        rows = replay.reconstruct(events, "s")
        self.assertEqual(1, len(rows))
        self.assertEqual(900, rows[0].rpm)
        self.assertEqual("INFERRED_INTERVAL_MATCH", rows[0].source)

    def test_does_not_attribute_without_matching_frame(self):
        events = [
            snapshot(100, [1, 0], [3.0, 0], [0.4, 0]),
            {"recordedAtMs": 150, "type": "telemetry", "data": {
                "fuel": "GNV", "rpm": 900, "load_bar": 0.4,
                "petrol_ms": 3.0, "plausible": True,
            }},
            snapshot(300, [2, 0], [3.1, 0], [0.41, 0]),
        ]
        self.assertEqual([], replay.reconstruct(events, "s"))

    def test_explicit_native_correlation_wins_and_keeps_epoch(self):
        event = {
            "fuel": "GNV", "bandIndex": 1, "previousCounter": 2, "counter": 3,
            "timeMs": 3.2, "mapBar": 0.42, "rpm": 1800,
            "correlationConfidence": 0.8, "matchedTelemetryFrames": 5,
        }
        events = [
            {"recordedAtMs": 50, "type": "autocal_native_calibration_epoch", "data": {}},
            snapshot(100, [0, 0], [0, 0], [0, 0], [event]),
        ]
        rows = replay.reconstruct(events, "s")
        self.assertEqual(1, len(rows))
        self.assertEqual("NATIVE_CORRELATION", rows[0].source)
        self.assertEqual(1, rows[0].epoch)
        self.assertAlmostEqual(0.8, rows[0].confidence)


if __name__ == "__main__":
    unittest.main()
