package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RealtimeLearningBufferTest {
    @Test
    fun transientFramesCoalesceInsteadOfGrowingBacklog() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val buffer = RealtimeLearningBuffer("learning-buffer-coalesce", importantCapacity = 16)
        try {
            buffer.beginGeneration(10L)
            assertTrue(buffer.submit(10L, 1L, true) {
                started.countDown()
                release.await(2L, TimeUnit.SECONDS)
            })
            assertTrue(started.await(1L, TimeUnit.SECONDS))

            repeat(5_000) { index -> assertTrue(buffer.submit(10L, index + 2L, false) {}) }

            val busy = buffer.metricsJson()
            assertTrue(busy.getLong("coalescedTransient") >= 4_900L)
            assertTrue(busy.getInt("pendingTransient") <= 1)
            assertTrue(busy.getInt("pending") <= 2)

            release.countDown()
            assertTrue(buffer.flush(2_000L))
            assertEquals(0, buffer.metricsJson().getInt("pending"))
        } finally {
            release.countDown()
            buffer.close()
        }
    }

    @Test
    fun importantEvidenceKeepsFifoOrderWhenBelowHotLimit() {
        val seen = Collections.synchronizedList(mutableListOf<Long>())
        val buffer = RealtimeLearningBuffer("learning-buffer-important", importantCapacity = 3)
        try {
            buffer.beginGeneration(20L)
            (1L..3L).forEach { sequence -> assertTrue(buffer.submit(20L, sequence, true) { seen += sequence }) }
            assertTrue(buffer.flush(2_000L))
            assertEquals((1L..3L).toList(), seen.toList())
            assertEquals(0L, buffer.metricsJson().getLong("supersededImportant"))
        } finally {
            buffer.close()
        }
    }

    @Test
    fun generationSwitchPurgesQueuedOldSessionAndRejectsStaleSubmission() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val buffer = RealtimeLearningBuffer("learning-buffer-generation", importantCapacity = 3)
        try {
            buffer.beginGeneration(30L)
            assertTrue(buffer.submit(30L, 1L, true) {
                started.countDown()
                release.await(2L, TimeUnit.SECONDS)
                seen += "old-active"
            })
            assertTrue(started.await(1L, TimeUnit.SECONDS))
            repeat(3) { index -> assertTrue(buffer.submit(30L, index + 2L, true) { seen += "old-queued-$index" }) }

            buffer.beginGeneration(31L)
            assertFalse(buffer.submit(30L, 100L, true) { seen += "stale" })
            assertTrue(buffer.submit(31L, 101L, true) { seen += "new" })
            release.countDown()
            assertTrue(buffer.flush(2_000L))

            assertTrue("new" in seen)
            assertFalse(seen.any { it.startsWith("old-queued") })
            assertFalse("stale" in seen)
            assertTrue(buffer.metricsJson().getLong("purgedImportant") >= 3L)
            assertTrue(buffer.metricsJson().getLong("rejectedStale") >= 1L)
        } finally {
            release.countDown()
            buffer.close()
        }
    }

    @Test
    fun overloadKeepsNewestThreePendingEvidenceWindows() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val seen = Collections.synchronizedList(mutableListOf<Long>())
        val buffer = RealtimeLearningBuffer("learning-buffer-recent", importantCapacity = 128)
        try {
            buffer.beginGeneration(40L)
            assertTrue(buffer.submit(40L, 1L, true) {
                started.countDown()
                release.await(2L, TimeUnit.SECONDS)
                seen += 1L
            })
            assertTrue(started.await(1L, TimeUnit.SECONDS))
            (2L..100L).forEach { sequence -> assertTrue(buffer.submit(40L, sequence, true) { seen += sequence }) }

            val busy = buffer.metricsJson()
            assertEquals(RealtimeLearningBuffer.MAX_HOT_EVIDENCE, busy.getInt("capacityImportant"))
            assertEquals(3, busy.getInt("pendingImportant"))
            assertTrue(busy.getLong("supersededImportant") >= 90L)
            assertEquals("SESSION_RECORDER", busy.getString("durableBacklog"))
            assertEquals("SUPERSEDE_OLDEST_OVERLAPPING_PENDING_EVIDENCE", busy.getString("overloadPolicy"))

            release.countDown()
            assertTrue(buffer.flush(2_000L))
            assertTrue(1L in seen)
            assertTrue(98L in seen && 99L in seen && 100L in seen)
            assertFalse(2L in seen)
        } finally {
            release.countDown()
            buffer.close()
        }
    }
}
