import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-render-original-v1.json")

EXPECTED_KEYS = {
    "PETR_INJ_TBP",
    "MNFLD_PRESS_THD",
    "NUM_BUF_UPD_PETR",
    "NUM_BUF_UPD_GAS",
    "PETR_INJ_TBUF_GAS_PREV",
    "MNFLD_PRESS_BUF_GAS_PREV",
    "PETR_INJ_TBUF_GAS",
    "MNFLD_PRESS_BUF_GAS",
    "MUL_ACT",
    "PETR_INJ_TBUF",
    "MNFLD_PRESS_BUF",
    "VECT_AUTOCAL_U8_1",
    "MAX_AUTOMATCH",
    "ACQUIRED_ZONES_PETROL",
    "ACQUIRED_ZONES_GAS",
    "CALIBRATION_VAL_1",
    "NUM_AUTOMATCH_EXECUTED",
    "PETR_MNFLD_PRESS_RV",
    "GAS_MNFLD_PRESS_RV",
}


class AutoCalAndroidRenderFixtureContractTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_fixture_is_explicitly_render_only_and_original_derived(self):
        self.assertEqual(
            self.data["schema"],
            "omegas.amarelo.android-render-composite-original-derived.v1",
        )
        self.assertEqual(
            self.data["classification"],
            "COMPOSITE_ORIGINAL_DERIVED_FOR_RENDER_ONLY",
        )
        provenance = self.data["provenance"]
        self.assertEqual(
            provenance["sourceRawSha256"],
            "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64",
        )
        self.assertEqual(provenance["targetPortmonIndex"], 824550)
        self.assertIn("not simultaneous scientific inference", provenance["coherenceWarning"])

    def test_fixture_keeps_exact_original_wire_transactions(self):
        rows = self.data["fields"]
        self.assertEqual({row["key"] for row in rows}, EXPECTED_KEYS)
        self.assertEqual(len(rows), 19)
        for row in rows:
            request = bytes.fromhex(row["request"])
            response = bytes.fromhex(row["response"])
            self.assertTrue(response.startswith(request), row["key"])
            self.assertGreaterEqual(len(response), len(request) + 3, row["key"])
        live = self.data["live"]
        self.assertEqual(live["request"], "48 01 49")
        self.assertTrue(bytes.fromhex(live["response"]).startswith(bytes.fromhex(live["request"])))

    def test_fixture_is_visually_rich_without_becoming_science_oracle(self):
        by_key = {row["key"]: row for row in self.data["fields"]}

        def payload(row):
            request = bytes.fromhex(row["request"])
            response = bytes.fromhex(row["response"])
            size = response[len(request) + 1]
            start = len(request) + 2
            return response[start:start + size]

        gas_zones = list(payload(by_key["ACQUIRED_ZONES_GAS"]))
        self.assertEqual(gas_zones, [0, 0, 1, 1])

        current = payload(by_key["PETR_INJ_TBUF_GAS"])
        previous = payload(by_key["PETR_INJ_TBUF_GAS_PREV"])
        petrol_ref = payload(by_key["PETR_MNFLD_PRESS_RV"])
        gas_ref = payload(by_key["GAS_MNFLD_PRESS_RV"])

        def non_zero_u16(raw):
            return sum(
                1 for index in range(0, len(raw), 2)
                if raw[index] != 0 or raw[index + 1] != 0
            )

        self.assertGreater(non_zero_u16(current), 0)
        self.assertGreater(non_zero_u16(previous), 0)
        self.assertEqual(non_zero_u16(petrol_ref), 30)
        self.assertEqual(non_zero_u16(gas_ref), 30)


if __name__ == "__main__":
    unittest.main()
