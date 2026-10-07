package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Test

/** Um só limiar de condução (1200 rpm) para evidência, AutoIdle e StallWatch. */
class RegimeThresholdsTest {

    @Test
    fun `um so limiar de condução em todos os consumidores`() {
        assertEquals(1_200.0, RegimeThresholds.DRIVING_RPM, 0.0)
        assertEquals(RegimeThresholds.DRIVING_RPM, EquivalenceLedger.DRIVING_MIN_RPM, 0.0)
        assertEquals(RegimeThresholds.DRIVING_RPM, EvidencePairs.REGIME_SPLIT_RPM, 0.0)
        assertEquals(RegimeThresholds.DRIVING_RPM, StallWatch.DRIVING_RPM, 0.0)
        assertEquals(RegimeThresholds.DRIVING_RPM.toInt(), IdleAcquisitionTracker.IDLE_RPM)
    }
}
