package com.omegas.prohub

import android.graphics.Bitmap
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omegas.prohub.autocal.AutoCalReadObservation
import com.omegas.prohub.autocal.AutoCalSnapshotBuilder
import com.omegas.prohub.autocal.AutoCalSnapshotSource
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import org.json.JSONArray
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
class AmareloAutoCalRenderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val sessionId = 9201L

    private data class RenderFixture(
        val observations: List<AutoCalReadObservation>,
        val livePayload: ByteArray,
        val provenance: JSONObject,
    )

    private fun launch(): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(12_000L) {
            var ready = false
            scenario.onActivity { ready = it.serviceOrNull() != null }
            ready
        }
        SystemClock.sleep(1_500L)
        return scenario
    }

    private fun fixture(): RenderFixture {
        val raw = instrumentation.context.assets
            .open("amarelo-autocal-render-original-v1.json")
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(raw)
        assertEquals(
            "COMPOSITE_ORIGINAL_DERIVED_FOR_RENDER_ONLY",
            root.getString("classification"),
        )
        val provenance = root.getJSONObject("provenance")
        assertEquals(
            "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64",
            provenance.getString("sourceRawSha256"),
        )
        assertEquals(824550, provenance.getInt("targetPortmonIndex"))
        assertEquals(0, root.getJSONArray("missing").length())

        val fieldsByKey = AutoCalProtocol.READ_ONLY_FIELDS.associateBy { it.key }
        val rows = root.getJSONArray("fields")
        val capturedAt = System.currentTimeMillis()
        val observations = ArrayList<AutoCalReadObservation>(rows.length())

        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val key = row.getString("key")
            val field = checkNotNull(fieldsByKey[key]) { "Unexpected fixture field $key" }
            val request = hex(row.getString("request"))
            val response = hex(row.getString("response"))
            check(response.size >= request.size + 3) { "$key: truncated response" }
            check(response.copyOfRange(0, request.size).contentEquals(request)) {
                "$key: response echo differs from request"
            }
            val status = response[request.size].toInt() and 0xFF
            val payloadSize = response[request.size + 1].toInt() and 0xFF
            val payloadStart = request.size + 2
            val payloadEnd = payloadStart + payloadSize
            check(response.size == payloadEnd + 1) { "$key: invalid response length" }
            observations += AutoCalReadObservation(
                field = field,
                status = status,
                payload = response.copyOfRange(payloadStart, payloadEnd),
                capturedAtMs = capturedAt,
            )
        }

        val liveRow = root.getJSONObject("live")
        val liveRequest = hex(liveRow.getString("request"))
        val liveResponse = hex(liveRow.getString("response"))
        check(liveRequest.contentEquals(byteArrayOf(0x48, 0x01, 0x49))) { "Unexpected live request" }
        check(liveResponse.copyOfRange(0, liveRequest.size).contentEquals(liveRequest)) {
            "Live response echo differs from request"
        }
        val liveStatus = liveResponse[liveRequest.size].toInt() and 0xFF
        check(liveStatus == Mp48Protocol.STATUS_ACK) { "Live status is not ACK" }
        val liveSize = liveResponse[liveRequest.size + 1].toInt() and 0xFF
        val liveStart = liveRequest.size + 2
        val livePayload = liveResponse.copyOfRange(liveStart, liveStart + liveSize)

        return RenderFixture(observations, livePayload, JSONObject(provenance.toString()))
    }

    private fun installFixture(
        scenario: ActivityScenario<MainActivity>,
        fixture: RenderFixture,
    ) {
        val capturedAt = fixture.observations.first().capturedAtMs
        val expectedFields = fixture.observations.map { it.field }.distinctBy { it.identity }
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = fixture.observations,
            expectedFields = expectedFields,
            sessionId = "AUTOCAL-$sessionId-ANDROID-RENDER",
            source = AutoCalSnapshotSource.REPLAY,
            startedAtMs = capturedAt,
            finishedAtMs = capturedAt,
        )
        assertTrue("Render replay must remain temporally coherent", snapshot.temporalCoherent)

        val decorated = snapshot.toJson()
            .put("available", true)
            .put("nativeAutoCal", true)
            .put("autoCalEnabled", 1)
            .put("nativeMaturityEvents", JSONArray())
            .put("nativeMaturityEventCount", 0)
            .put(
                "nativeCorrelationState",
                JSONObject()
                    .put("correlatedBands", JSONArray())
                    .put("retryableBands", JSONArray()),
            )

        val state = JSONObject()
            .put("state", "READY")
            .put("message", "Replay original-derived passando pelo decoder de produção")
            .put("sessionId", sessionId)
            .put("autoCalEnabled", 1)
            .put("nativeFlag13", 0)
            .put("autoMatchCount", 1)
            .put("maxAutomatch", 3)
            .put("appAutomaticWrite", false)

        scenario.onActivity { activity ->
            val service = activity.serviceOrNull() ?: error("service unavailable")
            setPrivateField(service.nativeAutoCal, "latestSnapshot", decorated)
            setPrivateField(service.nativeAutoCal, "state", state)

            val capturedElapsed = SystemClock.elapsedRealtime()
            service.telemetryStore.beginSession(sessionId)
            val live = Mp48Protocol.decodeTelemetry(fixture.livePayload, capturedElapsed)
                .toJson()
                .put("session_id", sessionId)
                .put("captured_elapsed_ms", capturedElapsed)
            val accepted = service.telemetryStore.updateFromEngineEvent(
                JSONObject()
                    .put("event", "telemetry")
                    .put("session_id", sessionId)
                    .put("data", live),
            )
            check(accepted != null) { "TelemetryStateStore rejected original-derived live frame" }
            activity.refreshWebUi()
        }
        SystemClock.sleep(900L)
    }

    private fun activateAutoCal(scenario: ActivityScenario<MainActivity>) {
        evalRaw(
            scenario,
            "document.querySelector('[data-route=\\\"autocal\\\"]')?.click(); 'ok';",
        )
        SystemClock.sleep(500L)
    }

    private fun dom(scenario: ActivityScenario<MainActivity>): JSONObject =
        evalJson(
            scenario,
            """
            JSON.stringify((() => {
              const screen = document.querySelector('[data-screen="autocal"]');
              const chart = document.getElementById('autocalReferenceChart');
              const kChart = document.getElementById('autocalKChart');
              const live = document.getElementById('autocalLiveTitle');
              const cockpit = document.querySelector('.autocal-cockpit');
              const toggle = document.querySelector('[data-autocal-toggle]');
              const kCard = document.querySelector('.autocal-k-card');
              const bandsCard = document.querySelector('.autocal-bands-card');
              const body = screen?.innerText ?? '';
              const rect = chart?.getBoundingClientRect();
              const screenRect = screen?.getBoundingClientRect();
              const cockpitRect = cockpit?.getBoundingClientRect();
              const toggleRect = toggle?.getBoundingClientRect();
              const kRect = kCard?.getBoundingClientRect();
              const bandsRect = bandsCard?.getBoundingClientRect();
              return {
                active: screen?.classList.contains('active') === true,
                hasLevels: /LEVELS|NÍVEL GNV/i.test(body),
                hasNaN: /\bNaN\b/.test(body),
                hasUndefined: /\bundefined\b/i.test(body),
                petrolPath: chart?.querySelector('.petrol')?.getAttribute('d') ?? '',
                gasPath: chart?.querySelector('.gas')?.getAttribute('d') ?? '',
                currentGasPoints: chart?.querySelectorAll('.autocal-acquisition-point.gas-current').length ?? 0,
                previousGasPoints: chart?.querySelectorAll('.autocal-acquisition-point.gas-previous').length ?? 0,
                petrolPoints: chart?.querySelectorAll('.autocal-acquisition-point.petrol-current').length ?? 0,
                zoneRegions: chart?.querySelectorAll('.autocal-zone-region').length ?? 0,
                gasActiveRegions: chart?.querySelectorAll('.autocal-zone-region.gas-active, .autocal-zone-region.both-active').length ?? 0,
                kPath: kChart?.querySelector('.autocal-k-line')?.getAttribute('d') ?? '',
                kPoints: kChart?.querySelectorAll('.autocal-k-point').length ?? 0,
                rpm: document.getElementById('autocalLiveRpm')?.textContent ?? '—',
                petrol: document.getElementById('autocalLivePetrol')?.textContent ?? '—',
                map: document.getElementById('autocalLiveMap')?.textContent ?? '—',
                zoneSummary: document.getElementById('autocalZoneSummary')?.textContent ?? '',
                chartWidth: rect?.width ?? 0,
                chartHeight: rect?.height ?? 0,
                viewportWidth: window.innerWidth,
                viewportHeight: window.innerHeight,
                screenBottom: screenRect?.bottom ?? 0,
                cockpitBottom: cockpitRect?.bottom ?? 0,
                cockpitHeight: cockpitRect?.height ?? 0,
                toggleBottom: toggleRect?.bottom ?? 0,
                kCardBottom: kRect?.bottom ?? 0,
                bandsCardBottom: bandsRect?.bottom ?? 0,
                hasPosition18Copy: body.includes('POSIÇÕES NATIVAS DE AQUISIÇÃO GNV'),
                hasFakeFourProgress: /GNV\s+\d+\/4/.test(body),
                previousEpochCopy: /época AutoMatch anterior|epoch anterior|snapshot.*anterior/i.test(body)
              };
            })())
            """.trimIndent(),
        )

    @Test
    fun autocalOriginalDerivedAdaptiveRender() {
        val scenario = launch()
        try {
            val fixture = fixture()
            activateAutoCal(scenario)
            installFixture(scenario, fixture)

            waitFor(5_000L) {
                runCatching {
                    val liveDom = dom(scenario)
                    liveDom.optString("rpm", "—") != "—" &&
                        liveDom.optString("petrol", "—") != "—" &&
                        liveDom.optString("map", "—") != "—"
                }.getOrDefault(false)
            }
            val dom = dom(scenario)
            saveEvidence("amarelo-autocal-original-derived", dom, fixture.provenance, scenario)

            assertTrue("AutoCal route must be active", dom.getBoolean("active"))
            assertFalse("LEVELS belongs only to Dashboard/Agora", dom.getBoolean("hasLevels"))
            assertFalse("AutoCal must not render NaN", dom.getBoolean("hasNaN"))
            assertFalse("AutoCal must not render undefined", dom.getBoolean("hasUndefined"))

            assertTrue("Gasoline reference curve must render", dom.getString("petrolPath").length > 20)
            assertTrue("GNV reference curve must render", dom.getString("gasPath").length > 20)
            assertTrue("Current native GNV epoch must expose points", dom.getInt("currentGasPoints") > 0)
            assertTrue("Previous native AutoMatch epoch must expose points", dom.getInt("previousGasPoints") > 0)
            assertTrue("Petrol acquisition layer must expose points", dom.getInt("petrolPoints") > 0)
            assertEquals("Exactly four native MAP regions must render", 4, dom.getInt("zoneRegions"))
            assertTrue("Original-derived fixture must show registered GNV regions", dom.getInt("gasActiveRegions") > 0)

            assertTrue("Native Curve K path must render", dom.getString("kPath").length > 20)
            assertTrue("Native Curve K must expose its sample points", dom.getInt("kPoints") >= 20)
            assertTrue("Live RPM must render", dom.getString("rpm") != "—")
            assertTrue("Live Petrol Injection must render", dom.getString("petrol") != "—")
            assertTrue("Live MAP must render", dom.getString("map") != "—")

            assertTrue("18 internal items must be described as positions, not MAP regions", dom.getBoolean("hasPosition18Copy"))
            assertFalse("No N/4 fake GNV progress is allowed", dom.getBoolean("hasFakeFourProgress"))
            assertTrue("Previous layer must be described as prior AutoMatch epoch", dom.getBoolean("previousEpochCopy"))

            assertTrue("Primary AutoCal chart must use substantial width", dom.getDouble("chartWidth") >= 760.0)
            assertTrue("Primary AutoCal chart must remain visually dominant", dom.getDouble("chartHeight") >= 260.0)
            assertTrue(
                "Normal 1280x720 AutoCal cockpit must fit without mandatory vertical scrolling",
                dom.getDouble("cockpitBottom") <= dom.getDouble("screenBottom") + 2.0,
            )
            assertTrue(
                "Native Curve K must remain visible in the normal 1280x720 composition",
                dom.getDouble("kCardBottom") <= dom.getDouble("screenBottom") + 2.0,
            )
            assertTrue(
                "Native acquisition positions must remain visible in the normal 1280x720 composition",
                dom.getDouble("bandsCardBottom") <= dom.getDouble("screenBottom") + 2.0,
            )
            assertTrue(
                "Auto Calibration primary action must remain visible without scrolling",
                dom.getDouble("toggleBottom") <= dom.getDouble("screenBottom") + 2.0,
            )
        } finally {
            scenario.close()
        }
    }

    private fun saveEvidence(
        name: String,
        dom: JSONObject,
        provenance: JSONObject,
        scenario: ActivityScenario<MainActivity>,
    ) {
        var web = JSONObject()
        scenario.onActivity { activity ->
            val view = activity.findViewById<WebView>(R.id.hubWebView)
            web = JSONObject()
                .put("webViewWidth", view.width)
                .put("webViewHeight", view.height)
        }
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "omegas-evidence")
        check(dir.mkdirs() || dir.isDirectory)
        val receipt = JSONObject()
            .put("scenario", name)
            .put("classification", "RENDER_INTEGRATION_ONLY")
            .put("sourceSha", BuildConfig.OMEGAS_BUILD_COMMIT)
            .put("displayWidth", metrics.widthPixels)
            .put("displayHeight", metrics.heightPixels)
            .put("densityDpi", metrics.densityDpi)
            .put("fixtureProvenance", provenance)
            .put("dom", dom)
            .put("webView", web)
        File(dir, "$name.json").writeText(receipt.toString(2))
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(dir, "$name.png")).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun evalJson(
        scenario: ActivityScenario<MainActivity>,
        script: String,
    ): JSONObject {
        val raw = evalRaw(scenario, script)
        val value = JSONTokener(raw).nextValue()
        return when (value) {
            is String -> JSONObject(value)
            is JSONObject -> value
            else -> error("unexpected JS result: $raw")
        }
    }

    private fun evalRaw(
        scenario: ActivityScenario<MainActivity>,
        script: String,
    ): String {
        val latch = CountDownLatch(1)
        var result = "null"
        scenario.onActivity { activity ->
            val webView = activity.findViewById<WebView>(R.id.hubWebView)
            webView.evaluateJavascript(script) {
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

    private fun hex(value: String): ByteArray =
        value.trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .map { it.toInt(16).toByte() }
            .toByteArray()
}
