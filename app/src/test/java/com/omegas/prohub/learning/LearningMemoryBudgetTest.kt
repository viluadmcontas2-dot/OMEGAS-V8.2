package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningMemoryBudgetTest {
    @Test
    fun `long provenance trajectory stabilizes at configured newest id bounds`() {
        val visits = linkedSetOf<String>()
        repeat(10_000) { index ->
            visits += "visit-$index"
            LearningMemoryBudget.trimNewestIds(visits, LearningMemoryBudget.MAX_REGION_VISIT_IDS)
        }
        assertEquals(LearningMemoryBudget.MAX_REGION_VISIT_IDS, visits.size)
        assertEquals("visit-${10_000 - LearningMemoryBudget.MAX_REGION_VISIT_IDS}", visits.first())
        assertEquals("visit-9999", visits.last())
    }

    @Test
    fun `sidecar trajectory stabilizes native snapshots visits and provenance`() {
        data class Item(val id: String, val seen: Long)
        val snapshots = (0 until 1_000).map { Item("snapshot-$it", it.toLong()) }
        val keptSnapshots = LearningEvidenceBudget.retainNewestSnapshotGroups(snapshots, { it.id })
        assertEquals(LearningEvidenceBudget.MAX_NATIVE_SNAPSHOTS, keptSnapshots.size)

        val visits = (0 until 10_000).map { Item("visit-$it", it.toLong()) }
        val keptVisits = LearningEvidenceBudget.retainNewestVisits(visits, { it.seen })
        assertEquals(LearningEvidenceBudget.MAX_VISIT_ACCUMULATORS, keptVisits.size)
        assertEquals("visit-9999", keptVisits.last().id)

        val provenance = LearningEvidenceBudget.retainNewestEntries((0 until 10_000).toList())
        assertEquals(LearningEvidenceBudget.MAX_PROVENANCE_ENTRIES, provenance.size)
        assertEquals(9_999, provenance.last())
    }

    @Test
    fun `all hot and persisted budget constants are finite positive bounds`() {
        assertTrue(LearningMemoryBudget.MAX_REGION_VISIT_IDS > 0)
        assertTrue(LearningMemoryBudget.MAX_REGION_SESSION_IDS > 0)
        assertTrue(LearningMemoryBudget.TARGET_PERSISTED_BYTES > 0)
        assertTrue(LearningEvidenceBudget.MAX_NATIVE_SNAPSHOTS > 0)
        assertTrue(LearningEvidenceBudget.MAX_NATIVE_ANCHORS > 0)
        assertTrue(LearningEvidenceBudget.MAX_VISIT_ACCUMULATORS > 0)
        assertTrue(LearningEvidenceBudget.MAX_PROVENANCE_ENTRIES > 0)
        assertTrue(LearningEvidenceBudget.MAX_PERSISTED_BYTES > 0)
    }
}
