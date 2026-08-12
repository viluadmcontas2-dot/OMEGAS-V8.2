import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLAN = ROOT / "app/src/main/java/com/omegas/prohub/calibration/MapBatchPlan.kt"
POLICY = ROOT / "app/src/main/java/com/omegas/prohub/calibration/CalibrationWriteSafetyPolicy.kt"
BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/web/V7JavascriptBridge.kt"
HUB = ROOT / "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt"
AUTOCAL_BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt"
AUTOCAL_ACTION = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt"
WRITER = ROOT / "app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt"
MANIFEST = ROOT / "config/omegas-release.json"


class V8MapBatchContract(unittest.TestCase):
    def setUp(self):
        self.plan = PLAN.read_text("utf-8")
        self.policy = POLICY.read_text("utf-8")
        self.bridge = BRIDGE.read_text("utf-8")
        self.hub = HUB.read_text("utf-8")
        self.autocal_bridge = AUTOCAL_BRIDGE.read_text("utf-8")
        self.autocal_action = AUTOCAL_ACTION.read_text("utf-8")
        self.writer = WRITER.read_text("utf-8")
        self.manifest = MANIFEST.read_text("utf-8")

    def test_user_intent_supports_full_writable_grid(self):
        self.assertIn("MAX_USER_CELLS = KMapPhysicalAxes.WRITABLE_ROWS * KMapPhysicalAxes.COLUMNS", self.plan)
        self.assertIn("INTERNAL_CHUNK_CELLS = 16", self.plan)
        self.assertIn("fun startMapBatchWrite", self.bridge)
        self.assertIn("MapBatchPlan.build(cells)", self.bridge)
        self.assertIn("até 144 células", self.bridge)

    def test_release_manifest_matches_144_cell_user_contract(self):
        self.assertIn('"mapa-k-intencao-unica-ate-144-celulas"', self.manifest)
        self.assertNotIn('"lote-unico-ate-16-celulas"', self.manifest)
        self.assertIn('"automaticCalibration": false', self.manifest)
        self.assertIn('"checkpoint-ack-readback"', self.manifest)

    def test_chunking_remains_native_not_javascript(self):
        self.assertIn("service.startKBatchWrite", self.bridge)
        self.assertIn("plan.chunks.forEachIndexed", self.bridge)
        self.assertIn("cells.length() !in 1..16", self.writer)

    def test_success_requires_every_cell_confirmed(self):
        self.assertIn("failure == null && completedCells == plan.totalCells", self.bridge)
        self.assertIn('.put("state", "BATCH_CONFIRMED")', self.bridge)
        self.assertIn('.put("readbackValid", true)', self.bridge)
        self.assertIn('.put("humanConfirmed", true)', self.bridge)

    def test_partial_failure_is_explicit(self):
        self.assertIn('.put("state", "BATCH_PARTIAL_FAILED")', self.bridge)
        self.assertIn('.put("confirmedCells", completedCells)', self.bridge)
        self.assertIn('.put("partial", completedCells > 0)', self.bridge)

    def test_single_native_safety_policy_covers_all_mutating_bridges(self):
        for marker in (
            "MAX_SAFE_TELEMETRY_AGE_MS = 2_500L",
            "DRIVING_PROBABLE_RPM = 1_200",
            "!status.serviceRunning",
            "!status.usbConnected",
            "status.usbPermissionPending",
            "!status.engineRunning || !status.engineReady || status.engineStuck",
            "status.directTelemetryAgeMs < 0L",
            "status.rpm >= DRIVING_PROBABLE_RPM",
        ):
            self.assertIn(marker, self.policy)

        self.assertIn("CalibrationWriteSafetyPolicy.unsafeReason(service.status())", self.bridge)
        self.assertGreaterEqual(
            self.hub.count("CalibrationWriteSafetyPolicy.unsafeReason(service.status())"),
            3,
        )
        self.assertIn("unsafeMutationReason =", self.autocal_bridge)
        self.assertIn("CalibrationWriteSafetyPolicy.unsafeReason(service.status())", self.autocal_bridge)
        self.assertGreaterEqual(self.autocal_action.count("unsafeMutationReason()"), 3)

    def test_legacy_apply_suggestion_is_prepare_only(self):
        self.assertIn("fun applySuggestion", self.bridge)
        self.assertIn('"MANUAL_REVIEW_REQUIRED"', self.bridge)
        self.assertIn('.put("writesStarted", false)', self.bridge)
        self.assertNotIn("v7ApplySuggestion", self.bridge)

    def test_writer_safety_boundaries_remain_present(self):
        for marker in (
            "createPreWriteBackup",
            "requireAck",
            "ECU_READBACK_NATIVE",
            "BATCH_PARTIAL_FAILED",
            "SAFETY_LOCKED_INSERTION_UNKNOWN",
        ):
            self.assertIn(marker, self.writer)


if __name__ == "__main__":
    unittest.main()
