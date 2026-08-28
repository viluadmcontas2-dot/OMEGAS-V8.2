from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/omegas/prohub"


def text(path: str) -> str:
    return (ROOT / path).read_text("utf-8")


class Phase02CalibrationIdentityGate(unittest.TestCase):
    def test_runtime_map_has_no_historical_axis_fallback(self):
        manager = text("app/src/main/java/com/omegas/prohub/calibration/KWriteManager.kt")
        fixture = text("app/src/main/java/com/omegas/prohub/calibration/KMapPhysicalAxes.kt")
        self.assertNotIn("KMapPhysicalAxes", manager)
        self.assertIn('HISTORICAL_FIXTURE', fixture)
        self.assertIn('.put("runtimeAuthority", false)', fixture)

    def test_geometry_uses_real_mp48_vectors_not_rif_giri(self):
        protocol = text("app/src/main/java/com/omegas/prohub/ecu/Mp48Protocol.kt")
        reader = text("app/src/main/java/com/omegas/prohub/calibration/CompositeCalibrationReader.kt")
        self.assertIn("TEMPI_PER_K_ADDRESS = 0x0037", protocol)
        self.assertIn("GIRI_PER_K_ADDRESS = 0x003D", protocol)
        self.assertIn("readKPetrolAxis()", reader)
        self.assertIn("readKRpmAxis()", reader)
        self.assertNotIn("0x000C", reader)

    def test_composite_read_is_single_read_only_unit_with_generation_guard(self):
        reader = text("app/src/main/java/com/omegas/prohub/calibration/CompositeCalibrationReader.kt")
        snapshot = text("app/src/main/java/com/omegas/prohub/calibration/CompositeCalibrationSnapshot.kt")
        self.assertIn("serial.unit(", reader)
        self.assertIn("Mp48WorkClass.READ_ONLY", reader)
        self.assertIn("CalibrationGenerationGuard.evaluate", reader)
        self.assertIn("CalibrationGenerationGuard.evaluate", snapshot)
        self.assertIn("MapKPhysicalHash.hash", snapshot)

    def test_identity_cannot_be_material_from_metadata_only(self):
        identity = text("app/src/main/java/com/omegas/prohub/calibration/CalibrationIdentity.kt")
        self.assertIn("private val hasMaterialPayload: Boolean", identity)
        self.assertIn("hasMaterialPayload && CalibrationIdentityStateResolver.materiallyUsable", identity)
        self.assertIn("hasMaterialPayload = true", identity)
        self.assertIn("hasMaterialPayload = false", identity)

    def test_legacy_persistence_is_observational_and_stale(self):
        migration = text("app/src/main/java/com/omegas/prohub/calibration/LegacyCalibrationMigration.kt")
        self.assertIn("LEGACY_OBSERVATIONAL", migration)
        self.assertIn("RESTORED_HISTORY", migration)
        self.assertIn("CalibrationFreshness.STALE", migration)
        self.assertNotIn("CalibrationFunctionFingerprint.from", migration)

    def test_session_start_bootstrap_is_bounded_and_non_writing(self):
        monitor = text("app/src/main/java/com/omegas/prohub/autocal/NativeAutoCalMonitor.kt")
        reader = text("app/src/main/java/com/omegas/prohub/calibration/CompositeCalibrationReader.kt")
        self.assertIn("if (ageMs < SESSION_SETTLE_MS)", monitor)
        self.assertIn("calibrationBootstrapAttempted", monitor)
        self.assertIn("readAtSessionStart(currentSession)", monitor)
        self.assertIn("telemetryAfter = true", reader)
        self.assertNotIn("Mp48WorkClass.MANUAL_WRITE", reader)
        self.assertNotIn("Thread.sleep", reader)


if __name__ == "__main__":
    unittest.main()
