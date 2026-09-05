package com.omegas.prohub.blue

import com.omegas.v7.runtime.CalibrationRevisionV7
import com.omegas.v7.runtime.EvidenceV7
import com.omegas.v7.runtime.FuelComparisonV7
import com.omegas.v7.runtime.FuelV7
import com.omegas.v7.runtime.V7SessionState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Única autoridade matemática de equivalência do OMEGAS Blue.
 *
 * O motor mede primeiro a referência física de gasolina em RPM×MAP, mede o erro
 * do GNV como razão logarítmica e aprende a sensibilidade dos atuadores K a
 * partir de intervenções confirmadas. Ele não conta visitas para fabricar
 * confiança e não possui caminho de escrita automática na ECU.
 */
class BlueCausalEngine(
    private val policy: BlueCausalPolicy = BlueCausalPolicy(),
) {
    fun calibrationState(revision: CalibrationRevisionV7): BlueCalibrationStateId =
        BlueCalibrationStateId(revision.curveK, revision.mapK)

    /**
     * Uma única evidência gasolina de alta qualidade pode publicar referência.
     * Múltiplos microbursts compatíveis reduzem ruído pela mediana, mas não são
     * requisito artificial de maturidade.
     */
    fun petrolReference(
        target: EvidenceV7,
        petrolEvidence: List<EvidenceV7>,
    ): BluePetrolReference? {
        val candidates = petrolEvidence.asSequence()
            .filter { it.fuel == FuelV7.PETROL }
            .map { evidence -> BlueCandidate(evidence, normalizedDistance(evidence, target)) }
            .filter { it.distance.isFinite() && it.distance <= policy.maximumNormalizedDistance }
            .sortedBy { it.distance }
            .take(policy.maximumReferenceBursts)
            .toList()
        if (candidates.isEmpty()) return null

        val values = candidates.map { it.evidence.petrolMs }.sorted()
        val median = if (values.size % 2 == 1) {
            values[values.size / 2]
        } else {
            (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0
        }
        if (!median.isFinite() || median <= policy.minimumPetrolMs) return null

        val nearest = candidates.first()
        val meanQuality = candidates.map { it.evidence.quality.coerceIn(0.0, 1.0) }.average()
        val proximity = exp(-0.35 * nearest.distance)
        val quality = sqrt(meanQuality.coerceIn(0.0, 1.0) * target.quality.coerceIn(0.0, 1.0)) * proximity
        return BluePetrolReference(
            petrolMs = median,
            support = candidates.size,
            quality = quality.coerceIn(0.0, 1.0),
            evidenceIds = candidates.map { it.evidence.id },
            nearestNormalizedDistance = nearest.distance,
        )
    }

    fun cngErrorLog(petrolOnCngMs: Double, petrolReferenceMs: Double): Double {
        require(petrolOnCngMs.isFinite() && petrolOnCngMs > policy.minimumPetrolMs)
        require(petrolReferenceMs.isFinite() && petrolReferenceMs > policy.minimumPetrolMs)
        return ln(petrolOnCngMs / petrolReferenceMs)
    }

    fun errorPercentFromLog(errorLog: Double): Double {
        require(errorLog.isFinite())
        return (exp(errorLog) - 1.0) * 100.0
    }

    /**
     * Identificação causal do ganho do atuador.
     *
     * Se Δln(K) não existe, ou se a resposta não é finita/coerente, o evento não
     * produz ganho. Nenhum valor histórico (1.0, 0.7 etc.) é usado como verdade.
     */
    fun actuatorGain(
        beforeErrorLog: Double,
        afterErrorLog: Double,
        beforeK: Double,
        afterK: Double,
    ): BlueActuatorGain? {
        if (!beforeErrorLog.isFinite() || !afterErrorLog.isFinite()) return null
        if (!beforeK.isFinite() || !afterK.isFinite() || beforeK <= 0.0 || afterK <= 0.0) return null
        val deltaLnK = ln(afterK / beforeK)
        if (!deltaLnK.isFinite() || abs(deltaLnK) < policy.minimumActuatorLogStep) return null
        val rawGain = -(afterErrorLog - beforeErrorLog) / deltaLnK
        if (!rawGain.isFinite() || rawGain <= 0.0) return null
        return BlueActuatorGain(
            gain = rawGain.coerceIn(policy.minimumAcceptedGain, policy.maximumAcceptedGain),
            rawGain = rawGain,
            saturated = rawGain !in policy.minimumAcceptedGain..policy.maximumAcceptedGain,
        )
    }

    /**
     * Converte erro observado em multiplicador-alvo apenas quando existe ganho
     * causal conhecido. Sem ganho, o motor mede e observa; não inventa correção.
     */
    fun correctionMultiplier(errorLog: Double, gain: BlueActuatorGain?): Double? {
        if (!errorLog.isFinite() || gain == null || gain.gain <= 0.0) return null
        return exp(errorLog / gain.gain)
            .coerceIn(policy.minimumCorrectionMultiplier, policy.maximumCorrectionMultiplier)
    }

    /**
     * Compatibilidade temporária do runtime V7: a matemática já é Blue e existe
     * somente aqui. Comparações antigas de outras revisões permanecem para
     * auditoria, mas nova evidência GNV é criada somente no estado ativo.
     */
    fun reconcile(state: V7SessionState, nowMs: Long = System.currentTimeMillis()): List<FuelComparisonV7> {
        require(nowMs >= 0L)
        val revision = state.calibration.revision
        val historical = state.comparisons.filter { it.revision != revision }
        val existingActive = state.comparisons
            .filter { it.revision == revision }
            .distinctBy { it.cngVisitId }
        val alreadyCompared = existingActive.mapTo(linkedSetOf()) { it.cngVisitId }
        val created = state.activeCngEvidence()
            .asSequence()
            .filter { it.cngRevision == revision }
            .distinctBy { it.visitId }
            .filterNot { it.visitId in alreadyCompared }
            .mapNotNull { cng -> compare(cng, state.petrolEvidence, revision) }
            .toList()
        return historical + (existingActive + created)
            .sortedWith(compareBy<FuelComparisonV7> { it.createdAtMs }.thenBy { it.cngVisitId })
    }

    private fun compare(
        cng: EvidenceV7,
        petrolEvidence: List<EvidenceV7>,
        revision: CalibrationRevisionV7,
    ): FuelComparisonV7? {
        val reference = petrolReference(cng, petrolEvidence) ?: return null
        val errorLog = cngErrorLog(cng.petrolMs, reference.petrolMs)
        val errorPercent = errorPercentFromLog(errorLog)
        val difference = cng.petrolMs - reference.petrolMs
        val direction = when {
            abs(difference) <= policy.deadbandMs || abs(errorPercent) <= policy.deadbandPercent -> "EQUIVALENT"
            difference > 0.0 -> "INCREASE_CNG_DELIVERY"
            else -> "DECREASE_CNG_DELIVERY"
        }
        return FuelComparisonV7(
            id = "BLUE:${revision.curveK}:${revision.mapK}:${cng.visitId}",
            revision = revision,
            cngVisitId = cng.visitId,
            petrolEvidenceIds = reference.evidenceIds,
            rpm = cng.rpm,
            mapBar = cng.mapBar,
            waterC = cng.waterC,
            petrolTargetMs = reference.petrolMs,
            petrolOnCngMs = cng.petrolMs,
            differenceMs = difference,
            errorPercent = errorPercent,
            direction = direction,
            quality = reference.quality,
            createdAtMs = cng.collectedAtMs,
        )
    }

    private fun normalizedDistance(reference: EvidenceV7, target: EvidenceV7): Double {
        val rpmWindow = max(
            policy.rpmMinimumWindow,
            max(abs(reference.rpm), abs(target.rpm)) * policy.rpmPercentWindow / 100.0,
        )
        val rpmUnits = abs(reference.rpm - target.rpm) / rpmWindow
        val mapUnits = abs(reference.mapBar - target.mapBar) / policy.mapWindowBar
        val waterUnits = when {
            !reference.waterC.isFinite() || !target.waterC.isFinite() -> 0.0
            reference.waterC == EvidenceV7.UNKNOWN_TEMPERATURE_C || target.waterC == EvidenceV7.UNKNOWN_TEMPERATURE_C -> 0.0
            else -> abs(reference.waterC - target.waterC) / policy.waterWindowC
        }
        return sqrt(rpmUnits * rpmUnits + mapUnits * mapUnits + 0.25 * waterUnits * waterUnits)
    }

    private data class BlueCandidate(val evidence: EvidenceV7, val distance: Double)
}

data class BlueCausalPolicy(
    val rpmMinimumWindow: Double = 120.0,
    val rpmPercentWindow: Double = 6.0,
    val mapWindowBar: Double = 0.08,
    val waterWindowC: Double = 8.0,
    val maximumNormalizedDistance: Double = 1.75,
    val maximumReferenceBursts: Int = 7,
    val minimumPetrolMs: Double = 0.05,
    val deadbandMs: Double = 0.08,
    val deadbandPercent: Double = 2.0,
    val minimumActuatorLogStep: Double = 0.002,
    val minimumAcceptedGain: Double = 0.20,
    val maximumAcceptedGain: Double = 2.50,
    val minimumCorrectionMultiplier: Double = 0.80,
    val maximumCorrectionMultiplier: Double = 1.20,
) {
    init {
        require(rpmMinimumWindow > 0.0)
        require(rpmPercentWindow > 0.0)
        require(mapWindowBar > 0.0)
        require(waterWindowC > 0.0)
        require(maximumNormalizedDistance > 0.0)
        require(maximumReferenceBursts > 0)
        require(minimumPetrolMs > 0.0)
        require(minimumAcceptedGain > 0.0 && maximumAcceptedGain > minimumAcceptedGain)
        require(minimumCorrectionMultiplier > 0.0 && maximumCorrectionMultiplier > minimumCorrectionMultiplier)
    }
}

data class BlueCalibrationStateId(val curveK: Long, val mapK: Long)

data class BluePetrolReference(
    val petrolMs: Double,
    val support: Int,
    val quality: Double,
    val evidenceIds: List<String>,
    val nearestNormalizedDistance: Double,
)

data class BlueActuatorGain(
    val gain: Double,
    val rawGain: Double,
    val saturated: Boolean,
)

data class BlueCausalSnapshot(
    val calibrationState: BlueCalibrationStateId,
    val petrolReference: BluePetrolReference?,
    val errorLog: Double?,
    val errorPercent: Double?,
    val actuatorGain: BlueActuatorGain?,
)

data class BlueCorrectionProposal(
    val calibrationState: BlueCalibrationStateId,
    val correctionMultiplier: Double?,
    val errorLog: Double,
    val errorPercent: Double,
    val actuatorGain: BlueActuatorGain?,
    val automaticWrite: Boolean = false,
)
