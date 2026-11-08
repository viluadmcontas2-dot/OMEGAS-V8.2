package com.omegas.prohub.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CalibrationCausalTransitionTest {
    private fun calibration(revision: CalibrationRevision = CalibrationRevision(0, 0)) =
        CalibrationState(
            revision = revision,
            curveK = List(CalibrationShape.CURVE_K_POINTS) { 1.0 },
            mapK = List(CalibrationShape.MAP_K_STORAGE_ROWS) {
                List(CalibrationShape.MAP_K_COLUMNS) { 100 }
            },
        )

    private fun runtime() = SessionRuntime(
        SessionState(sessionId = "causal-session", calibration = calibration()),
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
        assertEquals(SuggestionTarget.MAP_K, transition.target)
        assertEquals(before.revision, transition.beforeRevision)
        assertEquals(applied.revision, transition.afterRevision)
        assertEquals(before.materialFingerprint(), transition.beforeFingerprint)
        assertEquals(applied.materialFingerprint(), transition.afterFingerprint)
        assertNotEquals(transition.beforeFingerprint, transition.afterFingerprint)
        assertEquals(10.0, transition.preErrorPercent ?: Double.NaN, 0.0)
        assertEquals(CausalTransitionStatus.AWAITING_POST_EVIDENCE, transition.status)

        val restored = SessionSnapshotCodec.decode(SessionSnapshotCodec.encode(runtime.state))
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
                writer = CalibrationWriter { current, desired, _ ->
                    val wrongMap = current.mapK.map { it.toMutableList() }.toMutableList()
                    wrongMap[3][0] = 109
                    CalibrationWriteResult(
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
        assertEquals(SuggestionLifecycle.PENDING, runtime.state.suggestions.single().lifecycle)
    }

    @Test
    fun repeatedPostReadbackImprovementConfirmsCausalResponseAndGain() {
        val runtime = runtime()
        addPetrolReference(runtime)
        val suggestion = mapSuggestion(runtime.state.calibration, "map-confirmed", 10.0)
        runtime.registerSuggestion(suggestion)
        val applied = apply(runtime, suggestion)

        assertEquals(
            CausalTransitionStatus.AWAITING_POST_EVIDENCE,
            runtime.state.calibrationTransitions.single().status,
        )

        repeat(8) { index ->
            addCng(runtime, applied.revision, "post-good-$index", 30L + index, petrolMs = 3.57)
        }

        val transition = runtime.state.calibrationTransitions.single()
        assertEquals(CausalTransitionStatus.CONFIRMED, transition.status)
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
        assertEquals(CausalTransitionStatus.CONTRADICTED, transition.status)
        assertTrue((transition.postErrorPercent ?: 0.0) > 10.0)
        assertTrue((transition.responseGain ?: 0.0) < 0.0)
    }

    private fun mapSuggestion(
        calibration: CalibrationState,
        id: String,
        preErrorPercent: Double,
    ) = LocalSuggestion(
        id = id,
        createdAtMs = 10,
        expectedRevision = calibration.revision,
        target = SuggestionTarget.MAP_K,
        mapChanges = listOf(MapCellChange(3, 0, 100, 110)),
        rationale = "Passo científico manual",
        consolidatedErrorPercent = preErrorPercent,
    )

    private fun apply(runtime: SessionRuntime, suggestion: LocalSuggestion): CalibrationState =
        runtime.applySuggestionToEcu(
            suggestionId = suggestion.id,
            nowMs = 20,
            writer = CalibrationWriter { _, desired, _ ->
                CalibrationWriteResult(true, desired, "ACK + readback")
            },
        )

    private fun addPetrolReference(runtime: SessionRuntime) {
        runtime.addEvidence(
            SessionEvidence(
                id = "petrol-ref",
                fuel = CalibrationFuel.PETROL,
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
        runtime: SessionRuntime,
        revision: CalibrationRevision,
        visitId: String,
        collectedAtMs: Long,
        petrolMs: Double,
    ) {
        runtime.addEvidence(
            SessionEvidence(
                id = visitId,
                fuel = CalibrationFuel.CNG,
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
