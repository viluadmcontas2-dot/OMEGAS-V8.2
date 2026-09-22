package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalEpochTransitionTest {
    @Test
    fun `increment is an epoch transition without rollover`() {
        val transition = AutoCalEpochTransition.between(previous = 2, current = 3)
        assertEquals(2, transition?.before)
        assertEquals(3, transition?.after)
        assertFalse(transition?.rollover ?: true)
    }

    @Test
    fun `three to zero remains an epoch transition and is rollover`() {
        val transition = AutoCalEpochTransition.between(previous = 3, current = 0)
        assertEquals(3, transition?.before)
        assertEquals(0, transition?.after)
        assertTrue(transition?.rollover == true)
    }

    @Test
    fun `same value and missing baseline do not create epoch transition`() {
        assertNull(AutoCalEpochTransition.between(previous = 2, current = 2))
        assertNull(AutoCalEpochTransition.between(previous = null, current = 1))
    }
}
