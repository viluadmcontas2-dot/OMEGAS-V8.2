package com.omegas.prohub.util

import com.omegas.prohub.ecu.Mp48Fuel
import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceWorkClassifierTest {
    @Test
    fun `057a maps eligible scientific work without boolean important adapter`() {
        assertEquals(
            EvidenceWorkClass.STATIC_REFERENCE,
            EvidenceWorkClassifier.classify(Mp48Fuel.PETROL, true, true, "SAMPLE_ACCEPTED", false),
        )
        assertEquals(
            EvidenceWorkClass.DYNAMIC_COHERENT,
            EvidenceWorkClassifier.classify(Mp48Fuel.CNG, true, true, "SAMPLE_ACCEPTED", false),
        )
        assertEquals(
            EvidenceWorkClass.FAST_KSTAR,
            EvidenceWorkClassifier.classify(Mp48Fuel.CNG, true, true, "FAST_KSTAR_READY", false),
        )
        assertEquals(
            EvidenceWorkClass.POST_WRITE_REVALIDATION,
            EvidenceWorkClassifier.classify(Mp48Fuel.CNG, true, true, "SAMPLE_ACCEPTED", true),
        )
        assertEquals(
            EvidenceWorkClass.DIAGNOSTIC_ONLY,
            EvidenceWorkClassifier.classify(Mp48Fuel.CNG, false, false, "FORMING_SAMPLE", false),
        )
        assertEquals(
            EvidenceWorkClass.DIAGNOSTIC_ONLY,
            EvidenceWorkClassifier.classify(Mp48Fuel.TRANSITION, true, true, "TRANSITION", false),
        )
    }
}
