package com.omegas.prohub.autocal

/**
 * Valor registrado por um dono (ex.: a ponte da Activity atual). Só o dono atual o limpa: o destroy de uma
 * Activity antiga, que chega depois da nova já ter registrado, não desarma a ponte nova (revisão 2026-10-07 #5).
 */
class OwnedSlot<T : Any> {
    private val lock = Any()
    private var owner: Any? = null
    @Volatile private var value: T? = null

    fun set(newOwner: Any, newValue: T) = synchronized(lock) {
        owner = newOwner
        value = newValue
    }

    fun get(): T? = value

    fun isOwner(candidate: Any): Boolean = synchronized(lock) { owner === candidate }

    /** Limpa só se [candidate] ainda for o dono; devolve se limpou. */
    fun clearIf(candidate: Any): Boolean = synchronized(lock) {
        if (owner !== candidate) return@synchronized false
        owner = null
        value = null
        true
    }
}
