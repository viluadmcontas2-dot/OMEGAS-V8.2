package com.omegas.prohub.calibration

import com.omegas.v7.runtime.CalibrationRevisionV7
import com.omegas.v7.runtime.LocalSuggestionV7
import com.omegas.v7.runtime.MapCellChangeV7
import com.omegas.v7.runtime.SuggestionLifecycleV7
import com.omegas.v7.runtime.SuggestionTargetV7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionUiProjectionTest {
    private val revision = CalibrationRevisionV7(curveK = 2, mapK = 7)

    @Test
    fun `lifecycle interno vira estados humanos sem perder acionabilidade`() {
        val pending = LocalSuggestionV7(
            id = "advisor-map-a",
            createdAtMs = 100,
            updatedAtMs = 200,
            expectedRevision = revision,
            target = SuggestionTargetV7.MAP_K,
            mapChanges = listOf(MapCellChangeV7(1, 2, 120, 126)),
            rationale = "Residual local consolidado",
            lifecycle = SuggestionLifecycleV7.PENDING,
            confidence = 0.9,
        )
        val observing = pending.copy(
            id = "advisor-map-b",
            lifecycle = SuggestionLifecycleV7.OBSERVING,
            mapChanges = emptyList(),
            rationale = "Evidência em revalidação",
            stabilityState = "REVALIDATING",
            confidence = 0.4,
        )
        val result = SuggestionUiProjection.project(listOf(observing, pending), revision)
        val items = result.getJSONArray("items")
        assertEquals("PENDENTE", items.getJSONObject(0).getString("lifecycle"))
        assertTrue(items.getJSONObject(0).getBoolean("actionable"))
        assertEquals("OBSERVANDO", items.getJSONObject(1).getString("lifecycle"))
        assertFalse(items.getJSONObject(1).getBoolean("actionable"))
        assertEquals(1, result.getInt("readyCount"))
        assertFalse(result.getBoolean("automaticWrite"))
        assertTrue(result.getBoolean("humanSelectionRequired"))
    }

    @Test
    fun `revisao antiga vira historico nao acionavel`() {
        val superseded = LocalSuggestionV7(
            id = "advisor-map-old",
            createdAtMs = 10,
            updatedAtMs = 20,
            expectedRevision = CalibrationRevisionV7(curveK = 1, mapK = 6),
            target = SuggestionTargetV7.MAP_K,
            mapChanges = emptyList(),
            rationale = "Base anterior",
            lifecycle = SuggestionLifecycleV7.SUPERSEDED,
            confidence = 0.8,
        )
        val item = SuggestionUiProjection.project(listOf(superseded), revision)
            .getJSONArray("items").getJSONObject(0)
        assertEquals("SUPERADA", item.getString("lifecycle"))
        assertFalse(item.getBoolean("actionable"))
        assertTrue(item.getString("whatIsMissing").contains("revisão/base mudou"))
    }
}
