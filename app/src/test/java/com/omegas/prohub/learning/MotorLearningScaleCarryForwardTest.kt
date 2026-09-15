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

class MotorLearningScaleCarryForwardTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun endSessionPromotesIndependentPetrolScaleCandidate() {
        val prior = 1.065612
        val target = 1.066815
        val memory = seededMemory(prior)
        val started = memory.startSession()
        val sessionId = started.getString("session_id")

        supportPoints().forEachIndexed { index, (rpm, mapBar) ->
            val petrolMs = AdaptivePetrolReference.f2(rpm, mapBar) * target
            memory.ingest(
                petrolTelemetry(rpm, mapBar, petrolMs),
                SampleDecision.accepted(petrolSample(index, rpm, mapBar, petrolMs)),
            )
        }
        memory.endSession("TEST_COMPLETE")

        val scale = memory.export("test").getJSONObject("adaptiveScale")
        assertEquals(target, scale.getDouble("acceptedScale"), 0.0002)
        assertEquals(target, scale.getDouble("candidateScale"), 0.0002)
        assertEquals(sessionId, scale.getString("promotedSessionId"))
    }

    @Test
    fun distantSessionCandidateIsBoundedInsteadOfResettingCarriedScale() {
        val prior = 1.065612
        val misleading = 1.10058
        val memory = seededMemory(prior)
        memory.startSession()

        supportPoints().forEachIndexed { index, (rpm, mapBar) ->
            val petrolMs = AdaptivePetrolReference.f2(rpm, mapBar) * misleading
            memory.ingest(
                petrolTelemetry(rpm, mapBar, petrolMs),
                SampleDecision.accepted(petrolSample(index, rpm, mapBar, petrolMs)),
            )
        }
        memory.endSession("TEST_COMPLETE")

        val scale = memory.export("test").getJSONObject("adaptiveScale")
        assertTrue(scale.getDouble("acceptedScale") <= prior * 1.0050001)
        assertEquals(misleading, scale.getDouble("candidateScale"), 0.0002)
    }

    private fun seededMemory(scale: Double): MotorLearningMemory {
        val file = temporary.newFile("scale-${System.nanoTime()}.json")
        file.writeText(
            JSONObject()
                .put("format", MotorLearningMemory.FORMAT)
                .put("epoch", 1)
                .put("mapHash", "")
                .put(
                    "adaptiveScale",
                    AdaptivePetrolScaleState(
                        acceptedScale = scale,
                        candidateScale = scale,
                        supportRegions = 12,
                        promotedSessionId = "previous-session",
                        updatedAt = 1L,
                    ).toJson(),
                )
                .put("regions", JSONArray())
                .put("comparisons", JSONArray())
                .put("sessions", JSONArray())
                .toString(),
        )
        return MotorLearningMemory(file, RingLog())
    }

    private fun supportPoints() = listOf(
        900.0 to 0.30,
        1350.0 to 0.40,
        1850.0 to 0.55,
        2350.0 to 0.70,
        2900.0 to 0.80,
        3150.0 to 0.90,
    )

    private fun petrolSample(index: Int, rpm: Double, mapBar: Double, petrolMs: Double) = MotorSample(
        id = "petrol-$index",
        startedAtElapsedMs = 1_000L + index * 1_000L,
        endedAtElapsedMs = 1_550L + index * 1_000L,
        fuel = Mp48Fuel.PETROL,
        rpm = rpm,
        mapBar = mapBar,
        petrolMs = petrolMs,
        pressureDiffBar = 0.0,
        waterC = 85.0,
        gasC = 0.0,
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

    private fun petrolTelemetry(rpm: Double, mapBar: Double, petrolMs: Double) = Mp48Telemetry(
        capturedAtElapsedMs = 1_000L,
        rpm = rpm.toInt(),
        levelRaw = 100,
        gasRaw = 0,
        gasMsDiagnostic = null,
        petrolRaw = (petrolMs / 0.00256).toInt(),
        petrolCounts = (petrolMs / 0.00256).toInt(),
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = 0x80,
        fuel = Mp48Fuel.PETROL,
        state = Mp48Fuel.PETROL.wireName,
        waterRaw = 85,
        waterC = 85,
        gasC = 0,
        gasPressureRaw = 0,
        gasPressureAbsBar = 0.0,
        mapRaw = (mapBar * 1000).toInt(),
        mapBar = mapBar,
        pressureDiffBar = 0.0,
        plausible = true,
    )
}
