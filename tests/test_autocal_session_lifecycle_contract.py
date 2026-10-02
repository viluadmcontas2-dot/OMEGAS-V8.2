import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = (ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt").read_text(encoding="utf-8")
RECORDER = (ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt").read_text(encoding="utf-8")
BRIDGE = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt").read_text(encoding="utf-8")
LEDGER = (ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionSemanticLedger.kt").read_text(encoding="utf-8")


class AutoCalSessionLifecycleContractTest(unittest.TestCase):
    def test_usb_generation_is_the_session_boundary(self):
        anchor = SERVICE.index("val generationChanged = transition == UsbSessionTransition.GENERATION_CHANGED")
        generation = SERVICE[anchor:SERVICE.index("telemetryStore.beginSession(sessionId)", anchor)]
        stop = generation.index('sessionRecorder.stop("USB_SESSION_REPLACED")')
        self.assertIn('.put("usbSessionId", sessionId)', SERVICE)
        self.assertGreaterEqual(stop, 0)

    def test_manual_session_carries_current_physical_generation(self):
        start = SERVICE[SERVICE.index("fun startSessionRecording"):SERVICE.index("fun stopSessionRecording")]
        self.assertIn("usb.connectionSessionId", start)
        self.assertIn('"usbSessionId"', start)

    def test_recorder_owns_one_semantic_projection_not_another_capture_loop(self):
        self.assertIn("SessionSemanticLedger", RECORDER)
        self.assertIn("session_summary.json", LEDGER)
        self.assertIn('sessionRecorder.record("telemetry", "mp48", live)', SERVICE)
        self.assertNotIn("ScheduledExecutor", LEDGER)
        self.assertNotIn("ThreadPoolExecutor", LEDGER)

    def test_manual_autocal_snapshot_is_recorded_into_same_session_evidence(self):
        self.assertIn('sessionRecorder.record("autocal_manual_snapshot", "autocal", snapshot, force = true)', BRIDGE)

    def test_session_summary_is_exportable_and_never_claims_automatic_map_write(self):
        self.assertIn('automaticMapKMutation', LEDGER)
        self.assertIn('.put("automaticMapKMutation", false)', LEDGER)
        self.assertIn("SessionSemanticLedger.FILE_NAME", RECORDER)

    def test_summary_is_atomic_and_rebuildable_from_jsonl(self):
        for token in ("loadOrRebuild", "ATOMIC_MOVE", "events_", "rebuildParseErrors"):
            self.assertIn(token, LEDGER)


if __name__ == "__main__":
    unittest.main()
