from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"
PLANNER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalRefreshPlanner.kt"
SERVICE = ROOT / "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt"

class AutoCalRealtimeContract(unittest.TestCase):
    def test_acquisition_mirror_runs_at_one_hz_without_second_serial_authority(self):
        monitor = MONITOR.read_text("utf-8")
        planner = PLANNER.read_text("utf-8")
        service = SERVICE.read_text("utf-8")

        self.assertIn("ACQUISITION_INTERVAL_MS = 1_000L", planner)
        self.assertIn(
            "scheduleWithFixedDelay(::autoCalTick, 1_000L, 1_000L",
            service,
        )
        self.assertIn('reason = "AutoCal aquisição operacional agrupada"', monitor)
        self.assertIn('reason = "AutoCal referência agrupada"', monitor)
        self.assertGreaterEqual(monitor.count("serial.unit("), 2)
        self.assertGreaterEqual(monitor.count("telemetryAfter = true"), 2)
        self.assertIn("Mp48WorkClass.READ_ONLY", monitor)
        self.assertNotIn("UsbSerialManager", monitor)
        self.assertNotIn("Executors.", monitor)
        self.assertNotIn("ScheduledExecutor", monitor)

    def test_reference_lane_remains_slower_than_operational_acquisition(self):
        planner = PLANNER.read_text("utf-8")
        self.assertIn("REFERENCE_INTERVAL_MS = 4_000L", planner)

if __name__ == "__main__":
    unittest.main()
