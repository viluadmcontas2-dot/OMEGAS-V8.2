package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetrolReferenceEnvironmentAdversarialTest {
    @Test
    fun gasTemperatureAndPressureRemainVisibleWithoutPretendingToBeNativeSelectorGates() {
        val region = PetrolReferenceSelector.Region(
            id = "petrol-ref",
            rpm = 2_000.0,
            mapBar = 0.55,
            waterC = 90.0,
            petrolMs = 4.20,
            confidence = 0.95,
            sampleCount = 20,
            environment = PetrolReferenceEnvironmentBridge.region(
                waterC = 90.0,
                gasTemperatureC = 30.0,
                pressureDiffBar = 1.05,
            ),
        )

        val cool = PetrolReferenceSelector.estimate(
            regions = listOf(region),
            request = PetrolReferenceSelector.Request(
                rpm = 2_000.0,
                mapBar = 0.55,
                waterC = 90.0,
                environment = PetrolReferenceEnvironmentBridge.request(90.0, 32.0, 1.10),
            ),
        )
        val hotHighPressure = PetrolReferenceSelector.estimate(
            regions = listOf(region),
            request = PetrolReferenceSelector.Request(
                rpm = 2_000.0,
                mapBar = 0.55,
                waterC = 90.0,
                environment = PetrolReferenceEnvironmentBridge.request(90.0, 75.0, 1.80),
            ),
        )

        assertTrue(cool.available)
        assertTrue(hotHighPressure.available)
        assertEquals(cool.petrolTargetMs, hotHighPressure.petrolTargetMs)
        assertNotEquals(cool.requestEnvironment?.gasTemperatureC, hotHighPressure.requestEnvironment?.gasTemperatureC)
        assertNotEquals(cool.requestEnvironment?.pressureDiffBar, hotHighPressure.requestEnvironment?.pressureDiffBar)
        assertTrue(hotHighPressure.requestEnvironment!!.pressureSource.startsWith("NATIVE_ANCHORED:"))

        val json = hotHighPressure.toJson()
        assertFalse(json.getBoolean("gas_temperature_used_as_native_gate"))
        assertFalse(json.getBoolean("pressure_used_as_native_gate"))
        assertTrue(
            ScientificAuthorityRegistry.referenceSelectorComparability.token()
                .startsWith("OMEGAS_COMPARABILITY_POLICY:"),
        )
    }

    @Test
    fun unavailableEnvironmentDoesNotBecomeSyntheticZero() {
        val context = PetrolReferenceEnvironmentBridge.request(
            waterC = 90.0,
            gasTemperatureC = Double.NaN,
            pressureDiffBar = Double.NaN,
        )
        assertEquals(null, context.gasTemperatureC)
        assertEquals(null, context.pressureDiffBar)
        assertEquals(PetrolReferenceSelector.ContextKnownness.UNAVAILABLE, context.gasTemperatureKnownness)
        assertEquals(PetrolReferenceSelector.ContextKnownness.UNAVAILABLE, context.pressureKnownness)
    }
}
