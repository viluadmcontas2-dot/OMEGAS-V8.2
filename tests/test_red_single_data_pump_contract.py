from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/assets/ui"
NATIVE_API = (UI / "core/native-api.js").read_text(encoding="utf-8")
APP = (UI / "app.js").read_text(encoding="utf-8")


class RedSingleDataPumpContractTest(unittest.TestCase):
    def test_native_api_exposes_snapshot_seams(self):
        self.assertIn("presentSnapshot()", NATIVE_API)
        self.assertIn("scienceSnapshotSince(revision)", NATIVE_API)
        self.assertIn("getPresentSnapshot", NATIVE_API)
        self.assertIn("getScienceSnapshotSince", NATIVE_API)

    def test_app_scheduler_is_the_only_snapshot_pump(self):
        self.assertIn("api.presentSnapshot()", APP)
        self.assertIn("api.scienceSnapshotSince", APP)

    def test_route_navigation_does_not_call_heavy_learning_api_directly(self):
        self.assertNotIn("api.learning()", APP)
        self.assertNotIn("api.v7.getState()", APP)


if __name__ == "__main__":
    unittest.main()
