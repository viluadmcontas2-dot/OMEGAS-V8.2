#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OVERLAY = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryOverlayController.kt"


class NextOverlayContractTest(unittest.TestCase):
    def test_visual_presence_is_at_least_two_and_a_half_times_legacy_compact_size(self):
        source = OVERLAY.read_text(encoding="utf-8")
        self.assertIn("VISUAL_SCALE_VS_LEGACY = 2.5", source)
        self.assertIn("COMPACT_SIZE_DP = 105", source)
        self.assertIn("minWidth = dp(COMPACT_SIZE_DP)", source)
        self.assertIn("minHeight = dp(COMPACT_SIZE_DP)", source)

    def test_collapsed_overlay_is_informative_not_just_an_icon(self):
        source = OVERLAY.read_text(encoding="utf-8")
        self.assertIn('compactText?.text = "Ω\\n$rpm rpm\\n$fuel"', source)
        self.assertIn("EXPANDED_MIN_WIDTH_DP = 260", source)
        self.assertIn("METRIC_TEXT_SP = 18", source)
        for field in ["gasText", "cellText", "contextText", "freshnessText", "stftText"]:
            self.assertIn(field, source)

    def test_position_and_expanded_state_are_persisted_and_bounded(self):
        source = OVERLAY.read_text(encoding="utf-8")
        for token in [
            "KEY_X_DP",
            "KEY_Y_DP",
            "KEY_EXPANDED",
            "clampActualPosition",
            "persistPosition",
            'put("positionBounded", true)',
        ]:
            self.assertIn(token, source)
        self.assertIn("coerceIn", source)

    def test_overlay_remains_observational_only(self):
        source = OVERLAY.read_text(encoding="utf-8")
        forbidden = [
            "import com.omegas.prohub.calibration",
            "import com.omegas.prohub.usb",
            "protocolTransaction(",
            "startBatchWrite(",
            "startKWrite(",
            "startKFactorWrite(",
            "kWriter.",
            "kFactor.",
        ]
        for token in forbidden:
            self.assertNotIn(token, source)
        self.assertIn('.put("observationalOnly", true)', source)

    def test_overlay_keeps_bounded_redraw_rate(self):
        source = OVERLAY.read_text(encoding="utf-8")
        self.assertIn("REDRAW_MIN_INTERVAL_MS = 250L", source)
        self.assertIn("now - lastDrawAt < REDRAW_MIN_INTERVAL_MS", source)
        self.assertNotIn("setInterval", source)
        self.assertNotIn("Timer(", source)


if __name__ == "__main__":
    unittest.main()
