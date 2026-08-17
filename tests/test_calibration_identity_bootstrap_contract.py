from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"
READER = ROOT / "app/src/main/java/com/omegas/prohub/calibration/CompositeCalibrationReader.kt"


class CalibrationIdentityBootstrapContract(unittest.TestCase):
    def test_monitor_bootstraps_once_after_existing_settle_without_new_scheduler(self):
        source = MONITOR.read_text("utf-8")
        self.assertIn("SESSION_SETTLE_MS", source)
        self.assertIn("calibrationBootstrapAttempted", source)
        self.assertIn("calibrationBootstrapReader.readAtSessionStart(currentSession)", source)
        self.assertIn("CompositeCalibrationSnapshot.promote(raw)", source)
        self.assertIn("CalibrationIdentity.fromComposite", source)
        self.assertIn("CALIBRATION_BOOTSTRAP_FAILED", source)
        self.assertNotIn("Executors.new", source)
        self.assertNotIn("Thread(", source)
        self.assertNotIn("writeKCell", source)
        self.assertNotIn("startBatchWrite", source)

    def test_composite_reader_remains_one_read_only_serial_unit(self):
        source = READER.read_text("utf-8")
        self.assertIn("serial.unit(", source)
        self.assertIn("workClass = Mp48WorkClass.READ_ONLY", source)
        self.assertIn("telemetryAfter = true", source)
        self.assertNotIn("Mp48WorkClass.MANUAL_WRITE", source)
        self.assertNotIn("Executors", source)


if __name__ == "__main__":
    unittest.main()
