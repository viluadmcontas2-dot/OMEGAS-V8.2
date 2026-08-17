from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/java/com/omegas/prohub/ecu/NativeRuntimeManager.kt"
ADMISSION = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48BackpressureScheduler.kt"
POLICY = ROOT / "app/src/main/java/com/omegas/prohub/util/RuntimeBackpressurePolicy.kt"
LEARNING = ROOT / "app/src/main/java/com/omegas/prohub/util/RealtimeLearningBuffer.kt"


class Mp48RuntimeBackpressureIntegration(unittest.TestCase):
    def test_runtime_exposes_one_bounded_scheduler_over_the_engine(self):
        runtime = RUNTIME.read_text("utf-8")
        self.assertIn("private val engine = ResponseDrivenEcuEngine(", runtime)
        self.assertIn("private val serialAdmission = Mp48BackpressureScheduler(engine)", runtime)
        self.assertIn("fun serialScheduler(): Mp48SerialScheduler = serialAdmission", runtime)
        self.assertIn('.put("serialAdmission", serialAdmission.metricsJson())', runtime)
        self.assertNotIn("fun serialScheduler(): Mp48SerialScheduler = engine", runtime)

    def test_admission_has_no_thread_or_secondary_transport(self):
        source = ADMISSION.read_text("utf-8")
        self.assertIn("private val delegate: Mp48SerialScheduler", source)
        self.assertIn("Semaphore", source)
        self.assertIn("READ_ONLY", source)
        self.assertIn("MANUAL_WRITE", source)
        self.assertIn("SAFETY", source)
        self.assertIn("readOnlyRejected", source)
        self.assertIn("criticalRejected", source)
        self.assertNotIn("Executors", source)
        self.assertNotIn("Thread(", source)
        self.assertNotIn("UsbSerialManager", source)

    def test_one_policy_owns_resource_budgets(self):
        admission = ADMISSION.read_text("utf-8")
        policy = POLICY.read_text("utf-8")
        self.assertIn("RuntimeBackpressurePolicy.SECONDARY_READ_PENDING_CAPACITY", admission)
        self.assertIn("RuntimeBackpressurePolicy.CRITICAL_SERIAL_RESERVED_CAPACITY", admission)
        self.assertIn("SECONDARY_READ_PENDING_CAPACITY = 32", policy)
        self.assertIn("CRITICAL_SERIAL_RESERVED_CAPACITY = 8", policy)

    def test_active_learning_hot_buffer_is_already_hard_bounded(self):
        source = LEARNING.read_text("utf-8")
        self.assertIn("MAX_HOT_EVIDENCE = 3", source)
        self.assertIn("coerceIn(1, MAX_HOT_EVIDENCE)", source)
        self.assertIn("supersededImportant", source)
        self.assertIn("coalescedTransient", source)


if __name__ == "__main__":
    unittest.main()
