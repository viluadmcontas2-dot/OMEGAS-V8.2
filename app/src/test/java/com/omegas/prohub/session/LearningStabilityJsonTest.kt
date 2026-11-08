package com.omegas.prohub.session

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningStabilityJsonTest {
    @Test
    fun `projecao e reutilizada ate a lista cientifica de comparacoes mudar`() {
        val runtime = runtime()

        val emptyFirst = LearningStabilityJson.from(runtime)
        val emptySecond = LearningStabilityJson.from(runtime)
        assertSame(emptyFirst, emptySecond)
        assertEquals(0, emptyFirst.getInt("activeComparisons"))

        runtime.addEvidence(petrol())
        val petrolOnly = LearningStabilityJson.from(runtime)
        // Gasolina isolada não cria comparação; a projeção científica é a mesma.
        assertSame(emptyFirst, petrolOnly)

        runtime.addEvidence(cng())
        val compared = LearningStabilityJson.from(runtime)
        assertNotSame(emptyFirst, compared)
        assertEquals(1, compared.getInt("activeComparisons"))

        val comparedAgain = LearningStabilityJson.from(runtime)
        assertSame(compared, comparedAgain)
    }

    private fun runtime(): SessionRuntime = SessionRuntime(
        SessionState(
            sessionId = "stability-json-cache",
            calibration = CalibrationState(
                revision = CalibrationRevision(0, 0),
                curveK = List(CalibrationShape.CURVE_K_POINTS) { 1.0 },
                mapK = List(CalibrationShape.MAP_K_STORAGE_ROWS) {
                    List(CalibrationShape.MAP_K_COLUMNS) { 100 }
                },
            ),
        ),
    )

    private fun petrol(): SessionEvidence = SessionEvidence(
        id = "petrol",
        fuel = CalibrationFuel.PETROL,
        collectedAtMs = 10,
        visitId = "petrol",
        rpm = 1_850.0,
        mapBar = 0.50,
        petrolMs = 4.0,
        quality = 1.0,
        cngRevision = null,
        waterC = 82.0,
    )

    private fun cng(): SessionEvidence = SessionEvidence(
        id = "cng",
        fuel = CalibrationFuel.CNG,
        collectedAtMs = 20,
        visitId = "cng",
        rpm = 1_850.0,
        mapBar = 0.50,
        petrolMs = 4.5,
        quality = 1.0,
        cngRevision = CalibrationRevision(0, 0),
        waterC = 82.0,
    )
}
