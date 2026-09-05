package com.omegas.prohub.blue

import com.omegas.v7.runtime.CalibrationRevisionV7
import com.omegas.v7.runtime.CalibrationShapeV7
import com.omegas.v7.runtime.CalibrationStateV7
import com.omegas.v7.runtime.EvidenceV7
import com.omegas.v7.runtime.FuelV7
import com.omegas.v7.runtime.V7SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class BlueCausalEngineTest {
    private val revision = CalibrationRevisionV7(2, 7)

    private fun calibration() = CalibrationStateV7(
        revision = revision,
        curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
        mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
            List(CalibrationShapeV7.MAP_K_COLUMNS) { 140 }
        },
    )

    private fun evidence(
        id: String,
        fuel: FuelV7,
        visit: String,
        rpm: Double,
        map: Double,
        petrolMs: Double,
        quality: Double = 0.95,
    ) = EvidenceV7(
        id = id,
        fuel = fuel,
        collectedAtMs = if (fuel == FuelV7.PETROL) 10 else 20,
        visitId = visit,
        rpm = rpm,
        mapBar = map,
        petrolMs = petrolMs,
        quality = quality,
        cngRevision = if (fuel == FuelV7.CNG) revision else null,
        waterC = 88.0,
    )

    @Test
    fun one_high_quality_petrol_microburst_is_enough_to_form_reference() {
        val engine = BlueCausalEngine()
        val cng = evidence("g", FuelV7.CNG, "g-visit", 1500.0, 0.50, 4.40)
        val petrol = evidence("p", FuelV7.PETROL, "p-visit", 1504.0, 0.503, 4.00)

        val reference = engine.petrolReference(cng, listOf(petrol))

        assertEquals(4.00, reference!!.petrolMs, 1e-9)
        assertEquals(1, reference.support)
        assertTrue(reference.quality > 0.8)
    }

    @Test
    fun far_condition_does_not_become_reference_even_with_same_injection_time() {
        val engine = BlueCausalEngine()
        val cng = evidence("g", FuelV7.CNG, "g-visit", 3000.0, 0.90, 4.00)
        val petrol = evidence("p", FuelV7.PETROL, "p-visit", 900.0, 0.20, 4.00)

        assertNull(engine.petrolReference(cng, listOf(petrol)))
    }

    @Test
    fun cng_error_uses_log_ratio_against_petrol_reference() {
        val engine = BlueCausalEngine()
        val observed = engine.cngErrorLog(petrolOnCngMs = 4.40, petrolReferenceMs = 4.00)

        assertEquals(ln(1.10), observed, 1e-12)
        assertEquals(10.0, engine.errorPercentFromLog(observed), 1e-9)
    }

    @Test
    fun actuator_gain_is_identified_from_real_before_after_response() {
        val engine = BlueCausalEngine()
        val beforeError = ln(1.10)
        val afterError = ln(1.04)
        val beforeK = 1.00
        val afterK = 1.06

        val gain = engine.actuatorGain(beforeError, afterError, beforeK, afterK)

        assertTrue(gain != null)
        assertTrue(gain!!.gain > 0.0)
        assertEquals(-(afterError - beforeError) / ln(afterK / beforeK), gain.gain, 1e-12)
    }

    @Test
    fun reconcile_never_pools_cng_from_another_calibration_state() {
        val engine = BlueCausalEngine()
        val petrol = evidence("p", FuelV7.PETROL, "p-visit", 1500.0, 0.50, 4.00)
        val activeCng = evidence("g", FuelV7.CNG, "g-active", 1500.0, 0.50, 4.40)
        val oldRevision = CalibrationRevisionV7(1, 6)
        val oldCng = activeCng.copy(id = "old", visitId = "g-old", cngRevision = oldRevision)
        val state = V7SessionState(
            sessionId = "blue-test",
            calibration = calibration(),
            petrolEvidence = listOf(petrol),
            cngEvidenceByRevision = mapOf(oldRevision to listOf(oldCng), revision to listOf(activeCng)),
        )

        val comparisons = engine.reconcile(state)

        assertEquals(1, comparisons.size)
        assertEquals(revision, comparisons.single().revision)
        assertEquals("g-active", comparisons.single().cngVisitId)
    }
}
