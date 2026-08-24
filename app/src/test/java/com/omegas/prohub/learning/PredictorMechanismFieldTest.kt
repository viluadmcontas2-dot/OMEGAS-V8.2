package com.omegas.prohub.learning

import com.omegas.prohub.physics.CorrectionMechanism
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorMechanismFieldTest {
    @Test
    fun `non actuator or unknown mechanism cannot manufacture an actuator target`() {
        listOf(
            CorrectionMechanism.UNKNOWN,
            CorrectionMechanism.ENVIRONMENTAL_DIAGNOSTIC,
            CorrectionMechanism.NO_ACTION,
        ).forEach { mechanism ->
            val prediction = PredictorRelativeField.predict(input(mechanism = mechanism, support = triangle(mechanism)))
            assertEquals(PredictorFieldState.UNKNOWN_ABSTAIN, prediction.state)
            assertNull(prediction.targetK)
            assertFalse(prediction.actionable)
        }
    }

    @Test
    fun `Map field consumes only Map mechanism support`() {
        val mixed = triangle(CorrectionMechanism.CURVE_MUL_ACT) +
            triangle(CorrectionMechanism.MAP_LOCAL).take(2)
        val prediction = PredictorRelativeField.predict(
            input(mechanism = CorrectionMechanism.MAP_LOCAL, support = mixed),
        )

        assertEquals(PredictorFieldState.UNKNOWN_ABSTAIN, prediction.state)
        assertEquals("MECHANISM_SUPPORT_INSUFFICIENT", prediction.spatialReason)
    }

    @Test
    fun `distant opposite clusters remain legitimate local shape with near zero middle`() {
        val support = symmetricClusters()
        val positive = PredictorRelativeField.predict(
            input(
                targetRpm = 1800.0,
                targetPetrolMs = 4.0,
                mechanism = CorrectionMechanism.MAP_LOCAL,
                support = support,
            ),
        )
        val negative = PredictorRelativeField.predict(
            input(
                targetRpm = 5000.0,
                targetPetrolMs = 4.0,
                mechanism = CorrectionMechanism.MAP_LOCAL,
                support = support,
            ),
        )
        val middle = PredictorRelativeField.predict(
            input(
                targetRpm = 3400.0,
                targetPetrolMs = 4.0,
                mechanism = CorrectionMechanism.MAP_LOCAL,
                support = support,
            ),
        )

        assertTrue(positive.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertTrue(negative.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertTrue(middle.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertTrue(positive.predictedDeltaStar!! > 0.0)
        assertTrue(negative.predictedDeltaStar!! < 0.0)
        assertTrue(abs(middle.predictedDeltaStar!!) < abs(positive.predictedDeltaStar!!))
        assertTrue(abs(middle.predictedDeltaStar!!) < abs(negative.predictedDeltaStar!!))
        assertFalse(positive.spatialReason.contains("DIRECTION_CONFLICT"))
        assertFalse(negative.spatialReason.contains("DIRECTION_CONFLICT"))
    }

    @Test
    fun `co local context comparable contradiction widens uncertainty and lowers confidence`() {
        val consistent = localConflictFixture(conflicting = false, comparability = 1.0)
        val conflicting = localConflictFixture(conflicting = true, comparability = 1.0)

        val consistentPrediction = PredictorRelativeField.predict(input(support = consistent))
        val conflictingPrediction = PredictorRelativeField.predict(input(support = conflicting))

        assertTrue(consistentPrediction.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertTrue(conflictingPrediction.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertEquals(0.0, consistentPrediction.localConflictScore, 1e-12)
        assertTrue(conflictingPrediction.localConflictScore > consistentPrediction.localConflictScore)
        assertTrue(conflictingPrediction.uncertaintyStd!! > consistentPrediction.uncertaintyStd!!)
        assertTrue(conflictingPrediction.spatialConfidence < conflictingPrediction.baseSpatialConfidence)
        assertTrue(conflictingPrediction.spatialConfidence < consistentPrediction.spatialConfidence)
    }

    @Test
    fun `unknown context cannot manufacture conflict authority`() {
        val conflictingComparable = PredictorRelativeField.predict(
            input(support = localConflictFixture(conflicting = true, comparability = 1.0)),
        )
        val conflictingUnknownContext = PredictorRelativeField.predict(
            input(support = localConflictFixture(conflicting = true, comparability = 0.0)),
        )

        assertTrue(conflictingComparable.localConflictScore > 0.0)
        assertEquals(0.0, conflictingUnknownContext.localConflictScore, 1e-12)
        assertTrue(conflictingUnknownContext.state != PredictorFieldState.UNKNOWN_ABSTAIN)
    }

    @Test
    fun `opposite support under another mechanism cannot contaminate local conflict`() {
        val mapSupport = triangle(CorrectionMechanism.MAP_LOCAL, delta = 0.08, comparability = 1.0)
        val curveOpposite = triangle(CorrectionMechanism.CURVE_MUL_ACT, delta = -0.08, comparability = 1.0)
        val prediction = PredictorRelativeField.predict(
            input(mechanism = CorrectionMechanism.MAP_LOCAL, support = mapSupport + curveOpposite),
        )

        assertTrue(prediction.state != PredictorFieldState.UNKNOWN_ABSTAIN)
        assertEquals(CorrectionMechanism.MAP_LOCAL, prediction.mechanism)
        assertEquals(0.0, prediction.localConflictScore, 1e-12)
        assertTrue(prediction.predictedDeltaStar!! > 0.0)
    }

    private fun localConflictFixture(
        conflicting: Boolean,
        comparability: Double,
    ): List<PredictorRelativeObservation> {
        val positive = 0.08
        return listOf(
            observation("a", 2200.0, 3.0, positive, "visit-a", CorrectionMechanism.MAP_LOCAL, comparability),
            observation("b", 2800.0, 3.0, if (conflicting) -positive else positive, "visit-b", CorrectionMechanism.MAP_LOCAL, comparability),
            observation("c", 2500.0, 5.5, positive, "visit-c", CorrectionMechanism.MAP_LOCAL, comparability),
            observation("d", 2500.0, 4.2, if (conflicting) -positive else positive, "visit-d", CorrectionMechanism.MAP_LOCAL, comparability),
        )
    }

    private fun symmetricClusters(): List<PredictorRelativeObservation> {
        val delta = ln(1.1)
        return listOf(
            observation("p1", 1400.0, 2.5, delta, "p-1", CorrectionMechanism.MAP_LOCAL, 1.0),
            observation("p2", 1800.0, 5.5, delta, "p-2", CorrectionMechanism.MAP_LOCAL, 1.0),
            observation("p3", 2200.0, 3.8, delta, "p-3", CorrectionMechanism.MAP_LOCAL, 1.0),
            observation("n1", 4600.0, 2.5, -delta, "n-1", CorrectionMechanism.MAP_LOCAL, 1.0),
            observation("n2", 5000.0, 5.5, -delta, "n-2", CorrectionMechanism.MAP_LOCAL, 1.0),
            observation("n3", 5400.0, 3.8, -delta, "n-3", CorrectionMechanism.MAP_LOCAL, 1.0),
        )
    }

    private fun triangle(
        mechanism: CorrectionMechanism,
        delta: Double = 0.08,
        comparability: Double = 1.0,
    ): List<PredictorRelativeObservation> = listOf(
        observation("a-${mechanism.name}", 1400.0, 2.0, delta, "visit-a-${mechanism.name}", mechanism, comparability),
        observation("b-${mechanism.name}", 3400.0, 2.0, delta, "visit-b-${mechanism.name}", mechanism, comparability),
        observation("c-${mechanism.name}", 2400.0, 6.0, delta, "visit-c-${mechanism.name}", mechanism, comparability),
    )

    private fun input(
        targetRpm: Double = 2400.0,
        targetPetrolMs: Double = 3.5,
        mechanism: CorrectionMechanism = CorrectionMechanism.MAP_LOCAL,
        support: List<PredictorRelativeObservation> = triangle(CorrectionMechanism.MAP_LOCAL),
    ): PredictorRelativeFieldInput {
        val calibration = binding()
        return PredictorRelativeFieldInput(
            targetRpm = targetRpm,
            targetPetrolMs = targetPetrolMs,
            currentK = 120.0,
            queryUncertaintyStd = 0.02,
            support = support,
            calibration = calibration,
            expectedGeometryFingerprint = calibration.geometryFingerprint,
            mechanism = mechanism,
        )
    }

    private fun observation(
        id: String,
        rpm: Double,
        petrolReferenceMs: Double,
        deltaStar: Double,
        trajectoryId: String,
        mechanism: CorrectionMechanism,
        comparability: Double,
    ): PredictorRelativeObservation = PredictorRelativeObservation(
        id = id,
        rpm = rpm,
        petrolMs = petrolReferenceMs,
        currentK = 120.0,
        kStar = 120.0 * exp(deltaStar),
        uncertaintyStd = 0.01,
        quality = 0.95,
        trajectoryId = trajectoryId,
        provenance = "STEP154_TEST",
        geometryFingerprint = "geometry-A",
        mechanism = mechanism,
        queryContextComparability = comparability,
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
