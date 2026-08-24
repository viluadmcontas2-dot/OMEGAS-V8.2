package com.omegas.prohub.learning

import com.omegas.prohub.physics.CorrectionMechanism
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
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
 * Direct K* support only. `petrolMs` is Tpet_ref. Physics supplies the mechanism;
 * `queryContextComparability` is target-snapshot metadata for conflict authority,
 * not a learned global constant. Unknown context contributes zero conflict weight.
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
    val mechanism: CorrectionMechanism = CorrectionMechanism.UNKNOWN,
    val queryContextComparability: Double = 0.0,
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
        require(queryContextComparability.isFinite() && queryContextComparability in 0.0..1.0)
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
    val mechanism: CorrectionMechanism = CorrectionMechanism.UNKNOWN,
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
    val baseSpatialConfidence: Double,
    val spatialConfidence: Double,
    val localConflictScore: Double,
    val supportIds: List<String>,
    val spatialReason: String,
    val mechanism: CorrectionMechanism,
    val geometryFingerprint: String?,
    val equilibriumCoordinate: PredictorEquilibriumCoordinate?,
    val projectionWeights: List<PredictorTargetCellWeight>,
    val context: PredictorRelativeContext,
    val riskCalibrated: Boolean,
    val pImprove: Double?,
    val actionable: Boolean,
    val actionabilityState: PredictorActionabilityState,
    val actionabilityReason: String,
    val nextEvidence: String,
)

/**
 * Typed spatial field for Steps 151–154. Physics decides mechanism upstream.
 * Predictor estimates only the same-mechanism relative correction field on the
 * current Tpet_ref × RPM geometry. Local/context-comparable contradiction widens
 * variance and lowers confidence continuously; distant opposite signs are not vetoed.
 */
object PredictorRelativeField {
    private const val NORMAL_95 = 1.96

    fun predict(input: PredictorRelativeFieldInput): PredictorRelativePrediction {
        if (!isActuatorCorrection(input.mechanism)) {
            return abstain(input, mechanismAbstentionReason(input.mechanism))
        }
        val mechanismSupport = input.support.filter { it.mechanism == input.mechanism }
        if (mechanismSupport.size < 3) return abstain(input, "MECHANISM_SUPPORT_INSUFFICIENT")

        val calibration = input.calibration ?: return abstain(input, "GEOMETRY_UNKNOWN")
        val projection = PredictorTargetGeometry.project(
            calibration = calibration,
            expectedGeometryFingerprint = input.expectedGeometryFingerprint,
            rpm = input.targetRpm,
            petrolReferenceMs = input.targetPetrolReferenceMs,
        )
        if (!projection.available) return abstain(input, projection.reason)
        if (mechanismSupport.any { it.geometryFingerprint.isBlank() }) {
            return abstain(input, "SUPPORT_GEOMETRY_UNKNOWN", projection = projection)
        }
        if (mechanismSupport.any { it.geometryFingerprint != input.expectedGeometryFingerprint }) {
            return abstain(input, "GEOMETRY_MISMATCH", projection = projection)
        }

        val rpmAxis = calibration.rpmAxis.map(Int::toDouble).toDoubleArray()
        val petrolReferenceAxis = calibration.petrolAxisMs.toDoubleArray()
        val spatial = PredictorSpatialConfidence.evaluateRelative(
            targetRpm = input.targetRpm,
            targetPetrolMs = input.targetPetrolReferenceMs,
            support = mechanismSupport.map { observation ->
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
        if (!spatial.supported) {
            return abstain(input, spatial.reason, spatial.confidence, projection, mechanismSupport)
        }

        val contributions = mechanismSupport.map { observation ->
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
            return abstain(
                input,
                "INSUFFICIENT_TRAJECTORY_INDEPENDENCE",
                spatial.confidence,
                projection,
                mechanismSupport,
            )
        }

        val totalWeight = trajectoryEstimates.sumOf { it.weight }.coerceAtLeast(1e-12)
        val rawDelta = trajectoryEstimates.sumOf { it.deltaStar * it.weight } / totalWeight
        if (!rawDelta.isFinite()) {
            return abstain(input, "INVALID_RELATIVE_FIELD", spatial.confidence, projection, mechanismSupport)
        }

        val localVariance = trajectoryEstimates.sumOf { item ->
            val delta = item.deltaStar - rawDelta
            delta * delta * item.weight
        } / totalWeight
        val localStd = sqrt(localVariance.coerceAtLeast(0.0))
        val supportUncertainty = sqrt(
            trajectoryEstimates.sumOf { it.uncertaintyStd * it.uncertaintyStd * it.weight } / totalWeight,
        )
        val conflictScore = localConflictScore(trajectoryEstimates)
        val conflictThetaStd = conflictScore * localStd

        val nearestDistance = contributions.minOf { it.distance }
        val shrinkUncertainty = input.queryUncertaintyStd + supportUncertainty
        val shrinkFactor = 1.0 / (1.0 + nearestDistance + shrinkUncertainty)
        val predictedDelta = shrinkDelta(rawDelta, nearestDistance, shrinkUncertainty)

        val distanceThetaStd = nearestDistance * maxOf(abs(rawDelta), 0.01)
        val outputStd = sqrt(
            supportUncertainty * supportUncertainty +
                input.queryUncertaintyStd * input.queryUncertaintyStd +
                localStd * localStd +
                distanceThetaStd * distanceThetaStd +
                conflictThetaStd * conflictThetaStd,
        )
        val targetK = input.currentK * exp(predictedDelta)
        val lower = input.currentK * exp(predictedDelta - NORMAL_95 * outputStd)
        val upper = input.currentK * exp(predictedDelta + NORMAL_95 * outputStd)
        if (!targetK.isFinite() || !lower.isFinite() || !upper.isFinite()) {
            return abstain(
                input,
                "NON_FINITE_RELATIVE_PREDICTION",
                spatial.confidence,
                projection,
                mechanismSupport,
            )
        }

        val state = if (abs(shrinkFactor - 1.0) <= 1e-12) {
            PredictorFieldState.PREDICTED_INTERPOLATED
        } else {
            PredictorFieldState.PREDICTED_SHRUNK
        }
        val baseConfidence = spatial.confidence.coerceIn(0.0, 1.0)
        val conflictAdjustedConfidence = (baseConfidence * (1.0 - conflictScore)).coerceIn(0.0, 1.0)
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
            baseSpatialConfidence = baseConfidence,
            spatialConfidence = conflictAdjustedConfidence,
            localConflictScore = conflictScore,
            supportIds = mechanismSupport.map { it.id }.distinct(),
            spatialReason = spatial.reason,
            mechanism = input.mechanism,
            geometryFingerprint = projection.geometryFingerprint,
            equilibriumCoordinate = projection.coordinate,
            projectionWeights = projection.weights,
            context = input.context,
            riskCalibrated = false,
            pImprove = null,
            actionable = false,
            actionabilityState = PredictorActionabilityState.ABSTAIN,
            actionabilityReason = "RISK_NOT_CALIBRATED",
            nextEvidence = "CALIBRATE_P_IMPROVE_WITH_POST_WRITE_OUTCOMES",
        )
    }

    fun shrinkDelta(rawDeltaStar: Double, distance: Double, uncertainty: Double): Double {
        require(rawDeltaStar.isFinite())
        require(distance.isFinite() && distance >= 0.0)
        require(uncertainty.isFinite() && uncertainty >= 0.0)
        return rawDeltaStar / (1.0 + distance + uncertainty)
    }

    private fun isActuatorCorrection(mechanism: CorrectionMechanism): Boolean =
        mechanism == CorrectionMechanism.MAP_LOCAL || mechanism == CorrectionMechanism.CURVE_MUL_ACT

    private fun mechanismAbstentionReason(mechanism: CorrectionMechanism): String = when (mechanism) {
        CorrectionMechanism.UNKNOWN -> "MECHANISM_UNKNOWN"
        CorrectionMechanism.ENVIRONMENTAL_DIAGNOSTIC -> "MECHANISM_ENVIRONMENTAL_DIAGNOSTIC"
        CorrectionMechanism.NO_ACTION -> "MECHANISM_NO_ACTION"
        CorrectionMechanism.MAP_LOCAL,
        CorrectionMechanism.CURVE_MUL_ACT -> "MECHANISM_SUPPORT_INSUFFICIENT"
    }

    private fun localConflictScore(trajectoryEstimates: List<TrajectoryEstimate>): Double {
        val positiveWeight = trajectoryEstimates
            .filter { it.deltaStar > 0.0 }
            .sumOf { it.weight * it.contextComparability }
        val negativeWeight = trajectoryEstimates
            .filter { it.deltaStar < 0.0 }
            .sumOf { it.weight * it.contextComparability }
        val directionalWeight = positiveWeight + negativeWeight
        if (directionalWeight <= 0.0) return 0.0
        return (2.0 * min(positiveWeight, negativeWeight) / directionalWeight).coerceIn(0.0, 1.0)
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
        val comparability = (
            usable.sumOf { it.observation.queryContextComparability * it.weight } / total
            ).coerceIn(0.0, 1.0)
        val weight = usable.maxOf { it.weight }
        return TrajectoryEstimate(trajectoryId, delta, uncertainty, comparability, weight)
    }

    private fun abstain(
        input: PredictorRelativeFieldInput,
        reason: String,
        spatialConfidence: Double = 0.0,
        projection: PredictorGeometryProjection? = null,
        support: List<PredictorRelativeObservation> = input.support,
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
        baseSpatialConfidence = spatialConfidence.coerceIn(0.0, 1.0),
        spatialConfidence = spatialConfidence.coerceIn(0.0, 1.0),
        localConflictScore = 0.0,
        supportIds = support.map { it.id }.distinct(),
        spatialReason = reason,
        mechanism = input.mechanism,
        geometryFingerprint = projection?.geometryFingerprint ?: input.calibration?.geometryFingerprint,
        equilibriumCoordinate = projection?.coordinate,
        projectionWeights = projection?.weights.orEmpty(),
        context = input.context,
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
        val contextComparability: Double,
        val weight: Double,
    )
}
