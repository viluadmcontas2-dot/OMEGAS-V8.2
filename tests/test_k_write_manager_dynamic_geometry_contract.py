import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt"


class KWriteManagerDynamicGeometryContract(unittest.TestCase):
    def setUp(self):
        self.source = MANAGER.read_text("utf-8")

    def test_full_map_uses_current_session_geometry_not_historical_fixture(self):
        self.assertNotIn("KMapPhysicalAxes", self.source)
        self.assertIn("MapGeometryReader(serial)", self.source)
        self.assertIn("geometryReader.readRaw(expectedSessionId)", self.source)
        self.assertIn("MapGeometrySnapshot.create(", self.source)
        self.assertIn("Mp48GeometryCodec.timeAxisMs", self.source)
        self.assertIn('put("fingerprint", snapshot.fingerprint())', self.source)
        self.assertIn('put("sessionId", snapshot.usbSessionId)', self.source)
        self.assertIn('put("petrolBins", JSONArray(snapshot.timeAxisMs))', self.source)
        self.assertIn('put("rpmBins", JSONArray(snapshot.rpmAxisRaw))', self.source)

    def test_batch_preserves_axes_from_confirmed_cache(self):
        self.assertRegex(self.source, r'val\s+axes\s*=\s*cache\.optJSONObject\("axes"\)')
        self.assertIn('axes.optLong("sessionId", -1L) != expectedSessionId', self.source)
        self.assertIn('axes.optString("completeness") != MapGeometryCompleteness.KNOWN.name', self.source)
        self.assertIn('put("axisFingerprint", axes.getString("fingerprint"))', self.source)
        self.assertIn('put("axes", JSONObject(axes.toString()))', self.source)

    def test_dimensions_come_from_protocol_not_fixture_object(self):
        self.assertIn("const val COLUMN_COUNT = Mp48Protocol.MAP_COLUMNS", self.source)
        self.assertIn("const val TOTAL_ROW_COUNT = Mp48Protocol.MAP_ROWS", self.source)
        self.assertNotRegex(self.source, r"ROW_COUNT\s*=\s*KMapPhysicalAxes")


if __name__ == "__main__":
    unittest.main()
