package com.omegas.prohub.blue

import com.omegas.prohub.calibration.CalibrationShape
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

    @Test
    fun `zero measured deviation remains a comparison instead of disappearing`() {
        val revision = CalibrationRevision(0, 0)
        val petrol = evidence("petrol", FuelKind.PETROL, 4.0, revision = null)
        val cng = evidence("cng", FuelKind.CNG, 4.0, revision = revision)
        val state = BlueLearningState(
            sessionId = "session",
            calibration = calibration(revision),
            petrolEvidence = listOf(petrol),
            cngEvidenceByRevision = mapOf(revision to listOf(cng)),
        )
        val comparisons = engine.reconcile(state, nowMs = 2_000L)
        assertEquals(1, comparisons.size)
        assertEquals(0.0, comparisons.single().errorPercent, 1e-9)
        assertTrue(engine.isWithinActionDeadband(4.0, 4.0))
    }

    @Test
    fun `small measured deviation remains evidence while action stays in deadband`() {
        val revision = CalibrationRevision(0, 0)
        val petrol = evidence("petrol", FuelKind.PETROL, 4.0, revision = null)
        val cng = evidence("cng", FuelKind.CNG, 4.04, revision = revision)
        val state = BlueLearningState(
            sessionId = "session",
            calibration = calibration(revision),
            petrolEvidence = listOf(petrol),
            cngEvidenceByRevision = mapOf(revision to listOf(cng)),
        )
        val comparison = engine.reconcile(state, nowMs = 2_000L).single()
        assertTrue(comparison.errorPercent > 0.0)
        assertTrue(engine.isWithinActionDeadband(comparison.petrolOnCngMs, comparison.petrolTargetMs))
    }

    private fun evidence(
        id: String,
        fuel: FuelKind,
        petrolMs: Double,
        revision: CalibrationRevision?,
    ) = FuelEvidence(
        id = id,
        fuel = fuel,
        collectedAtMs = 1_000L,
        visitId = id,
        rpm = 1_500.0,
        mapBar = 0.50,
        petrolMs = petrolMs,
        quality = 0.95,
        cngRevision = revision,
    )

    private fun calibration(revision: CalibrationRevision) = CalibrationState(
        revision = revision,
        curveK = List(CalibrationShape.CURVE_K_POINTS) { 1.0 },
        mapK = List(CalibrationShape.MAP_K_STORAGE_ROWS) {
            List(CalibrationShape.MAP_K_COLUMNS) { 100 }
        },
    )
}