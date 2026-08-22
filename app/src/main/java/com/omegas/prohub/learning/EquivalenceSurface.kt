package com.omegas.prohub.learning

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/** Fuel lanes are intentionally separate: CNG never rewrites the gasoline ruler. */
internal enum class FuelLane {
    PETROL_REFERENCE,
    CNG_PETROL_OBSERVED,
}

/**
 * Bounded, incremental RPM × MAP surface for petrol injection time.
 *
 * Each observation touches at most four lattice nodes. Querying inspects one fixed
 * 4×4 local neighborhood per lane; there is no historical scan, candidate list or
 * global sort. The state size depends solely on [Config], never on driving time.
 */
internal class EquivalenceSurface(
    val config: Config,
) {
    data class Config(
        val minRpm: Double,
        val maxRpm: Double,
        val rpmStep: Double,
        val minMapBar: Double,
        val maxMapBar: Double,
        val mapStepBar: Double,
    ) {
        init {
            require(minRpm.isFinite() && maxRpm.isFinite() && maxRpm > minRpm)
            require(rpmStep.isFinite() && rpmStep > 0.0)
            require(minMapBar.isFinite() && maxMapBar.isFinite() && maxMapBar > minMapBar)
            require(mapStepBar.isFinite() && mapStepBar > 0.0)
        }

        companion object {
            fun mp48ReplayCandidate(): Config = Config(
                minRpm = 0.0,
                maxRpm = 9_000.0,
                rpmStep = 80.0,
                minMapBar = 0.0,
                maxMapBar = 2.5,
                mapStepBar = 0.02,
            )
        }
    }

    data class ObserveResult(
        val touchedNodes: Int,
        val acceptedWeight: Double,
    )

    data class LaneEstimate(
        val meanTinjMs: Double,
        val varianceMs2: Double,
        val effectiveSupport: Double,
        val nearestSupportDistanceCells: Double,
        val materialRevision: Long,
    )

    data class QueryResult(
        val petrol: LaneEstimate?,
        val cng: LaneEstimate?,
    )

    data class SnapshotNode(
        val lane: FuelLane,
        val index: Int,
        val sumW: Double,
        val sumW2: Double,
        val sumWTinj: Double,
        val sumWTinj2: Double,
        val materialRevision: Long,
    )

    data class Snapshot(
        val schema: String = SNAPSHOT_SCHEMA,
        val minRpm: Double,
        val maxRpm: Double,
        val rpmStep: Double,
        val minMapBar: Double,
        val maxMapBar: Double,
        val mapStepBar: Double,
        val nodes: List<SnapshotNode>,
        val legacySeededRegions: Int = 0,
        val legacySeedProvenance: String? = null,
    )

    private val rpmCount = pointCount(config.minRpm, config.maxRpm, config.rpmStep)
    private val mapCount = pointCount(config.minMapBar, config.maxMapBar, config.mapStepBar)
    private val nodeCount = rpmCount * mapCount
    private val petrol = LaneState(nodeCount)
    private val cng = LaneState(nodeCount)
    private var legacySeededRegions = 0

    fun observe(
        lane: FuelLane,
        rpm: Double,
        mapBar: Double,
        petrolTinjMs: Double,
        weight: Double,
        materialRevision: Long,
    ): ObserveResult {
        if (!rpm.isFinite() || !mapBar.isFinite() || !petrolTinjMs.isFinite() || petrolTinjMs <= 0.0) {
            return ObserveResult(0, 0.0)
        }
        if (!weight.isFinite() || weight <= 0.0) return ObserveResult(0, 0.0)
        val rpmAxis = axisWeights(rpm, config.minRpm, config.maxRpm, config.rpmStep, rpmCount)
            ?: return ObserveResult(0, 0.0)
        val mapAxis = axisWeights(mapBar, config.minMapBar, config.maxMapBar, config.mapStepBar, mapCount)
            ?: return ObserveResult(0, 0.0)
        val target = state(lane)
        var touched = 0
        rpmAxis.forEach { rpmPoint ->
            mapAxis.forEach { mapPoint ->
                val spatialWeight = rpmPoint.weight * mapPoint.weight
                if (spatialWeight <= 0.0) return@forEach
                val nodeWeight = weight * spatialWeight
                target.observe(
                    index = index(rpmPoint.index, mapPoint.index),
                    tinjMs = petrolTinjMs,
                    weight = nodeWeight,
                    revision = materialRevision,
                )
                touched += 1
            }
        }
        return ObserveResult(touched, weight)
    }

    /**
     * One-time migration seam for accepted legacy gasoline summaries. It preserves
     * the persisted mean and spread while capping one region to one effective vote.
     * No raw frame or synthetic sample identity is created.
     */
    fun seedPetrolSummary(
        rpm: Double,
        mapBar: Double,
        meanTinjMs: Double,
        varianceMs2: Double,
        seedWeight: Double,
        materialRevision: Long,
    ): ObserveResult {
        if (!rpm.isFinite() || !mapBar.isFinite() || !meanTinjMs.isFinite() || meanTinjMs <= 0.0) {
            return ObserveResult(0, 0.0)
        }
        if (!varianceMs2.isFinite() || varianceMs2 < 0.0 || !seedWeight.isFinite() || seedWeight <= 0.0) {
            return ObserveResult(0, 0.0)
        }
        val rpmAxis = axisWeights(rpm, config.minRpm, config.maxRpm, config.rpmStep, rpmCount)
            ?: return ObserveResult(0, 0.0)
        val mapAxis = axisWeights(mapBar, config.minMapBar, config.maxMapBar, config.mapStepBar, mapCount)
            ?: return ObserveResult(0, 0.0)
        val secondMoment = meanTinjMs * meanTinjMs + varianceMs2
        var touched = 0
        rpmAxis.forEach { rpmPoint ->
            mapAxis.forEach { mapPoint ->
                val spatialWeight = rpmPoint.weight * mapPoint.weight
                if (spatialWeight <= 0.0) return@forEach
                val nodeWeight = seedWeight * spatialWeight
                petrol.seedSummary(
                    index = index(rpmPoint.index, mapPoint.index),
                    meanTinjMs = meanTinjMs,
                    secondMomentMs2 = secondMoment,
                    weight = nodeWeight,
                    revision = materialRevision,
                )
                touched += 1
            }
        }
        if (touched > 0) legacySeededRegions += 1
        return ObserveResult(touched, seedWeight)
    }

    fun query(rpm: Double, mapBar: Double): QueryResult {
        if (!rpm.isFinite() || !mapBar.isFinite()) return QueryResult(null, null)
        if (rpm < config.minRpm || rpm > config.maxRpm || mapBar < config.minMapBar || mapBar > config.maxMapBar) {
            return QueryResult(null, null)
        }
        val rpmCenter = floor((rpm - config.minRpm) / config.rpmStep).toInt().coerceIn(0, rpmCount - 1)
        val mapCenter = floor((mapBar - config.minMapBar) / config.mapStepBar).toInt().coerceIn(0, mapCount - 1)
        return QueryResult(
            petrol = queryLane(petrol, rpm, mapBar, rpmCenter, mapCenter),
            cng = queryLane(cng, rpm, mapBar, rpmCenter, mapCenter),
        )
    }

    fun snapshot(): Snapshot {
        val nodes = ArrayList<SnapshotNode>()
        petrol.snapshotInto(FuelLane.PETROL_REFERENCE, nodes)
        cng.snapshotInto(FuelLane.CNG_PETROL_OBSERVED, nodes)
        return Snapshot(
            minRpm = config.minRpm,
            maxRpm = config.maxRpm,
            rpmStep = config.rpmStep,
            minMapBar = config.minMapBar,
            maxMapBar = config.maxMapBar,
            mapStepBar = config.mapStepBar,
            nodes = nodes,
            legacySeededRegions = legacySeededRegions,
            legacySeedProvenance = if (legacySeededRegions > 0) LegacyPetrolSeedPolicy.PROVENANCE else null,
        )
    }

    fun restore(snapshot: Snapshot) {
        require(snapshot.schema == SNAPSHOT_SCHEMA) { "Unsupported equivalence surface schema" }
        require(snapshot.minRpm == config.minRpm && snapshot.maxRpm == config.maxRpm) { "RPM lattice mismatch" }
        require(snapshot.rpmStep == config.rpmStep) { "RPM step mismatch" }
        require(snapshot.minMapBar == config.minMapBar && snapshot.maxMapBar == config.maxMapBar) { "MAP lattice mismatch" }
        require(snapshot.mapStepBar == config.mapStepBar) { "MAP step mismatch" }
        if (snapshot.legacySeededRegions > 0) {
            require(snapshot.legacySeedProvenance == LegacyPetrolSeedPolicy.PROVENANCE) { "Legacy seed provenance mismatch" }
        }
        petrol.clear()
        cng.clear()
        snapshot.nodes.forEach { node ->
            require(node.index in 0 until nodeCount) { "Equivalence node index out of bounds" }
            require(node.sumW.isFinite() && node.sumW > 0.0) { "Invalid equivalence weight" }
            require(node.sumW2.isFinite() && node.sumW2 > 0.0) { "Invalid equivalence squared weight" }
            require(node.sumWTinj.isFinite() && node.sumWTinj2.isFinite()) { "Invalid equivalence moments" }
            state(node.lane).restore(node)
        }
        legacySeededRegions = snapshot.legacySeededRegions.coerceAtLeast(0)
    }

    fun clearLane(lane: FuelLane) {
        state(lane).clear()
    }

    internal fun debugTotalWeight(lane: FuelLane): Double = state(lane).sumW.sum()
    internal fun debugAllocatedScalarCount(): Int = nodeCount * 10
    internal fun debugNodeCount(): Int = nodeCount
    internal fun debugMaximumQueryNodes(): Int = QUERY_DIAMETER * QUERY_DIAMETER

    private fun queryLane(
        lane: LaneState,
        rpm: Double,
        mapBar: Double,
        rpmCenter: Int,
        mapCenter: Int,
    ): LaneEstimate? {
        var totalKernel = 0.0
        var weightedMean = 0.0
        var weightedSecondMoment = 0.0
        var weightedInverseEss = 0.0
        var localScientificMass = 0.0
        var nearestDistance = Double.POSITIVE_INFINITY
        var materialRevision = 0L

        for (rpmOffset in QUERY_MIN_OFFSET..QUERY_MAX_OFFSET) {
            val rpmIndex = rpmCenter + rpmOffset
            if (rpmIndex !in 0 until rpmCount) continue
            val nodeRpm = config.minRpm + rpmIndex * config.rpmStep
            val dr = (rpm - nodeRpm) / config.rpmStep
            for (mapOffset in QUERY_MIN_OFFSET..QUERY_MAX_OFFSET) {
                val mapIndex = mapCenter + mapOffset
                if (mapIndex !in 0 until mapCount) continue
                val nodeMap = config.minMapBar + mapIndex * config.mapStepBar
                val dm = (mapBar - nodeMap) / config.mapStepBar
                val distanceSquared = dr * dr + dm * dm
                if (distanceSquared > QUERY_RADIUS_CELLS * QUERY_RADIUS_CELLS + EPSILON) continue
                val local = lane.estimate(index(rpmIndex, mapIndex)) ?: continue
                val kernel = exp(-0.5 * distanceSquared).coerceAtLeast(EPSILON)
                totalKernel += kernel
                weightedMean += kernel * local.meanTinjMs
                weightedSecondMoment += kernel * (local.varianceMs2 + local.meanTinjMs * local.meanTinjMs)
                weightedInverseEss += kernel / local.effectiveSupport.coerceAtLeast(EPSILON)
                localScientificMass += local.scientificMass
                nearestDistance = minOf(nearestDistance, sqrt(distanceSquared))
                materialRevision = maxOf(materialRevision, local.materialRevision)
            }
        }
        if (totalKernel <= EPSILON) return null
        val mean = weightedMean / totalKernel
        val secondMoment = weightedSecondMoment / totalKernel
        val variance = max(0.0, secondMoment - mean * mean)
        val inverseEss = weightedInverseEss / totalKernel
        val relativeEffectiveSupport = (1.0 / inverseEss.coerceAtLeast(EPSILON)).coerceAtLeast(EPSILON)
        // Kish ESS treats weights as relative and therefore cancels their absolute
        // scientific authority. The local mass cap keeps fractional novelty/stability
        // from impersonating full independent observations while preserving bilinear
        // conservation: the four spatial shares of one observation still sum to one.
        val massBoundedEffectiveSupport = minOf(
            relativeEffectiveSupport,
            localScientificMass.coerceAtLeast(EPSILON),
        )
        return LaneEstimate(
            meanTinjMs = mean,
            varianceMs2 = variance,
            effectiveSupport = massBoundedEffectiveSupport,
            nearestSupportDistanceCells = nearestDistance,
            materialRevision = materialRevision,
        )
    }

    private fun state(lane: FuelLane): LaneState = when (lane) {
        FuelLane.PETROL_REFERENCE -> petrol
        FuelLane.CNG_PETROL_OBSERVED -> cng
    }

    private fun index(rpmIndex: Int, mapIndex: Int): Int = rpmIndex * mapCount + mapIndex

    private data class LocalEstimate(
        val meanTinjMs: Double,
        val varianceMs2: Double,
        val effectiveSupport: Double,
        val scientificMass: Double,
        val materialRevision: Long,
    )

    private class LaneState(nodeCount: Int) {
        val sumW = DoubleArray(nodeCount)
        private val sumW2 = DoubleArray(nodeCount)
        private val sumWTinj = DoubleArray(nodeCount)
        private val sumWTinj2 = DoubleArray(nodeCount)
        private val revision = LongArray(nodeCount)

        fun observe(index: Int, tinjMs: Double, weight: Double, revision: Long) {
            sumW[index] += weight
            sumW2[index] += weight * weight
            sumWTinj[index] += weight * tinjMs
            sumWTinj2[index] += weight * tinjMs * tinjMs
            if (revision > this.revision[index]) this.revision[index] = revision
        }

        fun seedSummary(
            index: Int,
            meanTinjMs: Double,
            secondMomentMs2: Double,
            weight: Double,
            revision: Long,
        ) {
            sumW[index] += weight
            sumW2[index] += weight * weight / LegacyPetrolSeedPolicy.MAX_EFFECTIVE_SUPPORT
            sumWTinj[index] += weight * meanTinjMs
            sumWTinj2[index] += weight * secondMomentMs2
            if (revision > this.revision[index]) this.revision[index] = revision
        }

        fun restore(node: SnapshotNode) {
            sumW[node.index] += node.sumW
            sumW2[node.index] += node.sumW2
            sumWTinj[node.index] += node.sumWTinj
            sumWTinj2[node.index] += node.sumWTinj2
            if (node.materialRevision > revision[node.index]) revision[node.index] = node.materialRevision
        }

        fun snapshotInto(lane: FuelLane, target: MutableList<SnapshotNode>) {
            for (index in sumW.indices) {
                if (sumW[index] <= EPSILON) continue
                target += SnapshotNode(
                    lane = lane,
                    index = index,
                    sumW = sumW[index],
                    sumW2 = sumW2[index],
                    sumWTinj = sumWTinj[index],
                    sumWTinj2 = sumWTinj2[index],
                    materialRevision = revision[index],
                )
            }
        }

        fun clear() {
            sumW.fill(0.0)
            sumW2.fill(0.0)
            sumWTinj.fill(0.0)
            sumWTinj2.fill(0.0)
            revision.fill(0L)
        }

        fun estimate(index: Int): LocalEstimate? {
            val weight = sumW[index]
            if (weight <= EPSILON) return null
            val mean = sumWTinj[index] / weight
            val variance = max(0.0, sumWTinj2[index] / weight - mean * mean)
            val effectiveSupport = if (sumW2[index] <= EPSILON) 0.0 else weight * weight / sumW2[index]
            return LocalEstimate(
                meanTinjMs = mean,
                varianceMs2 = variance,
                effectiveSupport = effectiveSupport.coerceAtLeast(EPSILON),
                scientificMass = weight,
                materialRevision = revision[index],
            )
        }
    }

    private data class AxisPoint(val index: Int, val weight: Double)

    private class AxisWeights(
        private val first: AxisPoint,
        private val second: AxisPoint?,
    ) {
        inline fun forEach(block: (AxisPoint) -> Unit) {
            block(first)
            second?.let(block)
        }
    }

    private fun axisWeights(
        value: Double,
        min: Double,
        max: Double,
        step: Double,
        count: Int,
    ): AxisWeights? {
        if (value < min || value > max) return null
        val position = (value - min) / step
        val lower = floor(position).toInt().coerceIn(0, count - 1)
        val upper = (lower + 1).coerceAtMost(count - 1)
        if (lower == upper) return AxisWeights(AxisPoint(lower, 1.0), null)
        val fraction = (position - lower).coerceIn(0.0, 1.0)
        if (fraction <= EPSILON) return AxisWeights(AxisPoint(lower, 1.0), null)
        if (1.0 - fraction <= EPSILON) return AxisWeights(AxisPoint(upper, 1.0), null)
        return AxisWeights(
            AxisPoint(lower, 1.0 - fraction),
            AxisPoint(upper, fraction),
        )
    }

    companion object {
        internal const val SNAPSHOT_SCHEMA = "omegas-equivalence-surface-v1"
        private const val QUERY_MIN_OFFSET = -1
        private const val QUERY_MAX_OFFSET = 2
        private const val QUERY_DIAMETER = 4
        private const val QUERY_RADIUS_CELLS = 1.5
        private const val EPSILON = 1e-12

        private fun pointCount(min: Double, max: Double, step: Double): Int =
            ceil((max - min) / step).toInt() + 1
    }
}