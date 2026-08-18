package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CalibrationBoundLearningEvidenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @After
    fun clearAuthority() {
        LearningCalibrationAuthority.clear()
    }

    @Test
    fun cngIsBlockedUntilMaterialCalibrationBindingExists() {
        LearningCalibrationAuthority.clear()
        val store = store("blocked")
        store.startSession()
        val result = store.ingest(
            telemetry(Mp48Fuel.CNG, 1_000L),
            SampleDecision.accepted(sample("cng", Mp48Fuel.CNG, 1_000L, 4.5)),
        )

        assertEquals(LiveOnlyLearningStore.CALIBRATION_REQUIRED_REASON_CODE, result.getString("state"))
        assertFalse(result.getBoolean("learning"))
        assertEquals(0, store.export("test").getJSONArray("regions").length())
        store.close()
    }

    @Test
    fun calibrationChangeNeverAggregatesCngAcrossAAndBAndPreservesPetrol() {
        val store = store("partition")
        store.startSession()

        val a = binding("cal-A", generation = 1, geometry = "geo-A", session = 11L, mapHash = "map-A")
        LearningCalibrationAuthority.publish(a)
        store.ingest(
            telemetry(Mp48Fuel.PETROL, 1_000L),
            SampleDecision.accepted(sample("petrol", Mp48Fuel.PETROL, 1_000L, 4.0)),
        )
        val firstCng = store.ingest(
            telemetry(Mp48Fuel.CNG, 2_000L),
            SampleDecision.accepted(sample("cng-A", Mp48Fuel.CNG, 2_000L, 4.5)),
        )
        assertEquals("cal-A", firstCng.getJSONObject("comparison").getString("calibration_fingerprint"))

        val exportedA = store.export("A")
        assertEquals(1, countFuel(exportedA, Mp48Fuel.PETROL.wireName))
        assertEquals(1, countFuel(exportedA, Mp48Fuel.CNG.wireName))
        assertEquals("cal-A", cngRegion(exportedA).getString("calibration_fingerprint"))
        assertEquals(1, exportedA.getJSONArray("comparisons").length())

        val b = binding("cal-B", generation = 2, geometry = "geo-B", session = 11L, mapHash = "map-B")
        LearningCalibrationAuthority.publish(b)
        val firstB = store.ingest(
            telemetry(Mp48Fuel.CNG, 3_000L),
            SampleDecision.accepted(sample("cng-B", Mp48Fuel.CNG, 3_000L, 4.6)),
        )
        assertEquals("cal-B", firstB.getJSONObject("comparison").getString("calibration_fingerprint"))

        val exportedB = store.export("B")
        assertEquals(1, countFuel(exportedB, Mp48Fuel.PETROL.wireName))
        assertEquals(1, countFuel(exportedB, Mp48Fuel.CNG.wireName))
        assertEquals("cal-B", cngRegion(exportedB).getString("calibration_fingerprint"))
        assertEquals("geo-B", cngRegion(exportedB).getString("geometry_fingerprint"))
        assertEquals(2, cngRegion(exportedB).getInt("calibration_generation"))
        assertEquals(1, exportedB.getJSONArray("comparisons").length())
        assertTrue(exportedB.getJSONObject("lastReset").getString("reasonCode") == "CALIBRATION_IDENTITY_CHANGED")
        assertFalse(exportedB.toString().contains("\"calibration_fingerprint\":\"cal-A\""))
        store.close()
    }

    @Test
    fun reconnectingSameMaterialCalibrationUpdatesSessionProvenanceWithoutReset() {
        val store = store("same")
        store.startSession()
        LearningCalibrationAuthority.publish(binding("same", 7, "geo", 101L, "map"))
        store.ingest(
            telemetry(Mp48Fuel.PETROL, 1_000L),
            SampleDecision.accepted(sample("petrol", Mp48Fuel.PETROL, 1_000L, 4.0)),
        )
        store.ingest(
            telemetry(Mp48Fuel.CNG, 2_000L),
            SampleDecision.accepted(sample("cng-1", Mp48Fuel.CNG, 2_000L, 4.5)),
        )
        val before = store.export("before")
        assertEquals(1, before.getJSONArray("comparisons").length())

        store.endSession("reconnect")
        store.startSession()
        LearningCalibrationAuthority.publish(binding("same", 7, "geo", 202L, "map"))
        store.ingest(
            telemetry(Mp48Fuel.CNG, 5_000L),
            SampleDecision.accepted(sample("cng-2", Mp48Fuel.CNG, 5_000L, 4.4)),
        )
        val after = store.export("after")
        assertTrue(after.getJSONArray("comparisons").length() >= 1)
        assertEquals(202L, after.getJSONObject("calibration_binding").getLong("usb_session_id"))
        assertEquals("same", cngRegion(after).getString("calibration_fingerprint"))
        store.close()
    }

    private fun store(name: String) = LiveOnlyLearningStore(
        temporary.root.resolve("$name.json"),
        RingLog(),
    )

    private fun binding(
        fingerprint: String,
        generation: Int,
        geometry: String,
        session: Long,
        mapHash: String,
    ) = LearningCalibrationBinding(fingerprint, generation, geometry, session, mapHash)

    private fun countFuel(root: org.json.JSONObject, fuel: String): Int {
        val regions = root.getJSONArray("regions")
        var count = 0
        repeat(regions.length()) { if (regions.getJSONObject(it).getString("fuel") == fuel) count++ }
        return count
    }

    private fun cngRegion(root: org.json.JSONObject): org.json.JSONObject {
        val regions = root.getJSONArray("regions")
        repeat(regions.length()) {
            val region = regions.getJSONObject(it)
            if (region.getString("fuel") == Mp48Fuel.CNG.wireName) return region
        }
        error("CNG region missing")
    }

    private fun sample(id: String, fuel: Mp48Fuel, at: Long, petrolMs: Double) = MotorSample(
        id = id,
        startedAtElapsedMs = at,
        endedAtElapsedMs = at + 550L,
        fuel = fuel,
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

    private fun telemetry(fuel: Mp48Fuel, at: Long) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = if (fuel == Mp48Fuel.CNG) 100 else 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = 4.0,
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
