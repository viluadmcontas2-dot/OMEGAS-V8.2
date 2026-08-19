package com.omegas.prohub.autocal

import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Política read-only de cadência do status AutoCal baseada em custo × informação.
 *
 * O prior default é evidência histórica do veículo/corpus PortmonLOGNOVO e não
 * uma constante de firmware. A sessão atual é somada ao prior, portanto custo
 * e taxa de eventos reais passam a mover a recomendação continuamente.
 *
 * Esta classe não possui clock, thread, scheduler nem I/O. Ela apenas calcula
 * quando o próximo probe compacto deveria ser elegível.
 */
class AutoCalProbeCadencePolicy(
    private val prior: Prior = Prior.portmonLogNovo(),
) {
    data class Prior(
        val observationMs: Long,
        val probes: Long,
        val materialChanges: Long,
        val bootstrapWireCostMs: Double,
        val provenance: String,
    ) {
        init {
            require(observationMs > 0L)
            require(probes > 0L)
            require(materialChanges > 0L)
            require(bootstrapWireCostMs > 0.0 && bootstrapWireCostMs.isFinite())
        }

        val meanCadenceMs: Double
            get() = observationMs.toDouble() / probes.toDouble()

        val eventRatePerMs: Double
            get() = materialChanges.toDouble() / observationMs.toDouble()

        companion object {
            fun portmonLogNovo(): Prior = Prior(
                observationMs = 1_141_516L,
                probes = 434L,
                materialChanges = 4L,
                // 23 bytes no fio × 10 bits/byte em 9600 baud 8N1.
                bootstrapWireCostMs = 23.0 * 10.0 * 1_000.0 / 9_600.0,
                provenance = "PORTMONLOGNOVO_434_STATUS_PROBES_2026_08",
            )
        }
    }

    data class Decision(
        val recommendedCadenceMs: Long,
        val averageProbeCostMs: Double,
        val posteriorEventRatePerSecond: Double,
        val costRatioToPrior: Double,
        val eventRateRatioToPrior: Double,
        val priorMeanCadenceMs: Double,
        val priorProvenance: String,
    )

    fun recommend(metrics: AutoCalProbeMetrics.Snapshot): Decision {
        val costMs = metrics.averageWallElapsedMs
            ?.takeIf { it > 0.0 && it.isFinite() }
            ?: prior.bootstrapWireCostMs

        val posteriorExposureMs = prior.observationMs.toDouble() + metrics.observationSpanMs.coerceAtLeast(0L).toDouble()
        val posteriorChanges = prior.materialChanges.toDouble() + metrics.materialChanges.coerceAtLeast(0L).toDouble()
        val posteriorRatePerMs = posteriorChanges / posteriorExposureMs

        val costRatio = costMs / prior.bootstrapWireCostMs
        val eventRateRatio = posteriorRatePerMs / prior.eventRatePerMs
        val cadence = prior.meanCadenceMs * sqrt(costRatio / eventRateRatio)
        val recommended = cadence
            .takeIf { it.isFinite() && it > 0.0 }
            ?.roundToLong()
            ?.coerceAtLeast(1L)
            ?: prior.meanCadenceMs.roundToLong().coerceAtLeast(1L)

        return Decision(
            recommendedCadenceMs = recommended,
            averageProbeCostMs = costMs,
            posteriorEventRatePerSecond = posteriorRatePerMs * 1_000.0,
            costRatioToPrior = costRatio,
            eventRateRatioToPrior = eventRateRatio,
            priorMeanCadenceMs = prior.meanCadenceMs,
            priorProvenance = prior.provenance,
        )
    }
}
