package com.omegas.prohub.ecu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningTolerancePolicyTest {
    @Test
    fun `safe defaults represent the official V5 policy`() {
        val policy = LearningTolerancePolicy()
        assertEquals(10, policy.requiredFrames)
        assertEquals(3, policy.toleratedSerialFailures)
    }

    @Test
    fun `minimum values are bounded`() {
        val policy = LearningTolerancePolicy(
            requiredFrames = -1,
            maximumAttemptMs = 1L,
            warningGapMs = 1L,
            breakingGapMs = 1L,
        ).normalized()
        assertEquals(6, policy.requiredFrames)
        assertEquals(90L, policy.breakingGapMs)
        assertEquals(400L, policy.maximumAttemptMs)
        assertEquals(80L, policy.warningGapMs)
        assertTrue(policy.breakingGapMs > policy.warningGapMs)
    }

    @Test
    fun `maximum values are bounded`() {
        val policy = LearningTolerancePolicy(
            requiredFrames = 999,
            maximumAttemptMs = Long.MAX_VALUE,
            rpmOscillationPercent = 999.0,
        ).normalized()
        assertEquals(30, policy.requiredFrames)
        assertEquals(10_000L, policy.maximumAttemptMs)
        assertEquals(35.0, policy.rpmOscillationPercent, 0.0)
    }

    @Test
    fun `dependent thresholds remain monotonic`() {
        val policy = LearningTolerancePolicy(
            warningGapMs = 1_500L,
            breakingGapMs = 100L,
            toleratedSerialFailures = 8,
            hardRecoveryFailures = 2,
            petrolOscillationPercent = 8.0,
            strongPetrolOscillationPercent = 30.0,
            requiredFrames = 6,
            maximumAttemptMs = 400L,
        ).normalized()
        assertTrue(policy.breakingGapMs > policy.warningGapMs)
        assertTrue(policy.hardRecoveryFailures > policy.toleratedSerialFailures)
        assertTrue(policy.strongPetrolOscillationPercent <= policy.petrolOscillationPercent)
        assertTrue(policy.maximumAttemptMs >= policy.breakingGapMs)
    }

    @Test
    fun `json round trip preserves every operational tolerance`() {
        val original = LearningTolerancePolicy(
            requiredFrames = 18,
            warningGapMs = 320L,
            rpmCenterPercent = 7.5,
        ).normalized()
        val restored = LearningTolerancePolicy.fromJson(JSONObject(original.toJson().toString()))
        assertEquals(original, restored)
        assertEquals(original.toJson().keySet(), restored.toJson().keySet())
    }

    @Test
    fun `partial json update keeps unspecified current values`() {
        val current = LearningTolerancePolicy(requiredFrames = 20, breakingGapMs = 600L)
        val updated = LearningTolerancePolicy.fromJson(JSONObject().put("requiredFrames", 8), current)
        assertEquals(8, updated.requiredFrames)
        assertEquals(600L, updated.breakingGapMs)
    }
}

