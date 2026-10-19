package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class KBatchBackgroundVerifyTest {
    @Test fun divergentListsOnlyChangedMismatches() {
        val expected = intArrayOf(10, 20, 30, 40)
        val actual = intArrayOf(10, 99, 30, 77)
        assertEquals(listOf(1, 3), KBatchVerifyCore.divergent(expected, actual, listOf(3, 1)))
        assertEquals(listOf(3), KBatchVerifyCore.divergent(expected, actual, listOf(0, 2, 3))) // 1 não foi alterada: ignorada
        assertEquals(emptyList<Int>(), KBatchVerifyCore.divergent(expected, expected, listOf(0, 1, 2, 3)))
    }

    @Test fun nextPhaseCorrectsOnceThenFails() {
        assertEquals(KBatchVerifyCore.Phase.OK, KBatchVerifyCore.nextPhase(0, false))
        assertEquals(KBatchVerifyCore.Phase.OK, KBatchVerifyCore.nextPhase(0, true))
        assertEquals(KBatchVerifyCore.Phase.FIXING, KBatchVerifyCore.nextPhase(2, false))
        assertEquals(KBatchVerifyCore.Phase.FAILED, KBatchVerifyCore.nextPhase(1, true))
    }

    @Test fun detailNamesTheCell() {
        assertEquals("Gravação diferente na célula L3 C5 — corrigindo", KBatchVerifyCore.mismatchDetail(KBatchVerifyCore.mapCellLabel(2, 4), true))
        assertEquals("Gravação diferente na célula L3 C5", KBatchVerifyCore.mismatchDetail(KBatchVerifyCore.mapCellLabel(2, 4), false))
    }
}
