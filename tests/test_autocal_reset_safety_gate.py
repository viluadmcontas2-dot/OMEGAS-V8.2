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

    def test_backup_and_session_interlock_still_protect_host_resets(self):
        text = MANAGER.read_text(encoding="utf-8")
        self.assertIn("persistPreMutationBackup(prepared, before)", text)
        self.assertIn("require(!before.partial)", text)
        self.assertIn("ensureSession(prepared)", text)
        self.assertIn("unsafeMutationReason()", text)

    def test_curve_reset_uses_existing_verified_writer(self):
        text = KFACTOR.read_text(encoding="utf-8")
        self.assertIn("fun startResetToNeutral", text)
        self.assertIn("KFactorProtocol.rawFromFactor(1.0)", text)
        self.assertIn("startBatchWrite(points, reason)", text)
        self.assertIn("createBackup(adjustmentId", text)
        self.assertIn("readback K factor", text)

if __name__ == "__main__":
    unittest.main()
