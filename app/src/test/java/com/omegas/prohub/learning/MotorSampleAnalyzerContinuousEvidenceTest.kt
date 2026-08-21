package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorSampleAnalyzerContinuousEvidenceTest {
    private val policy = LearningTolerancePolicy(requiredFrames = 6)

    @Test
    fun `rpm instability remains valid weighted evidence`() {
        val analyzer = MotorSampleAnalyzer { policy }
        var decision: SampleDecision? = null
        repeat(6) { index ->
            decision = analyzer.add(
                frame(
                    at = index * 50L,
                    rpm = if (index < 3) 1_600 else 3_600,
                ),
            )
        }

        assertTrue(decision!!.learningEligible)
        assertNotNull(decision!!.sample)
        assertEquals("SAMPLE_ACCEPTED", decision!!.state)
        assertTrue(decision!!.sample!!.quality in 0.0..1.0)
    }

    @Test
    fun `cold water is diagnostic not a primary equivalence gate`() {
        val analyzer = MotorSampleAnalyzer { policy }
        var decision: SampleDecision? = null
        repeat(6) { index ->
            decision = analyzer.add(frame(at = index * 50L, waterC = 35))
        }

        assertTrue(decision!!.learningEligible)
        assertNotNull(decision!!.sample)
        assertEquals(35.0, decision!!.sample!!.diagnostics.waterCenterC, 0.0)
    }

    @Test
    fun `cng pressure instability is diagnostic not a primary equivalence gate`() {
        val analyzer = MotorSampleAnalyzer { policy }
        var decision: SampleDecision? = null
        repeat(6) { index ->
            decision = analyzer.add(
                frame(
                    at = index * 50L,
                    fuel = Mp48Fuel.CNG,
                    pressureDiffBar = if (index < 3) 0.6 else 2.4,
                ),
            )
        }

        assertTrue(decision!!.learningEligible)
        assertNotNull(decision!!.sample)
        assertTrue(decision!!.sample!!.diagnostics.pressureCenterShift > policy.pressureCenterBar)
    }

    @Test
    fun `cutoff remains hard zero`() {
        val analyzer = MotorSampleAnalyzer { policy }
        repeat(5) { analyzer.add(frame(it * 50L)) }
        val cutoff = analyzer.add(
            frame(
                at = 250L,
                fuel = Mp48Fuel.CUTOFF,
                petrolMs = 0.0,
                gasRaw = 0,
                mapBar = 0.2,
            ),
        )

        assertFalse(cutoff.learningEligible)
        assertEquals("CUTOFF", cutoff.state)
    }

    private fun frame(
        at: Long,
        fuel: Mp48Fuel = Mp48Fuel.PETROL,
        petrolMs: Double = 4.0,
        gasRaw: Int = if (fuel == Mp48Fuel.CNG) 200 else 0,
        mapBar: Double = 0.60,
        rpm: Int = 2_500,
        waterC: Int = 80,
        pressureDiffBar: Double = 1.4,
    ) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = rpm,
        levelRaw = 100,
        gasRaw = gasRaw,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = fuel,
        state = fuel.wireName,
        waterRaw = 80,
        waterC = waterC,
        gasC = 30,
        gasPressureRaw = 100,
        gasPressureAbsBar = pressureDiffBar + mapBar,
        mapRaw = 100,
        mapBar = mapBar,
        pressureDiffBar = pressureDiffBar,
        plausible = true,
    )
}
