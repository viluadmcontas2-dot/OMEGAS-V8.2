package com.omegas.prohub.physics

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KStarObservationCalibrationTest {
    @Test
    fun `zero error keeps current factor and zero physical delta`() {
        val estimate = estimate(petrolOnGasMs = 4.0, petrolReferenceMs = 4.0)
        val result = calibrate(estimate = estimate, frames = 6)

        assertFalse(estimate.abstained)
        assertEquals(1.0, estimate.targetFactor!!, 1e-12)
        assertEquals(0.0, estimate.logError, 1e-12)
        assertEquals(KStarDirectStage.DIRECT_CONFIRMED, result.stage)
        assertEquals(1.0, result.uncertainty.meanTargetFactor!!, 1e-12)
    }

    @Test
    fun `positive and negative observation errors preserve physical sign`() {
        val positive = estimate(4.4, 4.0)
        val negative = estimate(3.6, 4.0)

        assertTrue(positive.logError > 0.0)
        assertTrue(positive.targetFactor!! > 1.0)
        assertTrue(negative.logError < 0.0)
        assertTrue(negative.targetFactor!! < 1.0)
    }

    @Test
    fun `uncertainty grows with reference gain context model and contradiction uncertainty`() {
        val estimate = estimate(4.4, 4.0)
        val baseline = calibrate(estimate = estimate, frames = 6)
        val uncertainReference = calibrate(
            estimate = estimate,
            frames = 6,
            uncertainty = uncertainty().copy(petrolReferenceRelativeStd = 0.06),
        )
        val uncertainGain = calibrate(
            estimate = estimate(4.4, 4.0, gain = PlantGain.empiricallyBounded(1.0, 0.6, 1.4)),
            frames = 6,
        )
        val uncertainContext = calibrate(
            estimate = estimate,
            frames = 6,
            uncertainty = uncertainty().copy(contextThetaStd = 0.08, modelThetaStd = 0.05),
        )
        val contradiction = calibrate(
            estimate = estimate,
            frames = 6,
            uncertainty = uncertainty().copy(contradictionThetaStd = 0.10),
        )

        val baseWidth = width(baseline)
        assertTrue(width(uncertainReference) > baseWidth)
        assertTrue(width(uncertainGain) > baseWidth)
        assertTrue(width(uncertainContext) > baseWidth)
        assertTrue(width(contradiction) > baseWidth)
    }

    @Test
    fun `strong reference transitions from observed to provisional to confirmed at four and six frames`() {
        assertEquals(KStarDirectStage.OBSERVED, calibrate(estimate(), frames = 3).stage)
        assertEquals(KStarDirectStage.DIRECT_PROVISIONAL, calibrate(estimate(), frames = 4).stage)
        assertEquals(KStarDirectStage.DIRECT_PROVISIONAL, calibrate(estimate(), frames = 5).stage)
        assertEquals(KStarDirectStage.DIRECT_CONFIRMED, calibrate(estimate(), frames = 6).stage)
    }

    @Test
    fun `ambiguous or weak reference never becomes direct authority merely by collecting eight frames`() {
        val ambiguous = calibrate(estimate(), frames = 8, strongReference = true, ambiguityResolved = false)
        val weak = calibrate(estimate(), frames = 12, strongReference = false, ambiguityResolved = true)

        assertEquals(KStarDirectStage.FALLBACK_COLLECTION, ambiguous.stage)
        assertEquals(KStarDirectStage.FALLBACK_COLLECTION, weak.stage)
        assertFalse(ambiguous.directAuthority)
        assertFalse(weak.directAuthority)
    }

    @Test
    fun `confidence components remain explicit and consistent evidence is monotonic`() {
        val lower = KStarConfidenceComponents(
            reference = 0.90,
            observation = 0.90,
            effectiveSamples = 0.35,
            independentVisits = 0.40,
            geometricLocality = 0.85,
            contextMatch = 0.80,
            modelFit = 0.75,
            calibrationFreshness = 1.0,
        )
        val higher = lower.copy(effectiveSamples = 0.75, independentVisits = 0.80)
        val first = KStarObservationCalibration.confidence(lower)
        val second = KStarObservationCalibration.confidence(higher)

        assertEquals(lower, first.components)
        assertEquals(higher, second.components)
        assertTrue(second.score >= first.score)
    }

    @Test
    fun `gain prior near one is not empirical authority without an informative intervention`() {
        val priorOnly = PlantGainPosterior.prior(mean = 1.0, variance = 0.25).toPlantGain()
        val learned = PlantGainPosterior.prior(mean = 1.0, variance = 0.25)
            .update(
                beforeLogError = 0.10,
                afterLogError = 0.02,
                appliedLogFactorDelta = 0.08,
                observationVariance = 0.01,
            )
            .toPlantGain()

        assertEquals(MagnitudeAuthority.UNKNOWN, priorOnly.authority)
        assertEquals(MagnitudeAuthority.EMPIRICALLY_BOUNDED, learned.authority)
    }

    @Test
    fun `abstained K star remains abstained in calibration layer`() {
        val estimate = estimate(gain = PlantGain.unknown())
        val result = calibrate(estimate, frames = 20)

        assertTrue(estimate.abstained)
        assertEquals(KStarDirectStage.ABSTAIN, result.stage)
        assertFalse(result.directAuthority)
        assertTrue(result.uncertainty.abstained)
    }

    @Test
    fun `analytic target equation remains exactly theta plus e over g`() {
        val gain = PlantGain.empiricallyBounded(1.2, 1.0, 1.4)
        val estimate = estimate(4.5, 4.0, currentFactor = 1.08, gain = gain)
        val expected = ln(1.08) + ln(4.5 / 4.0) / 1.2

        assertEquals(expected, estimate.targetTheta!!, 1e-12)
    }

    private fun calibrate(
        estimate: KStarEstimate,
        frames: Int,
        strongReference: Boolean = true,
        ambiguityResolved: Boolean = true,
        uncertainty: KStarUncertaintyComponents = uncertainty(),
    ): KStarCalibratedObservation = KStarObservationCalibration.evaluate(
        estimate = estimate,
        frameCount = frames,
        strongReference = strongReference,
        ambiguityResolved = ambiguityResolved,
        uncertainty = uncertainty,
        confidenceComponents = KStarConfidenceComponents(
            reference = 0.9,
            observation = 0.9,
            effectiveSamples = 0.8,
            independentVisits = 0.8,
            geometricLocality = 0.85,
            contextMatch = 0.85,
            modelFit = 0.8,
            calibrationFreshness = 1.0,
        ),
    )

    private fun width(result: KStarCalibratedObservation): Double =
        result.uncertainty.upper95!! - result.uncertainty.lower95!!

    private fun uncertainty() = KStarUncertaintyComponents(
        petrolOnGasRelativeStd = 0.02,
        petrolReferenceRelativeStd = 0.02,
        currentThetaStd = 0.005,
        contextThetaStd = 0.01,
        modelThetaStd = 0.01,
        contradictionThetaStd = 0.0,
    )

    private fun estimate(
        petrolOnGasMs: Double = 4.4,
        petrolReferenceMs: Double = 4.0,
        currentFactor: Double = 1.0,
        gain: PlantGain = PlantGain.empiricallyBounded(1.0, 0.9, 1.1),
    ): KStarEstimate = KStarEstimator.estimate(
        KStarScientificInput(
            petrolOnGas = ScientificMeasurement(petrolOnGasMs, evidence("cng")),
            petrolReference = ScientificMeasurement(petrolReferenceMs, evidence("gas")),
            currentFactor = currentFactor,
            gain = gain,
        ),
    )

    private fun evidence(id: String): ResolvedScientificEvidence = ResolvedScientificEvidence(
        authorities = setOf(ScientificAuthority.CLASSIC_ASSISTED),
        role = ScientificEvidenceRole.OBSERVATION,
        evidenceIds = setOf("$id-evidence"),
        physicalEvidenceId = "$id-frame",
        effectiveWeight = 1.0,
        provenance = setOf("step150-test"),
    )
}
