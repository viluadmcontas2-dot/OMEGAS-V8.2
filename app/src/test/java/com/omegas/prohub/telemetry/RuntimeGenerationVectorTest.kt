package com.omegas.prohub.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeGenerationVectorTest {
    private fun base() = RuntimeGenerationVector(
        usbSessionId = 77L,
        calibrationFunctionFingerprint = "f".repeat(64),
        calibrationGeneration = 4,
        learningGeneration = 120L,
        predictorGeneration = 8L,
        uiRevision = 30L,
    )

    @Test
    fun `science input ignora revisions puramente derivadas de predictor e UI`() {
        val a = base()
        val b = base().copy(predictorGeneration = 9L, uiRevision = 99L)
        assertTrue(a.sameScientificInputs(b))
        assertFalse(a.samePresentationGeneration(b))
    }

    @Test
    fun `troca USB calibracao ou learning invalida science input`() {
        val a = base()
        assertFalse(a.sameScientificInputs(a.copy(usbSessionId = 78L)))
        assertFalse(a.sameScientificInputs(a.copy(calibrationFunctionFingerprint = "a".repeat(64))))
        assertFalse(a.sameScientificInputs(a.copy(calibrationGeneration = 5)))
        assertFalse(a.sameScientificInputs(a.copy(learningGeneration = 121L)))
    }

    @Test
    fun `valores conhecidos precisam ser monotonicos e fingerprints validos`() {
        RuntimeGenerationVector(1L, "a".repeat(64), 0, 0L, 0L, 0L)
        try {
            RuntimeGenerationVector(0L, "a".repeat(64), 0, 0L, 0L, 0L)
            throw AssertionError("usbSessionId zero deveria falhar")
        } catch (_: IllegalArgumentException) {}
    }
}
