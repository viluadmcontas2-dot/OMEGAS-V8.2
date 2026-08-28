package com.omegas.prohub.autocal

import com.omegas.prohub.learning.ContinuousLearningMath
import com.omegas.prohub.learning.LearningToleranceSettings
import com.omegas.prohub.learning.VisitConfidence
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Remove a tendência global da reconstrução inferida V6 antes de projetar o residual no
 * Mapa K. Produz diagnóstico para revisão, nunca valor de escrita.
 */
object AutoMatchResidualPlanner {
    fun analyze(autoMatch: JSONObject, learningExport: JSONObject): JSONObject {
        val global = GlobalTrend.from(autoMatch)
            ?: return unavailable("A reconstrução V5 precisa conter fator atual e calculado nos 30 pontos")
        val comparisons = learningExport.optJSONArray("comparisons") ?: JSONArray()
        val currentEpoch = learningExport.optInt("epoch").takeIf { learningExport.has("epoch") }
        val cells = linkedMapOf<String, CellAccumulator>()
        var acceptedComparisons = 0

        repeat(comparisons.length()) { index ->
            val raw = comparisons.optJSONObject(index) ?: return@repeat
            if (currentEpoch != null && raw.has("epoch") && raw.optInt("epoch") != currentEpoch) return@repeat
            val targetMs = raw.optDouble("petrol_target_ms", raw.optDouble("petrolTargetMs", Double.NaN))
            val observedMs = raw.optDouble("petrol_on_cng_ms", raw.optDouble("petrolOnCngMs", Double.NaN))
            val rpm = raw.optDouble("rpm", Double.NaN)
            val quality = raw.optDouble("quality", 0.1).coerceIn(0.02, 1.0)
            if (!targetMs.isFinite() || !observedMs.isFinite() || !rpm.isFinite() || targetMs <= 0.05) {
                return@repeat
            }
            val actualError = (observedMs - targetMs) / targetMs
            if (!actualError.isFinite()) return@repeat
            val globalCorrection = global.estimate(targetMs)
            val residual = actualError - globalCorrection
            val visitId = raw.optString("visit_id", raw.optString("visitId", raw.optString("id")))
                .ifBlank { "legacy-${raw.toString().hashCode()}" }
            val weights = parseWeights(raw).ifEmpty {
                ContinuousLearningMath.bilinearWeights(rpm, observedMs)
                    .map { CellWeight(it.row, it.column, it.weight) }
            }
            weights.forEach { contribution ->
                if (contribution.row !in 0..11 || contribution.column !in 0..11 || contribution.weight <= 0.0) {
                    return@forEach
                }
                val key = "${contribution.row}:${contribution.column}"
                cells.getOrPut(key) { CellAccumulator(contribution.row, contribution.column) }
                    .add(
                        residual = residual,
                        actualError = actualError,
                        globalCorrection = globalCorrection,
                        weight = quality * contribution.weight,
                        visitId = visitId,
                    )
            }
            acceptedComparisons += 1
        }

        val tolerance = LearningToleranceSettings.current
        // O residual local usa no máximo 2%; tolerâncias mais largas do
        // aprendizado não podem esconder uma diferença já isolada da tendência.
        val deadbandRatio = minOf(
            (tolerance.equivalenceDeadbandPercent / 100.0).coerceAtLeast(0.002),
            0.02,
        )
        val cellJson = JSONArray()
        cells.values.sortedWith(compareBy<CellAccumulator> { it.row }.thenBy { it.column }).forEach { cell ->
            val residual = cell.residualMean() ?: return@forEach
            val confidence = VisitConfidence.evaluate(
                uniqueVisits = cell.uniqueVisits(),
                effectiveVisits = cell.effectiveSamples(),
                spread = cell.residualSpread() ?: deadbandRatio,
                spreadLimit = deadbandRatio,
                consensus = cell.consensus(),
                provisionalVisits = tolerance.provisionalVisits,
                acceptedVisits = tolerance.acceptedVisits,
                confirmedVisits = tolerance.confirmedVisits,
            )
            cellJson.put(JSONObject()
                .put("row", cell.row)
                .put("column", cell.column)
                .put("actualErrorPercent", cell.actualMean()?.times(100.0) ?: JSONObject.NULL)
                .put("globalCorrectionPercent", cell.globalMean()?.times(100.0) ?: JSONObject.NULL)
                .put("residualPercent", residual * 100.0)
                .put("direction", direction(residual, deadbandRatio))
                .put("uniqueVisits", confidence.uniqueVisits)
                .put("effectiveVisits", confidence.effectiveVisits)
                .put("confidence", confidence.confidence)
                .put("confidenceStage", confidence.stage)
                .put("repeatability", confidence.repeatability)
                .put("consensus", confidence.consensus)
                .put("actionableForReview", confidence.confidence >= 0.55 && abs(residual) >= deadbandRatio)
                .put("automatic", false)
                .put("manualOnly", true)
                .put("requiresReview", true))
        }

        return JSONObject()
            .put("ok", true)
            .put("available", true)
            .put("mode", "AUTOMATCH_GLOBAL_THEN_MAP_RESIDUAL")
            .put("globalTrendRemoved", true)
            .put("dampingApplied", false)
            .put("automatic", false)
            .put("manualOnly", true)
            .put("requiresReview", true)
            .put("comparisonCount", acceptedComparisons)
            .put("cellCount", cellJson.length())
            .put("globalTrend", global.toJson())
            .put("cells", cellJson)
            .put("message", if (cellJson.length() == 0) {
                "Ainda não existem comparações gasolina/GNV válidas para calcular o residual"
            } else {
                "Tendência global AutoMatch removida; o restante é apenas candidato manual ao Mapa K"
            })
    }

    private fun parseWeights(raw: JSONObject): List<CellWeight> {
        val source = raw.optJSONArray("continuous_cell_weights")
            ?: raw.optJSONArray("continuousCellWeights")
            ?: return emptyList()
        return buildList {
            repeat(source.length()) { index ->
                val item = source.optJSONObject(index) ?: return@repeat
                add(CellWeight(
                    row = item.optInt("row", -1),
                    column = item.optInt("column", -1),
                    weight = item.optDouble("weight", 0.0),
                ))
            }
        }
    }

    private fun direction(value: Double, deadband: Double): String = when {
        abs(value) < (deadband * 0.999).coerceAtLeast(0.0) -> "EQUIVALENT_AFTER_GLOBAL"
        value > 0.0 -> "INCREASE_LOCAL_CNG_DELIVERY"
        else -> "DECREASE_LOCAL_CNG_DELIVERY"
    }

    private fun unavailable(message: String): JSONObject = JSONObject()
        .put("ok", true)
        .put("available", false)
        .put("mode", "AUTOMATCH_GLOBAL_THEN_MAP_RESIDUAL")
        .put("globalTrendRemoved", false)
        .put("dampingApplied", false)
        .put("automatic", false)
        .put("manualOnly", true)
        .put("requiresReview", true)
        .put("message", message)
        .put("cells", JSONArray())

    private data class CellWeight(val row: Int, val column: Int, val weight: Double)

    private class CellAccumulator(val row: Int, val column: Int) {
        private var weight = 0.0
        private var weightSquareSum = 0.0
        private var residualSum = 0.0
        private var residualSquareSum = 0.0
        private var actualSum = 0.0
        private var globalSum = 0.0
        private var positiveWeight = 0.0
        private var negativeWeight = 0.0
        private val visits = linkedSetOf<String>()

        fun add(
            residual: Double,
            actualError: Double,
            globalCorrection: Double,
            weight: Double,
            visitId: String,
        ) {
            if (!residual.isFinite() || weight <= 0.0 || !weight.isFinite()) return
            this.weight += weight
            weightSquareSum += weight * weight
            residualSum += residual * weight
            residualSquareSum += residual * residual * weight
            actualSum += actualError * weight
            globalSum += globalCorrection * weight
            if (residual > 0.0) positiveWeight += weight
            if (residual < 0.0) negativeWeight += weight
            visits += visitId
        }

        fun residualMean(): Double? = if (weight <= 0.0) null else residualSum / weight
        fun actualMean(): Double? = if (weight <= 0.0) null else actualSum / weight
        fun globalMean(): Double? = if (weight <= 0.0) null else globalSum / weight
        fun residualSpread(): Double? {
            val mean = residualMean() ?: return null
            return sqrt(max(0.0, residualSquareSum / weight - mean * mean))
        }
        fun effectiveSamples(): Double = if (weightSquareSum <= 0.0) 0.0 else weight * weight / weightSquareSum
        fun uniqueVisits(): Int = visits.size
        fun consensus(): Double {
            val directed = positiveWeight + negativeWeight
            return if (directed <= 0.0) 1.0 else max(positiveWeight, negativeWeight) / directed
        }
    }

    private class GlobalTrend(private val points: List<Point>) {
        data class Point(val petrolMs: Double, val correctionRatio: Double)

        fun estimate(petrolMs: Double): Double {
            if (petrolMs <= points.first().petrolMs) return points.first().correctionRatio
            if (petrolMs >= points.last().petrolMs) return points.last().correctionRatio
            val upperIndex = points.indexOfFirst { it.petrolMs >= petrolMs }.coerceAtLeast(1)
            val lower = points[upperIndex - 1]
            val upper = points[upperIndex]
            val fraction = ((petrolMs - lower.petrolMs) / (upper.petrolMs - lower.petrolMs))
                .coerceIn(0.0, 1.0)
            return lower.correctionRatio + (upper.correctionRatio - lower.correctionRatio) * fraction
        }

        fun toJson(): JSONArray = JSONArray(points.map { point ->
            JSONObject()
                .put("petrolMs", point.petrolMs)
                .put("correctionPercent", point.correctionRatio * 100.0)
        })

        companion object {
            fun from(analysis: JSONObject): GlobalTrend? {
                if (!analysis.optBoolean("ok") || !analysis.optBoolean("available")) return null
                val raw = analysis.optJSONArray("points") ?: return null
                if (raw.length() != 30) return null
                val points = buildList {
                    repeat(raw.length()) { index ->
                        val item = raw.optJSONObject(index) ?: return null
                        val petrolMs = item.optDouble("referenceTimeMs", Double.NaN)
                        val current = item.optDouble("currentFactor", Double.NaN)
                        val calculated = item.optDouble("calculatedFactor", Double.NaN)
                        if (!petrolMs.isFinite() || !current.isFinite() || !calculated.isFinite() || current <= 0.0) {
                            return null
                        }
                        add(Point(petrolMs, calculated / current - 1.0))
                    }
                }
                if (points.zipWithNext().any { (a, b) -> b.petrolMs <= a.petrolMs }) return null
                return GlobalTrend(points)
            }
        }
    }
}
