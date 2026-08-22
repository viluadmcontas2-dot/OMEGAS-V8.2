package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquivalenceRuntimeTest {
    private fun runtime(): EquivalenceRuntime = EquivalenceRuntime(
        surface = EquivalenceSurface(
            EquivalenceSurface.Config(
                minRpm = 0.0,
                maxRpm = 9000.0,
                rpmStep = 80.0,
                minMapBar = 0.0,
                maxMapBar = 2.5,
                mapStepBar = 0.02,
            ),
        ),
        deadbandFraction = 0.02,
        empiricalSingleObservationNoiseFraction = 0.019,
    )

    @Test
    fun cng_weight_is_stability_times_novelty() {
        val result = runtime().observe(
            lane = FuelLane.CNG_PETROL_OBSERVED,
            rpm = 2500.0,
            mapBar = 0.50,
            petrolTinjMs = 3.30,
            stability = 0.50,
            novelty = 0.40,
            materialRevision = 1L,
        )
        assertEquals(0.20, result.scientificWeight, 1e-12)
    }

    @Test
    fun petrol_weight_is_more_conservative_than_cng() {
        val result = runtime().observe(
            lane = FuelLane.PETROL_REFERENCE,
            rpm = 2500.0,
            mapBar = 0.50,
            petrolTinjMs = 3.00,
            stability = 0.50,
            novelty = 0.40,
            materialRevision = 1L,
        )
        assertEquals(0.10, result.scientificWeight, 1e-12)
    }

    @Test
    fun duplicate_window_adds_no_new_scientific_weight() {
        val r = runtime()
        val before = r.totalWeight(FuelLane.CNG_PETROL_OBSERVED)
        val result = r.observe(
            lane = FuelLane.CNG_PETROL_OBSERVED,
            rpm = 2500.0,
            mapBar = 0.50,
            petrolTinjMs = 3.30,
            stability = 0.70,
            novelty = 0.0,
            materialRevision = 1L,
        )
        assertEquals(0.0, result.scientificWeight, 0.0)
        assertEquals(before, r.totalWeight(FuelLane.CNG_PETROL_OBSERVED), 0.0)
    }

    @Test
    fun zero_weight_observation_cannot_open_phantom_visit_revision() {
        val r = runtime()
        val rejected = r.observe(
            lane = FuelLane.CNG_PETROL_OBSERVED,
            rpm = 2500.0,
            mapBar = 0.50,
            petrolTinjMs = 3.30,
            stability = 0.0,
            novelty = 1.0,
            materialRevision = 100L,
        )
        assertEquals(0.0, rejected.scientificWeight, 0.0)

        val accepted = r.observe(
            lane = FuelLane.CNG_PETROL_OBSERVED,
            rpm = 2500.0,
            mapBar = 0.50,
            petrolTinjMs = 3.30,
            stability = 1.0,
            novelty = 0.25,
            materialRevision = 200L,
        )
        assertTrue(accepted.scientificWeight > 0.0)
        assertEquals(
            "A zero-weight window must not become the visit identity for later evidence",
            200L,
            r.query(2500.0, 0.50).cng!!.materialRevision,
        )
    }

    @Test
    fun cng_evidence_is_retained_before_petrol_and_becomes_comparable_later() {
        val r = runtime()
        r.observe(FuelLane.CNG_PETROL_OBSERVED, 2500.0, 0.50, 3.30, 1.0, 1.0, 1L)
        assertTrue(r.query(2500.0, 0.50).cng != null)
        assertTrue(r.query(2500.0, 0.50).petrol == null)

        r.observe(FuelLane.PETROL_REFERENCE, 2500.0, 0.50, 3.00, 1.0, 1.0, 2L)
        val estimate = r.estimate(2500.0, 0.50)
        assertNotNull(estimate)
        assertEquals(0.30, estimate!!.deltaMs, 1e-9)
        assertEquals(0.10, estimate.errorFraction, 1e-9)
        assertTrue(estimate.actionable)
    }

    @Test
    fun empirical_noise_prevents_single_observation_false_precision() {
        val r = runtime()
        r.observe(FuelLane.CNG_PETROL_OBSERVED, 2500.0, 0.50, 3.03, 1.0, 1.0, 1L)
        r.observe(FuelLane.PETROL_REFERENCE, 2500.0, 0.50, 3.00, 1.0, 1.0, 2L)
        val estimate = r.estimate(2500.0, 0.50)!!
        assertTrue(estimate.uncertaintyFraction > 0.0)
        assertFalse(estimate.actionable)
    }

    @Test
    fun state_size_is_independent_of_observation_count() {
        val r = runtime()
        val before = r.allocatedScalarCount()
        repeat(10_000) { i ->
            r.observe(
                FuelLane.CNG_PETROL_OBSERVED,
                1000.0 + (i % 4000),
                0.20 + (i % 100) * 0.005,
                2.0 + (i % 50) * 0.01,
                0.5,
                0.2,
                i.toLong(),
            )
        }
        assertEquals(before, r.allocatedScalarCount())
    }
}
