package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LiveOnlyLearningStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `confirmed calibration preserves petrol and clears cng comparisons and suggestions`() {
        val store = store()
        store.startSession()
        store.ingest(
            telemetry(650L, Mp48Fuel.PETROL, 4.0),
            accepted(sample("p", 100L, 650L, Mp48Fuel.PETROL, 4.0)),
        )
        store.ingest(
            telemetry(1_350L, Mp48Fuel.CNG, 5.0),
            accepted(sample("g", 800L, 1_350L, Mp48Fuel.CNG, 5.0)),
        )
        val before = store.export("test")
        assertTrue(before.getJSONObject("summary").getInt("petrol_regions") > 0)
        assertTrue(before.getJSONObject("summary").getInt("cng_regions") > 0)
        assertTrue(before.getJSONArray("comparisons").length() > 0)

        val reset = store.onCalibrationAdjustment(confirmedUpdate())
        val after = store.export("test")
        assertTrue(reset.getBoolean("resetPerformed"))
        assertTrue(reset.getBoolean("petrolBaselinePreserved"))
        assertTrue(after.getJSONObject("summary").getInt("petrol_regions") > 0)
        assertEquals(0, after.getJSONObject("summary").getInt("cng_regions"))
        assertEquals(0, after.getJSONObject("summary").getInt("comparisons"))
        assertEquals(0, after.getJSONArray("comparisons").length())
        assertFalse(after.getBoolean("retroactiveLearningAccepted"))
        assertTrue(after.getBoolean("petrolBaselinePreserved"))
        assertEquals(LiveOnlyLearningStore.RESET_POLICY, after.getString("resetPolicy"))
    }

    @Test
    fun `repeated resets preserve petrol without growing internal namespaces`() {
        val store = store()
        store.startSession()
        store.ingest(
            telemetry(650L, Mp48Fuel.PETROL, 4.0),
            accepted(sample("stable-p", 100L, 650L, Mp48Fuel.PETROL, 4.0)),
        )

        store.onCalibrationAdjustment(confirmedUpdate().put("adjustmentId", "reset-1"))
        val afterFirst = store.export("test").getJSONArray("regions").getJSONObject(0)
        val firstId = afterFirst.getString("id")
        val firstVisits = afterFirst.getJSONArray("visits").toString()
        val firstSessions = afterFirst.getJSONArray("sessions").toString()

        repeat(4) { index ->
            store.onCalibrationAdjustment(
                confirmedUpdate().put("adjustmentId", "reset-${index + 2}"),
            )
        }

        val afterRepeated = store.export("test").getJSONArray("regions").getJSONObject(0)
        assertEquals(firstId, afterRepeated.getString("id"))
        assertEquals(firstVisits, afterRepeated.getJSONArray("visits").toString())
        assertEquals(firstSessions, afterRepeated.getJSONArray("sessions").toString())
        assertTrue(firstId.startsWith("local-petrol-preserved:"))
        assertFalse(firstId.removePrefix("local-petrol-preserved:").contains("local-petrol-preserved:"))
        assertFalse(afterRepeated.getString("id").contains("reset-audit:reset-audit:"))
    }

    @Test
    fun `retroactive archive is rejected without mutating active memory`() {
        val store = store()
        val result = store.merge(
            JSONObject()
                .put("format", SignalLearningStore.FORMAT)
                .put("deviceId", "other-phone"),
            "local",
        )
        assertFalse(result.getBoolean("ok"))
        assertFalse(result.getBoolean("accepted"))
        assertEquals(LiveOnlyLearningStore.RETROACTIVE_REASON_CODE, result.getString("reasonCode"))
        assertEquals(0, store.export("test").getJSONArray("regions").length())
    }

    @Test
    fun `after reset new live cng immediately uses preserved petrol baseline`() {
        val store = store()
        store.startSession()
        store.ingest(
            telemetry(650L, Mp48Fuel.PETROL, 4.0),
            accepted(sample("base-p", 100L, 650L, Mp48Fuel.PETROL, 4.0)),
        )
        store.ingest(
            telemetry(1_350L, Mp48Fuel.CNG, 5.0),
            accepted(sample("old-g", 800L, 1_350L, Mp48Fuel.CNG, 5.0)),
        )
        store.onCalibrationAdjustment(confirmedUpdate())

        val afterReset = store.export("test")
        assertTrue(afterReset.getJSONObject("summary").getInt("petrol_regions") > 0)
        assertEquals(0, afterReset.getJSONArray("comparisons").length())

        store.ingest(
            telemetry(2_050L, Mp48Fuel.CNG, 5.2),
            accepted(sample("new-live-g", 1_500L, 2_050L, Mp48Fuel.CNG, 5.2)),
        )
        assertTrue(store.export("test").getJSONArray("comparisons").length() > 0)
    }

    @Test
    fun `unconfirmed notification cannot erase learning`() {
        val store = store()
        store.startSession()
        store.ingest(
            telemetry(650L, Mp48Fuel.PETROL, 4.0),
            accepted(sample("p", 100L, 650L, Mp48Fuel.PETROL, 4.0)),
        )
        val result = store.onCalibrationAdjustment(JSONObject().put("newHash", "not-confirmed"))
        assertFalse(result.getBoolean("ok"))
        assertFalse(result.getBoolean("resetPerformed"))
        assertTrue(store.export("test").getJSONObject("summary").getInt("petrol_regions") > 0)
    }

    @Test
    fun `policy activation preserves legacy petrol and removes legacy cng once`() {
        val state = temporary.root.resolve("migration.json")
        SignalLearningStore(state, RingLog()).also { legacy ->
            legacy.startSession()
            legacy.ingest(
                telemetry(650L, Mp48Fuel.PETROL, 4.0),
                accepted(sample("legacy-p", 100L, 650L, Mp48Fuel.PETROL, 4.0)),
            )
            legacy.ingest(
                telemetry(1_350L, Mp48Fuel.CNG, 5.0),
                accepted(sample("legacy-g", 800L, 1_350L, Mp48Fuel.CNG, 5.0)),
            )
            legacy.close()
        }

        LiveOnlyLearningStore(state, RingLog()).also { first ->
            val migrated = first.export("test")
            assertTrue(migrated.getJSONObject("summary").getInt("petrol_regions") > 0)
            assertEquals(0, migrated.getJSONObject("summary").getInt("cng_regions"))
            assertEquals(0, migrated.getJSONArray("comparisons").length())
            first.close()
        }

        LiveOnlyLearningStore(state, RingLog()).also { restored ->
            assertTrue(restored.export("test").getJSONObject("summary").getInt("petrol_regions") > 0)
            restored.close()
        }
    }

    @Test
    fun `policy repairs petrol quarantined by previous full reset`() {
        val state = temporary.root.resolve("bad-reset.json")
        SignalLearningStore(state, RingLog()).also { original ->
            original.startSession()
            original.ingest(
                telemetry(650L, Mp48Fuel.PETROL, 4.0),
                accepted(sample("quarantined-p", 100L, 650L, Mp48Fuel.PETROL, 4.0)),
            )
            original.ingest(
                telemetry(1_350L, Mp48Fuel.CNG, 5.0),
                accepted(sample("quarantined-g", 800L, 1_350L, Mp48Fuel.CNG, 5.0)),
            )
            original.close()
        }

        val quarantine = File(temporary.root, "learning_quarantine").apply { mkdirs() }
        val archived = File(quarantine, "bad-reset_policy_v1_1000_0.json")
        state.copyTo(archived, overwrite = true)
        state.delete()
        File(state.parentFile, state.name + ".bak").delete()

        SignalLearningStore(state, RingLog()).also { emptyAfterBadReset ->
            emptyAfterBadReset.startSession()
            emptyAfterBadReset.close()
        }

        LiveOnlyLearningStore(state, RingLog()).also { repaired ->
            val recovered = repaired.export("test")
            assertTrue(recovered.getJSONObject("summary").getInt("petrol_regions") > 0)
            assertEquals(0, recovered.getJSONObject("summary").getInt("cng_regions"))
            assertEquals(0, recovered.getJSONArray("comparisons").length())
            assertEquals(
                archived.name,
                recovered.getJSONObject("lastReset").getString("petrolRecoverySource"),
            )
            repaired.close()
        }
    }

    private fun store() = LiveOnlyLearningStore(
        temporary.root.resolve("live-only-${System.nanoTime()}.json"),
        RingLog(),
    )

    private fun confirmedUpdate() = JSONObject()
        .put("calibrationType", "MAP_K")
        .put("adjustmentId", "test-adjustment")
        .put("newHash", "hash-after")
        .put("humanConfirmed", true)
        .put("readbackValid", true)

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
        pressureDiffBar = 1.4,
        waterC = 80.0,
        gasC = if (fuel == Mp48Fuel.CNG) 35.0 else 0.0,
        quality = 0.95,
        classification = SampleClassification.STRONG,
        frameCount = LearningTolerancePolicy().requiredFrames,
        diagnostics = diagnostics(),
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
        pressureDiffBar = 1.4,
        plausible = true,
    )
}
