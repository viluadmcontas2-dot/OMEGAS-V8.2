#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
ROUTE = NEXT / "routes/predictor.js"
SIM = NEXT / "adapters/simulated-predictor.js"
BOOT = NEXT / "bootstrap.js"
ADAPTER_INDEX = NEXT / "adapters/index.js"
KOTLIN = ROOT / "app/src/main/java/com/omegas/prohub/learning/PredictorInterpolator.kt"
CONFIDENCE = ROOT / "app/src/main/java/com/omegas/prohub/learning/PredictorSpatialConfidence.kt"


class NextPredictorContractTest(unittest.TestCase):
    def test_science_remains_kotlin_owned(self):
        route = ROUTE.read_text(encoding="utf-8")
        for token in ["physicalDistance", "convexHull", "estimateTargetK", "targetK =", "confidence ="]:
            self.assertNotIn(token, route)
        self.assertIn("PredictorInterpolator", KOTLIN.read_text(encoding="utf-8"))

    def test_prediction_never_feeds_its_own_confidence(self):
        kotlin = KOTLIN.read_text(encoding="utf-8")
        self.assertIn("supportFrozenBeforePrediction", kotlin)
        self.assertIn("predictionsFeedConfidence", kotlin)
        self.assertIn("if (cell.optBoolean(\"predicted\", false)) return@repeat", kotlin)

    def test_confidence_uses_physical_geometry_and_independent_trajectories(self):
        source = CONFIDENCE.read_text(encoding="utf-8")
        for token in ["KMapPhysicalAxes", "convexHull", "EXTRAPOLATION_OUTSIDE_SUPPORT_HULL", "INSUFFICIENT_TRAJECTORY_INDEPENDENCE"]:
            self.assertIn(token, source)

    def test_predictor_route_has_no_writer(self):
        route = ROUTE.read_text(encoding="utf-8")
        for token in ["OmegasNative", "protocolTransaction", "startBatchWrite", "writeK"]:
            self.assertNotIn(token, route)
        self.assertIn("Revisar no Mapa K", route)

    def test_unknown_cell_stays_without_target_in_simulator(self):
        sim = SIM.read_text(encoding="utf-8")
        self.assertIn("state === 'DESCONHECIDO' ? null", sim)
        self.assertIn("automaticWrite: false", sim)
        self.assertIn("extrapolationAllowed: false", sim)

    def test_bootstrap_uses_one_product_adapter_not_predictor_specific_transport(self):
        boot = BOOT.read_text(encoding="utf-8")
        index = ADAPTER_INDEX.read_text(encoding="utf-8")
        self.assertIn("nextAdapter.predictorSnapshot()", boot)
        self.assertIn("createNextAdapter", index)
        self.assertNotIn("simulatedPredictorAdapter", boot)
        self.assertNotIn("OmegasV7", route)
        self.assertIn("PREDICTOR_STATE", boot)
        self.assertNotIn("setInterval(", boot)


if __name__ == "__main__":
    unittest.main()
