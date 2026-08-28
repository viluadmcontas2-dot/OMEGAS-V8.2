package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Golden Slice G3B: the native AutoCal anchor must continue the same physical
 * story that produced the calibration-bound CNG comparison. Independent
 * harnesses or a maturity event from another USB session cannot be spliced in.
 */
class GoldenSliceG3bSessionContinuityTest {
    @get:Rule val temporary = TemporaryFolder()

    @After
    fun closePhysicalCalibrationAuthority() {
        LearningCalibrationAuthority.endPhysicalSession()
    }

    @Test
    fun `same store comparison and native anchor preserve physical identity and learning epoch`() {
        val sessionId = 41L
        activateCalibration(sessionId)
        val store = store()
        try {
            store.startSession()
            ingestComparablePetrolAndCng(store)

            val beforeNative = store.export("g3b")
            val comparison = beforeNative.getJSONArray("comparisons").getJSONObject(0)
            assertEquals(sessionId, comparison.getLong("usb_session_id"))
            assertEquals(1, comparison.getInt("calibration_generation"))

            val imported = store.importNativeSnapshot(snapshot(sessionId))
            val afterNative = store.export("g3b")
            val anchor = afterNative.getJSONArray("nativeLearningAnchors").getJSONObject(0)

            assertEquals(1, imported.getInt("importedNativeAnchors"))
            assertEquals(sessionId, anchor.getLong("sessionId"))
            assertEquals(afterNative.getInt("epoch"), anchor.getInt("calibrationEpoch"))
            assertEquals(comparison.getInt("calibration_generation"), anchor.getInt("calibrationGeneration"))
            assertEquals(comparison.getString("calibration_fingerprint"), anchor.getString("calibrationFingerprint"))
            assertEquals(comparison.getString("geometry_fingerprint"), anchor.getString("geometryFingerprint"))
            assertEquals(comparison.getString("map_hash"), anchor.getString("mapHash"))
            assertFalse(anchor.getBoolean("comparisonVote"))
            assertEquals(0.0, anchor.getDouble("effectiveComparisonWeight"), 0.0)
            assertFalse(anchor.getBoolean("automaticWrite"))
            assertEquals(1, afterNative.getJSONArray("comparisons").length())
        } finally {
            store.close()
        }
    }

    @Test
    fun `native maturity from another physical session cannot become an active anchor`() {
        activateCalibration(41L)
        val store = store()
        try {
            store.startSession()
            ingestComparablePetrolAndCng(store)
            assertEquals(1, store.export("g3b").getJSONArray("comparisons").length())

            val imported = store.importNativeSnapshot(snapshot(99L))
            val exported = store.export("g3b")

            assertEquals(0, imported.getInt("importedNativeAnchors"))
            assertEquals(0, exported.getJSONArray("nativeLearningAnchors").length())
            assertEquals(1, exported.getJSONArray("comparisons").length())
            assertTrue(exported.getJSONArray("nativeEcuEvidence").length() > 0)
        } finally {
            store.close()
        }
    }

    @Test
    fun `managed physical session without reconciled calibration keeps native maturity diagnostic only`() {
        LearningCalibrationAuthority.beginPhysicalSession()
        val store = store()
        try {
            store.startSession()
            val imported = store.importNativeSnapshot(snapshot(41L))
            val exported = store.export("g3b")

            assertEquals(0, imported.getInt("importedNativeAnchors"))
            assertEquals(0, exported.getJSONArray("nativeLearningAnchors").length())
            assertTrue(exported.getJSONArray("nativeEcuEvidence").length() > 0)
        } finally {
            store.close()
        }
    }

    private fun activateCalibration(sessionId: Long) {
        LearningCalibrationAuthority.beginPhysicalSession()
        LearningCalibrationAuthority.publish(
            LearningCalibrationBinding(
                calibrationFingerprint = "g3b-calibration",
                calibrationGeneration = 1,
                geometryFingerprint = "g3b-geometry",
                usbSessionId = sessionId,
                mapHash = "g3b-map",
                petrolAxisMs = emptyList(),
                rpmAxis = emptyList(),
            ),
        )
    }

    private fun ingestComparablePetrolAndCng(store: LiveOnlyLearningStore) {
        store.ingest(
            telemetry(650L, Mp48Fuel.PETROL, 4.0),
            accepted(sample("g3b-petrol", 100L, 650L, Mp48Fuel.PETROL, 4.0)),
        )
        store.ingest(
            telemetry(1_350L, Mp48Fuel.CNG, 4.3),
            accepted(sample("g3b-cng", 800L, 1_350L, Mp48Fuel.CNG, 4.3)),
        )
    }

    private fun snapshot(sessionId: Long): JSONObject {
        val counts = JSONArray()
        val petrol = JSONArray()
        val map = JSONArray()
        repeat(18) { index ->
            counts.put(if (index == 4) 8 else 0)
            petrol.put(2000 + index)
            map.put(500 + index)
        }
        val event = JSONObject()
            .put("eventType", "NATIVE_BAND_MATURED")
            .put("nativeValidity", true)
            .put("sessionId", sessionId)
            .put("snapshotId", "G3B-AUTOCAL")
            .put("snapshotHash", "g3b-snapshot-hash-$sessionId")
            .put("fuel", "GNV")
            .put("bandIndex", 4)
            .put("zone", "NORMAL")
            .put("counter", 8)
            .put("threshold", 8)
            .put("previousObservedAtElapsedMs", 1_000L)
            .put("observedAtElapsedMs", 2_000L)
            .put("correlationState", "CORRELATED")
            .put("correlationConfidence", 0.80)
            .put("rpmConfidence", 0.75)
            .put("rpm", 2_500)
            .put("correlatedPetrolMs", 4.30)
            .put("correlatedGasMs", 7.20)
            .put("correlatedMapBar", 0.60)
            .put("correlatedFuel", "GNV")
            .put("correlatedFrameElapsedMs", 1_350L)
            .put("correlationLagMs", 650L)
            .put("firstTelemetrySequence", 100L)
            .put("lastTelemetrySequence", 108L)
            .put("matchedTelemetryFrames", 9)

        return JSONObject()
            .put("snapshotId", "G3B-AUTOCAL")
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

    private fun store() = LiveOnlyLearningStore(
        temporary.root.resolve("g3b-${System.nanoTime()}.json"),
        RingLog(),
    )

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
