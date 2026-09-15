package com.omegas.v7.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CalibrationCausalTransitionV7Test {
    private fun calibration(revision: CalibrationRevisionV7 = CalibrationRevisionV7(0, 0)) =
        CalibrationStateV7(
            revision = revision,
            curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
            mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
                List(CalibrationShapeV7.MAP_K_COLUMNS) { 100 }
            },
        )

    private fun runtime() = V7SessionRuntime(
        V7SessionState(sessionId = "causal-session", calibration = calibration()),
    )

    @Test
    fun successfulReadbackRecordsMaterialTransitionAndRoundTrips() {
        val runtime = runtime()
        val before = runtime.state.calibration
        val suggestion = mapSuggestion(before, "map-causal-1", 10.0)
        runtime.registerSuggestion(suggestion)

        val applied = apply(runtime, suggestion)

        val transition = runtime.state.calibrationTransitions.single()
        assertEquals(suggestion.id, transition.suggestionId)
        assertEquals(SuggestionTargetV7.MAP_K, transition.target)
        assertEquals(before.revision, transition.beforeRevision)
        assertEquals(applied.revision, transition.afterRevision)
        assertEquals(before.materialFingerprint(), transition.beforeFingerprint)
        assertEquals(applied.materialFingerprint(), transition.afterFingerprint)
        assertNotEquals(transition.beforeFingerprint, transition.afterFingerprint)
        assertEquals(10.0, transition.preErrorPercent ?: Double.NaN, 0.0)
        assertEquals(CausalTransitionStatusV7.AWAITING_POST_EVIDENCE, transition.status)

        val restored = V7SessionSnapshotCodec.decode(V7SessionSnapshotCodec.encode(runtime.state))
        assertEquals(runtime.state.calibrationTransitions, restored.calibrationTransitions)
    }

    @Test
    fun sameRevisionWithWrongMaterialReadbackIsRejected() {
        val runtime = runtime()
        val before = runtime.state.calibration
        val suggestion = mapSuggestion(before, "map-causal-wrong-readback", 8.0)
        runtime.registerSuggestion(suggestion)

        try {
            runtime.applySuggestionToEcu(
                suggestionId = suggestion.id,
                nowMs = 20,
                writer = CalibrationWriterV7 { current, desired, _ ->
                    val wrongMap = current.mapK.map { it.toMutableList() }.toMutableList()
                    wrongMap[3][0] = 109
                    CalibrationWriteResultV7(
                        success = true,
                        readBack = desired.copy(mapK = wrongMap.map { it.toList() }),
                        message = "revision correta, material errado",
                    )
                },
            )
            fail("Readback materialmente divergente deveria ser rejeitado")
        } catch (_: IllegalArgumentException) {
            // esperado
        }

        assertEquals(before, runtime.state.calibration)
        assertTrue(runtime.state.calibrationTransitions.isEmpty())
        assertEquals(SuggestionLifecycleV7.PENDING, runtime.state.suggestions.single().lifecycle)
    }

    @Test
    fun repeatedPostReadbackImprovementConfirmsCausalResponseAndGain() {
        val runtime = runtime()
        addPetrolReference(runtime)
        val suggestion = mapSuggestion(runtime.state.calibration, "map-confirmed", 10.0)
        runtime.registerSuggestion(suggestion)
        val applied = apply(runtime, suggestion)

        assertEquals(
            CausalTransitionStatusV7.AWAITING_POST_EVIDENCE,
            runtime.state.calibrationTransitions.single().status,
        )

        repeat(8) { index ->
            addCng(runtime, applied.revision, "post-good-$index", 30L + index, petrolMs = 3.57)
        }

        val transition = runtime.state.calibrationTransitions.single()
        assertEquals(CausalTransitionStatusV7.CONFIRMED, transition.status)
        assertEquals(2.0, transition.postErrorPercent ?: Double.NaN, 1e-6)
        assertEquals(0.8, transition.responseGain ?: Double.NaN, 1e-6)
    }

    @Test
    fun repeatedPostReadbackWorseningContradictsCausalResponse() {
        val runtime = runtime()
        addPetrolReference(runtime)
        val suggestion = mapSuggestion(runtime.state.calibration, "map-contradicted", 10.0)
        runtime.registerSuggestion(suggestion)
        val applied = apply(runtime, suggestion)

        repeat(12) { index ->
            addCng(runtime, applied.revision, "post-bad-$index", 30L + index, petrolMs = 3.92)
        }

        val transition = runtime.state.calibrationTransitions.single()
        assertEquals(CausalTransitionStatusV7.CONTRADICTED, transition.status)
        assertTrue((transition.postErrorPercent ?: 0.0) > 10.0)
        assertTrue((transition.responseGain ?: 0.0) < 0.0)
    }

    private fun mapSuggestion(
        calibration: CalibrationStateV7,
        id: String,
        preErrorPercent: Double,
    ) = LocalSuggestionV7(
        id = id,
        createdAtMs = 10,
        expectedRevision = calibration.revision,
        target = SuggestionTargetV7.MAP_K,
        mapChanges = listOf(MapCellChangeV7(3, 0, 100, 110)),
        rationale = "Passo científico manual",
        consolidatedErrorPercent = preErrorPercent,
    )

    private fun apply(runtime: V7SessionRuntime, suggestion: LocalSuggestionV7): CalibrationStateV7 =
        runtime.applySuggestionToEcu(
            suggestionId = suggestion.id,
            nowMs = 20,
            writer = CalibrationWriterV7 { _, desired, _ ->
                CalibrationWriteResultV7(true, desired, "ACK + readback")
            },
        )

    private fun addPetrolReference(runtime: V7SessionRuntime) {
        runtime.addEvidence(
            EvidenceV7(
                id = "petrol-ref",
                fuel = FuelV7.PETROL,
                collectedAtMs = 1,
                visitId = "petrol-ref",
                rpm = 850.0,
                mapBar = 0.40,
                petrolMs = 3.50,
                quality = 1.0,
                cngRevision = null,
                waterC = 82.0,
            ),
        )
    }

    private fun addCng(
        runtime: V7SessionRuntime,
        revision: CalibrationRevisionV7,
        visitId: String,
        collectedAtMs: Long,
        petrolMs: Double,
    ) {
        runtime.addEvidence(
            EvidenceV7(
                id = visitId,
                fuel = FuelV7.CNG,
                collectedAtMs = collectedAtMs,
                visitId = visitId,
                rpm = 850.0,
                mapBar = 0.40,
                petrolMs = petrolMs,
                quality = 1.0,
                cngRevision = revision,
                waterC = 82.0,
            ),
        )
    }
}
