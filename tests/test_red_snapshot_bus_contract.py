from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt").read_text(encoding="utf-8")


class RedSnapshotBusContractTest(unittest.TestCase):
    def test_service_owns_one_runtime_snapshot_bus(self):
        self.assertIn("RuntimeSnapshotBus", SERVICE)
        self.assertIn("uiSnapshots", SERVICE)
        self.assertIn("uiSnapshots.publishPresent", SERVICE)

    def test_present_bridge_is_ram_only(self):
        self.assertIn("fun getPresentSnapshot(): String", BRIDGE)
        self.assertIn("presentSnapshotJson()", BRIDGE)

    def test_science_bridge_returns_cache_and_refreshes_off_webview_thread(self):
        self.assertIn("fun getScienceSnapshotSince(lastRevision: Long): String", BRIDGE)
        self.assertIn("scienceRefreshBusy", BRIDGE)
        self.assertIn("mapReadExecutor.execute", BRIDGE)
        self.assertIn("publishUiScienceSnapshot", BRIDGE)
        self.assertIn("scienceSnapshotSince", BRIDGE)

    def test_science_cache_signature_tracks_learning_and_map_files(self):
        self.assertIn("LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE", BRIDGE)
        self.assertIn('"learning_v6_evidence.json"', BRIDGE)
        self.assertIn('"k_map_cache.json"', BRIDGE)


if __name__ == "__main__":
    unittest.main()
