package com.omegas.sil

import com.omegas.prohub.ecu.ResponseDrivenEcuEngine
import com.omegas.prohub.learning.MotorLearningMemory
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OmegasSilCorpusReplayTest {
    @Test
    fun replayConfiguredCorpusThroughRealVerdeScience() {
        val inputPath = (System.getProperty("omegas.sil.input") ?: System.getenv("OMEGAS_SIL_INPUT"))?.trim().orEmpty()
        assumeTrue("omegas.sil.input not configured", inputPath.isNotEmpty())
        val input = File(inputPath)
        assertTrue("SIL input does not exist: $inputPath", input.isFile)

        val output = File(
            (System.getProperty("omegas.sil.output") ?: System.getenv("OMEGAS_SIL_OUTPUT"))?.trim().orEmpty()
                .ifEmpty { File(input.parentFile, "sil-output").absolutePath },
        ).apply { mkdirs() }
        val frames = readFrames(input)
        assertTrue("SIL corpus is empty", frames.isNotEmpty())

        val traceFile = File(output, "frames.ndjson")
        val stateFile = File(output, "learning-state.json")
        val learningFile = File(output, "learning.json")
        val summaryFile = File(output, "summary.json")
        traceFile.delete()
        stateFile.delete()

        val clock = SilRuntimeClock()
        val transport = RecordedMp48Transport(frames, clock)
        val memory = MotorLearningMemory(stateFile, RingLog())
        memory.startSession()
        val latch = CountDownLatch(frames.size)
        val decisionCounts = linkedMapOf<String, Int>()
        val learningCounts = linkedMapOf<String, Int>()
        var callbackIndex = 0
        var acceptedSamples = 0
        var registeredLearning = 0

        traceFile.bufferedWriter(Charsets.UTF_8).use { trace ->
            val engine = ResponseDrivenEcuEngine(
                usb = transport,
                log = RingLog(),
                onTelemetry = { telemetry, decision, _ ->
                    val source = frames[callbackIndex]
                    callbackIndex += 1
                    val learning = memory.ingest(telemetry, decision)
                    decisionCounts[decision.state] = (decisionCounts[decision.state] ?: 0) + 1
                    val learningState = learning.optString("state", "OBSERVING_ENGINE")
                    learningCounts[learningState] = (learningCounts[learningState] ?: 0) + 1
                    if (decision.learningEligible) acceptedSamples += 1
                    if (learning.optBoolean("registered_now", false)) registeredLearning += 1

                    val row = JSONObject()
                        .put("sequence", source.sequence)
                        .put("recorded_at_ms", source.recordedAtMs)
                        .put("rpm", telemetry.rpm)
                        .put("map_raw", telemetry.mapRaw)
                        .put("map_bar", telemetry.mapBar)
                        .put("petrol_raw", telemetry.petrolRaw)
                        .put("petrol_ms", telemetry.petrolMs)
                        .put("gas_raw", telemetry.gasRaw)
                        .put("fuel", telemetry.fuel.wireName)
                        .put("plausible", telemetry.plausible)
                        .put("decision_state", decision.state)
                        .put("reason_code", decision.reasonCode)
                        .put("frame_count", decision.frameCount)
                        .put("learning_eligible", decision.learningEligible)
                        .put("cell_key", decision.cellKey)
                        .put("quality", decision.sample?.quality ?: 0.0)
                        .put("learning_state", learningState)
                        .put("registered_now", learning.optBoolean("registered_now", false))
                        .put("reference_confidence", learning.optDouble("reference_confidence", 0.0))
                    trace.write(row.toString())
                    trace.newLine()
                    latch.countDown()
                },
                clock = clock,
            )
            engine.beginUsbSession(1L)
            assertTrue(engine.start())
            assertTrue("SIL did not consume configured corpus", latch.await(90, TimeUnit.SECONDS))
            engine.stop(graceful = false)
            engine.close()
        }

        memory.awaitPersistence()
        val learning = memory.export("omegas-sil")
        learningFile.writeText(learning.toString(2), Charsets.UTF_8)
        val traceHash = sha256(traceFile)
        val regions = learning.getJSONArray("regions").length()
        val comparisons = learning.getJSONArray("comparisons").length()
        val summary = JSONObject()
            .put("input", input.absolutePath)
            .put("frames_input", frames.size)
            .put("frames_callbacks", callbackIndex)
            .put("frames_consumed", transport.consumedFrames)
            .put("first_recorded_at_ms", frames.first().recordedAtMs)
            .put("last_recorded_at_ms", frames.last().recordedAtMs)
            .put("virtual_span_ms", frames.last().recordedAtMs - frames.first().recordedAtMs)
            .put("accepted_samples", acceptedSamples)
            .put("registered_learning", registeredLearning)
            .put("regions", regions)
            .put("comparisons", comparisons)
            .put("decision_counts", JSONObject(decisionCounts as Map<*, *>))
            .put("learning_state_counts", JSONObject(learningCounts as Map<*, *>))
            .put("trace_sha256", traceHash)
        summaryFile.writeText(summary.toString(2), Charsets.UTF_8)
        memory.close()

        assertEquals(frames.size, callbackIndex)
        assertEquals(frames.size, transport.consumedFrames)
        assertTrue("real learning memory produced no evidence", regions > 0 || comparisons > 0)
        println("OMEGAS_SIL_SUMMARY=" + summary.toString())
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
