package com.omegas.prohub.learning

import com.omegas.prohub.calibration.KWriteManager
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Etapa espacial conservativa do Predictor.
 *
 * A lista de suporte é congelada antes da primeira previsão e contém somente
 * evidência direta. Células PREVISTO nunca voltam como entrada científica.
 */
object PredictorInterpolator {
    fun build(
        learningSnapshot: JSONObject,
        confirmedMapSnapshot: JSONObject? = null,
    ): JSONObject {
        val direct = PredictorSurface.build(learningSnapshot, confirmedMapSnapshot)
        val epoch = direct.optInt("epoch", 1).coerceAtLeast(1)
        val cells = direct.optJSONArray("cells") ?: JSONArray()
        val trajectoriesByCell = trajectoriesByCell(learningSnapshot, epoch)

        // Congelado antes de qualquer mutação da superfície: previsão não se autoalimenta.
        val directSupport = buildDirectSupport(cells, trajectoriesByCell)
        val predicted = JSONArray()
        val output = JSONArray()

        repeat(cells.length()) { index ->
            val source = cells.optJSONObject(index) ?: return@repeat
            val cell = JSONObject(source.toString())
            if (cell.optString("state") != PredictorSurface.CellState.DESCONHECIDO.name ||
                cell.opt("currentK") !is Number
            ) {
                output.put(cell)
                return@repeat
            }

            val targetRpm = cell.optDouble("rpm", Double.NaN)
            val targetPetrolMs = cell.optDouble("petrolMs", Double.NaN)
            val confidence = PredictorSpatialConfidence.evaluate(targetRpm, targetPetrolMs, directSupport)
            if (!confidence.supported) {
                cell.put("predictionReason", confidence.reason)
                    .put("predictionConfidence", 0.0)
                    .put("predicted", false)
                output.put(cell)
                return@repeat
            }

            val estimate = estimateTargetK(
                targetRpm = targetRpm,
                targetPetrolMs = targetPetrolMs,
                support = directSupport,
                currentK = cell.optInt("currentK"),
            )
            if (!estimate.available) {
                cell.put("predictionReason", estimate.reason)
                    .put("predictionConfidence", 0.0)
                    .put("predicted", false)
                output.put(cell)
                return@repeat
            }

            val supportIds = JSONArray(estimate.supportIds.take(16))
            cell.put("state", PredictorSurface.CellState.PREVISTO.name)
                .put("stateReason", "K alvo interpolado somente de evidência direta independente")
                .put("targetK", estimate.targetK)
                .put("confidence", confidence.confidence)
                .put("predictionConfidence", confidence.confidence)
                .put("predictionReason", confidence.reason)
                .put("predicted", true)
                .put("directObservation", false)
                .put("supportCount", confidence.supportCount)
                .put("distinctTrajectories", confidence.distinctTrajectories)
                .put("physicalDistanceScore", confidence.physicalDistanceScore)
                .put("densityScore", confidence.densityScore)
                .put("qualityScore", confidence.qualityScore)
                .put("coherenceScore", confidence.coherenceScore)
                .put("independenceScore", confidence.independenceScore)
                .put("extrapolationPenalty", confidence.extrapolationPenalty)
                .put("predictionSupportIds", supportIds)
                .put("automaticWrite", false)
            predicted.put(JSONObject(cell.toString()))
            output.put(cell)
        }

        val counts = JSONObject()
        PredictorSurface.CellState.entries.forEach { state -> counts.put(state.name, 0) }
        repeat(output.length()) { index ->
            val state = output.optJSONObject(index)?.optString("state").orEmpty()
            if (counts.has(state)) counts.put(state, counts.optInt(state) + 1)
        }

        return JSONObject(direct.toString())
            .put("cells", output)
            .put("stateCounts", counts)
            .put("predictedCells", predicted.length())
            .put("interpolation", JSONObject()
                .put("mode", "DIRECT_EVIDENCE_SINGLE_PASS")
                .put("supportFrozenBeforePrediction", true)
                .put("predictionsFeedConfidence", false)
                .put("physicalGeometry", true)
                .put("trajectoryIndependence", true)
                .put("extrapolationAllowed", false)
                .put("automaticWrite", false))
    }

    private fun buildDirectSupport(
        cells: JSONArray,
        trajectoriesByCell: Map<String, Set<String>>,
    ): List<PredictorSpatialConfidence.SupportPoint> {
        val support = mutableListOf<PredictorSpatialConfidence.SupportPoint>()
        repeat(cells.length()) { index ->
            val cell = cells.optJSONObject(index) ?: return@repeat
            if (cell.optBoolean("predicted", false)) return@repeat
            if (!cell.optBoolean("directObservation", false)) return@repeat
            val targetK = cell.nullableDouble("targetK") ?: return@repeat
            val rpm = cell.optDouble("rpm", Double.NaN)
            val petrolMs = cell.optDouble("petrolMs", Double.NaN)
            if (!rpm.isFinite() || !petrolMs.isFinite()) return@repeat
            val cellKey = cell.optString("key")
            val trajectories = trajectoriesByCell[cellKey].orEmpty()
            if (trajectories.isEmpty()) return@repeat
            val quality = supportQuality(cell)
            if (quality <= 0.0) return@repeat
            trajectories.take(8).forEach { trajectoryId ->
                support += PredictorSpatialConfidence.SupportPoint(
                    id = "$cellKey:$trajectoryId",
                    rpm = rpm,
                    petrolMs = petrolMs,
                    targetK = targetK,
                    quality = quality,
                    trajectoryId = trajectoryId,
                )
            }
        }
        return support
    }

    private fun supportQuality(cell: JSONObject): Double {
        val residualConfidence = cell.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
        var nativeConfidence: Double? = null
        val provenance = cell.optJSONArray("provenance") ?: JSONArray()
        repeat(provenance.length()) { index ->
            val item = provenance.optJSONObject(index) ?: return@repeat
            if (item.optString("source") == "ECU_NATIVE_AUTOCAL") {
                val value = item.optDouble("correlationConfidence", 0.0).coerceIn(0.0, 1.0)
                nativeConfidence = max(nativeConfidence ?: 0.0, value)
            }
        }
        // Âncora nativa qualifica o suporte, mas nunca substitui a qualidade do residual.
        return nativeConfidence?.let { kotlin.math.sqrt(residualConfidence * it) } ?: residualConfidence
    }

    private fun trajectoriesByCell(learningSnapshot: JSONObject, epoch: Int): Map<String, Set<String>> {
        val result = linkedMapOf<String, MutableSet<String>>()
        val comparisons = learningSnapshot.optJSONArray("comparisons") ?: JSONArray()
        repeat(comparisons.length()) { index ->
            val comparison = comparisons.optJSONObject(index) ?: return@repeat
            if (comparison.has("epoch") && comparison.optInt("epoch") != epoch) return@repeat
            val visitId = comparison.optString("visit_id", comparison.optString("visitId", comparison.optString("id")))
            if (visitId.isBlank()) return@repeat
            val weights = comparison.optJSONArray("continuous_cell_weights")
                ?: comparison.optJSONArray("continuousCellWeights")
            if (weights != null && weights.length() > 0) {
                repeat(weights.length()) weightLoop@ { weightIndex ->
                    val item = weights.optJSONObject(weightIndex) ?: return@weightLoop
                    if (item.optDouble("weight", 0.0) <= 0.0) return@weightLoop
                    val row = item.optInt("row", -1)
                    val column = item.optInt("column", -1)
                    if (row >= 0 && column >= 0) result.getOrPut("$row:$column") { linkedSetOf() }.add(visitId)
                }
            } else {
                val rpm = comparison.optDouble("rpm", Double.NaN)
                val petrolMs = comparison.optDouble("petrol_on_cng_ms", comparison.optDouble("petrolOnCngMs", Double.NaN))
                if (!rpm.isFinite() || !petrolMs.isFinite()) return@repeat
                ContinuousLearningMath.bilinearWeights(rpm, petrolMs).forEach { weight ->
                    if (weight.weight > 0.0) result.getOrPut("${weight.row}:${weight.column}") { linkedSetOf() }.add(visitId)
                }
            }
        }
        return result
    }

    private fun estimateTargetK(
        targetRpm: Double,
        targetPetrolMs: Double,
        support: List<PredictorSpatialConfidence.SupportPoint>,
        currentK: Int,
    ): TargetEstimate {
        if (support.isEmpty()) return TargetEstimate(false, currentK, "NO_SUPPORT", emptyList())
        data class Contribution(val point: PredictorSpatialConfidence.SupportPoint, val weight: Double)
        val contributions = support.map { point ->
            val distance = PredictorSpatialConfidence.physicalDistance(targetRpm, targetPetrolMs, point.rpm, point.petrolMs)
            Contribution(point, point.quality / (1.0 + distance))
        }
        val byTrajectory = contributions.groupBy { it.point.trajectoryId }
        val trajectoryEstimates = byTrajectory.mapNotNull { (trajectoryId, items) ->
            val total = items.sumOf { it.weight }.coerceAtLeast(1e-12)
            val estimate = items.sumOf { it.point.targetK * it.weight } / total
            val quality = items.maxOf { it.point.quality / (1.0 + PredictorSpatialConfidence.physicalDistance(targetRpm, targetPetrolMs, it.point.rpm, it.point.petrolMs)) }
            if (!estimate.isFinite() || quality <= 0.0) null else TrajectoryEstimate(trajectoryId, estimate, quality)
        }
        if (trajectoryEstimates.size < 2) return TargetEstimate(false, currentK, "INSUFFICIENT_TRAJECTORY_INDEPENDENCE", emptyList())

        val hasIncrease = trajectoryEstimates.any { it.targetK > currentK.toDouble() }
        val hasDecrease = trajectoryEstimates.any { it.targetK < currentK.toDouble() }
        if (hasIncrease && hasDecrease) {
            return TargetEstimate(false, currentK, "DIRECTION_CONFLICT", trajectoryEstimates.map { it.id })
        }
        val total = trajectoryEstimates.sumOf { it.weight }.coerceAtLeast(1e-12)
        val target = (trajectoryEstimates.sumOf { it.targetK * it.weight } / total)
            .toInt()
            .coerceIn(KWriteManager.MIN_ALLOWED_K, KWriteManager.MAX_ALLOWED_K)
        return TargetEstimate(true, target, "DIRECT_SUPPORT_INTERPOLATION", trajectoryEstimates.map { it.id })
    }

    private data class TrajectoryEstimate(val id: String, val targetK: Double, val weight: Double)
    private data class TargetEstimate(
        val available: Boolean,
        val targetK: Int,
        val reason: String,
        val supportIds: List<String>,
    )

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null
}
