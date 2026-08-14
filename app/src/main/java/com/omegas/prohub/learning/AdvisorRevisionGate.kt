package com.omegas.prohub.learning

import kotlin.math.roundToLong

/**
 * Decide quando uma mudança científica merece republicar o Advisor.
 *
 * Não existe timer: a revisão avança somente quando o token semântico muda.
 * O token é construído pelo SignalLearningStore a partir de comparação ou
 * referência de gasolina, nunca de frame visual/telemetria bruta.
 */
internal class AdvisorRevisionGate {
    private var lastToken: String? = null
    private var revision: Long = 0L

    @Synchronized
    fun revise(token: String?): Long? {
        val normalized = token?.takeIf { it.isNotBlank() } ?: return null
        if (normalized == lastToken) return null
        lastToken = normalized
        revision = saturatingIncrement(revision)
        return revision
    }

    @Synchronized
    fun force(): Long {
        lastToken = null
        revision = saturatingIncrement(revision)
        return revision
    }

    @Synchronized
    fun currentRevision(): Long = revision

    companion object {
        fun observationMilestone(count: Int): Int {
            val safe = count.coerceAtLeast(1)
            return Integer.highestOneBit(safe)
        }

        fun quantize(value: Double, step: Double): Long {
            if (!value.isFinite()) return Long.MIN_VALUE
            return (value / step.coerceAtLeast(1e-9)).roundToLong()
        }

        private fun saturatingIncrement(value: Long): Long =
            if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
    }
}
