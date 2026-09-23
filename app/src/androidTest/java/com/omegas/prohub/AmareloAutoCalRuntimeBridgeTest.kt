package com.omegas.prohub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omegas.prohub.autocal.NativeAutoCalMonitor
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AmareloAutoCalRuntimeBridgeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val replaySessionId = 9301L

    private data class ReplayTransaction(
        val sequence: Int,
        val portmonIndex: Int,
        val request: ByteArray,
        val response: ByteArray,
    )

    private class CanonicalReplayScheduler(
        root: JSONObject,
        private val sessionId: Long,
    ) : Mp48SerialScheduler {
        private val positions = mutableMapOf<String, Int>()
        private val byRequest = linkedMapOf<String, MutableList<ReplayTransaction>>()
        val sourceRawSha256: String = root.getString("sourceRawSha256")
        val sourceRawName: String = root.getString("sourceRawName")
        val schema: String = root.getString("schema")
        val timingRule: String = root.getString("timingRule")
        val transactionCount: Int

        init {
            val rows = root.getJSONArray("transactions")
            transactionCount = rows.length()
            repeat(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                val request = parseHex(row.getString("request"))
                val tx = ReplayTransaction(
                    sequence = row.getInt("sequence"),
                    portmonIndex = row.getInt("portmon_index"),
                    request = request,
                    response = parseHex(row.getString("response")),
                )
                byRequest.getOrPut(request.key()) { mutableListOf() }.add(tx)
            }
        }

        override fun isConnected(): Boolean = true
        override fun currentSessionId(): Long = sessionId

        override fun transaction(
            request: ByteArray,
            reason: String,
            timeoutMs: Int,
            purgeBefore: Boolean,
            expectedSessionId: Long,
            workClass: Mp48WorkClass,
            telemetryAfter: Boolean,
        ): UsbProtocolReply {
            if (expectedSessionId > 0L && expectedSessionId != sessionId) {
                return UsbProtocolReply(false, request = request.copyOf(), error = "Replay session mismatch: " + reason)
            }
            val key = request.key()
            val queue = byRequest[key]
                ?: return UsbProtocolReply(false, request = request.copyOf(), error = "Canonical replay missing command " + key)
            val position = positions[key] ?: 0
            val tx = queue[position % queue.size]
            positions[key] = position + 1
            val response = tx.response
            if (response.size < request.size + 3 ||
                !response.copyOfRange(0, request.size).contentEquals(request)
            ) {
                return UsbProtocolReply(false, request = request.copyOf(), error = "Replay echo mismatch at Portmon " + tx.portmonIndex)
            }
            val status = response[request.size].toInt() and 0xFF
            val payloadSize = response[request.size + 1].toInt() and 0xFF
            val payloadStart = request.size + 2
            val payloadEnd = payloadStart + payloadSize
            if (response.size != payloadEnd + 1) {
                return UsbProtocolReply(false, status = status, request = request.copyOf(), echo = request.copyOf(), error = "Replay length mismatch at Portmon " + tx.portmonIndex)
            }
            val raw = response.copyOfRange(request.size, response.size)
            val expectedChecksum = raw.dropLast(1).sumOf { it.toInt() and 0xFF } and 0xFF
            val receivedChecksum = raw.last().toInt() and 0xFF
            if (expectedChecksum != receivedChecksum) {
                return UsbProtocolReply(false, status = status, request = request.copyOf(), echo = request.copyOf(), rawResponse = raw, error = "Replay checksum mismatch at Portmon " + tx.portmonIndex)
            }
            val payload = response.copyOfRange(payloadStart, payloadEnd)
            return UsbProtocolReply(
                ok = status == Mp48Protocol.STATUS_ACK,
                status = status,
                payload = payload,
                request = request.copyOf(),
                echo = request.copyOf(),
                rawResponse = raw,
                error = if (status == Mp48Protocol.STATUS_ACK) "" else "Replay status 0x%02X".format(status),
            )
        }

        override fun <T> unit(
            reason: String,
            expectedSessionId: Long,
            workClass: Mp48WorkClass,
            telemetryAfter: Boolean,
            waitTimeoutMs: Long,
            block: (Mp48SerialUnit) -> T,
        ): T {
            val boundSession = if (expectedSessionId > 0L) expectedSessionId else sessionId
            val unit = object : Mp48SerialUnit {
                override val sessionId: Long = boundSession
                override fun transaction(
                    request: ByteArray,
                    reason: String,
                    timeoutMs: Int,
                    purgeBefore: Boolean,
                ): UsbProtocolReply = this@CanonicalReplayScheduler.transaction(
                    request = request,
                    reason = reason,
                    timeoutMs = timeoutMs,
                    purgeBefore = purgeBefore,
                    expectedSessionId = boundSession,
                    workClass = workClass,
                    telemetryAfter = false,
                )
            }
            return block(unit)
        }

        fun provenance(): JSONObject = JSONObject()
            .put("schema", schema)
            .put("sourceRawName", sourceRawName)
            .put("sourceRawSha256", sourceRawSha256)
            .put("timingRule", timingRule)
            .put("transactionCount", transactionCount)
            .put("adapter", "ANDROID_TEST_MP48_SERIAL_SCHEDULER")

        companion object {
            private fun parseHex(value: String): ByteArray =
                value.trim().split(Regex("\\s+")).filter(String::isNotBlank).map { it.toInt(16).toByte() }.toByteArray()

            private fun ByteArray.key(): String =
                joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        }
    }

    @Test
    fun canonicalReplayFlowsThroughRuntimeBridgeAndWebView() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var originalMonitor: NativeAutoCalMonitor? = null
        try {
            waitFor(12_000L) {
                var ready = false
                scenario.onActivity { ready = it.serviceOrNull() != null }
                ready
            }
            SystemClock.sleep(1_500L)
            evalRaw(scenario, "document.querySelector('[data-route=\"autocal\"]')?.click(); 'ok';")
            SystemClock.sleep(500L)

            val root = JSONObject(
                instrumentation.context.assets.open("portmon-lognovo-replay-v1.json")
                    .bufferedReader().use { it.readText() },
            )
            val replay = CanonicalReplayScheduler(root, replaySessionId)
            assertEquals(
                "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64",
                replay.sourceRawSha256,
            )
            assertTrue(replay.transactionCount > 900)

            val monitor = NativeAutoCalMonitor(
                serial = replay,
                calibrationBusy = { false },
            )
            monitor.beginUsbSession(replaySessionId)
            SystemClock.sleep(8_100L)
            monitor.requestSnapshot("ANDROID_CANONICAL_REPLAY_RUNTIME_BRIDGE")
            monitor.tick()

            val runtimeSnapshot = monitor.latestSnapshotJson()
            val runtimeStatus = monitor.statusJson()
            assertTrue("Runtime replay must publish a snapshot", runtimeSnapshot.optBoolean("available"))
            assertEquals("ECU_READ", runtimeSnapshot.optString("source"))
            assertEquals(replaySessionId, runtimeStatus.optLong("sessionId"))

            val liveReply = replay.transaction(
                request = Mp48Protocol.CMD_TELEMETRY,
                reason = "canonical replay live frame",
                timeoutMs = 500,
                purgeBefore = false,
                expectedSessionId = replaySessionId,
                workClass = Mp48WorkClass.READ_ONLY,
                telemetryAfter = false,
            )
            assertTrue("Canonical replay must contain MP48 telemetry", liveReply.ok)
            val capturedElapsed = SystemClock.elapsedRealtime()
            val live = Mp48Protocol.decodeTelemetry(liveReply.payload, capturedElapsed)
                .toJson()
                .put("session_id", replaySessionId)
                .put("captured_elapsed_ms", capturedElapsed)

            scenario.onActivity { activity ->
                val service = activity.serviceOrNull() ?: error("service unavailable")
                originalMonitor = getPrivateField(service, "nativeAutoCal") as NativeAutoCalMonitor
                setPrivateField(service, "nativeAutoCal", monitor)
                service.telemetryStore.beginSession(replaySessionId)
                val accepted = service.telemetryStore.updateFromEngineEvent(
                    JSONObject()
                        .put("event", "telemetry")
                        .put("session_id", replaySessionId)
                        .put("data", live),
                )
                check(accepted != null) { "TelemetryStateStore rejected canonical replay frame" }
                activity.refreshWebUi()
            }

            waitFor(5_000L) {
                runCatching {
                    val proof = bridgeDom(scenario)
                    proof.optBoolean("bridgeAvailable") &&
                        proof.optBoolean("projectionOk") &&
                        proof.optString("projectionSource") == "NATIVE_MONITOR" &&
                        proof.optString("nativeSnapshotSource") == "ECU_READ" &&
                        proof.optString("petrolPath").length > 20 &&
                        proof.optString("gasPath").length > 20 &&
                        proof.optString("kPath").length > 20
                }.getOrDefault(false)
            }

            val proof = bridgeDom(scenario)
            saveEvidence(proof, replay.provenance(), scenario)

            assertTrue(proof.getBoolean("bridgeAvailable"))
            assertTrue(proof.getBoolean("projectionOk"))
            assertEquals("NATIVE_MONITOR", proof.getString("projectionSource"))
            assertEquals("ECU_READ", proof.getString("nativeSnapshotSource"))
            assertEquals(replaySessionId, proof.getLong("projectionSessionId"))
            assertTrue(proof.getString("petrolPath").length > 20)
            assertTrue(proof.getString("gasPath").length > 20)
            assertTrue(proof.getString("kPath").length > 20)
            assertTrue(proof.getInt("currentGasPoints") > 0)
            assertTrue(proof.getInt("previousGasPoints") > 0)
            assertEquals(4, proof.getInt("zoneRegions"))
            assertFalse(proof.getBoolean("hasLevels"))
            assertFalse(proof.getBoolean("hasNaN"))
            assertFalse(proof.getBoolean("hasUndefined"))
        } finally {
            val previous = originalMonitor
            if (previous != null) {
                runCatching {
                    scenario.onActivity { activity ->
                        activity.serviceOrNull()?.let { setPrivateField(it, "nativeAutoCal", previous) }
                    }
                }
            }
            scenario.close()
        }
    }

    private fun bridgeDom(scenario: ActivityScenario<MainActivity>): JSONObject =
        evalJson(
            scenario,
            """
            JSON.stringify((() => {
              const api = window.OmegasUi?.AutoCalApi;
              const app = window.OmegasApp;
              const bridgeAvailable = typeof window.OmegasAutoCal?.getUiProjection === 'function';
              if (!bridgeAvailable || !api || !app?.autoCalCockpit) {
                return { bridgeAvailable, projectionOk: false };
              }
              const projection = api.projection();
              app.autoCalCockpit.refresh();
              const screen = document.querySelector('[data-screen="autocal"]');
              const chart = document.getElementById('autocalReferenceChart');
              const kChart = document.getElementById('autocalKChart');
              const body = screen?.innerText ?? '';
              return {
                bridgeAvailable,
                projectionOk: projection?.ok === true,
                projectionSource: projection?.source ?? '',
                projectionSessionId: Number(projection?.sessionId ?? 0),
                nativeSnapshotSource: projection?.nativeSnapshot?.source ?? '',
                petrolPath: chart?.querySelector('.petrol')?.getAttribute('d') ?? '',
                gasPath: chart?.querySelector('.gas')?.getAttribute('d') ?? '',
                currentGasPoints: chart?.querySelectorAll('.autocal-acquisition-point.gas-current').length ?? 0,
                previousGasPoints: chart?.querySelectorAll('.autocal-acquisition-point.gas-previous').length ?? 0,
                zoneRegions: chart?.querySelectorAll('.autocal-zone-region').length ?? 0,
                kPath: kChart?.querySelector('.autocal-k-line')?.getAttribute('d') ?? '',
                hasLevels: /LEVELS|NÍVEL GNV/i.test(body),
                hasNaN: /\bNaN\b/.test(body),
                hasUndefined: /\bundefined\b/i.test(body)
              };
            })())
            """.trimIndent(),
        )

    private fun saveEvidence(
        proof: JSONObject,
        provenance: JSONObject,
        scenario: ActivityScenario<MainActivity>,
    ) {
        var web = JSONObject()
        scenario.onActivity { activity ->
            val view = activity.findViewById<WebView>(R.id.hubWebView)
            web = JSONObject().put("webViewWidth", view.width).put("webViewHeight", view.height)
        }
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "omegas-evidence")
        check(dir.mkdirs() || dir.isDirectory)
        val receipt = JSONObject()
            .put("scenario", "amarelo-autocal-canonical-replay-runtime-bridge")
            .put("classification", "CANONICAL_REPLAY_RUNTIME_BRIDGE_WEBVIEW")
            .put("sourceSha", BuildConfig.OMEGAS_BUILD_COMMIT)
            .put("displayWidth", metrics.widthPixels)
            .put("displayHeight", metrics.heightPixels)
            .put("densityDpi", metrics.densityDpi)
            .put("screenshotCapture", "APP_WINDOW_DECOR_VIEW")
            .put("replayProvenance", provenance)
            .put("bridgeDom", proof)
            .put("webView", web)
        File(dir, "amarelo-autocal-canonical-replay-runtime-bridge.json").writeText(receipt.toString(2))
        val bitmap = captureAppWindow(scenario)
        FileOutputStream(File(dir, "amarelo-autocal-canonical-replay-runtime-bridge.png")).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun getPrivateField(target: Any, name: String): Any? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }

    private fun captureAppWindow(scenario: ActivityScenario<MainActivity>): Bitmap {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val bitmap = Bitmap.createBitmap(
            metrics.widthPixels,
            metrics.heightPixels,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.BLACK)
        scenario.onActivity { activity ->
            val canvas = Canvas(bitmap)
            activity.window.decorView.draw(canvas)
        }
        return bitmap
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun evalJson(scenario: ActivityScenario<MainActivity>, script: String): JSONObject {
        val raw = evalRaw(scenario, script)
        val value = JSONTokener(raw).nextValue()
        return when (value) {
            is String -> JSONObject(value)
            is JSONObject -> value
            else -> error("unexpected JS result: " + raw)
        }
    }

    private fun evalRaw(scenario: ActivityScenario<MainActivity>, script: String): String {
        val latch = CountDownLatch(1)
        var result = "null"
        scenario.onActivity { activity ->
            activity.findViewById<WebView>(R.id.hubWebView).evaluateJavascript(script) {
                result = it ?: "null"
                latch.countDown()
            }
        }
        check(latch.await(8, TimeUnit.SECONDS)) { "JavaScript evaluation timeout" }
        return result
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100L)
        }
        error("condition timeout")
    }
}
