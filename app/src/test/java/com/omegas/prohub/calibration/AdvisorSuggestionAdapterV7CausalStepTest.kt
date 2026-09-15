package com.omegas.prohub.calibration

import com.omegas.v7.runtime.CalibrationRevisionV7
import com.omegas.v7.runtime.CalibrationShapeV7
import com.omegas.v7.runtime.CalibrationStateV7
import com.omegas.v7.runtime.CalibrationTransitionV7
import com.omegas.v7.runtime.CausalTransitionStatusV7
import com.omegas.v7.runtime.SuggestionTargetV7
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvisorSuggestionAdapterV7CausalStepTest {
    @Test
    fun confirmedCausalResponseRaisesOnlyTheSameMapCellFrom075To090() {
        val adapter = AdvisorSuggestionAdapterV7()
        val calibration = calibration()
        val advice = mapAdvice(row = 4, column = 0, errorPercent = 10.0, suggestedPercent = 7.5)

        val baseline = adapter.adapt(advice, calibration, nowMs = 200L)
            .single { it.target == SuggestionTargetV7.MAP_K }
        assertEquals(108, baseline.mapChanges.single().after)

        val confirmed = transition(
            status = CausalTransitionStatusV7.CONFIRMED,
            appliedAtMs = 100L,
            cell = "4:0",
        )
        val escalated = adapter.adapt(
            advice = advice,
            calibration = calibration,
            nowMs = 200L,
            causalTransitions = listOf(confirmed),
        ).single { it.target == SuggestionTargetV7.MAP_K }

        assertEquals(109, escalated.mapChanges.single().after)
    }

    @Test
    fun contradictedOrUnrelatedTransitionCannotUnlock090() {
        val adapter = AdvisorSuggestionAdapterV7()
        val calibration = calibration()
        val advice = mapAdvice(row = 4, column = 0, errorPercent = 10.0, suggestedPercent = 7.5)
        val contradicted = transition(
            status = CausalTransitionStatusV7.CONTRADICTED,
            appliedAtMs = 200L,
            cell = "4:0",
        )
        val olderConfirmed = transition(
            status = CausalTransitionStatusV7.CONFIRMED,
            appliedAtMs = 100L,
            cell = "4:0",
        )
        val unrelatedConfirmed = transition(
            status = CausalTransitionStatusV7.CONFIRMED,
            appliedAtMs = 300L,
            cell = "5:0",
        )

        val result = adapter.adapt(
            advice = advice,
            calibration = calibration,
            nowMs = 400L,
            causalTransitions = listOf(olderConfirmed, contradicted, unrelatedConfirmed),
        ).single { it.target == SuggestionTargetV7.MAP_K }

        assertEquals(108, result.mapChanges.single().after)
    }

    private fun calibration(): CalibrationStateV7 = CalibrationStateV7(
        revision = CalibrationRevisionV7(curveK = 2, mapK = 5),
        curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
        mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
            List(CalibrationShapeV7.MAP_K_COLUMNS) { 100 }
        },
    )

    private fun mapAdvice(
        row: Int,
        column: Int,
        errorPercent: Double,
        suggestedPercent: Double,
    ): JSONObject = JSONObject()
        .put("kFactorSuggestions", JSONArray())
        .put("mapCorrectionRegions", JSONArray())
        .put(
            "mapResidualSuggestions",
            JSONArray().put(
                JSONObject()
                    .put("row", row)
                    .put("column", column)
                    .put("confidence", 0.90)
                    .put("actionable", true)
                    .put("residualErrorPercent", errorPercent)
                    .put("suggestedDeltaPercent", suggestedPercent)
                    .put("decisionReason", "erro residual consolidado"),
            ),
        )

    private fun transition(
        status: CausalTransitionStatusV7,
        appliedAtMs: Long,
        cell: String,
    ): CalibrationTransitionV7 = CalibrationTransitionV7(
        suggestionId = "prior-$appliedAtMs-$cell",
        target = SuggestionTargetV7.MAP_K,
        appliedAtMs = appliedAtMs,
        beforeRevision = CalibrationRevisionV7(curveK = 2, mapK = 4),
        afterRevision = CalibrationRevisionV7(curveK = 2, mapK = 5),
        beforeFingerprint = "before-$appliedAtMs",
        afterFingerprint = "after-$appliedAtMs",
        preErrorPercent = 10.0,
        postErrorPercent = 2.5,
        responseGain = if (status == CausalTransitionStatusV7.CONFIRMED) 1.0 else -0.5,
        mapCells = listOf(cell),
        status = status,
    )
}
