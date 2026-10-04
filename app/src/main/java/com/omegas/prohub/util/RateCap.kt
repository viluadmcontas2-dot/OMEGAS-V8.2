package com.omegas.prohub.util

/**
 * Teto por janela fixa (padrão 1 s) para fluxos que podem inundar a gravação da sessão
 * (console.* da WebView, app_log). Só decide "entra ou não"; nunca toca a ECU.
 */
class RateCap(private val maxPerWindow: Int, private val windowMs: Long = 1_000L) {
    private var windowStart = Long.MIN_VALUE
    private var count = 0
    private var dropped = 0L

    @Synchronized
    fun allow(nowMs: Long): Boolean {
        if (windowStart == Long.MIN_VALUE || nowMs < windowStart || nowMs - windowStart >= windowMs) {
            windowStart = nowMs
            count = 0
        }
        return if (count < maxPerWindow) {
            count += 1
            true
        } else {
            dropped += 1
            false
        }
    }

    /** Quantas mensagens o teto descartou desde a última chamada (para um aviso único, sem inundar). */
    @Synchronized
    fun takeDropped(): Long {
        val value = dropped
        dropped = 0L
        return value
    }
}
