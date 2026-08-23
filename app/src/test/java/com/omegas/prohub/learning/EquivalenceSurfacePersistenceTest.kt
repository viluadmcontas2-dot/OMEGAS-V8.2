package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquivalenceSurfacePersistenceTest {
    private fun surface() = EquivalenceSurface(
        EquivalenceSurface.Config(
            minRpm = 0.0,
            maxRpm = 9000.0,
            rpmStep = 80.0,
            minMapBar = 0.0,
            maxMapBar = 2.5,
            mapStepBar = 0.02,
        ),
    )

    @Test
    fun sparse_snapshot_round_trips_without_fabricating_samples() {
        val original = surface()
        original.observe(FuelLane.PETROL_REFERENCE, 2500.0, 0.60, 3.00, 0.8, 11L)
        original.observe(FuelLane.CNG_PETROL_OBSERVED, 2500.0, 0.60, 3.30, 0.4, 12L)

        val snapshot = original.snapshot()
        assertTrue(snapshot.nodes.isNotEmpty())
        assertTrue(snapshot.nodes.size <= 8)

        val restored = surface()
        restored.restore(snapshot)
        val query = restored.query(2500.0, 0.60)

        assertEquals(original.debugTotalWeight(FuelLane.PETROL_REFERENCE), restored.debugTotalWeight(FuelLane.PETROL_REFERENCE), 1e-12)
        assertEquals(original.debugTotalWeight(FuelLane.CNG_PETROL_OBSERVED), restored.debugTotalWeight(FuelLane.CNG_PETROL_OBSERVED), 1e-12)
        assertEquals(3.00, query.petrol!!.meanTinjMs, 1e-9)
        assertEquals(3.30, query.cng!!.meanTinjMs, 1e-9)
    }

    @Test
    fun legacy_petrol_summary_preserves_mean_spread_and_explicit_provenance() {
        val s = surface()
        val seeded = s.seedPetrolSummary(
            rpm = 2500.0,
            mapBar = 0.60,
            meanTinjMs = 3.0,
            varianceMs2 = 0.04,
            seedWeight = 0.20,
            materialRevision = 7L,
        )

        assertTrue(seeded.touchedNodes in 1..4)
        val estimate = s.query(2500.0, 0.60).petrol!!
        assertEquals(3.0, estimate.meanTinjMs, 1e-9)
        assertEquals(0.04, estimate.varianceMs2, 1e-9)
        assertEquals(0.20, estimate.effectiveSupport, 1e-9)
        val snapshot = s.snapshot()
        assertEquals(1, snapshot.legacySeededRegions)
        assertEquals(LegacyPetrolSeedPolicy.PROVENANCE, snapshot.legacySeedProvenance)
    }

    @Test
    fun codec_round_trip_preserves_legacy_seed_marker_exactly_once() {
        val original = surface()
        original.seedPetrolSummary(
            rpm = 2500.0,
            mapBar = 0.60,
            meanTinjMs = 3.0,
            varianceMs2 = 0.04,
            seedWeight = 0.20,
            materialRevision = 7L,
        )

        val encoded = EquivalenceSurfaceCodec.encode(original.snapshot())
        assertTrue(encoded.contains("\"legacySeededRegions\":1"))
        assertTrue(encoded.contains("\"legacySeedProvenance\":\"${LegacyPetrolSeedPolicy.PROVENANCE}\""))

        val decoded = EquivalenceSurfaceCodec.decode(encoded)
        assertEquals(1, decoded.legacySeededRegions)
        assertEquals(LegacyPetrolSeedPolicy.PROVENANCE, decoded.legacySeedProvenance)

        val restored = surface()
        restored.restore(decoded)
        assertEquals(1, restored.snapshot().legacySeededRegions)
        assertEquals(LegacyPetrolSeedPolicy.PROVENANCE, restored.snapshot().legacySeedProvenance)
    }

    @Test
    fun clearing_cng_lane_preserves_petrol_reference() {
        val s = surface()
        s.observe(FuelLane.PETROL_REFERENCE, 2500.0, 0.60, 3.00, 1.0, 1L)
        s.observe(FuelLane.CNG_PETROL_OBSERVED, 2500.0, 0.60, 3.30, 1.0, 2L)

        s.clearLane(FuelLane.CNG_PETROL_OBSERVED)
        val query = s.query(2500.0, 0.60)

        assertEquals(3.00, query.petrol!!.meanTinjMs, 1e-9)
        assertNull(query.cng)
        assertEquals(0.0, s.debugTotalWeight(FuelLane.CNG_PETROL_OBSERVED), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun restore_rejects_incompatible_lattice_instead_of_guessing() {
        val original = surface()
        val snapshot = original.snapshot().copy(rpmStep = 100.0)
        surface().restore(snapshot)
    }
}
