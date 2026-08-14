#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "app/src/main/assets/ui-next"
ROUTE = NEXT / "routes/obd.js"
BOOT = NEXT / "bootstrap.js"
SIM = NEXT / "adapters/simulated-obd.js"
PROJECTION = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdUiProjection.kt"
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"


class NextObdContractTest(unittest.TestCase):
    def test_obd_projection_has_explicit_human_states(self):
        source = PROJECTION.read_text(encoding="utf-8")
        for state in ["OFF", "CONECTANDO", "VALIDO", "STALE", "SEM_PID", "ERRO"]:
            self.assertIn(state, source)
        self.assertIn("FRESH_MAX_MS = 5_000L", source)

    def test_zero_null_and_stale_are_distinct_in_native_contract(self):
        source = PROJECTION.read_text(encoding="utf-8")
        self.assertIn("hasNumber", source)
        self.assertIn("freshNumber", source)
        self.assertIn("JSONObject.NULL", source)

    def test_obd_is_observational_and_has_no_calibration_authority(self):
        projection = PROJECTION.read_text(encoding="utf-8")
        for token in ['put("observationalOnly", true)', 'put("ecuAuthority", false)', 'put("learningAuthority", false)', 'put("automaticCalibration", false)']:
            self.assertIn(token, projection)
        route = ROUTE.read_text(encoding="utf-8")
        self.assertIn("OBD informa; não decide", route)
        for token in ["startBatchWrite", "writeK", "protocolTransaction", "OmegasNative"]:
            self.assertNotIn(token, route)

    def test_obd_uses_global_low_rate_scheduler_not_route_timer(self):
        boot = BOOT.read_text(encoding="utf-8")
        self.assertIn("scheduler.addHook('obd-witness', loadObd, 1000)", boot)
        self.assertIn("scheduler.addHook('fast-telemetry', pollFastTelemetry, 100)", boot)
        for path in NEXT.rglob("*.js"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("setInterval(", source, str(path))
            self.assertNotIn("setTimeout(", source, str(path))

    def test_three_layers_stay_separate_without_hidden_average(self):
        route = ROUTE.read_text(encoding="utf-8")
        sim = SIM.read_text(encoding="utf-8")
        for label in ["Gasolina", "GNV", "Comparação"]:
            self.assertIn(label, route)
        self.assertIn("mergedValue: null", sim)
        self.assertIn("Não calcula média escondida", route)

    def test_real_manager_does_not_import_mp48_writers(self):
        source = MANAGER.read_text(encoding="utf-8")
        self.assertNotIn("import com.omegas.prohub.calibration.KWriteManager", source)
        self.assertNotIn("import com.omegas.prohub.usb.UsbSerialManager", source)
        self.assertIn("Ambiente OBD totalmente isolado do writer MP48", source)


if __name__ == "__main__":
    unittest.main()
