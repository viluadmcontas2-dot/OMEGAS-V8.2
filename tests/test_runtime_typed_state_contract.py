from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt"
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"
LEARNING = ROOT / "app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt"


class RuntimeTypedStateContract(unittest.TestCase):
    def test_service_and_runtime_constructor_contract_stays_compatible(self):
        runtime = RUNTIME.read_text("utf-8")
        service = SERVICE.read_text("utf-8")
        for token in (
            "paths: AppPaths",
            "private val usb: UsbSerialManager",
            "private val log: RingLog",
            "private val onStateChanged: () -> Unit",
            "private val onTelemetryEvent: (JSONObject) -> Unit",
            "private val onEngineExited: (Boolean) -> Unit",
        ):
            self.assertIn(token, runtime)
        for token in (
            "paths = paths",
            "usb = usb",
            "log = log",
            "onStateChanged = ::stateChanged",
            "onTelemetryEvent = ::consumeEngineEvent",
            "onEngineExited = ::onEngineExited",
            "private fun consumeEngineEvent(root: JSONObject)",
        ):
            self.assertIn(token, service)
        self.assertIn("@Volatile var running = false", runtime)
        self.assertIn("@Volatile var ready = false", runtime)
        self.assertIn("@Volatile var stuck = false", runtime)
        self.assertIn("@Volatile var lastError", runtime)
        self.assertNotIn("onError =", runtime)

    def test_canonical_latest_state_precedes_all_legacy_json_projection(self):
        runtime = RUNTIME.read_text("utf-8")
        self.assertIn("LatestOnlyState<CanonicalEvidence>", runtime)
        self.assertIn("latestCanonicalEvidence.beginGeneration(sessionId)", runtime)
        self.assertIn("latestCanonicalEvidence.clear()", runtime)
        self.assertIn("fun currentTelemetryFrame(): RuntimeTelemetryFrame? = latestCanonicalEvidence.current()?.frame", runtime)
        publish = runtime.index("if (!latestCanonicalEvidence.publish(evidence)) return")
        delivery_submit = runtime.index("telemetryDeliveryPipeline.submit(sequence)", publish)
        adaptive_submit = runtime.index("adaptiveShadowPipeline.submit(sequence)", publish)
        science_submit = runtime.index("val accepted = learningPipeline.submit(", publish)
        compatibility = runtime.index("private fun projectTelemetryCompatibility(", publish)
        legacy_json = runtime.index("val live = telemetry.toJson()", compatibility)
        self.assertLess(publish, delivery_submit)
        self.assertLess(publish, adaptive_submit)
        self.assertLess(publish, science_submit)
        self.assertLess(science_submit, compatibility)
        self.assertLess(compatibility, legacy_json)

    def test_scientific_pipeline_exposes_bounded_depth_and_evidence_cost(self):
        source = LEARNING.read_text("utf-8")
        for token in (
            "MAX_HOT_EVIDENCE = 3",
            "coalescedTransient",
            "supersededImportant",
            "rejectedStale",
            "lastImportantQueueDelayMs",
            "maxImportantQueueDelayMs",
            "lastImportantProcessingMs",
            "maxImportantProcessingMs",
            "executedImportant",
            "executedTransient",
        ):
            self.assertIn(token, source)


if __name__ == "__main__":
    unittest.main()
