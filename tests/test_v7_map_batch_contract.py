import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLAN = ROOT / "app/src/main/java/com/omegas/prohub/calibration/MapBatchPlan.kt"
POLICY = ROOT / "app/src/main/java/com/omegas/prohub/calibration/CalibrationWriteSafetyPolicy.kt"
CALIBRATION = ROOT / "app/src/main/java/com/omegas/prohub/web/CalibrationOperationsBridge.kt"
HUB = ROOT / "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt"
AUTOCAL_BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt"
AUTOCAL_ACTION = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt"
WRITER = ROOT / "app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt"
MAP_UI = ROOT / "app/src/main/assets/ui/screens/map.js"
MANIFEST = ROOT / "config/omegas-release.json"


class V8MapBatchContract(unittest.TestCase):
    def setUp(self):
        self.plan = PLAN.read_text("utf-8")
        self.policy = POLICY.read_text("utf-8")
        self.calibration = CALIBRATION.read_text("utf-8")
        self.hub = HUB.read_text("utf-8")
        self.autocal_bridge = AUTOCAL_BRIDGE.read_text("utf-8")
        self.autocal_action = AUTOCAL_ACTION.read_text("utf-8")
        self.writer = WRITER.read_text("utf-8")
        self.map_ui = MAP_UI.read_text("utf-8")
        self.manifest = MANIFEST.read_text("utf-8")

    def test_user_intent_supports_full_writable_grid_as_one_native_batch(self):
        self.assertIn("MAX_USER_CELLS = KMapPhysicalAxes.WRITABLE_ROWS * KMapPhysicalAxes.COLUMNS", self.plan)
        self.assertIn("INTERNAL_CHUNK_CELLS = MAX_USER_CELLS", self.plan)
        self.assertIn("fun startMapBatchWrite", self.calibration)
        self.assertIn("MapBatchPlan.build(cells)", self.calibration)
        self.assertIn("até 144 células", self.calibration)

    def test_release_manifest_matches_144_cell_user_contract(self):
        self.assertIn('"mapa-k-intencao-unica-ate-144-celulas"', self.manifest)
        self.assertNotIn('"lote-unico-ate-16-celulas"', self.manifest)
        self.assertIn('"automaticCalibration": false', self.manifest)
        self.assertIn('"checkpoint-ack-readback"', self.manifest)

    def test_writer_is_direct_target_without_ramp_or_artificial_pause(self):
        self.assertNotIn("buildRamp(", self.writer)
        self.assertNotIn("Thread.sleep(pauseMs", self.writer)
        self.assertIn("Mp48Protocol.writeKCell(row, column, target)", self.writer)
        self.assertIn("cells.length() !in 1..MAX_BATCH_CELLS", self.writer)
        self.assertIn("const val MAX_BATCH_CELLS = ROW_COUNT * COLUMN_COUNT", self.writer)
        self.assertIn("serial.unit(", self.writer)
        self.assertIn('"escrita direta Mapa K"', self.writer)

    def test_ui_does_not_request_ramping(self):
        self.assertIn("this.api.writeMap(this.review.items, 0, 0,", self.map_ui)
        self.assertNotIn("this.api.writeMap(this.review.items, 3, 150,", self.map_ui)

    def test_success_requires_every_cell_confirmed(self):
        self.assertIn("failure == null && completedCells == plan.totalCells", self.calibration)
        self.assertIn('.put("state", "BATCH_CONFIRMED")', self.calibration)
        self.assertIn('.put("readbackValid", true)', self.calibration)
        self.assertIn('.put("humanConfirmed", true)', self.calibration)

    def test_partial_failure_is_explicit(self):
        self.assertIn('.put("state", "BATCH_PARTIAL_FAILED")', self.calibration)
        self.assertIn('.put("confirmedCells", completedCells)', self.calibration)
        self.assertIn('.put("partial", completedCells > 0)', self.calibration)

    def test_single_native_safety_policy_covers_all_mutating_bridges(self):
        for marker in (
            "MAX_SAFE_TELEMETRY_AGE_MS = 2_500L",
            "!status.serviceRunning",
            "!status.usbConnected",
            "status.usbPermissionPending",
            "!status.engineRunning || !status.engineReady || status.engineStuck",
            "status.directTelemetryAgeMs < 0L",
        ):
            self.assertIn(marker, self.policy)

        self.assertNotIn("DRIVING_PROBABLE_RPM", self.policy)
        self.assertNotIn("status.rpm >=", self.policy)
        self.assertIn("CalibrationWriteSafetyPolicy.unsafeReason(service.status())", self.calibration)
        # A ponte OmegasNative não escreve mais na ECU (F3): nenhuma escrita sem a política fica nela.
        self.assertNotIn("fun startKWrite(", self.hub)
        self.assertNotIn("fun startKFactorWrite(", self.hub)
        self.assertIn("unsafeMutationReason =", self.autocal_bridge)
        self.assertIn("CalibrationWriteSafetyPolicy.unsafeReason(service.status())", self.autocal_bridge)
        self.assertGreaterEqual(self.autocal_action.count("unsafeMutationReason()"), 3)

    def test_writer_safety_boundaries_remain_present(self):
        for marker in (
            "createPreWriteBackup",
            "requireAck",
            "ECU_BATCH_VERIFIED_NATIVE",
            "BATCH_PARTIAL_FAILED",
            "SAFETY_LOCKED_INSERTION_UNKNOWN",
        ):
            self.assertIn(marker, self.writer)


if __name__ == "__main__":
    unittest.main()
