package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorSampleAnalyzerBoundaryTest {
    private val frames = LearningTolerancePolicy().requiredFrames

    @Test
    fun `one isolated cold reading does not contaminate an otherwise warm robust window`() {
        val analyzer = MotorSampleAnalyzer()
        var decision: SampleDecision? = null
        repeat(frames) { index ->
            decision = analyzer.add(frame(index * 50L, waterC = if (index == frames / 3) 20 else 80))
        }
        assertTrue(decision!!.learningEligible)
        assertEquals(80.0, decision!!.diagnostics!!.waterCenterC, 0.0001)
    }

    @Test
    fun `a majority of cold readings keeps the whole window outside absorption`() {
        val analyzer = MotorSampleAnalyzer()
        var decision: SampleDecision? = null
        val coldReadings = frames / 2 + 1
        repeat(frames) { index ->
            decision = analyzer.add(frame(index * 50L, waterC = if (index < coldReadings) 40 else 80))
        }
        assertEquals("ENGINE_WARMING", decision!!.state)
        assertFalse(decision!!.learningEligible)
        assertTrue(decision!!.diagnostics!!.waterCenterC < decision!!.diagnostics!!.minimumWaterC)
    }

    @Test
    fun `continuous map movement is rejected even with stable rpm`() {
        val analyzer = MotorSampleAnalyzer()
        var decision: SampleDecision? = null
        repeat(frames) { index ->
            decision = analyzer.add(frame(index * 50L, mapBar = if (index < frames / 2) 0.50 else 0.70))
        }
        assertEquals("SAMPLE_REJECTED", decision!!.state)
        assertTrue(decision!!.reason.contains("Carga"))
    }

    @Test
    fun `pressure instability blocks cng but does not invent a gasoline restriction`() {
        val cng = MotorSampleAnalyzer()
        val petrol = MotorSampleAnalyzer()
        var cngDecision: SampleDecision? = null
        var petrolDecision: SampleDecision? = null
        repeat(frames) { index ->
            val pressure = if (index % 2 == 0) 1.0 else 1.7
            cngDecision = cng.add(frame(index * 50L, fuel = Mp48Fuel.CNG, pressureDiffBar = pressure))
            petrolDecision = petrol.add(frame(index * 50L, fuel = Mp48Fuel.PETROL, pressureDiffBar = pressure))
        }
        assertEquals("SAMPLE_REJECTED", cngDecision!!.state)
        assertTrue(cngDecision!!.reason.contains("Pressão"))
        assertTrue(petrolDecision!!.learningEligible)
    }

    @Test
    fun `physical cutoff is recognized even before the fuel enum changes`() {
        val decision = MotorSampleAnalyzer().add(
            frame(
                at = 0L,
                fuel = Mp48Fuel.PETROL,
                rpm = 2_000,
                petrolMs = 0.50,
                gasRaw = 0,
                mapBar = 0.20,
            ),
        )
        assertEquals("CUTOFF", decision.state)
        assertFalse(decision.learningEligible)
    }

    @Test
    fun `low injection alone is not cutoff when manifold load remains high`() {
        val analyzer = MotorSampleAnalyzer()
        var decision: SampleDecision? = null
        repeat(frames) { index ->
            decision = analyzer.add(
                frame(
                    at = index * 50L,
                    fuel = Mp48Fuel.PETROL,
                    rpm = 2_000,
                    petrolMs = 0.50,
                    gasRaw = 0,
                    mapBar = 0.60,
                ),
            )
        }
        assertTrue(decision!!.learningEligible)
    }

    @Test
    fun `small isolated telemetry warnings remain auditable without changing the sample center`() {
        val analyzer = MotorSampleAnalyzer()
        val delayed = setOf(frames / 3, (frames * 2) / 3)
        var at = 0L
        var decision: SampleDecision? = null
        repeat(frames) { index ->
            if (index > 0) at += if (index in delayed) 220L else 50L
            decision = analyzer.add(
                frame(at = at, rpm = 2_500 + if (index % 2 == 0) 10 else -10),
                toleratedGap = index in delayed,
            )
        }
        assertTrue(decision!!.learningEligible)
        assertEquals(delayed.size, decision!!.toleratedGapCount)
        assertEquals(2_500.0, decision!!.sample!!.rpm, 0.0001)
    }

    @Test
    fun `engine off keeps the next window conservative`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(6) { index -> analyzer.add(frame(index * 50L)) }
        val off = analyzer.add(frame(500L, fuel = Mp48Fuel.ENGINE_OFF, rpm = 0, petrolMs = 0.0, mapBar = 0.0))
        assertEquals("ENGINE_OFF", off.state)
        assertFalse(off.learningEligible)

        repeat(frames - 1) { index ->
            assertFalse(analyzer.add(frame(1_000L + index * 50L)).learningEligible)
        }
        assertTrue(analyzer.add(frame(1_000L + (frames - 1) * 50L)).learningEligible)
    }

    private fun frame(
        at: Long,
        fuel: Mp48Fuel = Mp48Fuel.PETROL,
        petrolMs: Double = 4.0,
        gasRaw: Int = if (fuel == Mp48Fuel.CNG) 200 else 0,
        mapBar: Double = 0.60,
        rpm: Int = 2_500,
        waterC: Int = 80,
        pressureDiffBar: Double = 1.40,
    ) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = rpm,
        levelRaw = 100,
        gasRaw = gasRaw,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = fuel,
        state = fuel.wireName,
        waterRaw = waterC,
        waterC = waterC,
        gasC = 30,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = mapBar,
        pressureDiffBar = pressureDiffBar,
        plausible = true,
    )
}
