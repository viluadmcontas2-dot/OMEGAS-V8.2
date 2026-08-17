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
            "private val onTelemetryEvent: (String) -> Unit",
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
        ):
            self.assertIn(token, service)
        self.assertIn("@Volatile var running = false", runtime)
        self.assertIn("@Volatile var ready = false", runtime)
        self.assertIn("@Volatile var stuck = false", runtime)
        self.assertIn("@Volatile var lastError", runtime)
        self.assertNotIn("onError =", runtime)

    def test_typed_latest_state_precedes_legacy_json_projection(self):
        runtime = RUNTIME.read_text("utf-8")
        self.assertIn("LatestOnlyState<RuntimeTelemetryFrame>", runtime)
        self.assertIn("latestTelemetryState.beginGeneration(sessionId)", runtime)
        self.assertIn("latestTelemetryState.clear()", runtime)
        self.assertIn("fun currentTelemetryFrame(): RuntimeTelemetryFrame?", runtime)
        publish = runtime.index("if (!latestTelemetryState.publish(typedFrame)) return")
        legacy_json = runtime.index("val live = telemetry.toJson()")
        science_submit = runtime.index("val accepted = learningPipeline.submit(")
        self.assertLess(publish, legacy_json)
        self.assertLess(publish, science_submit)

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
