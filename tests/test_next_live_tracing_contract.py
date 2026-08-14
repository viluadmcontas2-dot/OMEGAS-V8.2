#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
STORE = ROOT / "app/src/main/java/com/omegas/prohub/telemetry/TelemetryStateStore.kt"
GRID = ROOT / "app/src/main/java/com/omegas/prohub/learning/LearningGridProjection.kt"
TRACE = NEXT / "live-tracing.js"
EDITOR = NEXT / "components/map-k-editor.js"
SCHEDULER = NEXT / "core/scheduler.js"


class NextLiveTracingContractTest(unittest.TestCase):
    def test_bilinear_math_stays_in_kotlin(self):
        store = STORE.read_text(encoding="utf-8")
        grid = GRID.read_text(encoding="utf-8")
        self.assertIn("LearningGridProjection.liveInterpolationJson", store)
        self.assertIn("ContinuousLearningMath.bilinearWeights", grid)
        for path in NEXT.rglob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("bilinearWeights", source, str(path))
            self.assertNotIn("trilinearWeights", source, str(path))

    def test_fast_payload_exports_at_most_four_current_weights(self):
        source = STORE.read_text(encoding="utf-8")
        self.assertIn("repeat(minOf(weights.length(), 4))", source)
        self.assertIn('.put("liveTrace", liveTrace)', source)
        self.assertIn('.put("educationalOnly", true)', source)
        self.assertIn('.put("affectsLearning", false)', source)
        self.assertIn('.put("affectsCalibration", false)', source)

    def test_trace_updates_only_small_markers_not_grid_history(self):
        editor = EDITOR.read_text(encoding="utf-8")
        self.assertIn("[0, 1, 2, 3]", editor)
        self.assertIn("updateMapKLiveTrace", editor)
        self.assertIn("slice(0, 4)", editor)
        self.assertNotIn("traceHistory", editor)
        self.assertNotIn("appendTrace", editor)

    def test_visual_toggle_cannot_touch_learning_or_writer(self):
        trace = TRACE.read_text(encoding="utf-8")
        self.assertIn("VISUAL_PREFERENCE_CHANGED", trace)
        self.assertIn("liveTracing", trace)
        for token in ["startBatchWrite", "startKFactorWrite", "protocolTransaction", "OmegasNative", "learningStatus"]:
            self.assertNotIn(token, trace)

    def test_scheduler_reduces_visual_cadence_under_pressure(self):
        trace = TRACE.read_text(encoding="utf-8")
        scheduler = SCHEDULER.read_text(encoding="utf-8")
        self.assertIn("scheduler.pressure() === 'REDUCED'", trace)
        self.assertIn("reduced ? 250 : 100", trace)
        self.assertIn("frameGap > 80", scheduler)
        self.assertIn("pressureScore", scheduler)
        self.assertIn("requestAnimationFrame", scheduler)

    def test_hidden_webview_stops_visual_trace_without_stopping_science(self):
        trace = TRACE.read_text(encoding="utf-8")
        self.assertIn("document.visibilityState === 'visible'", trace)
        self.assertNotIn("TELEMETRY_INVALIDATED", trace)
        self.assertNotIn("LEARNING_UPDATED", trace)


if __name__ == "__main__":
    unittest.main()
