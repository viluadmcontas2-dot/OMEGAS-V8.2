from pathlib import Path
import unittest


class CausalStepWiringContractTest(unittest.TestCase):
    def test_coordinator_passes_material_causal_transitions_to_adapter(self):
        source = Path(
            "app/src/main/java/com/omegas/prohub/calibration/V7CalibrationCoordinator.kt"
        ).read_text(encoding="utf-8")

        self.assertIn("calibrationTransitions", source)
        self.assertIn("causalTransitions = active.state.calibrationTransitions", source)


if __name__ == "__main__":
    unittest.main()
