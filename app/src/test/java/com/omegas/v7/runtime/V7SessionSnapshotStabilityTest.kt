package com.omegas.v7.runtime

import com.omegas.prohub.physics.CorrectionMechanism
import com.omegas.prohub.physics.MagnitudeAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V7SessionSnapshotStabilityTest {
    @Test
    fun `schema 7 preserva metadados de estabilidade e autoridade da sugestao`() {
        val state = initialState().copy(
            suggestions = listOf(
                LocalSuggestionV7(
                    id = "stable-map",
                    createdAtMs = 1000L,
                    expectedRevision = CalibrationRevisionV7(0, 0),
                    target = SuggestionTargetV7.MAP_K,
                    mapChanges = listOf(MapCellChangeV7(4, 2, 100, 108)),
                    rationale = "Consolidado",
                    updatedAtMs = 2000L,
                    lifecycle = SuggestionLifecycleV7.OBSERVING,
                    confidence = 0.91,
                    stabilityGeneration = 3,
                    stabilityState = LearningStabilityStateV7.REVALIDATING.name,
                    consolidatedErrorPercent = 8.4,
                    recentErrorPercent = 2.1,
                    physics = PhysicsSuggestionMetadataV7(
                        magnitudeAuthority = MagnitudeAuthority.EMPIRICALLY_BOUNDED,
                        correctionMechanism = CorrectionMechanism.MAP_LOCAL,
                        evidencePath = listOf("localized residual"),
                    ),
                ),
            ),
        )

        val encoded = V7SessionSnapshotCodec.encode(state)
        assertTrue(encoded.startsWith("schema=OMEGAS_V7_SESSION_7"))
        val decoded = V7SessionSnapshotCodec.decode(encoded)
        val suggestion = decoded.suggestions.single()
        assertEquals(3, suggestion.stabilityGeneration)
        assertEquals("REVALIDATING", suggestion.stabilityState)
        assertEquals(8.4, suggestion.consolidatedErrorPercent ?: Double.NaN, 0.0)
        assertEquals(2.1, suggestion.recentErrorPercent ?: Double.NaN, 0.0)
        assertEquals(SuggestionLifecycleV7.OBSERVING, suggestion.lifecycle)
        assertEquals(MagnitudeAuthority.EMPIRICALLY_BOUNDED, suggestion.physics.magnitudeAuthority)
        assertEquals(CorrectionMechanism.MAP_LOCAL, suggestion.physics.correctionMechanism)
        assertEquals(listOf("localized residual"), suggestion.physics.evidencePath)
    }

    @Test
    fun `schemas antigos continuam legiveis com autoridade fail closed`() {
        val old = V7SessionSnapshotCodec.encode(initialState())
            .replaceFirst("OMEGAS_V7_SESSION_7", "OMEGAS_V7_SESSION_6")
        val decoded = V7SessionSnapshotCodec.decode(old)
        assertTrue(decoded.suggestions.isEmpty())

        val legacySuggestion = initialState().copy(
            suggestions = listOf(
                LocalSuggestionV7(
                    id = "advisor-map-legacy",
                    createdAtMs = 10L,
                    expectedRevision = CalibrationRevisionV7(0, 0),
                    target = SuggestionTargetV7.MAP_K,
                    mapChanges = listOf(MapCellChangeV7(4, 2, 100, 101)),
                    rationale = "legacy",
                ),
            ),
        )
        val schema7 = V7SessionSnapshotCodec.encode(legacySuggestion)
        val line = schema7.lineSequence().first { it.startsWith("suggestion.0=") }
        val fields = line.substringAfter('=').split('|').take(11).joinToString("|")
        val schema5 = schema7.lineSequence()
            .filterNot { it.startsWith("suggestion.0=") }
            .joinToString("\n")
            .replaceFirst("OMEGAS_V7_SESSION_7", "OMEGAS_V7_SESSION_5") + "\nsuggestion.0=$fields\n"
        val restored = V7SessionSnapshotCodec.decode(schema5).suggestions.single()
        assertEquals(-1, restored.stabilityGeneration)
        assertEquals("UNASSESSED", restored.stabilityState)
        assertNull(restored.consolidatedErrorPercent)
        assertNull(restored.recentErrorPercent)
        assertEquals(MagnitudeAuthority.UNKNOWN, restored.physics.magnitudeAuthority)
        assertEquals(CorrectionMechanism.UNKNOWN, restored.physics.correctionMechanism)
        assertTrue(!restored.actionableAt(CalibrationRevisionV7(0, 0)))
    }

    private fun initialState(): V7SessionState = V7SessionState(
        sessionId = "snapshot-stability",
        calibration = CalibrationStateV7(
            revision = CalibrationRevisionV7(0, 0),
            curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
            mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
                List(CalibrationShapeV7.MAP_K_COLUMNS) { 100 }
            },
        ),
    )
}
