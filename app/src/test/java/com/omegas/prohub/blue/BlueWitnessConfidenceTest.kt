package com.omegas.prohub.blue

import com.omegas.prohub.obd.ObdWitnessState
import org.junit.Assert.assertEquals
import org.junit.Test

class BlueWitnessConfidenceTest {
    @Test
    fun `supporting OBD accelerates confidence`() {
        val result = BlueWitnessConfidence.assess(
            blueErrorPercent = 8.0,
            baseQuality = 0.60,
            obdResidualPp = 7.0,
            obdQuality = 0.80,
        )

        assertEquals(ObdWitnessState.SUPPORTS, result.state)
        assertEquals(0.68, result.effectiveConfidence, 0.0001)
    }

    @Test
    fun `conflicting OBD never boosts confidence`() {
        val result = BlueWitnessConfidence.assess(
            blueErrorPercent = 8.0,
            baseQuality = 0.60,
            obdResidualPp = -7.0,
            obdQuality = 0.90,
        )

        assertEquals(ObdWitnessState.CONFLICTS, result.state)
        assertEquals(0.60, result.effectiveConfidence, 0.0001)
    }
}
