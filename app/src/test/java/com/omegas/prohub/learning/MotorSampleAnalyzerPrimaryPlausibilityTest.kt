package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorSampleAnalyzerPrimaryPlausibilityTest {
    @Test
    fun `environment-only diagnostic implausibility does not block primary equivalence`() {
        val analyzer = MotorSampleAnalyzer { LearningTolerancePolicy(requiredFrames = 6) }
        var decision: SampleDecision? = null

        repeat(6) { index ->
            decision = analyzer.add(
                frame(
                    at = index * 50L,
                    basePlausible = false,
                    plausible = false,
                    waterC = 200,
                    gasC = 200,
                    plausibilityReasons = listOf(
                        "WATER_TEMPERATURE_OUT_OF_RANGE",
                        "GAS_TEMPERATURE_OUT_OF_RANGE",
                    ),
                ),
            )
        }

        assertTrue("RPM+MAP+petrol Tinj remain physically valid", decision!!.learningEligible)
        assertEquals("SAMPLE_ACCEPTED", decision!!.state)
        assertTrue(decision!!.sample!!.quality > 0.0)
        assertEquals(200.0, decision!!.sample!!.diagnostics.waterCenterC, 0.0)
    }

    @Test
    fun `invalid primary rpm map or petrol tinj still hard zero`() {
        listOf(
            frame(
                at = 0L,
                rpm = 9_500,
                basePlausible = false,
                plausible = false,
                plausibilityReasons = listOf("RPM_OUT_OF_RANGE"),
            ),
            frame(
                at = 50L,
                mapBar = 3.0,
                basePlausible = false,
                plausible = false,
                plausibilityReasons = listOf("MAP_OUT_OF_RANGE"),
            ),
            frame(
                at = 100L,
                petrolMs = 45.0,
                basePlausible = false,
                plausible = false,
                plausibilityReasons = listOf("PETROL_INJECTION_OUT_OF_RANGE"),
            ),
        ).forEach { telemetry ->
            val decision = MotorSampleAnalyzer { LearningTolerancePolicy(requiredFrames = 6) }.add(telemetry)
            assertFalse(decision.learningEligible)
            assertEquals(SampleClassification.INVALID, decision.classification)
        }
    }

    private fun frame(
        at: Long,
        rpm: Int = 2_500,
        mapBar: Double = 0.60,
        petrolMs: Double = 4.0,
        waterC: Int = 80,
        gasC: Int = 30,
        plausible: Boolean = true,
        basePlausible: Boolean = plausible,
        plausibilityReasons: List<String> = emptyList(),
    ) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = rpm,
        levelRaw = 100,
        gasRaw = 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = 0x80,
        fuel = Mp48Fuel.PETROL,
        state = Mp48Fuel.PETROL.wireName,
        waterRaw = 80,
        waterC = waterC,
        gasC = gasC,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = mapBar,
        pressureDiffBar = 1.4,
        plausible = plausible,
        basePlausible = basePlausible,
        cngPressurePlausible = true,
        plausibilityReasons = plausibilityReasons,
    )
}
