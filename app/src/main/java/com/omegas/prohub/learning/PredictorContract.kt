package com.omegas.prohub.learning

import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Contrato científico tipado do Predictor (Step 147).
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
    GEOMETRY_MISMATCH,
    SOURCE_IDENTITY_MISMATCH,
    INVALID_SOURCE_REVISION,
    SOURCE_REVISION_MISMATCH,
    SOURCE_NOT_CURRENT,
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
    val mapBar: Double,
    val petrolReferenceTemperatureC: Double? = null,
    val effectiveMass: Double? = null,
    val effectiveCapacity: Double? = null,
) {
    fun valid(): Boolean =
        rpm.isFinite() && rpm > 0.0 &&
            petrolInjectionMs.isFinite() && petrolInjectionMs > 0.0 &&
            mapBar.isFinite() && mapBar >= 0.0 &&
            petrolReferenceTemperatureC.finiteWhenPresent() &&
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
)

data class PredictorSnapshot(
    val state: PredictorSnapshotState,
    val revisionToken: String,
    val candidates: List<IdealTargetCandidate>,
    val abstentionReasons: Set<PredictorAbstentionReason>,
)

object PredictorContract {
    fun evaluate(input: PredictorInputSnapshot): PredictorSnapshot {
        val reasons = validate(input)
        if (reasons.isNotEmpty()) return abstain(input, reasons)

        val candidates = input.observations
            .sortedWith(compareBy<PredictorObservation> { it.cell.row }.thenBy { it.cell.column }.thenBy { it.provenance })
            .map { observation ->
                IdealTargetCandidate(
                    cell = observation.cell,
                    targetK = idealTarget(observation.kStar),
                    kStarObserved = observation.kStar,
                    currentKObserved = observation.currentK,
                    uncertaintyPercent = observation.uncertaintyPercent,
                    support = observation.support,
                    provenance = observation.provenance,
                    sourceRevisions = input.sourceRevisions,
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
        val calibrationUsable =
            calibration.calibrationFingerprint.isNotBlank() &&
                calibration.calibrationGeneration > 0 &&
                calibration.geometryFingerprint.isNotBlank() &&
                calibration.mapHash.isNotBlank() &&
                calibration.geometryKnown()
        if (!calibrationUsable || input.curveHash.isBlank() || input.epoch <= 0 || input.sessionId.isBlank()) {
            reasons += PredictorAbstentionReason.INVALID_CALIBRATION_IDENTITY
        }
        if (!input.sourceRevisions.valid()) {
            reasons += PredictorAbstentionReason.INVALID_SOURCE_REVISION
        }
        if (input.observations.isEmpty()) {
            reasons += PredictorAbstentionReason.NO_DIRECT_OBSERVATIONS
        }

        input.observations.forEach { observation ->
            val stamp = observation.stamp
            if (
                stamp.calibrationFingerprint != calibration.calibrationFingerprint ||
                stamp.calibrationGeneration != calibration.calibrationGeneration
            ) {
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
            observation.provenance.isNotBlank()

    /**
     * IdealTarget científico direto da observação K*. Não incorpora confidence,
     * beta, damping, MIN_SAFE_K ou qualquer StepPolicy/writer authority.
     */
    private fun idealTarget(kStar: Double): Int = kStar.roundToInt()

    private fun abstain(
        input: PredictorInputSnapshot,
        reasons: Set<PredictorAbstentionReason>,
    ): PredictorSnapshot = PredictorSnapshot(
        state = PredictorSnapshotState.ABSTAIN,
        revisionToken = revisionToken(input),
        candidates = emptyList(),
        abstentionReasons = reasons.toSet(),
    )

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
                    observation.operatingPoint.rpm,
                    observation.operatingPoint.petrolInjectionMs,
                    observation.operatingPoint.mapBar,
                    observation.operatingPoint.petrolReferenceTemperatureC,
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
            observations,
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

private fun Double?.finiteWhenPresent(): Boolean = this == null || isFinite()
private fun Double?.positiveFiniteWhenPresent(): Boolean = this == null || (isFinite() && this > 0.0)
