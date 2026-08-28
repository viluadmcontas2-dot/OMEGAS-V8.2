package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignalLearningStoreLiveLocalityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `forming window queries current rpm map instead of exposing previous local pair`() {
        val store = SignalLearningStore(temporary.root.resolve("live-locality.json"), RingLog())
        try {
            store.startSession()
            store.ingest(
                telemetry(650L, Mp48Fuel.PETROL, 2_500, 0.60, 3.00),
                SampleDecision.accepted(sample("petrol-a", 100L, 650L, Mp48Fuel.PETROL, 2_500.0, 0.60, 3.00)),
            )
            store.ingest(
                telemetry(1_250L, Mp48Fuel.CNG, 2_500, 0.60, 3.30),
                SampleDecision.accepted(sample("cng-a", 700L, 1_250L, Mp48Fuel.CNG, 2_500.0, 0.60, 3.30)),
            )
            assertTrue(store.export("test").getJSONObject("primaryEquivalence").getBoolean("comparable"))

            store.ingest(
                telemetry(1_300L, Mp48Fuel.CNG, 3_800, 1.10, 4.00),
                SampleDecision.forming(
                    count = 1,
                    minimum = 8,
                    desired = 12,
                    timing = SampleTiming(0L, 0L),
                    fuelConfirmed = "GNV",
                ),
            )

            val moved = store.export("test").getJSONObject("primaryEquivalence")
            assertFalse(
                "A healthy forming window in distant CNG-only territory must not expose the previous local pair",
                moved.getBoolean("comparable"),
            )
            assertFalse(moved.has("actionable"))
            assertTrue("Bounded gasoline memory remains retained", moved.getDouble("petrolWeight") > 0.0)
            assertTrue("Bounded CNG memory remains retained", moved.getDouble("cngWeight") > 0.0)
        } finally {
            store.close()
        }
    }

    private fun sample(
        id: String,
        start: Long,
        end: Long,
        fuel: Mp48Fuel,
        rpm: Double,
        mapBar: Double,
        petrolMs: Double,
    ) = MotorSample(
        id = id,
        startedAtElapsedMs = start,
        endedAtElapsedMs = end,
        fuel = fuel,
        rpm = rpm,
        mapBar = mapBar,
        petrolMs = petrolMs,
        pressureDiffBar = 1.4,
        waterC = 80.0,
        gasC = 30.0,
        quality = 1.0,
        classification = SampleClassification.STRONG,
        frameCount = 8,
        diagnostics = SampleDiagnostics(
            frameCount = 8,
            durationMs = 550L,
            medianIntervalMs = 50L,
            waterCenterC = 80.0,
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
            pressureCenterShift = 0.0,
            pressureCenterLimit = 0.04,
            pressureOscillation = 0.0,
            pressureOscillationLimit = 0.08,
        ),
    )

    private fun telemetry(
        at: Long,
        fuel: Mp48Fuel,
        rpm: Int,
        mapBar: Double,
        petrolMs: Double,
    ) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = rpm,
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
        mapBar = mapBar,
        pressureDiffBar = 1.4,
        plausible = true,
    )
}
