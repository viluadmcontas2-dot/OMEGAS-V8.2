package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorSuggestionLifecycleTest {
    @Test
    fun `happy path requires confirmed mutation before applied and after evidence leaves revalidating`() {
        var record = PredictorSuggestionRecord("s-1", "ideal-rev-A")
        record = PredictorSuggestionLifecycle.reduce(record, PredictorSuggestionEvent.PROMOTE_REVIEWABLE)
        record = PredictorSuggestionLifecycle.reduce(record, PredictorSuggestionEvent.PROMOTE_ACTIONABLE)
        record = PredictorSuggestionLifecycle.reduce(record, PredictorSuggestionEvent.HUMAN_ACCEPT)
        record = PredictorSuggestionLifecycle.reduce(record, PredictorSuggestionEvent.MUTATION_CONFIRMED)
        assertEquals(PredictorSuggestionState.APPLIED, record.state)
        record = PredictorSuggestionLifecycle.reduce(record, PredictorSuggestionEvent.BEGIN_REVALIDATION)
        assertEquals(PredictorSuggestionState.REVALIDATING, record.state)
        record = PredictorSuggestionLifecycle.reduce(record, PredictorSuggestionEvent.AFTER_IMPROVED)
        assertEquals(PredictorSuggestionState.IMPROVED, record.state)
        assertEquals("ideal-rev-A", record.idealTargetRevisionToken)
    }

    @Test
    fun `accepted is not applied until confirmed mutation`() {
        val accepted = PredictorSuggestionRecord("s-2", "ideal").let {
            PredictorSuggestionLifecycle.reduce(it, PredictorSuggestionEvent.PROMOTE_REVIEWABLE)
        }.let { PredictorSuggestionLifecycle.reduce(it, PredictorSuggestionEvent.PROMOTE_ACTIONABLE) }
            .let { PredictorSuggestionLifecycle.reduce(it, PredictorSuggestionEvent.HUMAN_ACCEPT) }
        assertEquals(PredictorSuggestionState.ACCEPTED, accepted.state)
        assertFalse(accepted.state == PredictorSuggestionState.APPLIED)
        assertFails { PredictorSuggestionLifecycle.reduce(accepted, PredictorSuggestionEvent.BEGIN_REVALIDATION) }
    }

    @Test
    fun `rejection stale superseded and failure are explicit terminal branches`() {
        val actionable = PredictorSuggestionRecord("s", "ideal")
            .let { PredictorSuggestionLifecycle.reduce(it, PredictorSuggestionEvent.PROMOTE_REVIEWABLE) }
            .let { PredictorSuggestionLifecycle.reduce(it, PredictorSuggestionEvent.PROMOTE_ACTIONABLE) }
        assertEquals(PredictorSuggestionState.REJECTED, PredictorSuggestionLifecycle.reduce(actionable, PredictorSuggestionEvent.HUMAN_REJECT).state)
        assertEquals(PredictorSuggestionState.STALE, PredictorSuggestionLifecycle.reduce(actionable, PredictorSuggestionEvent.MARK_STALE).state)
        assertEquals(PredictorSuggestionState.SUPERSEDED, PredictorSuggestionLifecycle.reduce(actionable, PredictorSuggestionEvent.MARK_SUPERSEDED).state)
        val accepted = PredictorSuggestionLifecycle.reduce(actionable, PredictorSuggestionEvent.HUMAN_ACCEPT)
        assertEquals(PredictorSuggestionState.FAILED, PredictorSuggestionLifecycle.reduce(accepted, PredictorSuggestionEvent.MUTATION_FAILED).state)
    }

    @Test
    fun `all after evidence events terminate revalidation`() {
        val applied = PredictorSuggestionRecord("s", "ideal")
            .let { PredictorSuggestionLifecycle.reduce(it, PredictorSuggestionEvent.PROMOTE_REVIEWABLE) }
            .let { PredictorSuggestionLifecycle.reduce(it, PredictorSuggestionEvent.PROMOTE_ACTIONABLE) }
            .let { PredictorSuggestionLifecycle.reduce(it, PredictorSuggestionEvent.HUMAN_ACCEPT) }
            .let { PredictorSuggestionLifecycle.reduce(it, PredictorSuggestionEvent.MUTATION_CONFIRMED) }
            .let { PredictorSuggestionLifecycle.reduce(it, PredictorSuggestionEvent.BEGIN_REVALIDATION) }
        val after = listOf(
            PredictorSuggestionEvent.AFTER_IMPROVED to PredictorSuggestionState.IMPROVED,
            PredictorSuggestionEvent.AFTER_CONVERGED to PredictorSuggestionState.CONVERGED,
            PredictorSuggestionEvent.AFTER_NO_CHANGE to PredictorSuggestionState.NO_CHANGE,
            PredictorSuggestionEvent.AFTER_REGRESSED to PredictorSuggestionState.REGRESSED,
            PredictorSuggestionEvent.AFTER_INCONCLUSIVE to PredictorSuggestionState.INCONCLUSIVE,
        )
        after.forEach { (event, state) -> assertEquals(state, PredictorSuggestionLifecycle.reduce(applied, event).state) }
    }

    @Test
    fun `UI churn is structurally absent from lifecycle events`() {
        assertTrue(PredictorSuggestionEvent.entries.none { it.name.contains("UI") || it.name.contains("ROUTE") || it.name.contains("RENDER") })
    }

    private fun assertFails(block: () -> Unit) {
        try { block(); throw AssertionError("expected invalid transition") } catch (_: IllegalArgumentException) { }
    }
}
