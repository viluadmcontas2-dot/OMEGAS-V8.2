package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignalLearningStoreEquivalenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun cng_is_retained_before_petrol_and_compared_when_reference_arrives() {
        val store = SignalLearningStore(
            temporary.root.resolve("equivalence-${System.nanoTime()}.json"),
            RingLog(),
        )
        try {
            store.startSession()
            store.ingest(
                telemetry(at = 650L, fuel = Mp48Fuel.CNG, petrolMs = 3.30),
                SampleDecision.accepted(sample("cng", 100L, 650L, Mp48Fuel.CNG, 3.30)),
            )
            val beforePetrol = store.export("test").getJSONObject("primaryEquivalence")
            assertTrue(beforePetrol.getDouble("cngWeight") > 0.0)
            assertEquals(0.0, beforePetrol.getDouble("petrolWeight"), 0.0)
            assertFalse(beforePetrol.getBoolean("comparable"))

            store.ingest(
                telemetry(at = 1_250L, fuel = Mp48Fuel.PETROL, petrolMs = 3.00),
                SampleDecision.accepted(sample("petrol", 700L, 1_250L, Mp48Fuel.PETROL, 3.00)),
            )
            val afterPetrol = store.export("test").getJSONObject("primaryEquivalence")
            assertTrue(afterPetrol.getDouble("petrolWeight") > 0.0)
            assertTrue(afterPetrol.getBoolean("comparable"))
            assertEquals(3.00, afterPetrol.getDouble("referenceMs"), 1e-9)
            assertEquals(3.30, afterPetrol.getDouble("cngMs"), 1e-9)
            assertEquals(0.10, afterPetrol.getDouble("errorFraction"), 1e-9)
            assertTrue(afterPetrol.getDouble("uncertaintyFraction") > 0.0)
        } finally {
            store.close()
        }
    }

    @Test
    fun pressure_and_temperature_do_not_participate_in_primary_equivalence_weight() {
        val a = EquivalenceEvidenceWeight.from(diagnostics(pressureShift = 0.0, water = 80.0)).stability
        val b = EquivalenceEvidenceWeight.from(diagnostics(pressureShift = 99.0, water = -20.0)).stability
        assertEquals(a, b, 1e-12)
    }

    private fun sample(
        id: String,
        start: Long,
        end: Long,
        fuel: Mp48Fuel,
        petrolMs: Double,
    ) = MotorSample(
        id = id,
        startedAtElapsedMs = start,
        endedAtElapsedMs = end,
        fuel = fuel,
        rpm = 2_500.0,
        mapBar = 0.60,
        petrolMs = petrolMs,
        pressureDiffBar = 1.4,
        waterC = 80.0,
        gasC = 30.0,
        quality = 1.0,
        classification = SampleClassification.STRONG,
        frameCount = 8,
        diagnostics = diagnostics(),
    )

    private fun diagnostics(pressureShift: Double = 0.0, water: Double = 80.0) = SampleDiagnostics(
        frameCount = 8,
        durationMs = 550L,
        medianIntervalMs = 50L,
        waterCenterC = water,
        minimumWaterC = 55,
        rpmCenterShift = 0.0,
        rpmCenterLimit = 62.5,
        rpmOscillation = 0.0,
        rpmOscillationLimit = 125.0,
        mapCenterShift = 0.0,
        mapCenterLimit = 0.025,
        mapOscillation = 0.0,
        mapOscillationLimit = 0.05,
        petrolCenterShift = 0.0,
        petrolCenterLimit = 0.24,
        petrolOscillationRatio = 0.0,
        petrolOscillationLimit = 0.15,
        pressureCenterShift = pressureShift,
        pressureCenterLimit = 0.04,
        pressureOscillation = pressureShift,
        pressureOscillationLimit = 0.08,
    )

    private fun telemetry(at: Long, fuel: Mp48Fuel, petrolMs: Double) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = if (fuel == Mp48Fuel.CNG) 100 else 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = fuel,
        state = fuel.wireName,
        waterRaw = 80,
        waterC = 80,
        gasC = 30,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = 0.60,
        pressureDiffBar = 1.4,
        plausible = true,
    )
}
