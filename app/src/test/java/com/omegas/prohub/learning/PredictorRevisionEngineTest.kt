package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PredictorRevisionEngineTest {
    @Test
    fun `one hundred thousand visual frames with same scientific revision produce no recompute`() {
        val engine = PredictorRevisionEngine(rows = 12, columns = 12)
        val key = key()
        val first = engine.plan(key)
        assertEquals(PredictorRecomputeKind.FULL, first.kind)
        engine.accept(first)

        repeat(100_000) {
            val plan = engine.plan(key)
            assertEquals(PredictorRecomputeKind.NOOP, plan.kind)
        }
        assertEquals(1L, engine.metrics().computedPlans)
        assertEquals(100_001L, engine.metrics().requestedPlans)
    }

    @Test
    fun `local evidence revision with one dirty cell produces one cell patch`() {
        val engine = PredictorRevisionEngine(12, 12)
        val base = key()
        engine.accept(engine.plan(base))
        val dirty = PredictorCellRef(3, 7)

        val patch = engine.plan(
            key = base.copy(evidenceRevision = 2L),
            affectedCells = setOf(dirty),
        )

        assertEquals(PredictorRecomputeKind.PATCH, patch.kind)
        assertEquals(setOf(dirty), patch.cells)
    }

    @Test
    fun `sensitivity revision patches only declared region`() {
        val engine = PredictorRevisionEngine(12, 12)
        val base = key()
        engine.accept(engine.plan(base))
        val cells = setOf(PredictorCellRef(1, 1), PredictorCellRef(1, 2))

        val patch = engine.plan(base.copy(sensitivityRevision = 9L), cells)

        assertEquals(PredictorRecomputeKind.PATCH, patch.kind)
        assertEquals(cells, patch.cells)
    }

    @Test
    fun `geometry physics calibration or model calibration change forces full grid`() {
        val variants = listOf<(PredictorScientificRevisionKey) -> PredictorScientificRevisionKey>(
            { it.copy(calibrationRevision = it.calibrationRevision + 1) },
            { it.copy(geometryRevision = it.geometryRevision + 1) },
            { it.copy(physicsRevision = it.physicsRevision + 1) },
            { it.copy(modelCalibrationRevision = it.modelCalibrationRevision + 1) },
        )
        variants.forEach { mutate ->
            val engine = PredictorRevisionEngine(12, 12)
            val base = key()
            engine.accept(engine.plan(base))
            val plan = engine.plan(mutate(base), setOf(PredictorCellRef(0, 0)))
            assertEquals(PredictorRecomputeKind.FULL, plan.kind)
            assertEquals(144, plan.cells.size)
        }
    }

    @Test
    fun `scientific revision changes token while same revision keeps token`() {
        val engine = PredictorRevisionEngine(12, 12)
        val base = key()
        val first = engine.plan(base)
        engine.accept(first)
        val same = engine.plan(base)
        val changed = engine.plan(base.copy(referenceRevision = 5L), setOf(PredictorCellRef(2, 2)))

        assertEquals(first.revisionToken, same.revisionToken)
        assertNotEquals(first.revisionToken, changed.revisionToken)
    }

    private fun key() = PredictorScientificRevisionKey(
        evidenceRevision = 1L,
        referenceRevision = 1L,
        calibrationRevision = 1L,
        geometryRevision = 1L,
        physicsRevision = 1L,
        modelCalibrationRevision = 1L,
        sensitivityRevision = 1L,
    )
}
