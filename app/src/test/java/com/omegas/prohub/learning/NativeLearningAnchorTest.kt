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
        val event = baseEvent()
            .put("correlationState", "CORRELATED")
            .put("correlationConfidence", 0.82)
            .put("rpmConfidence", 0.74)
            .put("rpm", 2450)
            .put("correlatedPetrolMs", 4.30)
            .put("correlatedMapBar", 0.54)
            .put("firstTelemetrySequence", 110L)
            .put("lastTelemetrySequence", 118L)
            .put("matchedTelemetryFrames", 9)

        val anchor = NativeLearningAnchor.fromMaturityEvent(event, calibrationEpoch = 7)
        assertNotNull(anchor)
        anchor!!
        assertEquals(7, anchor.calibrationEpoch)
        assertTrue(anchor.nativeValidity)
        assertEquals(2450, anchor.rpm)
        assertEquals(0.74, anchor.rpmConfidence, 1e-9)
        assertFalse(anchor.toJson().getBoolean("comparisonVote"))
        assertFalse(anchor.toJson().getBoolean("automaticWrite"))
    }

    @Test
    fun `native validity survives unreliable rpm without invented position`() {
        val anchor = NativeLearningAnchor.fromMaturityEvent(
            baseEvent()
                .put("correlationState", "NO_RELIABLE_CORRELATION")
                .put("correlationConfidence", 0.55)
                .put("rpmConfidence", 0.90)
                .put("rpm", 3300),
            calibrationEpoch = 2,
        )!!

        assertTrue(anchor.nativeValidity)
        assertNull(anchor.rpm)
        assertEquals(0.0, anchor.rpmConfidence, 1e-9)
        assertEquals(0.0, anchor.correlationConfidence, 1e-9)
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
        .put("snapshotId", "AUTOCAL-1")
        .put("snapshotHash", "hash")
        .put("bandIndex", 4)
        .put("zone", "NORMAL")
        .put("counter", 8)
        .put("threshold", 8)
        .put("previousObservedAtElapsedMs", 1000L)
        .put("observedAtElapsedMs", 2000L)
        .put("correlationState", "NO_RELIABLE_CORRELATION")
