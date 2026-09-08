package com.omegas.prohub.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRelevancePolicyTest {
    @Test
    fun `tiny reconnect probe is not a useful retained session`() {
        assertEquals(
            SessionRelevance.PROBE,
            SessionRelevancePolicy.classify(19, 4_999, protectedEvidence = false),
        )
    }

    @Test
    fun `useful telemetry session becomes valid at the documented boundary`() {
        assertEquals(
            SessionRelevance.VALID,
            SessionRelevancePolicy.classify(20, 5_000, protectedEvidence = false),
        )
    }

    @Test
    fun `confirmed or explicit protection always wins over size`() {
        assertEquals(SessionRelevance.PROTECTED, SessionRelevancePolicy.classify(0, 0, true))
        assertEquals(SessionRelevance.PROTECTED, SessionRelevancePolicy.classify(0, 0, false, true))
    }
}
