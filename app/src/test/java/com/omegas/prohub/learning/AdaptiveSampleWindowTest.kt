package com.omegas.prohub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSampleWindowTest {
    @Test
    fun `minimum adapts to the configured target`() {
        assertEquals(6, AdaptiveSampleWindow.minimumFrames(6))
        assertEquals(6, AdaptiveSampleWindow.minimumFrames(8))
        assertEquals(6, AdaptiveSampleWindow.minimumFrames(10))
        assertEquals(9, AdaptiveSampleWindow.minimumFrames(14))
        assertEquals(9, AdaptiveSampleWindow.minimumFrames(16))
        assertEquals(12, AdaptiveSampleWindow.minimumFrames(18))
        assertEquals(12, AdaptiveSampleWindow.minimumFrames(30))
    }

    @Test
    fun `early acceptance requires quality novelty of continuity and strong injection stability`() {
        val sample = sample(quality = 0.90, frameCount = 6, petrolOscillationRatio = 0.02)
        assertTrue(
            AdaptiveSampleWindow.canAcceptEarly(
                sample = sample,
                desiredFrames = 10,
                toleratedGapCount = 0,
                fullWindowRequired = false,
                strongPetrolOscillationRatio = 0.08,
            ),
        )
        assertFalse(AdaptiveSampleWindow.canAcceptEarly(sample, 10, 1, false, 0.08))
        assertFalse(AdaptiveSampleWindow.canAcceptEarly(sample, 10, 0, true, 0.08))
        assertFalse(AdaptiveSampleWindow.canAcceptEarly(sample(0.84, 6, 0.02), 10, 0, false, 0.08))
        assertFalse(AdaptiveSampleWindow.canAcceptEarly(sample(0.90, 6, 0.09), 10, 0, false, 0.08))
        assertFalse(AdaptiveSampleWindow.canAcceptEarly(sample(0.90, 10, 0.02), 10, 0, false, 0.08))
    }

    @Test
    fun `progressive stage accepts exceptional six standard eight and full ten`() {
        assertEquals(
            AdaptiveSampleWindow.Stage.FAST_ACCEPT,
            AdaptiveSampleWindow.acceptanceStage(
                sample = sample(quality = 0.90, frameCount = 6, petrolOscillationRatio = 0.02),
                desiredFrames = 10,
                toleratedGapCount = 0,
                fullWindowRequired = false,
                strongPetrolOscillationRatio = 0.08,
            ),
        )
        assertEquals(
            AdaptiveSampleWindow.Stage.FORMING,
            AdaptiveSampleWindow.acceptanceStage(
                sample = sample(quality = 0.82, frameCount = 6, petrolOscillationRatio = 0.02),
                desiredFrames = 10,
                toleratedGapCount = 0,
                fullWindowRequired = false,
                strongPetrolOscillationRatio = 0.08,
            ),
        )
        assertEquals(
            AdaptiveSampleWindow.Stage.STANDARD_ACCEPT,
            AdaptiveSampleWindow.acceptanceStage(
                sample = sample(quality = 0.82, frameCount = 8, petrolOscillationRatio = 0.03),
                desiredFrames = 10,
                toleratedGapCount = 0,
                fullWindowRequired = false,
                strongPetrolOscillationRatio = 0.08,
            ),
        )
        assertEquals(
            AdaptiveSampleWindow.Stage.FULL_ACCEPT,
            AdaptiveSampleWindow.acceptanceStage(
                sample = sample(quality = 0.70, frameCount = 10, petrolOscillationRatio = 0.09),
                desiredFrames = 10,
                toleratedGapCount = 1,
                fullWindowRequired = true,
                strongPetrolOscillationRatio = 0.08,
            ),
        )
    }

    @Test
    fun `full window protection blocks all early stages`() {
        assertEquals(
            AdaptiveSampleWindow.Stage.FORMING,
            AdaptiveSampleWindow.acceptanceStage(
                sample = sample(quality = 0.99, frameCount = 8, petrolOscillationRatio = 0.01),
                desiredFrames = 10,
                toleratedGapCount = 0,
                fullWindowRequired = true,
                strongPetrolOscillationRatio = 0.08,
            ),
        )
    }

    private fun sample(
        quality: Double,
        frameCount: Int,
        petrolOscillationRatio: Double,
    ): MotorSample = MotorSample(
        id = "sample",
        startedAtElapsedMs = 0L,
        endedAtElapsedMs = 250L,
        fuel = com.omegas.prohub.ecu.Mp48Fuel.PETROL,
        rpm = 2_500.0,
        mapBar = 0.60,
        petrolMs = 4.0,
        pressureDiffBar = 1.4,
        waterC = 80.0,
        gasC = 30.0,
        quality = quality,
        classification = SampleClassification.USABLE,
        frameCount = frameCount,
        diagnostics = SampleDiagnostics(
            frameCount = frameCount,
            durationMs = 250L,
            medianIntervalMs = 50L,
            waterCenterC = 80.0,
            minimumWaterC = 50,
            rpmCenterShift = 0.0,
            rpmCenterLimit = 25.0,
            rpmOscillation = 0.0,
            rpmOscillationLimit = 40.0,
            mapCenterShift = 0.0,
            mapCenterLimit = 0.02,
            mapOscillation = 0.0,
            mapOscillationLimit = 0.035,
            petrolCenterShift = 0.0,
            petrolCenterLimit = 0.24,
            petrolOscillationRatio = petrolOscillationRatio,
            petrolOscillationLimit = 0.10,
            pressureCenterShift = 0.0,
            pressureCenterLimit = 0.025,
            pressureOscillation = 0.0,
            pressureOscillationLimit = 0.04,
        ),
    )
}
