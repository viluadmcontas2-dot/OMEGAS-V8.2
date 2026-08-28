package com.omegas.prohub.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureContractsTest {
    @Test
    fun `freshness distinguishes available stale and unavailable snapshots`() {
        assertEquals(
            SnapshotAvailability.AVAILABLE,
            Freshness(producedAtMs = 1_000L, observedAtMs = 1_500L, maximumAgeMs = 1_000L).availability,
        )
        assertEquals(
            SnapshotAvailability.STALE,
            Freshness(producedAtMs = 1_000L, observedAtMs = 2_001L, maximumAgeMs = 1_000L).availability,
        )
        assertEquals(
            SnapshotAvailability.UNAVAILABLE,
            Freshness(producedAtMs = 0L, observedAtMs = 2_001L, maximumAgeMs = 1_000L).availability,
        )
    }

    @Test
    fun `scientific revision belongs to scientific events and not visual refresh`() {
        val revision = ScientificRevision(7L)
        val event = ProductEvent.LearningEvidenceChanged(occurredAtMs = 10L, scientificRevision = revision)
        assertEquals(revision, event.scientificRevision)
        assertEquals(null, ProductEvent.Telemetry(occurredAtMs = 11L).scientificRevision)
    }

    @Test
    fun `ecu mutation cannot cross contract without explicit human confirmation`() {
        val pending = HumanIntent(
            mutation = EcuMutationKind.MAP_K,
            requestedAtMs = 20L,
            operatorConfirmed = false,
            source = "MAP_EDITOR",
        )
        assertThrows(IllegalStateException::class.java) { pending.requireConfirmed() }

        val confirmed = pending.copy(operatorConfirmed = true)
        assertEquals(confirmed, confirmed.requireConfirmed())
    }

    @Test
    fun `ui boundary stays projection only`() {
        assertTrue(UiBoundaryContract.MAY_RENDER_STATE)
        assertTrue(UiBoundaryContract.MAY_EMIT_HUMAN_INTENT)
        assertFalse(UiBoundaryContract.MAY_TOUCH_USB_DIRECTLY)
        assertFalse(UiBoundaryContract.MAY_PARSE_MP48_DIRECTLY)
        assertFalse(UiBoundaryContract.MAY_WRITE_ECU_DIRECTLY)
        assertFalse(UiBoundaryContract.MAY_OWN_SCIENTIFIC_MATH)
    }

    @Test
    fun `state domains remain explicitly separated`() {
        assertEquals(
            setOf(AppStateDomain.SCIENTIFIC, AppStateDomain.OPERATIONAL, AppStateDomain.VISUAL),
            AppStateDomain.entries.toSet(),
        )
    }

    @Test
    fun `adaptive is a parallel scientific authority without hidden dominance`() {
        assertEquals(
            setOf(
                ScientificAuthority.OEM_NATIVE,
                ScientificAuthority.CLASSIC_ASSISTED,
                ScientificAuthority.ADAPTIVE_SHADOW,
            ),
            ScientificAuthority.entries.toSet(),
        )
        assertEquals(
            setOf(AdaptiveMode.OFFLINE_REPLAY, AdaptiveMode.LIVE_SHADOW, AdaptiveMode.PROPOSAL),
            AdaptiveMode.entries.toSet(),
        )
    }

    @Test
    fun `adaptive pre physical modes cannot own serial polling or ecu writer`() {
        assertTrue(AdaptiveBoundaryContract.MAY_CONSUME_CANONICAL_EVIDENCE)
        assertTrue(AdaptiveBoundaryContract.MAY_RECONSTRUCT_OFFLINE_EXPERIMENTS)
        assertTrue(AdaptiveBoundaryContract.MAY_PUBLISH_SHADOW_PREDICTION)
        assertTrue(AdaptiveBoundaryContract.MAY_PREPARE_REVIEWABLE_PROPOSAL)
        assertFalse(AdaptiveBoundaryContract.MAY_TOUCH_MP48_SERIAL)
        assertFalse(AdaptiveBoundaryContract.MAY_OWN_TELEMETRY_POLLING)
        assertFalse(AdaptiveBoundaryContract.MAY_OWN_WRITER)
        assertFalse(AdaptiveBoundaryContract.MAY_WRITE_ECU)
        assertFalse(AdaptiveBoundaryContract.MAY_TRIGGER_AUTOMATCH)
        assertFalse(AdaptiveBoundaryContract.MAY_START_CALIBRATION_AUTOMATICALLY)
    }
}
