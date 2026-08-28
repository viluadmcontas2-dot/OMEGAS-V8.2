package com.omegas.prohub.physics

import kotlin.math.exp
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationBoundRelativePriorTest {
    @Test
    fun `absolute historical target is converted to source-relative log correction`() {
        val prior = CalibrationBoundRelativePrior.fromAbsolute(
            sourceIdentity = identity("map-A", "curve-A"),
            dependencies = setOf(CalibrationDependency.MAP, CalibrationDependency.CURVE),
            sourceFactor = 1.05,
            targetFactor = 1.12,
            provenance = "post-write-outcome",
        )

        assertEquals(ln(1.12 / 1.05), prior.deltaStar, 1e-12)
        assertEquals(1.05, prior.sourceFactor, 1e-12)
    }

    @Test
    fun `compatible prior rebases relative correction onto current factor`() {
        val prior = prior(setOf(CalibrationDependency.MAP, CalibrationDependency.CURVE))
        val current = 1.20
        val result = prior.rebase(current, identity("map-A", "curve-A"))

        assertTrue(result.available)
        assertEquals(current * exp(prior.deltaStar), result.targetFactor!!, 1e-12)
        assertEquals("RELATIVE_PRIOR_REBASED", result.reason)
    }

    @Test
    fun `MAP-only dependency survives Curve change`() {
        val prior = prior(setOf(CalibrationDependency.MAP))
        val result = prior.rebase(1.20, identity("map-A", "curve-B"))

        assertTrue(result.available)
    }

    @Test
    fun `CURVE-only dependency survives Map change`() {
        val prior = prior(setOf(CalibrationDependency.CURVE))
        val result = prior.rebase(1.20, identity("map-B", "curve-A"))

        assertTrue(result.available)
    }

    @Test
    fun `declared Map dependency invalidates only when Map changes`() {
        val prior = prior(setOf(CalibrationDependency.MAP))
        val result = prior.rebase(1.20, identity("map-B", "curve-A"))

        assertFalse(result.available)
        assertEquals(null, result.targetFactor)
        assertEquals("MAP_DEPENDENCY_CHANGED", result.reason)
    }

    @Test
    fun `declared Curve dependency invalidates only when Curve changes`() {
        val prior = prior(setOf(CalibrationDependency.CURVE))
        val result = prior.rebase(1.20, identity("map-A", "curve-B"))

        assertFalse(result.available)
        assertEquals(null, result.targetFactor)
        assertEquals("CURVE_DEPENDENCY_CHANGED", result.reason)
    }

    @Test
    fun `prior depending on both invalidates on either calibration component`() {
        val prior = prior(setOf(CalibrationDependency.MAP, CalibrationDependency.CURVE))

        assertFalse(prior.rebase(1.20, identity("map-B", "curve-A")).available)
        assertFalse(prior.rebase(1.20, identity("map-A", "curve-B")).available)
    }

    @Test
    fun `invalid current factor fails closed instead of manufacturing a target`() {
        val result = prior(setOf(CalibrationDependency.MAP)).rebase(0.0, identity("map-A", "curve-A"))

        assertFalse(result.available)
        assertEquals(null, result.targetFactor)
        assertEquals("CURRENT_FACTOR_INVALID", result.reason)
    }

    private fun prior(dependencies: Set<CalibrationDependency>): CalibrationBoundRelativePrior =
        CalibrationBoundRelativePrior.fromAbsolute(
            sourceIdentity = identity("map-A", "curve-A"),
            dependencies = dependencies,
            sourceFactor = 1.05,
            targetFactor = 1.12,
            provenance = "step153-test",
        )

    private fun identity(mapHash: String, curveFingerprint: String) =
        CalibrationDependencyIdentity(mapHash, curveFingerprint)
}
