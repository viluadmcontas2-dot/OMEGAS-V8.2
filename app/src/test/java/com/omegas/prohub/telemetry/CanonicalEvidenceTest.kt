package com.omegas.prohub.telemetry

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.learning.SampleDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalEvidenceTest {
    @Test
    fun `one envelope preserves acquisition identity and recorder provenance`() {
        val telemetry = telemetry(at = 1234L, fuel = Mp48Fuel.CNG)
        val decision = SampleDecision.transition(
            state = "FORMING_SAMPLE",
            reason = "coletando",
            reasonCode = "FORMING_SAMPLE",
        )

        val evidence = CanonicalEvidence.from(
            telemetry = telemetry,
            decision = decision,
            sequence = 77L,
            usbSessionId = 9L,
        )

        assertSame(telemetry, evidence.rawTelemetry)
        assertSame(decision, evidence.sampleDecision)
        assertEquals(77L, evidence.frame.sequence)
        assertEquals(9L, evidence.frame.usbSessionId)
        assertEquals(CanonicalEvidence.SCHEMA, evidence.provenance.schema)
        assertEquals(CanonicalEvidence.ACQUISITION_SOURCE, evidence.provenance.acquisitionSource)

        val recorded = evidence.toRecorderJson()
        assertEquals(77L, recorded.getLong("sequence"))
        assertEquals(9L, recorded.getLong("session_id"))
        assertEquals("GNV", recorded.getString("fuel"))
        assertTrue(recorded.has("canonical_provenance"))
        assertEquals(
            CanonicalEvidence.SCHEMA,
            recorded.getJSONObject("canonical_provenance").getString("schema"),
        )
    }

    private fun telemetry(at: Long, fuel: Mp48Fuel) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = if (fuel == Mp48Fuel.CNG) 200 else 0,
        gasMsDiagnostic = if (fuel == Mp48Fuel.CNG) 6.0 else null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = 4.2,
        dynamicCorrection = 0,
        fuelByte = if (fuel == Mp48Fuel.CNG) 0x90 else 0x80,
        fuel = fuel,
        state = fuel.wireName,
        waterRaw = 80,
        waterC = 80,
        gasC = 35,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = 0.60,
        pressureDiffBar = 1.40,
        plausible = true,
    )
}
