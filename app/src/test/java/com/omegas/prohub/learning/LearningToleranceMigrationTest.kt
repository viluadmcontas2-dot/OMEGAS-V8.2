package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningToleranceMigrationTest {
    @Test
    fun `legacy collection windows migrate to cadence safe budgets`() {
        val expected = mapOf(18 to 6_000L, 14 to 4_500L, 10 to 3_000L, 8 to 2_000L, 6 to 1_600L)
        val legacy = mapOf(18 to 2_500L, 14 to 2_000L, 10 to 1_500L, 8 to 1_250L, 6 to 1_000L)
        legacy.forEach { (frames, oldMs) ->
            val migrated = LearningToleranceSettings.migrateLegacyCollectionWindow(
                LearningTolerancePolicy(requiredFrames = frames, maximumAttemptMs = oldMs),
            )
            assertEquals(expected.getValue(frames), migrated.maximumAttemptMs)
        }
    }

    @Test
    fun `custom window is never overwritten by migration`() {
        val custom = LearningTolerancePolicy(requiredFrames = 8, maximumAttemptMs = 4_000L)
        assertEquals(custom, LearningToleranceSettings.migrateLegacyCollectionWindow(custom))
    }
}
