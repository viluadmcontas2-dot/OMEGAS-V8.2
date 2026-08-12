package com.omegas.v7.runtime

import com.omegas.prohub.calibration.KMapPhysicalAxes
import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.learning.LearningToleranceSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class LearningStabilityStateV7 {
    NO_EVIDENCE,
    LEARNING,
    CONSOLIDATED,
    REVALIDATING,
}

data class LearningStabilitySnapshotV7(
    val state: LearningStabilityStateV7,
    val generation: Int,
    val consolidatedErrorPercent: Double?,
    val recentErrorPercent: Double?,
    val confidence: Double,
    val consolidatedEffectiveVisits: Double,
    val recentEffectiveVisits: Double,
    val consolidatedUniqueVisits: Int,
    val recentUniqueVisits: Int,
    val rpmBandCount: Int,
    val mapBandCount: Int,
    val direction: String,
    val reason: String,
) {
    val actionableBasis: Boolean
        get() = state == LearningStabilityStateV7.CONSOLIDATED ||
            state == LearningStabilityStateV7.REVALIDATING
}

/**
 * Memória científica reconstruível sobre visitas físicas imutáveis.
 *
 * Não altera equivalência, coleta, writer ou tolerâncias. Ela só separa a verdade
 * já consolidada da evidência recente que ainda precisa provar uma mudança real.
 * O resultado é determinístico: a mesma coleção de visitas, em qualquer ordem de
 * entrada, gera o mesmo estado porque a sequência é reconstruída por timestamp + id.
 */
object LearningStabilityV7 {
    private data class Observation(
        val visitId: String,
        val collectedAtMs: Long,
        val errorPercent: Double,
        val differenceMs: Double,
        val weight: Double,
        val rpm: Double,
        val mapBar: Double,
    )

    private data class Summary(
        val centerErrorPercent: Double,
        val centerDifferenceMs: Double,
        val errorMadPercent: Double,
        val differenceMadMs: Double,
        val effectiveVisits: Double,
        val uniqueVisits: Int,
        val directionConsensus: Double,
        val direction: String,
        val rpmBands: Int,
        val mapBands: Int,
    )

    fun mapCell(
        comparisons: List<FuelComparisonV7>,
        row: Int,
        column: Int,
    ): LearningStabilitySnapshotV7 {
        CalibrationShapeV7.requireEditableCell(row, column)
        val observations = comparisons.mapNotNull { comparison ->
            val cellWeight = mapCellWeight(comparison.rpm, comparison.petrolOnCngMs, row, column)
            observation(comparison, cellWeight)
        }
        return scan(observations)
    }

    fun curvePoint(
        comparisons: List<FuelComparisonV7>,
        index: Int,
    ): LearningStabilitySnapshotV7 {
        require(index in 0 until CalibrationShapeV7.CURVE_K_POINTS)
        val observations = comparisons.mapNotNull { comparison ->
            val (lower, upper, upperFraction) = KFactorProtocol.blendAxis(comparison.petrolTargetMs)
            val weight = when (index) {
                lower -> if (upper == lower) 1.0 else 1.0 - upperFraction
                upper -> upperFraction
                else -> 0.0
            }
            observation(comparison, weight)
        }
        return scan(observations)
    }

    fun mapGrid(comparisons: List<FuelComparisonV7>): Map<String, LearningStabilitySnapshotV7> = buildMap {
        repeat(CalibrationShapeV7.MAP_K_EDITABLE_ROWS) { row ->
            repeat(CalibrationShapeV7.MAP_K_COLUMNS) { column ->
                put("$row:$column", mapCell(comparisons, row, column))
            }
        }
    }

    fun curve(comparisons: List<FuelComparisonV7>): List<LearningStabilitySnapshotV7> =
        List(CalibrationShapeV7.CURVE_K_POINTS) { index -> curvePoint(comparisons, index) }

    private fun observation(comparison: FuelComparisonV7, contribution: Double): Observation? {
        val effective = comparison.quality.coerceIn(0.0, 1.0) * contribution.coerceIn(0.0, 1.0)
        if (effective <= 1e-9 || !comparison.errorPercent.isFinite() || !comparison.differenceMs.isFinite()) return null
        return Observation(
            visitId = comparison.cngVisitId,
            collectedAtMs = comparison.createdAtMs,
            errorPercent = comparison.errorPercent,
            differenceMs = comparison.differenceMs,
            weight = effective,
            rpm = comparison.rpm,
            mapBar = comparison.mapBar,
        )
    }

    private fun scan(source: List<Observation>): LearningStabilitySnapshotV7 {
        val tolerance = LearningToleranceSettings.current
        val ordered = source
            .sortedWith(compareBy<Observation> { it.collectedAtMs }.thenBy { it.visitId })
            .distinctBy { it.visitId }
        if (ordered.isEmpty()) return emptySnapshot()

        val learning = mutableListOf<Observation>()
        var consolidated = mutableListOf<Observation>()
        var candidate = mutableListOf<Observation>()
        var generation = 0

        ordered.forEach { item ->
            if (consolidated.isEmpty()) {
                learning += item
                val summary = summarize(learning)
                if (summary.isStableForPromotion()) {
                    consolidated = learning.toMutableList()
                    learning.clear()
                }
                return@forEach
            }

            val baseline = summarize(consolidated)
            val allowedDeltaPercent = max(
                tolerance.equivalenceDeadbandPercent,
                baseline.errorMadPercent * 2.0,
            )
            val agreesWithConsolidated = abs(item.errorPercent - baseline.centerErrorPercent) <= allowedDeltaPercent
            if (agreesWithConsolidated) {
                consolidated += item
                candidate.clear()
                return@forEach
            }

            candidate += item
            val recent = summarize(candidate)
            val promotedFarEnough = abs(recent.centerErrorPercent - baseline.centerErrorPercent) > allowedDeltaPercent
            if (promotedFarEnough && recent.isStableForPromotion()) {
                consolidated = candidate.toMutableList()
                candidate.clear()
                generation += 1
            }
        }

        if (consolidated.isEmpty()) {
            val learningSummary = summarize(learning)
            return LearningStabilitySnapshotV7(
                state = LearningStabilityStateV7.LEARNING,
                generation = 0,
                consolidatedErrorPercent = null,
                recentErrorPercent = learningSummary.centerErrorPercent.takeIf { learning.isNotEmpty() },
                confidence = learningSummary.confidence(),
                consolidatedEffectiveVisits = 0.0,
                recentEffectiveVisits = learningSummary.effectiveVisits,
                consolidatedUniqueVisits = 0,
                recentUniqueVisits = learningSummary.uniqueVisits,
                rpmBandCount = learningSummary.rpmBands,
                mapBandCount = learningSummary.mapBands,
                direction = learningSummary.direction,
                reason = "Aprendendo: evidência ainda não atingiu repetibilidade suficiente para consolidar.",
            )
        }

        val stable = summarize(consolidated)
        val recent = summarize(candidate)
        val state = if (candidate.isEmpty()) {
            LearningStabilityStateV7.CONSOLIDATED
        } else {
            LearningStabilityStateV7.REVALIDATING
        }
        return LearningStabilitySnapshotV7(
            state = state,
            generation = generation,
            consolidatedErrorPercent = stable.centerErrorPercent,
            recentErrorPercent = recent.centerErrorPercent.takeIf { candidate.isNotEmpty() },
            confidence = stable.confidence(),
            consolidatedEffectiveVisits = stable.effectiveVisits,
            recentEffectiveVisits = recent.effectiveVisits,
            consolidatedUniqueVisits = stable.uniqueVisits,
            recentUniqueVisits = recent.uniqueVisits,
            rpmBandCount = stable.rpmBands,
            mapBandCount = stable.mapBands,
            direction = stable.direction,
            reason = if (state == LearningStabilityStateV7.CONSOLIDATED) {
                "Consolidado: novas evidências compatíveis reforçam a memória sem deslocá-la por ruído isolado."
            } else {
                "Revalidando: existe uma tendência recente diferente, mas o valor consolidado permanece até a mudança ser repetível."
            },
        )
    }

    private fun Summary.isStableForPromotion(): Boolean {
        val tolerance = LearningToleranceSettings.current
        return effectiveVisits >= tolerance.confirmedVisits.toDouble() &&
            directionConsensus >= tolerance.directionConsensusMinimum &&
            differenceMadMs <= tolerance.comparisonMaximumMadMs
    }

    private fun Summary.confidence(): Double {
        val tolerance = LearningToleranceSettings.current
        val evidence = (effectiveVisits / tolerance.confirmedVisits.toDouble().coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        val spread = (1.0 - differenceMadMs / tolerance.comparisonMaximumMadMs.coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        return (sqrt(evidence) * directionConsensus.coerceIn(0.0, 1.0) * (0.5 + 0.5 * spread)).coerceIn(0.0, 1.0)
    }

    private fun summarize(values: List<Observation>): Summary {
        if (values.isEmpty()) {
            return Summary(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, "NO_EVIDENCE", 0, 0)
        }
        val weighted = values.map { it.errorPercent to it.weight }
        val centerError = weightedMedian(weighted)
        val centerDifference = weightedMedian(values.map { it.differenceMs to it.weight })
        val errorMad = weightedMedian(values.map { abs(it.errorPercent - centerError) to it.weight })
        val differenceMad = weightedMedian(values.map { abs(it.differenceMs - centerDifference) to it.weight })
        val dominant = direction(centerDifference, centerError)
        val totalWeight = values.sumOf { it.weight }.coerceAtLeast(1e-9)
        val directionWeight = values
            .filter { direction(it.differenceMs, it.errorPercent) == dominant }
            .sumOf { it.weight }
        return Summary(
            centerErrorPercent = centerError,
            centerDifferenceMs = centerDifference,
            errorMadPercent = errorMad,
            differenceMadMs = differenceMad,
            effectiveVisits = totalWeight,
            uniqueVisits = values.map { it.visitId }.distinct().size,
            directionConsensus = directionWeight / totalWeight,
            direction = dominant,
            rpmBands = values.map { nearestRpmBand(it.rpm) }.distinct().size,
            mapBands = values.map { (it.mapBar * 10.0).roundToInt() }.distinct().size,
        )
    }

    private fun direction(differenceMs: Double, errorPercent: Double): String {
        val tolerance = LearningToleranceSettings.current
        return when {
            abs(differenceMs) <= tolerance.equivalenceDeadbandMs ||
                abs(errorPercent) <= tolerance.equivalenceDeadbandPercent -> "EQUIVALENT"
            differenceMs > 0.0 -> "INCREASE_CNG_DELIVERY"
            else -> "DECREASE_CNG_DELIVERY"
        }
    }

    private fun weightedMedian(values: List<Pair<Double, Double>>): Double {
        val sorted = values
            .filter { it.first.isFinite() && it.second > 0.0 }
            .sortedBy { it.first }
        if (sorted.isEmpty()) return 0.0
        val total = sorted.sumOf { it.second }
        var running = 0.0
        sorted.forEach { (value, weight) ->
            running += weight
            if (running >= total / 2.0) return value
        }
        return sorted.last().first
    }

    private data class AxisBlend(val lower: Int, val upper: Int, val fraction: Double)

    private fun mapCellWeight(rpm: Double, petrolMs: Double, row: Int, column: Int): Double {
        val x = blend(KMapPhysicalAxes.rpmBins().map(Int::toDouble).toDoubleArray(), rpm)
        val y = blend(KMapPhysicalAxes.petrolBins(), petrolMs)
        val xWeight = when (column) {
            x.lower -> if (x.lower == x.upper) 1.0 else 1.0 - x.fraction
            x.upper -> x.fraction
            else -> 0.0
        }
        val yWeight = when (row) {
            y.lower -> if (y.lower == y.upper) 1.0 else 1.0 - y.fraction
            y.upper -> y.fraction
            else -> 0.0
        }
        return (xWeight * yWeight).coerceIn(0.0, 1.0)
    }

    private fun blend(values: DoubleArray, value: Double): AxisBlend {
        require(values.isNotEmpty())
        if (values.size == 1 || value <= values.first()) return AxisBlend(0, 0, 0.0)
        if (value >= values.last()) return AxisBlend(values.lastIndex, values.lastIndex, 0.0)
        val upper = values.indexOfFirst { it >= value }.coerceAtLeast(1)
        val lower = upper - 1
        val span = values[upper] - values[lower]
        val fraction = if (span <= 0.0) 0.0 else ((value - values[lower]) / span).coerceIn(0.0, 1.0)
        return AxisBlend(lower, upper, fraction)
    }

    private fun nearestRpmBand(rpm: Double): Int {
        val values = KMapPhysicalAxes.rpmBins()
        var best = 0
        var distance = Double.POSITIVE_INFINITY
        values.forEachIndexed { index, value ->
            val current = abs(value - rpm)
            if (current < distance) {
                distance = current
                best = index
            }
        }
        return best
    }

    private fun emptySnapshot(): LearningStabilitySnapshotV7 = LearningStabilitySnapshotV7(
        state = LearningStabilityStateV7.NO_EVIDENCE,
        generation = 0,
        consolidatedErrorPercent = null,
        recentErrorPercent = null,
        confidence = 0.0,
        consolidatedEffectiveVisits = 0.0,
        recentEffectiveVisits = 0.0,
        consolidatedUniqueVisits = 0,
        recentUniqueVisits = 0,
        rpmBandCount = 0,
        mapBandCount = 0,
        direction = "NO_EVIDENCE",
        reason = "Sem evidência comparável para esta região.",
    )
}
