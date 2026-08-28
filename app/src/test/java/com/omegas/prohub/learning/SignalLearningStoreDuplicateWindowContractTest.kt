package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignalLearningStoreDuplicateWindowContractTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `duplicate window does not grow provenance or wake advisor`() {
        val store = SignalLearningStore(temporary.root.resolve("duplicate-contract.json"), RingLog())
        try {
            store.startSession()
            val decision = SampleDecision.accepted(sample())
            val telemetry = telemetry()

            store.ingest(telemetry, decision)
            val before = store.export("test")
            val beforeRevision = before.getLong("advisorRevision")
            val beforeProvenance = before.getJSONArray("evidenceProvenance").length()
            val beforeWeight = before.getJSONObject("primaryEquivalence").getDouble("cngWeight")

            repeat(100) { store.ingest(telemetry, decision) }

            val after = store.export("test")
            assertEquals(beforeRevision, after.getLong("advisorRevision"))
            assertEquals(beforeProvenance, after.getJSONArray("evidenceProvenance").length())
            assertEquals(beforeWeight, after.getJSONObject("primaryEquivalence").getDouble("cngWeight"), 0.0)
        } finally {
            store.close()
        }
    }

    private fun sample() = MotorSample(
        id = "duplicate-cng",
        startedAtElapsedMs = 100L,
        endedAtElapsedMs = 650L,
        fuel = Mp48Fuel.CNG,
        rpm = 2_500.0,
        mapBar = 0.60,
        petrolMs = 3.30,
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

    private fun telemetry() = Mp48Telemetry(
        capturedAtElapsedMs = 650L,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = 100,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = 3.30,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = Mp48Fuel.CNG,
        state = Mp48Fuel.CNG.wireName,
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
