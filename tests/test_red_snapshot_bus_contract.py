from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/web/HubJavascriptBridge.kt").read_text(encoding="utf-8")
STORE = (ROOT / "app/src/main/java/com/omegas/prohub/telemetry/TelemetryStateStore.kt").read_text(encoding="utf-8")


class RedSnapshotBusContractTest(unittest.TestCase):
    def test_present_authority_reuses_latest_only_telemetry_store(self):
        self.assertIn("fun liveJson(): String", STORE)
        self.assertIn("RuntimeSnapshotBus", BRIDGE)
        self.assertIn("uiSnapshots", BRIDGE)
        self.assertIn("telemetryStore.liveJson()", BRIDGE)
        self.assertIn("uiSnapshots.publishPresent", BRIDGE)

    def test_present_bridge_body_is_ram_only(self):
        self.assertIn("fun getPresentSnapshot(): String", BRIDGE)
        start = BRIDGE.index("fun getPresentSnapshot(): String")
        end = BRIDGE.index("fun getStatus", start)
        body = BRIDGE[start:end]
        self.assertIn("telemetryStore.liveJson()", body)
        self.assertNotIn("fullEngineSnapshotJson", body)
        self.assertNotIn("getLearningMaps", body)
        self.assertNotIn("v7CalibrationStateJson", body)


if __name__ == "__main__":
    unittest.main()
