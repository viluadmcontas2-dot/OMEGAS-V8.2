package com.omegas.prohub.learning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLearningAnchorTest {
    @Test
    fun `correlated maturity creates positional anchor without comparison vote`() {
        val anchor = NativeLearningAnchor.fromMaturityEvent(baseEvent(), calibrationEpoch = 7)
        assertNotNull(anchor)
        anchor!!
        assertEquals(7, anchor.calibrationEpoch)
        assertEquals(9L, anchor.sessionId)
        assertTrue(anchor.nativeValidity)
        assertEquals("GNV", anchor.fuel)
        assertEquals(2450, anchor.rpm)
        assertEquals(7.2, anchor.gasMsDiagnostic!!, 1e-9)
        assertEquals(1500L, anchor.correlatedFrameElapsedMs)
        assertEquals(500L, anchor.lagMs)
        assertEquals(0.74, anchor.rpmConfidence, 1e-9)
        assertFalse(anchor.toJson().getBoolean("comparisonVote"))
        assertFalse(anchor.toJson().getBoolean("automaticWrite"))
    }

    @Test
    fun `unreliable correlation does not create native learning anchor`() {
        val anchor = NativeLearningAnchor.fromMaturityEvent(
            baseEvent().put("correlationState", "NO_RELIABLE_CORRELATION"),
            calibrationEpoch = 2,
        )
        assertNull(anchor)
    }

    @Test
    fun `zero confidence correlation does not create native learning anchor`() {
        assertNull(NativeLearningAnchor.fromMaturityEvent(baseEvent().put("rpmConfidence", 0.0), 2))
        assertNull(NativeLearningAnchor.fromMaturityEvent(baseEvent().put("correlationConfidence", 0.0), 2))
    }

    @Test
    fun `same physical passage has stable fingerprint across snapshots`() {
        val first = NativeLearningAnchor.fromMaturityEvent(baseEvent().put("snapshotId", "A"), 3)!!
        val second = NativeLearningAnchor.fromMaturityEvent(baseEvent().put("snapshotId", "B"), 3)!!
        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `registry rejects a different fingerprint for the same physical overlap`() {
        val registry = NativeLearningAnchorRegistry(maxEntries = 4)
        val first = NativeLearningAnchor.fromMaturityEvent(baseEvent().put("bandIndex", 1), 1)!!
        val samePassage = NativeLearningAnchor.fromMaturityEvent(baseEvent().put("bandIndex", 2), 1)!!

        assertTrue(first.fingerprint != samePassage.fingerprint)
        assertEquals(first.overlapKey, samePassage.overlapKey)
        assertTrue(registry.upsert(first))
        assertFalse(registry.upsert(samePassage))
        assertEquals(1L, registry.currentRevision())
        assertEquals(listOf(first.fingerprint), registry.snapshot().map { it.fingerprint })
    }

    @Test
    fun `registry revises only new physical passages and remains bounded`() {
        val registry = NativeLearningAnchorRegistry(maxEntries = 2)
        val a = NativeLearningAnchor.fromMaturityEvent(passageEvent(1, 110L, 118L), 1)!!
        val b = NativeLearningAnchor.fromMaturityEvent(passageEvent(2, 120L, 128L), 1)!!
        val c = NativeLearningAnchor.fromMaturityEvent(passageEvent(3, 130L, 138L), 1)!!

        assertTrue(registry.upsert(a))
        assertEquals(1L, registry.currentRevision())
        assertFalse(registry.upsert(a))
        assertEquals(1L, registry.currentRevision())
        assertTrue(registry.upsert(b))
        assertEquals(2L, registry.currentRevision())
        assertTrue(registry.upsert(c))
        assertEquals(3L, registry.currentRevision())
        assertEquals(listOf(b.fingerprint, c.fingerprint), registry.snapshot().map { it.fingerprint })
        assertEquals(listOf(2L, 3L), registry.snapshot().map { it.scientificRevision })
    }

    @Test
    fun `restored registry continues persisted scientific revision`() {
        val first = NativeLearningAnchorRegistry(maxEntries = 4)
        first.upsert(NativeLearningAnchor.fromMaturityEvent(passageEvent(1, 110L, 118L), 4)!!)
        first.upsert(NativeLearningAnchor.fromMaturityEvent(passageEvent(2, 120L, 128L), 4)!!)
        val restored = NativeLearningAnchorRegistry(maxEntries = 4)
        restored.replaceAll(first.snapshot().map { NativeLearningAnchor.fromJson(it.toJson())!! })

        assertEquals(2L, restored.currentRevision())
        restored.upsert(NativeLearningAnchor.fromMaturityEvent(passageEvent(3, 130L, 138L), 4)!!)
        assertEquals(3L, restored.currentRevision())
        assertEquals(3L, restored.snapshot().last().scientificRevision)
    }

    private fun passageEvent(bandIndex: Int, firstSequence: Long, lastSequence: Long): JSONObject =
        baseEvent()
            .put("bandIndex", bandIndex)
            .put("firstTelemetrySequence", firstSequence)
            .put("lastTelemetrySequence", lastSequence)
            .put("observedAtElapsedMs", 2000L + firstSequence)
            .put("correlatedFrameElapsedMs", 1500L + firstSequence)

    private fun baseEvent(): JSONObject = JSONObject()
        .put("eventType", "NATIVE_BAND_MATURED")
        .put("nativeValidity", true)
        .put("sessionId", 9L)
        .put("snapshotId", "AUTOCAL-1")
        .put("snapshotHash", "hash")
        .put("bandIndex", 4)
        .put("zone", "NORMAL")
        .put("counter", 8)
        .put("threshold", 8)
        .put("previousObservedAtElapsedMs", 1000L)
        .put("observedAtElapsedMs", 2000L)
        .put("correlationState", "CORRELATED")
        .put("correlationConfidence", 0.82)
        .put("rpmConfidence", 0.74)
        .put("rpm", 2450)
        .put("correlatedPetrolMs", 4.30)
        .put("correlatedGasMs", 7.2)
        .put("correlatedMapBar", 0.54)
        .put("correlatedFuel", "GNV")
        .put("correlatedFrameElapsedMs", 1500L)
        .put("correlationLagMs", 500L)
        .put("firstTelemetrySequence", 110L)
        .put("lastTelemetrySequence", 118L)
        .put("matchedTelemetryFrames", 9)
}
