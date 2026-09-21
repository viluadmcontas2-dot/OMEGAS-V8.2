package com.omegas.prohub.autocal

/**
 * Detecta somente a transição de uma banda GNV para o critério nativo de maturidade.
 *
 * A primeira leitura cria baseline. Crescimento posterior só reabre correlação quando a
 * banda já estava madura no baseline ou uma tentativa anterior falhou explicitamente.
 * Quando a AutoCal está pausada, a leitura atualiza o baseline sem produzir ciência nova.
 */
class NativeAutoCalMaturityTracker {
    data class Transition(
        val bandIndex: Int,
        val zone: Int,
        val previousCounter: Int,
        val counter: Int,
        val threshold: Int,
        val previousObservedAtElapsedMs: Long,
        val observedAtElapsedMs: Long,
        val correlationRetry: Boolean = false,
    )

    private var previousCounters: IntArray? = null
    private var previousObservedAtElapsedMs: Long = 0L
    private val retryableCorrelationBands = mutableSetOf<Int>()
    private val correlatedBands = mutableSetOf<Int>()

    fun reset() {
        previousCounters = null
        previousObservedAtElapsedMs = 0L
        retryableCorrelationBands.clear()
        correlatedBands.clear()
    }

    fun recordCorrelationResult(bandIndex: Int, correlated: Boolean) {
        if (bandIndex !in 0 until 18) return
        if (correlated) {
            correlatedBands.add(bandIndex)
            retryableCorrelationBands.remove(bandIndex)
        } else if (bandIndex !in correlatedBands) {
            retryableCorrelationBands.add(bandIndex)
        }
    }

    fun baseline(counters: IntArray, observedAtElapsedMs: Long) {
        previousCounters = counters.copyOf(18)
        previousObservedAtElapsedMs = observedAtElapsedMs
    }

    fun observe(
        counters: IntArray,
        gasLowThreshold: Int?,
        gasNormalThreshold: Int?,
        enabled: Boolean,
        observedAtElapsedMs: Long,
    ): List<Transition> {
        val normalized = counters.copyOf(18)
        val previous = previousCounters
        val previousAt = previousObservedAtElapsedMs
        previousCounters = normalized
        previousObservedAtElapsedMs = observedAtElapsedMs

        if (gasLowThreshold == null || gasNormalThreshold == null) return emptyList()

        fun thresholdFor(band: Int): Int = if (band <= 5) gasLowThreshold else gasNormalThreshold

        if (previous == null) {
            if (enabled) {
                repeat(18) { band ->
                    val threshold = thresholdFor(band)
                    if (threshold > 0 && normalized[band] >= threshold && band !in correlatedBands) {
                        retryableCorrelationBands.add(band)
                    }
                }
            }
            return emptyList()
        }
        if (!enabled) return emptyList()

        return buildList {
            repeat(18) { band ->
                val threshold = thresholdFor(band)
                if (threshold <= 0) return@repeat
                val before = previous.getOrElse(band) { 0 }
                val after = normalized[band]

                if (after < before) {
                    correlatedBands.remove(band)
                    retryableCorrelationBands.remove(band)
                    if (after >= threshold) retryableCorrelationBands.add(band)
                    return@repeat
                }
                if (after < threshold) {
                    correlatedBands.remove(band)
                    retryableCorrelationBands.remove(band)
                    return@repeat
                }

                val crossedThreshold = before < threshold && after >= threshold
                val retryGrowth = !crossedThreshold &&
                    band in retryableCorrelationBands &&
                    band !in correlatedBands &&
                    after > before

                if (crossedThreshold || retryGrowth) {
                    add(
                        Transition(
                            bandIndex = band,
                            zone = zone(band),
                            previousCounter = before,
                            counter = after,
                            threshold = threshold,
                            previousObservedAtElapsedMs = previousAt,
                            observedAtElapsedMs = observedAtElapsedMs,
                            correlationRetry = retryGrowth,
                        ),
                    )
                }
            }
        }
    }

    private fun zone(index: Int): Int = when (index) {
        in 0..5 -> 0
        in 6..9 -> 1
        in 10..13 -> 2
        else -> 3
    }
}
