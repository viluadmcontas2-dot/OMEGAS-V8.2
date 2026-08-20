package com.omegas.prohub.service

/**
 * Cheap producer-side admission for visual fan-out.
 *
 * The service calls this before building HubStatus/OBD JSON for the floating
 * overlay. It deliberately has no Android dependency so cadence semantics stay
 * deterministic and unit-testable. A backwards monotonic-clock sample fails open
 * once and becomes the new cadence origin instead of suppressing the overlay for
 * an arbitrary interval.
 */
class VisualFanoutAdmission(
    private val minimumIntervalMs: Long = 250L,
) {
    init {
        require(minimumIntervalMs >= 0L) { "minimumIntervalMs must be non-negative" }
    }

    private var lastAcceptedAtMs: Long? = null

    @Synchronized
    fun tryAcquire(nowMs: Long, force: Boolean = false): Boolean {
        val last = lastAcceptedAtMs
        val accepted = force || last == null || nowMs < last || nowMs - last >= minimumIntervalMs
        if (accepted) lastAcceptedAtMs = nowMs
        return accepted
    }
}
