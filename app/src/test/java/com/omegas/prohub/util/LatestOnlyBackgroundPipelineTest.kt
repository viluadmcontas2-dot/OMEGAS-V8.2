package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LatestOnlyBackgroundPipelineTest {
    @Test
    fun submitReturnsWhileConsumerIsBlocked() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pipeline = LatestOnlyBackgroundPipeline("latest-only-non-blocking-test")
        try {
            assertTrue(pipeline.submit(1L) {
                started.countDown()
                release.await(2L, TimeUnit.SECONDS)
            })
            assertTrue(started.await(1L, TimeUnit.SECONDS))
            assertTrue(pipeline.submit(2L) {})
            assertEquals(1, pipeline.metricsJson().getInt("pending"))
        } finally {
            release.countDown()
            pipeline.close()
        }
    }

    @Test
    fun slowConsumerKeepsOnlyNewestPendingState() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val seen = Collections.synchronizedList(mutableListOf<Long>())
        val pipeline = LatestOnlyBackgroundPipeline("latest-only-saturation-test")
        try {
            assertTrue(pipeline.submit(1L) {
                started.countDown()
                release.await(2L, TimeUnit.SECONDS)
                seen += 1L
            })
            assertTrue(started.await(1L, TimeUnit.SECONDS))
            (2L..100L).forEach { sequence ->
                assertTrue(pipeline.submit(sequence) { seen += sequence })
            }
            assertEquals(1, pipeline.metricsJson().getInt("pending"))
            assertTrue(pipeline.metricsJson().getLong("coalesced") >= 98L)

            release.countDown()
            assertTrue(pipeline.flush(2_000L))
            assertEquals(listOf(1L, 100L), seen.toList())
            assertEquals(100L, pipeline.metricsJson().getLong("lastCompletedSequence"))
            assertEquals(0, pipeline.metricsJson().getInt("pending"))
        } finally {
            release.countDown()
            pipeline.close()
        }
    }

    @Test
    fun failureDoesNotBlockNewestPendingState() {
        val failures = Collections.synchronizedList(mutableListOf<Long>())
        val seen = Collections.synchronizedList(mutableListOf<Long>())
        val pipeline = LatestOnlyBackgroundPipeline(
            threadName = "latest-only-failure-test",
            onFailure = { sequence, _ -> failures += sequence },
        )
        try {
            assertTrue(pipeline.submit(1L) { error("expected") })
            assertTrue(pipeline.submit(2L) { seen += 2L })
            assertTrue(pipeline.flush(2_000L))
            assertTrue(failures.contains(1L) || pipeline.metricsJson().getLong("coalesced") >= 1L)
            assertEquals(listOf(2L), seen.toList())
        } finally {
            pipeline.close()
        }
    }
}
