package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MotorLearningAdaptiveReferenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun liveCngUsesAdaptiveReferenceWhenNoPhysicalNeighborhoodExists() {
        val file = temporary.newFile("adaptive-live.json")
        val scale = 1.065612
        val support = listOf(
            900.0 to 0.20,
            1100.0 to 0.30,
            1400.0 to 0.75,
            2400.0 to 0.20,
            2800.0 to 0.70,
            3150.0 to 0.90,
        )
        val regions = JSONArray()
        support.forEachIndexed { index, (rpm, mapBar) ->
            val petrolMs = AdaptivePetrolReference.f2(rpm, mapBar) * scale
            regions.put(
                JSONObject()
                    .put("id", "petrol-$index")
                    .put("fuel", Mp48Fuel.PETROL.wireName)
                    .put("epoch", 0)
                    .put("rpm", rpm)
                    .put("map_bar", mapBar)
                    .put("petrol_ms", petrolMs)
                    .put("petrol_squared_mean", petrolMs * petrolMs)
                    .put("pressure_diff_bar", 0.0)
                    .put("water_c", 85.0)
                    .put("gas_c", 0.0)
                    .put("quality", 0.95)
                    .put("weight", 20.0)
                    .put("samples", 20)
                    .put("visits", JSONArray().put("pv-$index"))
                    .put("sessions", JSONArray().put("historic"))
                    .put("visit_count", 1)
                    .put("session_count", 1)
                    .put("updated_at", index + 1L),
            )
        }
        file.writeText(
            JSONObject()
                .put("format", MotorLearningMemory.FORMAT)
                .put("epoch", 1)
                .put("mapHash", "")
                .put("adaptiveScale", AdaptivePetrolScaleState(acceptedScale = scale).toJson())
                .put("regions", regions)
                .put("comparisons", JSONArray())
                .put("sessions", JSONArray())
                .toString(),
        )

        val memory = MotorLearningMemory(file, RingLog())
        memory.startSession()
        val sample = cngSample(rpm = 1_800.0, mapBar = 0.45, petrolMs = 5.0)
        val status = memory.ingest(cngTelemetry(), SampleDecision.accepted(sample))

        assertEquals("CONTINUOUS_FUEL_EQUIVALENCE", status.getString("state"))
        val comparison = status.getJSONObject("comparison")
        assertTrue(comparison.getDouble("petrol_target_ms") > 0.05)
        val exported = memory.export("test")
        assertEquals(1, exported.getJSONArray("comparisons").length())
        assertEquals(scale, exported.getJSONObject("adaptiveScale").getDouble("acceptedScale"), 0.000001)
    }

    private fun cngSample(rpm: Double, mapBar: Double, petrolMs: Double) = MotorSample(
        id = "cng-live",
        startedAtElapsedMs = 1_000L,
        endedAtElapsedMs = 1_550L,
        fuel = Mp48Fuel.CNG,
        rpm = rpm,
        mapBar = mapBar,
        petrolMs = petrolMs,
        pressureDiffBar = 1.4,
        waterC = 85.0,
        gasC = 65.0,
        quality = 0.95,
        classification = SampleClassification.STRONG,
        frameCount = LearningTolerancePolicy().requiredFrames,
        diagnostics = SampleDiagnostics(
            frameCount = LearningTolerancePolicy().requiredFrames,
            durationMs = 550L,
            medianIntervalMs = 50L,
            waterCenterC = 85.0,
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

    private fun cngTelemetry() = Mp48Telemetry(
        capturedAtElapsedMs = 1_000L,
        rpm = 1_800,
        levelRaw = 100,
        gasRaw = 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = 5.0,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = Mp48Fuel.CNG,
        state = Mp48Fuel.CNG.wireName,
        waterRaw = 85,
        waterC = 85,
        gasC = 65,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 450,
        mapBar = 0.45,
        pressureDiffBar = 1.4,
        plausible = true,
    )
}
