package com.omegas.v7.runtime

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningStabilityJsonV7Test {
    @Test
    fun `projecao e reutilizada ate a lista cientifica de comparacoes mudar`() {
        val runtime = runtime()

        val emptyFirst = LearningStabilityJsonV7.from(runtime)
        val emptySecond = LearningStabilityJsonV7.from(runtime)
        assertSame(emptyFirst, emptySecond)
        assertEquals(0, emptyFirst.getInt("activeComparisons"))

        runtime.addEvidence(petrol())
        val petrolOnly = LearningStabilityJsonV7.from(runtime)
        // Gasolina isolada não cria comparação; a projeção científica é a mesma.
        assertSame(emptyFirst, petrolOnly)

        runtime.addEvidence(cng())
        val compared = LearningStabilityJsonV7.from(runtime)
        assertNotSame(emptyFirst, compared)
        assertEquals(1, compared.getInt("activeComparisons"))

        val comparedAgain = LearningStabilityJsonV7.from(runtime)
        assertSame(compared, comparedAgain)
    }

    private fun runtime(): V7SessionRuntime = V7SessionRuntime(
        V7SessionState(
            sessionId = "stability-json-cache",
            calibration = CalibrationStateV7(
                revision = CalibrationRevisionV7(0, 0),
                curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
                mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
                    List(CalibrationShapeV7.MAP_K_COLUMNS) { 100 }
                },
            ),
        ),
    )

    private fun petrol(): EvidenceV7 = EvidenceV7(
        id = "petrol",
        fuel = FuelV7.PETROL,
        collectedAtMs = 10,
        visitId = "petrol",
        rpm = 1_850.0,
        mapBar = 0.50,
        petrolMs = 4.0,
        quality = 1.0,
        cngRevision = null,
        waterC = 82.0,
    )

    private fun cng(): EvidenceV7 = EvidenceV7(
        id = "cng",
        fuel = FuelV7.CNG,
        collectedAtMs = 20,
        visitId = "cng",
        rpm = 1_850.0,
        mapBar = 0.50,
        petrolMs = 4.5,
        quality = 1.0,
        cngRevision = CalibrationRevisionV7(0, 0),
        waterC = 82.0,
    )
}
