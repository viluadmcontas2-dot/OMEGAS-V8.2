package com.omegas.prohub.blue

import org.junit.Assert.assertEquals
import org.junit.Test

class BluePhysicalMatchingInvariantTest {
    private val revision = CalibrationRevision(curveK = 1, mapK = 1)

    private fun evidence(
        id: String,
        fuel: FuelKind,
        rpm: Double,
        map: Double,
        petrol: Double,
        water: Double,
    ) = FuelEvidence(
        id = id,
        fuel = fuel,
        collectedAtMs = 1_000L,
        visitId = id,
        rpm = rpm,
        mapBar = map,
        petrolMs = petrol,
        quality = 1.0,
        cngRevision = if (fuel == FuelKind.CNG) revision else null,
        waterC = water,
    )

    @Test
    fun `temperature cannot change petrol reference selection`() {
        val engine = BlueCausalEngine()
        val target = evidence("target", FuelKind.CNG, rpm = 2_000.0, map = 0.55, petrol = 4.20, water = 20.0)
        val cold = evidence("cold", FuelKind.PETROL, rpm = 2_000.0, map = 0.55, petrol = 4.00, water = 20.0)
        val hot = evidence("hot", FuelKind.PETROL, rpm = 2_000.0, map = 0.55, petrol = 4.40, water = 100.0)

        val reference = engine.petrolReference(target, listOf(cold, hot))!!

        assertEquals(4.20, reference.petrolMs, 0.0001)
        assertEquals(2, reference.supportCount)
    }
}
