package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONArray
import org.json.JSONObject

class SignalLearningStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `overlapping healthy windows remain visible and enter memory with reduced weight`() {
        val store = store()
        store.startSession()
        val first = store.ingest(telemetry(650L), accepted(sample("a", 100L, 650L)))
        val overlapping = store.ingest(telemetry(800L), accepted(sample("b", 250L, 800L)))
        val exported = store.export("test")
        val region = exported.getJSONArray("regions").getJSONObject(0)

        assertTrue(first.getBoolean("memory_sample_accepted"))
        assertTrue(overlapping.getBoolean("memory_sample_accepted"))
        assertEquals("OVERLAPPING_WINDOW_WEIGHTED", overlapping.getString("memory_reason_code"))
        assertEquals(2, region.getInt("samples"))
        assertTrue(region.getDouble("weight") < 2.0)
        assertEquals(1L, exported.getLong("independentSamples"))
        assertEquals(1L, exported.getLong("correlatedSamplesWeighted"))
    }

    @Test
    fun `first non overlapping window after the previous end receives full new weight`() {
        val store = store()
        store.startSession()
        store.ingest(telemetry(650L), accepted(sample("a", 100L, 650L)))
        store.ingest(telemetry(800L), accepted(sample("b", 250L, 800L)))
        val independent = store.ingest(telemetry(1_250L), accepted(sample("c", 700L, 1_250L)))
        val exported = store.export("test")

        assertTrue(independent.getBoolean("memory_sample_accepted"))
        assertEquals(3, exported.getJSONArray("regions").getJSONObject(0).getInt("samples"))
        assertEquals(2L, exported.getLong("independentSamples"))
        assertEquals(1L, exported.getLong("correlatedSamplesWeighted"))
    }

    @Test
    fun `projected cell change does not discard overlapping physical evidence`() {
        val store = store()
        store.startSession()
        store.ingest(telemetry(650L, petrolMs = 4.0), accepted(sample("a", 100L, 650L, petrolMs = 4.0)))
        val otherCell = store.ingest(telemetry(800L, petrolMs = 8.0), accepted(sample("b", 250L, 800L, petrolMs = 8.0)))
        val exported = store.export("test")
        val region = exported.getJSONArray("regions").getJSONObject(0)

        assertTrue(otherCell.getBoolean("memory_sample_accepted"))
        assertEquals("OVERLAPPING_WINDOW_WEIGHTED", otherCell.getString("memory_reason_code"))
        assertEquals(2, region.getInt("samples"))
        assertEquals(1L, exported.getLong("independentSamples"))
        assertEquals(1L, exported.getLong("correlatedSamplesWeighted"))
    }

    @Test
    fun `new projected cell is absorbed normally after fully new frames`() {
        val store = store()
        store.startSession()
        store.ingest(telemetry(650L, petrolMs = 4.0), accepted(sample("a", 100L, 650L, petrolMs = 4.0)))
        val otherCell = store.ingest(telemetry(1_250L, petrolMs = 8.0), accepted(sample("b", 700L, 1_250L, petrolMs = 8.0)))
        val exported = store.export("test")
        val regions = exported.getJSONArray("regions")
        val totalSamples = (0 until regions.length()).sumOf { index -> regions.getJSONObject(index).getInt("samples") }

        assertTrue(otherCell.getBoolean("memory_sample_accepted"))
        assertEquals(1, regions.length())
        assertEquals(2, totalSamples)
        assertEquals(2L, exported.getLong("independentSamples"))
        assertEquals(0L, exported.getLong("correlatedSamplesWeighted"))
    }

    @Test
    fun `new usb connection resets only live counters and preserves evidence`() {
        val store = store()
        store.startSession()
        store.ingest(telemetry(650L), accepted(sample("a", 100L, 650L)))
        store.endSession("USB_DISCONNECTED")
        store.startSession()
        val acceptedAgain = store.ingest(telemetry(650L), accepted(sample("b", 100L, 650L)))
        val exported = store.export("test")
        val region = exported.getJSONArray("regions").getJSONObject(0)

        assertTrue(acceptedAgain.getBoolean("memory_sample_accepted"))
        assertEquals(2, region.getInt("samples"))
        assertEquals(2, region.getInt("session_count"))
        assertEquals(1L, exported.getLong("independentSamples"))
        assertFalse(region.has("confidence_sessions"))
    }

    @Test
    fun `native sidecar remains bounded by complete recent snapshots`() {
        val state = temporary.root.resolve("bounded-native-${System.nanoTime()}.json")
        val store = SignalLearningStore(state, RingLog())
        try {
            repeat(32) { snapshotIndex ->
                val raw = JSONArray()
                val counts = JSONArray()
                repeat(18) { band ->
                    raw.put(100 + snapshotIndex + band)
                    counts.put(1 + band)
                }
                val snapshot = JSONObject()
                    .put("snapshotId", "snapshot-$snapshotIndex")
                    .put(
                        "fields",
                        JSONArray().put(
                            JSONObject()
                                .put("status", "VALID")
                                .put("rawValues", raw)
                                .put("counts", counts),
                        ),
                    )
                assertTrue(store.importNativeSnapshot(snapshot).getBoolean("ok"))
            }
            val exported = store.export("test")
            val evidence = exported.getJSONArray("nativeEcuEvidence")
            val snapshotIds = (0 until evidence.length())
                .map { evidence.getJSONObject(it).getString("snapshotId") }
                .distinct()

            assertEquals(LearningEvidenceBudget.MAX_NATIVE_SNAPSHOTS, snapshotIds.size)
            assertEquals("snapshot-16", snapshotIds.first())
            assertEquals("snapshot-31", snapshotIds.last())
            assertTrue(exported.getJSONObject("evidenceBudget").getLong("nativeSnapshotsEvicted") >= 16L)
        } finally {
            store.close()
        }
        val sidecar = state.parentFile.resolve("learning_v6_evidence.json")
        assertTrue(sidecar.length() <= LearningEvidenceBudget.MAX_PERSISTED_BYTES)
    }

    private fun store() = SignalLearningStore(
        temporary.root.resolve("signal-${System.nanoTime()}.json"),
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
        petrolMs: Double = 4.0,
        fuel: Mp48Fuel = Mp48Fuel.PETROL,
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

    private fun telemetry(at: Long, petrolMs: Double = 4.0) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
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
}

