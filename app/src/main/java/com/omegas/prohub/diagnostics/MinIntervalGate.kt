package com.omegas.prohub.diagnostics

/**
 * Deixa passar no máximo um evento a cada [minIntervalMs] (limite de taxa da gravação,
 * para fonte que publica rápido não inflar o log). Relógio que volta para trás libera
 * e recomeça a contagem, em vez de travar.
 */
class MinIntervalGate(private val minIntervalMs: Long) {
    private var lastAt = Long.MIN_VALUE

    @Synchronized
    fun allow(nowMs: Long): Boolean {
        if (lastAt != Long.MIN_VALUE && nowMs >= lastAt && nowMs - lastAt < minIntervalMs) return false
        lastAt = nowMs
        return true
    }

    @Synchronized
    fun reset() {
        lastAt = Long.MIN_VALUE
    }
}
