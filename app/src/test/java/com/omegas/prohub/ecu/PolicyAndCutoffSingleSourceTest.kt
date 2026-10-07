package com.omegas.prohub.ecu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auditoria USB→tela: a política de aprendizado só carrega campos que algum código lê, e o corte (cut-off) tem uma
 * única fonte ([PhysicalCutoff]) usada pelo decodificador MP48 e pelo analisador de amostras.
 */
class PolicyAndCutoffSingleSourceTest {
    private val deadFields = listOf(
        "evaluationStride", "physicalExitFrames", "historicalRpmMinimum", "historicalRpmPercent", "historicalMapBar",
        "historicalTemperatureC", "referenceMaximumSpreadMs", "directionConsensusMinimum", "comparisonMaximumMadMs",
        "comparisonMaximumGasTempSpanC", "comparisonMaximumPressureSpanBar", "equivalenceDeadbandMs",
        "equivalenceDeadbandPercent", "confidenceSampleTarget", "provisionalVisits", "acceptedVisits", "confirmedVisits",
        // cut-off: fonte única em PhysicalCutoff, não mais configurável na política
        "cutoffMinimumRpm", "cutoffMaximumPetrolMs", "cutoffMaximumMapBar",
    )

    @Test
    fun `politica nao expoe campos sem efeito nem o cutoff duplicado`() {
        val keys = LearningTolerancePolicy().toJson().keys().asSequence().toSet()
        deadFields.forEach { assertFalse("$it ainda na política", it in keys) }
        listOf("requiredFrames", "maximumAttemptMs", "warningGapMs", "breakingGapMs", "toleratedSerialFailures",
            "hardRecoveryFailures", "hardRecoverySilenceMs", "rpmCenterMinimum", "mapOscillationBar",
            "petrolOscillationPercent", "strongPetrolOscillationPercent").forEach { assertTrue("$it sumiu", it in keys) }
    }

    @Test
    fun `cutoff tem uma fonte so e as fronteiras valem`() {
        assertEquals(1_200, PhysicalCutoff.MIN_RPM)
        assertEquals(0.70, PhysicalCutoff.MAX_PETROL_MS, 0.0)
        assertEquals(0.35, PhysicalCutoff.MAX_MAP_BAR, 0.0)
        assertTrue(PhysicalCutoff.isCutoff(1_200, 0.69, 0, 0.34))
        assertFalse(PhysicalCutoff.isCutoff(1_199, 0.69, 0, 0.34))
        assertFalse(PhysicalCutoff.isCutoff(1_200, 0.70, 0, 0.34))
        assertFalse(PhysicalCutoff.isCutoff(1_200, 0.69, 1, 0.34))
        assertFalse(PhysicalCutoff.isCutoff(1_200, 0.69, 0, 0.35))
    }
}
