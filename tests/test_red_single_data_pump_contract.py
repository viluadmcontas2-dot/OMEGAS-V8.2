from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/assets/ui"
NATIVE_API = (UI / "core/native-api.js").read_text(encoding="utf-8")
APP = (UI / "app.js").read_text(encoding="utf-8")


class RedSingleDataPumpContractTest(unittest.TestCase):
    def test_native_api_exposes_only_the_present_seam(self):
        self.assertIn("presentSnapshot()", NATIVE_API)
        self.assertIn("getPresentSnapshot", NATIVE_API)
        self.assertNotIn("getScienceSnapshotSince", NATIVE_API)

    def test_route_navigation_does_not_call_heavy_learning_api_directly(self):
        self.assertIn("api.presentSnapshot()", APP)
        self.assertNotIn("api.learning()", APP)


if __name__ == "__main__":
    unittest.main()
