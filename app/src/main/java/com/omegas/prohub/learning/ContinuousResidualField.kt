package com.omegas.prohub.learning

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Projeta somente o residual local em RPM × MAP depois que a tendência global
 * da curva K já foi removida. A projeção nunca volta como evidência de entrada.
 */
internal object ContinuousResidualField {
    private const val RPM_BANDWIDTH = 650.0
    private const val MAP_BANDWIDTH = 0.080
    private const val ANCHOR_RPM_BANDWIDTH = 900.0
    private const val ANCHOR_PETROL_BANDWIDTH = 1.50
    private const val DIRECT_DISTANCE = 0.75
    private const val NEAR_DISTANCE = 2.00
    private const val MAX_ANCHOR_DISTANCE = 2.50
    private const val LOCAL_PRIOR_UNCERTAINTY = 0.025

    data class Sample(
        val rpm: Double,
        val mapBar: Double,
        val petrolTargetMs: Double,
        val errorRatio: Double,
        val globalErrorRatio: Double,
        val globalUncertaintyRatio: Double,
        val quality: Double,
        val visitId: String,
    ) {
        val residualRatio: Double get() = errorRatio - globalErrorRatio
    }

    data class GlobalEstimate(
        val errorRatio: Double,
        val uncertaintyRatio: Double,
        val available: Boolean,
    )

    fun predict(
        samples: List<Sample>,
        deadbandRatio: Double,
        globalAt: (Double) -> GlobalEstimate,
    ): JSONArray {
        val output = JSONArray()
        val rpmBins = KMapPhysicalAxes.rpmBins()
        val petrolBins = KMapPhysicalAxes.petrolBins()
        petrolBins.forEachIndexed { row, petrolMs ->
            rpmBins.forEachIndexed { column, rpm ->
                output.put(predictCell(row, column, rpm.toDouble(), petrolMs, samples, deadbandRatio, globalAt))
            }
        }
        return output
    }

    private fun predictCell(
        row: Int,
        column: Int,
        rpm: Double,
        petrolMs: Double,
        samples: List<Sample>,
        deadbandRatio: Double,
        globalAt: (Double) -> GlobalEstimate,
    ): JSONObject {
        val global = globalAt(petrolMs)
        val anchor = inferMap(rpm, petrolMs, samples)
        val local = if (anchor != null && anchor.nearestDistance <= MAX_ANCHOR_DISTANCE) {
            estimateLocal(rpm, anchor.mapBar, samples)
        } else null
        val supportType = when {
            local == null || local.nearestDistance > NEAR_DISTANCE -> "GLOBAL_ONLY"
            local.nearestDistance <= DIRECT_DISTANCE -> "DIRECT"
            else -> "NEAR"
        }
        val localAvailable = supportType == "DIRECT" || supportType == "NEAR"
        val localResidual = local?.mean?.takeIf { localAvailable }
        val uncertainty = if (localAvailable) {
            sqrt(global.uncertaintyRatio * global.uncertaintyRatio + local!!.uncertainty * local.uncertainty)
        } else global.uncertaintyRatio
        val usefulMargin = localResidual?.let { (abs(it) - deadbandRatio - uncertainty).coerceAtLeast(0.0) } ?: 0.0
        val visits = local?.uniqueVisits ?: 0
        val correctionFraction = when {
            visits >= 4 -> 0.90
            visits >= 2 -> 0.75
            else -> 0.55
        }
        val actionable = localAvailable && localResidual != null && usefulMargin > 0.0
        val suggested = if (actionable) localResidual * correctionFraction else null
        val total = if (global.available || localResidual != null) global.errorRatio + (localResidual ?: 0.0) else null
        return JSONObject()
            .put("row", row)
            .put("column", column)
            .put("rpm", rpm)
            .put("petrolMs", petrolMs)
            .put("inferredMapBar", anchor?.mapBar ?: JSONObject.NULL)
            .put("supportType", supportType)
            .put("predictedErrorPercent", total?.times(100.0) ?: JSONObject.NULL)
            .put("globalErrorPercent", if (global.available) global.errorRatio * 100.0 else JSONObject.NULL)
            .put("localResidualPercent", localResidual?.times(100.0) ?: JSONObject.NULL)
            .put("suggestedDeltaPercent", suggested?.times(100.0) ?: JSONObject.NULL)
            .put("estimatedResidualAfterPercent", if (suggested != null) (localResidual!! - suggested) * 100.0 else JSONObject.NULL)
            .put("uncertaintyPercent", uncertainty * 100.0)
            .put("usefulMarginPercent", usefulMargin * 100.0)
            .put("nearestDistance", local?.nearestDistance ?: JSONObject.NULL)
            .put("anchorDistance", anchor?.nearestDistance ?: JSONObject.NULL)
            .put("effectiveWeight", local?.weight ?: 0.0)
            .put("uniqueVisits", visits)
            .put("confidence", (1.0 / (1.0 + uncertainty * 12.0)).coerceIn(0.0, 1.0))
            .put("readiness", if (actionable) "AVAILABLE" else if (localAvailable) "OBSERVING" else "GLOBAL_ONLY")
            .put("decisionReason", when {
                actionable -> "Residual local previsto supera a faixa neutra e a incerteza."
                localAvailable -> "Residual local ainda coberto pela faixa neutra ou incerteza."
                global.available -> "Somente tendência global; nenhum residual local será aplicado ao mapa K."
                else -> "Sem suporte suficiente para previsão."
            })
            .put("direction", direction(localResidual, deadbandRatio))
            .put("actionable", actionable)
            .put("automatic", false)
            .put("humanConfirmationRequired", true)
    }

    private fun inferMap(rpm: Double, petrolMs: Double, samples: List<Sample>): AnchorEstimate? {
        if (samples.isEmpty()) return null
        val weighted = samples.map { sample ->
            val dr = (sample.rpm - rpm) / ANCHOR_RPM_BANDWIDTH
            val dt = (sample.petrolTargetMs - petrolMs) / ANCHOR_PETROL_BANDWIDTH
            val distance = sqrt(dr * dr + dt * dt)
            Weighted(sample, distance, sample.quality.coerceIn(0.02, 1.0) * gaussian(distance))
        }
        val nearest = weighted.minOf { it.distance }
        val useful = weighted.filter { it.weight >= 1e-6 }
        val total = useful.sumOf { it.weight }
        if (total <= 0.0) return null
        return AnchorEstimate(useful.sumOf { it.sample.mapBar * it.weight } / total, nearest)
    }

    private fun estimateLocal(rpm: Double, mapBar: Double, samples: List<Sample>): LocalEstimate? {
        if (samples.isEmpty()) return null
        val weighted = samples.map { sample ->
            val dr = (sample.rpm - rpm) / RPM_BANDWIDTH
            val dm = (sample.mapBar - mapBar) / MAP_BANDWIDTH
            val distance = sqrt(dr * dr + dm * dm)
            Weighted(sample, distance, sample.quality.coerceIn(0.02, 1.0) * gaussian(distance))
        }
        val nearest = weighted.minOf { it.distance }
        val useful = weighted.filter { it.distance <= NEAR_DISTANCE && it.weight >= 1e-6 }
        val total = useful.sumOf { it.weight }
        if (total <= 0.0) return null
        val mean = useful.sumOf { it.sample.residualRatio * it.weight } / total
        val variance = useful.sumOf {
            val delta = it.sample.residualRatio - mean
            delta * delta * it.weight
        } / total
        val uncertainty = sqrt(variance.coerceAtLeast(0.0)) + LOCAL_PRIOR_UNCERTAINTY / sqrt(total.coerceAtLeast(0.05))
        val visits = useful.filter { it.weight >= 0.05 }.map { it.sample.visitId }.toSet().size
        return LocalEstimate(mean, uncertainty, nearest, total, visits)
    }

    private fun gaussian(distance: Double): Double = exp(-0.5 * distance * distance)

    private fun direction(value: Double?, deadband: Double): String = when {
        value == null -> "INSUFFICIENT_EVIDENCE"
        abs(value) <= deadband -> "EQUIVALENT"
        value > 0.0 -> "INCREASE_CNG_DELIVERY"
        else -> "DECREASE_CNG_DELIVERY"
    }

    private data class Weighted(val sample: Sample, val distance: Double, val weight: Double)
    private data class AnchorEstimate(val mapBar: Double, val nearestDistance: Double)
    private data class LocalEstimate(
        val mean: Double,
        val uncertainty: Double,
        val nearestDistance: Double,
        val weight: Double,
        val uniqueVisits: Int,
    )
}
