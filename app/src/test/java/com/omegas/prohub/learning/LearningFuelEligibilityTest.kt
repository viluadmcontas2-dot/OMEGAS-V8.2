package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.telemetry.RuntimeFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningFuelEligibilityTest {
    @Test
    fun currentPlausiblePetrolAndCngAreTheOnlyEligibleFuelStates() {
        val expected = mapOf(
            Mp48Fuel.PETROL to LearningEligibilityMode.PETROL_REFERENCE,
            Mp48Fuel.CNG to LearningEligibilityMode.CNG_COMPARISON,
        )
        Mp48Fuel.entries.forEach { fuel ->
            val result = LearningFuelEligibility.evaluate(fuel, RuntimeFreshness.CURRENT, true)
            if (fuel in expected) {
                assertTrue(fuel.name, result.eligible)
                assertEquals(expected[fuel], result.mode)
            } else {
                assertFalse(fuel.name, result.eligible)
                assertEquals(LearningEligibilityMode.INELIGIBLE, result.mode)
            }
        }
    }

    @Test
    fun staleUnknownFreshnessAndImplausibleAlwaysFailClosed() {
        Mp48Fuel.entries.forEach { fuel ->
            assertEquals(
                "TELEMETRY_STALE",
                LearningFuelEligibility.evaluate(fuel, RuntimeFreshness.STALE, true).reasonCode,
            )
            assertEquals(
                "TELEMETRY_FRESHNESS_UNKNOWN",
                LearningFuelEligibility.evaluate(fuel, RuntimeFreshness.UNKNOWN, true).reasonCode,
            )
            assertEquals(
                "TELEMETRY_IMPLAUSIBLE",
                LearningFuelEligibility.evaluate(fuel, RuntimeFreshness.CURRENT, false).reasonCode,
            )
        }
    }

    @Test
    fun unknownFuelIsExplicitlyUnknownAndNeverConvertedToPetrolOrZero() {
        val result = LearningFuelEligibility.evaluate(Mp48Fuel.UNKNOWN, RuntimeFreshness.CURRENT, true)
        assertFalse(result.eligible)
        assertEquals("FUEL_UNKNOWN", result.reasonCode)
    }
}
