import base64
import hashlib
import struct
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEXT = ROOT / "tests/fixtures/progbase-finish-text-51a390.bin.b64"
RTTI = ROOT / "tests/fixtures/progbase-autocaldm-field-rtti.bin.b64"

TEXT_SHA = "51976269a1d301304bf11773b1b9d610598dae600171ef141d56aca83ad36385"
RTTI_SHA = "5964e6ef6449fb665196200aafefc0453dd92f135e1609e662441cdeadabfa96"

class ProgBaseFinishRttiOracle(unittest.TestCase):
    @staticmethod
    def decode(path: Path, expected: str) -> bytes:
        raw = base64.b64decode(path.read_text("ascii"), validate=True)
        assert hashlib.sha256(raw).hexdigest() == expected
        return raw

    def test_exact_rtti_maps_finish_source_and_target(self):
        raw = self.decode(RTTI, RTTI_SHA)
        self.assertIn(b"VECT_AUTOCAL_U8_1" + struct.pack("<I", 0x7C), raw)
        self.assertIn(b"VECT_AUTOCAL_U8_2" + struct.pack("<I", 0x80), raw)
        self.assertIn(b"VECT_AUTOCAL_U8_0" + struct.pack("<I", 0xCC), raw)
        self.assertIn(b"NUM_ATUOMATCH_EXECUTED" + struct.pack("<I", 0xD0), raw)

    def test_finish_methods_copy_7c_to_cc_and_full_finish_sleeps_100ms(self):
        raw = self.decode(TEXT, TEXT_SHA)
        first_source = raw.find(bytes.fromhex("8B 46 7C"))
        first_target = raw.find(bytes.fromhex("8B 86 CC 00 00 00"))
        sleep100 = raw.find(bytes.fromhex("6A 64"), first_target)
        second_source = raw.find(bytes.fromhex("8B 43 7C"), first_target + 1)
        second_target = raw.find(bytes.fromhex("8B 83 CC 00 00 00"), second_source)
        self.assertGreaterEqual(first_source, 0)
        self.assertGreater(first_target, first_source)
        self.assertGreater(sleep100, first_target)
        self.assertGreater(second_source, sleep100)
        self.assertGreater(second_target, second_source)

    def test_refuted_counter_finish_cannot_reenter_product_contract(self):
        protocol = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt").read_text("utf-8")
        manager = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text("utf-8")
        self.assertIn("finishAutoCalCommit(valueFromRow1: Int)", protocol)
        self.assertIn("VECT_AUTOCAL_U8_0.address", protocol)
        finish = manager.split("private fun executeFinish", 1)[1].split("private fun executeResetKFactor", 1)[0]
        self.assertIn("AutoCalProtocol.VECT_AUTOCAL_U8_1", finish)
        self.assertIn("AutoCalProtocol.VECT_AUTOCAL_U8_0", finish)
        self.assertNotIn("AutoCalProtocol.MAX_AUTOMATCH", finish)
        self.assertNotIn("AutoCalProtocol.NUM_AUTOMATCH_EXECUTED", finish)

if __name__ == "__main__":
    unittest.main()
