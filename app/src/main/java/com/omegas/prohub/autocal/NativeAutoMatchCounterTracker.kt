package com.omegas.prohub.autocal

/**
 * Converte aumento observado de NUM_AUTOMATCH_EXECUTED em evento causal nativo.
 *
 * Não lê ECU, não agenda I/O e não infere mudança de MUL_ACT. A primeira
 * observação de cada sessão é somente baseline.
 */
class NativeAutoMatchCounterTracker {
    data class Event(
        val eventType: String = "AUTOMATCH_COUNT_INCREASED",
        val sessionId: Long,
        val observedAtElapsedMs: Long,
        val beforeCount: Int,
        val afterCount: Int,
        val delta: Int,
        val mulActChangeConfirmed: Boolean = false,
    )

    private var sessionId: Long = 0L
    private var previousCount: Int? = null

    fun reset() {
        sessionId = 0L
        previousCount = null
    }

    fun observe(
        currentSessionId: Long,
        count: Int,
        observedAtElapsedMs: Long,
    ): Event? {
        if (currentSessionId <= 0L || count < 0) return null
        if (sessionId != currentSessionId) {
            sessionId = currentSessionId
            previousCount = count
            return null
        }

        val before = previousCount
        previousCount = count
        if (before == null || count <= before) return null

        return Event(
            sessionId = currentSessionId,
            observedAtElapsedMs = observedAtElapsedMs,
            beforeCount = before,
            afterCount = count,
            delta = count - before,
        )
    }
}
