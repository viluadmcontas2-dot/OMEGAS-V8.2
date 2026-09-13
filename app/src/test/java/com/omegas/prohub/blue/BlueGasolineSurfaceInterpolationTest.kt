package com.omegas.prohub.blue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #31: gasoline is a continuous RPM x MAP surface.
 *
 * These cases intentionally use the existing BlueCausalEngine.petrolReference
 * API so the RED proves a behavioral gap, not a missing parallel engine/API.
 * Every supported point lies on a simple local plane. A correct local
 * interpolation must therefore recover the plane value at an internal query.
 */
class BlueGasolineSurfaceInterpolationTest {
    private val engine = BlueCausalEngine()

    @Test
    fun `issue 31 interpolates horizontal rpm gap instead of taking candidate median`() {
        val target = evidence(FuelKind.CNG, rpm = 1_075.0, mapBar = 0.50, petrolMs = 4.80)
        val gasoline = listOf(
            evidence(FuelKind.PETROL, rpm = 1_000.0, mapBar = 0.50, petrolMs = 4.00),
            evidence(FuelKind.PETROL, rpm = 1_100.0, mapBar = 0.50, petrolMs = 4.40),
        )

        val reference = engine.petrolReference(target, gasoline)!!

        assertEquals(4.30, reference.petrolMs, 1e-9)
    }

    @Test
    fun `issue 31 interpolates vertical map gap instead of taking candidate median`() {
        val target = evidence(FuelKind.CNG, rpm = 1_000.0, mapBar = 0.475, petrolMs = 4.80)
        val gasoline = listOf(
            evidence(FuelKind.PETROL, rpm = 1_000.0, mapBar = 0.40, petrolMs = 4.00),
            evidence(FuelKind.PETROL, rpm = 1_000.0, mapBar = 0.50, petrolMs = 4.40),
        )

        val reference = engine.petrolReference(target, gasoline)!!

        assertEquals(4.30, reference.petrolMs, 1e-9)
    }

    @Test
    fun `issue 31 interpolates diagonal two dimensional neighborhood`() {
        val target = evidence(FuelKind.CNG, rpm = 1_075.0, mapBar = 0.475, petrolMs = 5.00)
        val gasoline = listOf(
            planeEvidence(rpm = 1_000.0, mapBar = 0.40),
            planeEvidence(rpm = 1_100.0, mapBar = 0.40),
            planeEvidence(rpm = 1_000.0, mapBar = 0.50),
            planeEvidence(rpm = 1_100.0, mapBar = 0.50),
        )

        val reference = engine.petrolReference(target, gasoline)!!

        assertEquals(localPlane(rpm = 1_075.0, mapBar = 0.475), reference.petrolMs, 1e-9)
    }

    @Test
    fun `issue 31 interpolates irregular supported two dimensional neighborhood`() {
        val target = evidence(FuelKind.CNG, rpm = 1_050.0, mapBar = 0.46, petrolMs = 4.90)
        val gasoline = listOf(
            planeEvidence(rpm = 1_000.0, mapBar = 0.40),
            planeEvidence(rpm = 1_100.0, mapBar = 0.40),
            planeEvidence(rpm = 980.0, mapBar = 0.50),
            planeEvidence(rpm = 1_120.0, mapBar = 0.52),
        )

        val reference = engine.petrolReference(target, gasoline)!!

        assertEquals(localPlane(rpm = 1_050.0, mapBar = 0.46), reference.petrolMs, 1e-9)
    }

    @Test
    fun `issue 31 abstains outside supported physical neighborhood`() {
        val target = evidence(FuelKind.CNG, rpm = 2_000.0, mapBar = 0.90, petrolMs = 8.00)
        val gasoline = listOf(
            planeEvidence(rpm = 1_000.0, mapBar = 0.40),
            planeEvidence(rpm = 1_100.0, mapBar = 0.50),
        )

        assertNull(engine.petrolReference(target, gasoline))
    }

    @Test
    fun `issue 31 abstains when physical match exists but evidence quality is invalid`() {
        val target = evidence(FuelKind.CNG, rpm = 1_000.0, mapBar = 0.45, petrolMs = 4.80)
        val gasoline = listOf(
            evidence(FuelKind.PETROL, rpm = 1_000.0, mapBar = 0.45, petrolMs = 4.20, quality = 0.20),
        )

        assertNull(engine.petrolReference(target, gasoline))
    }

    private fun localPlane(rpm: Double, mapBar: Double): Double =
        4.0 + 0.002 * (rpm - 1_000.0) + 4.0 * (mapBar - 0.40)

    private fun planeEvidence(rpm: Double, mapBar: Double): FuelEvidence =
        evidence(FuelKind.PETROL, rpm = rpm, mapBar = mapBar, petrolMs = localPlane(rpm, mapBar))

    private fun evidence(
        fuel: FuelKind,
        rpm: Double,
        mapBar: Double,
        petrolMs: Double,
        quality: Double = 0.95,
    ) = FuelEvidence(
        id = "${fuel.name}-$rpm-$mapBar-$petrolMs",
        fuel = fuel,
        collectedAtMs = if (fuel == FuelKind.PETROL) 1_000L else 1_000_000L,
        visitId = "${fuel.name}-$rpm-$mapBar",
        rpm = rpm,
        mapBar = mapBar,
        petrolMs = petrolMs,
        quality = quality,
        cngRevision = if (fuel == FuelKind.CNG) CalibrationRevision(0, 0) else null,
    )
}
