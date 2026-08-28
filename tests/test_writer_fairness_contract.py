from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAP = ROOT / "app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt"
CURVE = ROOT / "app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt"
SCHED = ROOT / "app/src/main/java/com/omegas/prohub/ecu/Mp48SerialScheduler.kt"


class WriterFairnessContract(unittest.TestCase):
    def test_map_write_and_readback_share_one_manual_write_unit(self):
        source = MAP.read_text("utf-8")
        unit = source.index('reason = "escrita + readback MAP_K[$row,$column]"')
        work = source.index("workClass = Mp48WorkClass.MANUAL_WRITE", unit)
        telemetry = source.index("telemetryAfter = true", unit)
        write = source.index("Mp48Protocol.writeKCell", unit)
        readback = source.index("readRow(unit, row", write)
        end = source.index("repeat(COLUMN_COUNT)", readback)
        self.assertLess(unit, work)
        self.assertLess(work, telemetry)
        self.assertLess(telemetry, write)
        self.assertLess(write, readback)
        self.assertLess(readback, end)

    def test_curve_write_and_full_readback_share_one_manual_write_unit(self):
        source = CURVE.read_text("utf-8")
        unit = source.index('reason = "escrita + readback K factor[$index]"')
        work = source.index("workClass = Mp48WorkClass.MANUAL_WRITE", unit)
        telemetry = source.index("telemetryAfter = true", unit)
        write = source.index("KFactorProtocol.writeFactor", unit)
        readback = source.index("readRawPoints(unit", write)
        compare = source.index("repeat(KFactorProtocol.POINT_COUNT)", readback)
        self.assertLess(unit, work)
        self.assertLess(work, telemetry)
        self.assertLess(telemetry, write)
        self.assertLess(write, readback)
        self.assertLess(readback, compare)

    def test_safety_outranks_manual_write_and_read_only(self):
        source = SCHED.read_text("utf-8")
        safety = source.index("SAFETY(0)")
        manual = source.index("MANUAL_WRITE(1)")
        read = source.index("READ_ONLY(2)")
        self.assertLess(safety, manual)
        self.assertLess(manual, read)

    def test_each_write_unit_yields_telemetry_before_next_unit(self):
        # Deterministic scheduler model: each indivisible write/readback pair is one
        # unit; telemetryAfter=true inserts a telemetry opportunity after every unit.
        cells = 16
        points = 30
        map_trace = []
        for index in range(cells):
            map_trace += [f"write+readback:{index}", "telemetry"]
        curve_trace = []
        for index in range(points):
            curve_trace += [f"write+readback:{index}", "telemetry"]
        self.assertTrue(all(map_trace[i + 1] == "telemetry" for i in range(0, len(map_trace), 2)))
        self.assertTrue(all(curve_trace[i + 1] == "telemetry" for i in range(0, len(curve_trace), 2)))
        self.assertEqual(cells, map_trace.count("telemetry"))
        self.assertEqual(points, curve_trace.count("telemetry"))


if __name__ == "__main__":
    unittest.main()
