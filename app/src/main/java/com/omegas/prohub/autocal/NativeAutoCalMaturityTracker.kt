package com.omegas.prohub.autocal

/**
 * Detecta somente a transição de uma banda GNV para o critério nativo de maturidade.
 *
 * A primeira leitura cria baseline. Leituras repetidas ou crescimento posterior de uma
 * banda já madura não criam outro evento. Quando a AutoCal está pausada, a leitura
 * atualiza o baseline sem produzir ciência nova.
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
    )

    private var previousCounters: IntArray? = null
    private var previousObservedAtElapsedMs: Long = 0L

    fun reset() {
        previousCounters = null
        previousObservedAtElapsedMs = 0L
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

        if (previous == null || !enabled) return emptyList()
        if (gasLowThreshold == null || gasNormalThreshold == null) return emptyList()

        return buildList {
            repeat(18) { band ->
                val threshold = if (band <= 5) gasLowThreshold else gasNormalThreshold
                if (threshold <= 0) return@repeat
                val before = previous.getOrElse(band) { 0 }
                val after = normalized[band]
                if (before < threshold && after >= threshold) {
                    add(
                        Transition(
                            bandIndex = band,
                            zone = zone(band),
                            previousCounter = before,
                            counter = after,
                            threshold = threshold,
                            previousObservedAtElapsedMs = previousAt,
                            observedAtElapsedMs = observedAtElapsedMs,
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
