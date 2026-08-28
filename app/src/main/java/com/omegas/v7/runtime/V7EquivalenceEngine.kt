package com.omegas.v7.runtime

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pareamento físico independente da grade K.
 *
 * RPM e MAP escolhem a referência primária. Petrol Inj não participa da busca;
 * ele é usado somente depois, para medir a diferença gasolina × GNV. Água,
 * temperatura do gás e pressão permanecem contexto diagnóstico, nunca gates do
 * pareamento operacional RPM+MAP+Tinj.
 */
data class EquivalencePolicyV7(
    val rpmMinimumWindow: Double = 120.0,
    val rpmPercentWindow: Double = 6.0,
    val mapWindowBar: Double = 0.08,
    // Retido por compatibilidade de configuração/snapshot; não participa da distância.
    val waterWindowC: Double = 8.0,
    val maximumNormalizedDistance: Double = 1.75,
    val maximumNeighbors: Int = 4,
    val deadbandMs: Double = 0.08,
    val deadbandPercent: Double = 2.0,
) {
    init {
        require(rpmMinimumWindow > 0.0)
        require(rpmPercentWindow > 0.0)
        require(mapWindowBar > 0.0)
        require(waterWindowC > 0.0)
        require(maximumNormalizedDistance > 0.0)
        require(maximumNeighbors > 0)
    }
}

data class FuelComparisonV7(
    val id: String,
    val revision: CalibrationRevisionV7,
    val cngVisitId: String,
    val petrolEvidenceIds: List<String>,
    val rpm: Double,
    val mapBar: Double,
    val waterC: Double,
    val petrolTargetMs: Double,
    val petrolOnCngMs: Double,
    val differenceMs: Double,
    val errorPercent: Double,
    val direction: String,
    val quality: Double,
    val createdAtMs: Long,
) {
    init {
        require(id.isNotBlank())
        require(cngVisitId.isNotBlank())
        require(petrolEvidenceIds.isNotEmpty())
        require(quality in 0.0..1.0)
    }
}

class V7EquivalenceEngine(
    private val policy: EquivalencePolicyV7 = EquivalencePolicyV7(),
) {
    /**
     * Preserva toda comparação já criada para uma visita física.
     *
     * Motivo: uma referência de gasolina que amadurece amanhã não pode reescrever
     * retroativamente o erro medido numa visita GNV já comparada. Visitas GNV que
     * ainda não tinham referência continuam pendentes e ganham sua primeira
     * comparação quando surgir gasolina fisicamente compatível.
     *
     * `nowMs` é mantido por compatibilidade de chamada, mas não participa da
     * identidade temporal: o timestamp científico é o da visita GNV.
     */
    fun reconcile(state: V7SessionState, nowMs: Long = System.currentTimeMillis()): List<FuelComparisonV7> {
        require(nowMs >= 0)
        val revision = state.calibration.revision
        val historical = state.comparisons.filter { it.revision != revision }
        val existingActive = state.comparisons
            .filter { it.revision == revision }
            .distinctBy { it.cngVisitId }
        val alreadyCompared = existingActive.mapTo(linkedSetOf()) { it.cngVisitId }
        val created = state.activeCngEvidence()
            .distinctBy { it.visitId }
            .filterNot { it.visitId in alreadyCompared }
            .mapNotNull { cng -> compare(cng, state.petrolEvidence, revision) }
        val active = (existingActive + created)
            .sortedWith(compareBy<FuelComparisonV7> { it.createdAtMs }.thenBy { it.cngVisitId })
        return historical + active
    }

    private fun compare(
        cng: EvidenceV7,
        petrolEvidence: List<EvidenceV7>,
        revision: CalibrationRevisionV7,
    ): FuelComparisonV7? {
        val candidates = petrolEvidence
            .asSequence()
            .filter { it.fuel == FuelV7.PETROL }
            .map { petrol -> Candidate(petrol, distance(petrol, cng)) }
            .filter { it.distance.isFinite() && it.distance <= policy.maximumNormalizedDistance }
            .sortedBy { it.distance }
            .take(policy.maximumNeighbors)
            .toList()
        if (candidates.isEmpty()) return null

        val weighted = candidates.map { candidate ->
            val proximity = exp(-0.5 * candidate.distance * candidate.distance)
            val inverseDistance = 1.0 / (0.15 + candidate.distance)
            val weight = (candidate.evidence.quality.coerceIn(0.05, 1.0) * proximity * inverseDistance)
                .coerceAtLeast(1e-9)
            candidate to weight
        }
        val totalWeight = weighted.sumOf { it.second }
        if (!totalWeight.isFinite() || totalWeight <= 0.0) return null
        val petrolTarget = weighted.sumOf { it.first.evidence.petrolMs * it.second } / totalWeight
        val difference = cng.petrolMs - petrolTarget
        val errorPercent = if (petrolTarget <= 0.05) 0.0 else difference / petrolTarget * 100.0
        val direction = when {
            abs(difference) <= policy.deadbandMs || abs(errorPercent) <= policy.deadbandPercent -> "EQUIVALENT"
            difference > 0.0 -> "INCREASE_CNG_DELIVERY"
            else -> "DECREASE_CNG_DELIVERY"
        }
        val referenceQuality = weighted.sumOf { it.first.evidence.quality * it.second } / totalWeight
        val nearestQuality = exp(-0.35 * candidates.first().distance)
        val pairQuality = sqrt(referenceQuality.coerceIn(0.0, 1.0) * cng.quality.coerceIn(0.0, 1.0)) * nearestQuality
        return FuelComparisonV7(
            id = "${revision.curveK}:${revision.mapK}:${cng.visitId}",
            revision = revision,
            cngVisitId = cng.visitId,
            petrolEvidenceIds = candidates.map { it.evidence.id },
            rpm = cng.rpm,
            mapBar = cng.mapBar,
            waterC = cng.waterC,
            petrolTargetMs = petrolTarget,
            petrolOnCngMs = cng.petrolMs,
            differenceMs = difference,
            errorPercent = errorPercent,
            direction = direction,
            quality = pairQuality.coerceIn(0.0, 1.0),
            createdAtMs = cng.collectedAtMs,
        )
    }

    private fun distance(petrol: EvidenceV7, cng: EvidenceV7): Double {
        val rpmWindow = max(
            policy.rpmMinimumWindow,
            max(abs(petrol.rpm), abs(cng.rpm)) * policy.rpmPercentWindow / 100.0,
        )
        val rpmUnits = abs(petrol.rpm - cng.rpm) / rpmWindow
        val mapUnits = abs(petrol.mapBar - cng.mapBar) / policy.mapWindowBar
        return sqrt(rpmUnits * rpmUnits + mapUnits * mapUnits)
    }

    private data class Candidate(val evidence: EvidenceV7, val distance: Double)
}
