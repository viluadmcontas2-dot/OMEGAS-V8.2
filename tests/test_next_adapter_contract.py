#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
CONTRACT = NEXT / "adapters/next-contract.js"
INDEX = NEXT / "adapters/index.js"
NATIVE = NEXT / "adapters/native-next.js"
SIMULATED = NEXT / "adapters/simulated-next.js"
BOOT = NEXT / "bootstrap.js"
ROUTES = NEXT / "routes"


class NextAdapterContractTest(unittest.TestCase):
    def test_contract_is_versioned_and_covers_product_capabilities(self):
        source = CONTRACT.read_text(encoding="utf-8")
        for token in [
            "omegas-next-adapter-v1",
            "omegas-next-fast-v1",
            "omegas-next-map-k-v1",
            "omegas-next-curve-k-v1",
            "omegas-next-obd-v1",
            "omegas-next-suggestions-v1",
            "CAPABILITY",
            "assertNextAdapter",
        ]:
            self.assertIn(token, source)

    def test_environment_selection_exists_in_one_file_only(self):
        index = INDEX.read_text(encoding="utf-8")
        self.assertIn("hasAndroidTransport", index)
        self.assertIn("nativeNextAdapter", index)
        self.assertIn("simulatedNextAdapter", index)
        for path in ROUTES.glob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("OmegasNative", source, str(path))
            self.assertNotIn("OmegasV7", source, str(path))
            self.assertNotIn("SIMULATED", source, str(path))

    def test_bootstrap_consumes_only_one_next_adapter(self):
        source = BOOT.read_text(encoding="utf-8")
        self.assertIn("import { nextAdapter }", source)
        self.assertIn("nextAdapter.fastTelemetry()", source)
        self.assertIn("nextAdapter.readMapK()", source)
        self.assertIn("nextAdapter.readCurveK()", source)
        self.assertIn("nextAdapter.predictorSnapshot()", source)
        self.assertIn("nextAdapter.obdSnapshot()", source)
        self.assertIn("nextAdapter.suggestionsSnapshot()", source)
        for token in [
            "simulatedAdapter",
            "simulatedMapKAdapter",
            "simulatedPredictorAdapter",
            "simulatedCurveAdapter",
            "simulatedObdAdapter",
            "simulatedSuggestionsAdapter",
            "OmegasNative",
            "OmegasV7",
        ]:
            self.assertNotIn(token, source)

    def test_netlify_simulator_is_explicitly_fictional_and_never_writes(self):
        source = SIMULATED.read_text(encoding="utf-8")
        self.assertIn("dataFictional: true", source)
        self.assertIn("source: 'FIXTURES_ONLY'", source)
        self.assertIn("Netlify/simulador nunca grava ECU", source)
        self.assertIn("AUTOCAL_ACTIONS", source)
        self.assertIn("available: false", source)

    def test_native_transport_encapsulates_legacy_bridges_in_one_file(self):
        native = NATIVE.read_text(encoding="utf-8")
        self.assertIn("LEGACY_BRIDGES_ENCAPSULATED", native)
        self.assertIn("OmegasNative", native)
        self.assertIn("OmegasV7", native)
        self.assertIn("MAP_WRITE", native)
        self.assertIn("CURVE_WRITE", native)
        self.assertIn("Writer NEXT permanece fechado", native)

    def test_capability_absence_is_reasoned_not_silent(self):
        native = NATIVE.read_text(encoding="utf-8")
        boot = BOOT.read_text(encoding="utf-8")
        self.assertIn("CELL_SEMANTICS", native)
        self.assertIn("AUTOCAL_STATUS", native)
        self.assertIn("reason:", native)
        self.assertIn("Funções ainda indisponíveis", boot)
        self.assertIn("capabilityReason", boot)

    def test_simulated_and_native_share_required_method_names(self):
        required = [
            "identity()", "capabilities()", "fastTelemetry()", "learningStatus()", "cellContext()",
            "predictorSnapshot()", "readMapK()", "previewMapK(", "readCurveK()", "previewCurveK(",
            "autoCalStatus()", "obdSnapshot()", "suggestionsSnapshot()",
        ]
        for path in [NATIVE, SIMULATED]:
            source = path.read_text(encoding="utf-8")
            for token in required:
                self.assertIn(token, source, f"{token} ausente em {path.name}")


if __name__ == "__main__":
    unittest.main()
