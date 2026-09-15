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
        val suggestion = LocalSuggestionV7(
            id = "map-causal-1",
            createdAtMs = 10,
            expectedRevision = before.revision,
            target = SuggestionTargetV7.MAP_K,
            mapChanges = listOf(MapCellChangeV7(3, 0, 100, 110)),
            rationale = "Primeiro passo científico manual",
            consolidatedErrorPercent = 10.0,
        )
        runtime.registerSuggestion(suggestion)

        val applied = runtime.applySuggestionToEcu(
            suggestionId = suggestion.id,
            nowMs = 20,
            writer = CalibrationWriterV7 { _, desired, _ ->
                CalibrationWriteResultV7(true, desired, "ACK + readback")
            },
        )

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
        val suggestion = LocalSuggestionV7(
            id = "map-causal-wrong-readback",
            createdAtMs = 10,
            expectedRevision = before.revision,
            target = SuggestionTargetV7.MAP_K,
            mapChanges = listOf(MapCellChangeV7(3, 0, 100, 110)),
            rationale = "Teste material",
            consolidatedErrorPercent = 8.0,
        )
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
}
