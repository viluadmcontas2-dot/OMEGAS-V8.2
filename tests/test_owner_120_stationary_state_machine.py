import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/omegas/prohub/autocal/StationaryCalibrationStateMachine.kt"

class Owner120StationaryStateMachine(unittest.TestCase):
    def test_unknown_active_complete_and_failure_paths(self):
        with tempfile.TemporaryDirectory(prefix="owner120-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(textwrap.dedent('''
                import com.omegas.prohub.autocal.StationaryCalibrationStateMachine as S
                fun main() {
                    val incomplete = S.Preconditions(false, true, true, true)
                    check(S.evaluate(incomplete, null, null, null, false).state == S.State.PRECONDITIONS_UNKNOWN)
                    val ready = S.Preconditions(true, true, true, true)
                    val active = S.evaluate(ready, true, false, false, false)
                    check(active.state == S.State.ECU_ACTIVE && !active.algorithmKnown && !active.appAutomaticWrite)
                    val completedUnknownOutput = S.evaluate(ready, false, true, false, false)
                    check(completedUnknownOutput.state == S.State.COMPLETED_OBSERVED)
                    check(completedUnknownOutput.mutationScope == "UNKNOWN")
                    check(completedUnknownOutput.recovery == "READ_OUTPUT_BEFORE_PROMOTION")
                    val failed = S.evaluate(ready, false, false, true, false)
                    check(failed.state == S.State.FAILED_OBSERVED)
                    check(failed.recovery == "RECONCILE_CALIBRATION_IDENTITY")
                    println("OWNER_120_STATIONARY_STATE=PASS")
                }
            '''), encoding="utf-8")
            jar = tmp / "owner120.jar"
            subprocess.run(["/root/.sdkman/candidates/kotlin/current/bin/kotlinc", str(TARGET), str(main), "-include-runtime", "-d", str(jar)], check=True, capture_output=True, text=True, timeout=30)
            result = subprocess.run(["java", "-jar", str(jar)], check=True, capture_output=True, text=True, timeout=10)
            self.assertIn("OWNER_120_STATIONARY_STATE=PASS", result.stdout)

if __name__ == "__main__":
    unittest.main()
