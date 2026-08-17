package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCalibrationMigrationTest {
    @Test
    fun `omegas7 antigo vira somente observacional sem identidade moderna`() {
        val legacy = """
            schema=OMEGAS_V7_SESSION_6
            session=sessao-antiga
            revision=7,9
            curve=${List(30) { 1.0 }.joinToString(",")}
            mapShape=13,12
            map.0=${List(12) { 150 }.joinToString(",")}
        """.trimIndent()
        val result = LegacyCalibrationMigration.fromV7Session(legacy, capturedAtMs = 100L)
        assertEquals(LegacyCalibrationClassification.LEGACY_OBSERVATIONAL, result.classification)
        assertEquals(CalibrationProvenance.RESTORED_HISTORY, result.identity.provenance)
        assertEquals(CalibrationFreshness.STALE, result.identity.freshness)
        assertEquals(CalibrationCompleteness.UNKNOWN, result.identity.completeness)
        assertFalse(result.identity.materiallyUsable())
        assertTrue(result.identity.functionFingerprint.isBlank())
        assertEquals(9L, result.identity.mapRevision)
        assertEquals(7L, result.identity.curveRevision)
    }

    @Test
    fun `cache legado com axes e rows nao herda fingerprint moderno`() {
        val result = LegacyCalibrationMigration.fromLegacyCacheKeys(
            keys = setOf("schema", "rows", "extraRow", "allRows", "axes", "hash", "sessionId"),
            capturedAtMs = 100L,
        )
        assertEquals(LegacyCalibrationClassification.LEGACY_OBSERVATIONAL, result.classification)
        assertTrue(result.identity.geometryFingerprint.isBlank())
        assertTrue(result.identity.functionFingerprint.isBlank())
        assertFalse(result.identity.materiallyUsable())
    }

    @Test
    fun `schema desconhecido e texto corrompido nao sao aceitos como legado valido`() {
        assertEquals(
            LegacyCalibrationClassification.REJECTED_INCOMPATIBLE,
            LegacyCalibrationMigration.fromV7Session("schema=OUTRO\nrevision=1,1", 0L).classification,
        )
        assertEquals(
            LegacyCalibrationClassification.CORRUPT,
            LegacyCalibrationMigration.fromV7Session("sem-igualdade", 0L).classification,
        )
    }
}
