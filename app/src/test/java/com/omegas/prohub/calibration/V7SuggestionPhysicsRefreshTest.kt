package com.omegas.prohub.calibration

import com.omegas.prohub.physics.CorrectionMechanism
import com.omegas.prohub.physics.EffectDirection
import com.omegas.prohub.physics.MagnitudeAuthority
import com.omegas.v7.runtime.CalibrationRevisionV7
import com.omegas.v7.runtime.CalibrationShapeV7
import com.omegas.v7.runtime.CalibrationStateV7
import com.omegas.v7.runtime.EvidenceV7
import com.omegas.v7.runtime.FuelV7
import com.omegas.v7.runtime.LearningStabilityStateV7
import com.omegas.v7.runtime.LocalSuggestionV7
import com.omegas.v7.runtime.MapCellChangeV7
import com.omegas.v7.runtime.PhysicsSuggestionMetadataV7
import com.omegas.v7.runtime.SuggestionLifecycleV7
import com.omegas.v7.runtime.SuggestionTargetV7
import com.omegas.v7.runtime.V7SessionRuntime
import com.omegas.v7.runtime.V7SessionState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V7SuggestionPhysicsRefreshTest {
    @Test
    fun stable_target_keeps_magnitude_but_refreshes_physics_authority() {
        val calibration = CalibrationStateV7(
            revision = CalibrationRevisionV7(2, 5),
            curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
            mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
                List(CalibrationShapeV7.MAP_K_COLUMNS) { 110 }
            },
        )
        val runtime = V7SessionRuntime(V7SessionState("physics-refresh", calibration))
        consolidate(runtime, row = 4, column = 2)
        val stability = runtime.mapStability(4, 2)
        assertEquals(LearningStabilityStateV7.CONSOLIDATED, stability.state)

        val fresh = AdvisorSuggestionAdapterV7().adapt(
            JSONObject()
                .put("kFactorSuggestions", JSONArray())
                .put("mapResidualSuggestions", JSONArray().put(
                    JSONObject()
                        .put("row", 4)
                        .put("column", 2)
                        .put("actionable", true)
                        .put("suggestedDeltaPercent", 10.0)
                        .put("confidence", 0.90)
                        .put("magnitudeAuthority", MagnitudeAuthority.EMPIRICALLY_BOUNDED.name)
                        .put("stepAuthority", MagnitudeAuthority.POLICY_ONLY.name)
                        .put("idealTarget", true)
                        .put("correctionMechanism", CorrectionMechanism.MAP_LOCAL.name)
                        .put("expectedEffectDirection", EffectDirection.INCREASE.name)
                        .put("expectedEffectAuthority", MagnitudeAuthority.EMPIRICALLY_BOUNDED.name)
                        .put("expectedEffectFalsifier", "map response fails to improve")
                        .put("mechanismEvidencePath", JSONArray().put("localized residual")),
                )),
            calibration,
            nowMs = 100,
        ).single()

        val stale = LocalSuggestionV7(
            id = fresh.id,
            createdAtMs = 50,
            expectedRevision = calibration.revision,
            target = SuggestionTargetV7.MAP_K,
            mapChanges = listOf(MapCellChangeV7(4, 2, 110, 120)),
            rationale = "legacy target without typed authority",
            updatedAtMs = 50,
            lifecycle = SuggestionLifecycleV7.PENDING,
            confidence = stability.confidence,
            stabilityGeneration = stability.generation,
            stabilityState = stability.state.name,
            physics = PhysicsSuggestionMetadataV7(),
        )
        runtime.registerSuggestion(stale)
        runtime.replaceSuggestions(listOf(fresh), nowMs = 200)

        val actual = runtime.state.activeSuggestions().single()
        assertEquals(120, actual.mapChanges.single().after)
        assertEquals(fresh.physics, actual.physics)
        assertTrue(actual.actionableAt(calibration.revision))
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
                ),
            )
        }
    }
}
