package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationIdentityStateTest {
    @Test
    fun `nenhum componente conhecido resulta UNKNOWN`() {
        val identity = CalibrationIdentity.observational(
            usbSessionId = null,
            generation = null,
            provenance = CalibrationProvenance.UNKNOWN,
            freshness = CalibrationFreshness.UNKNOWN,
            capturedAtMs = 0L,
        )
        assertEquals(CalibrationCompleteness.UNKNOWN, identity.completeness)
        assertFalse(identity.materiallyUsable())
    }

    @Test
    fun `algum mas nao todos componentes resulta PARTIAL`() {
        val identity = CalibrationIdentity.observational(
            usbSessionId = 77L,
            generation = 4,
            provenance = CalibrationProvenance.RECOVERY_READ,
            freshness = CalibrationFreshness.CURRENT_SESSION,
            capturedAtMs = 10L,
            mapHash = "a".repeat(64),
        )
        assertEquals(CalibrationCompleteness.PARTIAL, identity.completeness)
        assertFalse(identity.materiallyUsable())
    }

    @Test
    fun `todos componentes conhecidos podem ser KNOWN mas historico stale continua nao material`() {
        val identity = CalibrationIdentity.observational(
            usbSessionId = 77L,
            generation = 4,
            provenance = CalibrationProvenance.RESTORED_HISTORY,
            freshness = CalibrationFreshness.STALE,
            capturedAtMs = 10L,
            functionFingerprint = "f".repeat(64),
            geometryFingerprint = "g".repeat(64),
            mapHash = "m".repeat(64),
            curveAxisFingerprint = "a".repeat(64),
            curveFactorsFingerprint = "c".repeat(64),
        )
        assertEquals(CalibrationCompleteness.KNOWN, identity.completeness)
        assertFalse(identity.materiallyUsable())
    }

    @Test
    fun `metadados completos observacionais nao fabricam payload material`() {
        val identity = CalibrationIdentity.observational(
            usbSessionId = 77L,
            generation = 4,
            provenance = CalibrationProvenance.FULL_ECU_READ,
            freshness = CalibrationFreshness.CURRENT_SESSION,
            capturedAtMs = 10L,
            functionFingerprint = "f".repeat(64),
            geometryFingerprint = "g".repeat(64),
            mapHash = "m".repeat(64),
            curveAxisFingerprint = "a".repeat(64),
            curveFactorsFingerprint = "c".repeat(64),
        )
        assertEquals(CalibrationCompleteness.KNOWN, identity.completeness)
        assertFalse(identity.materiallyUsable())
    }

    @Test
    fun `full current known pode ser metadata ready mas identity exige payload real`() {
        val completeness = CalibrationIdentityStateResolver.completeness(
            usbSessionId = 77L,
            generation = 4,
            functionFingerprint = "f".repeat(64),
            geometryFingerprint = "g".repeat(64),
            mapHash = "m".repeat(64),
            curveAxisFingerprint = "a".repeat(64),
            curveFactorsFingerprint = "c".repeat(64),
        )
        assertEquals(CalibrationCompleteness.KNOWN, completeness)
        assertTrue(CalibrationIdentityStateResolver.materiallyUsable(
            completeness,
            CalibrationFreshness.CURRENT_SESSION,
            CalibrationProvenance.FULL_ECU_READ,
        ))
    }
}
