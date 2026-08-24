package com.omegas.prohub.learning

import com.omegas.prohub.physics.MagnitudeAuthority
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Contrato científico tipado do Predictor (Steps 147–155).
 *
 * Este boundary é deliberadamente puro: não conhece JSON, UI, transporte,
 * USB, serial, writer, Store, Router ou Scheduler. O único identity authority
 * recebido é o LearningCalibrationBinding já congelado pelo pipeline.
 *
 * Qualquer inconsistência material invalida o snapshot inteiro. Evidência de
 * outra calibração, geração, geometria, revisão, época ou sessão nunca produz
 * IdealTarget parcial.
 */
enum class PredictorSnapshotState {
    READY,
    ABSTAIN,
}

enum class PredictorAbstentionReason {
    INVALID_CALIBRATION_IDENTITY,
    CALIBRATION_IDENTITY_MISMATCH,
    IDENTITY_MISMATCH,
    GENERATION_STALE,
    GEOMETRY_UNKNOWN,
    GEOMETRY_MISMATCH,
    SOURCE_IDENTITY_MISMATCH,
    INVALID_SOURCE_REVISION,
    SOURCE_REVISION_MISMATCH,
    SOURCE_NOT_CURRENT,
    REFERENCE_STALE,
    CONTEXT_INSUFFICIENT,
    MUTATION_QUARANTINE,
    PHYSICS_UNKNOWN,
    SUPPORT_INSUFFICIENT,
    EPOCH_OR_SESSION_MISMATCH,
    NO_DIRECT_OBSERVATIONS,
    INVALID_SCIENTIFIC_VALUE,
    SCIENTIFIC_VALUE_UNKNOWN,
}

enum class PredictorKnownness {
    KNOWN,
    UNKNOWN,
}

enum class PredictorSourceFreshness {
    CURRENT,
    STALE,
    UNKNOWN,
}

enum class PredictorMutationState {
    STABLE,
    MUTATING,
    RECONCILING,
    UNKNOWN,
}

enum class PredictorReferenceState {
    CURRENT,
    STALE,
    UNKNOWN,
}

enum class PredictorPhysicsState {
    KNOWN,
    UNKNOWN,
}

enum class PredictorContextState {
    SUFFICIENT,
    INSUFFICIENT,
    UNKNOWN,
}

enum class PredictorSupportState {
    SUFFICIENT,
    INSUFFICIENT,
    UNKNOWN,
}

data class PredictorSourceRevisions(
    val mapRevision: Long,
    val curveRevision: Long,
    val evidenceRevision: Long,
    val referenceRevision: Long,
    val physicsRevision: Long,
) {
    fun valid(): Boolean =
        mapRevision > 0L &&
            curveRevision > 0L &&
            evidenceRevision > 0L &&
            referenceRevision > 0L &&
            physicsRevision > 0L
}

data class PredictorCell(
    val row: Int,
    val column: Int,
)

data class PredictorOperatingPoint(
    val rpm: Double,
    val petrolInjectionMs: Double,
    val mapBar: Double?,
    val deltaPressureBar: Double? = null,
    val petrolReferenceTemperatureC: Double? = null,
    val waterTemperatureC: Double? = null,
    val gasTemperatureC: Double? = null,
    val effectiveMass: Double? = null,
    val effectiveCapacity: Double? = null,
) {
    fun valid(): Boolean =
        rpm.isFinite() && rpm > 0.0 &&
            petrolInjectionMs.isFinite() && petrolInjectionMs > 0.0 &&
            mapBar.nonNegativeFiniteWhenPresent() &&
            deltaPressureBar.finiteWhenPresent() &&
            petrolReferenceTemperatureC.finiteWhenPresent() &&
            waterTemperatureC.finiteWhenPresent() &&
            gasTemperatureC.finiteWhenPresent() &&
            effectiveMass.positiveFiniteWhenPresent() &&
            effectiveCapacity.positiveFiniteWhenPresent()
}

data class PredictorEvidenceStamp(
    val calibrationFingerprint: String,
    val calibrationGeneration: Int,
    val geometryFingerprint: String,
    val mapHash: String,
    val curveHash: String,
    val sourceRevisions: PredictorSourceRevisions,
    val epoch: Int,
    val sessionId: String,
    val freshness: PredictorSourceFreshness,
)

/**
 * Evidência K* direta observada. Prediction é outro tipo e não herda deste tipo.
 * `currentK` é contexto para o futuro campo relativo delta*=ln(K_star / K_current),
 * não uma autorização de escrita e não uma StepPolicy amortecida.
 *
 * Context/support sufficiency e magnitude authority vêm tipados da autoridade
 * científica upstream; Predictor não inventa threshold local para promovê-los.
 */
data class PredictorObservation(
    val cell: PredictorCell,
    val kStar: Double,
    val currentK: Int,
    val uncertaintyPercent: Double,
    val support: Double,
    val knownness: PredictorKnownness,
    val operatingPoint: PredictorOperatingPoint,
    val stamp: PredictorEvidenceStamp,
    val provenance: String,
    val contextState: PredictorContextState = PredictorContextState.SUFFICIENT,
    val supportState: PredictorSupportState = PredictorSupportState.SUFFICIENT,
    val magnitudeAuthority: MagnitudeAuthority = MagnitudeAuthority.UNKNOWN,
    val assumptions: List<String> = emptyList(),
    val evidenceRefs: List<String> = emptyList(),
)

/**
 * Resultado previsto downstream. Intencionalmente não é PredictorObservation e
 * não possui conversão implícita/delegada para o input científico.
 */
data class PredictorPrediction(
    val candidate: IdealTargetCandidate,
    val confidence: Double,
    val sourceRevisionToken: String,
)

data class PredictorInputSnapshot(
    val calibration: LearningCalibrationBinding,
    val curveHash: String,
    val sourceRevisions: PredictorSourceRevisions,
    val epoch: Int,
    val sessionId: String,
    val observations: List<PredictorObservation>,
    val mutationState: PredictorMutationState = PredictorMutationState.STABLE,
    val referenceState: PredictorReferenceState = PredictorReferenceState.CURRENT,
    val physicsState: PredictorPhysicsState = PredictorPhysicsState.KNOWN,
    val previousSnapshot: PredictorSnapshot? = null,
    val model: PredictorModelDescriptor = PredictorModelDescriptor.directKStarDefault(),
    val predictionErrorStats: PredictorPredictionErrorStats = PredictorPredictionErrorStats.empty(),
)

data class IdealTargetCandidate(
    val cell: PredictorCell,
    val targetK: Int,
    val kStarObserved: Double,
    val currentKObserved: Int,
    val uncertaintyPercent: Double,
    val support: Double,
    val provenance: String,
    val sourceRevisions: PredictorSourceRevisions,
    val estimateK: Double,
    val range: PredictorTargetRange,
    val authority: MagnitudeAuthority,
    val assumptions: List<String>,
    val evidenceRefs: List<String>,
    val model: PredictorModelDescriptor,
    val predictionErrorStats: PredictorPredictionErrorStats,
) {
    /** Authority eligibility is not runtime actionability and exposes no writer handle. */
    fun industrialIdealAuthorityEligible(): Boolean =
        authority == MagnitudeAuthority.PHYSICALLY_ANCHORED ||
            authority == MagnitudeAuthority.EMPIRICALLY_BOUNDED
}

/**
 * Carry-forward exclusivamente diagnóstico. Nunca é copiado para `candidates`
 * do snapshot atual e portanto não recupera actionability durante quarantine.
 */
data class PredictorDiagnosticSnapshot(
    val revisionToken: String,
    val candidates: List<IdealTargetCandidate>,
    val stale: Boolean = true,
)

data class PredictorSnapshot(
    val state: PredictorSnapshotState,
    val revisionToken: String,
    val candidates: List<IdealTargetCandidate>,
    val abstentionReasons: Set<PredictorAbstentionReason>,
    val diagnosticPrevious: PredictorDiagnosticSnapshot? = null,
)

object PredictorContract {
    fun evaluate(input: PredictorInputSnapshot): PredictorSnapshot {
        val reasons = validate(input)
        if (reasons.isNotEmpty()) return abstain(input, reasons)

        val candidates = input.observations
            .sortedWith(compareBy<PredictorObservation> { it.cell.row }.thenBy { it.cell.column }.thenBy { it.provenance })
            .map { observation ->
                val halfWidth = observation.kStar * observation.uncertaintyPercent / 100.0
                IdealTargetCandidate(
                    cell = observation.cell,
                    targetK = idealTarget(observation.kStar),
                    kStarObserved = observation.kStar,
                    currentKObserved = observation.currentK,
                    uncertaintyPercent = observation.uncertaintyPercent,
                    support = observation.support,
                    provenance = observation.provenance,
                    sourceRevisions = input.sourceRevisions,
                    estimateK = observation.kStar,
                    range = PredictorTargetRange(
                        lowerK = (observation.kStar - halfWidth).coerceAtLeast(0.0),
                        upperK = observation.kStar + halfWidth,
                        basis = "OBSERVATION_DECLARED_UNCERTAINTY_PERCENT",
                    ),
                    authority = observation.magnitudeAuthority,
                    assumptions = observation.assumptions.ifEmpty {
                        listOf("same calibration identity", "current source revisions")
                    },
                    evidenceRefs = observation.evidenceRefs.ifEmpty { listOf(observation.provenance) },
                    model = input.model,
                    predictionErrorStats = input.predictionErrorStats,
                )
            }
        return PredictorSnapshot(
            state = PredictorSnapshotState.READY,
            revisionToken = revisionToken(input),
            candidates = candidates,
            abstentionReasons = emptySet(),
        )
    }

    private fun validate(input: PredictorInputSnapshot): LinkedHashSet<PredictorAbstentionReason> {
        val reasons = linkedSetOf<PredictorAbstentionReason>()
        val calibration = input.calibration
        val identityCoreUsable =
            calibration.calibrationFingerprint.isNotBlank() &&
                calibration.calibrationGeneration > 0 &&
                calibration.geometryFingerprint.isNotBlank() &&
                calibration.mapHash.isNotBlank()
        if (!identityCoreUsable || input.curveHash.isBlank() || input.epoch <= 0 || input.sessionId.isBlank()) {
            reasons += PredictorAbstentionReason.INVALID_CALIBRATION_IDENTITY
        }
        if (!calibration.geometryKnown()) {
            reasons += PredictorAbstentionReason.GEOMETRY_UNKNOWN
            reasons += PredictorAbstentionReason.INVALID_CALIBRATION_IDENTITY
        }
        if (!input.sourceRevisions.valid()) {
            reasons += PredictorAbstentionReason.INVALID_SOURCE_REVISION
        }
        if (input.observations.isEmpty()) {
            reasons += PredictorAbstentionReason.NO_DIRECT_OBSERVATIONS
        }
        if (input.referenceState != PredictorReferenceState.CURRENT) {
            reasons += PredictorAbstentionReason.REFERENCE_STALE
        }
        if (input.mutationState != PredictorMutationState.STABLE) {
            reasons += PredictorAbstentionReason.MUTATION_QUARANTINE
        }
        if (input.physicsState != PredictorPhysicsState.KNOWN) {
            reasons += PredictorAbstentionReason.PHYSICS_UNKNOWN
        }

        input.observations.forEach { observation ->
            val stamp = observation.stamp
            if (stamp.calibrationFingerprint != calibration.calibrationFingerprint) {
                reasons += PredictorAbstentionReason.IDENTITY_MISMATCH
                reasons += PredictorAbstentionReason.CALIBRATION_IDENTITY_MISMATCH
            }
            if (stamp.calibrationGeneration < calibration.calibrationGeneration) {
                reasons += PredictorAbstentionReason.GENERATION_STALE
                reasons += PredictorAbstentionReason.CALIBRATION_IDENTITY_MISMATCH
            } else if (stamp.calibrationGeneration != calibration.calibrationGeneration) {
                reasons += PredictorAbstentionReason.IDENTITY_MISMATCH
                reasons += PredictorAbstentionReason.CALIBRATION_IDENTITY_MISMATCH
            }
            if (stamp.geometryFingerprint != calibration.geometryFingerprint) {
                reasons += PredictorAbstentionReason.GEOMETRY_MISMATCH
            }
            if (stamp.mapHash != calibration.mapHash || stamp.curveHash != input.curveHash) {
                reasons += PredictorAbstentionReason.SOURCE_IDENTITY_MISMATCH
            }
            if (stamp.sourceRevisions != input.sourceRevisions) {
                reasons += PredictorAbstentionReason.SOURCE_REVISION_MISMATCH
            }
            if (!stamp.sourceRevisions.valid()) {
                reasons += PredictorAbstentionReason.INVALID_SOURCE_REVISION
            }
            if (stamp.freshness != PredictorSourceFreshness.CURRENT) {
                reasons += PredictorAbstentionReason.SOURCE_NOT_CURRENT
            }
            if (stamp.epoch != input.epoch || stamp.sessionId != input.sessionId) {
                reasons += PredictorAbstentionReason.EPOCH_OR_SESSION_MISMATCH
            }
            if (observation.knownness != PredictorKnownness.KNOWN) {
                reasons += PredictorAbstentionReason.SCIENTIFIC_VALUE_UNKNOWN
            }
            if (observation.contextState != PredictorContextState.SUFFICIENT) {
                reasons += PredictorAbstentionReason.CONTEXT_INSUFFICIENT
            }
            if (observation.supportState != PredictorSupportState.SUFFICIENT) {
                reasons += PredictorAbstentionReason.SUPPORT_INSUFFICIENT
            }
            if (!scientificallyValid(observation, calibration)) {
                reasons += PredictorAbstentionReason.INVALID_SCIENTIFIC_VALUE
            }
        }
        return reasons
    }

    private fun scientificallyValid(
        observation: PredictorObservation,
        calibration: LearningCalibrationBinding,
    ): Boolean =
        observation.cell.row in calibration.petrolAxisMs.indices &&
            observation.cell.column in calibration.rpmAxis.indices &&
            observation.kStar.isFinite() && observation.kStar in 0.0..255.0 &&
            observation.currentK in 0..255 &&
            observation.uncertaintyPercent.isFinite() && observation.uncertaintyPercent >= 0.0 &&
            observation.support.isFinite() && observation.support in 0.0..1.0 &&
            observation.operatingPoint.valid() &&
            observation.provenance.isNotBlank() &&
            observation.assumptions.none { it.isBlank() } &&
            observation.evidenceRefs.none { it.isBlank() }

    /**
     * IdealTarget científico direto da observação K*. Não incorpora confidence,
     * beta, damping, MIN_SAFE_K ou qualquer StepPolicy/writer authority.
     */
    private fun idealTarget(kStar: Double): Int = kStar.roundToInt()

    private fun abstain(
        input: PredictorInputSnapshot,
        reasons: Set<PredictorAbstentionReason>,
    ): PredictorSnapshot {
        val diagnosticPrevious = if (PredictorAbstentionReason.MUTATION_QUARANTINE in reasons) {
            input.previousSnapshot
                ?.takeIf { it.state == PredictorSnapshotState.READY }
                ?.let { previous ->
                    PredictorDiagnosticSnapshot(
                        revisionToken = previous.revisionToken,
                        candidates = previous.candidates.toList(),
                        stale = true,
                    )
                }
        } else {
            null
        }
        return PredictorSnapshot(
            state = PredictorSnapshotState.ABSTAIN,
            revisionToken = revisionToken(input),
            candidates = emptyList(),
            abstentionReasons = reasons.toSet(),
            diagnosticPrevious = diagnosticPrevious,
        )
    }

    private fun revisionToken(input: PredictorInputSnapshot): String {
        val observations = input.observations
            .sortedWith(compareBy<PredictorObservation> { it.cell.row }.thenBy { it.cell.column }.thenBy { it.provenance })
            .joinToString(";") { observation ->
                listOf(
                    observation.cell.row,
                    observation.cell.column,
                    observation.kStar,
                    observation.currentK,
                    observation.uncertaintyPercent,
                    observation.support,
                    observation.knownness.name,
                    observation.contextState.name,
                    observation.supportState.name,
                    observation.magnitudeAuthority.name,
                    observation.assumptions.joinToString(","),
                    observation.evidenceRefs.joinToString(","),
                    observation.operatingPoint.rpm,
                    observation.operatingPoint.petrolInjectionMs,
                    observation.operatingPoint.mapBar,
                    observation.operatingPoint.deltaPressureBar,
                    observation.operatingPoint.petrolReferenceTemperatureC,
                    observation.operatingPoint.waterTemperatureC,
                    observation.operatingPoint.gasTemperatureC,
                    observation.operatingPoint.effectiveMass,
                    observation.operatingPoint.effectiveCapacity,
                    observation.stamp.calibrationFingerprint,
                    observation.stamp.calibrationGeneration,
                    observation.stamp.geometryFingerprint,
                    observation.stamp.mapHash,
                    observation.stamp.curveHash,
                    observation.stamp.sourceRevisions,
                    observation.stamp.epoch,
                    observation.stamp.sessionId,
                    observation.stamp.freshness.name,
                    observation.provenance,
                ).joinToString("|")
            }
        val raw = listOf(
            input.calibration.key(),
            input.calibration.mapHash,
            input.curveHash,
            input.sourceRevisions,
            input.epoch,
            input.sessionId,
            input.mutationState.name,
            input.referenceState.name,
            input.physicsState.name,
            input.previousSnapshot?.revisionToken,
            input.model,
            input.predictionErrorStats,
            observations,
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

private fun Double?.finiteWhenPresent(): Boolean = this == null || isFinite()
private fun Double?.nonNegativeFiniteWhenPresent(): Boolean = this == null || (isFinite() && this >= 0.0)
private fun Double?.positiveFiniteWhenPresent(): Boolean = this == null || (isFinite() && this > 0.0)
