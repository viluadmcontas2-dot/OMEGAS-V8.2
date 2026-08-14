package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningEvidenceBudgetTest {
    private data class Native(val snapshot: String, val band: Int)
    private data class Visit(val id: String, val lastSeen: Long)

    @Test
    fun `native evidence keeps complete newest snapshot groups`() {
        val source = (1..24).flatMap { snapshot ->
            (0 until 18).map { band -> Native("s$snapshot", band) }
        }
        val kept = LearningEvidenceBudget.retainNewestSnapshotGroups(source, { it.snapshot })
        val ids = kept.map { it.snapshot }.distinct()

        assertEquals(LearningEvidenceBudget.MAX_NATIVE_SNAPSHOTS, ids.size)
        assertEquals("s9", ids.first())
        assertEquals("s24", ids.last())
        assertTrue(ids.all { id -> kept.count { it.snapshot == id } == 18 })
    }

    @Test
    fun `visit budget keeps newest activity instead of insertion order`() {
        val source = (1..400).map { index -> Visit("v$index", lastSeen = index.toLong()) }
        val kept = LearningEvidenceBudget.retainNewestVisits(source, { it.lastSeen })

        assertEquals(LearningEvidenceBudget.MAX_VISIT_ACCUMULATORS, kept.size)
        assertEquals("v145", kept.first().id)
        assertEquals("v400", kept.last().id)
    }
}
