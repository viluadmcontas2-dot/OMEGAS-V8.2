package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * GS-G3B single story: the same physical session/calibration that produces the
 * G3A PETROL→CNG comparison must also produce a real native-event correlation,
 * a zero-vote NativeLearningAnchor, and then survive the AutoMatch
 * stale→reconcile boundary without stitching independent harness results.
 */
class GoldenSliceG3bEndToEndStoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @After
    fun closeAuthority() {
        LearningCalibrationAuthority.endPhysicalSession()
    }

    @Test
    fun `one G3A to G3B story correlates native band then reconciles AutoMatch`() {
        val traceId = "GS-G3B-E2E-41"
        val sessionId = 41L
        LearningCalibrationAuthority.beginPhysicalSession()
        publishBinding("before", generation = 1, sessionId = sessionId)

        val store = LiveOnlyLearningStore(
            temporary.root.resolve("$traceId.json"),
            RingLog(),
        )
        try {
            store.startSession()

            // G3A — gasoline reference and comparable CNG observation in one store.
            store.ingest(
                telemetry(650L, Mp48Fuel.PETROL, 4.0),
                accepted(sample("$traceId-PETROL", 100L, 650L, Mp48Fuel.PETROL, 4.0)),
            )
            store.ingest(
                telemetry(1_350L, Mp48Fuel.CNG, 4.4),
                accepted(sample("$traceId-CNG", 800L, 1_350L, Mp48Fuel.CNG, 4.4)),
            )

            val g3a = store.export(traceId)
            assertTrue(g3a.getJSONObject("summary").getInt("petrol_regions") > 0)
            assertEquals(1, g3a.getJSONArray("comparisons").length())
            val comparisonBefore = g3a.getJSONArray("comparisons").getJSONObject(0)
            assertEquals(sessionId, comparisonBefore.getLong("usb_session_id"))
            assertEquals(1, comparisonBefore.getInt("calibration_generation"))
            assertEquals("calibration-before", comparisonBefore.getString("calibration_fingerprint"))

            // G3B — real typed micro-window + real production correlator in that same
            // CNG observation interval/session. Correlation fields are never fabricated.
            val window = NativeAnchorTelemetryWindow(maxFrames = 16, maxAgeMs = 5_000L)
            listOf(1_200L, 1_250L, 1_300L, 1_350L).forEach { at ->
                window.record(
                    elapsedMs = at,
                    rpm = 2_500,
                    mapBar = 0.60,
                    petrolMs = 4.4,
                    fuel = "GNV",
                    sessionId = sessionId,
                    gasMsDiagnostic = 8.8,
                    plausible = true,
                )
            }
            val correlation = NativeAutoCalEventCorrelator.correlate(
                frames = window.between(1_200L, 1_400L),
                sourceFuel = NativeAutoCalEventCorrelator.SourceFuel.CNG,
                nativePetrolMs = 4.4,
                nativeMapBar = 0.60,
                observedAtElapsedMs = 1_400L,
                windowFromElapsedMs = 1_200L,
                windowToElapsedMs = 1_400L,
                policy = LearningTolerancePolicy(),
                sessionId = sessionId,
            )
            assertEquals("CORRELATED", correlation.state)
            assertTrue(correlation.confidence > 0.0)
            assertTrue(correlation.matchedFrames >= 2)
            assertNotNull(correlation.overlapKey)

            val event = JSONObject()
                .put("eventType", "NATIVE_BAND_MATURED")
                .put("nativeValidity", true)
                .put("sessionId", sessionId)
                .put("snapshotId", "$traceId-AUTOCAL")
                .put("snapshotHash", "$traceId-SNAPSHOT-HASH")
                .put("sourceFuel", "CNG")
                .put("fuel", "GNV")
                .put("bandIndex", 4)
                .put("zone", "NORMAL")
                .put("counter", 8)
                .put("threshold", 8)
                .put("previousObservedAtElapsedMs", 1_200L)
                .put("observedAtElapsedMs", 1_400L)
                .put("correlationState", correlation.state)
                .put("correlationReason", correlation.reason)
                .put("correlationConfidence", correlation.confidence)
                .put("rpmConfidence", correlation.rpmConfidence)
                .put("rpm", correlation.rpm)
                .put("correlatedMapBar", correlation.mapBar)
                .put("correlatedPetrolMs", correlation.petrolMs)
                .put("correlatedGasMs", correlation.gasMsDiagnostic)
                .put("correlatedFuel", "GNV")
                .put("correlatedFrameElapsedMs", correlation.correlatedFrameElapsedMs)
                .put("correlationLagMs", correlation.lagMs)
                .put("firstTelemetrySequence", correlation.firstSequence)
                .put("lastTelemetrySequence", correlation.lastSequence)
                .put("matchedTelemetryFrames", correlation.matchedFrames)
                .put("overlapKey", correlation.overlapKey)

            val imported = store.importNativeSnapshot(nativeSnapshot(traceId, event))
            val anchored = store.export(traceId)
            assertEquals(1, imported.getInt("importedNativeAnchors"))
            assertEquals(1, anchored.getJSONArray("comparisons").length())
            assertEquals(1, anchored.getJSONArray("nativeLearningAnchors").length())
            val anchor = anchored.getJSONArray("nativeLearningAnchors").getJSONObject(0)
            assertEquals(sessionId, anchor.getLong("sessionId"))
            assertEquals(1, anchor.getInt("calibrationGeneration"))
            assertEquals("calibration-before", anchor.getString("calibrationFingerprint"))
            assertEquals("geometry-before", anchor.getString("geometryFingerprint"))
            assertEquals("map-before", anchor.getString("mapHash"))
            assertEquals(correlation.overlapKey, anchor.getString("overlapKey"))
            assertFalse(anchor.getBoolean("comparisonVote"))
            assertEquals(0.0, anchor.getDouble("effectiveComparisonWeight"), 0.0)
            assertFalse(anchor.getBoolean("automaticWrite"))

            // Same story crosses a material native AutoMatch: old GNV science must
            // stale, the petrol reference must survive, and CNG stays fail-closed
            // until a fresh Calibration Identity for the same physical session exists.
            LearningCalibrationAuthority.clear()
            val reset = store.onCalibrationAdjustment(
                JSONObject()
                    .put("source", "ECU_NATIVE_AUTOCAL")
                    .put("ecuNativeObserved", true)
                    .put("appWritePerformed", false)
                    .put("readbackValid", true)
                    .put("adjustmentId", "$traceId-AUTOMATCH")
                    .put("newHash", "map-after"),
            )
            assertTrue(reset.getBoolean("resetPerformed"))

            val stale = store.export(traceId)
            assertEquals("ECU_NATIVE_AUTOCAL_EPOCH", stale.getJSONObject("lastReset").getString("reasonCode"))
            assertTrue(stale.getJSONObject("summary").getInt("petrol_regions") > 0)
            assertEquals(0, stale.getJSONObject("summary").getInt("cng_regions"))
            assertEquals(0, stale.getJSONArray("comparisons").length())
            assertEquals(0, stale.getJSONArray("nativeLearningAnchors").length())

            val blocked = store.ingest(
                telemetry(2_050L, Mp48Fuel.CNG, 4.5),
                accepted(sample("$traceId-CNG-STALE", 1_500L, 2_050L, Mp48Fuel.CNG, 4.5)),
            )
            assertEquals(LiveOnlyLearningStore.CALIBRATION_REQUIRED_REASON_CODE, blocked.getString("state"))
            assertFalse(blocked.getBoolean("learning"))

            publishBinding("after", generation = 2, sessionId = sessionId)
            store.ingest(
                telemetry(2_750L, Mp48Fuel.CNG, 4.5),
                accepted(sample("$traceId-CNG-AFTER", 2_200L, 2_750L, Mp48Fuel.CNG, 4.5)),
            )
            val reconciled = store.export(traceId)
            assertTrue(reconciled.getJSONObject("summary").getInt("petrol_regions") > 0)
            assertTrue(reconciled.getJSONArray("comparisons").length() > 0)
            val comparisonAfter = reconciled.getJSONArray("comparisons").getJSONObject(0)
            assertEquals(sessionId, comparisonAfter.getLong("usb_session_id"))
            assertEquals(2, comparisonAfter.getInt("calibration_generation"))
            assertEquals("calibration-after", comparisonAfter.getString("calibration_fingerprint"))
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

    private fun nativeSnapshot(traceId: String, event: JSONObject): JSONObject {
        val counts = JSONArray()
        val petrol = JSONArray()
        val map = JSONArray()
        repeat(18) { index ->
            counts.put(if (index == 4) 8 else 0)
            petrol.put(2_000 + index)
            map.put(500 + index)
        }
        return JSONObject()
            .put("snapshotId", "$traceId-AUTOCAL")
            .put("autoCalEnabled", 1)
            .put(
                "fields",
                JSONArray()
                    .put(field("NUM_BUF_UPD_GAS", counts))
                    .put(field("PETR_INJ_TBUF_GAS", petrol))
                    .put(field("MNFLD_PRESS_BUF_GAS", map)),
            )
            .put("nativeMaturityEvents", JSONArray().put(event))
    }

    private fun field(key: String, values: JSONArray): JSONObject = JSONObject()
        .put("key", key)
        .put("status", "VALID")
        .put("rawValues", values)

    private fun accepted(sample: MotorSample): SampleDecision {
        val cell = LearningGridProjection.cellFor(sample.rpm, sample.petrolMs)
        return SampleDecision.accepted(sample).copy(
            cellKey = cell.getString("key"),
            cellRow = cell.getInt("row"),
            cellColumn = cell.getInt("column"),
        )
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
        pressureDiffBar = 1.40,
        waterC = 80.0,
        gasC = if (fuel == Mp48Fuel.CNG) 35.0 else 0.0,
        quality = 0.95,
        classification = SampleClassification.STRONG,
        frameCount = LearningTolerancePolicy().requiredFrames,
        diagnostics = diagnostics(),
    )

    private fun telemetry(at: Long, fuel: Mp48Fuel, petrolMs: Double) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = if (fuel == Mp48Fuel.CNG) 100 else 0,
        gasMsDiagnostic = if (fuel == Mp48Fuel.CNG) petrolMs * 2.0 else null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = if (fuel == Mp48Fuel.CNG) 1 else 0,
        fuel = fuel,
        state = fuel.wireName,
        waterRaw = 80,
        waterC = 80,
        gasC = if (fuel == Mp48Fuel.CNG) 35 else 0,
        gasPressureRaw = 100,
        gasPressureAbsBar = 2.0,
        mapRaw = 100,
        mapBar = 0.60,
        pressureDiffBar = 1.40,
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
