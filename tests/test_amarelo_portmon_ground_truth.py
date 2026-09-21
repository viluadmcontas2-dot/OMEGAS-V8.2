import json
import unittest
from pathlib import Path

from tools.omegas_amarelo.portmon import (
    decode_object_request,
    parse_portmon_lines,
)

ROOT = Path(__file__).resolve().parents[1]
AUTOCAL = ROOT / "tests" / "fixtures" / "portmon-autocal-cycle-v1.json"
LOGNOVO_RAW = ROOT / "tests" / "fixtures" / "portmon-lognovo-enable-disable-v1.txt"
LOGNOVO_META = ROOT / "tests" / "fixtures" / "portmon-lognovo-enable-disable-v1.json"

AUTOCAL_SHA = "4a70f5ae79b1d688c05bd169f3e6a588b52105580d24b8a72a5cff398a384c0b"
LOGNOVO_SHA = "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64"


class AutoCalGroundTruthTest(unittest.TestCase):
    def test_existing_autocal_fixture_keeps_raw_provenance(self):
        fixture = json.loads(AUTOCAL.read_text(encoding="utf-8"))
        self.assertEqual(AUTOCAL_SHA, fixture["sourceRawSha256"])
        self.assertGreaterEqual(fixture["commandCounts"]["29 61 01 8B"], 700)
        self.assertGreaterEqual(fixture["commandCounts"]["09 74 01 7E"], 3)

    def test_lognovo_fixture_is_mandatory_and_byte_exact(self):
        self.assertTrue(LOGNOVO_RAW.is_file(), "LOGNOVO raw excerpt must be committed")
        self.assertTrue(LOGNOVO_META.is_file(), "LOGNOVO provenance manifest must be committed")
        meta = json.loads(LOGNOVO_META.read_text(encoding="utf-8"))
        self.assertEqual(LOGNOVO_SHA, meta["sourceRawSha256"])
        self.assertEqual("12 4A 01 01 5E", meta["enableRequest"])
        self.assertEqual("12 4A 01 00 5D", meta["disableRequest"])
        self.assertTrue(meta["sourceLineRanges"])

    def test_parser_reconstructs_enable_checkbox_write(self):
        tx = list(parse_portmon_lines(LOGNOVO_RAW.read_text(encoding="utf-8").splitlines()))
        requests = [item.request.hex(" ").upper() for item in tx]
        self.assertIn("12 4A 01 01 5E", requests)
        self.assertIn("12 4A 01 00 5D", requests)

    def test_enable_disable_decode_as_same_native_object(self):
        enable = decode_object_request(bytes.fromhex("12 4A 01 01 5E"))
        disable = decode_object_request(bytes.fromhex("12 4A 01 00 5D"))
        self.assertEqual(0x014A, enable.address)
        self.assertEqual(0x014A, disable.address)
        self.assertEqual(1, enable.value)
        self.assertEqual(0, disable.value)
        self.assertEqual("WRITE_U8", enable.kind)
        self.assertEqual("WRITE_U8", disable.kind)

    def test_no_separate_pause_command_is_required_by_contract(self):
        meta = json.loads(LOGNOVO_META.read_text(encoding="utf-8"))
        self.assertEqual("AUTO_CAL_ENABLE_0_FREEZES_ACQUISITION", meta["disableSemantic"])
        self.assertFalse(meta["separatePauseControlProven"])


if __name__ == "__main__":
    unittest.main()
