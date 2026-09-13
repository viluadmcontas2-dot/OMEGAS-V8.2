package com.omegas.prohub.blue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BlueTemporalPairingTest {
    @Test
    fun `T01-RED01 gasoline reference remains valid after thirty one seconds`() {
        assertPermanentReference(ageMs = 31_000L)
    }

    @Test
    fun `T01-RED02 gasoline reference remains valid after thirty minutes`() {
        assertPermanentReference(ageMs = 30L * 60L * 1_000L)
    }

    @Test
    fun `T01-RED03 gasoline reference remains valid after twenty four hours`() {
        assertPermanentReference(ageMs = 24L * 60L * 60L * 1_000L)
    }

    @Test
    fun `T01-RED04 gasoline reference remains valid after several days`() {
        assertPermanentReference(ageMs = 7L * 24L * 60L * 60L * 1_000L)
    }

    @Test
    fun `T01-RED05 timestamp direction alone cannot invalidate a physical gasoline reference`() {
        val engine = BlueCausalEngine()
        val target = evidence(FuelKind.CNG, 100_000L, 2_000.0, 0.55, 4.80)
        val gasolineRecordedLater = evidence(FuelKind.PETROL, 101_000L, 2_000.0, 0.55, 4.50)

        val reference = engine.petrolReference(target, listOf(gasolineRecordedLater))

        assertNotNull(reference)
        assertEquals(4.50, reference!!.petrolMs, 0.0001)
        assertEquals(1, reference.supportCount)
    }

    @Test
    fun `same region gasoline history is aggregated without temporal preference`() {
        val engine = BlueCausalEngine()
        val target = evidence(FuelKind.CNG, 100_000L, 2_000.0, 0.55, 4.80)
        val oldPetrol = evidence(FuelKind.PETROL, 1_000L, 2_000.0, 0.55, 4.00)
        val recentPetrol = evidence(FuelKind.PETROL, 99_000L, 2_000.0, 0.55, 4.50)

        val reference = engine.petrolReference(target, listOf(oldPetrol, recentPetrol))

        assertNotNull(reference)
        assertEquals(4.25, reference!!.petrolMs, 0.0001)
        assertEquals(2, reference.supportCount)
    }

    private fun assertPermanentReference(ageMs: Long) {
        val engine = BlueCausalEngine()
        val targetAt = 10L * 24L * 60L * 60L * 1_000L
        val target = evidence(FuelKind.CNG, targetAt, 2_000.0, 0.55, 4.80)
        val gasoline = evidence(FuelKind.PETROL, targetAt - ageMs, 2_000.0, 0.55, 4.50)

        val reference = engine.petrolReference(target, listOf(gasoline))

        assertNotNull(reference)
        assertEquals(4.50, reference!!.petrolMs, 0.0001)
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
