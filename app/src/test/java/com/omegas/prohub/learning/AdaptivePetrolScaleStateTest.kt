package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePetrolScaleStateTest {
    @Test
    fun carriedScaleIsNotResetByOneDistantSessionCandidate() {
        val previous = AdaptivePetrolScaleState(
            acceptedScale = 1.065612,
            candidateScale = 1.065612,
            supportRegions = 12,
            promotedSessionId = "previous-session",
            updatedAt = 1L,
        )
        val noisySession = regionsForScale(1.10058)

        val promoted = AdaptivePetrolReference.promoteScale(
            previous = previous,
            sessionId = "new-session",
            sessionRegions = noisySession,
            updatedAt = 2L,
        )

        val maximumAllowed = previous.acceptedScale!! * 1.0050001
        assertTrue("single session must not reset S by ~3%", promoted.acceptedScale!! <= maximumAllowed)
        assertEquals(1.10058, promoted.candidateScale ?: Double.NaN, 0.0002)
    }

    @Test
    fun coherentNearbySessionCandidateCanBeAcceptedWithoutArtificialLag() {
        val previous = AdaptivePetrolScaleState(
            acceptedScale = 1.065612,
            candidateScale = 1.065612,
            supportRegions = 12,
            promotedSessionId = "previous-session",
            updatedAt = 1L,
        )

        val promoted = AdaptivePetrolReference.promoteScale(
            previous = previous,
            sessionId = "new-session",
            sessionRegions = regionsForScale(1.066815),
            updatedAt = 2L,
        )

        assertEquals(1.066815, promoted.acceptedScale ?: Double.NaN, 0.0002)
        assertEquals("new-session", promoted.promotedSessionId)
    }

    @Test
    fun sameSessionPromotionIsIdempotent() {
        val previous = AdaptivePetrolScaleState(
            acceptedScale = 1.065612,
            candidateScale = 1.065612,
            supportRegions = 12,
            promotedSessionId = "same-session",
            updatedAt = 1L,
        )

        val repeated = AdaptivePetrolReference.promoteScale(
            previous = previous,
            sessionId = "same-session",
            sessionRegions = regionsForScale(1.10),
            updatedAt = 2L,
        )

        assertEquals(previous, repeated)
    }

    @Test
    fun scaleStateRoundTripsForPersistence() {
        val state = AdaptivePetrolScaleState(
            acceptedScale = 1.065612,
            candidateScale = 1.066815,
            supportRegions = 8,
            promotedSessionId = "session-a",
            updatedAt = 1234L,
        )
        assertEquals(state, AdaptivePetrolScaleState.fromJson(state.toJson()))
    }

    private fun regionsForScale(scale: Double): List<PetrolReferenceSelector.Region> =
        listOf(
            900.0 to 0.30,
            1350.0 to 0.40,
            1850.0 to 0.55,
            2350.0 to 0.70,
            2900.0 to 0.80,
            3150.0 to 0.90,
        ).mapIndexed { index, (rpm, map) ->
            PetrolReferenceSelector.Region(
                id = "r-$index",
                rpm = rpm,
                mapBar = map,
                waterC = 85.0,
                petrolMs = AdaptivePetrolReference.f2(rpm, map) * scale,
                confidence = 0.9,
                sampleCount = 20,
            )
        }
}
