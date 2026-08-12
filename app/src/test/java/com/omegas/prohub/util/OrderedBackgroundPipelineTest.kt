package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OrderedBackgroundPipelineTest {
    @Test
    fun submitReturnsWhileSlowWorkIsStillBlocked() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pipeline = OrderedBackgroundPipeline("pipeline-non-blocking-test")
        try {
            assertTrue(
                pipeline.submit(1L) {
                    started.countDown()
                    release.await(2L, TimeUnit.SECONDS)
                },
            )
            assertTrue(started.await(1L, TimeUnit.SECONDS))
            assertEquals(1L, pipeline.metricsJson().getLong("pending"))

            release.countDown()
            assertTrue(pipeline.flush(2_000L))
            assertEquals(0L, pipeline.metricsJson().getLong("pending"))
        } finally {
            release.countDown()
            pipeline.close()
        }
    }

    @Test
    fun preservesSubmissionOrderAndFlushesEveryAcceptedTask() {
        val seen = Collections.synchronizedList(mutableListOf<Long>())
        val pipeline = OrderedBackgroundPipeline("pipeline-order-test")
        try {
            (1L..100L).forEach { sequence ->
                assertTrue(pipeline.submit(sequence) { seen += sequence })
            }

            assertTrue(pipeline.flush(3_000L))
            assertEquals((1L..100L).toList(), seen.toList())
            assertEquals(100L, pipeline.metricsJson().getLong("completed"))
            assertEquals(100L, pipeline.metricsJson().getLong("lastCompletedSequence"))
        } finally {
            pipeline.close()
        }
    }

    @Test
    fun failureIsRecordedWithoutStoppingLaterTasks() {
        val failures = Collections.synchronizedList(mutableListOf<Long>())
        val seen = Collections.synchronizedList(mutableListOf<Long>())
        val pipeline = OrderedBackgroundPipeline(
            threadName = "pipeline-failure-test",
            onFailure = { sequence, _ -> failures += sequence },
        )
        try {
            assertTrue(pipeline.submit(1L) { error("expected") })
            assertTrue(pipeline.submit(2L) { seen += 2L })

            assertTrue(pipeline.flush(2_000L))
            assertEquals(listOf(1L), failures.toList())
            assertEquals(listOf(2L), seen.toList())
            assertEquals(1L, pipeline.metricsJson().getLong("failed"))
            assertEquals(2L, pipeline.metricsJson().getLong("completed"))
        } finally {
            pipeline.close()
        }
    }
}
