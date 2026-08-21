package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Persistence/advisor-boundary projection of the bounded RPM×MAP surface.
 *
 * This is deliberately NOT a matcher. Pairing already happened by lattice index in
 * [EquivalenceSurface]. The adapter only turns compact moments into the legacy JSON
 * shape consumed by [AssistedCalibrationAdvisor], so Map/Curve projection can remain
 * downstream without rescanning raw regions or invoking environmental selectors.
 */
internal object BoundedEquivalenceAdvisorSnapshot {
    const val AUTHORITY = "RPM_MAP_PETROL_TINJ"
    private const val ORIGIN = "BOUNDED_EQUIVALENCE_SURFACE"
    private const val EPSILON = 1e-12

    fun build(snapshot: EquivalenceSurface.Snapshot, epoch: Int): JSONObject {
        val petrolByIndex = linkedMapOf<Int, EquivalenceSurface.SnapshotNode>()
        val cngByIndex = linkedMapOf<Int, EquivalenceSurface.SnapshotNode>()
        snapshot.nodes.forEach { node ->
            when (node.lane) {
                FuelLane.PETROL_REFERENCE -> petrolByIndex[node.index] = node
                FuelLane.CNG_PETROL_OBSERVED -> cngByIndex[node.index] = node
            }
        }

        val mapCount = ceil((snapshot.maxMapBar - snapshot.minMapBar) / snapshot.mapStepBar).toInt() + 1
        val comparisons = JSONArray()
        val deadband = (LearningToleranceSettings.current.equivalenceDeadbandPercent / 100.0)
            .coerceAtLeast(0.0)
        petrolByIndex.keys.intersect(cngByIndex.keys)
            .sorted()
            .forEach { index ->
                val petrol = petrolByIndex.getValue(index)
                val cng = cngByIndex.getValue(index)
                val referenceMs = mean(petrol) ?: return@forEach
                val observedMs = mean(cng) ?: return@forEach
                if (referenceMs <= 0.05 || observedMs <= 0.0) return@forEach

                val petrolEss = effectiveSupport(petrol)
                val cngEss = effectiveSupport(cng)
                val support = min(petrolEss, cngEss).coerceAtLeast(EPSILON)
                val referenceVariance = variance(petrol, referenceMs)
                val cngVariance = variance(cng, observedMs)
                val uncertainty = sqrt(
                    meanUncertaintyFraction(referenceVariance, referenceMs, petrolEss).let { it * it } +
                        meanUncertaintyFraction(cngVariance, observedMs, cngEss).let { it * it },
                )
                val errorRatio = (observedMs - referenceMs) / referenceMs
                val errorPct = errorRatio * 100.0
                val repeatability = 1.0 / (
                    1.0 + sqrt(referenceVariance + cngVariance) / referenceMs.coerceAtLeast(0.05)
                )
                val supportConfidence = 1.0 - exp(-sqrt(support))
                val quality = (supportConfidence * repeatability).coerceIn(0.02, 1.0)
                val rpmIndex = index / mapCount
                val mapIndex = index % mapCount
                val rpm = snapshot.minRpm + rpmIndex * snapshot.rpmStep
                val mapBar = snapshot.minMapBar + mapIndex * snapshot.mapStepBar
                val revision = max(petrol.materialRevision, cng.materialRevision)
                val id = "SURFACE-$index"

                comparisons.put(
                    JSONObject()
                        .put("id", id)
                        .put("dedupe_key", "$epoch:$ORIGIN:$index")
                        .put("visit_id", id)
                        .put("reference_region_id", id)
                        .put("origin", ORIGIN)
                        .put("rpm", rpm)
                        .put("map_bar", mapBar)
                        .put("petrol_target_ms", referenceMs)
                        .put("petrol_on_cng_ms", observedMs)
                        .put("difference_ms", observedMs - referenceMs)
                        .put("error_ratio", errorRatio)
                        .put("error_pct", errorPct)
                        .put("direction", direction(errorRatio, deadband))
                        .put("quality", quality)
                        .put("paired_scientific_weight", min(petrol.sumW, cng.sumW))
                        .put("petrol_effective_support", petrolEss)
                        .put("cng_effective_support", cngEss)
                        .put("upstream_uncertainty_fraction", uncertainty)
                        .put("useful_margin_fraction", abs(errorRatio) - uncertainty - deadband)
                        .put("observation_count", support.toInt().coerceAtLeast(1))
                        .put("material_revision", revision)
                        .put("epoch", epoch),
                )
            }

        return JSONObject()
            .put("format", MotorLearningMemory.FORMAT)
            .put("epoch", epoch.coerceAtLeast(1))
            .put("regions", JSONArray())
            .put("comparisons", comparisons)
            .put("primaryAuthority", AUTHORITY)
            .put("environmentGates", false)
            .put("surfaceRepresentation", EquivalenceSurfaceCodec.REPRESENTATION)
            .put("petrolWeight", petrolByIndex.values.sumOf { it.sumW })
            .put("cngWeight", cngByIndex.values.sumOf { it.sumW })
            .put("comparisonCount", comparisons.length())
            .put("legacySeededRegions", snapshot.legacySeededRegions)
            .also { root ->
                snapshot.legacySeedProvenance?.let { root.put("legacySeedProvenance", it) }
            }
    }

    private fun mean(node: EquivalenceSurface.SnapshotNode): Double? =
        if (node.sumW.isFinite() && node.sumW > EPSILON && node.sumWTinj.isFinite()) {
            node.sumWTinj / node.sumW
        } else null

    private fun variance(node: EquivalenceSurface.SnapshotNode, mean: Double): Double =
        if (!node.sumWTinj2.isFinite() || node.sumW <= EPSILON) 0.0
        else max(0.0, node.sumWTinj2 / node.sumW - mean * mean)

    private fun effectiveSupport(node: EquivalenceSurface.SnapshotNode): Double =
        if (!node.sumW2.isFinite() || node.sumW2 <= EPSILON) EPSILON
        else (node.sumW * node.sumW / node.sumW2).coerceAtLeast(EPSILON)

    private fun meanUncertaintyFraction(variance: Double, mean: Double, ess: Double): Double =
        sqrt(variance.coerceAtLeast(0.0)) / mean.coerceAtLeast(0.05) / sqrt(ess.coerceAtLeast(1.0))

    private fun direction(errorRatio: Double, deadband: Double): String = when {
        abs(errorRatio) <= deadband -> "EQUIVALENT"
        errorRatio > 0.0 -> "INCREASE_CNG_DELIVERY"
        else -> "DECREASE_CNG_DELIVERY"
    }
}
