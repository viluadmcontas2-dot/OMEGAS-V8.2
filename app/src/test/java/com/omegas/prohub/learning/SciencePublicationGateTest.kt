package com.omegas.prohub.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SciencePublicationGateTest {
    @Test
    fun `rolling ten frame windows publish only after eight new frames`() {
        val gate = SciencePublicationGate(minimumNoveltyFraction = 0.75)

        assertTrue(gate.evaluate("PETROL", 0L, 450L, 10, 50L).publish)
        for (step in 1..7) {
            val start = step * 50L
            val end = 450L + step * 50L
            assertFalse("step=$step must remain coalesced", gate.evaluate("PETROL", start, end, 10, 50L).publish)
        }
        assertTrue(gate.evaluate("PETROL", 400L, 850L, 10, 50L).publish)
    }

    @Test
    fun `fresh six frame fast sample publishes immediately`() {
        val gate = SciencePublicationGate(minimumNoveltyFraction = 0.75)
        val decision = gate.evaluate("CNG", 1_000L, 1_250L, 6, 50L)
        assertTrue(decision.publish)
        assertTrue(decision.novelty.fullyNew)
    }

    @Test
    fun `duplicate window never publishes`() {
        val gate = SciencePublicationGate(minimumNoveltyFraction = 0.75)
        assertTrue(gate.evaluate("PETROL", 0L, 450L, 10, 50L).publish)
        val duplicate = gate.evaluate("PETROL", 0L, 450L, 10, 50L)
        assertFalse(duplicate.publish)
        assertTrue(duplicate.novelty.duplicate)
    }

    @Test
    fun `safety boundary forces fresh publication without manufacturing novelty`() {
        val gate = SciencePublicationGate(minimumNoveltyFraction = 0.75)
        assertTrue(gate.evaluate("PETROL", 0L, 450L, 10, 50L).publish)
        val boundary = gate.evaluate(
            key = "PETROL",
            startedAtElapsedMs = 50L,
            endedAtElapsedMs = 500L,
            frameCount = 10,
            medianIntervalMs = 50L,
            forceBoundary = true,
        )
        assertTrue(boundary.publish)
        assertFalse(boundary.novelty.fullyNew)
    }

    @Test
    fun `reset forgets represented window`() {
        val gate = SciencePublicationGate(minimumNoveltyFraction = 0.75)
        assertTrue(gate.evaluate("PETROL", 0L, 450L, 10, 50L).publish)
        gate.reset("PETROL")
        assertTrue(gate.evaluate("PETROL", 50L, 500L, 10, 50L).publish)
    }
}
