package com.omegas.prohub.learning

import com.omegas.prohub.calibration.KMapPhysicalAxes
import kotlin.math.abs

/**
 * Autoridade científica pura do WU-004.
 *
 * Não escreve ECU, não depende de sessão USB e não transforma o centro observado
 * no endereço do Map K. A grade física vem exclusivamente de [KMapPhysicalAxes].
 */
object FixedControlSurfaceScience {
    data class ControlNode(
        val row: Int,
        val column: Int,
        val controlRpm: Double,
        val controlPetrolMs: Double,
    )

    data class VisitObservation(
        val visitKey: String,
        val revision: Int,
        val rpm: Double,
        val petrolMs: Double,
        val residualPercent: Double,
        val quality: Double,
        val collectedAtMs: Long,
        val sessionId: String = "",
    )

    data class PhysicalVisit(
        val visitKey: String,
        val revision: Int,
        val rpm: Double,
        val petrolMs: Double,
        val residualPercent: Double,
        val quality: Double,
        val collectedAtMs: Long,
        val provenanceSessions: Set<String>,
    )

    data class NodeContribution(
        val row: Int,
        val column: Int,
        val revision: Int,
        val visitKey: String,
        val residualPercent: Double,
        val weight: Double,
        val observedRpm: Double,
        val observedPetrolMs: Double,
    )

    data class NodeEvidence(
        val controlNode: ControlNode,
        val supportMass: Double,
        val kishEss: Double,
        val uniqueVisits: Int,
        val observedRpmCenter: Double?,
        val observedPetrolMsCenter: Double?,
        val residualMedianPercent: Double?,
        val residualMadPercent: Double?,
    )

    private val rpmAxis = KMapPhysicalAxes.rpmBins()
    private val petrolAxis = KMapPhysicalAxes.petrolBins()

    fun allNodes(): List<ControlNode> =
        petrolAxis.indices.flatMap { row ->
            rpmAxis.indices.map { column -> controlNode(row, column) }
        }

    fun controlNode(row: Int, column: Int): ControlNode {
        require(row in petrolAxis.indices) { "Map K row outside physical axis: $row" }
        require(column in rpmAxis.indices) { "Map K column outside physical axis: $column" }
        return ControlNode(
            row = row,
            column = column,
            controlRpm = rpmAxis[column].toDouble(),
            controlPetrolMs = petrolAxis[row],
        )
    }

    /**
     * Scientific identity is revision + physical visit key.
     * USB sessions are provenance only and never alter the selected vote.
     */
    fun dedupeVisits(observations: Iterable<VisitObservation>): List<PhysicalVisit> {
        data class Key(val revision: Int, val visitKey: String)
        data class Acc(
            val first: VisitObservation,
            val sessions: LinkedHashSet<String>,
        )

        val grouped = linkedMapOf<Key, Acc>()
        observations.forEach { observation ->
            validate(observation)
            val key = Key(observation.revision, observation.visitKey)
            val existing = grouped[key]
            if (existing == null) {
                grouped[key] = Acc(
                    first = observation,
                    sessions = linkedSetOf<String>().apply {
                        observation.sessionId.takeIf(String::isNotBlank)?.let(::add)
                    },
                )
            } else {
                require(sameScientificObservation(existing.first, observation)) {
                    "Conflicting evidence for physical visit " + observation.revision + ":" + observation.visitKey
                }
                observation.sessionId.takeIf(String::isNotBlank)?.let(existing.sessions::add)
            }
        }

        return grouped.values
            .map { acc ->
                PhysicalVisit(
                    visitKey = acc.first.visitKey,
                    revision = acc.first.revision,
                    rpm = acc.first.rpm,
                    petrolMs = acc.first.petrolMs,
                    residualPercent = acc.first.residualPercent,
                    quality = acc.first.quality,
                    collectedAtMs = acc.first.collectedAtMs,
                    provenanceSessions = acc.sessions.toSet(),
                )
            }
            .sortedWith(compareBy<PhysicalVisit>({ it.collectedAtMs }, { it.revision }, { it.visitKey }))
    }

    fun projectVisit(visit: PhysicalVisit): List<NodeContribution> {
        require(visit.quality.isFinite() && visit.quality in 0.0..1.0) {
            "Visit quality must be inside [0,1]"
        }
        require(visit.rpm.isFinite() && visit.petrolMs.isFinite() && visit.residualPercent.isFinite()) {
            "Visit science fields must be finite"
        }
        return ContinuousLearningMath.bilinearWeights(visit.rpm, visit.petrolMs)
            .map { contribution ->
                NodeContribution(
                    row = contribution.row,
                    column = contribution.column,
                    revision = visit.revision,
                    visitKey = visit.visitKey,
                    residualPercent = visit.residualPercent,
                    weight = visit.quality * contribution.weight,
                    observedRpm = visit.rpm,
                    observedPetrolMs = visit.petrolMs,
                )
            }
            .filter { it.weight > 0.0 }
    }

    fun projectObservations(observations: Iterable<VisitObservation>): List<NodeContribution> =
        dedupeVisits(observations).flatMap(::projectVisit)

    fun evidenceFor(
        row: Int,
        column: Int,
        contributions: Iterable<NodeContribution>,
    ): NodeEvidence {
        val node = controlNode(row, column)
        val local = contributions
            .filter { it.row == row && it.column == column && it.weight > 0.0 }
            .toList()

        if (local.isEmpty()) {
            return NodeEvidence(
                controlNode = node,
                supportMass = 0.0,
                kishEss = 0.0,
                uniqueVisits = 0,
                observedRpmCenter = null,
                observedPetrolMsCenter = null,
                residualMedianPercent = null,
                residualMadPercent = null,
            )
        }

        val supportMass = local.sumOf { it.weight }
        val residualMedian = weightedMedian(local.map { it.residualPercent to it.weight })
        val mad = weightedMedian(local.map { abs(it.residualPercent - residualMedian) to it.weight })
        return NodeEvidence(
            controlNode = node,
            supportMass = supportMass,
            kishEss = ContinuousLearningMath.effectiveSampleSize(local.map { it.weight }),
            uniqueVisits = local.map { it.revision to it.visitKey }.distinct().size,
            observedRpmCenter = weightedMean(local.map { it.observedRpm to it.weight }),
            observedPetrolMsCenter = weightedMean(local.map { it.observedPetrolMs to it.weight }),
            residualMedianPercent = residualMedian,
            residualMadPercent = mad,
        )
    }

    private fun validate(observation: VisitObservation) {
        require(observation.visitKey.isNotBlank()) { "visitKey is required" }
        require(observation.rpm.isFinite() && observation.petrolMs.isFinite()) { "Visit coordinates must be finite" }
        require(observation.residualPercent.isFinite()) { "Residual must be finite" }
        require(observation.quality.isFinite() && observation.quality in 0.0..1.0) { "Quality must be inside [0,1]" }
    }

    private fun sameScientificObservation(a: VisitObservation, b: VisitObservation): Boolean =
        a.revision == b.revision &&
            a.visitKey == b.visitKey &&
            abs(a.rpm - b.rpm) <= 1e-9 &&
            abs(a.petrolMs - b.petrolMs) <= 1e-9 &&
            abs(a.residualPercent - b.residualPercent) <= 1e-9 &&
            abs(a.quality - b.quality) <= 1e-9 &&
            a.collectedAtMs == b.collectedAtMs

    private fun weightedMean(values: List<Pair<Double, Double>>): Double? {
        val total = values.sumOf { it.second }
        if (total <= 0.0) return null
        return values.sumOf { it.first * it.second } / total
    }

    private fun weightedMedian(values: List<Pair<Double, Double>>): Double {
        val sorted = values
            .filter { it.first.isFinite() && it.second.isFinite() && it.second > 0.0 }
            .sortedBy { it.first }
        require(sorted.isNotEmpty()) { "Weighted median needs positive evidence" }
        val half = sorted.sumOf { it.second } / 2.0
        var running = 0.0
        sorted.forEach { (value, weight) ->
            running += weight
            if (running >= half) return value
        }
        return sorted.last().first
    }
}
