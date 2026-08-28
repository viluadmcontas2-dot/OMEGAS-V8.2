package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import com.omegas.prohub.ecu.Mp48Telemetry
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FuelEquivalenceMemoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `longer original injection on cng requests more cng delivery`() {
        val comparison = comparisonFor(petrolTarget = 4.0, petrolOnCng = 4.30)
        assertEquals("INCREASE_CNG_DELIVERY", comparison.getString("direction"))
        assertEquals(0.30, comparison.getDouble("difference_ms"), 0.0001)
        assertEquals(0.075, comparison.getDouble("error_ratio"), 0.0001)
        assertEquals(7.5, comparison.getDouble("error_pct"), 0.0001)
        assertTrue(comparison.getBoolean("equivalence_valid"))
    }

    @Test
    fun `shorter original injection on cng requests less cng delivery`() {
        val comparison = comparisonFor(petrolTarget = 4.0, petrolOnCng = 3.70)
        assertEquals("DECREASE_CNG_DELIVERY", comparison.getString("direction"))
        assertEquals(-0.30, comparison.getDouble("difference_ms"), 0.0001)
        assertEquals(-0.075, comparison.getDouble("error_ratio"), 0.0001)
        assertEquals(-7.5, comparison.getDouble("error_pct"), 0.0001)
    }

    @Test
    fun `deadband requires both absolute and percentage conditions`() {
        val comparison = comparisonFor(petrolTarget = 1.0, petrolOnCng = 1.08)
        assertTrue(kotlin.math.abs(comparison.getDouble("difference_ms")) <= LearningToleranceSettings.current.equivalenceDeadbandMs)
        assertTrue(kotlin.math.abs(comparison.getDouble("error_pct")) > LearningToleranceSettings.current.equivalenceDeadbandPercent)
        assertEquals("INCREASE_CNG_DELIVERY", comparison.getString("direction"))
    }

    @Test
    fun `difference inside both deadbands remains equivalent`() {
        val comparison = comparisonFor(petrolTarget = 4.0, petrolOnCng = 4.05)
        assertEquals("EQUIVALENT", comparison.getString("direction"))
        assertTrue(kotlin.math.abs(comparison.getDouble("difference_ms")) <= LearningToleranceSettings.current.equivalenceDeadbandMs)
        assertTrue(kotlin.math.abs(comparison.getDouble("error_pct")) <= LearningToleranceSettings.current.equivalenceDeadbandPercent)
    }

    @Test
    fun `comparison preserves denominator units ids timestamps and context`() {
        val comparison = comparisonFor(petrolTarget = 5.0, petrolOnCng = 5.50)
        assertEquals(5.0, comparison.getDouble("petrol_target_ms"), 0.0001)
        assertEquals(5.0, comparison.getDouble("reference_denominator_ms"), 0.0001)
        assertEquals(5.5, comparison.getDouble("petrol_on_cng_ms"), 0.0001)
        assertEquals("ms", comparison.getString("reference_unit"))
        assertEquals("ms", comparison.getString("observed_unit"))
        assertEquals("ms", comparison.getString("difference_unit"))
        assertEquals("ratio", comparison.getString("error_ratio_unit"))
        assertEquals("percent", comparison.getString("error_percent_unit"))
        assertEquals("CONTINUOUS_REFERENCE_SURFACE", comparison.getString("origin"))
        assertEquals(1, comparison.getJSONArray("reference_region_ids").length())
        assertTrue(comparison.getLong("reference_updated_at_wall_ms") > 0L)
        assertTrue(comparison.getJSONArray("reference_contexts").getJSONObject(0).getLong("updated_at_ms") > 0L)
        assertTrue(comparison.getJSONObject("request_environment").length() > 0)
        assertEquals(800L, comparison.getLong("cng_sample_started_at_elapsed_ms"))
        assertEquals(1_350L, comparison.getLong("cng_sample_ended_at_elapsed_ms"))
        assertEquals("wall_clock_ms", comparison.getJSONObject("timestamp_domains").getString("reference_updated_at_wall_ms"))
        assertEquals("monotonic_elapsed_ms", comparison.getJSONObject("timestamp_domains").getString("cng_sample_ended_at_elapsed_ms"))
    }

    private fun comparisonFor(petrolTarget: Double, petrolOnCng: Double): JSONObject {
        val store = SignalLearningStore(
            temporary.root.resolve("equivalence-${System.nanoTime()}.json"),
            RingLog(),
        )
        store.startSession()
        store.ingest(
            telemetry(at = 650L, fuel = Mp48Fuel.PETROL, petrolMs = petrolTarget),
            accepted(sample("petrol", 100L, 650L, Mp48Fuel.PETROL, petrolTarget)),
        )
        store.ingest(
            telemetry(at = 1_350L, fuel = Mp48Fuel.CNG, petrolMs = petrolOnCng),
            accepted(sample("cng", 800L, 1_350L, Mp48Fuel.CNG, petrolOnCng)),
        )
        val comparisons = store.export("test").getJSONArray("comparisons")
        assertEquals(1, comparisons.length())
        return comparisons.getJSONObject(0)
    }

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
        gasC = if (fuel == Mp48Fuel.CNG) 65.0 else 25.0,
        quality = 0.95,
        classification = SampleClassification.STRONG,
        frameCount = LearningTolerancePolicy().requiredFrames,
        diagnostics = diagnostics(),
    )

    private fun telemetry(at: Long, fuel: Mp48Fuel, petrolMs: Double) = Mp48Telemetry(
        capturedAtElapsedMs = at,
        rpm = 2_500,
        levelRaw = 100,
        gasRaw = if (fuel == Mp48Fuel.CNG) 200 else 0,
        gasMsDiagnostic = null,
        petrolRaw = 100,
        petrolCounts = 100,
        petrolMs = petrolMs,
        dynamicCorrection = 0,
        fuelByte = 0,
        fuel = fuel,
        state = fuel.wireName,
        waterRaw = 80,
        waterC = 80,
        gasC = if (fuel == Mp48Fuel.CNG) 65 else 25,
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
