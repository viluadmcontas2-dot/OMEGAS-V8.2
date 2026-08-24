package com.omegas.prohub.learning

import com.omegas.prohub.physics.CorrectionMechanism
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorRelativeFieldGeometryTest {
    @Test
    fun `current petrol on gas and environment are preserved but cannot move Tpet reference projection`() {
        val calibration = binding()
        val lowContext = PredictorRelativeContext(
            petrolOnCngMs = 3.2,
            mapBar = 0.45,
            deltaPressureBar = 0.80,
            waterTemperatureC = 82.0,
            gasTemperatureC = 31.0,
        )
        val highContext = PredictorRelativeContext(
            petrolOnCngMs = 7.8,
            mapBar = 0.90,
            deltaPressureBar = 1.40,
            waterTemperatureC = 96.0,
            gasTemperatureC = 54.0,
        )
        val lowCurrent = PredictorRelativeField.predict(input(calibration = calibration, context = lowContext))
        val highCurrent = PredictorRelativeField.predict(input(calibration = calibration, context = highContext))

        assertTrue(lowCurrent.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertTrue(highCurrent.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertEquals(lowContext, lowCurrent.context)
        assertEquals(highContext, highCurrent.context)
        assertEquals(lowCurrent.geometryFingerprint, highCurrent.geometryFingerprint)
        assertEquals(lowCurrent.equilibriumCoordinate, highCurrent.equilibriumCoordinate)
        assertEquals(lowCurrent.projectionWeights, highCurrent.projectionWeights)
        assertEquals(lowCurrent.predictedDeltaStar!!, highCurrent.predictedDeltaStar!!, 1e-12)
    }

    @Test
    fun `changing petrol reference changes canonical time axis coordinate`() {
        val calibration = binding()
        val low = PredictorRelativeField.predict(input(calibration, targetPetrolReferenceMs = 3.1))
        val high = PredictorRelativeField.predict(input(calibration, targetPetrolReferenceMs = 6.7))

        assertTrue(low.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertTrue(high.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertTrue(low.projectionWeights != high.projectionWeights)
        assertEquals(3.1, low.equilibriumCoordinate!!.petrolReferenceMs, 1e-12)
        assertEquals(6.7, high.equilibriumCoordinate!!.petrolReferenceMs, 1e-12)
    }

    @Test
    fun `geometry mismatch abstains before spatial prediction`() {
        val calibration = binding()
        val prediction = PredictorRelativeField.predict(
            input(calibration).copy(expectedGeometryFingerprint = "geometry-B"),
        )

        assertEquals(PredictorFieldState.UNKNOWN_ABSTAIN, prediction.state)
        assertEquals("GEOMETRY_MISMATCH", prediction.spatialReason)
        assertNull(prediction.targetK)
        assertFalse(prediction.actionable)
    }

    @Test
    fun `unknown runtime geometry abstains without historical fixture fallback`() {
        val calibration = binding().copy(petrolAxisMs = emptyList(), rpmAxis = emptyList())
        val prediction = PredictorRelativeField.predict(input(calibration))

        assertEquals(PredictorFieldState.UNKNOWN_ABSTAIN, prediction.state)
        assertEquals("GEOMETRY_UNKNOWN", prediction.spatialReason)
        assertNull(prediction.targetK)
    }

    private fun input(
        calibration: LearningCalibrationBinding,
        targetPetrolReferenceMs: Double = 3.5,
        context: PredictorRelativeContext = PredictorRelativeContext(),
    ): PredictorRelativeFieldInput = PredictorRelativeFieldInput(
        targetRpm = 2400.0,
        targetPetrolMs = targetPetrolReferenceMs,
        currentK = 120.0,
        queryUncertaintyStd = 0.03,
        support = support(context),
        calibration = calibration,
        expectedGeometryFingerprint = calibration.geometryFingerprint,
        context = context,
        mechanism = CorrectionMechanism.MAP_LOCAL,
    )

    private fun support(context: PredictorRelativeContext): List<PredictorRelativeObservation> = listOf(
        observation("a", 1400.0, 2.0, 129.0, "visit-a", context),
        observation("b", 3400.0, 2.0, 132.0, "visit-b", context),
        observation("c", 2400.0, 6.0, 130.0, "visit-c", context),
    )

    private fun observation(
        id: String,
        rpm: Double,
        petrolReferenceMs: Double,
        kStar: Double,
        trajectoryId: String,
        context: PredictorRelativeContext,
    ): PredictorRelativeObservation = PredictorRelativeObservation(
        id = id,
        rpm = rpm,
        petrolMs = petrolReferenceMs,
        currentK = 120.0,
        kStar = kStar,
        uncertaintyStd = 0.02,
        quality = 0.90,
        trajectoryId = trajectoryId,
        provenance = "DIRECT_KSTAR_STEP152",
        geometryFingerprint = "geometry-A",
        context = context,
        mechanism = CorrectionMechanism.MAP_LOCAL,
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
