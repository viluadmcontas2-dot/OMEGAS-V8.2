package com.omegas.prohub.telemetry

/** Freshness monotônica; o limite pertence ao policy/consumer, não ao frame. */
object RuntimeFreshnessPolicy {
    fun classify(
        capturedAtElapsedMs: Long,
        nowElapsedMs: Long,
        maximumAgeMs: Long,
    ): RuntimeFreshness {
        require(maximumAgeMs >= 0L) { "maximumAgeMs inválido" }
        if (capturedAtElapsedMs <= 0L || nowElapsedMs < capturedAtElapsedMs) return RuntimeFreshness.UNKNOWN
        return if (nowElapsedMs - capturedAtElapsedMs <= maximumAgeMs) {
            RuntimeFreshness.CURRENT
        } else {
            RuntimeFreshness.STALE
        }
    }

    fun scientificallyCurrent(freshness: RuntimeFreshness): Boolean =
        freshness == RuntimeFreshness.CURRENT
}
