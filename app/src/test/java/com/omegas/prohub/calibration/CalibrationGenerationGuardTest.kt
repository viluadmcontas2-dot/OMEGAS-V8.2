package com.omegas.prohub.calibration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CalibrationGenerationGuardTest {
    private fun factors(): IntArray = IntArray(30) { 0x4000 + it }

    @Test
    fun `contador e MUL ACT identicos mantem geracao estavel`() {
        val result = CalibrationGenerationGuard.evaluate(4, 4, factors(), factors())
        assertTrue(result.stable)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `mudanca no contador invalida snapshot composto`() {
        val result = CalibrationGenerationGuard.evaluate(4, 5, factors(), factors())
        assertFalse(result.stable)
        assertTrue("AUTOMATCH_COUNT_CHANGED" in result.reasons)
    }

    @Test
    fun `mudanca em um unico MUL ACT invalida snapshot composto`() {
        val end = factors().also { it[17] += 1 }
        val result = CalibrationGenerationGuard.evaluate(4, 4, factors(), end)
        assertFalse(result.stable)
        assertTrue("MUL_ACT_CHANGED" in result.reasons)
    }

    @Test
    fun `shape ou contador fora de U16 falham fechado`() {
        assertThrows(IllegalArgumentException::class.java) { CalibrationGenerationGuard.evaluate(-1, 0, factors(), factors()) }
        assertThrows(IllegalArgumentException::class.java) { CalibrationGenerationGuard.evaluate(0, 0, IntArray(29), factors()) }
    }
}
