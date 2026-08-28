from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
READER = ROOT / "app/src/main/java/com/omegas/prohub/calibration/CompositeCalibrationReader.kt"
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"


class CompositeCalibrationPerformanceContract(unittest.TestCase):
    def test_reader_measures_real_elapsed_time_and_remains_bounded(self):
        source = READER.read_text("utf-8")
        self.assertIn("val elapsedMs: Long = 0L", source)
        self.assertIn("val startedNs = System.nanoTime()", source)
        self.assertIn("result.copy(elapsedMs = elapsedMs)", source)
        self.assertIn("const val WAIT_TIMEOUT_MS = 30_000L", source)
        self.assertIn("private const val TRANSACTION_TIMEOUT_MS = 1_200", source)
        self.assertIn("telemetryAfter = true", source)
        self.assertNotIn("Thread.sleep", source)
        self.assertNotIn("Executors", source)

    def test_bootstrap_is_once_per_session_after_settle_not_a_recurring_heavy_poll(self):
        source = MONITOR.read_text("utf-8")
        self.assertIn("calibrationBootstrapAttempted = false", source)
        self.assertIn("if (!calibrationBootstrapAttempted)", source)
        self.assertIn("calibrationBootstrapAttempted = true", source)
        self.assertIn("if (ageMs < SESSION_SETTLE_MS)", source)
        self.assertNotIn("scheduleAtFixedRate", source)
        self.assertNotIn("scheduleWithFixedDelay", source)


if __name__ == "__main__":
    unittest.main()
