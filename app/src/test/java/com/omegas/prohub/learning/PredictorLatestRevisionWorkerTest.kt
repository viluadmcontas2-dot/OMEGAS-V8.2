package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorLatestRevisionWorkerTest {
    @Test
    fun `stale completion after newer request never publishes`() {
        val worker = PredictorLatestRevisionWorker<String>()
        worker.request(10L, nowMs = 100L)
        val n = worker.claimNext(nowMs = 101L)!!
        worker.request(11L, nowMs = 102L)

        val stale = worker.complete(n, "old", computeMs = 5L, nowMs = 106L)
        assertFalse(stale.published)
        assertNull(stale.value)

        val newest = worker.claimNext(nowMs = 107L)!!
        assertEquals(11L, newest.revision)
        val published = worker.complete(newest, "new", computeMs = 3L, nowMs = 110L)
        assertTrue(published.published)
        assertEquals("new", published.value)
    }

    @Test
    fun `burst keeps only newest pending revision`() {
        val worker = PredictorLatestRevisionWorker<Int>()
        worker.request(1L, 10L)
        val first = worker.claimNext(11L)!!
        worker.request(2L, 12L)
        worker.request(3L, 13L)
        worker.request(4L, 14L)

        val pending = worker.pendingRevision()
        assertEquals(4L, pending)
        worker.complete(first, 1, 7L, 18L)
        val last = worker.claimNext(19L)!!
        assertEquals(4L, last.revision)

        val metrics = worker.metrics()
        assertEquals(4L, metrics.requested)
        assertEquals(1L, metrics.computed)
        assertEquals(2L, metrics.coalesced)
        assertEquals(1L, metrics.superseded)
        assertTrue(metrics.maxQueueAgeMs >= 5L)
    }

    @Test
    fun `duplicate or older request cannot replace newer pending work`() {
        val worker = PredictorLatestRevisionWorker<Unit>()
        worker.request(8L, 1L)
        worker.request(8L, 2L)
        worker.request(7L, 3L)
        assertEquals(8L, worker.pendingRevision())
        assertEquals(3L, worker.metrics().requested)
        assertEquals(2L, worker.metrics().coalesced)
    }
}
