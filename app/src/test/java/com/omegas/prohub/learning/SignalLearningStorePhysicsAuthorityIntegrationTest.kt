package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignalLearningStorePhysicsAuthorityIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `published advisor carries physics authority metadata through real store path`() {
        val store = SignalLearningStore(
            temporary.root.resolve("physics-authority-${System.nanoTime()}.json"),
            RingLog(),
        )
        try {
            store.startSession()
            store.ingest(
                telemetry(at = 650L, fuel = Mp48Fuel.PETROL, petrolMs = 3.00),
                SampleDecision.accepted(sample("petrol", 100L, 650L, Mp48Fuel.PETROL, 3.00)),
            )
            store.ingest(
                telemetry(at = 1_250L, fuel = Mp48Fuel.CNG, petrolMs = 3.30),
                SampleDecision.accepted(sample("cng", 700L, 1_250L, Mp48Fuel.CNG, 3.30)),
            )

            repeat(200) {
                val exported = store.export("test")
                val advice = exported.getJSONObject("assistedCalibration")
                if (exported.optBoolean("advisorFresh", false) &&
                    advice.optString("primaryAuthority") == BoundedEquivalenceAdvisorSnapshot.AUTHORITY &&
                    advice.optInt("comparisonCount", 0) > 0
                ) {
                    val physicsPolicy = advice.getJSONObject("physicsPolicy")
                    assertEquals("POLICY_ONLY", physicsPolicy.getString("magnitudeAuthority"))
                    assertFalse(physicsPolicy.getBoolean("idealTarget"))

                    val curve = advice.getJSONArray("kFactorSuggestions")
                    assertTrue(curve.length() > 0)
                    repeat(curve.length()) { index ->
                        val item = curve.getJSONObject(index)
                        assertEquals("POLICY_ONLY", item.getString("magnitudeAuthority"))
                        assertEquals("STEP_POLICY_BASELINE", item.getString("magnitudeRole"))
                        assertEquals("UNKNOWN", item.getString("correctionMechanism"))
                        assertEquals("CURVE_MUL_ACT", item.getString("mechanismCandidateLane"))
                        assertFalse(item.getBoolean("idealTarget"))
                    }

                    val map = advice.getJSONArray("mapResidualSuggestions")
                    assertTrue(map.length() > 0)
                    repeat(map.length()) { index ->
                        val item = map.getJSONObject(index)
                        assertEquals("POLICY_ONLY", item.getString("magnitudeAuthority"))
                        assertEquals("STEP_POLICY_BASELINE", item.getString("magnitudeRole"))
                        assertEquals("UNKNOWN", item.getString("correctionMechanism"))
                        assertEquals("MAP_LOCAL", item.getString("mechanismCandidateLane"))
                        assertFalse(item.getBoolean("idealTarget"))
                    }

                    assertFalse(advice.optBoolean("automatic", true))
                    assertTrue(advice.optBoolean("humanConfirmationRequired", false))
                    return
                }
                Thread.sleep(10L)
            }
            fail("Advisor never published Physics authority metadata through SignalLearningStore")
        } finally {
            store.close()
        }
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
