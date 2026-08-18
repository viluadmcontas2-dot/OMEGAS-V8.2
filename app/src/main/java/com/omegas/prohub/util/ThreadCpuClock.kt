package com.omegas.prohub.util

/** CPU efetiva da thread quando disponível no Android; host sem runtime Android retorna -1. */
internal object ThreadCpuClock {
    fun nowNanos(): Long = try {
        android.os.Debug.threadCpuTimeNanos()
    } catch (_: Throwable) {
        -1L
    }

    fun elapsedMillis(startNanos: Long, endNanos: Long): Long =
        if (startNanos < 0L || endNanos < startNanos) -1L
        else (endNanos - startNanos) / 1_000_000L
}
