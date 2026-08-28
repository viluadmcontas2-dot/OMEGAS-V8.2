package com.omegas.prohub.learning

import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Converte comparações gasolina/GNV em propostas manuais de duas camadas:
 *
 * 1. tendência contínua por tempo de injeção para a curva K factor;
 * 2. erro residual contínuo por RPM × tempo de injeção para o mapa K.
 *
 * Não existe meta fixa de sessões, visitas ou regiões. Cada amostra válida reduz
 * a incerteza da área que realmente observou. Uma proposta nasce quando o erro
 * útil supera a incerteza e a faixa neutra configurável, sem qualquer acesso ao
 * USB ou writer.
 */
object AssistedCalibrationAdvisor {
    private const val MAP_BANDWIDTH_BAR = 0.060
    private const val BASE_PRIOR_UNCERTAINTY_RATIO = 0.060
    private const val WEIGHT_UNCERTAINTY_RATIO = 0.030
    private const val MIN_CORRECTION_FRACTION = 0.45
    private const val MAX_CORRECTION_FRACTION = 0.90
    private val mapKnots = DoubleArray(18) { 0.20 + it * 0.05 }

    fun analyze(exportedLearning: JSONObject): JSONObject {
        val reconciled = LearningSnapshotReconciler.reconcile(exportedLearning)
        val comparisons = reconciled.optJSONArray("comparisons") ?: JSONArray()
        val currentEpoch = reconciled.optInt("epoch").takeIf { reconciled.has("epoch") }
        val samples = mutableListOf<ComparisonSample>()
        repeat(comparisons.length()) { index ->
            val raw = comparisons.optJSONObject(index) ?: return@repeat
            if (currentEpoch != null && raw.has("epoch") && raw.optInt("epoch") != currentEpoch) return@repeat
            parse(raw)?.let(samples::add)
        }
        if (samples.isEmpty()) {
            return emptyResult()
                .put("reconciliation", reconciled.optJSONObject("reconciliation") ?: JSONObject())
        }

        val pairedCurves = pairedCurves(samples)
        val global = globalCurve(samples)
        val residual = residualMap(samples, global)
        val regions = mapCorrectionRegions(residual)
        return JSONObject()
            .put("ok", true)
            .put("mode", "CONTINUOUS_ADAPTIVE_MANUAL")
            .put("automatic", false)
            .put("humanConfirmationRequired", true)
            .put("comparisonCount", samples.size)
            .put("uniqueVisitCount", samples.map { it.visitId }.toSet().size)
            .put("petrolCurve", pairedCurves.petrol)
            .put("cngCurve", pairedCurves.cng)
            .put("curveAxes", JSONObject()
                .put("horizontal", "PETROL_INJECTION_MS")
                .put("vertical", "MAP_BAR")
                .put("comparison", "HORIZONTAL_AT_SAME_MAP"))
            .put("kFactorAxisMs", JSONArray(KFactorProtocol.OBSERVED_PETROL_AXIS_MS.toList()))
            .put("kFactorSuggestions", global.toJson())
            .put("mapResidualSuggestions", residual)
            .put("mapCorrectionRegions", regions)
            .put("reconciliation", reconciled.optJSONObject("reconciliation") ?: JSONObject())
            .put("method", JSONObject()
                .put("global", "continuous-same-map-error-surface")
                .put("residual", "bilinear-rpm-petrol-after-supported-global-removal")
                .put("decision", "useful-margin-error-minus-uncertainty-minus-deadband")
                .put("confidence", "continuous-weight-repeatability-and-uncertainty")
                .put("fixedBands", false)
                .put("fixedSampleThreshold", false)
                .put("partialCoverageActionable", true)
                .put("sessionGating", false)
                .put("validEvidenceDiscarded", false)
                .put("correlatedEvidenceWeighted", true)
                .put("automaticWrite", false)
                .put("correctionFractionMinimum", MIN_CORRECTION_FRACTION)
                .put("correctionFractionMaximum", MAX_CORRECTION_FRACTION))
    }

    private fun pairedCurves(samples: List<ComparisonSample>): PairedCurves {
        val buckets = Array(mapKnots.size) { PairedCurveBucket() }
        samples.forEach { sample ->
            mapKnots.forEachIndexed { index, knot ->
                val localWeight = sample.weight * gaussian(sample.mapBar - knot, MAP_BANDWIDTH_BAR)
                buckets[index].add(sample.petrolTargetMs, sample.petrolObservedMs, localWeight, sample.visitId)
            }
        }
        val petrol = JSONArray()
        val cng = JSONArray()
        buckets.forEachIndexed { index, bucket ->
            val mapBar = mapKnots[index]
            petrol.put(JSONObject()
                .put("mapBar", mapBar)
                .put("petrolMs", bucket.targetMeanOrNull() ?: JSONObject.NULL)
                .put("confidence", bucket.confidence())
                .put("confidenceStage", bucket.confidenceStage())
                .put("readiness", bucket.readiness())
                .put("uniqueVisits", bucket.uniqueVisits())
                .put("effectiveSamples", bucket.effectiveSamples())
                .put("spreadMs", bucket.targetSpreadOrNull() ?: JSONObject.NULL)
                .put("series", "PETROL")
                .put("marker", "CIRCLE"))
            cng.put(JSONObject()
                .put("mapBar", mapBar)
                .put("petrolMs", bucket.observedMeanOrNull() ?: JSONObject.NULL)
                .put("confidence", bucket.confidence())
                .put("confidenceStage", bucket.confidenceStage())
                .put("readiness", bucket.readiness())
                .put("uniqueVisits", bucket.uniqueVisits())
                .put("effectiveSamples", bucket.effectiveSamples())
                .put("spreadMs", bucket.observedSpreadOrNull() ?: JSONObject.NULL)
                .put("series", "CNG")
                .put("marker", "SQUARE"))
        }
        return PairedCurves(petrol, cng)
    }

    private fun globalCurve(samples: List<ComparisonSample>): GlobalCurve {
        val buckets = Array(KFactorProtocol.POINT_COUNT) { WeightedBucket() }
        samples.forEach { sample ->
            val (lower, upper, upperFraction) = KFactorProtocol.blendAxis(sample.petrolTargetMs)
            val lowerFraction = 1.0 - upperFraction
            buckets[lower].add(
                sample.errorRatio,
                sample.weight * lowerFraction,
                sample.rpm,
                sample.visitId,
                sample.upstreamUncertaintyFraction,
            )
            if (upper != lower) {
                buckets[upper].add(
                    sample.errorRatio,
                    sample.weight * upperFraction,
                    sample.rpm,
                    sample.visitId,
                    sample.upstreamUncertaintyFraction,
                )
            }
        }
        return GlobalCurve(buckets.mapIndexed { index, bucket ->
            val estimate = bucket.meanOrNull()
            val decision = bucket.decision(estimate)
            GlobalPoint(
                index = index,
                petrolMs = KFactorProtocol.OBSERVED_PETROL_AXIS_MS[index],
                errorRatio = estimate,
                decision = decision,
                effectiveWeight = bucket.weight,
                effectiveSamples = bucket.effectiveSamples(),
                uniqueVisits = bucket.uniqueVisits(),
                rpmCoverage = bucket.rpmCoverage(),
                spread = bucket.spreadOrNull(),
            )
        })
    }

    private fun residualMap(samples: List<ComparisonSample>, global: GlobalCurve): JSONArray {
        val cells = linkedMapOf<String, ResidualBucket>()
        samples.forEach { sample ->
            val globalEstimate = global.estimate(sample.petrolTargetMs)
            val residual = sample.errorRatio - if (globalEstimate.available) globalEstimate.value else 0.0
            val contributions = sample.continuousWeights.ifEmpty {
                ContinuousLearningMath.bilinearWeights(sample.rpm, sample.petrolObservedMs)
                    .map { CellWeight(it.row, it.column, it.weight) }
            }
            contributions.forEach contributionLoop@ { contribution ->
                if (contribution.row !in 0..11 || contribution.column !in 0..11 || contribution.weight <= 0.0) {
                    return@contributionLoop
                }
                val key = "${contribution.row}:${contribution.column}"
                cells.getOrPut(key) { ResidualBucket(contribution.row, contribution.column) }
                    .add(
                        value = residual,
                        addedWeight = sample.weight * contribution.weight,
                        rpm = sample.rpm,
                        mapBar = sample.mapBar,
                        visitId = sample.visitId,
                        upstreamUncertaintyFraction = sample.upstreamUncertaintyFraction,
                        removedGlobal = globalEstimate.available,
                        // Bounded rows and the global trend share the same upstream evidence.
                        // Re-adding global uncertainty would count correlated measurement noise twice.
                        globalUncertainty = if (
                            globalEstimate.available && sample.upstreamUncertaintyFraction == null
                        ) globalEstimate.uncertainty else 0.0,
                    )
            }
        }
        val result = JSONArray()
        cells.values
            .sortedWith(compareBy<ResidualBucket> { it.row }.thenBy { it.column })
            .forEach { bucket ->
                val residual = bucket.meanOrNull()
                val decision = bucket.decision(residual, bucket.averageGlobalUncertainty())
                result.put(JSONObject()
                    .put("row", bucket.row)
                    .put("column", bucket.column)
                    .put("residualErrorPercent", residual?.times(100.0) ?: JSONObject.NULL)
                    .put("suggestedDeltaPercent", decision.suggestedDeltaRatio?.times(100.0) ?: JSONObject.NULL)
                    .put("estimatedResidualAfterPercent", decision.estimatedResidualAfterRatio?.times(100.0) ?: JSONObject.NULL)
                    .put("uncertaintyPercent", decision.uncertaintyRatio.times(100.0))
                    .put("usefulMarginPercent", decision.usefulMarginRatio.times(100.0))
                    .put("correctionFraction", decision.correctionFraction ?: JSONObject.NULL)
                    .put("evidenceUtility", decision.utility)
                    .put("readiness", decision.readiness)
                    .put("decisionReason", decision.reason)
                    .put("direction", direction(residual))
                    .put("effectiveWeight", bucket.weight)
                    .put("effectiveSamples", bucket.effectiveSamples())
                    .put("uniqueVisits", bucket.uniqueVisits())
                    .put("confidenceStage", decision.compatibilityStage)
                    .put("confidence", decision.confidence)
                    .put("spreadPercent", bucket.spreadOrNull()?.times(100.0) ?: JSONObject.NULL)
                    .put("rpmMean", bucket.rpmMean())
                    .put("mapMeanBar", bucket.mapMean())
                    .put("globalTrendRemoved", bucket.globalRemovalFraction() > 0.5)
                    .put("actionable", decision.actionable)
                    .put("automatic", false)
                    .put("humanConfirmationRequired", true))
            }
        return result
    }

    private fun mapCorrectionRegions(residual: JSONArray): JSONArray {
        val cells = linkedMapOf<String, JSONObject>()
        repeat(residual.length()) { index ->
            val cell = residual.optJSONObject(index) ?: return@repeat
            if (!cell.optBoolean("actionable")) return@repeat
            val direction = cell.optString("direction")
            if (direction !in setOf("INCREASE_CNG_DELIVERY", "DECREASE_CNG_DELIVERY")) return@repeat
            cells["${cell.optInt("row")}:${cell.optInt("column")}"] = cell
        }
        val pending = cells.keys.toMutableSet()
        val regions = JSONArray()
        var regionNumber = 1
        while (pending.isNotEmpty()) {
            val start = pending.first()
            val direction = cells.getValue(start).optString("direction")
            val queue = ArrayDeque<String>()
            val members = mutableListOf<JSONObject>()
            queue.add(start)
            pending.remove(start)
            while (queue.isNotEmpty()) {
                val key = queue.removeFirst()
                val cell = cells.getValue(key)
                members += cell
                val row = cell.optInt("row")
                val column = cell.optInt("column")
                listOf(row - 1 to column, row + 1 to column, row to column - 1, row to column + 1)
                    .map { "${it.first}:${it.second}" }
                    .filter { it in pending && cells[it]?.optString("direction") == direction }
                    .forEach {
                        pending.remove(it)
                        queue.add(it)
                    }
            }
            val regionId = "MAP-${regionNumber.toString().padStart(2, '0')}"
            regionNumber += 1
            val weights = members.map { max(0.05, it.optDouble("evidenceUtility", 0.0)) }
            val totalWeight = weights.sum().coerceAtLeast(1e-9)
            val suggested = members.indices.sumOf { members[it].optDouble("suggestedDeltaPercent", 0.0) * weights[it] } / totalWeight
            val error = members.indices.sumOf { members[it].optDouble("residualErrorPercent", 0.0) * weights[it] } / totalWeight
            val uncertainty = members.indices.sumOf { members[it].optDouble("uncertaintyPercent", 0.0) * weights[it] } / totalWeight
            val utility = members.indices.sumOf { members[it].optDouble("evidenceUtility", 0.0) * weights[it] } / totalWeight
            members.forEach { it.put("regionId", regionId) }
            regions.put(JSONObject()
                .put("id", regionId)
                .put("direction", direction)
                .put("cellCount", members.size)
                .put("cells", JSONArray(members.map { JSONObject().put("row", it.optInt("row")).put("column", it.optInt("column")) }))
                .put("rowStart", members.minOf { it.optInt("row") })
                .put("rowEnd", members.maxOf { it.optInt("row") })
                .put("columnStart", members.minOf { it.optInt("column") })
                .put("columnEnd", members.maxOf { it.optInt("column") })
                .put("residualErrorPercent", error)
                .put("suggestedDeltaPercent", suggested)
                .put("uncertaintyPercent", uncertainty)
                .put("evidenceUtility", utility)
                .put("readiness", "AVAILABLE")
                .put("actionable", true)
                .put("automatic", false)
                .put("humanConfirmationRequired", true))
        }
        return regions
    }

    private fun parse(raw: JSONObject): ComparisonSample? {
        val target = raw.optDouble("petrol_target_ms", raw.optDouble("petrolTargetMs", Double.NaN))
        val observed = raw.optDouble("petrol_on_cng_ms", raw.optDouble("petrolOnCngMs", Double.NaN))
        val rpm = raw.optDouble("rpm", Double.NaN)
        val mapBar = raw.optDouble("map_bar", raw.optDouble("mapBar", Double.NaN))
        val quality = raw.optDouble("quality", 0.1).coerceIn(0.0, 1.0)
        val upstreamUncertaintyFraction = raw
            .optDouble("upstream_uncertainty_fraction", Double.NaN)
            .takeIf { it.isFinite() && it >= 0.0 }
        if (!target.isFinite() || !observed.isFinite() || !rpm.isFinite() || !mapBar.isFinite() ||
            target <= 0.05 || observed < 0.0 || rpm < 0.0 || mapBar < 0.0
        ) return null
        val ratio = (observed - target) / target
        if (!ratio.isFinite()) return null
        val weights = mutableListOf<CellWeight>()
        val rawWeights = raw.optJSONArray("continuous_cell_weights")
            ?: raw.optJSONArray("continuousCellWeights")
            ?: JSONArray()
        repeat(rawWeights.length()) { index ->
            val item = rawWeights.optJSONObject(index) ?: return@repeat
            weights += CellWeight(
                row = item.optInt("row", -1),
                column = item.optInt("column", -1),
                weight = item.optDouble("weight", 0.0),
            )
        }
        val visitId = raw.optString("visit_id", raw.optString("visitId", raw.optString("id")))
            .ifBlank { "sample-${raw.toString().hashCode()}" }
        return ComparisonSample(
            petrolTargetMs = target,
            petrolObservedMs = observed,
            errorRatio = ratio,
            rpm = rpm,
            mapBar = mapBar,
            weight = quality,
            visitId = visitId,
            upstreamUncertaintyFraction = upstreamUncertaintyFraction,
            continuousWeights = weights,
        )
    }

    private fun emptyResult(): JSONObject = JSONObject()
        .put("ok", true)
        .put("mode", "CONTINUOUS_ADAPTIVE_MANUAL")
        .put("automatic", false)
        .put("humanConfirmationRequired", true)
        .put("comparisonCount", 0)
        .put("uniqueVisitCount", 0)
        .put("petrolCurve", JSONArray())
        .put("cngCurve", JSONArray())
        .put("kFactorAxisMs", JSONArray(KFactorProtocol.OBSERVED_PETROL_AXIS_MS.toList()))
        .put("kFactorSuggestions", JSONArray())
        .put("mapResidualSuggestions", JSONArray())
        .put("mapCorrectionRegions", JSONArray())
        .put("message", "Ainda não existem comparações gasolina/GNV")

    private fun gaussian(distance: Double, bandwidth: Double): Double {
        val normalized = distance / bandwidth.coerceAtLeast(1e-6)
        return exp(-0.5 * normalized * normalized).coerceAtLeast(1e-12)
    }

    private fun direction(value: Double?): String = when {
        value == null -> "INSUFFICIENT_EVIDENCE"
        abs(value) <= configuredDeadbandRatio() -> "EQUIVALENT"
        value > 0.0 -> "INCREASE_CNG_DELIVERY"
        else -> "DECREASE_CNG_DELIVERY"
    }

    private fun configuredDeadbandRatio(): Double =
        (LearningToleranceSettings.current.equivalenceDeadbandPercent / 100.0).coerceAtLeast(0.001)

    private data class ComparisonSample(
        val petrolTargetMs: Double,
        val petrolObservedMs: Double,
        val errorRatio: Double,
        val rpm: Double,
        val mapBar: Double,
        val weight: Double,
        val visitId: String,
        val upstreamUncertaintyFraction: Double?,
        val continuousWeights: List<CellWeight>,
    )

    private data class CellWeight(val row: Int, val column: Int, val weight: Double)
    private data class PairedCurves(val petrol: JSONArray, val cng: JSONArray)
    private data class Estimate(
        val value: Double,
        val uncertainty: Double,
        val confidence: Double,
        val available: Boolean,
    )

    private data class AdaptiveDecision(
        val actionable: Boolean,
        val readiness: String,
        val reason: String,
        val uncertaintyRatio: Double,
        val usefulMarginRatio: Double,
        val correctionFraction: Double?,
        val suggestedDeltaRatio: Double?,
        val estimatedResidualAfterRatio: Double?,
        val utility: Double,
        val confidence: Double,
        val compatibilityStage: String,
    )

    private open class WeightedStats {
        var weight = 0.0
            private set
        private var weightSquareSum = 0.0
        private var sum = 0.0
        private var squareSum = 0.0
        private var upstreamVarianceWeightSum = 0.0
        private var upstreamWeight = 0.0
        private var legacyWeight = 0.0
        private val visitIds = linkedSetOf<String>()

        fun addValue(
            value: Double,
            addedWeight: Double,
            visitId: String,
            upstreamUncertaintyFraction: Double? = null,
        ) {
            if (addedWeight <= 0.0 || !addedWeight.isFinite() || !value.isFinite()) return
            weight += addedWeight
            weightSquareSum += addedWeight * addedWeight
            sum += value * addedWeight
            squareSum += value * value * addedWeight
            if (upstreamUncertaintyFraction != null &&
                upstreamUncertaintyFraction.isFinite() &&
                upstreamUncertaintyFraction >= 0.0
            ) {
                upstreamVarianceWeightSum += upstreamUncertaintyFraction * upstreamUncertaintyFraction * addedWeight
                upstreamWeight += addedWeight
            } else {
                legacyWeight += addedWeight
            }
            visitIds += visitId
        }

        fun meanOrNull(): Double? = if (weight <= 0.0) null else sum / weight

        fun spreadOrNull(): Double? {
            val mean = meanOrNull() ?: return null
            return sqrt(max(0.0, squareSum / weight - mean * mean))
        }

        fun effectiveSamples(): Double = if (weightSquareSum <= 0.0) 0.0 else weight * weight / weightSquareSum

        fun uniqueVisits(): Int = visitIds.size

        fun baseUncertainty(): Double {
            if (weight <= 0.0) return 1.0
            val effective = effectiveSamples().coerceAtLeast(0.25)
            val independent = min(effective, uniqueVisits().coerceAtLeast(1).toDouble()).coerceAtLeast(0.5)
            // Several lattice projections may belong to the same physical CNG visit.
            // They can refine the weighted mean, but cannot dilute spread as independent evidence.
            val spreadTerm = (spreadOrNull() ?: 0.0) / sqrt(independent)
            // Bounded equivalence already calculated uncertainty of each local mean from
            // petrol/CNG lane variance, empirical noise and its own effective support.
            // Projected Advisor rows must not divide that mean uncertainty a second time.
            if (upstreamWeight > 0.0 && legacyWeight <= 1e-12) {
                val upstreamTerm = sqrt(
                    (upstreamVarianceWeightSum / upstreamWeight).coerceAtLeast(0.0),
                )
                return sqrt(spreadTerm * spreadTerm + upstreamTerm * upstreamTerm)
            }
            val sparseTerm = BASE_PRIOR_UNCERTAINTY_RATIO / sqrt(independent)
            val weightTerm = WEIGHT_UNCERTAINTY_RATIO / sqrt(weight.coerceAtLeast(0.25))
            return sqrt(spreadTerm * spreadTerm + sparseTerm * sparseTerm + weightTerm * weightTerm)
        }

        fun decision(estimate: Double?, additionalUncertainty: Double = 0.0): AdaptiveDecision {
            if (estimate == null || !estimate.isFinite()) {
                return AdaptiveDecision(
                    actionable = false,
                    readiness = "NO_EVIDENCE",
                    reason = "Nenhuma estimativa válida nesta posição",
                    uncertaintyRatio = 1.0,
                    usefulMarginRatio = 0.0,
                    correctionFraction = null,
                    suggestedDeltaRatio = null,
                    estimatedResidualAfterRatio = null,
                    utility = 0.0,
                    confidence = 0.0,
                    compatibilityStage = "OBSERVED",
                )
            }
            val deadband = configuredDeadbandRatio()
            val uncertainty = sqrt(baseUncertainty() * baseUncertainty() + additionalUncertainty * additionalUncertainty)
            val magnitude = abs(estimate)
            val usefulMargin = (magnitude - uncertainty - deadband).coerceAtLeast(0.0)
            val equivalent = magnitude <= deadband
            val actionable = !equivalent && usefulMargin > 0.0
            val certainty = if (magnitude <= 1e-9) 0.0 else (usefulMargin / magnitude).coerceIn(0.0, 1.0)
            val correctionFraction = if (actionable) {
                MIN_CORRECTION_FRACTION + (MAX_CORRECTION_FRACTION - MIN_CORRECTION_FRACTION) * sqrt(certainty)
            } else null
            val suggested = correctionFraction?.let { estimate * it }
            val residualAfter = suggested?.let { estimate - it }
            val signalScore = if (magnitude <= 1e-9) 0.0 else 1.0 - exp(-magnitude / max(uncertainty, deadband))
            val evidenceScore = 1.0 - exp(-sqrt(weight.coerceAtLeast(0.0)))
            val repeatability = 1.0 / (1.0 + (spreadOrNull() ?: 0.0) / deadband.coerceAtLeast(1e-6))
            val rawConfidence = (0.50 * signalScore + 0.30 * evidenceScore + 0.20 * repeatability).coerceIn(0.0, 1.0)
            val confidence = min(rawConfidence, evidenceScore)
            val utility = if (magnitude <= 1e-9) 0.0 else (usefulMargin / magnitude * confidence).coerceIn(0.0, 1.0)
            val readiness = when {
                equivalent -> "EQUIVALENT"
                actionable -> "AVAILABLE"
                else -> "OBSERVING"
            }
            val reason = when (readiness) {
                "EQUIVALENT" -> "Diferença dentro da faixa neutra configurada"
                "AVAILABLE" -> "O erro útil supera a incerteza; correção relevante disponível"
                else -> "A estimativa existe, mas a incerteza ainda cobre o benefício provável"
            }
            val compatibilityStage = when {
                confidence >= 0.80 -> "CONFIRMED"
                confidence >= 0.60 -> "ACCEPTED"
                confidence >= 0.35 -> "PROVISIONAL"
                else -> "OBSERVED"
            }
            return AdaptiveDecision(
                actionable = actionable,
                readiness = readiness,
                reason = reason,
                uncertaintyRatio = uncertainty,
                usefulMarginRatio = usefulMargin,
                correctionFraction = correctionFraction,
                suggestedDeltaRatio = suggested,
                estimatedResidualAfterRatio = residualAfter,
                utility = utility,
                confidence = confidence,
                compatibilityStage = compatibilityStage,
            )
        }

        fun confidence(): Double = decision(meanOrNull()).confidence
        fun confidenceStage(): String = decision(meanOrNull()).compatibilityStage
        fun readiness(): String = decision(meanOrNull()).readiness
    }

    private class PairedCurveBucket {
        private val target = WeightedStats()
        private val observed = WeightedStats()

        fun add(targetMs: Double, observedMs: Double, weight: Double, visitId: String) {
            target.addValue(targetMs, weight, visitId)
            observed.addValue(observedMs, weight, visitId)
        }

        fun targetMeanOrNull(): Double? = target.meanOrNull()
        fun observedMeanOrNull(): Double? = observed.meanOrNull()
        fun targetSpreadOrNull(): Double? = target.spreadOrNull()
        fun observedSpreadOrNull(): Double? = observed.spreadOrNull()
        fun effectiveSamples(): Double = min(target.effectiveSamples(), observed.effectiveSamples())
        fun uniqueVisits(): Int = min(target.uniqueVisits(), observed.uniqueVisits())
        fun confidence(): Double = min(target.confidence(), observed.confidence())
        fun confidenceStage(): String = lowerStage(target.confidenceStage(), observed.confidenceStage())
        fun readiness(): String = lowerReadiness(target.readiness(), observed.readiness())
    }

    private class WeightedBucket : WeightedStats() {
        private var rpmMin = Double.POSITIVE_INFINITY
        private var rpmMax = Double.NEGATIVE_INFINITY

        fun add(
            value: Double,
            addedWeight: Double,
            rpm: Double,
            visitId: String,
            upstreamUncertaintyFraction: Double?,
        ) {
            addValue(value, addedWeight, visitId, upstreamUncertaintyFraction)
            if (addedWeight > 0.0) {
                rpmMin = min(rpmMin, rpm)
                rpmMax = max(rpmMax, rpm)
            }
        }

        fun rpmCoverage(): Double = if (!rpmMin.isFinite() || !rpmMax.isFinite()) 0.0 else rpmMax - rpmMin
    }

    private class ResidualBucket(val row: Int, val column: Int) : WeightedStats() {
        private var rpmSum = 0.0
        private var mapSum = 0.0
        private var removedGlobalWeight = 0.0
        private var globalUncertaintySum = 0.0

        fun add(
            value: Double,
            addedWeight: Double,
            rpm: Double,
            mapBar: Double,
            visitId: String,
            upstreamUncertaintyFraction: Double?,
            removedGlobal: Boolean,
            globalUncertainty: Double,
        ) {
            addValue(value, addedWeight, visitId, upstreamUncertaintyFraction)
            if (addedWeight > 0.0) {
                rpmSum += rpm * addedWeight
                mapSum += mapBar * addedWeight
                if (removedGlobal) removedGlobalWeight += addedWeight
                globalUncertaintySum += globalUncertainty * addedWeight
            }
        }

        fun rpmMean(): Double = if (weight <= 0.0) 0.0 else rpmSum / weight
        fun mapMean(): Double = if (weight <= 0.0) 0.0 else mapSum / weight
        fun globalRemovalFraction(): Double = if (weight <= 0.0) 0.0 else removedGlobalWeight / weight
        fun averageGlobalUncertainty(): Double = if (weight <= 0.0) 0.0 else globalUncertaintySum / weight
    }

    private data class GlobalPoint(
        val index: Int,
        val petrolMs: Double,
        val errorRatio: Double?,
        val decision: AdaptiveDecision,
        val effectiveWeight: Double,
        val effectiveSamples: Double,
        val uniqueVisits: Int,
        val rpmCoverage: Double,
        val spread: Double?,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("index", index)
            .put("petrolMs", petrolMs)
            .put("errorPercent", errorRatio?.times(100.0) ?: JSONObject.NULL)
            .put("suggestedDeltaPercent", decision.suggestedDeltaRatio?.times(100.0) ?: JSONObject.NULL)
            .put("estimatedResidualAfterPercent", decision.estimatedResidualAfterRatio?.times(100.0) ?: JSONObject.NULL)
            .put("uncertaintyPercent", decision.uncertaintyRatio.times(100.0))
            .put("usefulMarginPercent", decision.usefulMarginRatio.times(100.0))
            .put("correctionFraction", decision.correctionFraction ?: JSONObject.NULL)
            .put("evidenceUtility", decision.utility)
            .put("readiness", decision.readiness)
            .put("decisionReason", decision.reason)
            .put("direction", direction(errorRatio))
            .put("effectiveWeight", effectiveWeight)
            .put("effectiveSamples", effectiveSamples)
            .put("uniqueVisits", uniqueVisits)
            .put("confidenceStage", decision.compatibilityStage)
            .put("confidence", decision.confidence)
            .put("rpmCoverage", rpmCoverage)
            .put("spreadPercent", spread?.times(100.0) ?: JSONObject.NULL)
            .put("actionable", decision.actionable)
            .put("automatic", false)
            .put("humanConfirmationRequired", true)
    }

    private class GlobalCurve(private val points: List<GlobalPoint>) {
        fun estimate(petrolMs: Double): Estimate {
            val (lower, upper, fraction) = KFactorProtocol.blendAxis(petrolMs)
            val lowerPoint = points[lower]
            val upperPoint = points[upper]
            val lowerAvailable = lowerPoint.errorRatio != null
            val upperAvailable = upperPoint.errorRatio != null
            if (!lowerAvailable && !upperAvailable) return Estimate(0.0, 0.0, 0.0, false)
            val lowerError = lowerPoint.errorRatio ?: upperPoint.errorRatio ?: 0.0
            val upperError = upperPoint.errorRatio ?: lowerPoint.errorRatio ?: 0.0
            val value = lowerError + (upperError - lowerError) * fraction
            val lowerUncertainty = lowerPoint.decision.uncertaintyRatio
            val upperUncertainty = upperPoint.decision.uncertaintyRatio
            val uncertainty = lowerUncertainty + (upperUncertainty - lowerUncertainty) * fraction
            val confidence = min(lowerPoint.decision.confidence, upperPoint.decision.confidence)
            return Estimate(value, uncertainty, confidence, true)
        }

        fun toJson(): JSONArray = JSONArray(points.map { it.toJson() })
    }

    private fun lowerStage(first: String, second: String): String {
        val order = listOf("OBSERVED", "PROVISIONAL", "ACCEPTED", "CONFIRMED")
        val index = min(order.indexOf(first).coerceAtLeast(0), order.indexOf(second).coerceAtLeast(0))
        return order[index]
    }

    private fun lowerReadiness(first: String, second: String): String {
        val order = listOf("NO_EVIDENCE", "OBSERVING", "EQUIVALENT", "AVAILABLE")
        val index = min(order.indexOf(first).coerceAtLeast(0), order.indexOf(second).coerceAtLeast(0))
        return order[index]
    }
}