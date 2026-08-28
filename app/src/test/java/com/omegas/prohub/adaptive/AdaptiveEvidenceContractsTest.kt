package com.omegas.prohub.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveEvidenceContractsTest {
    @Test
    fun `adaptive alias resolves to the telemetry canonical envelope`() {
        assertEquals("omegas-canonical-evidence-v1", CanonicalEvidence.SCHEMA)
        assertEquals("telemetry.CanonicalEvidence", CanonicalEvidenceContract.SOURCE_TYPE)
    }

    @Test
    fun `canonical evidence contract cannot own transport json science or writer`() {
        assertTrue(CanonicalEvidenceContract.SINGLE_PHYSICAL_ACQUISITION)
        assertFalse(CanonicalEvidenceContract.MAY_CREATE_SECOND_MP48_POLLING)
        assertFalse(CanonicalEvidenceContract.MAY_REPARSE_JSON_TO_FORM_SCIENCE)
        assertFalse(CanonicalEvidenceContract.MAY_WRITE_ECU)
    }
}
