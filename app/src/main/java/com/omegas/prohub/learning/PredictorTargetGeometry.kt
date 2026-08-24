package com.omegas.prohub.learning

data class PredictorEquilibriumCoordinate(
    val rpm: Double,
    val petrolReferenceMs: Double,
)

data class PredictorTargetCellWeight(
    val row: Int,
    val column: Int,
    val weight: Double,
)

data class PredictorGeometryProjection(
    val available: Boolean,
    val reason: String,
    val geometryFingerprint: String,
    val coordinate: PredictorEquilibriumCoordinate?,
    val weights: List<PredictorTargetCellWeight>,
)

/**
 * Pure Predictor-side projection onto the current Calibration Identity geometry.
 * The physical Map K coordinate is RPM x equilibrium petrol reference time.
 * Current petrol-on-GNV timing and environmental context are deliberately not
 * accepted as geometry inputs.
 */
object PredictorTargetGeometry {
    fun project(
        calibration: LearningCalibrationBinding,
        expectedGeometryFingerprint: String,
        rpm: Double,
        petrolReferenceMs: Double,
    ): PredictorGeometryProjection {
        if (!calibration.geometryKnown()) {
            return unavailable(calibration.geometryFingerprint, "GEOMETRY_UNKNOWN")
        }
        if (
            expectedGeometryFingerprint.isBlank() ||
            calibration.geometryFingerprint != expectedGeometryFingerprint
        ) {
            return unavailable(calibration.geometryFingerprint, "GEOMETRY_MISMATCH")
        }
        if (!axesStrictlyIncreasing(calibration)) {
            return unavailable(calibration.geometryFingerprint, "GEOMETRY_INVALID")
        }
        if (!rpm.isFinite() || rpm <= 0.0 || !petrolReferenceMs.isFinite() || petrolReferenceMs <= 0.0) {
            return unavailable(calibration.geometryFingerprint, "INVALID_TARGET_COORDINATE")
        }

        val coordinate = PredictorEquilibriumCoordinate(rpm, petrolReferenceMs)
        val weights = ContinuousLearningMath.bilinearWeights(
            rpm = rpm,
            petrolMs = petrolReferenceMs,
            rpmAxis = calibration.rpmAxis.map(Int::toDouble).toDoubleArray(),
            petrolAxisMs = calibration.petrolAxisMs.toDoubleArray(),
        ).map { contribution ->
            PredictorTargetCellWeight(
                row = contribution.row,
                column = contribution.column,
                weight = contribution.weight,
            )
        }
        if (weights.isEmpty()) {
            return unavailable(calibration.geometryFingerprint, "GEOMETRY_PROJECTION_EMPTY")
        }
        return PredictorGeometryProjection(
            available = true,
            reason = "RUNTIME_GEOMETRY_TPET_REF_RPM",
            geometryFingerprint = calibration.geometryFingerprint,
            coordinate = coordinate,
            weights = weights,
        )
    }

    private fun axesStrictlyIncreasing(calibration: LearningCalibrationBinding): Boolean =
        calibration.petrolAxisMs.zipWithNext().all { (left, right) -> right > left } &&
            calibration.rpmAxis.zipWithNext().all { (left, right) -> right > left }

    private fun unavailable(
        geometryFingerprint: String,
        reason: String,
    ): PredictorGeometryProjection = PredictorGeometryProjection(
        available = false,
        reason = reason,
        geometryFingerprint = geometryFingerprint,
        coordinate = null,
        weights = emptyList(),
    )
}
