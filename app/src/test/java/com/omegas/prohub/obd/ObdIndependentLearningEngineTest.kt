package com.omegas.prohub.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdIndependentLearningEngineTest {
    @Test
    fun `five GNV STFT samples learn without MP48 data`() {
        val engine = ObdIndependentLearningEngine()
        repeat(5) { index ->
            assertTrue(engine.observe(sample(stft = 9.8 + index * 0.1, at = 1_000L + index)))
        }

        val result = engine.evaluate(rpm = 2_000.0, mapBar = 0.55, epoch = "obd-0")

        assertEquals(ObdLearningState.READY, result.state)
        assertEquals(10.0, result.stftMedianPct!!, 0.15)
        assertEquals(1.10, result.correctionMultiplier!!, 0.002)
        assertEquals(5, result.sampleCount)
        assertTrue(result.quality >= 0.55)
    }

    @Test
    fun `negative STFT reduces fuel and an outlier cannot dominate median`() {
        val engine = ObdIndependentLearningEngine()
        listOf(-10.0, -10.2, -9.8, -10.1, 40.0).forEachIndexed { index, stft ->
            assertTrue(engine.observe(sample(stft = stft, at = 2_000L + index)))
        }

        val result = engine.evaluate(2_000.0, 0.55, "obd-0")

        assertEquals(-10.0, result.stftMedianPct!!, 0.15)
        assertEquals(0.90, result.correctionMultiplier!!, 0.002)
    }

    @Test
    fun `distant region and another epoch do not contaminate result`() {
        val engine = ObdIndependentLearningEngine()
        repeat(5) { engine.observe(sample(stft = 8.0, at = 3_000L + it)) }
        repeat(5) { engine.observe(sample(stft = -20.0, rpm = 4_000.0, map = 1.10, at = 4_000L + it)) }
        engine.beginEpoch("obd-1")
        repeat(5) { engine.observe(sample(stft = -6.0, at = 5_000L + it, epoch = "obd-1")) }

        val current = engine.evaluate(2_000.0, 0.55, "obd-1")
        val old = engine.evaluate(2_000.0, 0.55, "obd-0")

        assertEquals(-6.0, current.stftMedianPct!!, 0.01)
        assertEquals(ObdLearningState.INSUFFICIENT, old.state)
    }

    @Test
    fun `snapshot restores current OBD evidence independently`() {
        val source = ObdIndependentLearningEngine()
        repeat(5) { source.observe(sample(stft = 5.0, at = 6_000L + it)) }

        val restored = ObdIndependentLearningEngine()
        restored.restoreJson(source.snapshotJson())
        val result = restored.evaluate(2_000.0, 0.55, "obd-0")

        assertEquals(ObdLearningState.READY, result.state)
        assertEquals(5, result.sampleCount)
    }

    @Test
    fun `invalid or non GNV declared samples fail closed`() {
        val engine = ObdIndependentLearningEngine()

        assertFalse(engine.observe(sample(stft = 8.0, gnvMode = false)))
        assertFalse(engine.observe(sample(stft = 60.0)))
        assertFalse(engine.observe(sample(stft = 8.0, span = 751L)))
        assertEquals(ObdLearningState.UNAVAILABLE, engine.evaluate(2_000.0, 0.55, "obd-0").state)
    }

    private fun sample(
        stft: Double,
        rpm: Double = 2_000.0,
        map: Double = 0.55,
        at: Long = 1_000L,
        epoch: String = "obd-0",
        span: Long = 120L,
        gnvMode: Boolean = true,
    ) = ObdLearningSample(
        observedAtMs = at,
        rpm = rpm,
        mapBar = map,
        stftPct = stft,
        acquisitionSpanMs = span,
        epoch = epoch,
        gnvModeDeclared = gnvMode,
    )
}
