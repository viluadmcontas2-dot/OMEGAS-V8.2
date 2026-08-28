package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Test

class MulActPhysicsTest {
    @Test fun q14NeutralAndStepDirectionArePreservedWithoutSlopeClaim() {
        assertEquals(1.0, PhysicsFactors.mulActFromQ14(16_384), 1e-12)
        assertEquals(1.25, PhysicsFactors.mulActFromQ14(20_480), 1e-12)
        assertEquals(EffectDirection.INCREASE, PhysicsFactors.mulActDirection(16_384, 20_480))
        assertEquals(EffectDirection.DECREASE, PhysicsFactors.mulActDirection(20_480, 16_384))
        assertEquals(EffectDirection.NEUTRAL, PhysicsFactors.mulActDirection(16_384, 16_384))
    }
}
