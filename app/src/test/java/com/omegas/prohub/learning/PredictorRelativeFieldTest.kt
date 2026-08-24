package com.omegas.prohub.learning

import kotlin.math.abs
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorRelativeFieldTest {
    @Test
    fun `direct observation learns relative delta star not absolute K`() {
        val observation = support(
            id = "direct",
            rpm = 2400.0,
            petrolMs = 4.0,
            currentK = 120.0,
            kStar = 132.0,
            trajectoryId = "visit-a",
        )

        assertEquals(ln(132.0 / 120.0), observation.deltaStar, 1e-12)
    }

    @Test
    fun `inside physical hull predicts local positive correction from direct support`() {
        val prediction = PredictorRelativeField.predict(
            input(
                targetRpm = 2400.0,
                targetPetrolMs = 3.5,
                support = positiveTriangle(),
            ),
        )

        assertTrue(prediction.state == PredictorFieldState.PREDICTED_INTERPOLATED ||
            prediction.state == PredictorFieldState.PREDICTED_SHRUNK)
        assertTrue(requireNotNull(prediction.predictedDeltaStar) > 0.0)
        assertTrue(requireNotNull(prediction.targetK) > prediction.currentK)
        assertFalse(prediction.actionable)
        assertEquals(PredictorActionabilityState.ABSTAIN, prediction.actionabilityState)
        assertEquals("RISK_NOT_CALIBRATED", prediction.actionabilityReason)
        assertFalse(prediction.riskCalibrated)
        assertNull(prediction.pImprove)
    }

    @Test
    fun `more query uncertainty shrinks correction and widens interval`() {
        val low = PredictorRelativeField.predict(input(queryUncertaintyStd = 0.01, support = positiveTriangle()))
        val high = PredictorRelativeField.predict(input(queryUncertaintyStd = 0.20, support = positiveTriangle()))

        assertTrue(abs(requireNotNull(high.predictedDeltaStar)) < abs(requireNotNull(low.predictedDeltaStar)))
        assertTrue(width(high) > width(low))
    }

    @Test
    fun `shrink function tends toward zero as distance grows`() {
        val raw = 0.12
        val near = PredictorRelativeField.shrinkDelta(raw, distance = 0.0, uncertainty = 0.0)
        val mid = PredictorRelativeField.shrinkDelta(raw, distance = 2.0, uncertainty = 0.05)
        val far = PredictorRelativeField.shrinkDelta(raw, distance = 20.0, uncertainty = 0.05)
        val extreme = PredictorRelativeField.shrinkDelta(raw, distance = 10_000.0, uncertainty = 0.05)

        assertEquals(raw, near, 1e-12)
        assertTrue(abs(mid) < abs(near))
        assertTrue(abs(far) < abs(mid))
        assertTrue(abs(extreme) < 1e-4)
    }

    @Test
    fun `outside support hull abstains instead of free extrapolating`() {
        val prediction = PredictorRelativeField.predict(
            input(
                targetRpm = 6200.0,
                targetPetrolMs = 11.0,
                support = positiveTriangle(),
            ),
        )

        assertEquals(PredictorFieldState.UNKNOWN_ABSTAIN, prediction.state)
        assertEquals(PredictorActionabilityState.ABSTAIN, prediction.actionabilityState)
        assertNull(prediction.targetK)
        assertNull(prediction.predictedDeltaStar)
        assertFalse(prediction.actionable)
        assertTrue(prediction.nextEvidence.isNotBlank())
    }

    @Test
    fun `repeated same trajectory cannot manufacture independent spatial authority`() {
        val repeated = positiveTriangle().map { it.copy(trajectoryId = "same-visit") }
        val prediction = PredictorRelativeField.predict(input(support = repeated))

        assertEquals(PredictorFieldState.UNKNOWN_ABSTAIN, prediction.state)
        assertNull(prediction.targetK)
    }

    @Test
    fun `opposite distant signs do not trigger a global direction veto`() {
        val support = listOf(
            support("near-a", 2200.0, 3.0, 120.0, 132.0, "visit-a", quality = 0.98),
            support("near-b", 2600.0, 3.0, 120.0, 130.0, "visit-b", quality = 0.95),
            support("far-negative", 2400.0, 8.0, 120.0, 108.0, "visit-c", quality = 0.30),
        )
        val prediction = PredictorRelativeField.predict(
            input(targetRpm = 2400.0, targetPetrolMs = 3.4, support = support),
        )

        assertTrue(prediction.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertTrue(requireNotNull(prediction.predictedDeltaStar) > 0.0)
    }

    @Test
    fun `prediction type cannot become direct observation authority`() {
        assertFalse(PredictorRelativeObservation::class.java.isAssignableFrom(PredictorRelativePrediction::class.java))
        assertFalse(PredictorRelativePrediction::class.java.isAssignableFrom(PredictorRelativeObservation::class.java))
    }

    @Test
    fun `high diagnostic confidence remains abstained without risk calibration`() {
        val support = positiveTriangle().map { it.copy(quality = 1.0, uncertaintyStd = 0.0) }
        val prediction = PredictorRelativeField.predict(input(queryUncertaintyStd = 0.0, support = support))

        assertTrue(prediction.spatialConfidence > 0.0)
        assertFalse(prediction.riskCalibrated)
        assertNull(prediction.pImprove)
        assertFalse(prediction.actionable)
        assertEquals(PredictorActionabilityState.ABSTAIN, prediction.actionabilityState)
        assertEquals("RISK_NOT_CALIBRATED", prediction.actionabilityReason)
    }

    private fun width(prediction: PredictorRelativePrediction): Double =
        requireNotNull(prediction.upper95K) - requireNotNull(prediction.lower95K)

    private fun input(
        targetRpm: Double = 2400.0,
        targetPetrolMs: Double = 3.5,
        currentK: Double = 120.0,
        queryUncertaintyStd: Double = 0.03,
        support: List<PredictorRelativeObservation> = positiveTriangle(),
    ): PredictorRelativeFieldInput {
        val calibration = binding()
        return PredictorRelativeFieldInput(
            targetRpm = targetRpm,
            targetPetrolMs = targetPetrolMs,
            currentK = currentK,
            queryUncertaintyStd = queryUncertaintyStd,
            support = support,
            calibration = calibration,
            expectedGeometryFingerprint = calibration.geometryFingerprint,
        )
    }

    private fun positiveTriangle(): List<PredictorRelativeObservation> = listOf(
        support("a", 1400.0, 2.0, 120.0, 129.0, "visit-a"),
        support("b", 3400.0, 2.0, 120.0, 132.0, "visit-b"),
        support("c", 2400.0, 6.0, 120.0, 130.0, "visit-c"),
    )

    private fun support(
        id: String,
        rpm: Double,
        petrolMs: Double,
        currentK: Double,
        kStar: Double,
        trajectoryId: String,
        uncertaintyStd: Double = 0.02,
        quality: Double = 0.90,
    ) = PredictorRelativeObservation(
        id = id,
        rpm = rpm,
        petrolMs = petrolMs,
        currentK = currentK,
        kStar = kStar,
        uncertaintyStd = uncertaintyStd,
        quality = quality,
        trajectoryId = trajectoryId,
        provenance = "DIRECT_KSTAR_TEST",
        geometryFingerprint = "geometry-A",
    )

    private fun binding(): LearningCalibrationBinding = LearningCalibrationBinding(
        calibrationFingerprint = "calibration-A",
        calibrationGeneration = 7,
        geometryFingerprint = "geometry-A",
        usbSessionId = 21L,
        mapHash = "map-A",
        petrolAxisMs = listOf(2.0, 2.5, 3.0, 3.5, 4.5, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0),
        rpmAxis = listOf(850, 1350, 1850, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500),
    )
}
