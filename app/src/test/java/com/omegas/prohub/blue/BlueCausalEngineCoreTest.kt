package com.omegas.prohub.blue

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueCausalEngineCoreTest {
    private val engine = BlueCausalEngine()

    @Test
    fun `log ratio error remains the primary physical error`() {
        assertEquals(ln(1.10), engine.cngErrorLog(4.40, 4.00), 1e-9)
        assertEquals(10.0, engine.errorPercentFromLog(ln(1.10)), 1e-9)
    }

    @Test
    fun `causal gain requires an observed before after K intervention`() {
        assertNull(engine.actuatorGain(0.10, 0.05, 1.0, 1.0))
        val gain = engine.actuatorGain(0.10, 0.05, 1.0, 1.05)
        assertNotNull(gain)
        assertTrue(gain!!.gain > 0.0)
    }

    @Test
    fun `correction is unavailable without causal gain and bounded with it`() {
        val error = ln(1.10)
        assertNull(engine.correctionMultiplier(error, null))
        val gain = engine.actuatorGain(0.10, 0.05, 1.0, 1.05)!!
        val multiplier = engine.correctionMultiplier(error, gain)!!
        assertTrue(multiplier in 0.80..1.20)
        assertTrue(multiplier > 1.0)
    }

    @Test
    fun `calibration state keeps curve and map revisions independent`() {
        val state = engine.calibrationState(CalibrationRevision(curveK = 7, mapK = 11))
        assertEquals(7L, state.curveK)
        assertEquals(11L, state.mapK)
    }
}
