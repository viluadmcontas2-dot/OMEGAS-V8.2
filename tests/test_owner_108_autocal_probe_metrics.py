import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
METRICS = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalProbeMetrics.kt"
POLICY = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalProbeCadencePolicy.kt"
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"


class Owner108AutoCalProbeMetricsContract(unittest.TestCase):
    def _compile_and_run(self, main_source: str) -> str:
        with tempfile.TemporaryDirectory(prefix="owner108-probe-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(textwrap.dedent(main_source), encoding="utf-8")
            jar = tmp / "owner108.jar"
            subprocess.run(
                ["kotlinc", str(METRICS), str(POLICY), str(main), "-include-runtime", "-d", str(jar)],
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
            return result.stdout

    def test_probe_metrics_measure_cost_information_and_telemetry_gap_without_io(self):
        source = METRICS.read_text("utf-8")
        self.assertNotIn("Thread(", source)
        self.assertNotIn("Executors.", source)
        self.assertNotIn("Mp48SerialScheduler", source)
        self.assertNotIn("MANUAL_WRITE", source)

        stdout = self._compile_and_run(
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

                // Falsificador: sem frame posterior ao fim do probe, gap não existe ainda.
                metrics.resolveTelemetryGap(listOf(990L, 1_050L))
                val unresolved = metrics.snapshot()
                check(unresolved.pendingTelemetryGap)
                check(unresolved.lastTelemetryGapMs == null)

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
                check(snapshot.observationSpanMs == 3_000L)
                check(snapshot.lastCadenceMs == 3_000L)
                check(snapshot.lastTelemetryGapMs == 180L)
                check(snapshot.maxTelemetryGapMs == 180L)
                check(!snapshot.pendingTelemetryGap)
                check(snapshot.informationYield == 0.5)
                check(snapshot.averageWallElapsedMs == 110.0)
                println("OWNER_108_PROBE_METRICS=PASS")
            }
            """
        )
        self.assertIn("OWNER_108_PROBE_METRICS=PASS", stdout)

    def test_frozen_cost_information_policy_moves_only_with_cost_and_information(self):
        policy_source = POLICY.read_text("utf-8")
        self.assertNotIn("Thread(", policy_source)
        self.assertNotIn("Executors.", policy_source)
        self.assertNotIn("Mp48SerialScheduler", policy_source)
        self.assertNotIn("MANUAL_WRITE", policy_source)
        self.assertIn("sqrt(costRatio / eventRateRatio)", policy_source)
        self.assertIn("observationMs = 1_141_516L", policy_source)
        self.assertIn("probes = 434L", policy_source)
        self.assertIn("materialChanges = 4L", policy_source)

        stdout = self._compile_and_run(
            """
            import com.omegas.prohub.autocal.AutoCalProbeCadencePolicy
            import com.omegas.prohub.autocal.AutoCalProbeMetrics

            fun measured(costMs: Long, spanMs: Long, changes: Int): AutoCalProbeMetrics.Snapshot {
                val metrics = AutoCalProbeMetrics()
                metrics.recordCycle(1_000L, 1_000L + costMs, 3, 20, costMs, true, false, null)
                if (spanMs > 0L) {
                    metrics.recordCycle(1_000L + spanMs, 1_000L + spanMs + costMs, 3, 20, costMs, true, false, null)
                }
                repeat(changes) { metrics.markMaterialChange() }
                return metrics.snapshot()
            }

            fun main() {
                val policy = AutoCalProbeCadencePolicy()
                val empty = AutoCalProbeMetrics()
                val bootstrap = policy.recommend(empty.snapshot())
                check(bootstrap.recommendedCadenceMs == 2_630L)
                check(bootstrap.priorProvenance == "PORTMONLOGNOVO_434_STATUS_PROBES_2026_08")

                val normalCost = policy.recommend(measured(costMs = 30L, spanMs = 60_000L, changes = 0))
                val highCost = policy.recommend(measured(costMs = 120L, spanMs = 60_000L, changes = 0))
                check(highCost.recommendedCadenceMs > normalCost.recommendedCadenceMs)

                val quiet = policy.recommend(measured(costMs = 30L, spanMs = 60_000L, changes = 0))
                val informative = policy.recommend(measured(costMs = 30L, spanMs = 60_000L, changes = 3))
                check(informative.recommendedCadenceMs < quiet.recommendedCadenceMs)

                val shortQuiet = policy.recommend(measured(costMs = 30L, spanMs = 60_000L, changes = 0))
                val longQuiet = policy.recommend(measured(costMs = 30L, spanMs = 600_000L, changes = 0))
                check(longQuiet.recommendedCadenceMs > shortQuiet.recommendedCadenceMs)

                val reset = AutoCalProbeMetrics()
                reset.recordCycle(1_000L, 1_120L, 3, 20, 120L, true, false, null)
                reset.recordCycle(61_000L, 61_120L, 3, 20, 120L, true, false, null)
                reset.markMaterialChange()
                reset.reset()
                check(policy.recommend(reset.snapshot()).recommendedCadenceMs == bootstrap.recommendedCadenceMs)
                println("OWNER_108A_COST_INFORMATION=PASS")
            }
            """
        )
        self.assertIn("OWNER_108A_COST_INFORMATION=PASS", stdout)

    def test_native_monitor_applies_policy_only_to_compact_status_probe(self):
        monitor = MONITOR.read_text("utf-8")
        self.assertIn("private val probeMetrics = AutoCalProbeMetrics()", monitor)
        self.assertIn("private val probeCadencePolicy = AutoCalProbeCadencePolicy()", monitor)
        self.assertIn("previousProbe == null ||", monitor)
        self.assertIn("val freshProbe = if (statusProbeDue) probe(currentSession) ?: return else null", monitor)
        self.assertIn("freshProbe != null && previousProbe != null", monitor)
        self.assertIn("if (freshProbe != null) scheduleNextStatusProbe", monitor)
        self.assertIn("val maturityProbe = if (thresholdsReady) probeMaturityCounters(currentSession) else null", monitor)
        self.assertIn("probeMetrics.recordCycle(", monitor)
        self.assertIn("probeMetrics.markMaterialChange()", monitor)
        self.assertIn('.put("cadenceAuthority", "COST_INFORMATION_POLICY")', monitor)
        self.assertIn('.put("opportunityClock", "SERVICE_HEALTH_TICK")', monitor)
        self.assertIn('.put("policyApplied", true)', monitor)
        self.assertIn("reply.echo.size + reply.rawResponse.size", monitor)
        self.assertIn("AutoCalProtocol.CMD_NATIVE_STATUS", monitor)
        self.assertIn("timeoutMs = 700", monitor)
        self.assertNotIn("ScheduledExecutor", monitor)
        self.assertNotIn("Executors.", monitor)
        self.assertNotIn("Mp48WorkClass.MANUAL_WRITE", monitor)
        self.assertNotIn('.put("cadenceAuthority", "SERVICE_HEALTH_TICK")', monitor)


if __name__ == "__main__":
    unittest.main()
