package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorSampleAnalyzerTest {
    private val defaultFrames = LearningTolerancePolicy().requiredFrames

    @Test
    fun `default policy accepts six exceptional readings and keeps target ten`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(5) { assertFalse(analyzer.add(frame(it * 50L)).learningEligible) }
        val accepted = analyzer.add(frame(250L))
        assertTrue(accepted.learningEligible)
        assertEquals("SAMPLE_ACCEPTED_EARLY", accepted.reasonCode)
        assertEquals(6, accepted.minimumFrames)
        assertEquals(10, accepted.desiredFrames)
        assertEquals(6, accepted.sample?.frameCount)
    }

    @Test
    fun `fresh usb reset keeps fast path available`() {
        val analyzer = MotorSampleAnalyzer()
        analyzer.reset()
        repeat(5) { assertFalse(analyzer.add(frame(it * 50L)).learningEligible) }
        val accepted = analyzer.add(frame(250L))
        assertTrue(accepted.learningEligible)
        assertEquals("SAMPLE_ACCEPTED_EARLY", accepted.reasonCode)
        assertEquals(6, accepted.sample?.frameCount)
    }

    @Test
    fun `four clean readings publish visual micro candidate without learning evidence`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(3) { assertFalse(analyzer.add(frame(it * 50L)).learningEligible) }
        val preview = analyzer.add(frame(150L))
        assertFalse(preview.learningEligible)
        assertEquals("MICRO_CANDIDATE", preview.reasonCode)
        assertEquals(4, preview.frameCount)
    }

    @Test
    fun `planned operation discards the incomplete window and requires the full target`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(defaultFrames / 2) { analyzer.add(frame(it * 50L)) }
        analyzer.markPlannedOperation()
        repeat(defaultFrames - 1) { assertFalse(analyzer.add(frame(1_000L + it * 50L)).learningEligible) }
        val accepted = analyzer.add(frame(1_000L + (defaultFrames - 1) * 50L))
        assertTrue(accepted.learningEligible)
        assertEquals("SAMPLE_ACCEPTED", accepted.reasonCode)
        assertEquals(defaultFrames, accepted.sample?.frameCount)
    }

    @Test
    fun `isolated serial delay preserves readings but disables early acceptance`() {
        val analyzer = MotorSampleAnalyzer()
        var at = 0L
        var warning: SampleDecision? = null
        var accepted: SampleDecision? = null
        repeat(defaultFrames) { index ->
            if (index > 0) at += if (index == defaultFrames / 2) 350L else 50L
            val decision = analyzer.add(frame(at), toleratedGap = index == defaultFrames / 2)
            if (index == defaultFrames / 2) warning = decision
            accepted = decision
        }
        assertEquals("TOLERATED_TELEMETRY_DELAY", warning?.reasonCode)
        assertTrue(accepted!!.learningEligible)
        assertEquals("SAMPLE_ACCEPTED", accepted!!.reasonCode)
        assertEquals(1, accepted!!.toleratedGapCount)
    }

    @Test
    fun `real loss requires a complete new window`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(defaultFrames / 2) { analyzer.add(frame(it * 50L)) }
        analyzer.markContinuityLost()
        repeat(defaultFrames - 1) { assertFalse(analyzer.add(frame(1_000L + it * 50L)).learningEligible) }
        assertTrue(analyzer.add(frame(1_000L + (defaultFrames - 1) * 50L)).learningEligible)
    }

    @Test
    fun `petrol to cng discards confirmation window`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(6) { analyzer.add(frame(it * 50L, Mp48Fuel.PETROL)) }
        var decision: SampleDecision? = null
        repeat(defaultFrames) { decision = analyzer.add(frame(1_000L + it * 50L, Mp48Fuel.CNG)) }
        assertEquals("FUEL_STABLE", decision?.state)
        assertFalse(decision!!.learningEligible)
        repeat(defaultFrames - 1) { assertFalse(analyzer.add(frame(2_000L + it * 50L, Mp48Fuel.CNG)).learningEligible) }
        assertTrue(analyzer.add(frame(2_000L + (defaultFrames - 1) * 50L, Mp48Fuel.CNG)).learningEligible)
    }

    @Test
    fun `cng to petrol has the same symmetric protection`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(6) { analyzer.add(frame(it * 50L, Mp48Fuel.CNG)) }
        repeat(defaultFrames) { analyzer.add(frame(1_000L + it * 50L, Mp48Fuel.PETROL)) }
        repeat(defaultFrames - 1) { assertFalse(analyzer.add(frame(2_000L + it * 50L, Mp48Fuel.PETROL)).learningEligible) }
        assertTrue(analyzer.add(frame(2_000L + (defaultFrames - 1) * 50L, Mp48Fuel.PETROL)).learningEligible)
    }

    @Test
    fun `oscillating fuel never creates evidence`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(defaultFrames * 4) {
            val fuel = if (it % 2 == 0) Mp48Fuel.PETROL else Mp48Fuel.CNG
            assertFalse(analyzer.add(frame(it * 50L, fuel)).learningEligible)
        }
    }

    @Test
    fun `cutoff invalidates the previous window and requires the full target`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(defaultFrames - 2) { analyzer.add(frame(it * 50L)) }
        val cutoffAt = (defaultFrames - 2) * 50L
        val cutoff = analyzer.add(frame(cutoffAt, Mp48Fuel.CUTOFF, petrolMs = 0.0, gasRaw = 0, mapBar = 0.2))
        assertEquals("CUTOFF", cutoff.state)
        repeat(defaultFrames - 1) { assertFalse(analyzer.add(frame(cutoffAt + 100L + it * 50L)).learningEligible) }
        val accepted = analyzer.add(frame(cutoffAt + 100L + (defaultFrames - 1) * 50L))
        assertTrue(accepted.learningEligible)
        assertNotNull(accepted.sample)
    }

    @Test
    fun `unplanned gap above the configured continuity limit starts a new window`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(defaultFrames / 2) { analyzer.add(frame(it * 50L)) }
        val previousAt = (defaultFrames / 2 - 1) * 50L
        val decision = analyzer.add(frame(previousAt + LearningTolerancePolicy().breakingGapMs + 100L))
        assertEquals("TELEMETRY_GAP", decision.state)
        assertEquals("REAL_TELEMETRY_LOSS", decision.reasonCode)
        assertTrue(decision.continuityLost)
    }

    @Test
    fun `three isolated delays remain auditable and usable at the full target`() {
        val analyzer = MotorSampleAnalyzer()
        val delayed = setOf(2, 5, 8).filter { it < defaultFrames }.toSet()
        var at = 0L
        var finalDecision: SampleDecision? = null
        repeat(defaultFrames) { index ->
            if (index > 0) at += if (index in delayed) 260L else 50L
            finalDecision = analyzer.add(frame(at), toleratedGap = index in delayed)
            if (index < defaultFrames - 1) assertFalse(finalDecision!!.learningEligible)
        }
        assertTrue(finalDecision!!.learningEligible)
        assertEquals(delayed.size, finalDecision!!.toleratedGapCount)
    }

    @Test
    fun `planned gap argument starts a clean full window`() {
        val analyzer = MotorSampleAnalyzer()
        repeat(defaultFrames - 2) { analyzer.add(frame(it * 50L)) }
        val restarted = analyzer.add(frame(1_000L), plannedGap = true)
        assertEquals(1, restarted.frameCount)
        assertTrue(restarted.plannedOperation)
        repeat(defaultFrames - 2) { assertFalse(analyzer.add(frame(1_050L + it * 50L)).learningEligible) }
        assertTrue(analyzer.add(frame(1_050L + (defaultFrames - 2) * 50L)).learningEligible)
    }

    @Test
    fun `cold engine never creates learning evidence`() {
        val analyzer = MotorSampleAnalyzer()
        var decision: SampleDecision? = null
        repeat(defaultFrames) { decision = analyzer.add(frame(it * 50L, waterC = 40)) }
        assertEquals("ENGINE_WARMING", decision?.state)
        assertFalse(decision!!.learningEligible)
    }

    @Test
    fun `unstable rpm is rejected at the complete target`() {
        val analyzer = MotorSampleAnalyzer()
        var decision: SampleDecision? = null
        repeat(defaultFrames) { index ->
            decision = analyzer.add(frame(index * 50L, rpm = if (index < defaultFrames / 2) 1_600 else 3_600))
        }
        assertEquals("SAMPLE_REJECTED", decision?.state)
        assertFalse(decision!!.learningEligible)
    }

    @Test
    fun `every decision identifies its physical K cell`() {
        val decision = MotorSampleAnalyzer().add(frame(0L))
        assertEquals("3:3", decision.cellKey)
        assertEquals(3, decision.cellRow)
        assertEquals(3, decision.cellColumn)
    }

    @Test
    fun `minimum selectable window accepts six healthy readings`() {
        val analyzer = MotorSampleAnalyzer { LearningTolerancePolicy(requiredFrames = 6) }
        repeat(5) { assertFalse(analyzer.add(frame(it * 50L)).learningEligible) }
        val accepted = analyzer.add(frame(250L))
        assertTrue(accepted.learningEligible)
        assertEquals(6, accepted.minimumFrames)
        assertEquals(6, accepted.desiredFrames)
        assertEquals(6, accepted.sample?.frameCount)
    }

    @Test
    fun `target of eight can accept six exceptional readings`() {
        val analyzer = MotorSampleAnalyzer {
            LearningTolerancePolicy(requiredFrames = 8, maximumAttemptMs = 2_000L)
        }
        repeat(5) { assertFalse(analyzer.add(frame(it * 250L)).learningEligible) }
        val accepted = analyzer.add(frame(1_250L))
        assertTrue(accepted.learningEligible)
        assertEquals("SAMPLE_ACCEPTED_EARLY", accepted.reasonCode)
        assertEquals(6, accepted.minimumFrames)
        assertEquals(8, accepted.desiredFrames)
        assertTrue(accepted.windowBudgetMs >= 2_000L)
    }

    @Test
    fun `expired window is reported before reaching the adaptive minimum`() {
        val analyzer = MotorSampleAnalyzer {
            LearningTolerancePolicy(requiredFrames = 18, maximumAttemptMs = 1_000L)
        }
        repeat(6) { analyzer.add(frame(0L)) }
        val expired = analyzer.add(frame(1_100L), toleratedGap = true)
        assertEquals("WINDOW_TIMEOUT", expired.state)
        assertEquals("WINDOW_TIMEOUT", expired.reasonCode)
        assertEquals(1, expired.frameCount)
        assertEquals(6, expired.framesEvicted)
        assertEquals(1_100L, expired.windowAgeMs)
    }

    @Test
    fun `strict target of sixteen uses adaptive minimum of nine`() {
        val analyzer = MotorSampleAnalyzer {
            LearningTolerancePolicy(requiredFrames = 16, maximumAttemptMs = 2_000L)
        }
        repeat(8) { assertFalse(analyzer.add(frame(it * 50L)).learningEligible) }
        val accepted = analyzer.add(frame(400L))
        assertTrue(accepted.learningEligible)
        assertEquals(9, accepted.minimumFrames)
        assertEquals(16, accepted.desiredFrames)
    }

    @Test
    fun `changing tolerance policy restarts the physical window`() {
        var policy = LearningTolerancePolicy(requiredFrames = 12)
        val analyzer = MotorSampleAnalyzer { policy }
        repeat(6) { analyzer.add(frame(it * 50L)) }
        policy = policy.copy(requiredFrames = 6)
        val restarted = analyzer.add(frame(1_000L))
        assertFalse(restarted.learningEligible)
        assertEquals(1, restarted.frameCount)
        repeat(4) { assertFalse(analyzer.add(frame(1_050L + it * 50L)).learningEligible) }
        assertTrue(analyzer.add(frame(1_250L)).learningEligible)
    }

    @Test
    fun `strict and tolerant rpm policies produce opposite decisions`() {
        val strict = MotorSampleAnalyzer { LearningTolerancePolicy(requiredFrames = 6) }
        val tolerant = MotorSampleAnalyzer {
            LearningTolerancePolicy(
                requiredFrames = 6,
                rpmCenterMinimum = 250.0,
                rpmOscillationMinimum = 300.0,
            )
        }
        var strictDecision: SampleDecision? = null
        var tolerantDecision: SampleDecision? = null
        repeat(6) { index ->
            val telemetry = frame(index * 50L, rpm = if (index % 2 == 0) 2_400 else 2_600)
            strictDecision = strict.add(telemetry)
            tolerantDecision = tolerant.add(telemetry)
        }
        assertEquals("SAMPLE_REJECTED", strictDecision?.state)
        assertTrue(tolerantDecision!!.learningEligible)
    }

    private fun frame(
        at: Long,
        fuel: Mp48Fuel = Mp48Fuel.PETROL,
        petrolMs: Double = 4.0,
        gasRaw: Int = if (fuel == Mp48Fuel.CNG) 200 else 0,
        mapBar: Double = 0.60,
        rpm: Int = 2_500,
        waterC: Int = 80,
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
        waterRaw = 80,
        waterC = waterC,
        gasC = 30,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = mapBar,
        pressureDiffBar = 1.4,
        plausible = true,
    )
}
