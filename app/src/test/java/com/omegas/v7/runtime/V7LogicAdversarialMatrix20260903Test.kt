package com.omegas.v7.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V7LogicAdversarialMatrix20260903Test {
    private val rev0 = CalibrationRevisionV7(0, 0)

    private fun calibration(rev: CalibrationRevisionV7 = rev0) = CalibrationStateV7(
        revision = rev,
        curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
        mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
            List(CalibrationShapeV7.MAP_K_COLUMNS) { 100 }
        },
    )

    private fun runtime(rev: CalibrationRevisionV7 = rev0) = V7SessionRuntime(
        V7SessionState(sessionId = "logic-adversarial-20260903", calibration = calibration(rev)),
    )

    private fun petrol(visit: String, id: String = visit, ms: Double = 2.5) = EvidenceV7(
        id = id,
        fuel = FuelV7.PETROL,
        collectedAtMs = 1L,
        visitId = visit,
        rpm = 1500.0,
        mapBar = 0.50,
        petrolMs = ms,
        quality = 1.0,
        cngRevision = null,
        waterC = 82.0,
    )

    private fun cng(
        visit: String,
        id: String = visit,
        ms: Double = 2.8,
        rev: CalibrationRevisionV7 = rev0,
    ) = EvidenceV7(
        id = id,
        fuel = FuelV7.CNG,
        collectedAtMs = 2L,
        visitId = visit,
        rpm = 1500.0,
        mapBar = 0.50,
        petrolMs = ms,
        quality = 1.0,
        cngRevision = rev,
        waterC = 82.0,
    )

    private fun mapSuggestion(
        id: String = "s1",
        rev: CalibrationRevisionV7 = rev0,
        row: Int = 0,
        column: Int = 0,
        before: Int = 100,
        after: Int = 101,
    ) = LocalSuggestionV7(
        id = id,
        createdAtMs = 10L,
        expectedRevision = rev,
        target = SuggestionTargetV7.MAP_K,
        mapChanges = listOf(MapCellChangeV7(row, column, before, after)),
        rationale = "adversarial logic check",
    )

    private fun curveSuggestion(
        id: String = "c1",
        rev: CalibrationRevisionV7 = rev0,
        index: Int = 0,
        before: Double = 1.0,
        after: Double = 1.01,
    ) = LocalSuggestionV7(
        id = id,
        createdAtMs = 10L,
        expectedRevision = rev,
        target = SuggestionTargetV7.CURVE_K,
        curveChanges = listOf(CurvePointChangeV7(index, before, after)),
        rationale = "adversarial logic check",
    )

    private fun expectFailure(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: Throwable) { failed = true }
        assertTrue("expected fail-closed behavior", failed)
    }

    @Test fun `01 same petrol visit is immutable`() {
        val r = runtime(); r.addEvidence(petrol("v1", "a", 2.5)); r.addEvidence(petrol("v1", "b", 9.9))
        assertEquals(1, r.state.petrolEvidence.size); assertEquals(2.5, r.state.petrolEvidence.single().petrolMs, 0.0)
    }

    @Test fun `02 same CNG visit is immutable`() {
        val r = runtime(); r.addEvidence(cng("v1", "a", 2.8)); r.addEvidence(cng("v1", "b", 9.9))
        assertEquals(1, r.state.activeCngEvidence().size); assertEquals(2.8, r.state.activeCngEvidence().single().petrolMs, 0.0)
    }

    @Test fun `03 distinct petrol visits remain independent evidence`() {
        val r = runtime(); r.addEvidence(petrol("v1")); r.addEvidence(petrol("v2")); assertEquals(2, r.state.petrolEvidence.size)
    }

    @Test fun `04 distinct CNG visits remain independent evidence`() {
        val r = runtime(); r.addEvidence(cng("v1")); r.addEvidence(cng("v2")); assertEquals(2, r.state.activeCngEvidence().size)
    }

    @Test fun `05 CNG evidence from stale calibration revision is rejected`() {
        val r = runtime(); expectFailure { r.addEvidence(cng("v", rev = CalibrationRevisionV7(0, 1))) }
    }

    @Test fun `06 same suggestion id replaces instead of duplicating`() {
        val r = runtime(); r.registerSuggestion(mapSuggestion(after = 101)); r.registerSuggestion(mapSuggestion(after = 102))
        assertEquals(1, r.state.suggestions.size); assertEquals(102, r.state.suggestions.single().mapChanges.single().after)
    }

    @Test fun `07 replaceSuggestions rejects duplicate IDs in same generation`() {
        val r = runtime(); val s = mapSuggestion(); expectFailure { r.replaceSuggestions(listOf(s, s.copy(updatedAtMs = 11L)), 20L) }
    }

    @Test fun `08 registerSuggestion rejects stale revision`() {
        val r = runtime(); expectFailure { r.registerSuggestion(mapSuggestion(rev = CalibrationRevisionV7(0, 1))) }
    }

    @Test fun `09 registering proposal alone never mutates calibration`() {
        val r = runtime(); val before = r.state.calibration; r.registerSuggestion(mapSuggestion()); assertEquals(before, r.state.calibration)
    }

    @Test fun `10 missing suggestion cannot invoke writer`() {
        val r = runtime(); var called = false
        expectFailure { r.applySuggestionToEcu("missing", 100L) { _, desired, _ -> called = true; CalibrationWriteResultV7(true, desired, "ok") } }
        assertFalse(called)
    }

    @Test fun `11 failed writer leaves calibration unchanged`() {
        val r = runtime(); r.registerSuggestion(mapSuggestion()); val before = r.state.calibration
        expectFailure { r.applySuggestionToEcu("s1", 100L) { _, _, _ -> CalibrationWriteResultV7(false, null, "rejected") } }
        assertEquals(before, r.state.calibration)
    }

    @Test fun `12 throwing writer leaves calibration unchanged`() {
        val r = runtime(); r.registerSuggestion(mapSuggestion()); val before = r.state.calibration
        expectFailure { r.applySuggestionToEcu("s1", 100L) { _, _, _ -> error("transport failed") } }
        assertEquals(before, r.state.calibration)
    }

    @Test fun `13 success without readback is rejected`() {
        val r = runtime(); r.registerSuggestion(mapSuggestion())
        expectFailure { r.applySuggestionToEcu("s1", 100L) { _, _, _ -> CalibrationWriteResultV7(true, null, "bad") } }
    }

    @Test fun `14 readback with wrong revision is rejected`() {
        val r = runtime(); r.registerSuggestion(mapSuggestion())
        expectFailure { r.applySuggestionToEcu("s1", 100L) { current, _, _ -> CalibrationWriteResultV7(true, current, "wrong") } }
    }

    @Test fun `15 checkpoint exists before writer is invoked`() {
        val r = runtime(); r.registerSuggestion(mapSuggestion()); var observed = false
        r.applySuggestionToEcu("s1", 100L) { _, desired, _ -> observed = r.state.checkpoints.size == 1; CalibrationWriteResultV7(true, desired, "ok") }
        assertTrue(observed)
    }

    @Test fun `16 successful map apply changes only target cell and map revision`() {
        val r = runtime(); r.registerSuggestion(mapSuggestion(row = 0, column = 0)); val before = r.state.calibration
        val applied = r.applySuggestionToEcu("s1", 100L) { _, desired, _ -> CalibrationWriteResultV7(true, desired, "ok") }
        assertEquals(1L, applied.revision.mapK); assertEquals(0L, applied.revision.curveK)
        assertEquals(101, applied.mapK[0][0]); assertEquals(before.mapK[0][1], applied.mapK[0][1])
    }

    @Test fun `17 applying one proposal supersedes siblings from old revision`() {
        val r = runtime(); r.registerSuggestion(mapSuggestion("s1", row = 0, column = 0)); r.registerSuggestion(mapSuggestion("s2", row = 0, column = 1))
        r.applySuggestionToEcu("s1", 100L) { _, desired, _ -> CalibrationWriteResultV7(true, desired, "ok") }
        assertEquals(SuggestionLifecycleV7.APPLIED, r.state.suggestions.single { it.id == "s1" }.lifecycle)
        assertEquals(SuggestionLifecycleV7.SUPERSEDED, r.state.suggestions.single { it.id == "s2" }.lifecycle)
    }

    @Test fun `18 restore checkpoint returns calibration before apply`() {
        val r = runtime(); r.registerSuggestion(mapSuggestion()); val before = r.state.calibration
        r.applySuggestionToEcu("s1", 100L) { _, desired, _ -> CalibrationWriteResultV7(true, desired, "ok") }
        val restored = r.restoreCheckpoint(r.state.checkpoints.single().id); assertEquals(before, restored); assertEquals(before, r.state.calibration)
    }

    @Test fun `19 no-op map change is invalid`() { expectFailure { MapCellChangeV7(0, 0, 100, 100) } }
    @Test fun `20 storage-only thirteenth row cannot be edited`() { expectFailure { MapCellChangeV7(12, 0, 100, 101) } }
    @Test fun `21 nonexistent map column is rejected`() { expectFailure { MapCellChangeV7(0, 12, 100, 101) } }
    @Test fun `22 invalid curve index is rejected`() { expectFailure { CurvePointChangeV7(30, 1.0, 1.1) } }
    @Test fun `23 petrol evidence cannot carry CNG revision`() { expectFailure { EvidenceV7("x", FuelV7.PETROL, 1L, "v", 1000.0, .4, 2.0, 1.0, rev0) } }
    @Test fun `24 CNG evidence requires revision`() { expectFailure { EvidenceV7("x", FuelV7.CNG, 1L, "v", 1000.0, .4, 2.0, 1.0, null) } }
    @Test fun `25 negative quality is rejected`() { expectFailure { EvidenceV7("x", FuelV7.PETROL, 1L, "v", 1000.0, .4, 2.0, -.1, null) } }
    @Test fun `26 quality above one is rejected`() { expectFailure { EvidenceV7("x", FuelV7.PETROL, 1L, "v", 1000.0, .4, 2.0, 1.1, null) } }
    @Test fun `27 negative rpm is rejected`() { expectFailure { EvidenceV7("x", FuelV7.PETROL, 1L, "v", -1.0, .4, 2.0, 1.0, null) } }
    @Test fun `28 nonfinite rpm is rejected`() { expectFailure { EvidenceV7("x", FuelV7.PETROL, 1L, "v", Double.NaN, .4, 2.0, 1.0, null) } }
    @Test fun `29 negative MAP is rejected`() { expectFailure { EvidenceV7("x", FuelV7.PETROL, 1L, "v", 1000.0, -.1, 2.0, 1.0, null) } }
    @Test fun `30 negative petrol injection is rejected`() { expectFailure { EvidenceV7("x", FuelV7.PETROL, 1L, "v", 1000.0, .4, -.1, 1.0, null) } }
    @Test fun `31 pending map proposal cannot be empty`() { expectFailure { LocalSuggestionV7("x", 1L, rev0, SuggestionTargetV7.MAP_K, rationale = "x") } }
    @Test fun `32 pending curve proposal cannot be empty`() { expectFailure { LocalSuggestionV7("x", 1L, rev0, SuggestionTargetV7.CURVE_K, rationale = "x") } }
    @Test fun `33 map proposal cannot carry curve changes`() { expectFailure { LocalSuggestionV7("x", 1L, rev0, SuggestionTargetV7.MAP_K, curveChanges = listOf(CurvePointChangeV7(0,1.0,1.1)), rationale = "x") } }
    @Test fun `34 curve proposal cannot carry map changes`() { expectFailure { LocalSuggestionV7("x", 1L, rev0, SuggestionTargetV7.CURVE_K, mapChanges = listOf(MapCellChangeV7(0,0,100,101)), rationale = "x") } }
    @Test fun `35 successful curve apply increments only curve revision`() {
        val r = runtime(); r.registerSuggestion(curveSuggestion()); val applied = r.applySuggestionToEcu("c1",100L) { _, desired, _ -> CalibrationWriteResultV7(true, desired, "ok") }
        assertEquals(1L, applied.revision.curveK); assertEquals(0L, applied.revision.mapK); assertEquals(1.01, applied.curveK[0], 0.0)
    }
}
