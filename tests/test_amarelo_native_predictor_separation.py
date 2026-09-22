from pathlib import Path
import unittest

PROJECTION = Path("app/src/main/java/com/omegas/prohub/autocal/AutoCalUiProjection.kt")
PREDICTOR = Path("app/src/main/java/com/omegas/prohub/autocal/AutoMatchSnapshotAnalysis.kt")


class NativeAutoCalPredictorSeparationTest(unittest.TestCase):
    def test_native_projection_does_not_execute_inferred_predictor(self):
        source = PROJECTION.read_text(encoding="utf-8")
        self.assertNotIn("AutoMatchSnapshotAnalysis", source)
        self.assertIn('"mode", "NATIVE_AUTOCAL_ONLY"', source)
        self.assertIn('"inferredPredictorAttached", false', source)

    def test_predictor_stays_available_as_separate_module(self):
        source = PREDICTOR.read_text(encoding="utf-8")
        self.assertIn("object AutoMatchSnapshotAnalysis", source)
        self.assertIn('"AUTOMATCH_INFERIDO_V2"', source)
        self.assertIn('"nativeFirmwareExact", false', source)
        self.assertIn('"manualOnly", true', source)


if __name__ == "__main__":
    unittest.main()
