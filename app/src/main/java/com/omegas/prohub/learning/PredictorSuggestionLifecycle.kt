package com.omegas.prohub.learning

enum class PredictorSuggestionState {
    CANDIDATE,
    REVIEWABLE,
    ACTIONABLE,
    ACCEPTED,
    REJECTED,
    STALE,
    SUPERSEDED,
    APPLIED,
    FAILED,
    REVALIDATING,
    IMPROVED,
    CONVERGED,
    NO_CHANGE,
    REGRESSED,
    INCONCLUSIVE,
}

enum class PredictorSuggestionEvent {
    PROMOTE_REVIEWABLE,
    PROMOTE_ACTIONABLE,
    HUMAN_ACCEPT,
    HUMAN_REJECT,
    MARK_STALE,
    MARK_SUPERSEDED,
    MUTATION_CONFIRMED,
    MUTATION_FAILED,
    BEGIN_REVALIDATION,
    AFTER_IMPROVED,
    AFTER_CONVERGED,
    AFTER_NO_CHANGE,
    AFTER_REGRESSED,
    AFTER_INCONCLUSIVE,
}

data class PredictorSuggestionRecord(
    val id: String,
    val idealTargetRevisionToken: String,
    val state: PredictorSuggestionState = PredictorSuggestionState.CANDIDATE,
    val lifecycleRevision: Long = 0L,
) {
    init {
        require(id.isNotBlank())
        require(idealTargetRevisionToken.isNotBlank())
        require(lifecycleRevision >= 0L)
    }
}

/**
 * Pure lifecycle reducer. It can change lifecycle state only; the IdealTarget
 * revision token is intentionally immutable across every transition.
 */
object PredictorSuggestionLifecycle {
    fun reduce(
        record: PredictorSuggestionRecord,
        event: PredictorSuggestionEvent,
    ): PredictorSuggestionRecord {
        val next = transition(record.state, event)
        return record.copy(
            state = next,
            lifecycleRevision = increment(record.lifecycleRevision),
        )
    }

    private fun transition(
        state: PredictorSuggestionState,
        event: PredictorSuggestionEvent,
    ): PredictorSuggestionState = when (state) {
        PredictorSuggestionState.CANDIDATE -> when (event) {
            PredictorSuggestionEvent.PROMOTE_REVIEWABLE -> PredictorSuggestionState.REVIEWABLE
            PredictorSuggestionEvent.MARK_STALE -> PredictorSuggestionState.STALE
            PredictorSuggestionEvent.MARK_SUPERSEDED -> PredictorSuggestionState.SUPERSEDED
            else -> invalid(state, event)
        }
        PredictorSuggestionState.REVIEWABLE -> when (event) {
            PredictorSuggestionEvent.PROMOTE_ACTIONABLE -> PredictorSuggestionState.ACTIONABLE
            PredictorSuggestionEvent.MARK_STALE -> PredictorSuggestionState.STALE
            PredictorSuggestionEvent.MARK_SUPERSEDED -> PredictorSuggestionState.SUPERSEDED
            else -> invalid(state, event)
        }
        PredictorSuggestionState.ACTIONABLE -> when (event) {
            PredictorSuggestionEvent.HUMAN_ACCEPT -> PredictorSuggestionState.ACCEPTED
            PredictorSuggestionEvent.HUMAN_REJECT -> PredictorSuggestionState.REJECTED
            PredictorSuggestionEvent.MARK_STALE -> PredictorSuggestionState.STALE
            PredictorSuggestionEvent.MARK_SUPERSEDED -> PredictorSuggestionState.SUPERSEDED
            else -> invalid(state, event)
        }
        PredictorSuggestionState.ACCEPTED -> when (event) {
            PredictorSuggestionEvent.MUTATION_CONFIRMED -> PredictorSuggestionState.APPLIED
            PredictorSuggestionEvent.MUTATION_FAILED -> PredictorSuggestionState.FAILED
            PredictorSuggestionEvent.MARK_STALE -> PredictorSuggestionState.STALE
            PredictorSuggestionEvent.MARK_SUPERSEDED -> PredictorSuggestionState.SUPERSEDED
            else -> invalid(state, event)
        }
        PredictorSuggestionState.APPLIED -> when (event) {
            PredictorSuggestionEvent.BEGIN_REVALIDATION -> PredictorSuggestionState.REVALIDATING
            else -> invalid(state, event)
        }
        PredictorSuggestionState.REVALIDATING -> when (event) {
            PredictorSuggestionEvent.AFTER_IMPROVED -> PredictorSuggestionState.IMPROVED
            PredictorSuggestionEvent.AFTER_CONVERGED -> PredictorSuggestionState.CONVERGED
            PredictorSuggestionEvent.AFTER_NO_CHANGE -> PredictorSuggestionState.NO_CHANGE
            PredictorSuggestionEvent.AFTER_REGRESSED -> PredictorSuggestionState.REGRESSED
            PredictorSuggestionEvent.AFTER_INCONCLUSIVE -> PredictorSuggestionState.INCONCLUSIVE
            else -> invalid(state, event)
        }
        PredictorSuggestionState.REJECTED,
        PredictorSuggestionState.STALE,
        PredictorSuggestionState.SUPERSEDED,
        PredictorSuggestionState.FAILED,
        PredictorSuggestionState.IMPROVED,
        PredictorSuggestionState.CONVERGED,
        PredictorSuggestionState.NO_CHANGE,
        PredictorSuggestionState.REGRESSED,
        PredictorSuggestionState.INCONCLUSIVE,
        -> invalid(state, event)
    }

    private fun invalid(
        state: PredictorSuggestionState,
        event: PredictorSuggestionEvent,
    ): Nothing = throw IllegalArgumentException("Invalid Predictor Suggestion transition: $state + $event")

    private fun increment(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1L
}
