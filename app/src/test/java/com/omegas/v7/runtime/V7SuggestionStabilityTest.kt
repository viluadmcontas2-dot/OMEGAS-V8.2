package com.omegas.v7.runtime

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V7SuggestionStabilityTest {
    @Test
    fun `sugestao fica preservada e bloqueada durante revalidacao e so muda apos nova geracao`() {
        val runtime = runtime()
        val row = 4
        val column = 2
        val rpm = KMapPhysicalAxes.rpmBins()[column].toDouble()
        val observed = KMapPhysicalAxes.petrolBins()[row]
        val initialTargetMs = observed / 1.10
        runtime.addEvidence(petrol("petrol-initial", initialTargetMs, rpm, 1L))
        repeat(8) { index -> runtime.addEvidence(cng("initial-$index", observed, rpm, 10L + index)) }
        assertEquals(LearningStabilityStateV7.CONSOLIDATED, runtime.mapStability(row, column).state)

        val initialSuggestion = suggestion(runtime, row, column, after = 120)
        runtime.replaceSuggestions(listOf(initialSuggestion), nowMs = 100L)
        val pending = runtime.state.activeSuggestions().single()
        assertEquals(SuggestionLifecycleV7.PENDING, pending.lifecycle)
        assertEquals(120, pending.mapChanges.single().after)
        val initialGeneration = pending.stabilityGeneration

        // Uma tendência contraditória isolada não pode mudar o alvo já consolidado.
        runtime.addEvidence(petrol("petrol-new", observed / 1.02, rpm, 150L))
        runtime.addEvidence(cng("recent-one", observed, rpm, 160L))
        assertEquals(LearningStabilityStateV7.REVALIDATING, runtime.mapStability(row, column).state)
        runtime.replaceSuggestions(listOf(suggestion(runtime, row, column, after = 105)), nowMs = 170L)

        val revalidating = runtime.state.suggestions.single()
        assertEquals(SuggestionLifecycleV7.OBSERVING, revalidating.lifecycle)
        assertEquals(LearningStabilityStateV7.REVALIDATING.name, revalidating.stabilityState)
        assertEquals(120, revalidating.mapChanges.single().after)
        assertFalse(revalidating.actionableAt(runtime.state.calibration.revision))

        // A mesma mudança, quando repetível, promove nova geração e libera um alvo novo.
        repeat(7) { index -> runtime.addEvidence(cng("recent-${index + 2}", observed, rpm, 180L + index)) }
        val promoted = runtime.mapStability(row, column)
        assertEquals(LearningStabilityStateV7.CONSOLIDATED, promoted.state)
        assertTrue(promoted.generation > initialGeneration)

        runtime.replaceSuggestions(listOf(suggestion(runtime, row, column, after = 105)), nowMs = 220L)
        val refreshed = runtime.state.activeSuggestions().single()
        assertEquals(SuggestionLifecycleV7.PENDING, refreshed.lifecycle)
        assertEquals(LearningStabilityStateV7.CONSOLIDATED.name, refreshed.stabilityState)
        assertEquals(105, refreshed.mapChanges.single().after)
        assertTrue(refreshed.stabilityGeneration > initialGeneration)
    }

    private fun runtime(): V7SessionRuntime = V7SessionRuntime(
        V7SessionState(
            sessionId = "suggestion-stability",
            calibration = CalibrationStateV7(
                revision = CalibrationRevisionV7(0, 0),
                curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
                mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
                    List(CalibrationShapeV7.MAP_K_COLUMNS) { 100 }
                },
            ),
        ),
    )

    private fun petrol(id: String, petrolMs: Double, rpm: Double, at: Long): EvidenceV7 = EvidenceV7(
        id = id,
        fuel = FuelV7.PETROL,
        collectedAtMs = at,
        visitId = id,
        rpm = rpm,
        mapBar = 0.50,
        petrolMs = petrolMs,
        quality = 1.0,
        cngRevision = null,
        waterC = 82.0,
    )

    private fun cng(id: String, petrolMs: Double, rpm: Double, at: Long): EvidenceV7 = EvidenceV7(
        id = id,
        fuel = FuelV7.CNG,
        collectedAtMs = at,
        visitId = id,
        rpm = rpm,
        mapBar = 0.50,
        petrolMs = petrolMs,
        quality = 1.0,
        cngRevision = CalibrationRevisionV7(0, 0),
        waterC = 82.0,
    )

    private fun suggestion(runtime: V7SessionRuntime, row: Int, column: Int, after: Int): LocalSuggestionV7 = LocalSuggestionV7(
        id = "stable-cell",
        createdAtMs = 90L,
        expectedRevision = runtime.state.calibration.revision,
        target = SuggestionTargetV7.MAP_K,
        mapChanges = listOf(MapCellChangeV7(row, column, runtime.state.calibration.mapK[row][column], after)),
        rationale = "Correção local",
    )
}
