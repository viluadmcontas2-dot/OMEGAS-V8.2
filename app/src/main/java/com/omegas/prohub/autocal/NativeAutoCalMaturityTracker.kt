package com.omegas.prohub.autocal

/**
 * Avalia maturidade nativa GNV em três estados explícitos.
 *
 * UNKNOWN significa ausência de evidência suficiente (threshold/shape/counter),
 * nunca FALSE. A primeira leitura válida estabelece baseline sem fabricar
 * transição; leituras posteriores só emitem evento quando cruzam o threshold.
 */
class NativeAutoCalMaturityTracker {
    enum class MaturityState {
        UNKNOWN,
        FALSE,
        TRUE,
    }

    data class Transition(
        val bandIndex: Int,
        val zone: Int,
        val previousCounter: Int,
        val counter: Int,
        val threshold: Int,
        val previousObservedAtElapsedMs: Long,
        val observedAtElapsedMs: Long,
    )

    data class Assessment(
        val states: List<MaturityState>,
        val transitions: List<Transition>,
        val unknownReason: String? = null,
    ) {
        init {
            require(states.size == EXPECTED_BANDS)
        }

        val overallState: MaturityState
            get() = when {
                states.any { it == MaturityState.UNKNOWN } -> MaturityState.UNKNOWN
                states.all { it == MaturityState.TRUE } -> MaturityState.TRUE
                else -> MaturityState.FALSE
            }

        val knownBands: Int get() = states.count { it != MaturityState.UNKNOWN }
        val matureBands: Int get() = states.count { it == MaturityState.TRUE }

        companion object {
            fun unknown(reason: String): Assessment = Assessment(
                states = List(EXPECTED_BANDS) { MaturityState.UNKNOWN },
                transitions = emptyList(),
                unknownReason = reason,
            )
        }
    }

    private var previousCounters: IntArray? = null
    private var previousObservedAtElapsedMs: Long = 0L

    fun reset() {
        previousCounters = null
        previousObservedAtElapsedMs = 0L
    }

    fun baseline(counters: IntArray, observedAtElapsedMs: Long) {
        if (counters.size != EXPECTED_BANDS) {
            previousCounters = null
            previousObservedAtElapsedMs = 0L
            return
        }
        previousCounters = counters.copyOf()
        previousObservedAtElapsedMs = observedAtElapsedMs
    }

    fun observe(
        counters: IntArray,
        gasLowThreshold: Int?,
        gasNormalThreshold: Int?,
        enabled: Boolean,
        observedAtElapsedMs: Long,
    ): Assessment {
        if (counters.size != EXPECTED_BANDS) {
            return Assessment.unknown("COUNTER_SHAPE_UNKNOWN")
        }

        val normalized = counters.copyOf()
        val previous = previousCounters
        val previousAt = previousObservedAtElapsedMs
        previousCounters = normalized
        previousObservedAtElapsedMs = observedAtElapsedMs

        val states = List(EXPECTED_BANDS) { band ->
            val threshold = thresholdForBand(band, gasLowThreshold, gasNormalThreshold)
            when {
                threshold == null || threshold <= 0 -> MaturityState.UNKNOWN
                normalized[band] >= threshold -> MaturityState.TRUE
                else -> MaturityState.FALSE
            }
        }

        val unknownReason = if (states.any { it == MaturityState.UNKNOWN }) {
            "THRESHOLD_UNKNOWN"
        } else null

        if (previous == null || !enabled) {
            return Assessment(states, emptyList(), unknownReason)
        }

        val transitions = buildList {
            repeat(EXPECTED_BANDS) { band ->
                val threshold = thresholdForBand(band, gasLowThreshold, gasNormalThreshold)
                    ?.takeIf { it > 0 }
                    ?: return@repeat
                val before = previous[band]
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
        return Assessment(states, transitions, unknownReason)
    }

    private fun thresholdForBand(
        band: Int,
        gasLowThreshold: Int?,
        gasNormalThreshold: Int?,
    ): Int? = if (band <= 5) gasLowThreshold else gasNormalThreshold

    private fun zone(index: Int): Int = when (index) {
        in 0..5 -> 0
        in 6..9 -> 1
        in 10..13 -> 2
        else -> 3
    }

    companion object {
        const val EXPECTED_BANDS = 18
    }
}
