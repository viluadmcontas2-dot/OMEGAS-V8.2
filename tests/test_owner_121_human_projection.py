import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECTION = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalHumanProjection.kt"
PROGRESSION = ROOT / "app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalProgression.kt"

class Owner121HumanProjection(unittest.TestCase):
    def test_projection_keeps_18_and_30_roles_separate(self):
        projection = PROJECTION.read_text("utf-8")
        progression = PROGRESSION.read_text("utf-8")
        self.assertIn('xAxis: String = "TPET_MS"', projection)
        self.assertIn('yAxis: String = "MAP_BAR"', projection)
        self.assertIn('"Adquirindo gasolina"', projection)
        self.assertIn('"Adquirindo GNV"', projection)
        self.assertIn('"AutoMatch alterou calibração — revalidando"', projection)
        self.assertIn('"Estado nativo insuficiente"', projection)
        self.assertIn('reference30Role: String = "SEPARATE_CURVE_REFERENCE_OVERLAY_ONLY"', projection)
        self.assertIn('curveKSeparateSurface: Boolean = true', projection)
        self.assertIn('const val ACQUISITION_BANDS = 18', progression)
        self.assertIn('const val REFERENCE_POINTS = 30', progression)
        self.assertNotIn('rpm = band.index', projection)
        self.assertNotIn('rpm:', projection)

if __name__ == "__main__":
    unittest.main()
