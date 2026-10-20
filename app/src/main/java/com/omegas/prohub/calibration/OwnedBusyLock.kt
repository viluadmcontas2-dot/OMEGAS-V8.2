package com.omegas.prohub.calibration

import java.util.concurrent.atomic.AtomicLong

/**
 * "Ocupado" com dono para os escritores K: quem adquire recebe um token e só esse token libera.
 * Liberar de novo (ex.: o fim da conferência em segundo plano) ou com token de outra operação não faz nada,
 * então uma operação nunca apaga o "ocupado" de outra.
 */
class OwnedBusyLock {
    private val owner = AtomicLong(0L)
    private val generation = AtomicLong(0L)

    fun isBusy(): Boolean = owner.get() != 0L

    /** Token (> 0) se adquiriu; 0 se outra operação já está com o "ocupado". */
    fun tryAcquire(): Long {
        val token = generation.incrementAndGet()
        return if (owner.compareAndSet(0L, token)) token else 0L
    }

    /** true só na primeira liberação feita pelo dono. */
    fun release(token: Long): Boolean = token != 0L && owner.compareAndSet(token, 0L)
}
