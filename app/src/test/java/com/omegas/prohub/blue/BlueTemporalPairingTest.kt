package com.omegas.prohub.blue

import org.junit.Assert.assertEquals
import org.junit.Test

class BlueTemporalPairingTest {
    @Test
    fun `recent gasoline microburst is preferred over older same-region history`() {
        val engine = BlueCausalEngine()
        val target = evidence(FuelKind.CNG, 100_000L, 2000.0, 0.55, 4.80)
        val oldPetrol = evidence(FuelKind.PETROL, 1_000L, 2000.0, 0.55, 4.00)
        val recentPetrol = evidence(FuelKind.PETROL, 99_000L, 2000.0, 0.55, 4.50)

        val reference = engine.petrolReference(target, listOf(oldPetrol, recentPetrol))!!

        assertEquals(4.50, reference.petrolMs, 0.0001)
        assertEquals(1, reference.supportCount)
    }

    private fun evidence(
        fuel: FuelKind,
        at: Long,
        rpm: Double,
        map: Double,
        petrol: Double,
    ) = FuelEvidence(
        id = "$fuel-$at-$petrol",
        fuel = fuel,
        collectedAtMs = at,
        visitId = "$fuel-$at",
        rpm = rpm,
        mapBar = map,
        petrolMs = petrol,
        quality = 0.90,
        cngRevision = if (fuel == FuelKind.CNG) CalibrationRevision(0, 0) else null,
    )
}
