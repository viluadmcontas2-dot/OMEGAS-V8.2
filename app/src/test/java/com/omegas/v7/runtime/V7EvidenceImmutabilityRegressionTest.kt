package com.omegas.v7.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class V7EvidenceImmutabilityRegressionTest {
    @Test
    fun `visita historica nao pode ser reinterpretada por snapshot posterior`() {
        val runtime = V7SessionRuntime(initialState())
        val first = EvidenceV7(
            id = "petrol:visit-1:first",
            fuel = FuelV7.PETROL,
            collectedAtMs = 1_000L,
            visitId = "visit-1",
            rpm = 1_850.0,
            mapBar = 0.61,
            petrolMs = 4.40,
            quality = 0.90,
            cngRevision = null,
            waterC = 82.0,
            gasC = 31.0,
            pressureDiffBar = 1.20,
        )
        val reinterpreted = first.copy(
            id = "petrol:visit-1:later-region-mean",
            collectedAtMs = 2_000L,
            rpm = 1_930.0,
            mapBar = 0.64,
            petrolMs = 4.86,
            quality = 0.95,
        )

        runtime.addEvidence(first)
        runtime.addEvidence(reinterpreted)

        val stored = runtime.state.petrolEvidence.single()
        assertEquals("O primeiro registro fisico da visita deve permanecer a autoridade", first.id, stored.id)
        assertEquals(first.collectedAtMs, stored.collectedAtMs)
        assertEquals(first.rpm, stored.rpm, 0.0)
        assertEquals(first.mapBar, stored.mapBar, 0.0)
        assertEquals(first.petrolMs, stored.petrolMs, 0.0)
        assertEquals(first.quality, stored.quality, 0.0)
    }

    private fun initialState(): V7SessionState = V7SessionState(
        sessionId = "immutability-regression",
        calibration = CalibrationStateV7(
            revision = CalibrationRevisionV7(curveK = 0, mapK = 0),
            curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
            mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
                List(CalibrationShapeV7.MAP_K_COLUMNS) { 100 }
            },
        ),
    )
}
