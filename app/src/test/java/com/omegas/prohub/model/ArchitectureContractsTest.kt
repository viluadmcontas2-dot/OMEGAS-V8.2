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
}
