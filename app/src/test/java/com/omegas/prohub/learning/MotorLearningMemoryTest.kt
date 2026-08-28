package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MotorLearningMemoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `janelas repetidas consolidam a visita sem inflar a evidencia`() {
        val memory = memory()
        memory.startSession()
        memory.ingest(telemetry(), accepted(sample("petrol", 100L)))
        memory.ingest(
            telemetry(at = 900L, fuel = Mp48Fuel.CNG),
            accepted(sample("cng-early", 900L, Mp48Fuel.CNG, petrolMs = 5.0, quality = 0.40)),
        )
        val status = memory.ingest(
            telemetry(at = 1_500L, fuel = Mp48Fuel.CNG),
            accepted(sample("cng-stable", 1_500L, Mp48Fuel.CNG, petrolMs = 4.0, quality = 1.0)),
        )

        val comparisons = memory.export("test").getJSONArray("comparisons")
        assertEquals(1, comparisons.length())
        val consolidated = comparisons.getJSONObject(0)
        assertEquals(2, consolidated.getInt("observation_count"))
        assertTrue(consolidated.getDouble("petrol_on_cng_ms") < 5.0)
        assertTrue(consolidated.getDouble("petrol_on_cng_ms") > 4.0)
        assertTrue(status.getString("reason").contains("consolidada"))
        assertEquals(
            status.getJSONObject("comparison").getString("direction"),
            status.getString("direction"),
        )
    }

    @Test
    fun `repeated samples and visual reads do not invent visits`() {
        val memory = memory()
        memory.startSession()
        memory.ingest(telemetry(), accepted(sample("a", 100L)))
        repeat(20) { memory.statusJson() }
        memory.ingest(telemetry(at = 300L), accepted(sample("b", 300L)))
        val region = memory.export("test").getJSONArray("regions").getJSONObject(0)
        assertEquals(2, region.getInt("samples"))
        assertEquals(1, region.getInt("visit_count"))
        assertEquals(1, region.getInt("session_count"))
    }

    @Test
    fun `three physical frames outside and return create a new visit`() {
        val memory = memory()
        memory.startSession()
        memory.ingest(telemetry(), accepted(sample("a", 100L)))
        repeat(3) { index ->
            memory.ingest(
                telemetry(at = 300L + index * 50L, rpm = 4_500),
                SampleDecision.forming(index + 1, 10, 10, SampleTiming(0L, 0L)),
            )
        }
        memory.ingest(telemetry(at = 600L), accepted(sample("b", 600L)))
        val region = memory.export("test").getJSONArray("regions").getJSONObject(0)
        assertEquals(2, region.getInt("visit_count"))
    }

    @Test
    fun `usb connections remain diagnostic and never gate confidence`() {
        val memory = memory()
        memory.startSession()
        memory.ingest(telemetry(), accepted(sample("a", 100L)))
        memory.endSession("USB_DISCONNECTED")
        memory.startSession()
        memory.ingest(telemetry(at = 500L), accepted(sample("b", 500L)))
        val exported = memory.export("test")
        val region = exported.getJSONArray("regions").getJSONObject(0)
        assertEquals(2, region.getInt("session_count"))
        assertEquals(2, exported.getJSONArray("sessions").length())
        assertFalse(region.has("confidence_sessions"))
        assertFalse(region.has("needs_second_session"))
    }

    @Test
    fun `persisted state and its single projection survive recreation`() {
        val file = temporary.newFile("learning.json")
        file.delete()
        val first = MotorLearningMemory(file, RingLog())
        first.startSession()
        first.ingest(telemetry(), accepted(sample("a", 100L)))
        first.awaitPersistence()
        val before = first.export("test")
        val restored = MotorLearningMemory(file, RingLog()).export("test")
        assertEquals(before.getJSONArray("regions").toString(), restored.getJSONArray("regions").toString())
        assertEquals(
            before.getJSONObject("integrity").getString("projectionHash"),
            restored.getJSONObject("integrity").getString("projectionHash"),
        )
        assertTrue(restored.getJSONObject("integrity").getBoolean("ok"))
    }

    @Test
    fun `corrupt primary state recovers the previous valid backup`() {
        val file = temporary.newFile("recover.json")
        file.delete()
        val first = MotorLearningMemory(file, RingLog())
        first.startSession()
        first.ingest(telemetry(), accepted(sample("a", 100L)))
        first.awaitPersistence()
        first.ingest(telemetry(at = 800L), accepted(sample("b", 800L)))
        first.awaitPersistence()
        file.writeText("{truncated")
        val recovered = MotorLearningMemory(file, RingLog()).export("test")
        assertTrue(recovered.getJSONArray("regions").length() > 0)
        assertTrue(recovered.getJSONObject("integrity").getBoolean("ok"))
    }

    @Test
    fun `intentional projection divergence is explicit`() {
        val region = sampleRegionJson()
        val regions = JSONArray().put(region)
        val expected = LearningGridProjection.project(regions, 1)
        val report = LearningGridProjection.integrity(regions, JSONArray(), JSONArray(), 1, "map")
        assertTrue(expected.length() > 0)
        assertFalse(report.getBoolean("ok"))
        assertTrue(report.getJSONArray("onlyInMemory").length() > 0)
    }

    @Test
    fun `import refuses an intentionally divergent V5 payload`() {
        val source = memory()
        source.startSession()
        source.ingest(telemetry(), accepted(sample("a", 100L)))
        val payload = source.export("source").put("cells", JSONArray())
        val result = memory().merge(payload, "target")
        assertFalse(result.getBoolean("ok"))
        assertTrue(result.getJSONObject("integrity").getJSONArray("onlyInMemory").length() > 0)
    }

    @Test
    fun `valid V5 export imports its portable petrol evidence`() {
        val source = memory()
        source.startSession()
        source.ingest(telemetry(), accepted(sample("a", 100L)))
        val result = memory().merge(source.export("source"), "target")
        assertTrue(result.getBoolean("ok"))
        assertEquals(1, result.getInt("mergedRegions"))
    }

    @Test
    fun `cutoff closes occupancy before a later healthy return`() {
        val memory = memory()
        memory.startSession()
        memory.ingest(telemetry(), accepted(sample("a", 100L)))
        memory.ingest(
            telemetry(at = 300L, fuel = Mp48Fuel.CUTOFF),
            SampleDecision.transition(state = "CUTOFF", reason = "cutoff"),
        )
        memory.ingest(telemetry(at = 1_000L), accepted(sample("b", 1_000L)))
        val region = memory.export("test").getJSONArray("regions").getJSONObject(0)
        assertEquals(2, region.getInt("visit_count"))
    }

    @Test
    fun `interrupted connection is explicit after restoration`() {
        val file = temporary.newFile("interrupted.json")
        file.delete()
        val first = MotorLearningMemory(file, RingLog())
        first.startSession()
        first.ingest(telemetry(), accepted(sample("a", 100L)))
        first.awaitPersistence()
        val restored = MotorLearningMemory(file, RingLog()).export("test")
        assertEquals(
            "PROCESS_INTERRUPTED",
            restored.getJSONArray("sessions").getJSONObject(0).getString("end_reason"),
        )
    }

    @Test
    fun `memory from another format is ignored without being rewritten`() {
        val file = temporary.newFile("future.json")
        val original = JSONObject()
            .put("format", "omegas-learning-v99")
            .put("regions", JSONArray().put(sampleRegionJson()))
            .toString(2)
        file.writeText(original)
        val exported = MotorLearningMemory(file, RingLog()).export("test")
        assertEquals(0, exported.getJSONArray("regions").length())
        assertEquals(original, file.readText())
    }

    @Test
    fun `localized map adjustment preserves cng evidence outside changed cells`() {
        val memory = memory()
        memory.startSession()
        memory.ingest(telemetry(fuel = Mp48Fuel.PETROL), accepted(sample("petrol", 100L)))
        memory.ingest(
            telemetry(at = 900L, fuel = Mp48Fuel.CNG),
            accepted(sample("cng", 900L, Mp48Fuel.CNG)),
        )
        val before = memory.export("test")
        val cng = before.getJSONArray("regions").getJSONObject(1)
        val cngCell = LearningGridProjection.cellFor(cng.getDouble("rpm"), cng.getDouble("petrol_ms"))
        val untouchedRow = if (cngCell.getInt("row") == 0) 1 else 0
        val result = memory.onCalibrationAdjustment(
            JSONObject()
                .put("newHash", "after-local-change")
                .put("cells", JSONArray().put(JSONObject()
                    .put("row", untouchedRow)
                    .put("column", cngCell.getInt("column"))))
        )
        val after = memory.export("test")
        val carriedCng = after.getJSONArray("regions").getJSONObject(1)
        assertEquals(result.getInt("epoch"), carriedCng.getInt("epoch"))
        assertEquals(1, result.getJSONObject("revalidation").getInt("preservedRegions"))
        assertEquals(0, result.getJSONObject("revalidation").getInt("revalidationRegions"))
        assertEquals(1, after.getJSONObject("summary").getInt("cng_regions"))
        assertEquals(0, after.getJSONArray("regions").getJSONObject(0).getInt("epoch"))
    }

    @Test
    fun `localized map adjustment revalidates only the cng cell that changed`() {
        val memory = memory()
        memory.startSession()
        memory.ingest(telemetry(fuel = Mp48Fuel.PETROL), accepted(sample("petrol", 100L)))
        memory.ingest(
            telemetry(at = 900L, fuel = Mp48Fuel.CNG),
            accepted(sample("cng", 900L, Mp48Fuel.CNG)),
        )
        val before = memory.export("test")
        val cng = before.getJSONArray("regions").getJSONObject(1)
        val cngCell = LearningGridProjection.cellFor(cng.getDouble("rpm"), cng.getDouble("petrol_ms"))
        val result = memory.onCalibrationAdjustment(
            JSONObject()
                .put("newHash", "after-targeted-change")
                .put("cells", JSONArray().put(JSONObject()
                    .put("row", cngCell.getInt("row"))
                    .put("column", cngCell.getInt("column"))))
        )
        val after = memory.export("test")
        val staleCng = after.getJSONArray("regions").getJSONObject(1)
        assertEquals(1, staleCng.getInt("epoch"))
        assertEquals(0, after.getJSONObject("summary").getInt("cng_regions"))
        assertEquals(0, result.getJSONObject("revalidation").getInt("preservedRegions"))
        assertEquals(1, result.getJSONObject("revalidation").getInt("revalidationRegions"))
        assertEquals(0, after.getJSONArray("regions").getJSONObject(0).getInt("epoch"))
    }

    @Test
    fun `compacted provenance preserves exact visit and session counts after restore`() {
        val file = temporary.newFile("compacted-restore.json")
        val region = sampleRegionJson()
            .put("visits", JSONArray((1..40).map { "visit-$it" }))
            .put("sessions", JSONArray((1..20).map { "session-$it" }))
            .put("visit_count", 40)
            .put("session_count", 20)
        file.writeText(
            JSONObject()
                .put("format", MotorLearningMemory.FORMAT)
                .put("epoch", 1)
                .put("mapHash", "")
                .put("regions", JSONArray().put(region))
                .put("comparisons", JSONArray())
                .put("sessions", JSONArray())
                .toString(),
        )

        val restored = MotorLearningMemory(file, RingLog()).export("test")
        val restoredRegion = restored.getJSONArray("regions").getJSONObject(0)
        assertEquals(40, restoredRegion.getInt("visit_count"))
        assertEquals(20, restoredRegion.getInt("session_count"))
        assertEquals(LearningMemoryBudget.MAX_REGION_VISIT_IDS, restoredRegion.getJSONArray("visits").length())
        assertEquals(LearningMemoryBudget.MAX_REGION_SESSION_IDS, restoredRegion.getJSONArray("sessions").length())
        assertTrue(restoredRegion.getBoolean("visit_ids_compacted"))
        assertTrue(restoredRegion.getBoolean("session_ids_compacted"))
    }

    @Test
    fun `persisted learning keeps primary science and omits derived cell projection`() {
        val file = temporary.newFile("primary-science.json")
        file.delete()
        val memory = MotorLearningMemory(file, RingLog())
        memory.startSession()
        memory.ingest(telemetry(), accepted(sample("a", 100L)))
        memory.awaitPersistence()

        val persisted = JSONObject(file.readText())
        val region = persisted.getJSONArray("regions").getJSONObject(0)
        assertFalse(region.has("cell"))
        assertTrue(persisted.has("memoryBudget"))
        assertEquals(
            LearningMemoryBudget.POLICY,
            persisted.getJSONObject("memoryBudget").getString("policy"),
        )
    }

    @Test
    fun `advisor snapshot excludes ui projections and session history`() {
        val memory = memory()
        memory.startSession()
        memory.ingest(telemetry(), accepted(sample("a", 100L)))

        val advisor = memory.advisorSnapshot()
        assertTrue(advisor.has("regions"))
        assertTrue(advisor.has("comparisons"))
        assertFalse(advisor.has("cells"))
        assertFalse(advisor.has("grid"))
        assertFalse(advisor.has("integrity"))
        assertFalse(advisor.has("summary"))
        assertFalse(advisor.has("sessions"))
    }

    @Test
    fun `persisted size no longer scales with full legacy provenance arrays`() {
        fun persistLegacy(visitTotal: Int): Long {
            val file = temporary.newFile("legacy-$visitTotal.json")
            val region = sampleRegionJson()
                .put("visits", JSONArray((1..visitTotal).map { "visit-$it" }))
                .put("sessions", JSONArray((1..20).map { "session-$it" }))
                .put("visit_count", visitTotal)
                .put("session_count", 20)
            file.writeText(
                JSONObject()
                    .put("format", MotorLearningMemory.FORMAT)
                    .put("epoch", 1)
                    .put("mapHash", "")
                    .put("regions", JSONArray().put(region))
                    .put("comparisons", JSONArray())
                    .put("sessions", JSONArray())
                    .toString(),
            )
            val memory = MotorLearningMemory(file, RingLog())
            memory.startSession()
            memory.ingest(telemetry(), accepted(sample("refresh-$visitTotal", 1_000L)))
            memory.awaitPersistence()
            return file.length()
        }

        val twentyVisits = persistLegacy(20)
        val hundredVisits = persistLegacy(100)
        assertTrue(kotlin.math.abs(hundredVisits - twentyVisits) < 1_024L)
    }

    private fun memory() = MotorLearningMemory(
        temporary.root.resolve("learning-${System.nanoTime()}.json"),
        RingLog(),
    )

    private fun accepted(sample: MotorSample) = SampleDecision.accepted(sample)

    private fun sample(
        id: String,
        at: Long,
        fuel: Mp48Fuel = Mp48Fuel.PETROL,
        petrolMs: Double = 4.0,
        quality: Double = 0.95,
    ) = MotorSample(
        id = id,
        startedAtElapsedMs = at,
        endedAtElapsedMs = at + 550L,
        fuel = fuel,
        rpm = 2_500.0,
        mapBar = 0.60,
        petrolMs = petrolMs,
        pressureDiffBar = 1.4,
        waterC = 80.0,
        gasC = if (fuel == Mp48Fuel.CNG) 65.0 else 0.0,
        quality = quality,
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

    private fun telemetry(
        at: Long = 100L,
        rpm: Int = 2_500,
        fuel: Mp48Fuel = Mp48Fuel.PETROL,
    ) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = rpm,
        levelRaw = 100,
        gasRaw = 0,
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

    private fun sampleRegionJson() = JSONObject()
        .put("id", "r1")
        .put("fuel", "GASOLINA")
        .put("epoch", 0)
        .put("rpm", 2_500.0)
        .put("map_bar", 0.60)
        .put("petrol_ms", 4.0)
        .put("samples", 2)
        .put("visit_count", 1)
        .put("session_count", 1)
        .put("confidence", 0.4)
        .put("stage", "OBSERVED")
        .put("updated_at", 1L)
}
