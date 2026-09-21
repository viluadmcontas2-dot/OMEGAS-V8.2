import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-enable-automatch-coupling-v1.json")


class AutoCalEnableAutoMatchCouplingEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_progbase_binds_single_enable_control(self):
        resource = self.data["progbase_resource"]
        self.assertEqual(resource["control"], "CheckAutoCalEnable")
        self.assertEqual(resource["value_binding"], "AutoCalDM.AUTO_CAL_ENABLE")
        self.assertEqual(resource["status"], "PROVEN_RESOURCE")

    def test_manual_automatch_is_separate_action(self):
        manual = self.data["manual_automatch"]
        self.assertTrue(manual["separate_from_enable_control"])
        self.assertEqual(manual["action_code"], 8)
        self.assertEqual(manual["handler"], "ActionAutoMatchExecute")

    def test_lognovo_enable_disable_has_no_manual_action8_write(self):
        raw = self.data["raw_lognovo"]
        self.assertEqual(raw["enable_writes"], 4)
        self.assertEqual(raw["disable_writes"], 4)
        self.assertEqual(raw["manual_action8_writes"], 0)
        conclusions = self.data["conclusions"]
        self.assertEqual(conclusions["auto_cal_enable_is_single_host_mode_control"], "PROVEN")
        self.assertEqual(conclusions["host_sends_separate_manual_automatch_when_enabling"], "FALSIFIED_IN_LOGNOVO")
        self.assertEqual(conclusions["automatic_automatch_requires_second_ui_enable"], "FALSIFIED_PRODUCT_MODEL")


if __name__ == "__main__":
    unittest.main()
