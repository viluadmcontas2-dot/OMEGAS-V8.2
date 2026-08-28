package com.omegas.prohub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceWorkClassTest {
    @Test
    fun `marginal information classes are explicit without pretending numeric science`() {
        assertEquals(
            MarginalInformationClass.DIAGNOSTIC_PRESENT_STATE,
            EvidenceWorkClass.DIAGNOSTIC_ONLY.marginalInformationClass,
        )
        assertEquals(
            MarginalInformationClass.CAUSAL_POST_INTERVENTION,
            EvidenceWorkClass.POST_WRITE_REVALIDATION.marginalInformationClass,
        )
        assertTrue(EvidenceWorkClass.DIAGNOSTIC_ONLY.diagnosticOnly)
        assertFalse(EvidenceWorkClass.FAST_KSTAR.diagnosticOnly)
    }

    @Test
    fun `backpressure never replaces higher value pending work with lower value work`() {
        assertFalse(
            EvidenceBackpressurePolicy.incomingMaySupersede(
                EvidenceWorkClass.DIAGNOSTIC_ONLY,
                EvidenceWorkClass.FAST_KSTAR,
            ),
        )
        assertFalse(
            EvidenceBackpressurePolicy.incomingMaySupersede(
                EvidenceWorkClass.DYNAMIC_COHERENT,
                EvidenceWorkClass.POST_WRITE_REVALIDATION,
            ),
        )
        assertTrue(
            EvidenceBackpressurePolicy.incomingMaySupersede(
                EvidenceWorkClass.POST_WRITE_REVALIDATION,
                EvidenceWorkClass.DYNAMIC_COHERENT,
            ),
        )
    }

    @Test
    fun `semantic value order is monotonic for overload only`() {
        val order = listOf(
            EvidenceWorkClass.DIAGNOSTIC_ONLY,
            EvidenceWorkClass.STATIC_REFERENCE,
            EvidenceWorkClass.DYNAMIC_COHERENT,
            EvidenceWorkClass.FAST_KSTAR,
            EvidenceWorkClass.POST_WRITE_REVALIDATION,
        )
        assertEquals(order.sortedBy { it.valueRank }, order)
    }
}
