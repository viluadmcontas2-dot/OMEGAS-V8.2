package com.omegas.prohub.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EvidenceRouterBackpressureTest {
    @Test
    fun highValueEvidenceSupersedesLowestPendingAndLowValueCannotEvictHigherValue() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val buffer = RealtimeLearningBuffer("evidence-router-priority", importantCapacity = 3)
        try {
            buffer.beginGeneration(91L)
            assertTrue(buffer.submit(91L, 1L, EvidenceWorkClass.DYNAMIC_COHERENT) {
                started.countDown()
                release.await(2L, TimeUnit.SECONDS)
                seen += "active"
            })
            assertTrue(started.await(1L, TimeUnit.SECONDS))

            assertTrue(buffer.submit(91L, 2L, EvidenceWorkClass.STATIC_REFERENCE) { seen += "static" })
            assertTrue(buffer.submit(91L, 3L, EvidenceWorkClass.DYNAMIC_COHERENT) { seen += "dynamic" })
            assertTrue(buffer.submit(91L, 4L, EvidenceWorkClass.FAST_KSTAR) { seen += "fast" })

            // Fila cheia: revalidação pós-write deve retirar o menor valor (STATIC_REFERENCE).
            assertTrue(buffer.submit(91L, 5L, EvidenceWorkClass.POST_WRITE_REVALIDATION) { seen += "post" })
            // Agora os pendentes são DYNAMIC, FAST e POST. STATIC não pode expulsar nenhum deles.
            assertFalse(buffer.submit(91L, 6L, EvidenceWorkClass.STATIC_REFERENCE) { seen += "low-incoming" })

            val busy = buffer.metricsJson()
            assertTrue(busy.getLong("supersededImportant") >= 1L)
            assertTrue(busy.getLong("rejectedLowValue") >= 1L)
            assertTrue(busy.getJSONObject("supersededByClass").getLong("STATIC_REFERENCE") >= 1L)
            assertTrue(busy.getJSONObject("rejectedByClass").getLong("STATIC_REFERENCE") >= 1L)

            release.countDown()
            assertTrue(buffer.flush(2_000L))
            assertFalse("static" in seen)
            assertFalse("low-incoming" in seen)
            assertTrue("dynamic" in seen)
            assertTrue("fast" in seen)
            assertTrue("post" in seen)
        } finally {
            release.countDown()
            buffer.close()
        }
    }

    @Test
    fun legacyBooleanProducerStillMapsToSemanticClasses() {
        val buffer = RealtimeLearningBuffer("evidence-router-legacy", importantCapacity = 3)
        try {
            buffer.beginGeneration(92L)
            assertTrue(buffer.submit(92L, 1L, true) {})
            assertTrue(buffer.submit(92L, 2L, false) {})
            assertTrue(buffer.flush(2_000L))
            val metrics = buffer.metricsJson()
            assertTrue(metrics.getJSONObject("executedByClass").getLong("DYNAMIC_COHERENT") >= 1L)
            assertTrue(metrics.getJSONObject("executedByClass").getLong("DIAGNOSTIC_ONLY") >= 1L)
        } finally {
            buffer.close()
        }
    }
}
