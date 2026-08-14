package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CellSemanticSnapshotTest {
    @Test
    fun `valor atual e referencia equivalente permanecem objetos diferentes`() {
        val result = CellSemanticProjection.build(
            CellSemanticProjection.Input(
                row = 4,
                column = 3,
                currentRpm = 1_850.0,
                currentPetrolMs = 4.50,
                currentMapBar = 0.62,
                currentFuel = "CNG",
                gasolineReferenceMs = 5.18,
                gasolineReferenceState = CellSemanticProjection.EvidenceState.CONSOLIDATED,
                gasolineReferenceConfidence = 0.88,
                observedPetrolOnCngMs = 5.72,
                comparisonQuality = 0.82,
                comparisonStage = CellSemanticProjection.EvidenceState.CONSOLIDATED,
                comparisonReason = "Condição equivalente por RPM, MAP e temperatura",
                currentK = 120.0,
                targetK = 132.0,
            ),
        )

        val current = result.getJSONObject("currentCondition").getJSONObject("petrolInjection")
        val reference = result.getJSONObject("gasolineEquivalentReference")
        val cng = result.getJSONObject("cngObservation")
        val comparison = result.getJSONObject("comparison")
        val calibration = result.getJSONObject("calibration")

        assertEquals("CURRENT_CONDITION", current.getString("role"))
        assertEquals(4.50, current.getDouble("value"), 0.0001)
        assertEquals("GASOLINE_EQUIVALENT_REFERENCE", reference.getString("role"))
        assertEquals(5.18, reference.getDouble("value"), 0.0001)
        assertEquals("CNG_OBSERVATION", cng.getString("role"))
        assertEquals(5.72, cng.getDouble("value"), 0.0001)
        assertTrue(comparison.getBoolean("comparable"))
        assertEquals(0.54, comparison.getDouble("differenceMs"), 0.0001)
        assertEquals("INCREASE_CNG_DELIVERY", comparison.getString("direction"))
        assertEquals("CALIBRATION_K", calibration.getString("role"))
        assertEquals(120.0, calibration.getDouble("currentK"), 0.0001)
        assertFalse(calibration.getBoolean("automaticWrite"))
        assertTrue(calibration.getBoolean("humanConfirmationRequired"))
        assertFalse(result.getJSONObject("cell").getBoolean("isMeasurement"))
    }

    @Test
    fun `sem referencia equivalente nao inventa rico pobre nem ajuste`() {
        val result = CellSemanticProjection.build(
            CellSemanticProjection.Input(
                row = 1,
                column = 2,
                currentRpm = 1_300.0,
                currentPetrolMs = 3.20,
                currentMapBar = 0.45,
                currentFuel = "CNG",
                gasolineReferenceMs = null,
                observedPetrolOnCngMs = 3.65,
            ),
        )
        val comparison = result.getJSONObject("comparison")
        assertFalse(comparison.getBoolean("comparable"))
        assertEquals("UNKNOWN", comparison.getString("direction"))
        assertTrue(comparison.isNull("differenceMs"))
        assertTrue(comparison.isNull("differencePct"))
    }

    @Test
    fun `OBD stale desaparece em vez de parecer atual`() {
        val result = CellSemanticProjection.build(
            CellSemanticProjection.Input(
                row = 0,
                column = 0,
                currentRpm = 900.0,
                currentPetrolMs = 4.1,
                currentMapBar = 0.34,
                currentFuel = "PETROL",
                gasolineReferenceMs = 4.1,
                observedPetrolOnCngMs = null,
                obdTrimPct = 12.5,
                obdFresh = false,
            ),
        )
        val obd = result.getJSONObject("obdWitness")
        assertFalse(obd.getBoolean("fresh"))
        assertTrue(obd.isNull("trimPct"))
        assertTrue(obd.getBoolean("observationalOnly"))
    }
}
