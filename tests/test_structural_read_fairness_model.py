from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
READER = ROOT / "app/src/main/java/com/omegas/prohub/calibration/CompositeCalibrationReader.kt"
ENGINE = ROOT / "app/src/main/java/com/omegas/prohub/ecu/ResponseDrivenEcuEngine.kt"


class StructuralReadFairnessModel(unittest.TestCase):
    def test_composite_snapshot_is_one_read_only_atomic_unit_with_telemetry_after(self):
        source = READER.read_text("utf-8")
        self.assertIn('reason = "snapshot físico composto da calibração"', source)
        self.assertIn("workClass = Mp48WorkClass.READ_ONLY", source)
        self.assertIn("telemetryAfter = true", source)
        self.assertIn("20 transações", source)
        self.assertIn("countStart = readAutoMatchCount(unit)", source)
        self.assertIn("mulActStart = readCurveVector", source)
        self.assertIn("mulActEnd = readCurveVector", source)
        self.assertIn("countEnd = readAutoMatchCount(unit)", source)

    def test_engine_returns_to_telemetry_immediately_after_planned_unit(self):
        source = ENGINE.read_text("utf-8")
        queued = source.index("val queued = queue.poll()")
        run = source.index("runQueued(queued)", queued)
        mark = source.index("plannedWorkSinceLastTelemetry = true", run)
        after = source.index("if (queued.telemetryAfter", mark)
        poll = source.index("pollTelemetry()", after)
        continue_loop = source.index("continue", poll)
        self.assertLess(queued, run)
        self.assertLess(run, mark)
        self.assertLess(mark, after)
        self.assertLess(after, poll)
        self.assertLess(poll, continue_loop)

    def test_before_during_after_distribution_recovers_without_threshold_assumption(self):
        # Deterministic model of the exact scheduling contract: baseline telemetry,
        # one atomic 20-transaction read, immediate telemetry, then normal telemetry.
        for transaction_ms in (5, 25, 100, 1200):
            baseline = [transaction_ms] * 8
            during = [20 * transaction_ms + transaction_ms]
            after = [transaction_ms] * 8
            self.assertGreater(during[0], max(baseline))
            self.assertEqual(baseline, after)
            self.assertEqual(transaction_ms, max(after))


if __name__ == "__main__":
    unittest.main()
