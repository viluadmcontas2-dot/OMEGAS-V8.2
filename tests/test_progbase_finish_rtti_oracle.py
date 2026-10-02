import base64
import hashlib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FINISH = ROOT / "tests/fixtures/progbase-finish-text-51a390.bin.b64"
DFM = ROOT / "tests/fixtures/progbase-tautocaldm-dfm.bin.b64"
GRID = ROOT / "tests/fixtures/progbase-autocal-grid-510df8.bin.b64"

FINISH_SHA = "51976269a1d301304bf11773b1b9d610598dae600171ef141d56aca83ad36385"
DFM_SHA = "96ba3d934bf66593f919e22e37e2a90a171be5c765530e39d484ecfc16c1ee86"
GRID_SHA = "350acefdcc01748c9bd28c027fa3df160d070be2ba9b820b0f4b343981eeff7a"

COMPONENTS = [
    "MNFLD_PRESS_THD",
    "PETR_INJ_TBP",
    "AUTO_CAL_ENABLE",
    "VECT_AUTOCAL_U8_1",
    "VECT_AUTOCAL_U8_2",
    "EN_CDN_T_THD",
    "PETR_INJ_TBUF",
    "MNFLD_PRESS_BUF",
    "NUM_BUF_UPD_PETR",
    "PETR_INJ_TBUF_GAS_PREV",
    "MNFLD_PRESS_BUF_GAS_PREV",
    "PETR_INJ_TBUF_GAS",
    "NUM_BUF_UPD_GAS",
    "MUL_ACT",
    "MNFLD_PRESS_BUF_GAS",
    "LIMIT_PRESSURE_MIN",
    "GAS_POINT_2DELETE",
    "PETROL_POINT_2DELETE",
    "LIMIT_PRESSURE_MAX",
    "ACQUIRED_ZONES_PETROL",
    "ACQUIRED_ZONES_GAS",
    "CALIBRATION_VAL_1",
    "MODULE_VERSION",
    "VECT_AUTOCAL_U8_0",
    "NUM_ATUOMATCH_EXECUTED",
]

class ProgBaseFinishOracle(unittest.TestCase):
    @staticmethod
    def decode(path: Path, expected: str) -> bytes:
        raw = base64.b64decode(path.read_text("ascii"), validate=True)
        assert hashlib.sha256(raw).hexdigest() == expected
        return raw

    def test_dfm_component_order_is_exact(self):
        raw = self.decode(DFM, DFM_SHA)
        positions = []
        start = 0
        for name in COMPONENTS:
            pos = raw.find(name.encode("ascii"), start)
            self.assertGreaterEqual(pos, 0, name)
            positions.append(pos)
            start = pos + len(name)
        self.assertEqual(positions, sorted(positions))

    def test_grid_anchors_contiguous_component_layout(self):
        raw = self.decode(GRID, GRID_SHA)
        # First DFM component is accessed at +0x6C and initialized as the
        # 18-point 0.2..2.0 MAP reference family.
        self.assertGreaterEqual(raw.count(bytes.fromhex("8B 43 6C")), 18)
        # Second DFM component is the adjacent +0x70 vector.
        self.assertGreaterEqual(raw.count(bytes.fromhex("8B 43 70")), 18)
        # The 23rd component (MODULE_VERSION, index 22) lands at
        # 0x6C + 22*4 = 0xC4 and is compared with mode/version value 4.
        self.assertIn(bytes.fromhex("8B 83 C4 00 00 00"), raw)
        self.assertIn(bytes.fromhex("83 F8 04"), raw)

    def test_finish_offsets_resolve_to_max_automatch_and_counter(self):
        # With the exact DFM order anchored at +0x6C and +0xC4,
        # four-byte component pointers resolve these two Finish offsets.
        base = 0x6C
        offsets = {name: base + index * 4 for index, name in enumerate(COMPONENTS)}
        self.assertEqual(0x7C, offsets["VECT_AUTOCAL_U8_2"])
        self.assertEqual(0xCC, offsets["NUM_ATUOMATCH_EXECUTED"])
        self.assertEqual(0xC8, offsets["VECT_AUTOCAL_U8_0"])

        raw = self.decode(FINISH, FINISH_SHA)
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

    def test_product_contract_uses_max_to_counter_only(self):
        protocol = (ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt").read_text("utf-8")
        manager = (ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt").read_text("utf-8")
        self.assertIn("finishAutoCalCommit(maxAutomatch: Int, counterWidthBytes: Int)", protocol)
        self.assertIn("NUM_AUTOMATCH_EXECUTED.address", protocol)
        finish = manager.split("private fun executeFinish", 1)[1].split("private fun executeResetKFactor", 1)[0]
        self.assertIn("AutoCalProtocol.MAX_AUTOMATCH", finish)
        self.assertIn("AutoCalProtocol.NUM_AUTOMATCH_EXECUTED", finish)
        self.assertNotIn("AutoCalProtocol.VECT_AUTOCAL_U8_0", finish)
        self.assertNotIn("AutoCalProtocol.VECT_AUTOCAL_U8_1", finish)

if __name__ == "__main__":
    unittest.main()
