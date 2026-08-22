from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/assets/ui"
NATIVE_API = (UI / "core/native-api.js").read_text(encoding="utf-8")
APP = (UI / "app.js").read_text(encoding="utf-8")
PREDICTOR = (UI / "screens/predictor.js").read_text(encoding="utf-8")
CURRENT_CELL = (UI / "components/predictor-current-cell.js").read_text(encoding="utf-8")


class RedSingleDataPumpContractTest(unittest.TestCase):
    def test_native_api_exposes_snapshot_seams(self):
        self.assertIn("presentSnapshot()", NATIVE_API)
        self.assertIn("scienceSnapshotSince(revision)", NATIVE_API)
        self.assertIn("getPresentSnapshot", NATIVE_API)
        self.assertIn("getScienceSnapshotSince", NATIVE_API)

    def test_app_scheduler_is_the_only_snapshot_pump(self):
        self.assertIn("api.presentSnapshot()", APP)
        self.assertIn("api.scienceSnapshotSince", APP)
        for source in (PREDICTOR, CURRENT_CELL):
            self.assertNotIn("presentSnapshot()", source)
            self.assertNotIn("scienceSnapshotSince", source)

    def test_predictor_does_not_poll_v7_state_or_add_context_timer(self):
        self.assertNotIn("this.api.v7.getState()", PREDICTOR)
        self.assertNotIn("scheduler.addHook('context'", PREDICTOR)
        self.assertIn("state.calibrationState", PREDICTOR)

    def test_current_cell_renders_store_telemetry_without_fast_polling(self):
        self.assertNotIn("this.api.telemetry()", CURRENT_CELL)
        self.assertNotIn("scheduler.addHook('fast'", CURRENT_CELL)
        self.assertIn("state.telemetry", CURRENT_CELL)

    def test_route_navigation_does_not_call_heavy_learning_api_directly(self):
        self.assertNotIn("api.learning()", APP)
        self.assertNotIn("api.v7.getState()", APP)


if __name__ == "__main__":
    unittest.main()
