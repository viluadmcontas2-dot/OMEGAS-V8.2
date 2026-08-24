package com.omegas.prohub.learning

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Scientific/diagnostic state of the typed relative correction field. */
enum class PredictorFieldState {
    DIRECT_CONFIRMED,
    DIRECT_PROVISIONAL,
    PREDICTED_INTERPOLATED,
    PREDICTED_SHRUNK,
    UNKNOWN_ABSTAIN,
}

/** Actionability is separate from the scientific prediction state. */
enum class PredictorActionabilityState {
    ABSTAIN,
    ACTIONABLE,
}

/** Context is preserved for conditioning/ablation, never used as the 2D Map K coordinate. */
data class PredictorRelativeContext(
    val petrolOnCngMs: Double? = null,
    val mapBar: Double? = null,
    val deltaPressureBar: Double? = null,
    val waterTemperatureC: Double? = null,
    val gasTemperatureC: Double? = null,
) {
    init {
        listOf(petrolOnCngMs, mapBar, deltaPressureBar, waterTemperatureC, gasTemperatureC)
            .filterNotNull()
            .forEach { require(it.isFinite()) }
    }
}

/**
 * Direct K* support only. `petrolMs` is the gasoline equilibrium reference time
 * (Tpet_ref) kept under its Step151 source-compatible name. Current petrol-on-GNV
 * timing lives only in [context]. Prediction is intentionally a different type.
 */
data class PredictorRelativeObservation(
    val id: String,
    val rpm: Double,
    val petrolMs: Double,
    val currentK: Double,
    val kStar: Double,
    val uncertaintyStd: Double,
    val quality: Double,
    val trajectoryId: String,
    val provenance: String,
    val geometryFingerprint: String = "",
    val context: PredictorRelativeContext = PredictorRelativeContext(),
) {
    init {
        require(id.isNotBlank())
        require(rpm.isFinite() && rpm > 0.0)
        require(petrolMs.isFinite() && petrolMs > 0.0)
        require(currentK.isFinite() && currentK > 0.0 && currentK <= 255.0)
        require(kStar.isFinite() && kStar > 0.0 && kStar <= 255.0)
        require(uncertaintyStd.isFinite() && uncertaintyStd >= 0.0)
        require(quality.isFinite() && quality in 0.0..1.0)
        require(trajectoryId.isNotBlank())
        require(provenance.isNotBlank())
    }

    val petrolReferenceMs: Double
        get() = petrolMs

    val deltaStar: Double
        get() = ln(kStar / currentK)
}

data class PredictorRelativeFieldInput(
    val targetRpm: Double,
    val targetPetrolMs: Double,
    val currentK: Double,
    val queryUncertaintyStd: Double,
    val support: List<PredictorRelativeObservation>,
    val calibration: LearningCalibrationBinding? = null,
    val expectedGeometryFingerprint: String = calibration?.geometryFingerprint.orEmpty(),
    val context: PredictorRelativeContext = PredictorRelativeContext(),
) {
    init {
        require(targetRpm.isFinite() && targetRpm > 0.0)
        require(targetPetrolMs.isFinite() && targetPetrolMs > 0.0)
        require(currentK.isFinite() && currentK > 0.0 && currentK <= 255.0)
        require(queryUncertaintyStd.isFinite() && queryUncertaintyStd >= 0.0)
    }

    val targetPetrolReferenceMs: Double
        get() = targetPetrolMs
}

data class PredictorRelativePrediction(
    val state: PredictorFieldState,
    val currentK: Double,
    val rawDeltaStar: Double?,
    val predictedDeltaStar: Double?,
    val targetK: Double?,
    val lower95K: Double?,
    val upper95K: Double?,
    val uncertaintyStd: Double?,
    val shrinkFactor: Double?,
    val spatialConfidence: Double,
    val supportIds: List<String>,
    val spatialReason: String,
    val geometryFingerprint: String?,
    val equilibriumCoordinate: PredictorEquilibriumCoordinate?,
    val projectionWeights: List<PredictorTargetCellWeight>,
    val riskCalibrated: Boolean,
    val pImprove: Double?,
    val actionable: Boolean,
    val actionabilityState: PredictorActionabilityState,
    val actionabilityReason: String,
    val nextEvidence: String,
)

/**
 * Typed spatial field for Steps 151–152. It predicts only the relative physical
 * correction delta*=ln(K_star / K_current), using Tpet_ref × RPM on the current
 * Calibration Identity geometry, then shrinks that correction toward zero as
 * physical distance or uncertainty rises. Context never becomes a third Map K axis.
 */
object PredictorRelativeField {
    private const val NORMAL_95 = 1.96

    fun predict(input: PredictorRelativeFieldInput): PredictorRelativePrediction {
        if (input.support.isEmpty()) return abstain(input, "NO_DIRECT_KSTAR_SUPPORT")
        val calibration = input.calibration ?: return abstain(input, "GEOMETRY_UNKNOWN")
        val projection = PredictorTargetGeometry.project(
            calibration = calibration,
            expectedGeometryFingerprint = input.expectedGeometryFingerprint,
            rpm = input.targetRpm,
            petrolReferenceMs = input.targetPetrolReferenceMs,
        )
        if (!projection.available) return abstain(input, projection.reason)
        if (input.support.any { it.geometryFingerprint.isBlank() }) {
            return abstain(input, "SUPPORT_GEOMETRY_UNKNOWN", projection = projection)
        }
        if (input.support.any { it.geometryFingerprint != input.expectedGeometryFingerprint }) {
            return abstain(input, "GEOMETRY_MISMATCH", projection = projection)
        }

        val rpmAxis = calibration.rpmAxis.map(Int::toDouble).toDoubleArray()
        val petrolReferenceAxis = calibration.petrolAxisMs.toDoubleArray()
        val spatial = PredictorSpatialConfidence.evaluateRelative(
            targetRpm = input.targetRpm,
            targetPetrolMs = input.targetPetrolReferenceMs,
            support = input.support.map { observation ->
                PredictorSpatialConfidence.RelativeSupportPoint(
                    id = observation.id,
                    rpm = observation.rpm,
                    petrolMs = observation.petrolReferenceMs,
                    deltaStar = observation.deltaStar,
                    quality = observation.quality,
                    trajectoryId = observation.trajectoryId,
                )
            },
            rpmAxis = rpmAxis,
            petrolReferenceAxisMs = petrolReferenceAxis,
        )
        if (!spatial.supported) return abstain(input, spatial.reason, spatial.confidence, projection)

        val contributions = input.support.map { observation ->
            val distance = PredictorSpatialConfidence.physicalDistance(
                input.targetRpm,
                input.targetPetrolReferenceMs,
                observation.rpm,
                observation.petrolReferenceMs,
                rpmAxis,
                petrolReferenceAxis,
            )
            DirectContribution(
                observation = observation,
                distance = distance,
                weight = observation.quality / (1.0 + distance),
            )
        }
        val trajectoryEstimates = contributions
            .groupBy { it.observation.trajectoryId }
            .mapNotNull { (trajectoryId, items) -> trajectoryEstimate(trajectoryId, items) }
        if (trajectoryEstimates.size < 2) {
            return abstain(input, "INSUFFICIENT_TRAJECTORY_INDEPENDENCE", spatial.confidence, projection)
        }

        val totalWeight = trajectoryEstimates.sumOf { it.weight }.coerceAtLeast(1e-12)
        val rawDelta = trajectoryEstimates.sumOf { it.deltaStar * it.weight } / totalWeight
        if (!rawDelta.isFinite()) return abstain(input, "INVALID_RELATIVE_FIELD", spatial.confidence, projection)

        val localVariance = trajectoryEstimates.sumOf { item ->
            val delta = item.deltaStar - rawDelta
            delta * delta * item.weight
        } / totalWeight
        val localStd = sqrt(localVariance.coerceAtLeast(0.0))
        val supportUncertainty = sqrt(
            trajectoryEstimates.sumOf { it.uncertaintyStd * it.uncertaintyStd * it.weight } / totalWeight,
        )
        val nearestDistance = contributions.minOf { it.distance }
        val shrinkUncertainty = input.queryUncertaintyStd + supportUncertainty
        val shrinkFactor = 1.0 / (1.0 + nearestDistance + shrinkUncertainty)
        val predictedDelta = shrinkDelta(rawDelta, nearestDistance, shrinkUncertainty)

        // Distance creates model uncertainty rather than extrapolation authority.
        val distanceThetaStd = nearestDistance * maxOf(abs(rawDelta), 0.01)
        val outputStd = sqrt(
            supportUncertainty * supportUncertainty +
                input.queryUncertaintyStd * input.queryUncertaintyStd +
                localStd * localStd +
                distanceThetaStd * distanceThetaStd,
        )
        val targetK = input.currentK * exp(predictedDelta)
        val lower = input.currentK * exp(predictedDelta - NORMAL_95 * outputStd)
        val upper = input.currentK * exp(predictedDelta + NORMAL_95 * outputStd)
        if (!targetK.isFinite() || !lower.isFinite() || !upper.isFinite()) {
            return abstain(input, "NON_FINITE_RELATIVE_PREDICTION", spatial.confidence, projection)
        }

        val state = if (abs(shrinkFactor - 1.0) <= 1e-12) {
            PredictorFieldState.PREDICTED_INTERPOLATED
        } else {
            PredictorFieldState.PREDICTED_SHRUNK
        }
        return PredictorRelativePrediction(
            state = state,
            currentK = input.currentK,
            rawDeltaStar = rawDelta,
            predictedDeltaStar = predictedDelta,
            targetK = targetK,
            lower95K = lower,
            upper95K = upper,
            uncertaintyStd = outputStd,
            shrinkFactor = shrinkFactor,
            spatialConfidence = spatial.confidence,
            supportIds = input.support.map { it.id }.distinct(),
            spatialReason = spatial.reason,
            geometryFingerprint = projection.geometryFingerprint,
            equilibriumCoordinate = projection.coordinate,
            projectionWeights = projection.weights,
            riskCalibrated = false,
            pImprove = null,
            actionable = false,
            actionabilityState = PredictorActionabilityState.ABSTAIN,
            actionabilityReason = "RISK_NOT_CALIBRATED",
            nextEvidence = "CALIBRATE_P_IMPROVE_WITH_POST_WRITE_OUTCOMES",
        )
    }

    /** Public for invariant/property testing; no hidden cell-count or direction threshold. */
    fun shrinkDelta(rawDeltaStar: Double, distance: Double, uncertainty: Double): Double {
        require(rawDeltaStar.isFinite())
        require(distance.isFinite() && distance >= 0.0)
        require(uncertainty.isFinite() && uncertainty >= 0.0)
        return rawDeltaStar / (1.0 + distance + uncertainty)
    }

    private fun trajectoryEstimate(
        trajectoryId: String,
        items: List<DirectContribution>,
    ): TrajectoryEstimate? {
        val usable = items.filter { it.weight > 0.0 && it.observation.deltaStar.isFinite() }
        if (usable.isEmpty()) return null
        val total = usable.sumOf { it.weight }.coerceAtLeast(1e-12)
        val delta = usable.sumOf { it.observation.deltaStar * it.weight } / total
        val uncertainty = sqrt(
            usable.sumOf { it.observation.uncertaintyStd * it.observation.uncertaintyStd * it.weight } / total,
        )
        // One trajectory contributes at most its strongest local evidence weight.
        val weight = usable.maxOf { it.weight }
        return TrajectoryEstimate(trajectoryId, delta, uncertainty, weight)
    }

    private fun abstain(
        input: PredictorRelativeFieldInput,
        reason: String,
        spatialConfidence: Double = 0.0,
        projection: PredictorGeometryProjection? = null,
    ): PredictorRelativePrediction = PredictorRelativePrediction(
        state = PredictorFieldState.UNKNOWN_ABSTAIN,
        currentK = input.currentK,
        rawDeltaStar = null,
        predictedDeltaStar = null,
        targetK = null,
        lower95K = null,
        upper95K = null,
        uncertaintyStd = null,
        shrinkFactor = null,
        spatialConfidence = spatialConfidence.coerceIn(0.0, 1.0),
        supportIds = input.support.map { it.id }.distinct(),
        spatialReason = reason,
        geometryFingerprint = projection?.geometryFingerprint ?: input.calibration?.geometryFingerprint,
        equilibriumCoordinate = projection?.coordinate,
        projectionWeights = projection?.weights.orEmpty(),
        riskCalibrated = false,
        pImprove = null,
        actionable = false,
        actionabilityState = PredictorActionabilityState.ABSTAIN,
        actionabilityReason = "SCIENTIFIC_SUPPORT_ABSTAIN:$reason",
        nextEvidence = "COLLECT_DIRECT_KSTAR_SUPPORT:$reason",
    )

    private data class DirectContribution(
        val observation: PredictorRelativeObservation,
        val distance: Double,
        val weight: Double,
    )

    private data class TrajectoryEstimate(
        val trajectoryId: String,
        val deltaStar: Double,
        val uncertaintyStd: Double,
        val weight: Double,
    )
}
