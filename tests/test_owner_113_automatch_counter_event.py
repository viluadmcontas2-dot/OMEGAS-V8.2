import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRACKER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoMatchCounterTracker.kt"
MONITOR = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt"


class Owner113AutoMatchCounterEvent(unittest.TestCase):
    def test_count_change_is_typed_session_bound_event_without_mul_assumption(self):
        source = TRACKER.read_text("utf-8")
        self.assertNotIn("Thread(", source)
        self.assertNotIn("Executor", source)
        self.assertNotIn("Mp48SerialScheduler", source)
        self.assertIn("mulActChangeConfirmed: Boolean = false", source)

        with tempfile.TemporaryDirectory(prefix="owner113-automatch-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(textwrap.dedent(
                """
                import com.omegas.prohub.autocal.NativeAutoMatchCounterTracker

                fun main() {
                    val tracker = NativeAutoMatchCounterTracker()

                    check(tracker.observe(10L, 0, 1_000L) == null)
                    check(tracker.observe(10L, 0, 2_000L) == null)

                    val one = tracker.observe(10L, 1, 3_000L)!!
                    check(one.eventType == "AUTOMATCH_COUNT_INCREASED")
                    check(one.sessionId == 10L)
                    check(one.observedAtElapsedMs == 3_000L)
                    check(one.beforeCount == 0)
                    check(one.afterCount == 1)
                    check(one.delta == 1)
                    check(!one.mulActChangeConfirmed)

                    val jump = tracker.observe(10L, 3, 4_000L)!!
                    check(jump.beforeCount == 1)
                    check(jump.afterCount == 3)
                    check(jump.delta == 2)

                    // Queda/reset não fabrica incremento; apenas vira novo baseline da sessão.
                    check(tracker.observe(10L, 0, 5_000L) == null)
                    val afterReset = tracker.observe(10L, 1, 6_000L)!!
                    check(afterReset.beforeCount == 0)
                    check(afterReset.afterCount == 1)

                    // Nova sessão nunca conecta o contador da sessão anterior.
                    check(tracker.observe(11L, 2, 7_000L) == null)
                    val newSession = tracker.observe(11L, 3, 8_000L)!!
                    check(newSession.sessionId == 11L)
                    check(newSession.beforeCount == 2)
                    check(newSession.afterCount == 3)

                    check(tracker.observe(0L, 99, 9_000L) == null)
                    check(tracker.observe(11L, -1, 9_500L) == null)

                    println("OWNER_113_AUTOMATCH_COUNTER=PASS")
                }
                """
            ), encoding="utf-8")
            jar = tmp / "owner113.jar"
            subprocess.run(
                ["kotlinc", str(TRACKER), str(main), "-include-runtime", "-d", str(jar)],
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
            self.assertIn("OWNER_113_AUTOMATCH_COUNTER=PASS", result.stdout)

    def test_monitor_uses_typed_event_without_extra_io_or_mul_promotion(self):
        monitor = MONITOR.read_text("utf-8")
        self.assertIn("private val autoMatchCounterTracker = NativeAutoMatchCounterTracker()", monitor)
        self.assertIn("autoMatchCounterTracker.reset()", monitor)
        self.assertIn("val autoMatchEvent = freshProbe?.let", monitor)
        self.assertIn("autoMatchCounterTracker.observe(", monitor)
        self.assertIn('snapshotReason = if (autoMatchEvent != null) "AUTOMATCH_COUNT_CHANGED"', monitor)
        self.assertIn('.put("latestAutoMatchEvent"', monitor)
        self.assertIn('.put("beforeCount", event.beforeCount)', monitor)
        self.assertIn('.put("afterCount", event.afterCount)', monitor)
        self.assertIn('.put("delta", event.delta)', monitor)
        self.assertIn('.put("mulActChangeConfirmed", event.mulActChangeConfirmed)', monitor)
        # O owner 113 não adiciona nova leitura: usa o freshProbe que já existia.
        self.assertEqual(monitor.count("AutoCal status leve"), 1)
        self.assertEqual(monitor.count("AutoCal fallback contador 0x0174"), 1)
        self.assertNotIn("MANUAL_WRITE", monitor)
        self.assertNotIn("ScheduledExecutor", monitor)


if __name__ == "__main__":
    unittest.main()
