package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoMatchEvidenceBracketTest {
    private fun event(before: Int = 1, after: Int = 2) = NativeAutoMatchCounterTracker.Event(
        sessionId = 77L,
        observedAtElapsedMs = 2_000L,
        beforeCount = before,
        afterCount = after,
        delta = after - before,
    )

    private fun stable(count: Int = 1, values: IntArray = IntArray(30) { 0x4000 }) =
        NativeAutoMatchEvidenceBracket.StableVector(
            sessionId = 77L,
            autoMatchCount = count,
            capturedAtElapsedMs = 1_900L,
            rawValues = values,
            rawPayloadHex = "BEFORE",
        )

    @Test
    fun `contador avancou e um fator mudou fecha causalidade observada`() {
        val after = IntArray(30) { 0x4000 }.also { it[10] = 0x4CCC }
        val result = NativeAutoMatchEvidenceBracket.evaluate(
            event = event(),
            before = stable(),
            afterSessionId = 77L,
            afterAutoMatchCount = 2,
            afterCapturedAtElapsedMs = 2_100L,
            afterRaw = after,
            afterPayloadHex = "AFTER",
        )

        assertEquals(NativeAutoMatchEvidenceBracket.State.FACTOR_CHANGE_CONFIRMED, result.state)
        assertEquals(1, result.changedPointCount)
        assertEquals(10, result.pointDeltas.single().index)
        assertEquals(0x4000, result.pointDeltas.single().beforeRaw)
        assertEquals(0x4CCC, result.pointDeltas.single().afterRaw)
        assertTrue(result.physicalChangeKnown)
    }

    @Test
    fun `contador avancou sem mudar MUL ACT nao inventa mudanca`() {
        val result = NativeAutoMatchEvidenceBracket.evaluate(
            event = event(),
            before = stable(),
            afterSessionId = 77L,
            afterAutoMatchCount = 2,
            afterCapturedAtElapsedMs = 2_100L,
            afterRaw = IntArray(30) { 0x4000 },
        )

        assertEquals(NativeAutoMatchEvidenceBracket.State.NO_FACTOR_CHANGE_OBSERVED, result.state)
        assertEquals(0, result.changedPointCount)
        assertTrue(result.physicalChangeKnown)
    }

    @Test
    fun `before de outro contador falha fechado`() {
        val result = NativeAutoMatchEvidenceBracket.evaluate(
            event = event(1, 2),
            before = stable(count = 0),
            afterSessionId = 77L,
            afterAutoMatchCount = 2,
            afterCapturedAtElapsedMs = 2_100L,
            afterRaw = IntArray(30) { 0x4000 },
        )

        assertEquals(NativeAutoMatchEvidenceBracket.State.INCONCLUSIVE, result.state)
        assertEquals("BEFORE_COUNT_MISMATCH", result.reason)
        assertFalse(result.physicalChangeKnown)
    }

    @Test
    fun `snapshot depois com contador diferente falha fechado`() {
        val result = NativeAutoMatchEvidenceBracket.evaluate(
            event = event(1, 2),
            before = stable(count = 1),
            afterSessionId = 77L,
            afterAutoMatchCount = 3,
            afterCapturedAtElapsedMs = 2_100L,
            afterRaw = IntArray(30) { 0x4000 },
        )

        assertEquals(NativeAutoMatchEvidenceBracket.State.INCONCLUSIVE, result.state)
        assertEquals("AFTER_COUNT_MISMATCH", result.reason)
    }
}
