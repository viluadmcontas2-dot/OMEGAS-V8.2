package com.omegas.prohub.calibration

import com.omegas.prohub.session.CalibrationRevision
import com.omegas.prohub.session.CalibrationShape
import com.omegas.prohub.session.CalibrationState
import com.omegas.prohub.session.CalibrationTransition
import com.omegas.prohub.session.CalibrationWriteResult
import com.omegas.prohub.session.CalibrationWriter
import com.omegas.prohub.session.CausalTransitionStatus
import com.omegas.prohub.session.SessionEvidence
import com.omegas.prohub.session.CalibrationFuel
import com.omegas.prohub.session.LearningStabilityState
import com.omegas.prohub.session.SuggestionLifecycle
import com.omegas.prohub.session.SuggestionTarget
import com.omegas.prohub.session.SessionRuntime
import com.omegas.prohub.session.SessionState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorSuggestionAdapterTest {
    private fun calibration() = CalibrationState(
        revision = CalibrationRevision(2, 5),
        curveK = List(CalibrationShape.CURVE_K_POINTS) { 1.0 },
        mapK = List(CalibrationShape.MAP_K_STORAGE_ROWS) {
            List(CalibrationShape.MAP_K_COLUMNS) { 110 }
        },
    )

    @Test
    fun map_advice_becomes_one_persistent_entity_per_editable_cell() {
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray()
                .put(mapItem(0, 0, 10.0))
                .put(mapItem(12, 0, 20.0)))
            .put("mapCorrectionRegions", JSONArray()
                .put(JSONObject()
                    .put("id", "MAP-01")
                    .put("actionable", true)
                    .put("cells", JSONArray()
                        .put(JSONObject().put("row", 0).put("column", 0))
                        .put(JSONObject().put("row", 12).put("column", 0)))))

        val suggestions = AdvisorSuggestionAdapter().adapt(advice, calibration(), nowMs = 100)

        val map = suggestions.single { it.target == SuggestionTarget.MAP_K }
        val change = map.mapChanges.single()
        assertEquals(0, change.row)
        assertEquals(0, change.column)
        assertEquals(110, change.before)
        assertEquals(121, change.after)
        assertEquals(SuggestionLifecycle.PENDING, map.lifecycle)
        assertTrue(map.id.startsWith("advisor-map-"))
    }

    @Test
    fun seventeen_cells_become_seventeen_selectable_suggestions_not_writer_chunks() {
        val residual = JSONArray()
        repeat(17) { index -> residual.put(mapItem(index / 12, index % 12, 10.0)) }
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", residual)
            .put("mapCorrectionRegions", JSONArray())

        val suggestions = AdvisorSuggestionAdapter().adapt(advice, calibration(), nowMs = 100)
            .filter { it.target == SuggestionTarget.MAP_K }

        assertEquals(17, suggestions.size)
        assertTrue(suggestions.all { it.mapChanges.size == 1 })
        assertEquals(17, suggestions.flatMap { it.mapChanges }.distinctBy { it.row to it.column }.size)
    }

    @Test
    fun same_physical_cell_keeps_id_when_raw_candidate_magnitude_changes() {
        val first = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(mapItem(2, 4, 5.0)))
        val second = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(mapItem(2, 4, 12.0)))

        val a = AdvisorSuggestionAdapter().adapt(first, calibration(), nowMs = 100).single()
        val b = AdvisorSuggestionAdapter().adapt(second, calibration(), nowMs = 200).single()

        assertEquals(a.id, b.id)
        assertTrue(a.mapChanges.single().after != b.mapChanges.single().after)
    }

    @Test
    fun non_actionable_cell_is_preserved_as_observing_without_old_value_being_actionable() {
        val item = JSONObject()
            .put("row", 2)
            .put("column", 4)
            .put("actionable", false)
            .put("confidence", 0.72)
            .put("decisionReason", "Incerteza ainda cobre o benefício")
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(item))

        val suggestion = AdvisorSuggestionAdapter().adapt(advice, calibration(), nowMs = 100).single()

        assertEquals(SuggestionLifecycle.OBSERVING, suggestion.lifecycle)
        assertTrue(suggestion.mapChanges.isEmpty())
        assertEquals(0.72, suggestion.confidence, 0.0)
    }

    @Test
    fun curve_advice_is_quantized_to_real_q14_values_and_id_is_stable() {
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray()
                .put(JSONObject()
                    .put("index", 3)
                    .put("actionable", true)
                    .put("suggestedDeltaPercent", 8.0)
                    .put("confidence", 0.9)
                    .put("readiness", "AVAILABLE")))
            .put("mapResidualSuggestions", JSONArray())
            .put("mapCorrectionRegions", JSONArray())

        val suggestion = AdvisorSuggestionAdapter().adapt(advice, calibration(), nowMs = 100)
            .single { it.target == SuggestionTarget.CURVE_K }

        val change = suggestion.curveChanges.single()
        assertEquals(3, change.index)
        assertEquals(1.0, change.before, 1e-12)
        assertEquals(1.08, change.after, 0.0001)
        assertEquals(SuggestionLifecycle.PENDING, suggestion.lifecycle)
    }

    @Test
    fun global_curve_uses_bounded_step_and_explains_ideal_target_and_residual() {
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray()
                .put(JSONObject()
                    .put("index", 3)
                    .put("actionable", true)
                    .put("idealDeltaPercent", 20.0)
                    .put("suggestedDeltaPercent", 10.0)
                    .put("estimatedResidualAfterPercent", 10.0)
                    .put("confidence", 0.9)
                    .put("uniqueVisits", 2)
                    .put("readiness", "AVAILABLE")))
            .put("mapResidualSuggestions", JSONArray())
            .put("mapCorrectionRegions", JSONArray())

        val suggestion = AdvisorSuggestionAdapter().adapt(advice, calibration(), nowMs = 100)
            .single { it.target == SuggestionTarget.CURVE_K }

        assertEquals(1.10, suggestion.curveChanges.single().after, 0.0001)
        assertTrue(suggestion.rationale.contains("alvo ideal +20,0%"))
        assertTrue(suggestion.rationale.contains("passo seguro +10,0%"))
        assertTrue(suggestion.rationale.contains("resíduo estimado +10,0%"))
    }

    @Test
    fun confirmed_causal_response_allows_090_only_for_same_map_cell() {
        val item = mapItem(3, 0, 7.5)
            .put("residualErrorPercent", 10.0)
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(item))
            .put("mapCorrectionRegions", JSONArray())

        val confirmed = AdvisorSuggestionAdapter().adapt(
            advice = advice,
            calibration = calibration(),
            nowMs = 100,
            causalTransitions = listOf(transition(mapCells = listOf("3:0"))),
        ).single()
        val differentCell = AdvisorSuggestionAdapter().adapt(
            advice = advice,
            calibration = calibration(),
            nowMs = 100,
            causalTransitions = listOf(transition(mapCells = listOf("4:0"))),
        ).single()
        val contradicted = AdvisorSuggestionAdapter().adapt(
            advice = advice,
            calibration = calibration(),
            nowMs = 100,
            causalTransitions = listOf(transition(
                mapCells = listOf("3:0"),
                status = CausalTransitionStatus.CONTRADICTED,
            )),
        ).single()

        // 110 × 1.09 = 119.9 -> 120. Sem confirmação, 110 × 1.075 -> 118.
        assertEquals(120, confirmed.mapChanges.single().after)
        assertEquals(118, differentCell.mapChanges.single().after)
        assertEquals(118, contradicted.mapChanges.single().after)
    }

    @Test
    fun confirmed_causal_response_allows_090_only_for_same_curve_point() {
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray()
                .put(JSONObject()
                    .put("index", 3)
                    .put("actionable", true)
                    .put("idealDeltaPercent", 20.0)
                    .put("suggestedDeltaPercent", 15.0)
                    .put("estimatedResidualAfterPercent", 5.0)
                    .put("confidence", 0.9)
                    .put("readiness", "AVAILABLE")))
            .put("mapResidualSuggestions", JSONArray())
            .put("mapCorrectionRegions", JSONArray())

        val confirmed = AdvisorSuggestionAdapter().adapt(
            advice = advice,
            calibration = calibration(),
            nowMs = 100,
            causalTransitions = listOf(transition(
                target = SuggestionTarget.CURVE_K,
                curveIndexes = listOf(3),
            )),
        ).single { it.target == SuggestionTarget.CURVE_K }
        val awaiting = AdvisorSuggestionAdapter().adapt(
            advice = advice,
            calibration = calibration(),
            nowMs = 100,
            causalTransitions = listOf(transition(
                target = SuggestionTarget.CURVE_K,
                curveIndexes = listOf(3),
                status = CausalTransitionStatus.AWAITING_POST_EVIDENCE,
            )),
        ).single { it.target == SuggestionTarget.CURVE_K }

        assertEquals(1.18, confirmed.curveChanges.single().after, 0.0001)
        assertEquals(1.15, awaiting.curveChanges.single().after, 0.0001)
    }

    @Test
    fun advisor_candidate_reaches_writer_only_after_cell_is_consolidated() {
        val initial = calibration()
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(mapItem(4, 2, 10.0)))
            .put("mapCorrectionRegions", JSONArray())
        val suggestion = AdvisorSuggestionAdapter().adapt(advice, initial, nowMs = 100)
            .single { it.target == SuggestionTarget.MAP_K }
        val runtime = SessionRuntime(SessionState("adapter-e2e", initial))
        consolidate(runtime, row = 4, column = 2)
        runtime.replaceSuggestions(listOf(suggestion), nowMs = 100)
        val prepared = runtime.state.activeSuggestions().single()
        assertEquals(LearningStabilityState.CONSOLIDATED.name, prepared.stabilityState)
        assertEquals(SuggestionLifecycle.PENDING, prepared.lifecycle)
        var writerCalls = 0
        val writer = CalibrationWriter { _, desired, received ->
            writerCalls += 1
            assertEquals(suggestion.id, received.id)
            CalibrationWriteResult(true, desired, "readback confirmado")
        }

        val applied = runtime.applySuggestionToEcu(suggestion.id, 200, writer)

        assertEquals(1, writerCalls)
        assertEquals(121, applied.mapK[4][2])
        assertEquals(CalibrationRevision(2, 6), applied.revision)
        assertEquals(1, runtime.state.checkpoints.size)
        assertEquals(SuggestionLifecycle.APPLIED, runtime.state.suggestions.single().lifecycle)
    }

    private fun consolidate(runtime: SessionRuntime, row: Int, column: Int) {
        val rpm = KMapPhysicalAxes.rpmBins()[column].toDouble()
        val observed = KMapPhysicalAxes.petrolBins()[row]
        val target = observed / 1.10
        runtime.addEvidence(
            SessionEvidence(
                id = "p-ref",
                fuel = CalibrationFuel.PETROL,
                collectedAtMs = 1,
                visitId = "p-ref",
                rpm = rpm,
                mapBar = 0.50,
                petrolMs = target,
                quality = 1.0,
                cngRevision = null,
                waterC = 82.0,
            ),
        )
        repeat(8) { index ->
            runtime.addEvidence(
                SessionEvidence(
                    id = "g-$index",
                    fuel = CalibrationFuel.CNG,
                    collectedAtMs = 10L + index,
                    visitId = "g-$index",
                    rpm = rpm,
                    mapBar = 0.50,
                    petrolMs = observed,
                    quality = 1.0,
                    cngRevision = runtime.state.calibration.revision,
                    waterC = 82.0,
                ),
            )
        }
        assertEquals(LearningStabilityState.CONSOLIDATED, runtime.mapStability(row, column).state)
    }

    private fun transition(
        target: SuggestionTarget = SuggestionTarget.MAP_K,
        mapCells: List<String> = emptyList(),
        curveIndexes: List<Int> = emptyList(),
        status: CausalTransitionStatus = CausalTransitionStatus.CONFIRMED,
    ) = CalibrationTransition(
        suggestionId = "prior-transition",
        target = target,
        appliedAtMs = 50,
        beforeRevision = CalibrationRevision(1, 4),
        afterRevision = CalibrationRevision(2, 5),
        beforeFingerprint = "before",
        afterFingerprint = "after",
        preErrorPercent = 10.0,
        postErrorPercent = 2.0,
        responseGain = 0.8,
        mapCells = mapCells,
        curveIndexes = curveIndexes,
        status = status,
    )

    private fun mapItem(row: Int, column: Int, deltaPercent: Double): JSONObject = JSONObject()
        .put("row", row)
        .put("column", column)
        .put("actionable", true)
        .put("suggestedDeltaPercent", deltaPercent)
        .put("confidence", 0.90)
}