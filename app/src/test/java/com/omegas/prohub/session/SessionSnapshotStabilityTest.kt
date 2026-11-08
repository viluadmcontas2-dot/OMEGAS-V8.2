package com.omegas.prohub.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSnapshotStabilityTest {
    @Test
    fun `schema 6 preserva metadados de estabilidade da sugestao`() {
        val state = initialState().copy(
            suggestions = listOf(
                LocalSuggestion(
                    id = "stable-map",
                    createdAtMs = 1000L,
                    expectedRevision = CalibrationRevision(0, 0),
                    target = SuggestionTarget.MAP_K,
                    mapChanges = listOf(MapCellChange(4, 2, 100, 108)),
                    rationale = "Consolidado",
                    updatedAtMs = 2000L,
                    lifecycle = SuggestionLifecycle.OBSERVING,
                    confidence = 0.91,
                    stabilityGeneration = 3,
                    stabilityState = LearningStabilityState.REVALIDATING.name,
                    consolidatedErrorPercent = 8.4,
                    recentErrorPercent = 2.1,
                ),
            ),
        )

        val encoded = SessionSnapshotCodec.encode(state)
        assertTrue(encoded.startsWith("schema=OMEGAS_V7_SESSION_6"))
        val decoded = SessionSnapshotCodec.decode(encoded)
        val suggestion = decoded.suggestions.single()
        assertEquals(3, suggestion.stabilityGeneration)
        assertEquals("REVALIDATING", suggestion.stabilityState)
        assertEquals(8.4, suggestion.consolidatedErrorPercent ?: Double.NaN, 0.0)
        assertEquals(2.1, suggestion.recentErrorPercent ?: Double.NaN, 0.0)
        assertEquals(SuggestionLifecycle.OBSERVING, suggestion.lifecycle)
    }

    @Test
    fun `schema 5 antigo continua legivel com defaults de estabilidade`() {
        val old = SessionSnapshotCodec.encode(initialState())
            .replaceFirst("OMEGAS_V7_SESSION_6", "OMEGAS_V7_SESSION_5")
        val decoded = SessionSnapshotCodec.decode(old)
        assertTrue(decoded.suggestions.isEmpty())

        val legacySuggestion = initialState().copy(
            suggestions = listOf(
                LocalSuggestion(
                    id = "legacy",
                    createdAtMs = 10L,
                    expectedRevision = CalibrationRevision(0, 0),
                    target = SuggestionTarget.MAP_K,
                    mapChanges = listOf(MapCellChange(4, 2, 100, 101)),
                    rationale = "legacy",
                ),
            ),
        )
        val schema6 = SessionSnapshotCodec.encode(legacySuggestion)
        val line = schema6.lineSequence().first { it.startsWith("suggestion.0=") }
        val fields = line.substringAfter('=').split('|').take(11).joinToString("|")
        val schema5 = schema6.lineSequence()
            .filterNot { it.startsWith("suggestion.0=") }
            .joinToString("\n")
            .replaceFirst("OMEGAS_V7_SESSION_6", "OMEGAS_V7_SESSION_5") + "\nsuggestion.0=$fields\n"
        val restored = SessionSnapshotCodec.decode(schema5).suggestions.single()
        assertEquals(-1, restored.stabilityGeneration)
        assertEquals("UNASSESSED", restored.stabilityState)
        assertNull(restored.consolidatedErrorPercent)
        assertNull(restored.recentErrorPercent)
    }

    private fun initialState(): SessionState = SessionState(
        sessionId = "snapshot-stability",
        calibration = CalibrationState(
            revision = CalibrationRevision(0, 0),
            curveK = List(CalibrationShape.CURVE_K_POINTS) { 1.0 },
            mapK = List(CalibrationShape.MAP_K_STORAGE_ROWS) {
                List(CalibrationShape.MAP_K_COLUMNS) { 100 }
            },
        ),
    )
}
