package com.omegas.prohub.calibration

import com.omegas.prohub.session.CalibrationRevision
import com.omegas.prohub.session.CalibrationShape
import com.omegas.prohub.session.CalibrationState
import com.omegas.prohub.session.CalibrationTransition
import com.omegas.prohub.session.CausalTransitionStatus
import com.omegas.prohub.session.SuggestionTarget
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvisorSuggestionAdapterCausalStepTest {
    @Test
    fun confirmedCausalResponseRaisesOnlyTheSameMapCellFrom075To090() {
        val adapter = AdvisorSuggestionAdapter()
        val calibration = calibration()
        val advice = mapAdvice(row = 4, column = 0, errorPercent = 10.0, suggestedPercent = 7.5)

        val baseline = adapter.adapt(advice, calibration, nowMs = 200L)
            .single { it.target == SuggestionTarget.MAP_K }
        assertEquals(108, baseline.mapChanges.single().after)

        val confirmed = transition(
            status = CausalTransitionStatus.CONFIRMED,
            appliedAtMs = 100L,
            cell = "4:0",
        )
        val escalated = adapter.adapt(
            advice = advice,
            calibration = calibration,
            nowMs = 200L,
            causalTransitions = listOf(confirmed),
        ).single { it.target == SuggestionTarget.MAP_K }

        assertEquals(109, escalated.mapChanges.single().after)
    }

    @Test
    fun contradictedOrUnrelatedTransitionCannotUnlock090() {
        val adapter = AdvisorSuggestionAdapter()
        val calibration = calibration()
        val advice = mapAdvice(row = 4, column = 0, errorPercent = 10.0, suggestedPercent = 7.5)
        val contradicted = transition(
            status = CausalTransitionStatus.CONTRADICTED,
            appliedAtMs = 200L,
            cell = "4:0",
        )
        val olderConfirmed = transition(
            status = CausalTransitionStatus.CONFIRMED,
            appliedAtMs = 100L,
            cell = "4:0",
        )
        val unrelatedConfirmed = transition(
            status = CausalTransitionStatus.CONFIRMED,
            appliedAtMs = 300L,
            cell = "5:0",
        )

        val result = adapter.adapt(
            advice = advice,
            calibration = calibration,
            nowMs = 400L,
            causalTransitions = listOf(olderConfirmed, contradicted, unrelatedConfirmed),
        ).single { it.target == SuggestionTarget.MAP_K }

        assertEquals(108, result.mapChanges.single().after)
    }

    private fun calibration(): CalibrationState = CalibrationState(
        revision = CalibrationRevision(curveK = 2, mapK = 5),
        curveK = List(CalibrationShape.CURVE_K_POINTS) { 1.0 },
        mapK = List(CalibrationShape.MAP_K_STORAGE_ROWS) {
            List(CalibrationShape.MAP_K_COLUMNS) { 100 }
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
        status: CausalTransitionStatus,
        appliedAtMs: Long,
        cell: String,
    ): CalibrationTransition = CalibrationTransition(
        suggestionId = "prior-$appliedAtMs-$cell",
        target = SuggestionTarget.MAP_K,
        appliedAtMs = appliedAtMs,
        beforeRevision = CalibrationRevision(curveK = 2, mapK = 4),
        afterRevision = CalibrationRevision(curveK = 2, mapK = 5),
        beforeFingerprint = "before-$appliedAtMs",
        afterFingerprint = "after-$appliedAtMs",
        preErrorPercent = 10.0,
        postErrorPercent = 2.5,
        responseGain = if (status == CausalTransitionStatus.CONFIRMED) 1.0 else -0.5,
        mapCells = listOf(cell),
        status = status,
    )
}
