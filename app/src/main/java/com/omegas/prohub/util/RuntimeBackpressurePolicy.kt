package com.omegas.prohub.util

/**
 * Budgets de admissão do hotfix RED.
 *
 * Estes limites governam apenas espera concorrente diante da authority serial
 * já existente. Eles não criam fila, thread, transporte ou segunda authority.
 */
object RuntimeBackpressurePolicy {
    const val SECONDARY_READ_PENDING_CAPACITY = 32
    const val CRITICAL_SERIAL_RESERVED_CAPACITY = 8
}
