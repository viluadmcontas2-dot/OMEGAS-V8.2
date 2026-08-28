package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorFailClosedStateTest {
    @Test
    fun `stale generation emits generation stale and no target`() {
        val result = PredictorContract.evaluate(
            input(observation = observation(stamp = stamp(generation = 6))),
        )
        assertBlocked(result, PredictorAbstentionReason.GENERATION_STALE)
    }

    @Test
    fun `unknown runtime geometry emits geometry unknown`() {
        val result = PredictorContract.evaluate(
            input(calibration = binding(geometryKnown = false)),
        )
        assertBlocked(result, PredictorAbstentionReason.GEOMETRY_UNKNOWN)
    }

    @Test
    fun `stale or unknown reference emits reference stale`() {
        assertBlocked(
            PredictorContract.evaluate(input(referenceState = PredictorReferenceState.STALE)),
            PredictorAbstentionReason.REFERENCE_STALE,
        )
        assertBlocked(
            PredictorContract.evaluate(input(referenceState = PredictorReferenceState.UNKNOWN)),
            PredictorAbstentionReason.REFERENCE_STALE,
        )
    }

    @Test
    fun `insufficient or unknown context emits context insufficient`() {
        assertBlocked(
            PredictorContract.evaluate(input(observation = observation(contextState = PredictorContextState.INSUFFICIENT))),
            PredictorAbstentionReason.CONTEXT_INSUFFICIENT,
        )
        assertBlocked(
            PredictorContract.evaluate(input(observation = observation(contextState = PredictorContextState.UNKNOWN))),
            PredictorAbstentionReason.CONTEXT_INSUFFICIENT,
        )
    }

    @Test
    fun `mutation reconciling or unknown state quarantines publication`() {
        listOf(
            PredictorMutationState.MUTATING,
            PredictorMutationState.RECONCILING,
            PredictorMutationState.UNKNOWN,
        ).forEach { state ->
            assertBlocked(
                PredictorContract.evaluate(input(mutationState = state)),
                PredictorAbstentionReason.MUTATION_QUARANTINE,
            )
        }
    }

    @Test
    fun `unknown physics blocks publication`() {
        assertBlocked(
            PredictorContract.evaluate(input(physicsState = PredictorPhysicsState.UNKNOWN)),
            PredictorAbstentionReason.PHYSICS_UNKNOWN,
        )
    }

    @Test
    fun `insufficient or unknown support blocks publication`() {
        assertBlocked(
            PredictorContract.evaluate(input(observation = observation(supportState = PredictorSupportState.INSUFFICIENT))),
            PredictorAbstentionReason.SUPPORT_INSUFFICIENT,
        )
        assertBlocked(
            PredictorContract.evaluate(input(observation = observation(supportState = PredictorSupportState.UNKNOWN))),
            PredictorAbstentionReason.SUPPORT_INSUFFICIENT,
        )
    }

    @Test
    fun `delayed epoch callback remains non actionable`() {
        val result = PredictorContract.evaluate(
            input(observation = observation(stamp = stamp(epoch = 3))),
        )
        assertBlocked(result, PredictorAbstentionReason.EPOCH_OR_SESSION_MISMATCH)
    }

    @Test
    fun `quarantine keeps previous ready snapshot only as stale diagnostic`() {
        val previous = PredictorContract.evaluate(input())
        assertEquals(PredictorSnapshotState.READY, previous.state)

        val quarantined = PredictorContract.evaluate(
            input(
                mutationState = PredictorMutationState.MUTATING,
                previousSnapshot = previous,
            ),
        )

        assertBlocked(quarantined, PredictorAbstentionReason.MUTATION_QUARANTINE)
        assertNotNull(quarantined.diagnosticPrevious)
        assertTrue(quarantined.diagnosticPrevious!!.stale)
        assertEquals(previous.revisionToken, quarantined.diagnosticPrevious!!.revisionToken)
        assertEquals(previous.candidates, quarantined.diagnosticPrevious!!.candidates)
    }

    @Test
    fun `stable current known sufficient input stays ready`() {
        val result = PredictorContract.evaluate(input())
        assertEquals(PredictorSnapshotState.READY, result.state)
        assertEquals(132, result.candidates.single().targetK)
        assertTrue(result.abstentionReasons.isEmpty())
    }

    private fun assertBlocked(result: PredictorSnapshot, reason: PredictorAbstentionReason) {
        assertEquals(PredictorSnapshotState.ABSTAIN, result.state)
        assertTrue(result.candidates.isEmpty())
        assertTrue("Expected $reason in ${result.abstentionReasons}", reason in result.abstentionReasons)
    }

    private fun input(
        calibration: LearningCalibrationBinding = binding(),
        observation: PredictorObservation = observation(),
        mutationState: PredictorMutationState = PredictorMutationState.STABLE,
        referenceState: PredictorReferenceState = PredictorReferenceState.CURRENT,
        physicsState: PredictorPhysicsState = PredictorPhysicsState.KNOWN,
        previousSnapshot: PredictorSnapshot? = null,
    ) = PredictorInputSnapshot(
        calibration = calibration,
        curveHash = "curve-A",
        sourceRevisions = revisions(),
        epoch = 4,
        sessionId = "science-session-4",
        observations = listOf(observation),
        mutationState = mutationState,
        referenceState = referenceState,
        physicsState = physicsState,
        previousSnapshot = previousSnapshot,
    )

    private fun observation(
        stamp: PredictorEvidenceStamp = stamp(),
        contextState: PredictorContextState = PredictorContextState.SUFFICIENT,
        supportState: PredictorSupportState = PredictorSupportState.SUFFICIENT,
    ) = PredictorObservation(
        cell = PredictorCell(2, 3),
        kStar = 132.0,
        currentK = 120,
        uncertaintyPercent = 1.5,
        support = 0.82,
        knownness = PredictorKnownness.KNOWN,
        operatingPoint = PredictorOperatingPoint(
            rpm = 2400.0,
            petrolInjectionMs = 4.5,
            mapBar = 0.60,
            deltaPressureBar = 0.42,
            petrolReferenceTemperatureC = 32.0,
            waterTemperatureC = 88.0,
            gasTemperatureC = 41.0,
            effectiveMass = 1.0,
            effectiveCapacity = 1.0,
        ),
        stamp = stamp,
        provenance = "DIRECT",
        contextState = contextState,
        supportState = supportState,
    )

    private fun stamp(
        generation: Int = 7,
        epoch: Int = 4,
    ) = PredictorEvidenceStamp(
        calibrationFingerprint = "calibration-A",
        calibrationGeneration = generation,
        geometryFingerprint = "geometry-A",
        mapHash = "map-A",
        curveHash = "curve-A",
        sourceRevisions = revisions(),
        epoch = epoch,
        sessionId = "science-session-4",
        freshness = PredictorSourceFreshness.CURRENT,
    )

    private fun revisions() = PredictorSourceRevisions(11L, 12L, 13L, 14L, 15L)

    private fun binding(geometryKnown: Boolean = true) = LearningCalibrationBinding(
        calibrationFingerprint = "calibration-A",
        calibrationGeneration = 7,
        geometryFingerprint = "geometry-A",
        usbSessionId = 21L,
        mapHash = "map-A",
        petrolAxisMs = if (geometryKnown) List(12) { index -> 1.0 + index } else emptyList(),
        rpmAxis = if (geometryKnown) List(12) { index -> 800 + index * 300 } else emptyList(),
    )
}
