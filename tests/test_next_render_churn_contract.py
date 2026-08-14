#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROUTES = ROOT / "app/src/main/assets/ui-next/routes"


class NextRenderChurnContractTest(unittest.TestCase):
    def test_heavy_routes_memoize_their_own_state_references(self):
        expected = {
            "aprender.js": ["lastCellContext", "lastMapState", "lastSuggestions", "lastEditorState"],
            "mapa-k.js": ["lastMapState", "lastCellContext"],
            "predictor.js": ["lastPredictorState"],
            "curva-k.js": ["lastCurveState", "lastAutoCalState"],
            "obd.js": ["lastObdState"],
        }
        for filename, tokens in expected.items():
            source = (ROUTES / filename).read_text(encoding="utf-8")
            for token in tokens:
                self.assertIn(token, source, filename)
            self.assertIn("return;", source, filename)

    def test_only_agora_reads_fast_telemetry_for_primary_route_render(self):
        agora = (ROUTES / "agora.js").read_text(encoding="utf-8")
        self.assertIn("state.telemetry", agora)
        for filename in ["predictor.js", "curva-k.js", "obd.js"]:
            source = (ROUTES / filename).read_text(encoding="utf-8")
            self.assertNotIn("state.telemetry", source, filename)

    def test_map_tracing_is_not_part_of_grid_rerender_path(self):
        mapa = (ROUTES / "mapa-k.js").read_text(encoding="utf-8")
        self.assertNotIn("liveTrace", mapa)
        trace = (ROOT / "app/src/main/assets/ui-next/live-tracing.js").read_text(encoding="utf-8")
        self.assertIn("updateMapKLiveTrace", trace)


if __name__ == "__main__":
    unittest.main()
