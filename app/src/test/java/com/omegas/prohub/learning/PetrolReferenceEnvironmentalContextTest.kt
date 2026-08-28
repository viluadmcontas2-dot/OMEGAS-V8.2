package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetrolReferenceEnvironmentalContextTest {
    private val policy = LearningTolerancePolicy(
        historicalRpmMinimum = 100.0,
        historicalRpmPercent = 5.0,
        historicalMapBar = 0.10,
        historicalTemperatureC = 10.0,
        referenceMaximumSpreadMs = 0.35,
        confidenceSampleTarget = 6,
    )

    @Test
    fun missingWaterIsUnknownAndNeverBecomesZeroDegrees() {
        val region = PetrolReferenceSelector.Region(
            id = "petrol",
            rpm = 2_000.0,
            mapBar = 0.50,
            waterC = -273.15,
            petrolMs = 4.0,
            confidence = 0.9,
            sampleCount = 10,
        )
        val request = PetrolReferenceSelector.Request(
            rpm = 2_000.0,
            mapBar = 0.50,
            waterC = 80.0,
        )
        val result = PetrolReferenceSelector.estimate(listOf(region), request, policy)
        assertTrue(result.available)
        assertFalse(result.temperatureCompared)
        val stored = result.selectedRegionContexts.single().environment
        assertFalse(stored.waterKnown())
        assertEquals(-273.15, stored.waterC!!, 0.0)
        assertEquals(80.0, result.requestEnvironment!!.waterC!!, 0.0)
    }

    @Test
    fun knownWaterRemainsComparableContext() {
        val region = PetrolReferenceSelector.Region(
            id = "petrol",
            rpm = 2_000.0,
            mapBar = 0.50,
            waterC = 82.0,
            petrolMs = 4.0,
            confidence = 0.9,
            sampleCount = 10,
        )
        val request = PetrolReferenceSelector.Request(2_010.0, 0.51, 84.0)
        val result = PetrolReferenceSelector.estimate(listOf(region), request, policy)
        assertTrue(result.available)
        assertTrue(result.temperatureCompared)
        assertEquals(2.0, result.nearestWaterDelta!!, 1e-9)
    }

    @Test
    fun gasTemperatureIsPreservedButDoesNotBecomeNativeGate() {
        fun environment(gasC: Double) = PetrolReferenceSelector.EnvironmentalContext(
            waterC = 82.0,
            waterFreshness = PetrolReferenceSelector.ContextFreshness.OBSERVED,
            waterSource = "LANDI_ECU_REGION",
            gasTemperatureC = gasC,
            gasTemperatureFreshness = PetrolReferenceSelector.ContextFreshness.OBSERVED,
            gasTemperatureSource = "MP48_RUNTIME_GAS_TEMP",
        )
        val cold = PetrolReferenceSelector.Region(
            id = "cold",
            rpm = 2_000.0,
            mapBar = 0.50,
            waterC = 82.0,
            petrolMs = 4.0,
            confidence = 0.9,
            sampleCount = 10,
            environment = environment(20.0),
        )
        val hot = PetrolReferenceSelector.Region(
            id = "hot",
            rpm = 2_000.0,
            mapBar = 0.50,
            waterC = 82.0,
            petrolMs = 4.0,
            confidence = 0.9,
            sampleCount = 10,
            environment = environment(60.0),
        )
        val request = PetrolReferenceSelector.Request(
            rpm = 2_000.0,
            mapBar = 0.50,
            waterC = 82.0,
            environment = PetrolReferenceSelector.EnvironmentalContext(
                waterC = 82.0,
                waterFreshness = PetrolReferenceSelector.ContextFreshness.CURRENT,
                waterSource = "LANDI_ECU_CURRENT",
                gasTemperatureC = 55.0,
                gasTemperatureFreshness = PetrolReferenceSelector.ContextFreshness.CURRENT,
                gasTemperatureSource = "MP48_RUNTIME_GAS_TEMP",
            ),
        )
        val result = PetrolReferenceSelector.estimate(listOf(cold, hot), request, policy)
        assertTrue(result.available)
        assertEquals(4.0, result.petrolTargetMs!!, 1e-9)
        assertEquals(55.0, result.requestEnvironment!!.gasTemperatureC!!, 0.0)
        val temperatures = result.selectedRegionContexts.mapNotNull { it.environment.gasTemperatureC }.sorted()
        assertEquals(listOf(20.0, 60.0), temperatures)
        assertFalse(result.toJson().getBoolean("gas_temperature_used_as_native_gate"))
    }

    @Test
    fun pressureAndMapProvenanceSurviveWithoutInventingPressureGate() {
        val environment = PetrolReferenceSelector.EnvironmentalContext(
            waterC = 82.0,
            waterFreshness = PetrolReferenceSelector.ContextFreshness.OBSERVED,
            waterSource = "LANDI_ECU_REGION",
            pressureDiffBar = 1.25,
            gasPressureAbsBar = 1.75,
            pressureFreshness = PetrolReferenceSelector.ContextFreshness.OBSERVED,
            pressureSource = "MP48_RUNTIME_PRESSURE",
            mapSource = "MP48_RUNTIME_MAP",
        )
        val region = PetrolReferenceSelector.Region(
            id = "petrol",
            rpm = 2_000.0,
            mapBar = 0.50,
            waterC = 82.0,
            petrolMs = 4.0,
            confidence = 0.9,
            sampleCount = 10,
            environment = environment,
        )
        val request = PetrolReferenceSelector.Request(
            rpm = 2_000.0,
            mapBar = 0.50,
            waterC = 82.0,
            environment = environment.copy(
                pressureDiffBar = 1.30,
                gasPressureAbsBar = 1.80,
                pressureFreshness = PetrolReferenceSelector.ContextFreshness.CURRENT,
            ),
        )
        val result = PetrolReferenceSelector.estimate(listOf(region), request, policy)
        assertTrue(result.available)
        assertEquals(1.30, result.requestEnvironment!!.pressureDiffBar!!, 0.0)
        assertEquals(1.80, result.requestEnvironment!!.gasPressureAbsBar!!, 0.0)
        assertEquals("MP48_RUNTIME_MAP", result.requestEnvironment!!.mapSource)
        assertEquals(1.25, result.selectedRegionContexts.single().environment.pressureDiffBar!!, 0.0)
        assertFalse(result.toJson().getBoolean("pressure_used_as_native_gate"))
    }
}
