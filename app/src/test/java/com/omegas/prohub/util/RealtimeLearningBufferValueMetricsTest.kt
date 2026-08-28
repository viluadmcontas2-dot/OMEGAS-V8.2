package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeLearningBufferValueMetricsTest {
    @Test
    fun importantEvidenceCostIsMeasuredSeparatelyFromTransientState() {
        val buffer = RealtimeLearningBuffer("learning-buffer-value-metrics", importantCapacity = 3)
        try {
            buffer.beginGeneration(77L)
            assertTrue(buffer.submit(77L, 1L, true) { Thread.sleep(15L) })
            assertTrue(buffer.flush(2_000L))

            val afterEvidence = buffer.metricsJson()
            assertEquals(1L, afterEvidence.getLong("executedImportant"))
            assertEquals(0L, afterEvidence.getLong("executedTransient"))
            assertTrue(afterEvidence.getLong("lastImportantProcessingMs") >= 5L)
            assertTrue(afterEvidence.getLong("maxImportantProcessingMs") >= afterEvidence.getLong("lastImportantProcessingMs"))

            assertTrue(buffer.submit(77L, 2L, false) {})
            assertTrue(buffer.flush(2_000L))
            val afterTransient = buffer.metricsJson()
            assertEquals(1L, afterTransient.getLong("executedImportant"))
            assertEquals(1L, afterTransient.getLong("executedTransient"))
            assertTrue(afterTransient.getLong("maxImportantProcessingMs") >= 5L)
        } finally {
            buffer.close()
        }
    }
}
