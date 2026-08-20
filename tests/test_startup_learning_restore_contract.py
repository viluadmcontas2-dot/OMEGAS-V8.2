import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt").read_text("utf-8")
DEFERRED = (ROOT / "app/src/main/java/com/omegas/prohub/learning/DeferredLiveOnlyLearningStore.kt").read_text("utf-8")
SIGNAL = (ROOT / "app/src/main/java/com/omegas/prohub/learning/SignalLearningStore.kt").read_text("utf-8")
MEMORY = (ROOT / "app/src/main/java/com/omegas/prohub/learning/MotorLearningMemory.kt").read_text("utf-8")


class StartupLearningRestoreContract(unittest.TestCase):
    def test_runtime_no_longer_constructs_learning_or_migration_synchronously(self):
        self.assertIn("DeferredLiveOnlyLearningStore(paths.runtimeRoot, log)", RUNTIME)
        self.assertNotIn("LearningTelemetrySchemaMigration.prepare(paths.runtimeRoot, log)", RUNTIME)
        self.assertNotIn("LiveOnlyLearningStore(\n        File(paths.runtimeRoot", RUNTIME)

    def test_restore_work_is_owned_by_dedicated_daemon_thread(self):
        self.assertIn('Thread(runnable, "omegas-learning-restore").apply { isDaemon = true }', DEFERRED)
        self.assertIn("LearningTelemetrySchemaMigration.prepare(runtimeRoot, log)", DEFERRED)
        self.assertIn("LiveOnlyLearningStore(stateFile, log)", DEFERRED)
        self.assertIn("restoreExecutor.execute", DEFERRED)

    def test_telemetry_does_not_wait_for_restore(self):
        self.assertIn('skippedFrames.incrementAndGet()', DEFERRED)
        self.assertIn('"Telemetria ativa; Learning aguardando restauração"', DEFERRED)
        self.assertIn('const val STATE_RESTORING = "LEARNING_RESTORING"', DEFERRED)
        consume_start = RUNTIME.index("    private fun consumeTelemetry(")
        consume_end = RUNTIME.index("\n    private fun publishLearningState", consume_start)
        consume = RUNTIME[consume_start:consume_end]
        self.assertIn("telemetryDeliveryPipeline.submit(sequence)", consume)
        self.assertIn("learningPipeline.submit(", consume)

    def test_exports_and_manual_preview_fail_fast_while_restore_is_pending(self):
        self.assertIn('unavailable("export", deviceId)', DEFERRED)
        self.assertIn('unavailable("preview_k_write", "$row:$column:$value")', DEFERRED)
        self.assertIn('if (!exported.optBoolean("ok", false)) return exported.put("componentRevision", 0L)', RUNTIME)
        self.assertIn('if (cached.optString("state") == DeferredLiveOnlyLearningStore.STATE_RESTORING)', RUNTIME)
        self.assertIn('if (!refreshed.optBoolean("restoring", false))', RUNTIME)

    def test_confirmed_calibration_is_never_forgotten_during_restore(self):
        self.assertIn("DeferredOperation.CalibrationAdjustment(JSONObject(payload.toString()))", DEFERRED)
        self.assertIn("replayDeferredOperationsLocked(restored.store)", DEFERRED)
        self.assertIn("is DeferredOperation.CalibrationAdjustment ->", DEFERRED)
        self.assertIn("store.onCalibrationAdjustment(operation.payload)", DEFERRED)
        self.assertIn('put("deferred", true)', DEFERRED)
        self.assertIn('put("resetPerformed", false)', DEFERRED)

    def test_existing_heavy_operations_remain_behind_deferred_boundary(self):
        self.assertIn("load()", MEMORY)
        self.assertIn("rebuildVisualStatusFromMemory()", MEMORY)
        self.assertIn("@Volatile private var advisor = analyzeCurrentMemory()", SIGNAL)
        self.assertIn("init { loadEvidenceState() }", SIGNAL)
        self.assertNotIn("SignalLearningStore(", RUNTIME)
        self.assertNotIn("MotorLearningMemory(", RUNTIME)


if __name__ == "__main__":
    unittest.main()
