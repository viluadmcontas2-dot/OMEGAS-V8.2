package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PragmaticEquivalenceContractTest {
    @Test fun sameRpmMapPairRemainsComparableWithoutEnvironmentalOrKFactors() {
        val reference = EquivalenceSample(rpm = 2200.0, mapBar = 0.55, petrolInjectionMs = 3.80, capturedAtMs = 1000L)
        val gas = EquivalenceSample(rpm = 2210.0, mapBar = 0.56, petrolInjectionMs = 4.18, capturedAtMs = 1100L)

        val result = PragmaticEquivalence.compare(reference, gas, rpmTolerance = 80.0, mapToleranceBar = 0.04)

        assertTrue(result.comparable)
        assertEquals(0.38, result.injectionDeltaMs!!, 1e-9)
        assertEquals(4.18 / 3.80, result.injectionRatio!!, 1e-9)
        assertEquals(EffectDirection.INCREASE, result.direction)
    }

    @Test fun rpmOrMapMismatchRejectsPairInsteadOfInventingEnvironmentalCorrection() {
        val reference = EquivalenceSample(2200.0, 0.55, 3.80, 1000L)
        val rpmMiss = EquivalenceSample(2500.0, 0.56, 4.18, 1100L)
        val mapMiss = EquivalenceSample(2210.0, 0.70, 4.18, 1100L)

        assertFalse(PragmaticEquivalence.compare(reference, rpmMiss, 80.0, 0.04).comparable)
        assertFalse(PragmaticEquivalence.compare(reference, mapMiss, 80.0, 0.04).comparable)
    }

    @Test fun operationalContractDoesNotRequireEnvironmentalInputs() {
        assertEquals(setOf("RPM", "MAP", "PETROL_T_INJ"), PragmaticEquivalence.REQUIRED_OPERATIONAL_INPUTS)
    }
}
