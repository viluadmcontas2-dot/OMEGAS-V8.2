package com.omegas.prohub.learning

import com.omegas.prohub.telemetry.RuntimeFreshness

data class ScientificSignal<T>(
    val value: T?,
    val capturedAtElapsedMs: Long?,
    val freshness: RuntimeFreshness,
    val available: Boolean,
    val plausible: Boolean,
    val source: String,
) {
    init {
        require(source.isNotBlank())
        if (available) require(value != null) { "Sinal disponível exige valor" }
        if (capturedAtElapsedMs != null) require(capturedAtElapsedMs >= 0L)
    }

    fun evaluate(nowElapsedMs: Long, maxAgeMs: Long): ScientificSignalEvaluation<T> {
        require(nowElapsedMs >= 0L)
        require(maxAgeMs >= 0L)
        if (!available || value == null) {
            return ScientificSignalEvaluation(false, null, "SIGNAL_UNAVAILABLE", null)
        }
        if (!plausible) {
            return ScientificSignalEvaluation(false, null, "SIGNAL_IMPLAUSIBLE", ageMs(nowElapsedMs))
        }
        if (freshness != RuntimeFreshness.CURRENT) {
            return ScientificSignalEvaluation(
                false,
                null,
                if (freshness == RuntimeFreshness.STALE) "SIGNAL_STALE" else "SIGNAL_FRESHNESS_UNKNOWN",
                ageMs(nowElapsedMs),
            )
        }
        val captured = capturedAtElapsedMs
            ?: return ScientificSignalEvaluation(false, null, "SIGNAL_TIMESTAMP_UNKNOWN", null)
        if (captured > nowElapsedMs) {
            return ScientificSignalEvaluation(false, null, "SIGNAL_TIMESTAMP_IN_FUTURE", captured - nowElapsedMs)
        }
        val age = nowElapsedMs - captured
        if (age > maxAgeMs) {
            return ScientificSignalEvaluation(false, null, "SIGNAL_STALE", age)
        }
        return ScientificSignalEvaluation(true, value, "SIGNAL_CURRENT", age)
    }

    private fun ageMs(nowElapsedMs: Long): Long? =
        capturedAtElapsedMs?.let { if (it <= nowElapsedMs) nowElapsedMs - it else null }
}

data class ScientificSignalEvaluation<T>(
    val usable: Boolean,
    val value: T?,
    val reasonCode: String,
    val ageMs: Long?,
)
