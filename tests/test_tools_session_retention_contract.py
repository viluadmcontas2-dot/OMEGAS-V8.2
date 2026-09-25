from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
DRAWERS = (ROOT / "app/src/main/assets/ui/components/drawers.js").read_text()
RECORDER = (ROOT / "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt").read_text()


class ToolsSessionRetentionContractTest(unittest.TestCase):
    def test_log_policy_button_refreshes_state_without_closing_panel(self):
        self.assertIn("settingsOpenBeforeRender", DRAWERS)
        self.assertIn("${settingsOpenBeforeRender ? 'open' : ''}", DRAWERS)
        self.assertIn("this.refreshSessionStatus(result);", DRAWERS)
        self.assertIn("this.store.patch({ sessionStatus: result });", DRAWERS)
        self.assertIn("this.sessionSettingsFeedback", DRAWERS)
        self.assertIn("['INPUT', 'SELECT'].includes(active.tagName)", DRAWERS)
        self.assertNotIn("panel.open === true", DRAWERS)

    def test_session_recorder_honors_configured_log_size_limit(self):
        self.assertIn("effectiveLimitMb = settings.sessionLogMaxMb.coerceIn(64, 1_024)", RECORDER)
        self.assertIn("limite de $effectiveLimitMb MB atingido", RECORDER)
        self.assertNotIn("coerceAtLeast(512)", RECORDER)


if __name__ == "__main__":
    unittest.main()
