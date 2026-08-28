package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** GS-G3B continuation: a material native AutoMatch must stale old GNV science and require a fresh identity. */
class GoldenSliceG3bAutoMatchReconcileTest {
    @get:Rule val temporary = TemporaryFolder()

    @After fun closeAuthority() = LearningCalibrationAuthority.endPhysicalSession()

    @Test
    fun `native AutoMatch invalidates old GNV then fresh identity resumes same petrol story`() {
        LearningCalibrationAuthority.beginPhysicalSession()
        publishBinding("before", 1, 41L)
        val store = LiveOnlyLearningStore(temporary.root.resolve("g3b-automatch.json"), RingLog())
        try {
            store.startSession()
            store.ingest(telemetry(650L, Mp48Fuel.PETROL, 4.0), accepted(sample("petrol", 100L, 650L, Mp48Fuel.PETROL, 4.0)))
            store.ingest(telemetry(1_350L, Mp48Fuel.CNG, 4.4), accepted(sample("cng-before", 800L, 1_350L, Mp48Fuel.CNG, 4.4)))
            val before = store.export("g3b")
            assertTrue(before.getJSONObject("summary").getInt("petrol_regions") > 0)
            assertTrue(before.getJSONArray("comparisons").length() > 0)

            LearningCalibrationAuthority.clear()
            val reset = store.onCalibrationAdjustment(
                JSONObject()
                    .put("source", "ECU_NATIVE_AUTOCAL")
                    .put("ecuNativeObserved", true)
                    .put("appWritePerformed", false)
                    .put("readbackValid", true)
                    .put("adjustmentId", "native-automatch-41")
                    .put("newHash", "map-after"),
            )
            val stale = store.export("g3b")
            assertTrue(reset.getBoolean("resetPerformed"))
            assertEquals("ECU_NATIVE_AUTOCAL_EPOCH", stale.getJSONObject("lastReset").getString("reasonCode"))
            assertTrue(stale.getJSONObject("summary").getInt("petrol_regions") > 0)
            assertEquals(0, stale.getJSONObject("summary").getInt("cng_regions"))
            assertEquals(0, stale.getJSONArray("comparisons").length())
            assertEquals(0, stale.getJSONArray("nativeLearningAnchors").length())

            val blocked = store.ingest(telemetry(2_050L, Mp48Fuel.CNG, 4.5), accepted(sample("cng-stale", 1_500L, 2_050L, Mp48Fuel.CNG, 4.5)))
            assertEquals(LiveOnlyLearningStore.CALIBRATION_REQUIRED_REASON_CODE, blocked.getString("state"))
            assertFalse(blocked.getBoolean("learning"))

            publishBinding("after", 2, 41L)
            store.ingest(telemetry(2_750L, Mp48Fuel.CNG, 4.5), accepted(sample("cng-after", 2_200L, 2_750L, Mp48Fuel.CNG, 4.5)))
            val reconciled = store.export("g3b")
            assertTrue(reconciled.getJSONArray("comparisons").length() > 0)
            val comparison = reconciled.getJSONArray("comparisons").getJSONObject(0)
            assertEquals(2, comparison.getInt("calibration_generation"))
            assertEquals("calibration-after", comparison.getString("calibration_fingerprint"))
            assertEquals(41L, comparison.getLong("usb_session_id"))
        } finally {
            store.close()
        }
    }

    private fun publishBinding(label: String, generation: Int, sessionId: Long) {
        LearningCalibrationAuthority.publish(
            LearningCalibrationBinding(
                calibrationFingerprint = "calibration-$label",
                calibrationGeneration = generation,
                geometryFingerprint = "geometry-$label",
                usbSessionId = sessionId,
                mapHash = "map-$label",
                petrolAxisMs = emptyList(),
                rpmAxis = emptyList(),
            ),
        )
    }

    private fun accepted(sample: MotorSample): SampleDecision {
        val cell = LearningGridProjection.cellFor(sample.rpm, sample.petrolMs)
        return SampleDecision.accepted(sample).copy(cellKey = cell.getString("key"), cellRow = cell.getInt("row"), cellColumn = cell.getInt("column"))
    }

    private fun sample(id: String, start: Long, end: Long, fuel: Mp48Fuel, petrolMs: Double) = MotorSample(
        id = id, startedAtElapsedMs = start, endedAtElapsedMs = end, fuel = fuel, rpm = 2_500.0, mapBar = 0.60,
        petrolMs = petrolMs, pressureDiffBar = 1.4, waterC = 80.0, gasC = if (fuel == Mp48Fuel.CNG) 35.0 else 0.0,
        quality = 0.95, classification = SampleClassification.STRONG, frameCount = LearningTolerancePolicy().requiredFrames,
        diagnostics = SampleDiagnostics(
            frameCount = LearningTolerancePolicy().requiredFrames, durationMs = 550L, medianIntervalMs = 50L,
            waterCenterC = 80.0, minimumWaterC = 55, rpmCenterShift = 0.0, rpmCenterLimit = 62.5,
            rpmOscillation = 0.0, rpmOscillationLimit = 125.0, mapCenterShift = 0.0, mapCenterLimit = 0.025,
            mapOscillation = 0.0, mapOscillationLimit = 0.05, petrolCenterShift = 0.0, petrolCenterLimit = 0.24,
            petrolOscillationRatio = 0.0, petrolOscillationLimit = 0.15, pressureCenterShift = 0.0,
            pressureCenterLimit = 0.04, pressureOscillation = 0.0, pressureOscillationLimit = 0.08,
        ),
    )

    private fun telemetry(at: Long, fuel: Mp48Fuel, petrolMs: Double) = Mp48Telemetry(
        capturedAtElapsedMs = at, rpm = 2_500, levelRaw = 100, gasRaw = if (fuel == Mp48Fuel.CNG) 100 else 0,
        gasMsDiagnostic = if (fuel == Mp48Fuel.CNG) petrolMs * 2.0 else null, petrolRaw = 100, petrolCounts = 100,
        petrolMs = petrolMs, dynamicCorrection = 0, fuelByte = if (fuel == Mp48Fuel.CNG) 1 else 0, fuel = fuel,
        state = fuel.wireName, waterRaw = 80, waterC = 80, gasC = if (fuel == Mp48Fuel.CNG) 35 else 0,
        gasPressureRaw = 100, gasPressureAbsBar = 2.0, mapRaw = 100, mapBar = 0.60, pressureDiffBar = 1.4, plausible = true,
    )
}
