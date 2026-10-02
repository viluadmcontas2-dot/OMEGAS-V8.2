package com.omegas.prohub

import com.omegas.prohub.ecu.*
import com.omegas.prohub.learning.*
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.abs

/** WorkUnit #46: actual frames -> disk restart -> independent physical visits -> advisor -> UI. */
class LearningLatencyContractTest {
    @get:Rule val temporary = TemporaryFolder()
    private val policy = LearningTolerancePolicy()

    private fun fast(decision: SampleDecision) = AdaptiveSampleWindow.acceptanceStage(
        decision.sample!!, decision.desiredFrames, decision.toleratedGapCount, false,
        policy.strongPetrolOscillationPercent / 100.0).name

    @Test fun matrix() {
        val cases = listOf(
            Scenario("A", .08), Scenario("B", -.08), Scenario("C", .03),
            Scenario("D", .01), Scenario("E", .08, noise = true),
            Scenario("F", .08, spatial = true),
        )
        for (case in cases) {
            val result = run(case)
            println("LATENCY_MATRIX=" + result.toString())
            val actionable = result.getInt("firstActionable")
            if (case.name in listOf("A", "B", "E", "F")) assertTrue(result.toString(), actionable > 0)
            if (case.name == "D") assertEquals(0, result.getInt("actionableDecisions"))
            if (case.name != "D") {
                val expected = if (case.ratio > 0) "INCREASE_CNG_DELIVERY" else "DECREASE_CNG_DELIVERY"
                assertEquals(expected, result.getJSONObject("lastPoint").getString("direction"))
            }
        }
    }

    @Test fun auxiliaryInvarianceAcrossCollectionReferenceAndAdvisor() {
        val baseline = run(Scenario("invariant-base", .08))
        val varied = run(Scenario("invariant-varied", .08, auxiliary = true))
        for (key in listOf("firstAny", "firstProvisional", "firstAccepted", "firstConfirmed",
            "firstActionable", "comparisons", "visits", "petrolFrames", "firstCngFrames")) {
            assertEquals(key, baseline.getInt(key), varied.getInt(key))
        }
        val expected = baseline.getJSONObject("lastPoint")
        val actual = varied.getJSONObject("lastPoint")
        for (key in listOf("errorPercent", "confidence", "uncertaintyPercent", "usefulMarginPercent")) {
            assertEquals(key, expected.getDouble(key), actual.getDouble(key), 1e-9)
        }
        assertEquals(baseline.getDouble("referenceMs"), varied.getDouble("referenceMs"), 1e-9)
        assertEquals("Auxiliary sensors must not gate the memory proposal",
            baseline.getBoolean("memoryActionable"), varied.getBoolean("memoryActionable"))
        println("AUXILIARY_INVARIANCE=PASS water=10..110 gas=-20..100 pressure=0.4..2.4");
    }

    @Test fun referenceNeighborhoodAndNoCoverage() {
        val regions = listOf(
            PetrolReferenceSelector.Region("left", 2380.0, .51, 80.0, 4.9, .9, 10),
            PetrolReferenceSelector.Region("right", 2420.0, .53, 80.0, 5.1, .9, 10),
        )
        for ((rpm, map) in listOf(2380.0 to .51, 2400.0 to .51, 2380.0 to .52, 2400.0 to .52)) {
            val ref = PetrolReferenceSelector.estimate(regions, PetrolReferenceSelector.Request(rpm, map, 20.0), policy)
            assertTrue(ref.toJson().toString(), ref.available)
            assertTrue(ref.petrolTargetMs!! in 4.9..5.1)
            val warm = PetrolReferenceSelector.estimate(regions, PetrolReferenceSelector.Request(rpm, map, 110.0), policy)
            assertEquals(ref.petrolTargetMs!!, warm.petrolTargetMs!!, 1e-12)
            assertEquals(ref.quality, warm.quality, 1e-12)
        }
        var previous: Double? = null
        for (rpm in 2380..2420) {
            val ref = PetrolReferenceSelector.estimate(regions, PetrolReferenceSelector.Request(rpm.toDouble(), .52, 80.0), policy)
            assertTrue(ref.available)
            previous?.let { assertTrue(abs(ref.petrolTargetMs!! - it) < .03) }
            previous = ref.petrolTargetMs
        }
        val missing = PetrolReferenceSelector.estimate(regions, PetrolReferenceSelector.Request(4500.0, .9, 80.0), policy)
        assertFalse(missing.available)
        assertEquals("NO_LOCAL_PETROL_REFERENCE", missing.reasonCode)
        assertNull(missing.petrolTargetMs)
    }

    private data class Scenario(val name: String, val ratio: Double,
        val noise: Boolean = false, val spatial: Boolean = false, val auxiliary: Boolean = false)

    private fun run(case: Scenario): JSONObject {
        val file = temporary.root.resolve(case.name + ".json")
        var memory = MotorLearningMemory(file, RingLog())
        val analyzer = MotorSampleAnalyzer { policy }
        var tick = 0L
        memory.startSession()
        fun collect(fuel: Mp48Fuel, visit: Int): Pair<Int, SampleDecision> {
            for (index in 0 until 100) {
                val offset = if (case.spatial && fuel == Mp48Fuel.CNG) (visit % 3 - 1) else 0
                val jitter = if (case.noise && fuel == Mp48Fuel.CNG)
                    listOf(-.008, .006, -.002, .004, -.005, .008)[index % 6] else 0.0
                var frame = telemetry(tick, fuel, 2400 + offset * 20, .52 + offset * .008,
                    5.0 * if (fuel == Mp48Fuel.PETROL) 1.0 else (1.0 + case.ratio + jitter))
                if (case.auxiliary) frame = frame.copy(
                    waterC = if ((index / 100 + visit) % 2 == 0) 10 else 110,
                    gasC = if ((index / 100 + visit) % 2 == 0) -20 else 100,
                    pressureDiffBar = if ((index / 100 + visit) % 2 == 0) .4 else 2.4,
                    gasPressureAbsBar = if ((index / 100 + visit) % 2 == 0) .9 else 2.9)
                tick += 50
                val decision = analyzer.add(frame)
                memory.ingest(frame, decision)
                if (decision.learningEligible) return (index + 1) to decision
            }
            error("Collection never accepted ${case.name} fuel=$fuel visit=$visit")
        }
        try {
            val petrol = collect(Mp48Fuel.PETROL, 0)
            val before = memory.export("contract")
            memory.awaitPersistence()
            assertTrue(file.isFile && file.length() > 0)
            val persisted = JSONObject(file.readText())
            assertEquals(MotorLearningMemory.FORMAT, persisted.getString("format"))
            assertTrue(persisted.getString("stateDigest").isNotBlank())
            assertEquals(Mp48Fuel.PETROL.wireName, persisted.getJSONArray("regions").getJSONObject(0).getString("fuel"))
            memory.close()
            memory = MotorLearningMemory(file, RingLog())
            val after = memory.export("contract")
            assertEquals(before.getJSONArray("regions").toString(), after.getJSONArray("regions").toString())
            assertEquals(before.getJSONObject("integrity").getString("projectionHash"), after.getJSONObject("integrity").getString("projectionHash"))
            assertTrue(after.getJSONObject("integrity").getBoolean("ok"))
            assertEquals(0, after.getJSONArray("regions").getJSONObject(0).getInt("epoch"))
            memory.startSession()
            var firstAny = -1; var firstProvisional = -1; var firstAccepted = -1
            var firstConfirmed = -1; var firstActionable = -1; var actionableDecisions = 0
            var firstCngFrames = -1
            var point = JSONObject()
            val trace = JSONArray()
            var referenceMs = 0.0
            repeat(20) { zeroVisit ->
                val visit = zeroVisit + 1
                if (visit > 1) {
                    repeat(policy.physicalExitFrames) {
                        val outside = telemetry(tick, Mp48Fuel.CNG, 4000, .85, 5.4)
                        memory.ingest(outside, SampleDecision.forming(0, 6, 10, SampleTiming(0, 50)))
                        tick += 50
                    }
                }
                analyzer.reset()
                val (frames, decision) = collect(Mp48Fuel.CNG, visit)
                if (visit == 1) firstCngFrames = frames
                val raw = memory.export("contract")
                val comparisons = raw.getJSONArray("comparisons")
                assertEquals("Physical exits must generate independent comparisons", visit, comparisons.length())
                val comparison = comparisons.getJSONObject(comparisons.length() - 1)
                referenceMs = comparison.getDouble("petrol_target_ms")
                assertEquals(5.0, referenceMs, 1e-9)
                val difference = comparison.getDouble("petrol_on_cng_ms") - referenceMs
                assertEquals(difference, comparison.getDouble("difference_ms"), 1e-9)
                val advice = AssistedCalibrationAdvisor.analyze(memory.advisorSnapshot())
                val points = advice.getJSONArray("kFactorSuggestions")
                point = (0 until points.length()).map { points.getJSONObject(it) }
                    .filter { !it.isNull("errorPercent") }.minByOrNull { abs(it.getDouble("petrolMs") - 5.0) }
                    ?: error("No estimate")
                if (firstAny < 0) firstAny = visit
                when (point.getString("confidenceStage")) {
                    "PROVISIONAL" -> if (firstProvisional < 0) firstProvisional = visit
                    "ACCEPTED" -> if (firstAccepted < 0) firstAccepted = visit
                    "CONFIRMED" -> if (firstConfirmed < 0) firstConfirmed = visit
                }
                if (point.getBoolean("actionable")) {
                    if (firstActionable < 0) firstActionable = visit
                    actionableDecisions++
                    assertEquals(if (case.ratio > 0) "INCREASE_CNG_DELIVERY" else "DECREASE_CNG_DELIVERY", point.getString("direction"))
                    assertTrue(abs(point.getDouble("suggestedDeltaPercent")) <= abs(point.getDouble("errorPercent")))
                }
                assertFalse(advice.getBoolean("automatic"))
                assertTrue(advice.getBoolean("humanConfirmationRequired"))
                trace.put(JSONObject().put("visit", visit).put("frames", frames)
                    .put("minimumFrames", decision.minimumFrames).put("desiredFrames", decision.desiredFrames)
                    .put("quality", decision.sample!!.quality).put("reasonCode", decision.reasonCode)
                    .put("acceptanceStage", fast(decision)).put("point", point))
            }
            val memoryActionable = memory.statusJson().getBoolean("actionable")
            val ui = LearningUiSnapshotAssembler.assemble(memory.export("contract"))
            assertEquals(ui.getJSONObject("assistedCalibration").toString(), ui.getJSONObject("assisted_calibration").toString())
            assertFalse(ui.getJSONObject("assistedCalibration").getBoolean("automatic"))
            if (case.name == "A") println("UI_PAYLOAD=" + ui.toString())
            // Epoch transition: retained old CNG must not become evidence in the new epoch.
            val old = memory.export("contract")
            val oldEpoch = old.getInt("epoch")
            memory.onCalibrationAdjustment(JSONObject().put("newHash", "changed-k"))
            val next = memory.export("contract")
            assertEquals(oldEpoch + 1, next.getInt("epoch"))
            assertTrue((0 until next.getJSONArray("regions").length()).any {
                val region = next.getJSONArray("regions").getJSONObject(it)
                region.getString("fuel") == Mp48Fuel.CNG.wireName && region.getInt("epoch") == oldEpoch
            })
            assertEquals(0, AssistedCalibrationAdvisor.analyze(memory.advisorSnapshot()).getInt("comparisonCount"))
            analyzer.reset()
            collect(Mp48Fuel.CNG, 21)
            assertEquals(1, AssistedCalibrationAdvisor.analyze(memory.advisorSnapshot()).getInt("comparisonCount"))
            return JSONObject().put("scenario", case.name).put("firstAny", firstAny)
                .put("firstProvisional", firstProvisional).put("firstAccepted", firstAccepted)
                .put("firstConfirmed", firstConfirmed).put("firstActionable", firstActionable)
                .put("actionableDecisions", actionableDecisions).put("visits", 20).put("comparisons", 20)
                .put("petrolFrames", petrol.first).put("petrolDecision", petrol.second.toJson())
                .put("petrolAcceptanceStage", fast(petrol.second))
                .put("firstCngFrames", firstCngFrames).put("referenceMs", referenceMs)
                .put("deadbandPercent", policy.equivalenceDeadbandPercent)
                .put("lastPoint", point).put("trace", trace).put("memoryActionable", memoryActionable)
        } finally { memory.close() }
    }

    private fun telemetry(at: Long, fuel: Mp48Fuel, rpm: Int, map: Double, petrol: Double) = Mp48Telemetry(
        capturedAtElapsedMs = at, rpm = rpm, levelRaw = 100,
        gasRaw = if (fuel == Mp48Fuel.CNG) 200 else 0, gasMsDiagnostic = null,
        petrolRaw = 100, petrolCounts = 100, petrolMs = petrol, dynamicCorrection = 0,
        fuelByte = if (fuel == Mp48Fuel.CNG) 0x90 else 0x80, fuel = fuel, state = fuel.wireName,
        waterRaw = 80, waterC = 80, gasC = 30, gasPressureRaw = 100, gasPressureAbsBar = 2.0,
        mapRaw = 100, mapBar = map, pressureDiffBar = 1.4, plausible = true)
}
