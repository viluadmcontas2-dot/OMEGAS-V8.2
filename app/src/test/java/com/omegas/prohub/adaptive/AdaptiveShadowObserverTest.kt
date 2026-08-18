package com.omegas.prohub.adaptive

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.learning.SampleDecision
import com.omegas.prohub.telemetry.CanonicalEvidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveShadowObserverTest {
    @Test
    fun `shadow observes current canonical evidence and rejects stale session or duplicate`() {
        val observer = AdaptiveShadowObserver()
        observer.beginSession(9L)

        val current = evidence(sequence = 1L, session = 9L)
        assertTrue(observer.observe(current))
        assertFalse(observer.observe(current))
        assertFalse(observer.observe(evidence(sequence = 2L, session = 8L)))

        val metrics = observer.metricsJson()
        assertTrue(metrics.getBoolean("polling").not())
        assertTrue(metrics.getBoolean("writer").not())
        assertTrue(metrics.getBoolean("automatic_calibration").not())
        assertTrue(metrics.getLong("observed") == 1L)
        assertTrue(metrics.getLong("rejected_stale") == 2L)
    }

    private fun evidence(sequence: Long, session: Long): CanonicalEvidence {
        val telemetry = Mp48Telemetry(
            capturedAtElapsedMs = sequence * 100L,
            rpm = 2_000,
            levelRaw = 100,
            gasRaw = 0,
            gasMsDiagnostic = null,
            petrolRaw = 100,
            petrolCounts = 100,
            petrolMs = 4.0,
            dynamicCorrection = 0,
            fuelByte = 0x80,
            fuel = Mp48Fuel.PETROL,
            state = Mp48Fuel.PETROL.wireName,
            waterRaw = 80,
            waterC = 80,
            gasC = 30,
            gasPressureRaw = 100,
            gasPressureAbsBar = 2.0,
            mapRaw = 100,
            mapBar = 0.55,
            pressureDiffBar = 1.45,
            plausible = true,
        )
        return CanonicalEvidence.from(
            telemetry = telemetry,
            decision = SampleDecision.transition(reason = "observe", state = "FORMING_SAMPLE"),
            sequence = sequence,
            usbSessionId = session,
        )
    }
}
