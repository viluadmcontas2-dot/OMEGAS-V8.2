from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt"
UI = ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js"

class AutoCalResetSafetyGateTest(unittest.TestCase):
    def test_destructive_resets_are_blocked_in_bridge(self):
        text = BRIDGE.read_text(encoding="utf-8")
        self.assertIn("DESTRUCTIVE_RESET_INTERLOCK_MESSAGE", text)
        self.assertIn("return localFailure(DESTRUCTIVE_RESET_INTERLOCK_MESSAGE)", text)

    def test_reset_buttons_are_disabled_in_ui(self):
        text = UI.read_text(encoding="utf-8")
        self.assertIn('data-autocal-action="RESET_PETROL" disabled', text)
        self.assertIn('data-autocal-action="RESET_GAS" disabled', text)
        lower = text.lower()
        self.assertIn("resets continuam bloqueados", lower)
        self.assertIn("efeito amplo observado", lower)
        self.assertIn("não promete seletividade inexistente", lower)

if __name__ == "__main__":
    unittest.main()
