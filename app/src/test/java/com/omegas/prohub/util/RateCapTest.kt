package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateCapTest {
    @Test fun `aceita ate o teto na janela e descarta o excesso`() {
        val cap = RateCap(maxPerWindow = 3, windowMs = 1_000L)
        assertTrue(cap.allow(0L))
        assertTrue(cap.allow(10L))
        assertTrue(cap.allow(20L))
        assertFalse(cap.allow(30L))
        assertFalse(cap.allow(999L))
        assertEquals(2L, cap.takeDropped())
        assertEquals(0L, cap.takeDropped())
    }

    @Test fun `nova janela volta a aceitar`() {
        val cap = RateCap(maxPerWindow = 1, windowMs = 1_000L)
        assertTrue(cap.allow(0L))
        assertFalse(cap.allow(500L))
        assertTrue(cap.allow(1_000L))
    }

    @Test fun `relogio que anda para tras nao trava o teto`() {
        val cap = RateCap(maxPerWindow = 1, windowMs = 1_000L)
        assertTrue(cap.allow(5_000L))
        assertTrue(cap.allow(100L))
    }
}
