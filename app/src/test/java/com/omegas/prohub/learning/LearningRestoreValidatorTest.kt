package com.omegas.prohub.learning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LearningRestoreValidatorTest {
    @Test
    fun `current state without persisted identity is explicit legacy observational for CNG`() {
        val root = Files.createTempDirectory("omegas-restore-current").toFile()
        try {
            val active = root.resolve(LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE)
            active.writeText(
                JSONObject()
                    .put("format", MotorLearningMemory.FORMAT)
                    .put("stateDigest", "digest-present")
                    .toString(),
            )
            val result = LearningRestoreValidator.validate(root, active)
            assertEquals("CURRENT_COMPATIBLE", result.getString("state"))
            assertEquals("MISSING", result.getString("identity_state"))
            assertTrue(result.getBoolean("legacy_observational"))
            assertFalse(result.getBoolean("restored_cng_actionable"))
            assertTrue(result.getBoolean("telemetry_allowed"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `old state without digest is downgraded instead of promoted to active science`() {
        val root = Files.createTempDirectory("omegas-restore-legacy").toFile()
        try {
            val active = root.resolve(LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE)
            active.writeText(JSONObject().put("format", MotorLearningMemory.FORMAT).toString())
            val result = LearningRestoreValidator.validate(root, active)
            assertEquals("LEGACY_OBSERVATIONAL", result.getString("state"))
            assertTrue(result.getBoolean("legacy_observational"))
            assertFalse(result.getBoolean("restored_cng_actionable"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `corrupt and incompatible states are explicit while telemetry remains allowed`() {
        val root = Files.createTempDirectory("omegas-restore-invalid").toFile()
        try {
            val active = root.resolve(LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE)
            active.writeText("{broken")
            var result = LearningRestoreValidator.validate(root, active)
            assertEquals("CORRUPT", result.getString("state"))
            assertTrue(result.getBoolean("telemetry_allowed"))

            active.writeText(JSONObject().put("format", "some-other-learning-format").toString())
            result = LearningRestoreValidator.validate(root, active)
            assertEquals("INCOMPATIBLE", result.getString("state"))
            assertTrue(result.getBoolean("telemetry_allowed"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `identity only remains non actionable until live geometry is known`() {
        val root = Files.createTempDirectory("omegas-restore-identity").toFile()
        try {
            val active = root.resolve(LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE)
            active.writeText(
                JSONObject()
                    .put("format", MotorLearningMemory.FORMAT)
                    .put("stateDigest", "digest-present")
                    .toString(),
            )
            root.resolve(LearningRestoreValidator.CALIBRATION_BINDING_FILE).writeText(
                JSONObject()
                    .put("calibration_fingerprint", "cal-A")
                    .put("calibration_generation", 2)
                    .put("geometry_fingerprint", "geo-A")
                    .put("usb_session_id", 77L)
                    .put("map_hash", "map-A")
                    .toString(),
            )
            val result = LearningRestoreValidator.validate(root, active)
            assertEquals("IDENTITY_ONLY", result.getString("identity_state"))
            assertFalse(result.getBoolean("restored_cng_actionable"))
            assertTrue(result.getBoolean("requires_live_calibration_identity_for_cng"))
        } finally {
            root.deleteRecursively()
        }
    }
}
