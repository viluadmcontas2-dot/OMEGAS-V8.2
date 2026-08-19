import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRACKER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMaturityTracker.kt"


class Owner109AutoCalMaturityTriState(unittest.TestCase):
    def test_maturity_is_unknown_when_threshold_or_shape_is_unknown(self):
        source = TRACKER.read_text("utf-8")
        self.assertIn("enum class MaturityState", source)
        self.assertIn("UNKNOWN", source)
        self.assertIn("COUNTER_SHAPE_UNKNOWN", source)
        self.assertIn("THRESHOLD_UNKNOWN", source)
        self.assertNotIn("copyOf(18)", source)

        with tempfile.TemporaryDirectory(prefix="owner109-maturity-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(
                textwrap.dedent(
                    """
                    import com.omegas.prohub.autocal.NativeAutoCalMaturityTracker
                    import com.omegas.prohub.autocal.NativeAutoCalMaturityTracker.MaturityState

                    fun main() {
                        val tracker = NativeAutoCalMaturityTracker()

                        val missingThreshold = tracker.assess(
                            counters = IntArray(18) { 5 },
                            gasLowThreshold = null,
                            gasNormalThreshold = 3,
                            enabled = true,
                            observedAtElapsedMs = 1_000L,
                        )
                        check(missingThreshold.overallState == MaturityState.UNKNOWN)
                        check(missingThreshold.states.take(6).all { it == MaturityState.UNKNOWN })
                        check(missingThreshold.states.drop(6).all { it == MaturityState.TRUE })
                        check(missingThreshold.transitions.isEmpty())

                        val badShape = tracker.assess(
                            counters = IntArray(17) { 10 },
                            gasLowThreshold = 3,
                            gasNormalThreshold = 3,
                            enabled = true,
                            observedAtElapsedMs = 2_000L,
                        )
                        check(badShape.overallState == MaturityState.UNKNOWN)
                        check(badShape.knownBands == 0)
                        check(badShape.unknownReason == "COUNTER_SHAPE_UNKNOWN")

                        tracker.reset()
                        val baseline = tracker.assess(
                            counters = IntArray(18) { 2 },
                            gasLowThreshold = 3,
                            gasNormalThreshold = 3,
                            enabled = true,
                            observedAtElapsedMs = 3_000L,
                        )
                        check(baseline.overallState == MaturityState.FALSE)
                        check(baseline.transitions.isEmpty())

                        val crossed = IntArray(18) { 2 }
                        crossed[0] = 3
                        val second = tracker.assess(
                            counters = crossed,
                            gasLowThreshold = 3,
                            gasNormalThreshold = 3,
                            enabled = true,
                            observedAtElapsedMs = 4_000L,
                        )
                        check(second.overallState == MaturityState.FALSE)
                        check(second.matureBands == 1)
                        check(second.transitions.size == 1)
                        check(second.transitions.single().bandIndex == 0)

                        val allMature = tracker.assess(
                            counters = IntArray(18) { 3 },
                            gasLowThreshold = 3,
                            gasNormalThreshold = 3,
                            enabled = true,
                            observedAtElapsedMs = 5_000L,
                        )
                        check(allMature.overallState == MaturityState.TRUE)
                        check(allMature.knownBands == 18)
                        check(allMature.matureBands == 18)

                        println("OWNER_109_MATURITY_TRISTATE=PASS")
                    }
                    """
                ),
                encoding="utf-8",
            )
            jar = tmp / "owner109.jar"
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
            self.assertIn("OWNER_109_MATURITY_TRISTATE=PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
