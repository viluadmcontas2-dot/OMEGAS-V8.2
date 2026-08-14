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
    fun `same physical passage has stable fingerprint across snapshots`() {
        val first = NativeLearningAnchor.fromMaturityEvent(baseEvent().put("snapshotId", "A"), 3)!!
        val second = NativeLearningAnchor.fromMaturityEvent(baseEvent().put("snapshotId", "B"), 3)!!
        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `registry deduplicates and remains bounded`() {
        val registry = NativeLearningAnchorRegistry(maxEntries = 2)
        val a = NativeLearningAnchor.fromMaturityEvent(baseEvent().put("bandIndex", 1), 1)!!
        val b = NativeLearningAnchor.fromMaturityEvent(baseEvent().put("bandIndex", 2), 1)!!
        val c = NativeLearningAnchor.fromMaturityEvent(baseEvent().put("bandIndex", 3), 1)!!

        assertTrue(registry.upsert(a))
        assertFalse(registry.upsert(a))
        assertTrue(registry.upsert(b))
        assertTrue(registry.upsert(c))
        assertEquals(listOf(b.fingerprint, c.fingerprint), registry.snapshot().map { it.fingerprint })
    }

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
