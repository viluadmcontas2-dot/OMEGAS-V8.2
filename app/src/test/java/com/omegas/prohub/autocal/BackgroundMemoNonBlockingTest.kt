package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMemoNonBlockingTest {
    @Test
    fun `non blocking read never computes on caller and serves stale while warmer refreshes`() {
        var now = 0L
        var runs = 0
        val memo = BackgroundMemo(
            refreshMs = 1_000L,
            staleMs = 2_500L,
            watchMs = 15_000L,
            clock = { now },
        ) {
            runs += 1
            "v$runs"
        }

        assertEquals("warming", memo.getNonBlocking("warming"))
        assertEquals(0, runs)

        assertTrue(memo.refreshIfWatched())
        assertEquals("v1", memo.getNonBlocking("warming"))
        assertEquals(1, runs)

        now += 4_000L
        assertEquals("stale value must not block JS", "v1", memo.getNonBlocking("warming"))
        assertEquals(1, runs)

        assertTrue(memo.refreshIfWatched())
        assertEquals("v2", memo.getNonBlocking("warming"))

        memo.invalidate()
        assertEquals("last safe value survives invalidation until background refresh", "v2", memo.getNonBlocking("warming"))
        assertEquals(2, runs)
    }
}
