package com.omegas.prohub.calibration

import com.omegas.prohub.physics.CorrectionMechanism
import com.omegas.prohub.physics.EffectDirection
import com.omegas.prohub.physics.MagnitudeAuthority
import com.omegas.v7.runtime.CalibrationRevisionV7
import com.omegas.v7.runtime.CalibrationShapeV7
import com.omegas.v7.runtime.CalibrationStateV7
import com.omegas.v7.runtime.CalibrationWriteResultV7
import com.omegas.v7.runtime.CalibrationWriterV7
import com.omegas.v7.runtime.EvidenceV7
import com.omegas.v7.runtime.FuelV7
import com.omegas.v7.runtime.LearningStabilityStateV7
import com.omegas.v7.runtime.SuggestionLifecycleV7
import com.omegas.v7.runtime.SuggestionTargetV7
import com.omegas.v7.runtime.V7SessionRuntime
import com.omegas.v7.runtime.V7SessionState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorSuggestionAdapterV7Test {
    private fun calibration() = CalibrationStateV7(
        revision = CalibrationRevisionV7(2, 5),
        curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
        mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
            List(CalibrationShapeV7.MAP_K_COLUMNS) { 110 }
        },
    )

    @Test
    fun policy_only_unknown_map_advice_stays_observing_even_when_legacy_item_is_actionable() {
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(
                JSONObject()
                    .put("row", 2)
                    .put("column", 4)
                    .put("actionable", true)
                    .put("suggestedDeltaPercent", 12.0)
                    .put("confidence", 0.9)
                    .put("magnitudeAuthority", MagnitudeAuthority.POLICY_ONLY.name)
                    .put("idealTarget", false)
                    .put("correctionMechanism", CorrectionMechanism.UNKNOWN.name)
                    .put("mechanismCandidateLane", CorrectionMechanism.MAP_LOCAL.name),
            ))

        val suggestion = AdvisorSuggestionAdapterV7().adapt(advice, calibration(), nowMs = 100).single()

        assertEquals(SuggestionLifecycleV7.OBSERVING, suggestion.lifecycle)
        assertTrue(suggestion.mapChanges.isEmpty())
    }

    @Test
    fun candidate_lane_alone_never_authorizes_a_curve_change() {
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray().put(
                JSONObject()
                    .put("index", 3)
                    .put("actionable", true)
                    .put("suggestedDeltaPercent", 8.0)
                    .put("confidence", 0.9)
                    .put("readiness", "AVAILABLE")
                    .put("magnitudeAuthority", MagnitudeAuthority.EMPIRICALLY_BOUNDED.name)
                    .put("idealTarget", true)
                    .put("correctionMechanism", CorrectionMechanism.UNKNOWN.name)
                    .put("mechanismCandidateLane", CorrectionMechanism.CURVE_MUL_ACT.name),
            ))
            .put("mapResidualSuggestions", JSONArray())

        val suggestion = AdvisorSuggestionAdapterV7().adapt(advice, calibration(), nowMs = 100).single()

        assertEquals(SuggestionLifecycleV7.OBSERVING, suggestion.lifecycle)
        assertTrue(suggestion.curveChanges.isEmpty())
    }

    @Test
    fun explicit_ideal_empirical_target_with_matching_map_mechanism_can_be_pending() {
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(mapItem(0, 0, 10.0)))

        val suggestion = AdvisorSuggestionAdapterV7().adapt(advice, calibration(), nowMs = 100).single()

        assertEquals(SuggestionLifecycleV7.PENDING, suggestion.lifecycle)
        assertEquals(121, suggestion.mapChanges.single().after)
    }

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

        val suggestions = AdvisorSuggestionAdapterV7().adapt(advice, calibration(), nowMs = 100)

        val map = suggestions.single { it.target == SuggestionTargetV7.MAP_K }
        val change = map.mapChanges.single()
        assertEquals(0, change.row)
        assertEquals(0, change.column)
        assertEquals(110, change.before)
        assertEquals(121, change.after)
        assertEquals(SuggestionLifecycleV7.PENDING, map.lifecycle)
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

        val suggestions = AdvisorSuggestionAdapterV7().adapt(advice, calibration(), nowMs = 100)
            .filter { it.target == SuggestionTargetV7.MAP_K }

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

        val a = AdvisorSuggestionAdapterV7().adapt(first, calibration(), nowMs = 100).single()
        val b = AdvisorSuggestionAdapterV7().adapt(second, calibration(), nowMs = 200).single()

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

        val suggestion = AdvisorSuggestionAdapterV7().adapt(advice, calibration(), nowMs = 100).single()

        assertEquals(SuggestionLifecycleV7.OBSERVING, suggestion.lifecycle)
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
                    .put("readiness", "AVAILABLE")
                    .put("magnitudeAuthority", MagnitudeAuthority.EMPIRICALLY_BOUNDED.name)
                    .put("idealTarget", true)
                    .put("correctionMechanism", CorrectionMechanism.CURVE_MUL_ACT.name)
                    .put("expectedEffectDirection", EffectDirection.INCREASE.name)
                    .put("expectedEffectFalsifier", "curve response fails to improve")
                    .put("mechanismEvidencePath", JSONArray().put("broad coherent residual"))))
            .put("mapResidualSuggestions", JSONArray())
            .put("mapCorrectionRegions", JSONArray())

        val suggestion = AdvisorSuggestionAdapterV7().adapt(advice, calibration(), nowMs = 100)
            .single { it.target == SuggestionTargetV7.CURVE_K }

        val change = suggestion.curveChanges.single()
        assertEquals(3, change.index)
        assertEquals(1.0, change.before, 1e-12)
        assertEquals(1.08, change.after, 0.0001)
        assertEquals(SuggestionLifecycleV7.PENDING, suggestion.lifecycle)
    }

    @Test
    fun advisor_candidate_reaches_writer_only_after_cell_is_consolidated() {
        val initial = calibration()
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(mapItem(4, 2, 10.0)))
            .put("mapCorrectionRegions", JSONArray())
        val suggestion = AdvisorSuggestionAdapterV7().adapt(advice, initial, nowMs = 100)
            .single { it.target == SuggestionTargetV7.MAP_K }
        val runtime = V7SessionRuntime(V7SessionState("adapter-e2e", initial))
        consolidate(runtime, row = 4, column = 2)
        runtime.replaceSuggestions(listOf(suggestion), nowMs = 100)
        val prepared = runtime.state.activeSuggestions().single()
        assertEquals(LearningStabilityStateV7.CONSOLIDATED.name, prepared.stabilityState)
        assertEquals(SuggestionLifecycleV7.PENDING, prepared.lifecycle)
        var writerCalls = 0
        val writer = CalibrationWriterV7 { _, desired, received ->
            writerCalls += 1
            assertEquals(suggestion.id, received.id)
            CalibrationWriteResultV7(true, desired, "readback confirmado")
        }

        val applied = runtime.applySuggestionToEcu(suggestion.id, 200, writer)

        assertEquals(1, writerCalls)
        assertEquals(121, applied.mapK[4][2])
        assertEquals(CalibrationRevisionV7(2, 6), applied.revision)
        assertEquals(1, runtime.state.checkpoints.size)
        assertEquals(SuggestionLifecycleV7.APPLIED, runtime.state.suggestions.single().lifecycle)
    }

    private fun consolidate(runtime: V7SessionRuntime, row: Int, column: Int) {
        val rpm = KMapPhysicalAxes.rpmBins()[column].toDouble()
        val observed = KMapPhysicalAxes.petrolBins()[row]
        val target = observed / 1.10
        runtime.addEvidence(
            EvidenceV7(
                id = "p-ref",
                fuel = FuelV7.PETROL,
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
                EvidenceV7(
                    id = "g-$index",
                    fuel = FuelV7.CNG,
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
        assertEquals(LearningStabilityStateV7.CONSOLIDATED, runtime.mapStability(row, column).state)
    }

    private fun mapItem(row: Int, column: Int, deltaPercent: Double): JSONObject = JSONObject()
        .put("row", row)
        .put("column", column)
        .put("actionable", true)
        .put("suggestedDeltaPercent", deltaPercent)
        .put("confidence", 0.90)
        .put("magnitudeAuthority", MagnitudeAuthority.EMPIRICALLY_BOUNDED.name)
        .put("idealTarget", true)
        .put("correctionMechanism", CorrectionMechanism.MAP_LOCAL.name)
        .put("expectedEffectDirection", EffectDirection.INCREASE.name)
        .put("expectedEffectFalsifier", "map response fails to improve")
        .put("mechanismEvidencePath", JSONArray().put("localized residual"))
}
