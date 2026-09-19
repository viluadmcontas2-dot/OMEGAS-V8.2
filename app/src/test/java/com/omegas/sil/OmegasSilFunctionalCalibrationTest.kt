package com.omegas.sil

import com.omegas.prohub.calibration.AdvisorSuggestionAdapterV7
import com.omegas.prohub.ecu.ResponseDrivenEcuEngine
import com.omegas.prohub.learning.SignalLearningStore
import com.omegas.prohub.util.RingLog
import com.omegas.v7.runtime.CalibrationRevisionV7
import com.omegas.v7.runtime.CalibrationShapeV7
import com.omegas.v7.runtime.CalibrationStateV7
import com.omegas.v7.runtime.SuggestionLifecycleV7
import com.omegas.v7.runtime.SuggestionTargetV7
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Functional SIL gate:
 *
 * recorded MP48 -> real ECU engine -> real SignalLearningStore -> real Advisor
 * -> real V7 suggestion adapter.
 *
 * This test never writes to the ECU. It proves that the learning chain can turn
 * recorded physical evidence into a concrete manual calibration proposal.
 */
class OmegasSilFunctionalCalibrationTest {
    @Test
    fun replayRecordedMp48IntoConcreteManualCalibration() {
        val inputPath = (
            System.getProperty("omegas.sil.input")
                ?: System.getenv("OMEGAS_SIL_INPUT")
            )?.trim().orEmpty()
        assumeTrue("omegas.sil.input not configured", inputPath.isNotEmpty())

        val input = File(inputPath)
        assertTrue("SIL input does not exist: $inputPath", input.isFile)

        val output = File(
            (
                System.getProperty("omegas.sil.output")
                    ?: System.getenv("OMEGAS_SIL_OUTPUT")
                )?.trim().orEmpty()
                .ifEmpty { File(input.parentFile, "sil-functional-output").absolutePath },
        ).apply { mkdirs() }

        val frames = readFrames(input)
        assertTrue("SIL corpus is empty", frames.isNotEmpty())

        val clock = SilRuntimeClock()
        val transport = RecordedMp48Transport(frames, clock)
        val store = SignalLearningStore(File(output, "learning-state.json"), RingLog())
        store.startSession()

        val latch = CountDownLatch(frames.size)
        var callbackIndex = 0
        var strictAnchors = 0
        var firstAnchorSequence: Long? = null
        var secondAnchorSequence: Long? = null
        var firstConcreteSequence: Long? = null
        var firstConcreteRecordedAtMs: Long? = null

        try {
            val neutralCalibration = CalibrationStateV7(
                revision = CalibrationRevisionV7(0, 0),
                curveK = List(CalibrationShapeV7.CURVE_K_POINTS) { 1.0 },
                mapK = List(CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
                    List(CalibrationShapeV7.MAP_K_COLUMNS) { 110 }
                },
            )
            val adapter = AdvisorSuggestionAdapterV7()

            val engine = ResponseDrivenEcuEngine(
                usb = transport,
                log = RingLog(),
                onTelemetry = { telemetry, decision, _ ->
                    val source = frames[callbackIndex]
                    callbackIndex += 1
                    val learning = store.ingest(telemetry, decision)

                    if (learning.optBoolean("strict_switch_anchor_registered", false)) {
                        strictAnchors += 1
                        if (firstAnchorSequence == null) firstAnchorSequence = source.sequence
                        if (strictAnchors == 2 && secondAnchorSequence == null) {
                            secondAnchorSequence = source.sequence
                        }
                    }

                    if (strictAnchors >= 2 && firstConcreteSequence == null) {
                        val exported = store.export("omegas-sil-functional")
                        val advice = exported.optJSONObject("assistedCalibration")
                        val fresh = exported.optBoolean("advisorFresh", false)
                        if (
                            fresh &&
                            advice != null &&
                            advice.optInt("strictSwitchAnchorCount", 0) >= 2 &&
                            advice.optJSONObject("method")
                                ?.optString("globalSource") == "STRICT_SWITCH_ANCHORS"
                        ) {
                            val concrete = adapter.adapt(
                                advice = advice,
                                calibration = neutralCalibration,
                                nowMs = source.recordedAtMs,
                            )
                            if (concrete.any {
                                    it.lifecycle == SuggestionLifecycleV7.PENDING &&
                                        (
                                            it.target == SuggestionTargetV7.CURVE_K ||
                                                it.target == SuggestionTargetV7.MAP_K
                                            )
                                }
                            ) {
                                firstConcreteSequence = source.sequence
                                firstConcreteRecordedAtMs = source.recordedAtMs
                            }
                        }
                    }

                    latch.countDown()
                },
                clock = clock,
            )

            engine.beginUsbSession(1L)
            assertTrue(engine.start())
            assertTrue(
                "SIL did not consume configured corpus",
                latch.await(90, TimeUnit.SECONDS),
            )
            engine.stop(graceful = false)
            engine.close()

            var exported = store.export("omegas-sil-functional")
            val deadline = System.currentTimeMillis() + 5_000L
            while (
                System.currentTimeMillis() < deadline &&
                !exported.optBoolean("advisorFresh", false)
            ) {
                Thread.sleep(10L)
                exported = store.export("omegas-sil-functional")
            }

            val advice = exported.getJSONObject("assistedCalibration")
            val suggestions = adapter.adapt(
                advice = advice,
                calibration = neutralCalibration,
                nowMs = frames.last().recordedAtMs,
            )
            val curveSuggestions = suggestions.filter {
                it.target == SuggestionTargetV7.CURVE_K &&
                    it.lifecycle == SuggestionLifecycleV7.PENDING
            }
            val mapSuggestions = suggestions.filter {
                it.target == SuggestionTargetV7.MAP_K &&
                    it.lifecycle == SuggestionLifecycleV7.PENDING
            }
            val curveChanges = curveSuggestions.sumOf { it.curveChanges.size }
            val mapChanges = mapSuggestions.sumOf { it.mapChanges.size }
            val globalSource = advice.optJSONObject("method")
                ?.optString("globalSource", "")
                .orEmpty()

            val summary = JSONObject()
                .put("input", input.absolutePath)
                .put("frames_input", frames.size)
                .put("frames_callbacks", callbackIndex)
                .put("frames_consumed", transport.consumedFrames)
                .put("strict_switch_anchors", strictAnchors)
                .put("first_anchor_sequence", firstAnchorSequence ?: JSONObject.NULL)
                .put("second_anchor_sequence", secondAnchorSequence ?: JSONObject.NULL)
                .put("first_concrete_sequence", firstConcreteSequence ?: JSONObject.NULL)
                .put("first_concrete_recorded_at_ms", firstConcreteRecordedAtMs ?: JSONObject.NULL)
                .put("advisor_fresh", exported.optBoolean("advisorFresh", false))
                .put("advisor_revision", exported.optLong("advisorRevision", 0L))
                .put("advisor_published_revision", exported.optLong("advisorPublishedRevision", 0L))
                .put("strict_anchor_count_advisor", advice.optInt("strictSwitchAnchorCount", 0))
                .put("global_source", globalSource)
                .put("curve_suggestions", curveSuggestions.size)
                .put("curve_changes", curveChanges)
                .put("map_suggestions", mapSuggestions.size)
                .put("map_changes", mapChanges)
                .put("automatic", false)
                .put("human_confirmation_required", true)

            File(output, "functional-calibration.json")
                .writeText(summary.toString(2), Charsets.UTF_8)
            File(output, "assisted-calibration.json")
                .writeText(advice.toString(2), Charsets.UTF_8)
            File(output, "learning.json")
                .writeText(exported.toString(2), Charsets.UTF_8)

            assertEquals(frames.size, callbackIndex)
            assertEquals(frames.size, transport.consumedFrames)
            assertTrue(
                "Recorded replay produced fewer than two strict PETROL->CNG anchors: $strictAnchors",
                strictAnchors >= 2,
            )
            assertTrue("Advisor did not become fresh", exported.optBoolean("advisorFresh", false))
            assertEquals("STRICT_SWITCH_ANCHORS", globalSource)
            assertTrue(
                "No concrete manual Curve-K/Map-K change was produced",
                curveChanges > 0 || mapChanges > 0,
            )
            assertTrue(
                "No concrete calibration was observed after the second strict anchor",
                firstConcreteSequence != null,
            )

            println("OMEGAS_FUNCTIONAL_CALIBRATION=" + summary.toString())
        } finally {
            runCatching { store.endSession("SIL_COMPLETE") }
            store.close()
        }
    }

    private fun readFrames(input: File): List<RecordedMp48Frame> = input.useLines { lines ->
        lines.filter { it.isNotBlank() }.map { raw ->
            val row = JSONObject(raw)
            RecordedMp48Frame(
                sequence = row.getLong("sequence"),
                recordedAtMs = row.getLong("recorded_at_ms"),
                payload = hex(row.getString("payload_hex")),
            )
        }.toList()
    }

    private fun hex(value: String): ByteArray {
        val clean = value.filterNot(Char::isWhitespace)
        require(clean.length % 2 == 0) { "Odd hex length" }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
