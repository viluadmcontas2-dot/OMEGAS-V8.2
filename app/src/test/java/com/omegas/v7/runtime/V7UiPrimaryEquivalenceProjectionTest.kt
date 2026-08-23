package com.omegas.v7.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V7UiPrimaryEquivalenceProjectionTest {
    @Test
    fun comparisonExplanationDoesNotPresentTemperatureAsPrimaryMatchingAuthority() {
        val revision = CalibrationRevisionV7(0, 0)
        val state = V7SessionState(
            sessionId = "ui-primary-equivalence",
            calibration = CalibrationStateV7(
                revision = revision,
                curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
                mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
                    List(CalibrationShapeV7.MAP_K_COLUMNS) { 110 }
                },
            ),
            comparisons = listOf(
                FuelComparisonV7(
                    id = "0:0:cng-1",
                    revision = revision,
                    cngVisitId = "cng-1",
                    petrolEvidenceIds = listOf("petrol-1"),
                    rpm = 1_500.0,
                    mapBar = 0.50,
                    waterC = 100.0,
                    petrolTargetMs = 3.80,
                    petrolOnCngMs = 4.20,
                    differenceMs = 0.40,
                    errorPercent = 10.526315789,
                    direction = "INCREASE_CNG_DELIVERY",
                    quality = 0.9,
                    createdAtMs = 10L,
                ),
            ),
        )

        val explanation = V7UiProjection.from(state).learning.explanation

        assertTrue(explanation.contains("RPM e MAP"))
        assertFalse(explanation.contains("temperatura", ignoreCase = true))
    }
}
