import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FLOW = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeCalibrationFlow.kt"


class Owner105NativeFlowContract(unittest.TestCase):
    def test_three_native_flows_are_typed_and_cross_flow_transition_fails_closed(self):
        source = FLOW.read_text("utf-8")
        self.assertIn("AUTOCAL_ACQUISITION", source)
        self.assertIn("AUTOMATCH", source)
        self.assertIn("AUTOMATIC_ECU_CALIBRATION_STATIONARY", source)
        self.assertIn("require(from.flow == to.flow)", source)

        with tempfile.TemporaryDirectory(prefix="owner105-native-flow-") as tmp:
            tmp = Path(tmp)
            main = tmp / "Main.kt"
            main.write_text(
                textwrap.dedent(
                    """
                    import com.omegas.prohub.autocal.*

                    fun main() {
                        NativeCalibrationFlowGuard.requireSameFlow(
                            AutoCalAcquisitionFlowState.IDLE,
                            AutoCalAcquisitionFlowState.ENABLED,
                        )
                        var blocked = false
                        try {
                            NativeCalibrationFlowGuard.requireSameFlow(
                                AutoCalAcquisitionFlowState.ENABLED,
                                AutoMatchFlowState.OBSERVED,
                            )
                        } catch (_: IllegalArgumentException) {
                            blocked = true
                        }
                        check(blocked)
                        check(AutoMatchFlowState.OBSERVED.flow == NativeCalibrationFlow.AUTOMATCH)
                        check(
                            StationaryCalibrationFlowState.OBSERVED.flow ==
                                NativeCalibrationFlow.AUTOMATIC_ECU_CALIBRATION_STATIONARY
                        )
                        println("OWNER_105_NATIVE_FLOW=PASS")
                    }
                    """
                ),
                encoding="utf-8",
            )
            jar = tmp / "owner105.jar"
            subprocess.run(
                ["kotlinc", str(FLOW), str(main), "-include-runtime", "-d", str(jar)],
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
            self.assertIn("OWNER_105_NATIVE_FLOW=PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
