from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalJavascriptBridge.kt"
UI = ROOT / "app/src/main/assets/ui/screens/autocal-cockpit.js"
MANAGER = ROOT / "app/src/main/java/com/omegas/prohub/autocal/AutoCalNativeActionManager.kt"
PROTOCOL = ROOT / "app/src/main/java/com/omegas/prohub/ecu/AutoCalProtocol.kt"
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
        protocol = PROTOCOL.read_text(encoding="utf-8")
        expected = {
            "RESET_PETROL": ("RESET_PETROL(0x01)", "ManualActionMode.RESET_PETROL"),
            "RESET_GAS": ("RESET_GAS(0x02)", "ManualActionMode.RESET_GAS"),
            "RESET_ALL": ("RESET_ALL(0x04)", "ManualActionMode.RESET_ALL"),
        }
        self.assertIn("AUTOCAL_ACTION_COMMAND = 0x24", protocol)
        self.assertIn("AUTOCAL_ACTION_SUBOP_CONTROL = 0x04", protocol)
        for action, (mode, manager_route) in expected.items():
            self.assertIn(f'data-autocal-action="{action}"', ui)
            self.assertIn(f"AutoCalNativeActionManager.Action.{action}", bridge)
            self.assertIn(mode, protocol)
            self.assertIn(manager_route, manager)
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
