package com.omegas.prohub.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentEquivalenceStatusPolicyTest {
    @Test
    fun `hard zero states cannot expose cached local equivalence`() {
        listOf(
            SampleDecision.invalid("invalid"),
            SampleDecision.transition(state = "ENGINE_OFF", reason = "off"),
            SampleDecision.transition(state = "CUTOFF", reason = "cutoff"),
            SampleDecision.transition(state = "FUEL_TRANSITION", reason = "transition"),
            SampleDecision.transition(state = "FUEL_VERIFYING", reason = "verifying"),
            SampleDecision.transition(state = "FUEL_UNKNOWN", reason = "unknown"),
            SampleDecision.transition(
                state = "TELEMETRY_GAP",
                reason = "gap",
                continuityLost = true,
                reasonCode = "REAL_TELEMETRY_LOSS",
            ),
        ).forEach { decision ->
            assertFalse(decision.state, CurrentEquivalenceStatusPolicy.allowsCachedEstimate(decision))
        }
        assertFalse(CurrentEquivalenceStatusPolicy.allowsCachedEstimate(null))
    }

    @Test
    fun `forming a healthy next window may keep the last local estimate visible`() {
        assertTrue(
            CurrentEquivalenceStatusPolicy.allowsCachedEstimate(
                SampleDecision.forming(
                    count = 4,
                    minimum = 8,
                    desired = 12,
                    timing = SampleTiming(150L, 50L),
                    fuelConfirmed = "GNV",
                ),
            ),
        )
    }

    @Test
    fun `accepted evidence remains eligible for current local status`() {
        val sample = MotorSample(
            id = "accepted",
            startedAtElapsedMs = 0L,
            endedAtElapsedMs = 350L,
            fuel = com.omegas.prohub.ecu.Mp48Fuel.CNG,
            rpm = 2_500.0,
            mapBar = 0.60,
            petrolMs = 3.30,
            pressureDiffBar = 1.4,
            waterC = 80.0,
            gasC = 30.0,
            quality = 1.0,
            classification = SampleClassification.STRONG,
            frameCount = 8,
            diagnostics = SampleDiagnostics(
                frameCount = 8,
                durationMs = 350L,
                medianIntervalMs = 50L,
                waterCenterC = 80.0,
                minimumWaterC = 55,
                rpmCenterShift = 0.0,
                rpmCenterLimit = 60.0,
                rpmOscillation = 0.0,
                rpmOscillationLimit = 120.0,
                mapCenterShift = 0.0,
                mapCenterLimit = 0.025,
                mapOscillation = 0.0,
                mapOscillationLimit = 0.05,
                petrolCenterShift = 0.0,
                petrolCenterLimit = 0.2,
                petrolOscillationRatio = 0.0,
                petrolOscillationLimit = 0.15,
                pressureCenterShift = 0.0,
                pressureCenterLimit = 0.04,
                pressureOscillation = 0.0,
                pressureOscillationLimit = 0.08,
            ),
        )
        assertTrue(CurrentEquivalenceStatusPolicy.allowsCachedEstimate(SampleDecision.accepted(sample)))
    }
}
