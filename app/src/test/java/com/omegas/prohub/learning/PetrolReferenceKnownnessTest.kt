package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetrolReferenceKnownnessTest {
    @Test
    fun knownnessSeparatesKnownUnavailableStaleAndImplausible() {
        val known = PetrolReferenceSelector.EnvironmentalContext(
            waterC = 90.0,
            waterFreshness = PetrolReferenceSelector.ContextFreshness.CURRENT,
            pressureDiffBar = 1.25,
            pressureFreshness = PetrolReferenceSelector.ContextFreshness.CURRENT,
        )
        assertEquals(PetrolReferenceSelector.ContextKnownness.KNOWN, known.waterKnownness)
        assertEquals(PetrolReferenceSelector.ContextKnownness.KNOWN, known.pressureKnownness)
        assertTrue(known.waterKnown())
        assertTrue(known.pressureKnown())

        val unavailable = PetrolReferenceSelector.EnvironmentalContext()
        assertEquals(PetrolReferenceSelector.ContextKnownness.UNAVAILABLE, unavailable.waterKnownness)
        assertEquals(PetrolReferenceSelector.ContextKnownness.UNAVAILABLE, unavailable.pressureKnownness)
        assertFalse(unavailable.waterKnown())

        val stale = PetrolReferenceSelector.EnvironmentalContext(
            gasTemperatureC = 42.0,
            gasTemperatureFreshness = PetrolReferenceSelector.ContextFreshness.STALE,
        )
        assertEquals(PetrolReferenceSelector.ContextKnownness.STALE, stale.gasTemperatureKnownness)
        assertFalse(stale.gasTemperatureKnown())

        val implausibleWater = PetrolReferenceSelector.EnvironmentalContext(
            waterC = -273.15,
            waterFreshness = PetrolReferenceSelector.ContextFreshness.CURRENT,
        )
        assertEquals(PetrolReferenceSelector.ContextKnownness.IMPLAUSIBLE, implausibleWater.waterKnownness)
        assertFalse(implausibleWater.waterKnown())

        val implausiblePressure = PetrolReferenceSelector.EnvironmentalContext(
            pressureDiffBar = Double.POSITIVE_INFINITY,
            pressureFreshness = PetrolReferenceSelector.ContextFreshness.CURRENT,
        )
        assertEquals(PetrolReferenceSelector.ContextKnownness.IMPLAUSIBLE, implausiblePressure.pressureKnownness)
        assertFalse(implausiblePressure.pressureKnown())
    }

    @Test
    fun knownnessIsSerializedSeparatelyFromFreshnessAndSource() {
        val json = PetrolReferenceSelector.EnvironmentalContext(
            waterC = 88.0,
            waterFreshness = PetrolReferenceSelector.ContextFreshness.OBSERVED,
            waterSource = "LANDI_ECU_REGION",
            gasTemperatureC = 37.0,
            gasTemperatureFreshness = PetrolReferenceSelector.ContextFreshness.STALE,
            pressureDiffBar = 1.10,
            pressureFreshness = PetrolReferenceSelector.ContextFreshness.CURRENT,
            pressureSource = "NATIVE_ANCHORED:E4",
        ).toJson()

        assertEquals("KNOWN", json.getString("water_knownness"))
        assertEquals("OBSERVED", json.getString("water_freshness"))
        assertEquals("STALE", json.getString("gas_temperature_knownness"))
        assertEquals("KNOWN", json.getString("pressure_knownness"))
        assertEquals("NATIVE_ANCHORED:E4", json.getString("pressure_source"))
    }
}
