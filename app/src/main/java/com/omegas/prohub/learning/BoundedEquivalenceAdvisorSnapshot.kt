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
 * This is deliberately NOT a second matcher. The same bounded local-kernel semantics
 * already used by [EquivalenceSurface] are reused here so nearby valid evidence cannot
 * disappear merely because the two fuel lanes landed on adjacent lattice nodes.
 * Gasoline nodes remain the projection anchors: CNG-only territory never manufactures
 * an Advisor comparison or authority by itself.
 */
internal object BoundedEquivalenceAdvisorSnapshot {
    const val AUTHORITY = "RPM_MAP_PETROL_TINJ"
    private const val ORIGIN = "BOUNDED_EQUIVALENCE_SURFACE"
    private const val EPSILON = 1e-12
    private const val LOCAL_RADIUS_CELLS = 1.5

    fun build(snapshot: EquivalenceSurface.Snapshot, epoch: Int): JSONObject {
        val petrolByIndex = linkedMapOf<Int, EquivalenceSurface.SnapshotNode>()
        val cngByIndex = linkedMapOf<Int, EquivalenceSurface.SnapshotNode>()
        snapshot.nodes.forEach { node ->
            when (node.lane) {
                FuelLane.PETROL_REFERENCE -> petrolByIndex[node.index] = node
                FuelLane.CNG_PETROL_OBSERVED -> cngByIndex[node.index] = node
            }
        }

        val surface = EquivalenceSurface(
            EquivalenceSurface.Config(
                minRpm = snapshot.minRpm,
                maxRpm = snapshot.maxRpm,
                rpmStep = snapshot.rpmStep,
                minMapBar = snapshot.minMapBar,
                maxMapBar = snapshot.maxMapBar,
                mapStepBar = snapshot.mapStepBar,
            ),
        ).also { it.restore(snapshot) }

        val mapCount = ceil((snapshot.maxMapBar - snapshot.minMapBar) / snapshot.mapStepBar).toInt() + 1
        val rpmCount = ceil((snapshot.maxRpm - snapshot.minRpm) / snapshot.rpmStep).toInt() + 1
        val cngAllocationDenominator = DoubleArray(rpmCount * mapCount)
        cngByIndex.keys.forEach { cngIndex ->
            cngAllocationDenominator[cngIndex] = localPetrolKernelMass(
                centerIndex = cngIndex,
                petrolIndices = petrolByIndex.keys,
                rpmCount = rpmCount,
                mapCount = mapCount,
            )
        }
        val projected = mutableListOf<JSONObject>()
        val deadband = (LearningToleranceSettings.current.equivalenceDeadbandPercent / 100.0)
            .coerceAtLeast(0.0)

        // Gasoline is the ruler. Project only at gasoline-supported lattice nodes,
        // querying the same local bounded kernel for both lanes at that coordinate.
        petrolByIndex.keys.sorted().forEach { index ->
            val petrolAnchor = petrolByIndex.getValue(index)
            val rpmIndex = index / mapCount
            val mapIndex = index % mapCount
            val rpm = snapshot.minRpm + rpmIndex * snapshot.rpmStep
            val mapBar = snapshot.minMapBar + mapIndex * snapshot.mapStepBar
            val query = surface.query(rpm, mapBar)
            val petrol = query.petrol ?: return@forEach
            val cng = query.cng ?: return@forEach
            val referenceMs = petrol.meanTinjMs
            val observedMs = cng.meanTinjMs
            if (referenceMs <= 0.05 || observedMs <= 0.0) return@forEach

            val petrolEss = petrol.effectiveSupport.coerceAtLeast(EPSILON)
            val cngEss = cng.effectiveSupport.coerceAtLeast(EPSILON)
            val support = min(petrolEss, cngEss).coerceAtLeast(EPSILON)
            val uncertainty = sqrt(
                meanUncertaintyFraction(petrol.varianceMs2, referenceMs, petrolEss).let { it * it } +
                    meanUncertaintyFraction(cng.varianceMs2, observedMs, cngEss).let { it * it },
            )
            val errorRatio = (observedMs - referenceMs) / referenceMs
            val errorPct = errorRatio * 100.0
            val repeatability = 1.0 / (
                1.0 + sqrt(petrol.varianceMs2 + cng.varianceMs2) / referenceMs.coerceAtLeast(0.05)
            )
            val supportConfidence = 1.0 - exp(-sqrt(support))
            val cngLocalWeight = allocatedLocalScientificWeight(
                centerIndex = index,
                byIndex = cngByIndex,
                allocationDenominator = cngAllocationDenominator,
                rpmCount = rpmCount,
                mapCount = mapCount,
            )
            val pairedScientificWeight = min(petrolAnchor.sumW, cngLocalWeight).coerceAtLeast(0.0)
            if (pairedScientificWeight <= EPSILON) return@forEach
            // AssistedCalibrationAdvisor consumes `quality` as scientific sample weight.
            // Geometry/repeatability may only reduce upstream authority, never floor it
            // upward or turn one physical observation into stronger pseudo-evidence.
            val quality = (supportConfidence * repeatability * pairedScientificWeight)
                .coerceIn(0.0, 1.0)
            val revision = max(petrol.materialRevision, cng.materialRevision)
            val id = "SURFACE-$index"
            val visitId = "SURFACE-REV-$revision"

            projected += JSONObject()
                .put("id", id)
                .put("dedupe_key", "$epoch:$ORIGIN:$index:$revision")
                .put("visit_id", visitId)
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
                .put("paired_scientific_weight", pairedScientificWeight)
                .put("petrol_effective_support", petrolEss)
                .put("cng_effective_support", cngEss)
                .put("upstream_uncertainty_fraction", uncertainty)
                .put("useful_margin_fraction", abs(errorRatio) - uncertainty - deadband)
                .put("observation_count", support.toInt().coerceAtLeast(1))
                .put("material_revision", revision)
                .put("epoch", epoch)
        }

        val petrolWeight = petrolByIndex.values.sumOf { it.sumW }
        val cngWeight = cngByIndex.values.sumOf { it.sumW }
        val authorityBudget = min(petrolWeight, cngWeight).coerceAtLeast(0.0)
        val rawPairedWeight = projected.sumOf { it.optDouble("paired_scientific_weight", 0.0) }
        val authorityScale = when {
            rawPairedWeight <= EPSILON -> 1.0
            rawPairedWeight <= authorityBudget + EPSILON -> 1.0
            else -> (authorityBudget / rawPairedWeight).coerceIn(0.0, 1.0)
        }
        if (authorityScale < 1.0) {
            projected.forEach { row ->
                row.put(
                    "paired_scientific_weight",
                    row.optDouble("paired_scientific_weight", 0.0) * authorityScale,
                )
                row.put("quality", row.optDouble("quality", 0.0) * authorityScale)
            }
        }
        projected.forEach { it.put("authority_budget_scale", authorityScale) }
        val comparisons = JSONArray(projected)

        return JSONObject()
            .put("format", MotorLearningMemory.FORMAT)
            .put("epoch", epoch.coerceAtLeast(1))
            .put("regions", JSONArray())
            .put("comparisons", comparisons)
            .put("primaryAuthority", AUTHORITY)
            .put("environmentGates", false)
            .put("surfaceRepresentation", EquivalenceSurfaceCodec.REPRESENTATION)
            .put("petrolWeight", petrolWeight)
            .put("cngWeight", cngWeight)
            .put("projectionAuthorityBudget", authorityBudget)
            .put("projectionRawPairedWeight", rawPairedWeight)
            .put("projectionAuthorityScale", authorityScale)
            .put("comparisonCount", comparisons.length())
            .put("legacySeededRegions", snapshot.legacySeededRegions)
            .also { root ->
                snapshot.legacySeedProvenance?.let { root.put("legacySeedProvenance", it) }
            }
    }

    private fun allocatedLocalScientificWeight(
        centerIndex: Int,
        byIndex: Map<Int, EquivalenceSurface.SnapshotNode>,
        allocationDenominator: DoubleArray,
        rpmCount: Int,
        mapCount: Int,
    ): Double {
        val centerRpm = centerIndex / mapCount
        val centerMap = centerIndex % mapCount
        var weight = 0.0
        for (dr in -1..1) {
            val rpmIndex = centerRpm + dr
            if (rpmIndex !in 0 until rpmCount) continue
            for (dm in -1..1) {
                val mapIndex = centerMap + dm
                if (mapIndex !in 0 until mapCount) continue
                val distanceSquared = (dr * dr + dm * dm).toDouble()
                if (distanceSquared > LOCAL_RADIUS_CELLS * LOCAL_RADIUS_CELLS + EPSILON) continue
                val nodeIndex = rpmIndex * mapCount + mapIndex
                val node = byIndex[nodeIndex] ?: continue
                val denominator = allocationDenominator[nodeIndex]
                if (denominator <= EPSILON) continue
                weight += node.sumW * exp(-0.5 * distanceSquared) / denominator
            }
        }
        return weight.coerceAtLeast(0.0)
    }

    private fun localPetrolKernelMass(
        centerIndex: Int,
        petrolIndices: Set<Int>,
        rpmCount: Int,
        mapCount: Int,
    ): Double {
        val centerRpm = centerIndex / mapCount
        val centerMap = centerIndex % mapCount
        var kernelMass = 0.0
        for (dr in -1..1) {
            val rpmIndex = centerRpm + dr
            if (rpmIndex !in 0 until rpmCount) continue
            for (dm in -1..1) {
                val mapIndex = centerMap + dm
                if (mapIndex !in 0 until mapCount) continue
                val distanceSquared = (dr * dr + dm * dm).toDouble()
                if (distanceSquared > LOCAL_RADIUS_CELLS * LOCAL_RADIUS_CELLS + EPSILON) continue
                val petrolIndex = rpmIndex * mapCount + mapIndex
                if (petrolIndex !in petrolIndices) continue
                kernelMass += exp(-0.5 * distanceSquared)
            }
        }
        return kernelMass.coerceAtLeast(0.0)
    }

    private fun meanUncertaintyFraction(variance: Double, mean: Double, ess: Double): Double {
        val normalizedEss = ess.coerceAtLeast(1.0)
        val spreadOfMean = sqrt(variance.coerceAtLeast(0.0)) /
            mean.coerceAtLeast(0.05) / sqrt(normalizedEss)
        val empiricalNoiseOfMean = EquivalenceRuntime.EMPIRICAL_SINGLE_OBSERVATION_NOISE_FRACTION /
            sqrt(normalizedEss)
        return sqrt(spreadOfMean * spreadOfMean + empiricalNoiseOfMean * empiricalNoiseOfMean)
    }

    private fun direction(errorRatio: Double, deadband: Double): String = when {
        abs(errorRatio) <= deadband -> "EQUIVALENT"
        errorRatio > 0.0 -> "INCREASE_CNG_DELIVERY"
        else -> "DECREASE_CNG_DELIVERY"
    }
}
