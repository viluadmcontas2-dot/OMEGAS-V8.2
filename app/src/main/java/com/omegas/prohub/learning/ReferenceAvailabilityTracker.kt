package com.omegas.prohub.learning

/**
 * Mede a latência real entre a primeira tentativa bloqueada e a primeira
 * referência utilizável subsequente. Não altera comparabilidade nem confiança.
 */
internal data class ReferenceAvailabilityMetric(
    val state: String,
    val timeToReferenceMs: Long,
    val blockReason: String?,
    val measurementOrigin: String = "FIRST_BLOCKED_REFERENCE_ATTEMPT",
)

internal class ReferenceAvailabilityTracker {
    private var blockedSinceNanos: Long? = null
    private var lastBlockReason: String? = null

    @Synchronized
    fun record(
        available: Boolean,
        reasonCode: String,
        nowNanos: Long = System.nanoTime(),
    ): ReferenceAvailabilityMetric {
        require(nowNanos >= 0L)
        if (!available) {
            val started = blockedSinceNanos ?: nowNanos.also { blockedSinceNanos = it }
            lastBlockReason = reasonCode
            return ReferenceAvailabilityMetric(
                state = "BLOCKED",
                timeToReferenceMs = nanosToMillis(nowNanos - started),
                blockReason = reasonCode,
            )
        }

        val started = blockedSinceNanos
        val previousReason = lastBlockReason
        blockedSinceNanos = null
        lastBlockReason = null
        return ReferenceAvailabilityMetric(
            state = "AVAILABLE",
            timeToReferenceMs = if (started == null) 0L else nanosToMillis(nowNanos - started),
            blockReason = previousReason,
        )
    }

    private fun nanosToMillis(value: Long): Long =
        (value.coerceAtLeast(0L) / 1_000_000L)
}

internal object PetrolReferenceAvailability {
    private val tracker = ReferenceAvailabilityTracker()

    fun record(available: Boolean, reasonCode: String): ReferenceAvailabilityMetric =
        tracker.record(available, reasonCode)
}
