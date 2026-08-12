package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetrolReferenceSelectorUnknownTemperatureTest {
    private val policy = LearningTolerancePolicy()

    @Test
    fun unknown_temperature_does_not_block_local_rpm_map_match() {
        val result = PetrolReferenceSelector.estimate(
            regions = listOf(
                PetrolReferenceSelector.Region(
                    id = "petrol-local",
                    rpm = 2_500.0,
                    mapBar = 0.52,
                    waterC = -273.15,
                    petrolMs = 4.80,
                    confidence = 0.90,
                    sampleCount = 30,
                ),
            ),
            request = PetrolReferenceSelector.Request(
                rpm = 2_540.0,
                mapBar = 0.55,
                waterC = -273.15,
            ),
            policy = policy,
        )

        assertTrue(result.toJson().toString(), result.available)
        assertEquals(4.80, result.petrolTargetMs ?: 0.0, 1e-9)
        assertFalse(result.temperatureCompared)
        assertEquals(0.0, result.nearestWaterDelta ?: -1.0, 1e-9)
    }

    @Test
    fun one_missing_temperature_disables_only_temperature_dimension() {
        val result = PetrolReferenceSelector.estimate(
            regions = listOf(
                PetrolReferenceSelector.Region(
                    id = "petrol-local",
                    rpm = 1_850.0,
                    mapBar = 0.46,
                    waterC = 88.0,
                    petrolMs = 3.95,
                    confidence = 0.80,
                    sampleCount = 15,
                ),
            ),
            request = PetrolReferenceSelector.Request(
                rpm = 1_900.0,
                mapBar = 0.49,
                waterC = -273.15,
            ),
            policy = policy,
        )

        assertTrue(result.toJson().toString(), result.available)
        assertFalse(result.temperatureCompared)
    }

    @Test
    fun clearly_distant_rpm_and_map_are_still_rejected_without_temperature() {
        val result = PetrolReferenceSelector.estimate(
            regions = listOf(
                PetrolReferenceSelector.Region(
                    id = "petrol-far",
                    rpm = 900.0,
                    mapBar = 0.20,
                    waterC = -273.15,
                    petrolMs = 4.0,
                    confidence = 1.0,
                    sampleCount = 100,
                ),
            ),
            request = PetrolReferenceSelector.Request(
                rpm = 3_000.0,
                mapBar = 0.90,
                waterC = -273.15,
            ),
            policy = policy,
        )

        assertFalse(result.available)
        assertEquals("NO_LOCAL_PETROL_REFERENCE", result.reasonCode)
    }
}
