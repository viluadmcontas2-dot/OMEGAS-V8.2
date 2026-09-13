package com.omegas.prohub.blue

import com.omegas.prohub.calibration.CalibrationShape
import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #31: measured gasoline and interpolated gasoline must remain distinguishable. */
class BlueGasolineReferenceProvenanceTest {
    private val engine = BlueCausalEngine()

    @Test
    fun `issue 31 direct gasoline support is explicitly observed`() {
        val target = evidence("cng", FuelKind.CNG, rpm = 1_000.0, mapBar = 0.45, petrolMs = 4.8)
        val gasoline = evidence("petrol", FuelKind.PETROL, rpm = 1_000.0, mapBar = 0.45, petrolMs = 4.2)

        val reference = engine.petrolReference(target, listOf(gasoline))!!

        assertEquals("OBSERVED", reflected(reference, "getProvenance"))
    }

    @Test
    fun `issue 31 exact observed coordinate remains observed even with neighboring support`() {
        val target = evidence("cng", FuelKind.CNG, rpm = 1_000.0, mapBar = 0.45, petrolMs = 4.8)
        val gasoline = listOf(
            evidence("exact", FuelKind.PETROL, rpm = 1_000.0, mapBar = 0.45, petrolMs = 4.2),
            evidence("neighbor", FuelKind.PETROL, rpm = 1_080.0, mapBar = 0.45, petrolMs = 4.5),
        )

        val reference = engine.petrolReference(target, gasoline)!!

        assertEquals(4.2, reference.petrolMs, 1e-9)
        assertEquals("OBSERVED", reflected(reference, "getProvenance"))
    }

    @Test
    fun `issue 31 supported spatial estimate is explicitly interpolated`() {
        val target = evidence("cng", FuelKind.CNG, rpm = 1_050.0, mapBar = 0.45, petrolMs = 4.8)
        val gasoline = listOf(
            evidence("left", FuelKind.PETROL, rpm = 1_000.0, mapBar = 0.45, petrolMs = 4.0),
            evidence("right", FuelKind.PETROL, rpm = 1_100.0, mapBar = 0.45, petrolMs = 4.4),
        )

        val reference = engine.petrolReference(target, gasoline)!!

        assertEquals(4.2, reference.petrolMs, 1e-9)
        assertEquals("INTERPOLATED", reflected(reference, "getProvenance"))
    }

    @Test
    fun `issue 31 comparison preserves gasoline reference provenance`() {
        val revision = CalibrationRevision(3, 5)
        val target = evidence("cng", FuelKind.CNG, rpm = 1_050.0, mapBar = 0.45, petrolMs = 4.8, revision = revision)
        val gasoline = listOf(
            evidence("left", FuelKind.PETROL, rpm = 1_000.0, mapBar = 0.45, petrolMs = 4.0),
            evidence("right", FuelKind.PETROL, rpm = 1_100.0, mapBar = 0.45, petrolMs = 4.4),
        )
        val state = BlueLearningState(
            sessionId = "provenance",
            calibration = calibration(revision),
            petrolEvidence = gasoline,
            cngEvidenceByRevision = mapOf(revision to listOf(target)),
        )

        val comparison = engine.reconcile(state).single()

        assertEquals("INTERPOLATED", reflected(comparison, "getPetrolReferenceProvenance"))
    }

    private fun reflected(instance: Any, getter: String): String? =
        instance.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
            ?.invoke(instance)
            ?.toString()

    private fun evidence(
        id: String,
        fuel: FuelKind,
        rpm: Double,
        mapBar: Double,
        petrolMs: Double,
        revision: CalibrationRevision? = null,
    ) = FuelEvidence(
        id = id,
        fuel = fuel,
        collectedAtMs = if (fuel == FuelKind.PETROL) 1_000L else 1_000_000L,
        visitId = id,
        rpm = rpm,
        mapBar = mapBar,
        petrolMs = petrolMs,
        quality = 0.95,
        cngRevision = revision,
    )

    private fun calibration(revision: CalibrationRevision) = CalibrationState(
        revision = revision,
        curveK = List(CalibrationShape.CURVE_K_POINTS) { 1.0 },
        mapK = List(CalibrationShape.MAP_K_STORAGE_ROWS) {
            List(CalibrationShape.MAP_K_COLUMNS) { 100 }
        },
    )
}
