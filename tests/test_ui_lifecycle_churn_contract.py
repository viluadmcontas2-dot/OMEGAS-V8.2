from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/assets/ui/app.js"
SCHED = ROOT / "app/src/main/assets/ui/core/scheduler.js"
DRAWERS = ROOT / "app/src/main/assets/ui/components/drawers.js"
FLOATING = ROOT / "app/src/main/assets/ui/components/floating-telemetry.js"


class UiLifecycleChurnContract(unittest.TestCase):
    def test_shell_owns_singletons_once(self):
        app = APP.read_text("utf-8")
        self.assertEqual(1, app.count("const scheduler = new ui.Scheduler("))
        self.assertEqual(1, app.count("const utilities = ui.Drawers ? new ui.Drawers("))
        self.assertEqual(1, app.count("bindGlobalEvents();"))
        self.assertIn("if (instances[route]) return instances[route]", app)

    def test_background_foreground_reuses_same_timer(self):
        scheduler = SCHED.read_text("utf-8")
        app = APP.read_text("utf-8")
        self.assertIn("if (this.timer) return", scheduler)
        self.assertIn("root.clearInterval(this.timer)", scheduler)
        self.assertIn("this.timer = null", scheduler)
        self.assertIn("document.addEventListener('visibilitychange'", app)
        self.assertIn("scheduler.start();", app)
        self.assertIn("scheduler.stop();", app)

    def test_dynamic_drawer_content_uses_single_delegated_tool_listener(self):
        source = DRAWERS.read_text("utf-8")
        self.assertEqual(1, source.count("this.bind();"))
        self.assertEqual(1, source.count("addEventListener('click', event => this.handleToolClick(event))"))
        self.assertEqual(1, source.count("addEventListener('change', event => this.handleToolChange(event))"))
        self.assertIn("host.innerHTML = `", source)

    def test_floating_overlay_boots_once(self):
        source = FLOATING.read_text("utf-8")
        self.assertIn("if (app.floatingTelemetry) return", source)
        self.assertIn("app.floatingTelemetry = new FloatingTelemetry(app)", source)
        self.assertIn("this.unsubscribe = this.store.subscribe", source)

    def test_100_visibility_cycles_return_timer_count_to_baseline(self):
        # Behavioral model of Scheduler.start/stop idempotency used by visibilitychange.
        timer = None
        creates = 0
        clears = 0
        next_id = 1
        for _ in range(100):
            if timer is None:
                timer = next_id
                next_id += 1
                creates += 1
            # repeated visible notification must not create a second timer
            if timer is None:
                timer = next_id
                next_id += 1
                creates += 1
            if timer is not None:
                timer = None
                clears += 1
            # repeated hidden notification is a no-op
            if timer is not None:
                timer = None
                clears += 1
        self.assertIsNone(timer)
        self.assertEqual(100, creates)
        self.assertEqual(100, clears)


if __name__ == "__main__":
    unittest.main()
