package com.omegas.prohub.physics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KStarMonteCarloOracleTest {
    @Test
    fun `analytic log uncertainty reproduces seeded canonical Monte Carlo interval`() {
        val gain = PlantGain.empiricallyBounded(1.0, 0.9, 1.1)
        val estimate = KStarEstimate(
            logError = kotlin.math.ln(4.4 / 4.0),
            currentTheta = 0.0,
            targetTheta = kotlin.math.ln(4.4 / 4.0),
            targetFactor = 1.1,
            gain = gain,
            authority = MagnitudeAuthority.EMPIRICALLY_BOUNDED,
            abstained = false,
            reason = "fixture",
            scientificTrace = KStarScientificTrace(
                petrolOnGasAuthorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
                petrolReferenceAuthorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
                petrolOnGasEvidenceIds = setOf("cng"),
                petrolReferenceEvidenceIds = setOf("gas"),
                petrolOnGasPhysicalEvidenceIds = setOf("cng-frame"),
                petrolReferencePhysicalEvidenceIds = setOf("gas-frame"),
                provenance = setOf("step150-oracle"),
            ),
        )
        val uncertainty = KStarUncertaintyComponents(
            petrolOnGasRelativeStd = 0.02,
            petrolReferenceRelativeStd = 0.02,
            currentThetaStd = 0.005,
            contextThetaStd = 0.01,
            modelThetaStd = 0.01,
            contradictionThetaStd = 0.0,
        )
        val analytic = KStarObservationCalibration.propagate(estimate, uncertainty)
        val oracle = PhysicsUncertaintyOracle.propagate(
            KStarPropagationOracleRequest(
                petrolOnGasMs = 4.4,
                petrolReferenceMs = 4.0,
                currentFactor = 1.0,
                gain = gain,
                uncertainty = uncertainty,
                draws = 30_000,
                seed = 150L,
            ),
        )

        assertFalse(analytic.abstained)
        assertFalse(oracle.abstained)
        assertTrue(relativeError(analytic.lower95!!, oracle.lower95!!) < 0.03)
        assertTrue(relativeError(analytic.upper95!!, oracle.upper95!!) < 0.03)
        assertTrue(relativeError(analytic.meanTargetFactor!!, oracle.meanTargetFactor!!) < 0.02)
    }

    private fun relativeError(a: Double, b: Double): Double = kotlin.math.abs(a - b) / kotlin.math.max(kotlin.math.abs(b), 1e-12)
}
