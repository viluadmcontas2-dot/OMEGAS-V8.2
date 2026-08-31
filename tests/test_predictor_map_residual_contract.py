import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADVISOR = ROOT / 'app/src/main/java/com/omegas/prohub/learning/AssistedCalibrationAdvisor.kt'
KWRITE = ROOT / 'app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt'
MAP_UI = ROOT / 'app/src/main/assets/ui/screens/map.js'
PREDICTOR = ROOT / 'app/src/main/java/com/omegas/prohub/learning/PredictorSurface.kt'


class PredictorMapResidualContract(unittest.TestCase):
    def setUp(self):
        self.advisor = ADVISOR.read_text('utf-8')
        self.kwrite = KWRITE.read_text('utf-8')
        self.map_ui = MAP_UI.read_text('utf-8')
        self.predictor = PREDICTOR.read_text('utf-8')

    def test_local_map_residual_is_after_global_removal(self):
        self.assertIn('sample.errorRatio - if (globalEstimate.available) globalEstimate.value else 0.0', self.advisor)
        self.assertIn('globalTrendRemoved', self.advisor)
        self.assertIn('mapResidualSuggestions', self.advisor)

    def test_official_writer_keeps_special_row_outside_manual_surface(self):
        self.assertIn('const val ROW_COUNT = KMapPhysicalAxes.WRITABLE_ROWS', self.kwrite)
        self.assertIn('const val EXTRA_ROW = KMapPhysicalAxes.WRITABLE_ROWS', self.kwrite)
        self.assertIn('cells.length() !in 1..16', self.kwrite)
        self.assertIn('readback', self.kwrite.lower())
        self.assertIn('Mp48WorkClass.MANUAL_WRITE', self.kwrite)
        self.assertIn('Mp48WorkClass.SAFETY', self.kwrite)

    def test_ui_keeps_0c_protected_and_manual_review_before_write(self):
        self.assertIn('Linha técnica 0C protegida', self.map_ui)
        self.assertIn('previewMapAdjustment', self.map_ui)
        self.assertIn('openReview()', self.map_ui)
        self.assertIn('writeReview()', self.map_ui)

    def test_predictor_cannot_become_map_writer(self):
        self.assertNotIn('KWriteManager', self.predictor)
        self.assertNotIn('MANUAL_WRITE', self.predictor)
        self.assertNotIn('protocolTransaction', self.predictor)
        self.assertIn('automaticWrite", false', self.predictor)


if __name__ == '__main__':
    unittest.main()
