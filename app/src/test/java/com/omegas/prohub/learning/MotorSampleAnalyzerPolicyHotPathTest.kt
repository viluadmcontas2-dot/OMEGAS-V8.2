package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MotorSampleAnalyzerPolicyHotPathTest {
    @Test
    fun `telemetry hot path reads policy provider once per frame`() {
        var reads = 0
        var policy = LearningTolerancePolicy(requiredFrames = 10)
        val analyzer = MotorSampleAnalyzer {
            reads += 1
            policy
        }
        reads = 0

        repeat(20) { index -> analyzer.add(frame(index * 50L)) }

        assertEquals(20, reads)

        policy = policy.copy(requiredFrames = 6)
        val restarted = analyzer.add(frame(2_000L))
        assertEquals(21, reads)
        assertFalse(restarted.learningEligible)
        assertEquals(1, restarted.frameCount)
    }

    private fun frame(at: Long) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = 4.0,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = Mp48Fuel.PETROL,
        state = Mp48Fuel.PETROL.wireName,
        waterRaw = 80,
        waterC = 80,
        gasC = 30,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = 0.60,
        pressureDiffBar = 1.4,
        plausible = true,
    )
}
