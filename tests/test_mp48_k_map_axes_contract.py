import hashlib
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = ROOT / "config/mp48-k-map-physical-axes.lock.json"
AXES_PATH = ROOT / "app/src/main/java/com/omegas/prohub/calibration/KMapPhysicalAxes.kt"


class Mp48KMapPhysicalAxesContract(unittest.TestCase):
    def setUp(self):
        self.lock = json.loads(LOCK_PATH.read_text("utf-8"))
        self.axes = AXES_PATH.read_text("utf-8")

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

    def test_lock_and_runtime_authority_match(self):
        self.assertEqual("IMMUTABLE_PHYSICAL_CONTRACT", self.lock["status"])
        digest = hashlib.sha256(self.canonical().encode("utf-8")).hexdigest()
        self.assertEqual(self.lock["sha256"], digest)
        self.assertEqual(self.lock["rpmBins"], self.kotlin_array("RPM", int))
        self.assertEqual(self.lock["petrolMsBins"], self.kotlin_array("PETROL_MS", float))
        self.assertIn(f'const val LOCK_SHA256 = "{digest}"', self.axes)

    def test_ui_consumes_axes_returned_by_ecu_read(self):
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
