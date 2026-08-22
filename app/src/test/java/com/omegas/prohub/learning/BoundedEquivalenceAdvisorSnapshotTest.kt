package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedEquivalenceAdvisorSnapshotTest {
    @Test
    fun advisor_input_contains_only_bounded_surface_pairs_and_no_environment_gate() {
        val surface = EquivalenceSurface(
            EquivalenceSurface.Config(
                minRpm = 1_000.0,
                maxRpm = 4_000.0,
                rpmStep = 80.0,
                minMapBar = 0.20,
                maxMapBar = 1.20,
                mapStepBar = 0.02,
            ),
        )
        surface.observe(FuelLane.PETROL_REFERENCE, 2_500.0, 0.60, 3.00, 0.8, 11L)
        surface.observe(FuelLane.CNG_PETROL_OBSERVED, 2_500.0, 0.60, 3.30, 0.6, 12L)

        val snapshot = BoundedEquivalenceAdvisorSnapshot.build(surface.snapshot(), epoch = 7)
        val comparisons = snapshot.getJSONArray("comparisons")

        assertEquals(0, snapshot.getJSONArray("regions").length())
        assertTrue(comparisons.length() in 1..16)
        repeat(comparisons.length()) { index ->
            val row = comparisons.getJSONObject(index)
            assertEquals("BOUNDED_EQUIVALENCE_SURFACE", row.getString("origin"))
            assertEquals(7, row.getInt("epoch"))
            assertEquals(3.00, row.getDouble("petrol_target_ms"), 1e-9)
            assertEquals(3.30, row.getDouble("petrol_on_cng_ms"), 1e-9)
            assertEquals(10.0, row.getDouble("error_pct"), 1e-9)
            assertTrue(row.getDouble("quality") > 0.0)
            assertFalse(row.has("water_c"))
            assertFalse(row.has("gas_c"))
            assertFalse(row.has("pressure_diff_bar"))
        }
        assertEquals("RPM_MAP_PETROL_TINJ", snapshot.getString("primaryAuthority"))
        assertFalse(snapshot.getBoolean("environmentGates"))
    }

    @Test
    fun nearby_non_overlapping_nodes_remain_comparable_for_advisor() {
        val surface = EquivalenceSurface(
            EquivalenceSurface.Config(
                minRpm = 1_000.0,
                maxRpm = 4_000.0,
                rpmStep = 80.0,
                minMapBar = 0.20,
                maxMapBar = 1.20,
                mapStepBar = 0.02,
            ),
        )
        surface.observe(FuelLane.PETROL_REFERENCE, 2_360.0, 0.60, 3.00, 1.0, 21L)
        surface.observe(FuelLane.CNG_PETROL_OBSERVED, 2_440.0, 0.60, 3.30, 1.0, 22L)

        val snapshot = BoundedEquivalenceAdvisorSnapshot.build(surface.snapshot(), epoch = 8)
        val comparisons = snapshot.getJSONArray("comparisons")

        assertTrue("Nearby valid evidence must not disappear at the Advisor boundary", comparisons.length() > 0)
        repeat(comparisons.length()) { index ->
            val row = comparisons.getJSONObject(index)
            assertEquals(3.00, row.getDouble("petrol_target_ms"), 1e-9)
            assertEquals(3.30, row.getDouble("petrol_on_cng_ms"), 1e-9)
        }
    }

    @Test
    fun advisor_projection_never_inflates_tiny_upstream_scientific_mass() {
        val surface = EquivalenceSurface(
            EquivalenceSurface.Config(
                minRpm = 1_000.0,
                maxRpm = 4_000.0,
                rpmStep = 80.0,
                minMapBar = 0.20,
                maxMapBar = 1.20,
                mapStepBar = 0.02,
            ),
        )
        surface.observe(FuelLane.PETROL_REFERENCE, 2_400.0, 0.60, 3.00, 0.005, 31L)
        surface.observe(FuelLane.CNG_PETROL_OBSERVED, 2_400.0, 0.60, 3.30, 0.004, 32L)

        val comparisons = BoundedEquivalenceAdvisorSnapshot.build(surface.snapshot(), epoch = 9)
            .getJSONArray("comparisons")
        assertTrue(comparisons.length() > 0)
        repeat(comparisons.length()) { index ->
            val row = comparisons.getJSONObject(index)
            val paired = row.getDouble("paired_scientific_weight")
            val projectedQuality = row.getDouble("quality")
            assertTrue("Downstream quality cannot exceed paired upstream scientific mass", projectedQuality <= paired + 1e-12)
        }
    }

    @Test
    fun overlapping_local_projection_conserves_total_scientific_authority() {
        val surface = EquivalenceSurface(
            EquivalenceSurface.Config(
                minRpm = 1_000.0,
                maxRpm = 4_000.0,
                rpmStep = 80.0,
                minMapBar = 0.20,
                maxMapBar = 1.20,
                mapStepBar = 0.02,
            ),
        )
        surface.observe(FuelLane.CNG_PETROL_OBSERVED, 2_400.0, 0.60, 3.30, 0.20, 40L)
        listOf(
            2_320.0 to 0.58, 2_320.0 to 0.60, 2_320.0 to 0.62,
            2_400.0 to 0.58, 2_400.0 to 0.60, 2_400.0 to 0.62,
            2_480.0 to 0.58, 2_480.0 to 0.60, 2_480.0 to 0.62,
        ).forEachIndexed { index, (rpm, map) ->
            surface.observe(FuelLane.PETROL_REFERENCE, rpm, map, 3.00, 0.20, 41L + index)
        }

        val projected = BoundedEquivalenceAdvisorSnapshot.build(surface.snapshot(), epoch = 10)
        val comparisons = projected.getJSONArray("comparisons")
        val authorityBudget = minOf(projected.getDouble("petrolWeight"), projected.getDouble("cngWeight"))
        var pairedTotal = 0.0
        var qualityTotal = 0.0
        repeat(comparisons.length()) { index ->
            val row = comparisons.getJSONObject(index)
            pairedTotal += row.getDouble("paired_scientific_weight")
            qualityTotal += row.getDouble("quality")
        }

        assertTrue("Overlapping projection cannot duplicate paired physical authority", pairedTotal <= authorityBudget + 1e-12)
        assertTrue("Advisor quality cannot exceed the available cross-fuel authority budget", qualityTotal <= authorityBudget + 1e-12)
    }

    @Test
    fun unpaired_lane_never_fabricates_comparison() {
        val surface = EquivalenceSurface(EquivalenceSurface.Config.mp48ReplayCandidate())
        surface.observe(FuelLane.CNG_PETROL_OBSERVED, 2_500.0, 0.60, 3.30, 0.6, 12L)

        val snapshot = BoundedEquivalenceAdvisorSnapshot.build(surface.snapshot(), epoch = 1)

        assertEquals(0, snapshot.getJSONArray("comparisons").length())
        assertTrue(snapshot.getDouble("cngWeight") > 0.0)
        assertEquals(0.0, snapshot.getDouble("petrolWeight"), 0.0)
    }
}
