import hashlib
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = ROOT / "config/mp48-k-map-physical-axes.lock.json"
AXES_PATH = ROOT / "app/src/main/java/com/omegas/prohub/calibration/KMapPhysicalAxes.kt"
PROTOCOL_PATH = ROOT / "app/src/main/java/com/omegas/prohub/ecu/KMapGeometryProtocol.kt"
GEOMETRY_PATH = ROOT / "app/src/main/java/com/omegas/prohub/calibration/KMapGeometry.kt"


class Mp48KMapPhysicalAxesContract(unittest.TestCase):
    def setUp(self):
        self.lock = json.loads(LOCK_PATH.read_text("utf-8"))
        self.axes = AXES_PATH.read_text("utf-8")
        self.protocol = PROTOCOL_PATH.read_text("utf-8")
        self.geometry = GEOMETRY_PATH.read_text("utf-8")

    def canonical(self):
        rpm = ",".join(str(value) for value in self.lock["rpmBins"])
        petrol = ",".join(f"{float(value):.1f}" for value in self.lock["petrolMsBins"])
        return (
            f"schema={self.lock['schema']};address={self.lock['address']};"
            f"writableRows={self.lock['writableRows']};protocolRows={self.lock['protocolRows']};"
            f"columns={self.lock['columns']};specialRow={self.lock['specialRow']};"
            f"rpmBins={rpm};petrolMsBins={petrol}"
        )

    def kotlin_array(self, name, caster):
        match = re.search(rf"{name}\s*=\s*(?:int|double)ArrayOf\((.*?)\n\s*\)", self.axes, re.S)
        self.assertIsNotNone(match, f"Array Kotlin ausente: {name}")
        return [caster(value.strip()) for value in match.group(1).split(",") if value.strip()]

    def test_lock_is_historical_fixture_not_runtime_authority(self):
        self.assertEqual("HISTORICAL_FIXTURE_ONLY", self.lock["status"])
        self.assertFalse(self.lock["runtimeAuthority"])
        digest = hashlib.sha256(self.canonical().encode("utf-8")).hexdigest()
        self.assertEqual(self.lock["sha256"], digest)
        self.assertEqual(self.lock["rpmBins"], self.kotlin_array("RPM", int))
        self.assertEqual(self.lock["petrolMsBins"], self.kotlin_array("PETROL_MS", float))
        self.assertIn(f'const val LOCK_SHA256 = "{digest}"', self.axes)
        self.assertIn("const val RUNTIME_AUTHORITY = false", self.axes)
        self.assertIn('TEMPI_PER_K (0x0037)', self.axes)
        self.assertIn('GIRI_PER_K (0x003D)', self.axes)

    def test_native_geometry_contract_uses_e5_addresses_and_strict_shape(self):
        self.assertIn("TIME_AXIS_ADDRESS = 0x0037", self.protocol)
        self.assertIn("RPM_AXIS_ADDRESS = 0x003D", self.protocol)
        self.assertIn("PAYLOAD_SIZE = POINT_COUNT * BYTES_PER_POINT", self.protocol)
        self.assertIn("payload.size == PAYLOAD_SIZE", self.protocol)
        self.assertIn("TIME_MS_PER_COUNT = 0.00256", self.protocol)
        self.assertNotIn("0x000C", self.protocol + self.geometry)
        self.assertIn("serial.unit(", self.geometry)
        self.assertIn("expectedSessionId = expectedSessionId", self.geometry)
        self.assertIn("runtimeAuthority\", true", self.geometry)

    def test_ui_consumes_axes_from_map_payload_without_js_hardcode(self):
        editor = (ROOT / "app/src/main/assets/ui/map-editor.js").read_text("utf-8")
        screen = (ROOT / "app/src/main/assets/ui/screens/map.js").read_text("utf-8")
        self.assertIn("payload.axes", editor)
        self.assertIn("this.editor.load(result)", screen)
        self.assertIn("snapshot.axes.petrolBins", screen)
        self.assertIn("snapshot.axes.rpmBins", screen)
        self.assertNotIn("const RPM_BINS", editor + screen)
        self.assertNotIn("const INJ_BINS", editor + screen)


if __name__ == "__main__":
    unittest.main()
