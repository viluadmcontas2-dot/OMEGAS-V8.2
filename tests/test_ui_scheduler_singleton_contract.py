from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/assets/ui/app.js"
SCHEDULER = ROOT / "app/src/main/assets/ui/core/scheduler.js"
PREDICTOR = ROOT / "app/src/main/assets/ui/core/predictor-model.js"


class UiSchedulerSingletonContract(unittest.TestCase):
    def test_shell_creates_exactly_one_scheduler(self):
        source = APP.read_text("utf-8")
        self.assertEqual(1, source.count("new ui.Scheduler("))
        self.assertNotIn("new ui.Scheduler(", PREDICTOR.read_text("utf-8"))

    def test_start_stop_and_hooks_are_idempotent_and_removable(self):
        source = SCHEDULER.read_text("utf-8")
        self.assertIn("if (this.timer) return", source)
        self.assertIn("root.clearInterval(this.timer)", source)
        self.assertIn("this.timer = null", source)
        self.assertIn("fast: new Set()", source)
        self.assertIn("status: new Set()", source)
        self.assertIn("context: new Set()", source)
        self.assertIn("return () => set.delete(listener)", source)

    def test_predictor_has_no_visual_tick_clock(self):
        source = PREDICTOR.read_text("utf-8")
        for forbidden in ("setInterval", "requestAnimationFrame", "addHook(", "Scheduler"):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
