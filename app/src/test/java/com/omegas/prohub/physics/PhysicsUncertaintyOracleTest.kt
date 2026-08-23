package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsUncertaintyOracleTest {
    @Test
    fun `unknown gain abstains without numeric target`() {
        val result = PhysicsUncertaintyOracle.estimate(
            OracleRequest(
                petrolOnGasMs = 5.5,
                petrolReferenceMs = 5.0,
                currentFactor = 1.0,
                gain = PlantGain.unknown(),
                measurementStdRatio = 0.01,
                driftStdRatio = 0.0,
                draws = 400,
                seed = 7L,
            ),
        )
        assertTrue(result.abstained)
        assertNull(result.meanTargetFactor)
        assertEquals(MagnitudeAuthority.UNKNOWN, result.authority)
    }

    @Test
    fun `positive petrol residual asks for larger actuation when gain positive`() {
        val gain = PlantGain.empiricallyBounded(mean = 1.0, lower = 0.8, upper = 1.2)
        val result = PhysicsUncertaintyOracle.estimate(
            OracleRequest(5.5, 5.0, 1.0, gain, 0.005, 0.0, 1000, 11L),
        )
        assertFalse(result.abstained)
        assertTrue(requireNotNull(result.meanTargetFactor) > 1.0)
        assertTrue(requireNotNull(result.lower95) <= requireNotNull(result.meanTargetFactor))
        assertTrue(requireNotNull(result.upper95) >= requireNotNull(result.meanTargetFactor))
    }

    @Test
    fun `noise and drift widen interval deterministically`() {
        val gain = PlantGain.empiricallyBounded(1.0, 0.85, 1.15)
        val tight = PhysicsUncertaintyOracle.estimate(OracleRequest(5.4, 5.0, 1.0, gain, 0.002, 0.0, 1200, 42L))
        val wide = PhysicsUncertaintyOracle.estimate(OracleRequest(5.4, 5.0, 1.0, gain, 0.03, 0.02, 1200, 42L))
        val tightWidth = requireNotNull(tight.upper95) - requireNotNull(tight.lower95)
        val wideWidth = requireNotNull(wide.upper95) - requireNotNull(wide.lower95)
        assertTrue(wideWidth > tightWidth)
        assertEquals(wide, PhysicsUncertaintyOracle.estimate(OracleRequest(5.4, 5.0, 1.0, gain, 0.03, 0.02, 1200, 42L)))
    }

    @Test
    fun `analytic approximation stays inside oracle interval`() {
        val gain = PlantGain.empiricallyBounded(1.05, 0.9, 1.2)
        val oracle = PhysicsUncertaintyOracle.estimate(OracleRequest(5.3, 5.0, 1.08, gain, 0.01, 0.01, 1600, 9L))
        val analytic = KStarEstimator.estimate(typedInput(5.3, 5.0, 1.08, gain))
        val target = requireNotNull(analytic.targetFactor)
        assertTrue(target >= requireNotNull(oracle.lower95))
        assertTrue(target <= requireNotNull(oracle.upper95))
    }

    @Test
    fun `posterior gain update never fabricates gain without informative intervention`() {
        val prior = PlantGainPosterior.unknown()
        val unchanged = prior.update(
            beforeLogError = 0.10,
            afterLogError = 0.08,
            appliedLogFactorDelta = 0.0,
            observationVariance = 0.01,
        )
        assertEquals(MagnitudeAuthority.UNKNOWN, unchanged.toPlantGain().authority)

        val learned = PlantGainPosterior.prior(mean = 1.0, variance = 0.25).update(
            beforeLogError = 0.10,
            afterLogError = 0.02,
            appliedLogFactorDelta = 0.08,
            observationVariance = 0.01,
        )
        assertEquals(MagnitudeAuthority.EMPIRICALLY_BOUNDED, learned.toPlantGain().authority)
        assertTrue(requireNotNull(learned.mean) > 0.0)
    }

    private fun typedInput(
        petrolOnGasMs: Double,
        petrolReferenceMs: Double,
        currentFactor: Double,
        gain: PlantGain,
    ): KStarScientificInput {
        fun evidence(id: String): ResolvedScientificEvidence = ResolvedScientificEvidence(
            authorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
            role = ScientificEvidenceRole.OBSERVATION,
            evidenceIds = setOf(id),
            physicalEvidenceId = id,
            effectiveWeight = 1.0,
            provenance = setOf("physics-uncertainty-oracle-test"),
        )
        return KStarScientificInput(
            petrolOnGas = ScientificMeasurement(petrolOnGasMs, evidence("analytic-cng")),
            petrolReference = ScientificMeasurement(petrolReferenceMs, evidence("analytic-gas")),
            currentFactor = currentFactor,
            gain = gain,
        )
    }
}
