package com.omegas.prohub.calibration

import com.omegas.prohub.physics.CorrectionMechanism
import com.omegas.prohub.physics.EffectDirection
import com.omegas.prohub.physics.MagnitudeAuthority
import com.omegas.v7.runtime.CalibrationRevisionV7
import com.omegas.v7.runtime.CalibrationShapeV7
import com.omegas.v7.runtime.CalibrationStateV7
import com.omegas.v7.runtime.LocalSuggestionV7
import com.omegas.v7.runtime.MapCellChangeV7
import com.omegas.v7.runtime.SuggestionTargetV7
import com.omegas.v7.runtime.V7SessionSnapshotCodec
import com.omegas.v7.runtime.V7SessionState
import com.omegas.v7.runtime.V7UiProjection
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorPhysicsAuthorityPersistenceV7Test {
    private fun calibration() = CalibrationStateV7(
        revision = CalibrationRevisionV7(3, 7),
        curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
        mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
            List(CalibrationShapeV7.MAP_K_COLUMNS) { 110 }
        },
    )

    @Test
    fun physics_authority_survives_advisor_suggestion_snapshot_and_ui_projection() {
        val advice = JSONObject()
            .put("kFactorSuggestions", JSONArray())
            .put("mapResidualSuggestions", JSONArray().put(
                JSONObject()
                    .put("row", 2)
                    .put("column", 4)
                    .put("actionable", true)
                    .put("suggestedDeltaPercent", 10.0)
                    .put("confidence", 0.91)
                    .put("magnitudeAuthority", MagnitudeAuthority.EMPIRICALLY_BOUNDED.name)
                    .put("stepAuthority", MagnitudeAuthority.POLICY_ONLY.name)
                    .put("idealTarget", true)
                    .put("correctionMechanism", CorrectionMechanism.MAP_LOCAL.name)
                    .put("expectedEffectDirection", EffectDirection.INCREASE.name)
                    .put("expectedEffectLowerBound", 1.08)
                    .put("expectedEffectUpperBound", 1.12)
                    .put("expectedEffectAssumptions", JSONArray().put("gain empirically bounded"))
                    .put("expectedEffectFalsifier", "residual fails to improve")
                    .put("mechanismEvidencePath", JSONArray().put("localized residual").put("gain posterior")),
            ))

        val suggestion = AdvisorSuggestionAdapterV7().adapt(advice, calibration(), nowMs = 100).single()

        assertEquals(MagnitudeAuthority.EMPIRICALLY_BOUNDED, suggestion.physics.magnitudeAuthority)
        assertEquals(MagnitudeAuthority.POLICY_ONLY, suggestion.physics.stepAuthority)
        assertEquals(CorrectionMechanism.MAP_LOCAL, suggestion.physics.correctionMechanism)
        assertEquals(EffectDirection.INCREASE, suggestion.physics.effectDirection)
        assertEquals(1.08, suggestion.physics.lowerBound!!, 0.0)
        assertEquals(1.12, suggestion.physics.upperBound!!, 0.0)
        assertEquals(listOf("gain empirically bounded"), suggestion.physics.assumptions)
        assertEquals("residual fails to improve", suggestion.physics.falsifier)
        assertEquals(listOf("localized residual", "gain posterior"), suggestion.physics.evidencePath)
        assertTrue(suggestion.physics.idealTarget)
        assertTrue(suggestion.actionableAt(calibration().revision))

        val restored = V7SessionSnapshotCodec.decode(
            V7SessionSnapshotCodec.encode(V7SessionState("physics-persist", calibration(), suggestions = listOf(suggestion))),
        )
        val persisted = restored.suggestions.single()
        assertEquals(suggestion.physics, persisted.physics)

        val ui = V7UiProjection.from(restored).adjustment.authorities.single()
        assertEquals(suggestion.id, ui.suggestionId)
        assertEquals(MagnitudeAuthority.EMPIRICALLY_BOUNDED, ui.magnitudeAuthority)
        assertEquals(MagnitudeAuthority.POLICY_ONLY, ui.stepAuthority)
        assertEquals(CorrectionMechanism.MAP_LOCAL, ui.correctionMechanism)
        assertEquals(EffectDirection.INCREASE, ui.effectDirection)
        assertTrue(ui.idealTarget)
    }

    @Test
    fun legacy_advisor_pending_without_physics_metadata_fails_closed() {
        val calibration = calibration()
        val legacy = LocalSuggestionV7(
            id = "advisor-map-legacy",
            createdAtMs = 10,
            expectedRevision = calibration.revision,
            target = SuggestionTargetV7.MAP_K,
            mapChanges = listOf(MapCellChangeV7(2, 4, 110, 121)),
            rationale = "legacy persisted advisor suggestion",
        )

        assertFalse(legacy.actionableAt(calibration.revision))
    }
}
