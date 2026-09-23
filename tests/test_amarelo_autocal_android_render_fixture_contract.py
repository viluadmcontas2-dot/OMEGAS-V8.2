import json
from pathlib import Path
import unittest

FIXTURE = Path("tests/fixtures/amarelo-autocal-render-original-v1.json")
REPLAY_FIXTURE = Path("tests/fixtures/portmon-lognovo-replay-v1.json")
ANDROID_TEST = Path("app/src/androidTest/java/com/omegas/prohub/AmareloAutoCalRenderTest.kt")
RUNTIME_BRIDGE_TEST = Path("app/src/androidTest/java/com/omegas/prohub/AmareloAutoCalRuntimeBridgeTest.kt")

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


    def test_canonical_replay_runtime_bridge_test_has_no_projection_stub(self):
        replay = json.loads(REPLAY_FIXTURE.read_text(encoding="utf-8"))
        self.assertEqual(
            replay["sourceRawSha256"],
            "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64",
        )
        requests = {row["request"] for row in replay["transactions"]}
        for request in ("48 0B 53", "48 01 49", "29 4B 01 75", "29 61 01 8B", "29 8D 01 B7", "29 8E 01 B8"):
            self.assertIn(request, requests)

        android = RUNTIME_BRIDGE_TEST.read_text(encoding="utf-8")
        self.assertIn('open("portmon-lognovo-replay-v1.json")', android)
        self.assertIn("CanonicalReplayScheduler", android)
        self.assertIn("NativeAutoCalMonitor(", android)
        self.assertIn('setPrivateField(service, "nativeAutoCal", monitor)', android)
        self.assertIn("window.OmegasAutoCal?.getUiProjection", android)
        self.assertIn("const projection = api.projection();", android)
        self.assertNotIn("api.projection = () =>", android)
        self.assertNotIn('setPrivateField(service.nativeAutoCal, "latestSnapshot"', android)
        self.assertNotIn("service.nativeAutoCal = monitor", android)

    def test_amarelo_debug_apk_has_separate_identity_from_verde(self):
        config = json.loads(Path("config/omegas-release.json").read_text(encoding="utf-8"))
        self.assertEqual(config["applicationId"], "com.omegas.v7.test")
        self.assertEqual(config["appLabel"], "OMEGAS AMARELO TEST")
        gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('applicationIdSuffix = ".amarelo"', gradle)
        render = Path("tools/ci/run_amarelo_android_render_evidence.sh").read_text(encoding="utf-8")
        self.assertIn("com.omegas.v7.test.amarelo.test/androidx.test.runner.AndroidJUnitRunner", render)
        self.assertIn("/sdcard/Android/data/com.omegas.v7.test.amarelo/files/omegas-evidence/", render)
        self.assertNotIn("pm grant com.omegas.v7.test android.permission", render)


if __name__ == "__main__":
    unittest.main()
