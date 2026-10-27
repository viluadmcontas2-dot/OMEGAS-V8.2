package com.omegas.prohub.session

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionRuntimeTest {
    private fun calibration(revision: CalibrationRevision = CalibrationRevision(0, 0)) =
        CalibrationState(
            revision = revision,
            curveK = List(CalibrationShape.CURVE_K_POINTS) { 1.0 },
            mapK = List(CalibrationShape.MAP_K_STORAGE_ROWS) {
                List(CalibrationShape.MAP_K_COLUMNS) { 100 }
            },
        )

    private fun runtime() = SessionRuntime(
        SessionState(sessionId = "session-1", calibration = calibration()),
    )

    private val acceptingWriter = CalibrationWriter { _, desired, _ ->
        CalibrationWriteResult(success = true, readBack = desired, message = "ECU escrita")
    }

    @Test
    fun cng_can_arrive_before_petrol_and_remains_visible() {
        val runtime = runtime()
        runtime.addEvidence(
            SessionEvidence("g1", CalibrationFuel.CNG, 10, "visit-g", 1500.0, 0.5, 4.0, 0.9, runtime.state.calibration.revision),
        )

        assertEquals(1, runtime.state.activeCngEvidence().size)
        assertEquals(0, runtime.state.petrolEvidence.size)
        assertEquals(LearningReadiness.CNG_ONLY, UiProjection.from(runtime.state).now.readiness)
        assertTrue(UiProjection.from(runtime.state).learning.explanation.contains("permanece salvo"))
    }

    @Test
    fun one_physical_visit_is_one_immutable_evidence_unit() {
        val runtime = runtime()
        runtime.addEvidence(SessionEvidence("p1", CalibrationFuel.PETROL, 10, "visit-1", 1200.0, 0.4, 3.0, 0.5, null))
        runtime.addEvidence(SessionEvidence("p2", CalibrationFuel.PETROL, 20, "visit-1", 1210.0, 0.4, 3.1, 0.9, null))

        assertEquals(1, runtime.state.petrolEvidence.size)
        assertEquals("p1", runtime.state.petrolEvidence.single().id)
        assertEquals(3.0, runtime.state.petrolEvidence.single().petrolMs, 0.0)
    }

    @Test
    fun suggestion_is_written_only_through_ecu_writer_and_becomes_applied_history() {
        val runtime = runtime()
        val suggestion = mapSuggestion(runtime, "s1", 3, 0, 145)
        runtime.registerSuggestion(suggestion)

        val applied = runtime.applySuggestionToEcu("s1", 40, acceptingWriter)

        assertEquals(145, applied.mapK[3][0])
        assertEquals(CalibrationRevision(0, 1), applied.revision)
        assertEquals(1, runtime.state.checkpoints.size)
        assertEquals("ECU escrita", runtime.state.lastWriteMessage)
        assertEquals(SuggestionLifecycle.APPLIED, runtime.state.suggestions.single().lifecycle)
        assertTrue(runtime.state.activeSuggestions().isEmpty())
    }

    @Test
    fun pending_suggestion_keeps_magnitude_while_same_consolidated_generation_is_valid() {
        val runtime = runtime()
        consolidateCell(runtime, row = 4, column = 2)
        val first = mapSuggestion(runtime, "stable-cell", 4, 2, 120).copy(createdAtMs = 30, updatedAtMs = 30, confidence = 0.5)
        runtime.replaceSuggestions(listOf(first), nowMs = 30)
        val matured = mapSuggestion(runtime, "stable-cell", 4, 2, 130).copy(createdAtMs = 60, updatedAtMs = 60, confidence = 0.9)

        runtime.replaceSuggestions(listOf(matured), nowMs = 60)

        val actual = runtime.state.activeSuggestions().single()
        assertEquals("stable-cell", actual.id)
        assertEquals(30L, actual.createdAtMs)
        assertEquals(60L, actual.updatedAtMs)
        assertEquals(120, actual.mapChanges.single().after)
        assertEquals(LearningStabilityState.CONSOLIDATED.name, actual.stabilityState)
        assertEquals(SuggestionLifecycle.PENDING, actual.lifecycle)
    }

    @Test
    fun missing_current_advice_turns_pending_into_observing_instead_of_deleting_it() {
        val runtime = runtime()
        consolidateCell(runtime, row = 4, column = 2)
        runtime.replaceSuggestions(listOf(mapSuggestion(runtime, "keep-me", 4, 2, 120)), nowMs = 30)

        runtime.replaceSuggestions(emptyList(), nowMs = 70)

        val actual = runtime.state.suggestions.single()
        assertEquals("keep-me", actual.id)
        assertEquals(SuggestionLifecycle.OBSERVING, actual.lifecycle)
        assertTrue(!actual.actionableAt(runtime.state.calibration.revision))
    }

    @Test
    fun observing_suggestion_returns_pending_with_same_target_when_same_consolidated_generation_returns() {
        val runtime = runtime()
        consolidateCell(runtime, row = 4, column = 2)
        val pending = mapSuggestion(runtime, "returns", 4, 2, 120)
        runtime.replaceSuggestions(listOf(pending), nowMs = 30)
        runtime.replaceSuggestions(emptyList(), nowMs = 40)

        runtime.replaceSuggestions(listOf(mapSuggestion(runtime, "returns", 4, 2, 125)), nowMs = 50)

        val actual = runtime.state.activeSuggestions().single()
        assertEquals(SuggestionLifecycle.PENDING, actual.lifecycle)
        assertEquals(120, actual.mapChanges.single().after)
        assertEquals(30L, actual.createdAtMs)
    }

    @Test
    fun thirteenth_physical_row_survives_snapshot_roundtrip() {
        val map = calibration().mapK.map { it.toMutableList() }.toMutableList()
        map[12][11] = 123
        val state = SessionState(
            sessionId = "row-12",
            calibration = calibration().copy(mapK = map.map { it.toList() }),
        )

        val restored = SessionSnapshotCodec.decode(SessionSnapshotCodec.encode(state))

        assertEquals(123, restored.calibration.mapK[12][11])
        assertEquals(13, restored.calibration.mapK.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun thirteenth_physical_row_is_not_editable() {
        MapCellChange(row = 12, column = 11, before = 100, after = 123)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fourteenth_row_is_rejected() {
        MapCellChange(row = 13, column = 0, before = 100, after = 110)
    }

    @Test
    fun failed_writer_preserves_checkpoint_without_advancing_revision() {
        val runtime = runtime()
        runtime.registerSuggestion(mapSuggestion(runtime, "s1", 3, 0, 145))

        try {
            runtime.applySuggestionToEcu(
                "s1",
                40,
                CalibrationWriter { _, _, _ -> CalibrationWriteResult(false, message = "Falha serial") },
            )
            fail("A escrita deveria falhar")
        } catch (_: IllegalArgumentException) {
            // esperado
        }

        assertEquals(CalibrationRevision(0, 0), runtime.state.calibration.revision)
        assertEquals(1, runtime.state.checkpoints.size)
        assertEquals(1, runtime.state.suggestions.size)
        assertEquals(SuggestionLifecycle.PENDING, runtime.state.suggestions.single().lifecycle)
        assertEquals("Falha serial", runtime.state.lastWriteMessage)
    }

    @Test
    fun registered_suggestion_survives_snapshot_roundtrip_with_lifecycle_metadata() {
        val runtime = runtime()
        val suggestion = mapSuggestion(runtime, "persisted", 2, 1, 120).copy(
            updatedAtMs = 55,
            confidence = 0.82,
            stabilityGeneration = 2,
            stabilityState = LearningStabilityState.CONSOLIDATED.name,
            consolidatedErrorPercent = 6.5,
        )
        runtime.registerSuggestion(suggestion)

        val restored = SessionRuntime(
            SessionSnapshotCodec.decode(SessionSnapshotCodec.encode(runtime.state)),
        )

        assertEquals(listOf(suggestion), restored.state.suggestions)
    }

    @Test
    fun successful_write_keeps_sibling_as_superseded_history() {
        val runtime = runtime()
        val first = mapSuggestion(runtime, "first", 2, 1, 120)
        val sibling = mapSuggestion(runtime, "sibling", 3, 1, 121)
        runtime.registerSuggestion(first)
        runtime.registerSuggestion(sibling)

        runtime.applySuggestionToEcu(first.id, 40, acceptingWriter)

        assertEquals(CalibrationRevision(0, 1), runtime.state.calibration.revision)
        assertEquals(SuggestionLifecycle.APPLIED, runtime.state.suggestions.single { it.id == "first" }.lifecycle)
        assertEquals(SuggestionLifecycle.SUPERSEDED, runtime.state.suggestions.single { it.id == "sibling" }.lifecycle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun stale_cng_evidence_cannot_enter_new_revision() {
        val runtime = runtime()
        val oldRevision = runtime.state.calibration.revision
        val suggestion = LocalSuggestion(
            id = "s1",
            createdAtMs = 30,
            expectedRevision = oldRevision,
            target = SuggestionTarget.CURVE_K,
            curveChanges = listOf(CurvePointChange(0, 1.0, 1.1)),
            rationale = "Ajuste global",
        )
        runtime.registerSuggestion(suggestion)
        runtime.applySuggestionToEcu("s1", 40, acceptingWriter)
        runtime.addEvidence(SessionEvidence("g-old", CalibrationFuel.CNG, 50, "visit-old", 1500.0, 0.5, 4.0, 0.9, oldRevision))
    }

    @Test
    fun petrol_reference_survives_revision_and_snapshot_roundtrip() {
        val runtime = runtime()
        runtime.addEvidence(SessionEvidence("p1", CalibrationFuel.PETROL, 10, "visit-p", 1500.0, 0.5, 4.0, 0.9, null))
        runtime.registerSuggestion(mapSuggestion(runtime, "s1", 3, 0, 145))
        runtime.applySuggestionToEcu("s1", 40, acceptingWriter)

        val restored = SessionSnapshotCodec.decode(SessionSnapshotCodec.encode(runtime.state))

        assertEquals(1, restored.petrolEvidence.size)
        assertEquals(runtime.state.calibration, restored.calibration)
        assertEquals(runtime.state.checkpoints, restored.checkpoints)
        assertNotEquals(CalibrationRevision(0, 0), restored.calibration.revision)
        assertEquals(SuggestionLifecycle.APPLIED, restored.suggestions.single().lifecycle)
    }

    private fun consolidateCell(runtime: SessionRuntime, row: Int, column: Int) {
        val rpm = KMapPhysicalAxes.rpmBins()[column].toDouble()
        val observed = KMapPhysicalAxes.petrolBins()[row]
        val target = observed / 1.10
        runtime.addEvidence(
            SessionEvidence(
                id = "petrol-$row-$column",
                fuel = CalibrationFuel.PETROL,
                collectedAtMs = 1,
                visitId = "petrol-$row-$column",
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
                SessionEvidence(
                    id = "cng-$row-$column-$index",
                    fuel = CalibrationFuel.CNG,
                    collectedAtMs = 10L + index,
                    visitId = "cng-$row-$column-$index",
                    rpm = rpm,
                    mapBar = 0.50,
                    petrolMs = observed,
                    quality = 1.0,
                    cngRevision = runtime.state.calibration.revision,
                    waterC = 82.0,
                ),
            )
        }
        assertEquals(LearningStabilityState.CONSOLIDATED, runtime.mapStability(row, column).state)
    }

    private fun mapSuggestion(
        runtime: SessionRuntime,
        id: String,
        row: Int,
        column: Int,
        target: Int,
    ): LocalSuggestion = LocalSuggestion(
        id = id,
        createdAtMs = 30,
        expectedRevision = runtime.state.calibration.revision,
        target = SuggestionTarget.MAP_K,
        mapChanges = listOf(
            MapCellChange(
                row = row,
                column = column,
                before = runtime.state.calibration.mapK[row][column],
                after = target,
            ),
        ),
        rationale = "Correção local observada",
    )
}
