package com.omegas.prohub.adaptive

import com.omegas.prohub.telemetry.RuntimeFreshness
import com.omegas.prohub.telemetry.RuntimeFuelState
import com.omegas.prohub.telemetry.RuntimeTelemetryFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveEvidenceContractsTest {
    @Test
    fun `canonical evidence is the same typed runtime frame not a copied telemetry dto`() {
        val runtime = RuntimeTelemetryFrame(
            sequence = 9L,
            usbSessionId = 3L,
            capturedAtElapsedMs = 100L,
            rpm = 2200,
            petrolMs = 5.5,
            gasMsDiagnostic = 8.0,
            waterC = 82.0,
            gasTemperatureC = 31.0,
            gasPressureAbsBar = 2.1,
            mapBar = 0.52,
            pressureDiffBar = 1.58,
            fuel = RuntimeFuelState.CNG,
            plausible = true,
            freshness = RuntimeFreshness.CURRENT,
        )
        val evidence: CanonicalEvidence = runtime
        assertSame(runtime, evidence)
        assertEquals(3L, evidence.usbSessionId)
        assertEquals(100L, evidence.capturedAtElapsedMs)
    }

    @Test
    fun `canonical evidence contract cannot own transport json science or writer`() {
        assertTrue(CanonicalEvidenceContract.SINGLE_PHYSICAL_ACQUISITION)
        assertFalse(CanonicalEvidenceContract.MAY_CREATE_SECOND_MP48_POLLING)
        assertFalse(CanonicalEvidenceContract.MAY_REPARSE_JSON_TO_FORM_SCIENCE)
        assertFalse(CanonicalEvidenceContract.MAY_WRITE_ECU)
    }
}
