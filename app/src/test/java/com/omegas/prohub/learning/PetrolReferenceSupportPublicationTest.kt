package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PetrolReferenceSupportPublicationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun stableAndNoisyRegionsPublishSupportAndDispersionAndDoNotShareConfidence() {
        val stable = memory("stable")
        stable.startSession()
        repeat(6) { index ->
            stable.ingest(
                telemetry(index * 700L),
                SampleDecision.accepted(sample("stable-$index", index * 700L, 4.0)),
            )
        }

        val noisy = memory("noisy")
        noisy.startSession()
        listOf(3.0, 5.0, 3.0, 5.0, 3.0, 5.0).forEachIndexed { index, petrolMs ->
            noisy.ingest(
                telemetry(index * 700L),
                SampleDecision.accepted(sample("noisy-$index", index * 700L, petrolMs)),
            )
        }

        val stableRegion = stable.export("test").getJSONArray("regions").getJSONObject(0)
        val noisyRegion = noisy.export("test").getJSONArray("regions").getJSONObject(0)
        val stableRobust = stableRegion.getJSONObject("petrol_robust")
        val noisyRobust = noisyRegion.getJSONObject("petrol_robust")

        assertEquals(6, stableRegion.getInt("samples"))
        assertEquals(1, stableRegion.getInt("visit_count"))
        assertEquals(1, stableRegion.getInt("session_count"))
        assertTrue(stableRegion.getString("provenance_policy").isNotBlank())
        assertEquals(6L, stableRobust.getLong("total_observed"))
        assertEquals(6, stableRobust.getInt("retained_count"))
        assertEquals(4.0, stableRobust.getDouble("median_ms"), 1e-9)
        assertEquals(0.0, stableRobust.getDouble("mad_ms"), 1e-9)
        assertTrue(noisyRobust.getDouble("mad_ms") > stableRobust.getDouble("mad_ms"))
        assertTrue(noisyRobust.getDouble("iqr_ms") > stableRobust.getDouble("iqr_ms"))
        assertTrue(stableRegion.getDouble("confidence") > noisyRegion.getDouble("confidence"))
    }

    private fun memory(name: String) = MotorLearningMemory(
        temporary.root.resolve("$name.json"),
        RingLog(),
    )

    private fun sample(id: String, at: Long, petrolMs: Double) = MotorSample(
        id = id,
        startedAtElapsedMs = at,
        endedAtElapsedMs = at + 550L,
        fuel = Mp48Fuel.PETROL,
        rpm = 2_500.0,
        mapBar = 0.60,
        petrolMs = petrolMs,
        pressureDiffBar = 1.4,
        waterC = 80.0,
        gasC = 30.0,
        quality = 0.95,
        classification = SampleClassification.STRONG,
        frameCount = LearningTolerancePolicy().requiredFrames,
        diagnostics = diagnostics(),
    )

    private fun telemetry(at: Long) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = 4.0,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = Mp48Fuel.PETROL,
        state = Mp48Fuel.PETROL.wireName,
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

    private fun diagnostics() = SampleDiagnostics(
        frameCount = LearningTolerancePolicy().requiredFrames,
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
    )
}
