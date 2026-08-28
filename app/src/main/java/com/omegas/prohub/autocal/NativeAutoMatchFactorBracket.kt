package com.omegas.prohub.autocal

/** Resultado read-only do bracket MUL_ACT ao redor de um evento AutoMatch. */
object NativeAutoMatchFactorBracket {
    enum class State {
        FACTOR_CHANGE_CONFIRMED,
        NO_FACTOR_CHANGE_OBSERVED,
        INCONCLUSIVE,
    }

    data class Result(
        val state: State,
        val beforeHash: String?,
        val afterHash: String?,
        val physicalChangeKnown: Boolean,
    )

    fun evaluate(beforeHash: String?, afterHash: String?): Result {
        val before = beforeHash?.takeIf { it.isNotBlank() }
        val after = afterHash?.takeIf { it.isNotBlank() }
        if (before == null || after == null) {
            return Result(State.INCONCLUSIVE, before, after, false)
        }
        if (before == after) {
            return Result(State.NO_FACTOR_CHANGE_OBSERVED, before, after, false)
        }
        return Result(State.FACTOR_CHANGE_CONFIRMED, before, after, true)
    }
}
