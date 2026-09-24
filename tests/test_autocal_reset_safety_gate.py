from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt"
UI = ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js"
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt"
KFACTOR = ROOT / "app/src/main/java/com/omegas/prohub/calibration/KFactorManager.kt"

class AutoCalResetSafetyGateTest(unittest.TestCase):
    def test_progbase_resets_are_allowed_by_code_evidence(self):
        bridge = BRIDGE.read_text(encoding="utf-8")
        for name in ("RESET_PETROL", "RESET_GAS", "RESET_ALL"):
            self.assertIn(f"AutoCalNativeActionManager.Action.{name}", bridge)
        self.assertIn('normalized == "RESET_K_FACTOR"', bridge)

    def test_session_and_mutation_interlocks_protect_host_resets_without_backup_gate(self):
        text = MANAGER.read_text(encoding="utf-8")
        self.assertIn("ensureSession(prepared)", text)
        self.assertIn("unsafeMutationReason()", text)
        self.assertIn('update("SENDING_ACTION"', text)
        self.assertIn('update("READING_AFTER", "Atualizando estado da ECU"', text)
        self.assertIn('.put("automaticBackup", false)', text)
        self.assertNotIn("persistPreMutationBackup", text)
        self.assertNotIn("PERSISTING_BACKUP", text)
        self.assertNotIn("READING_BEFORE", text)

    def test_ui_reset_buttons_route_to_progbase_actions(self):
        ui = UI.read_text(encoding="utf-8")
        api = (ROOT / "app/src/main/assets/ui/core/autocal-api.js").read_text(encoding="utf-8")
        bridge = BRIDGE.read_text(encoding="utf-8")
        manager = MANAGER.read_text(encoding="utf-8")
        expected = {
            "RESET_PETROL": "Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, 0x01))",
            "RESET_GAS": "Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, 0x02))",
            "RESET_ALL": "Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, 0x04))",
        }
        for action, frame in expected.items():
            self.assertIn(f'data-autocal-action="{action}"', ui)
            self.assertIn(f"AutoCalNativeActionManager.Action.{action}", bridge)
            self.assertIn(frame, manager)
        self.assertIn("this.prepare(button.dataset.autocalAction)", ui)
        self.assertIn("prepare: action => invoke('prepareNativeAction'", api)
        self.assertIn("execute: preparationId => invoke('executeNativeAction'", api)
        self.assertIn("val result = actionManager.execute(preparationId)", bridge)

    def test_curve_reset_uses_existing_verified_writer(self):
        text = KFACTOR.read_text(encoding="utf-8")
        self.assertIn("fun startResetToNeutral", text)
        self.assertIn("KFactorProtocol.rawFromFactor(1.0)", text)
        self.assertIn("startBatchWrite(points, reason)", text)
        self.assertNotIn("createBackup(adjustmentId", text)
        self.assertIn('.put("automaticBackup", false)', text)
        self.assertIn("readback K factor", text)

if __name__ == "__main__":
    unittest.main()
