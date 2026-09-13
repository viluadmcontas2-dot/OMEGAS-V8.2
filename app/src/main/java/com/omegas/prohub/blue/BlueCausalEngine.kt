package com.omegas.prohub.blue

import com.omegas.prohub.learning.ContinuousLearningMath
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Single scientific authority for gasoline reference and CNG deviation.
 *
 * A coordinate hit is not enough. Reference quality rewards physical proximity
 * and stable evidence. The gasoline reference is persistent: time since a valid
 * gasoline observation does not invalidate physical RPM x MAP support. The
 * engine never invents an actuator response: a K correction is available only
 * after a causal gain was measured from an actual before/after calibration event.
 */
class BlueCausalEngine(
    private val policy: BluePolicy = BluePolicy(),
) {
    fun calibrationState(revision: CalibrationRevision): BlueCalibrationStateId =
        BlueCalibrationStateId(revision.curveK, revision.mapK)

    fun petrolReference(
        target: FuelEvidence,
        petrolEvidence: List<FuelEvidence>,
    ): BluePetrolReference? {
        val candidates = petrolEvidence
            .asSequence()
            .filter { it.fuel == FuelKind.PETROL }
            .filter { it.petrolMs > 0.0 && it.quality >= policy.minimumEvidenceQuality }
            .map { evidence -> BlueCandidate(evidence, normalizedDistance(evidence, target)) }
            .filter { it.distance <= policy.maximumNormalizedDistance }
            .sortedBy { it.distance }
            .take(policy.maximumReferenceBursts)
            .toList()
        if (candidates.isEmpty()) return null

        val rpmScale = max(policy.minimumRpmWindow, target.rpm * policy.relativeRpmWindow)
        val hasObservedCoordinate = candidates.any { it.distance <= EXACT_COORDINATE_EPSILON }
        val referencePetrolMs = if (candidates.size == 1) {
            // One physically valid observation is already real support. Spatial
            // interpolation is only needed when multiple coordinates exist.
            candidates.single().evidence.petrolMs
        } else {
            ContinuousLearningMath.interpolateSupported2D(
                candidates.map { candidate ->
                    ContinuousLearningMath.SurfacePoint(
                        x = (candidate.evidence.rpm - target.rpm) / rpmScale,
                        y = (candidate.evidence.mapBar - target.mapBar) / policy.mapWindowBar,
                        value = candidate.evidence.petrolMs,
                        weight = candidate.evidence.quality,
                    )
                },
            ) ?: return null
        }
        val provenance = if (candidates.size == 1 || hasObservedCoordinate) {
            BlueReferenceProvenance.OBSERVED
        } else {
            BlueReferenceProvenance.INTERPOLATED
        }

        val values = candidates.map { it.evidence.petrolMs }.sorted()
        val meanQuality = candidates.map { it.evidence.quality }.average()
        val proximity = exp(-candidates.map { it.distance }.average()).coerceIn(0.0, 1.0)
        val quality = (sqrt(meanQuality * target.quality.coerceIn(0.0, 1.0)) * proximity)
            .coerceIn(0.0, 1.0)
        return BluePetrolReference(
            petrolMs = referencePetrolMs,
            quality = quality,
            supportCount = candidates.size,
            nearestDistance = candidates.first().distance,
            evidenceIds = candidates.map { it.evidence.id },
            spreadMs = values.last() - values.first(),
            provenance = provenance,
        )
    }

    fun cngErrorLog(petrolOnCngMs: Double, petrolReferenceMs: Double): Double {
        require(petrolOnCngMs > 0.0 && petrolReferenceMs > 0.0)
        return ln(petrolOnCngMs / petrolReferenceMs)
    }

    fun errorPercentFromLog(errorLog: Double): Double = (exp(errorLog) - 1.0) * 100.0

    /**
     * Deadband is an action policy, never an evidence filter. A measured pair
     * inside the band still belongs in comparisons and must remain visible.
     */
    fun isWithinActionDeadband(petrolOnCngMs: Double, petrolReferenceMs: Double): Boolean {
        require(petrolOnCngMs > 0.0 && petrolReferenceMs > 0.0)
        val errorPercent = errorPercentFromLog(cngErrorLog(petrolOnCngMs, petrolReferenceMs))
        return abs(petrolOnCngMs - petrolReferenceMs) <= policy.absoluteDeadbandMs ||
            abs(errorPercent) <= policy.relativeDeadbandPercent
    }

    fun actuatorGain(
        beforeErrorLog: Double,
        afterErrorLog: Double,
        beforeK: Double,
        afterK: Double,
    ): BlueActuatorGain? {
        if (!beforeK.isFinite() || !afterK.isFinite() || beforeK <= 0.0 || afterK <= 0.0) return null
        val deltaLnK = ln(afterK / beforeK)
        if (abs(deltaLnK) < policy.minimumActuatorStepLog) return null
        val gain = -(afterErrorLog - beforeErrorLog) / deltaLnK
        if (!gain.isFinite() || gain <= policy.minimumAcceptedGain || gain > policy.maximumAcceptedGain) return null
        return BlueActuatorGain(gain)
    }

    fun correctionMultiplier(errorLog: Double, gain: BlueActuatorGain?): Double? {
        gain ?: return null
        return exp(errorLog / gain.gain).coerceIn(
            policy.minimumCorrectionMultiplier,
            policy.maximumCorrectionMultiplier,
        )
    }

    fun reconcile(
        state: BlueLearningState,
        @Suppress("UNUSED_PARAMETER") nowMs: Long = System.currentTimeMillis(),
    ): List<FuelComparison> {
        val activeRevision = state.calibration.revision
        val historical = state.comparisons.filter { it.revision != activeRevision }
        val active = state.activeCngEvidence().mapNotNull { cng ->
            compare(cng, state.petrolEvidence, activeRevision)
        }
        return (historical + active)
            .distinctBy { it.id }
            .sortedWith(compareBy<FuelComparison> { it.createdAtMs }.thenBy { it.cngVisitId })
    }

    private fun compare(
        cng: FuelEvidence,
        petrolEvidence: List<FuelEvidence>,
        revision: CalibrationRevision,
    ): FuelComparison? {
        val reference = petrolReference(cng, petrolEvidence) ?: return null
        if (reference.quality < policy.minimumComparisonQuality) return null
        val errorLog = cngErrorLog(cng.petrolMs, reference.petrolMs)
        val errorPercent = errorPercentFromLog(errorLog)
        return FuelComparison(
            id = "${revision.curveK}:${revision.mapK}:${cng.visitId}",
            revision = revision,
            petrolVisitId = "reference:${cng.visitId}",
            cngVisitId = cng.visitId,
            rpm = cng.rpm,
            mapBar = cng.mapBar,
            petrolTargetMs = reference.petrolMs,
            petrolOnCngMs = cng.petrolMs,
            errorPercent = errorPercent,
            quality = reference.quality,
            createdAtMs = cng.collectedAtMs,
            referenceEvidenceIds = reference.evidenceIds,
            referenceSpreadMs = reference.spreadMs,
            petrolReferenceProvenance = reference.provenance,
        )
    }

    private fun normalizedDistance(reference: FuelEvidence, target: FuelEvidence): Double {
        val rpmScale = max(policy.minimumRpmWindow, target.rpm * policy.relativeRpmWindow)
        val rpm = abs(reference.rpm - target.rpm) / rpmScale
        val map = abs(reference.mapBar - target.mapBar) / policy.mapWindowBar
        return sqrt(rpm.pow(2) + map.pow(2))
    }

    private data class BlueCandidate(val evidence: FuelEvidence, val distance: Double)

    private companion object {
        const val EXACT_COORDINATE_EPSILON = 1e-9
    }
}

data class BluePolicy(
    val minimumEvidenceQuality: Double = 0.45,
    val minimumComparisonQuality: Double = 0.50,
    val minimumRpmWindow: Double = 120.0,
    val relativeRpmWindow: Double = 0.06,
    val mapWindowBar: Double = 0.08,
    val maximumNormalizedDistance: Double = 1.75,
    val maximumReferenceBursts: Int = 7,
    val absoluteDeadbandMs: Double = 0.08,
    val relativeDeadbandPercent: Double = 2.0,
    val minimumActuatorStepLog: Double = 0.003,
    val minimumAcceptedGain: Double = 0.05,
    val maximumAcceptedGain: Double = 12.0,
    val minimumCorrectionMultiplier: Double = 0.80,
    val maximumCorrectionMultiplier: Double = 1.20,
)

data class BluePetrolReference(
    val petrolMs: Double,
    val quality: Double,
    val supportCount: Int,
    val nearestDistance: Double,
    val evidenceIds: List<String> = emptyList(),
    val spreadMs: Double = 0.0,
    val provenance: BlueReferenceProvenance = BlueReferenceProvenance.OBSERVED,
)

data class BlueActuatorGain(val gain: Double)

data class BlueCalibrationStateId(val curveK: Long, val mapK: Long)

data class BlueCorrectionProposal(
    val calibrationState: BlueCalibrationStateId,
    val correctionMultiplier: Double?,
    val errorLog: Double,
    val errorPercent: Double,
    val actuatorGain: BlueActuatorGain?,
    val automaticWrite: Boolean = false,
)
