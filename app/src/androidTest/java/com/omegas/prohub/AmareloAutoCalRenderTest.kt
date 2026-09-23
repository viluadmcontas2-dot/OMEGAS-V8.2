package com.omegas.prohub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omegas.prohub.autocal.AutoCalReadObservation
import com.omegas.prohub.autocal.AutoCalSnapshotBuilder
import com.omegas.prohub.autocal.AutoCalSnapshotSource
import com.omegas.prohub.autocal.AutoCalUiProjection
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

        var renderProjection = JSONObject()
        var renderTelemetry = JSONObject()
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

            renderTelemetry = JSONObject()
                .put("valid", true)
                .put("ageMs", 50)
                .put("telemetryAgeMs", 50)
                .put("sessionId", sessionId)
                .put("sequence", 1)
                .put("updatedAt", System.currentTimeMillis())
                .put("live", JSONObject(live.toString()))

            renderProjection = AutoCalUiProjection.project(
                nativeStatus = JSONObject(state.toString()).put("updatedAt", System.currentTimeMillis()),
                nativeSnapshot = JSONObject(decorated.toString()),
                manualStatus = JSONObject().put("state", "IDLE").put("sessionId", sessionId),
                manualSnapshot = JSONObject().put("available", false),
                telemetryStatus = JSONObject(renderTelemetry.toString()),
            )
            activity.refreshWebUi()
        }

        val projectionLiteral = renderProjection.toString()
        val telemetryLiteral = renderTelemetry.toString()
        val patched = evalRaw(
            scenario,
            """
            (() => {
              const api = window.OmegasUi?.AutoCalApi;
              const app = window.OmegasApp;
              if (!api || !app?.store || !app?.autoCalCockpit) return 'missing';
              const projection = $projectionLiteral;
              const telemetry = $telemetryLiteral;
              api.projection = () => projection;
              app.api.presentSnapshot = () => ({ ok: true, revision: 1, data: telemetry });
              api.actionStatus = () => ({ state: 'IDLE', busy: false });
              api.sessionStatus = () => ({
                recording: false,
                durationMs: 0,
                droppedEvents: 0,
                semanticSummary: { sessionId: 'ANDROID-RENDER', autocal: { correlatedRegions: [], gasZones: 2 } },
                documentsMirror: { available: true, lastSyncOk: true }
              });
              app.store.patch({ telemetry });
              app.autoCalCockpit.refresh();
              return 'ok';
            })();
            """.trimIndent(),
        )
        check(patched.contains("ok")) { "WebView render seam unavailable: $patched" }
        SystemClock.sleep(250L)
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
              const technical = document.getElementById('autocalTechnicalDetails');
              const zoneMeter = document.getElementById('autocalZoneMeter');
              const body = screen?.innerText ?? '';
              const rect = chart?.getBoundingClientRect();
              const screenRect = screen?.getBoundingClientRect();
              const cockpitRect = cockpit?.getBoundingClientRect();
              const toggleRect = toggle?.getBoundingClientRect();
              const kRect = kCard?.getBoundingClientRect();
              const bandsRect = bandsCard?.getBoundingClientRect();
              const zoneMeterRect = zoneMeter?.getBoundingClientRect();
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
                zoneMeterBottom: zoneMeterRect?.bottom ?? 0,
                technicalOpen: technical?.open === true,
                bandsInsideTechnical: technical?.contains(bandsCard) === true,
                gasZoneStates: [...document.querySelectorAll('[data-autocal-zone-gas]')].map(node => node.dataset.state || ''),
                gasZoneLabels: [...document.querySelectorAll('[data-autocal-zone-gas]')].map(node => node.textContent.trim()),
                currentGasZones: [...document.querySelectorAll('[data-autocal-zone-gas][data-current="true"]')].map(node => Number(node.dataset.autocalZoneGas) + 1),
                hasPosition18Copy: /18\s+posições nativas de aquisição GNV/i.test(screen?.textContent ?? ''),
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
            val interactions = exerciseCockpitInteractions(scenario)
            saveEvidence("amarelo-autocal-original-derived", dom, interactions, fixture.provenance, scenario)

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
            assertTrue("Primary AutoCal chart must remain visually dominant", dom.getDouble("chartHeight") >= 300.0)
            assertTrue(
                "Normal 1280x720 AutoCal cockpit must fit without mandatory vertical scrolling",
                dom.getDouble("cockpitBottom") <= dom.getDouble("screenBottom") + 2.0,
            )
            assertTrue(
                "Native Curve K must remain visible in the normal 1280x720 composition",
                dom.getDouble("kCardBottom") <= dom.getDouble("screenBottom") + 2.0,
            )
            assertFalse("Technical disclosure must stay closed in the normal driving view", dom.getBoolean("technicalOpen"))
            assertTrue("18 native positions must remain available inside technical disclosure", dom.getBoolean("bandsInsideTechnical"))
            assertTrue(
                "Operator Z1-Z4 map must remain visible in the normal 1280x720 composition",
                dom.getDouble("zoneMeterBottom") <= dom.getDouble("screenBottom") + 2.0,
            )
            assertEquals("GNV zone map must expose exactly four cells", 4, dom.getJSONArray("gasZoneStates").length())
            assertTrue(
                "GNV zone cells must use explicit OK/FALTA state",
                dom.getJSONArray("gasZoneLabels").toString().contains("OK") ||
                    dom.getJSONArray("gasZoneLabels").toString().contains("FALTA"),
            )
            assertEquals("Current physical MAP must identify exactly one GNV zone", 1, dom.getJSONArray("currentGasZones").length())
            assertTrue(
                "Auto Calibration primary action must remain visible without scrolling",
                dom.getDouble("toggleBottom") <= dom.getDouble("screenBottom") + 2.0,
            )

            assertTrue("Primary button must route active AutoCal to disable", interactions.getBoolean("toggleDisableRequested"))
            assertEquals("Consultar ECU must call the reader exactly once", 1, interactions.getInt("readRequests"))
            assertEquals("Reset GNV must route through prepare", "RESET_GAS", interactions.getString("preparedAction"))
            assertTrue("Reset preparation must expose the review surface", interactions.getBoolean("reviewVisible"))
            assertTrue("Technical details must open on demand", interactions.getBoolean("technicalOpened"))
            assertTrue("Sessions drawer must open from the cockpit", interactions.getBoolean("sessionsOpened"))
            assertTrue("Sessions drawer must render returned sessions", interactions.getInt("sessionItems") > 0)
            assertTrue("Reference points must drive the point inspector", interactions.getBoolean("referenceInspectorChanged"))
            assertTrue("Native positions must drive the band inspector", interactions.getBoolean("bandInspectorChanged"))
        } finally {
            scenario.close()
        }
    }

    private fun exerciseCockpitInteractions(
        scenario: ActivityScenario<MainActivity>,
    ): JSONObject =
        evalJson(
            scenario,
            """
            JSON.stringify((() => {
              const app = window.OmegasApp;
              const api = window.OmegasUi?.AutoCalApi;
              const cockpit = app?.autoCalCockpit;
              if (!app?.store || !api || !cockpit) return { available: false };

              const original = {
                setAcquisitionEnabled: api.setAcquisitionEnabled,
                startRead: api.startRead,
                prepare: api.prepare,
                cancelPreparation: api.cancelPreparation,
                sessions: api.sessions
              };
              const tap = node => {
                if (!node) return false;
                node.dispatchEvent(new MouseEvent('click', { bubbles: true }));
                return true;
              };

              let toggleRequest = null;
              let readRequests = 0;
              let preparedAction = '';

              api.setAcquisitionEnabled = enabled => {
                toggleRequest = enabled;
                return { ok: true };
              };
              api.startRead = () => {
                readRequests += 1;
                return { ok: true, state: 'QUEUED', busy: true, message: 'Render interaction read' };
              };
              api.prepare = action => {
                preparedAction = String(action || '');
                return {
                  ok: true,
                  prepared: true,
                  preparationId: 'render-preparation',
                  action: preparedAction,
                  label: preparedAction === 'RESET_GAS' ? 'Reset GNV' : preparedAction,
                  description: 'Render-only UI interaction',
                  commandHex: '00',
                  sessionId: 'ANDROID-RENDER',
                  mayChangeMulAct: false
                };
              };
              api.cancelPreparation = () => ({ ok: true });
              api.sessions = () => [{
                id: 'ANDROID-RENDER-SESSION',
                active: false,
                createdAt: Date.now(),
                durationMs: 60000,
                reason: 'Render interaction',
                semanticSummary: { autocal: { correlatedRegions: [1], gasZones: 2 } }
              }];

              const toggle = document.querySelector('[data-autocal-toggle]');
              tap(toggle);

              const read = document.querySelector('[data-autocal-read]');
              tap(read);

              const more = document.querySelector('.autocal-more-actions');
              if (more) more.open = true;

              tap(document.querySelector('[data-autocal-action="RESET_GAS"]'));
              const review = document.getElementById('autocalReview');
              const reviewVisible = !!review && review.hidden === false;
              tap(review?.querySelector('[data-autocal-cancel]'));

              tap(document.querySelector('[data-autocal-technical-toggle]'));
              const technical = document.getElementById('autocalTechnicalDetails');
              const technicalOpened = technical?.open === true;
              if (technical) technical.open = false;

              tap(document.querySelector('[data-autocal-sessions]'));
              const sessionDrawer = document.getElementById('autocalSessionDrawer');
              const sessionsOpened = !!sessionDrawer && sessionDrawer.hidden === false;
              const sessionItems = document.querySelectorAll('#autocalSessionList .autocal-session-item').length;
              tap(document.querySelector('[data-autocal-sessions]'));

              const referenceBefore = document.getElementById('autocalChartInspector')?.textContent || '';
              const selectedReference = document.querySelector('[data-autocal-ref-index].selected')?.dataset?.autocalRefIndex;
              const referenceTarget = Array.from(document.querySelectorAll('[data-autocal-ref-index]'))
                .find(node => node.dataset.autocalRefIndex !== selectedReference);
              tap(referenceTarget);
              const referenceAfter = document.getElementById('autocalChartInspector')?.textContent || '';

              const bandBefore = document.getElementById('autocalBandInspector')?.textContent || '';
              const selectedBand = document.querySelector('[data-autocal-band-index].selected')?.dataset?.autocalBandIndex;
              const bandTarget = Array.from(document.querySelectorAll('[data-autocal-band-index]'))
                .find(node => node.dataset.autocalBandIndex !== selectedBand);
              tap(bandTarget);
              const bandAfter = document.getElementById('autocalBandInspector')?.textContent || '';

              if (more) more.open = false;
              document.getElementById('alertToast')?.classList.remove('show');

              api.setAcquisitionEnabled = original.setAcquisitionEnabled;
              api.startRead = original.startRead;
              api.prepare = original.prepare;
              api.cancelPreparation = original.cancelPreparation;
              api.sessions = original.sessions;
              cockpit.refresh();

              return {
                available: true,
                toggleDisableRequested: toggleRequest === false,
                readRequests,
                preparedAction,
                reviewVisible,
                technicalOpened,
                sessionsOpened,
                sessionItems,
                referenceInspectorChanged: referenceAfter.length > 0 && referenceAfter !== referenceBefore,
                bandInspectorChanged: bandAfter.length > 0 && bandAfter !== bandBefore
              };
            })())
            """.trimIndent(),
        )

    private fun saveEvidence(
        name: String,
        dom: JSONObject,
        interactions: JSONObject,
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
            .put("screenshotCapture", "APP_WINDOW_DECOR_VIEW")
            .put("fixtureProvenance", provenance)
            .put("dom", dom)
            .put("interactions", interactions)
            .put("webView", web)
        File(dir, "$name.json").writeText(receipt.toString(2))
        val bitmap = captureAppWindow(scenario)
        FileOutputStream(File(dir, "$name.png")).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
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
