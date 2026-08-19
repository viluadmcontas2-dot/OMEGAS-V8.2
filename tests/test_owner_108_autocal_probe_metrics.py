import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
METRICS = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalProbeMetrics.kt"


class Owner108AutoCalProbeMetricsContract(unittest.TestCase):
    def test_probe_metrics_measure_cost_information_and_telemetry_gap_without_io(self):
        source = METRICS.read_text("utf-8")
        self.assertNotIn("Thread(", source)
        self.assertNotIn("Executors.", source)
        self.assertNotIn("Mp48SerialScheduler", source)
        self.assertNotIn("MANUAL_WRITE", source)

        with tempfile.TemporaryDirectory(prefix="owner108-probe-metrics-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(
                textwrap.dedent(
                    """
                    import com.omegas.prohub.autocal.AutoCalProbeMetrics

                    fun main() {
                        val metrics = AutoCalProbeMetrics()
                        metrics.recordCycle(
                            startedAtElapsedMs = 1_000L,
                            finishedAtElapsedMs = 1_080L,
                            requestBytes = 3,
                            responseBytes = 15,
                            serialElapsedMs = 62L,
                            success = true,
                            fallbackUsed = false,
                            lastTelemetryBeforeMs = 990L,
                        )
                        check(metrics.snapshot().pendingTelemetryGap)
                        metrics.resolveTelemetryGap(listOf(990L, 1_095L, 1_120L))

                        metrics.recordCycle(
                            startedAtElapsedMs = 4_000L,
                            finishedAtElapsedMs = 4_140L,
                            requestBytes = 8,
                            responseBytes = 20,
                            serialElapsedMs = 101L,
                            success = true,
                            fallbackUsed = true,
                            lastTelemetryBeforeMs = 3_985L,
                        )
                        metrics.markMaterialChange()
                        metrics.resolveTelemetryGap(listOf(3_985L, 4_165L))

                        val snapshot = metrics.snapshot()
                        check(snapshot.cycles == 2L)
                        check(snapshot.successfulCycles == 2L)
                        check(snapshot.fallbackCycles == 1L)
                        check(snapshot.materialChanges == 1L)
                        check(snapshot.requestBytes == 11L)
                        check(snapshot.responseBytes == 35L)
                        check(snapshot.serialElapsedMs == 163L)
                        check(snapshot.wallElapsedMs == 220L)
                        check(snapshot.lastWallElapsedMs == 140L)
                        check(snapshot.maxWallElapsedMs == 140L)
                        check(snapshot.lastCadenceMs == 3_000L)
                        check(snapshot.lastTelemetryGapMs == 180L)
                        check(snapshot.maxTelemetryGapMs == 180L)
                        check(!snapshot.pendingTelemetryGap)
                        check(snapshot.informationYield == 0.5)
                        check(snapshot.lastCostShare != null)
                        check(kotlin.math.abs(snapshot.lastCostShare!! - (140.0 / 3000.0)) < 0.000001)
                        println("OWNER_108_PROBE_METRICS=PASS")
                    }
                    """
                ),
                encoding="utf-8",
            )
            jar = tmp / "owner108.jar"
            subprocess.run(
                ["kotlinc", str(METRICS), str(main), "-include-runtime", "-d", str(jar)],
                check=True,
                capture_output=True,
                text=True,
                timeout=30,
            )
            result = subprocess.run(
                ["java", "-jar", str(jar)],
                check=True,
                capture_output=True,
                text=True,
                timeout=10,
            )
            self.assertIn("OWNER_108_PROBE_METRICS=PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
