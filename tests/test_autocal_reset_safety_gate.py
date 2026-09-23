from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt"
UI = ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js"
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt"

class AutoCalResetSafetyGateTest(unittest.TestCase):
    def test_only_observed_broad_reset_can_be_prepared(self):
        bridge = BRIDGE.read_text(encoding="utf-8")
        self.assertIn("if (parsed != AutoCalNativeActionManager.Action.RESET_GAS)", bridge)
        self.assertIn("currentNativeManager()?.prepare(parsed.name)", bridge)
        self.assertIn("if (action != AutoCalNativeActionManager.Action.RESET_GAS)", bridge)
        self.assertIn("actionManager.execute(preparationId)", bridge)
        self.assertIn("RESETAR AQUISIÇÃO", bridge)

    def test_broad_reset_is_operator_controlled_and_explicit(self):
        text = UI.read_text(encoding="utf-8")
        self.assertIn('data-autocal-action="RESET_GAS" class="danger-primary"', text)
        self.assertNotIn('data-autocal-action="RESET_PETROL"', text)
        self.assertIn("efeito amplo", text)
        self.assertIn("sem restauração automática", text)
        self.assertNotIn('data-autocal-action="RESET_ALL"', text)

    def test_backup_and_session_interlock_still_protect_usb(self):
        text = MANAGER.read_text(encoding="utf-8")
        self.assertIn('persistPreMutationBackup(prepared, before)', text)
        self.assertIn('require(!before.partial)', text)
        self.assertIn('verified.getString("beforeHash") == before.snapshotHash', text)
        self.assertIn("ensureSession(prepared)", text)
        self.assertIn("unsafeMutationReason()", text)

if __name__ == "__main__":
    unittest.main()
