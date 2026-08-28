package com.omegas.prohub.learning

/**
 * Microjanela científica curta para correlacionar eventos AutoCal com o contexto MP48.
 *
 * Não é histórico visual nem log de sessão. Mantém apenas os poucos sinais físicos
 * necessários, com sequência monotônica, limite por quantidade e limite por idade.
 */
class NativeAnchorTelemetryWindow(
    private val maxFrames: Int = 256,
    private val maxAgeMs: Long = 10_000L,
) {
    data class Frame(
        val sequence: Long,
        val elapsedMs: Long,
        val rpm: Int,
        val mapBar: Double,
        val petrolMs: Double,
        val fuel: String,
        val sessionId: Long = 0L,
        val gasMsDiagnostic: Double? = null,
        val plausible: Boolean = true,
    )

    private val lock = Any()
    private val frames = ArrayDeque<Frame>()
    private var sequence = 0L

    init {
        require(maxFrames > 0)
        require(maxAgeMs > 0L)
    }

    fun reset() = synchronized(lock) {
        frames.clear()
        sequence = 0L
    }

    fun record(
        elapsedMs: Long,
        rpm: Int,
        mapBar: Double,
        petrolMs: Double,
        fuel: String,
        sessionId: Long = 0L,
        gasMsDiagnostic: Double? = null,
        plausible: Boolean = true,
    ): Frame = synchronized(lock) {
        sequence += 1L
        val frame = Frame(
            sequence = sequence,
            elapsedMs = elapsedMs,
            rpm = rpm,
            mapBar = mapBar,
            petrolMs = petrolMs,
            fuel = fuel,
            sessionId = sessionId,
            gasMsDiagnostic = gasMsDiagnostic,
            plausible = plausible,
        )
        frames.addLast(frame)
        trimLocked(elapsedMs)
        frame
    }

    fun between(fromElapsedMs: Long, toElapsedMs: Long): List<Frame> = synchronized(lock) {
        if (toElapsedMs < fromElapsedMs) return@synchronized emptyList()
        frames.filter { it.elapsedMs in fromElapsedMs..toElapsedMs }
    }

    fun snapshot(): List<Frame> = synchronized(lock) { frames.toList() }

    fun size(): Int = synchronized(lock) { frames.size }

    private fun trimLocked(nowElapsedMs: Long) {
        val minimumElapsedMs = nowElapsedMs - maxAgeMs
        while (frames.isNotEmpty() && frames.first().elapsedMs < minimumElapsedMs) {
            frames.removeFirst()
        }
        while (frames.size > maxFrames) {
            frames.removeFirst()
        }
    }
}
