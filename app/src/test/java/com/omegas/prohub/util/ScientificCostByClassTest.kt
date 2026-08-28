package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificCostByClassTest {
    @Test
    fun `055a publishes qualitative information class separately from measured cost`() {
        val buffer = RealtimeLearningBuffer("science-cost-test", importantCapacity = 3)
        try {
            buffer.beginGeneration(7L)
            assertTrue(
                buffer.submit(
                    generation = 7L,
                    sequence = 1L,
                    workClass = EvidenceWorkClass.STATIC_REFERENCE,
                ) {},
            )
            assertTrue(buffer.flush(1_000L))

            val metrics = buffer.metricsJson()
            assertEquals(
                "QUALITATIVE_ORDER_ONLY_NOT_CONFIDENCE_OR_PROBABILITY",
                metrics.getString("marginalInformationModel"),
            )
            val cost = metrics.getJSONObject("costByClass").getJSONObject("STATIC_REFERENCE")
            assertEquals("REUSABLE_REFERENCE", cost.getString("marginalInformationClass"))
            assertEquals(
                "QUALITATIVE_ORDER_NOT_CONFIDENCE_OR_PROBABILITY",
                cost.getString("informationInterpretation"),
            )
            assertEquals(1L, cost.getLong("observations"))
            assertTrue(cost.getDouble("avgQueueDelayMs") >= 0.0)
            assertTrue(cost.getDouble("avgProcessingMs") >= 0.0)
        } finally {
            buffer.close()
        }
    }
}
