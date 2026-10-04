package com.omegas.prohub.autocal

/**
 * Recuo do probe (status leve) quando a ECU não responde a ele enquanto a telemetria flui: 2 s, 4 s, 8 s ...
 * teto 30 s, zera no sucesso. Sem isto, o probe que falha (~1,6 s de serial) volta no tique seguinte (100 ms)
 * e esfomeia o cursor do vivo. Só decide QUANDO tentar de novo; não conhece comando algum.
 */
class ProbeBackoff {
    private var failures = 0
    @Volatile var untilMs = 0L
        private set

    @Synchronized
    fun onFailure(nowMs: Long) {
        failures = (failures + 1).coerceAtMost(MAX_EXPONENT)
        untilMs = nowMs + (BASE_MS shl (failures - 1)).coerceAtMost(CAP_MS)
    }

    @Synchronized
    fun onSuccess() {
        failures = 0
        untilMs = 0L
    }

    @Synchronized
    fun reset() = onSuccess()

    fun active(nowMs: Long): Boolean = nowMs < untilMs

    companion object {
        const val BASE_MS = 2_000L
        const val CAP_MS = 30_000L
        private const val MAX_EXPONENT = 5
    }
}
