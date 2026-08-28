package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RuntimeConsumerBudgetMetricsTest {
    @Test
    fun `latest only consumer exposes bounded pending bytes and overload contract`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pipeline = LatestOnlyBackgroundPipeline("budget-latest-test")
        try {
            pipeline.submit(1L, estimatedBytes = 111) {
                started.countDown()
                release.await(1, TimeUnit.SECONDS)
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            pipeline.submit(2L, estimatedBytes = 321) {}

            val metrics = pipeline.metricsJson()
            assertEquals(1, metrics.getInt("queueBound"))
            assertEquals(321, metrics.getInt("pendingEstimatedBytes"))
            assertEquals("COALESCE_PENDING_TO_LATEST", metrics.getString("overloadPolicy"))
            assertEquals("EVENT_DRIVEN_NO_TIMER", metrics.getString("cadence"))
        } finally {
            release.countDown()
            pipeline.close()
        }
    }

    @Test
    fun `science consumer exposes bounded queued bytes and semantic overload contract`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val buffer = RealtimeLearningBuffer("budget-science-test", importantCapacity = 3)
        try {
            buffer.beginGeneration(9L)
            buffer.submit(9L, 1L, EvidenceWorkClass.STATIC_REFERENCE, estimatedBytes = 120) {
                started.countDown()
                release.await(1, TimeUnit.SECONDS)
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            buffer.submit(9L, 2L, EvidenceWorkClass.DYNAMIC_COHERENT, estimatedBytes = 240) {}

            val metrics = buffer.metricsJson()
            assertEquals(3, metrics.getInt("queueBoundImportant"))
            assertTrue(metrics.getLong("queuedEstimatedBytes") >= 240L)
            assertEquals("SUPERSEDE_LOWEST_VALUE_PENDING_OR_REJECT_INCOMING", metrics.getString("overloadPolicy"))
            assertEquals(false, metrics.getBoolean("acquisitionDropAllowed"))
        } finally {
            release.countDown()
            buffer.close()
        }
    }
}
