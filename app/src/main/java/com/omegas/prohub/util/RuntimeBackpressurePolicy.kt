package com.omegas.prohub.util

enum class RuntimeWorkLane {
    ACQUISITION,
    SCIENTIFIC_LEDGER,
    VISUAL,
    SECONDARY_READ,
    MANUAL_WRITE,
    SAFETY,
}

enum class OverflowStrategy {
    BYPASS_SECONDARY_QUEUE,
    OVERWRITE_LATEST,
    REJECT_AND_COUNT,
    RESERVED_BOUNDED_WAIT,
}

data class RuntimeQueueRule(
    val capacity: Int,
    val strategy: OverflowStrategy,
)

/**
 * Limites de recurso do runtime; não são thresholds científicos.
 * Capacidades são budgets iniciais para hardware de baixo headroom e devem ser
 * reavaliadas por métricas/soak, nunca usadas para inferir qualidade científica.
 */
object RuntimeBackpressurePolicy {
    const val SCIENTIFIC_PENDING_CAPACITY = 64
    const val SECONDARY_READ_PENDING_CAPACITY = 32
    const val CRITICAL_SERIAL_RESERVED_CAPACITY = 8

    fun rule(lane: RuntimeWorkLane): RuntimeQueueRule = when (lane) {
        RuntimeWorkLane.ACQUISITION -> RuntimeQueueRule(0, OverflowStrategy.BYPASS_SECONDARY_QUEUE)
        RuntimeWorkLane.VISUAL -> RuntimeQueueRule(1, OverflowStrategy.OVERWRITE_LATEST)
        RuntimeWorkLane.SCIENTIFIC_LEDGER -> RuntimeQueueRule(SCIENTIFIC_PENDING_CAPACITY, OverflowStrategy.REJECT_AND_COUNT)
        RuntimeWorkLane.SECONDARY_READ -> RuntimeQueueRule(SECONDARY_READ_PENDING_CAPACITY, OverflowStrategy.REJECT_AND_COUNT)
        RuntimeWorkLane.MANUAL_WRITE,
        RuntimeWorkLane.SAFETY -> RuntimeQueueRule(CRITICAL_SERIAL_RESERVED_CAPACITY, OverflowStrategy.RESERVED_BOUNDED_WAIT)
    }
}
