package com.omegas.prohub

import android.graphics.Bitmap
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omegas.prohub.ecu.Mp48Protocol
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DashboardLevelsRenderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private data class LiveFixture(
        val payload: ByteArray,
        val levelRaw: Int,
    )

    private fun launch(): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(12_000L) { var ready=false; scenario.onActivity { ready = it.serviceOrNull() != null }; ready }
        SystemClock.sleep(2_500L)
        return scenario
    }

    private fun liveFixture(): LiveFixture {
        val raw = instrumentation.context.assets
            .open("portmon-autocal-cycle-v1.json")
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(raw)
        val rows = root.getJSONArray("transactions")
        for (index in 0 until rows.length()) {
            val tx = rows.getJSONObject(index)
            if (tx.getString("request") != "48 01 49") continue
            val response = hex(tx.getString("response"))
            val requestSize = hex(tx.getString("request")).size
            val payloadSize = response[requestSize + 1].toInt() and 0xFF
            val payload = response.copyOfRange(requestSize + 2, requestSize + 2 + payloadSize)
            val decoded = Mp48Protocol.decodeTelemetry(payload, SystemClock.elapsedRealtime())
            return LiveFixture(payload, decoded.levelRaw)
        }
        error("No live Portmon frame")
    }

    private fun injectFresh(scenario: ActivityScenario<MainActivity>, fixture: LiveFixture) {
        scenario.onActivity { activity ->
            val service = activity.serviceOrNull() ?: error("service unavailable")
            val session = 9001L
            val captured = SystemClock.elapsedRealtime()
            service.telemetryStore.beginSession(session)
            val live = Mp48Protocol.decodeTelemetry(fixture.payload, captured).toJson()
                .put("session_id", session)
                .put("captured_elapsed_ms", captured)
            val accepted = service.telemetryStore.updateFromEngineEvent(
                JSONObject()
                    .put("event", "telemetry")
                    .put("session_id", session)
                    .put("data", live),
            )
            check(accepted != null) { "TelemetryStateStore rejected replay" }
            activity.refreshWebUi()
        }
        SystemClock.sleep(1_400L)
    }

    private fun dashboardDom(scenario: ActivityScenario<MainActivity>): JSONObject =
        evalJson(
            scenario,
            """
            JSON.stringify((() => {
              const labels = [...document.querySelectorAll('.now-metric-card small')].map(n => n.textContent.trim().toUpperCase());
              return {
                route: document.querySelector('[data-screen="dashboard"]')?.classList.contains('active') === true,
                hasLevelsRawLabel: labels.includes('LEVELS RAW'),
                levelsRaw: document.getElementById('dashLevelsRaw')?.textContent ?? null,
                hero: document.getElementById('dashHeroStatus')?.textContent ?? null,
                bodyHasLevelsRaw: document.body.innerText.toUpperCase().includes('LEVELS RAW')
              };
            })())
            """.trimIndent(),
        )

    private fun autocalDom(scenario: ActivityScenario<MainActivity>): JSONObject {
        evalRaw(scenario, "document.querySelector('[data-route=\"autocal\"]')?.click(); 'ok';")
        SystemClock.sleep(1_500L)
        return evalJson(
            scenario,
            """
            JSON.stringify({
              active: document.querySelector('[data-screen="autocal"]')?.classList.contains('active') === true,
              level: document.getElementById('autocalLiveLevel')?.textContent ?? null,
              title: document.getElementById('autocalLiveTitle')?.textContent ?? null
            })
            """.trimIndent(),
        )
    }

    @Test
    fun dashboardFreshLevelsRaw() {
        val scenario = launch()
        try {
            val fixture = liveFixture()
            injectFresh(scenario, fixture)
            val dom = dashboardDom(scenario)
            saveEvidence("dashboard-fresh", dom, scenario)
            assertTrue("Dashboard must render LEVELS RAW label", dom.getBoolean("hasLevelsRawLabel"))
            assertEquals(fixture.levelRaw.toString(), dom.optString("levelsRaw"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun dashboardInvalidLevelsPlaceholder() {
        val scenario = launch()
        try {
            val dom = dashboardDom(scenario)
            saveEvidence("dashboard-invalid", dom, scenario)
            assertTrue("Dashboard must reserve LEVELS RAW even without telemetry", dom.getBoolean("hasLevelsRawLabel"))
            assertEquals("—", dom.optString("levelsRaw"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun autocalFreshLevelsControl() {
        val scenario = launch()
        try {
            val fixture = liveFixture()
            injectFresh(scenario, fixture)
            val dom = autocalDom(scenario)
            saveEvidence("autocal-fresh-control", dom, scenario)
            assertTrue("AutoCal route must activate", dom.getBoolean("active"))
            assertEquals(fixture.levelRaw.toString(), dom.optString("level"))
        } finally {
            scenario.close()
        }
    }

    private fun saveEvidence(
        name: String,
        dom: JSONObject,
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
            .put("sourceSha", BuildConfig.OMEGAS_BUILD_COMMIT)
            .put("displayWidth", metrics.widthPixels)
            .put("displayHeight", metrics.heightPixels)
            .put("densityDpi", metrics.densityDpi)
            .put("dom", dom)
            .put("webView", web)
        File(dir, "$name.json").writeText(receipt.toString(2))
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(dir, "$name.png")).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun evalJson(scenario: ActivityScenario<MainActivity>, script: String): JSONObject {
        val raw = evalRaw(scenario, script)
        val value = JSONTokener(raw).nextValue()
        return when (value) {
            is String -> JSONObject(value)
            is JSONObject -> value
            else -> error("unexpected JS result: $raw")
        }
    }

    private fun evalRaw(scenario: ActivityScenario<MainActivity>, script: String): String {
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
        value.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            .map { it.toInt(16).toByte() }.toByteArray()
}
