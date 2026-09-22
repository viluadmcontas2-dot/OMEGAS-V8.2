package com.omegas.prohub.autocal

data class AutoCalEpochTransition(
    val before: Int,
    val after: Int,
) {
    val rollover: Boolean get() = after < before

    companion object {
        fun between(previous: Int?, current: Int): AutoCalEpochTransition? {
            if (previous == null || previous == current) return null
            return AutoCalEpochTransition(before = previous, after = current)
        }
    }
}
