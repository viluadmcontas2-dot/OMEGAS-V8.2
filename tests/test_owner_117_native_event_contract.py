import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORRELATOR = ROOT / "app/src/main/java/com/omegas/prohub/learning/NativeAutoCalEventCorrelator.kt"

class Owner117NativeEventContract(unittest.TestCase):
    def test_contract_is_fuel_typed_and_fail_closed(self):
        source = CORRELATOR.read_text("utf-8")
        self.assertIn("enum class SourceFuel { PETROL, CNG }", source)
        self.assertIn('state = "INCONCLUSIVE"', source)
        self.assertIn("rpm = null", source)
        self.assertIn("overlapKey", source)
        self.assertIn("windowFromElapsedMs", source)
        self.assertIn("windowToElapsedMs", source)
        self.assertIn("firstSequence", source)
        self.assertIn("lastSequence", source)
        self.assertIn("canCloseWindowEarly", source)
        self.assertNotIn("bandIndex", source)

if __name__ == "__main__":
    unittest.main()
