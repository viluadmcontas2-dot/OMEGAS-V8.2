package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Um só limiar de condução (1200 rpm) para a EVIDÊNCIA de equivalência e para o AutoIdle. O StallWatch tem limiar
 * PRÓPRIO (1000 rpm): apagão que parte de 1000–1199 rpm é real e não pode sumir do diagnóstico.
 */
class RegimeThresholdsTest {

    @Test
    fun `um so limiar de condução para evidencia e AutoIdle`() {
        assertEquals(1_200.0, RegimeThresholds.DRIVING_RPM, 0.0)
        assertEquals(RegimeThresholds.DRIVING_RPM, EquivalenceLedger.DRIVING_MIN_RPM, 0.0)
        assertEquals(RegimeThresholds.DRIVING_RPM, EvidencePairs.REGIME_SPLIT_RPM, 0.0)
        assertEquals(RegimeThresholds.DRIVING_RPM.toInt(), IdleAcquisitionTracker.IDLE_RPM)
    }

    @Test
    fun `engasgo mantem limiar proprio abaixo do de evidencia`() {
        assertEquals(1_000.0, StallWatch.DRIVING_RPM, 0.0)
    }
}
