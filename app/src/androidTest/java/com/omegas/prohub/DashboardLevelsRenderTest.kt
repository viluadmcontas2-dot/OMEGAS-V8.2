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
import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import org.json.JSONArray
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

    private fun injectFresh(
        scenario: ActivityScenario<MainActivity>,
        fixture: LiveFixture,
        settleMs: Long = 700L,
        sessionId: Long = 9001L,
    ) {
        scenario.onActivity { activity ->
            val service = activity.serviceOrNull() ?: error("service unavailable")
            val session = sessionId
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
        SystemClock.sleep(settleMs)
    }

    private val referenceAutoCalFields = listOf(
        AutoCalProtocol.PETR_INJ_TBP,
        AutoCalProtocol.MNFLD_PRESS_THD,
        AutoCalProtocol.MUL_ACT,
        AutoCalProtocol.PETR_MNFLD_PRESS_RV,
        AutoCalProtocol.GAS_MNFLD_PRESS_RV,
    )

    private data class OriginalAutoCalFixture(
        val observations: List<AutoCalReadObservation>,
        val provenance: JSONObject,
    )

    private data class OriginalKFactorFixture(
        val operation: JSONObject,
        val provenance: JSONObject,
    )

    private fun originalKFactorFixture(
        fixtureName: String = "portmon-lognovo-autocal-reference-v1.json",
    ): OriginalKFactorFixture {
        val raw = instrumentation.context.assets
            .open(fixtureName)
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(raw)
        check(root.getString("classification") == "ORIGINAL_DERIVED") {
            "$fixtureName is not allowed as a Curve K oracle"
        }
        val provenance = root.getJSONObject("provenance")
        check(provenance.getString("sourceRawSha256") ==
            "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64") {
            "Unexpected PortmonLOGNOVO source hash"
        }
        val rows = root.getJSONArray("transactions")

        fun payloadForKey(key: String): ByteArray {
            for (index in 0 until rows.length()) {
                val tx = rows.getJSONObject(index)
                if (tx.getString("key") != key) continue
                val request = hex(tx.getString("request"))
                val response = hex(tx.getString("response"))
                check(response.size >= request.size + 3) { "$key: truncated response" }
                check(response.copyOfRange(0, request.size).contentEquals(request)) {
                    "$key: response echo differs from original request"
                }
                val status = response[request.size].toInt() and 0xFF
                check(status == Mp48Protocol.STATUS_ACK) { "$key: original transaction is not ACK" }
                val payloadSize = response[request.size + 1].toInt() and 0xFF
                val payloadStart = request.size + 2
                val payloadEnd = payloadStart + payloadSize
                check(response.size == payloadEnd + 1) {
                    "$key: response must contain exactly one trailing checksum byte"
                }
                return response.copyOfRange(payloadStart, payloadEnd)
            }
            error("Missing original transaction for $key")
        }

        val axisRaw = KFactorProtocol.decodeRawPoints(payloadForKey("PETR_INJ_TBP"))
        val factorsRaw = KFactorProtocol.decodeRawPoints(payloadForKey("MUL_ACT"))
        check(axisRaw.size == KFactorProtocol.POINT_COUNT)
        check(factorsRaw.size == KFactorProtocol.POINT_COUNT)
        val points = JSONArray()
        repeat(KFactorProtocol.POINT_COUNT) { index ->
            points.put(JSONObject()
                .put("index", index)
                .put("petrolAxisRaw", axisRaw[index])
                .put("petrolMs", KFactorProtocol.petrolMsFromAxisRaw(axisRaw[index]))
                .put("factorRaw", factorsRaw[index])
                .put("factor", KFactorProtocol.factorFromRaw(factorsRaw[index])))
        }
        val operation = JSONObject()
            .put("ok", true)
            .put("state", "COMPLETED")
            .put("busy", false)
            .put("axisRaw", JSONArray(axisRaw.toList()))
            .put("factorsRaw", JSONArray(factorsRaw.toList()))
            .put("points", points)
            .put("pointCount", KFactorProtocol.POINT_COUNT)
            .put("factorEncoding", "Q14")
            .put("axisEncoding", "raw/512 ms")
            .put("complete", true)
            .put("automatic", false)
            .put("source", "ORIGINAL_DERIVED_REPLAY")
        return OriginalKFactorFixture(
            operation = operation,
            provenance = JSONObject(provenance.toString())
                .put("replayRole", "CURVE_K_POSITIVE_RENDER")
                .put("productionDecoder", "KFactorProtocol")
                .put("liveEcuClaim", false),
        )
    }

    private fun autoCalFixtureRawValues(
        fixtureName: String,
    ): Map<String, IntArray> {
        val raw = instrumentation.context.assets
            .open(fixtureName)
            .bufferedReader()
            .use { it.readText() }
        val fields = JSONObject(raw).getJSONArray("fields")
        val result = linkedMapOf<String, IntArray>()
        for (index in 0 until fields.length()) {
            val field = fields.getJSONObject(index)
            val values = field.getJSONArray("rawValues")
            result[field.getString("key")] = IntArray(values.length()) { values.getInt(it) }
        }
        return result
    }

    private fun payloadFor(field: AutoCalProtocol.Field, values: IntArray): ByteArray =
        when (field.encoding) {
            AutoCalProtocol.Encoding.U8 -> ByteArray(values.size) { values[it].toByte() }
            AutoCalProtocol.Encoding.U16_LE,
            AutoCalProtocol.Encoding.S16_LE,
            AutoCalProtocol.Encoding.Q14_U16_LE -> ByteArray(values.size * 2).also { payload ->
                values.forEachIndexed { index, value ->
                    val raw = value and 0xFFFF
                    payload[index * 2] = (raw and 0xFF).toByte()
                    payload[index * 2 + 1] = ((raw ushr 8) and 0xFF).toByte()
                }
            }
            AutoCalProtocol.Encoding.U8_OR_U16_LE ->
                error("Render fixture does not use U8_OR_U16_LE")
        }

    private fun originalAutoCalFixture(
        fixtureName: String = "portmon-lognovo-autocal-reference-v1.json",
    ): OriginalAutoCalFixture {
        val raw = instrumentation.context.assets
            .open(fixtureName)
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(raw)
        check(root.getString("classification") == "ORIGINAL_DERIVED") {
            "$fixtureName is not allowed as an AutoCal oracle"
        }
        val provenance = root.getJSONObject("provenance")
        check(provenance.getString("sourceRawSha256") ==
            "43a632724182c72cbd4f386ea0f7421e01d38242b48b919705671751e9eb8a64") {
            "Unexpected PortmonLOGNOVO source hash"
        }
        val fieldsByKey = referenceAutoCalFields.associateBy { it.key }
        val rows = root.getJSONArray("transactions")
        val capturedAt = System.currentTimeMillis()
        val observations = ArrayList<AutoCalReadObservation>(rows.length())
        for (index in 0 until rows.length()) {
            val tx = rows.getJSONObject(index)
            val field = checkNotNull(fieldsByKey[tx.getString("key")]) {
                "Unexpected reference field ${tx.getString("key")}"
            }
            val request = hex(tx.getString("request"))
            val response = hex(tx.getString("response"))
            check(response.size >= request.size + 3) { "${field.key}: truncated response" }
            check(response.copyOfRange(0, request.size).contentEquals(request)) {
                "${field.key}: response echo differs from original request"
            }
            val status = response[request.size].toInt() and 0xFF
            val payloadSize = response[request.size + 1].toInt() and 0xFF
            val payloadStart = request.size + 2
            val payloadEnd = payloadStart + payloadSize
            check(response.size == payloadEnd + 1) {
                "${field.key}: response must contain exactly one trailing checksum byte"
            }
            val payload = response.copyOfRange(payloadStart, payloadEnd)
            val decoded = AutoCalProtocol.decode(field, status, payload)
            check(decoded.elementCount == field.expectedElementsHint) {
                "${field.key}: original fixture has ${decoded.elementCount} elements"
            }
            observations += AutoCalReadObservation(
                field = field,
                status = status,
                payload = payload,
                capturedAtMs = capturedAt,
            )
        }
        check(observations.map { it.field.key }.toSet() == fieldsByKey.keys) {
            "Original AutoCal fixture must contain exactly the five reference fields"
        }
        return OriginalAutoCalFixture(observations, provenance)
    }

    private fun getPrivateField(target: Any, name: String): Any? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun publishAutoCalSnapshot(
        scenario: ActivityScenario<MainActivity>,
        observations: List<AutoCalReadObservation>,
        expectedFields: List<AutoCalProtocol.Field>,
        sessionId: Long,
        message: String,
    ) {
        val capturedAt = observations.maxOfOrNull { it.capturedAtMs } ?: System.currentTimeMillis()
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = observations,
            expectedFields = expectedFields,
            sessionId = "AUTOCAL-$sessionId-RENDER",
            source = AutoCalSnapshotSource.REPLAY,
            startedAtMs = observations.minOfOrNull { it.capturedAtMs } ?: capturedAt,
            finishedAtMs = capturedAt,
        )
        check(!snapshot.partial) { "Reference render fixture became partial: ${snapshot.warnings}" }
        check(snapshot.temporalCoherent) { "Reference render fixture became incoherent: ${snapshot.warnings}" }

        val decorated = snapshot.toJson()
            .put("available", true)
            .put("nativeAutoCal", true)
            .put("autoCalEnabled", 1)
            .put("nativeMaturityEvents", JSONArray())
            .put("nativeMaturityEventCount", 0)
            .put("nativeCorrelationState", JSONObject()
                .put("correlatedBands", JSONArray())
                .put("retryableBands", JSONArray()))

        val state = JSONObject()
            .put("state", "READY")
            .put("message", message)
            .put("sessionId", sessionId)
            .put("autoCalEnabled", 1)
            .put("nativeFlag13", 0)
            .put("autoMatchCount", 0)
            .put("appAutomaticWrite", false)

        scenario.onActivity { activity ->
            val service = activity.serviceOrNull() ?: error("service unavailable")
            setPrivateField(service.nativeAutoCal, "latestSnapshot", decorated)
            setPrivateField(service.nativeAutoCal, "state", state)
            activity.refreshWebUi()
        }
    }

    private fun installOriginalAutoCalReferenceFixture(
        scenario: ActivityScenario<MainActivity>,
        sessionId: Long = 9001L,
    ): JSONObject {
        val fixture = originalAutoCalFixture()
        publishAutoCalSnapshot(
            scenario = scenario,
            observations = fixture.observations,
            expectedFields = referenceAutoCalFields,
            sessionId = sessionId,
            message = "Original-derived PortmonLOGNOVO reference through production decoder",
        )
        return fixture.provenance
    }

    private fun installTestOnlyAutoCalReferenceFixture(
        scenario: ActivityScenario<MainActivity>,
        sessionId: Long = 9001L,
        fixtureName: String,
    ): JSONObject {
        val raw = instrumentation.context.assets
            .open(fixtureName)
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(raw)
        check(root.getString("classification") == "SYNTHETIC_NON_SCIENTIFIC") {
            "$fixtureName must remain explicitly non-scientific"
        }
        check(root.getString("scientificUse") == "VISUAL_ONLY_NON_SCIENTIFIC") {
            "$fixtureName must remain visual-only evidence"
        }
        val rawByKey = autoCalFixtureRawValues(fixtureName)
        val capturedAt = System.currentTimeMillis()
        val observations = referenceAutoCalFields.map { field ->
            val values = checkNotNull(rawByKey[field.key]) { "Missing render fixture field ${field.key}" }
            AutoCalReadObservation(
                field = field,
                status = Mp48Protocol.STATUS_ACK,
                payload = payloadFor(field, values),
                capturedAtMs = capturedAt,
            )
        }
        publishAutoCalSnapshot(
            scenario = scenario,
            observations = observations,
            expectedFields = referenceAutoCalFields,
            sessionId = sessionId,
            message = "TEST_ONLY visual mutation through production AutoCal projection",
        )
        return JSONObject()
            .put("classification", root.getString("classification"))
            .put("scientificUse", root.getString("scientificUse"))
            .put("source", root.optString("source"))
            .put("description", root.optString("description"))
            .put("nativeFirmwareExact", root.optBoolean("nativeFirmwareExact", false))
    }

    private fun installOriginalKFactorCurve(
        scenario: ActivityScenario<MainActivity>,
    ): JSONObject {
        val fixture = originalKFactorFixture()
        waitFor(10_000L) {
            var idle = false
            scenario.onActivity { activity ->
                val bridge = getPrivateField(activity, "v7Bridge")
                if (bridge != null) {
                    val getLastOperation = bridge.javaClass.getMethod("getLastOperation")
                    val current = JSONObject(getLastOperation.invoke(bridge) as String)
                    idle = !current.optBoolean("busy", false)
                }
            }
            idle
        }
        scenario.onActivity { activity ->
            val bridge = checkNotNull(getPrivateField(activity, "v7Bridge")) {
                "V7 bridge unavailable"
            }
            setPrivateField(bridge, "lastOperation", JSONObject(fixture.operation.toString()))
            activity.refreshWebUi()
        }
        evalRaw(
            scenario,
            """
            (() => {
              const curve = window.OmegasApp?.screens?.curve;
              if (!curve) throw new Error('Curve screen unavailable');
              curve.reading = true;
              curve.poll();
              return 'ok';
            })()
            """.trimIndent(),
        )
        SystemClock.sleep(650L)
        return fixture.provenance
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
                petrol: document.getElementById('dashHeroPetrol')?.textContent ?? null,
                map: document.getElementById('dashMap')?.textContent ?? null,
                hero: document.getElementById('dashHeroStatus')?.textContent ?? null,
                bodyHasLevelsRaw: document.body.innerText.toUpperCase().includes('LEVELS RAW')
              };
            })())
            """.trimIndent(),
        )

    private fun activateRoute(
        scenario: ActivityScenario<MainActivity>,
        route: String,
        settleMs: Long = 350L,
    ) {
        evalRaw(scenario, "document.querySelector('[data-route=\"$route\"]')?.click(); 'ok';")
        SystemClock.sleep(settleMs)
    }

    private fun activateAutocal(scenario: ActivityScenario<MainActivity>) =
        activateRoute(scenario, "autocal")

    private fun autocalDom(scenario: ActivityScenario<MainActivity>): JSONObject =
        evalJson(
            scenario,
            """
            JSON.stringify({
              active: document.querySelector('[data-screen="autocal"]')?.classList.contains('active') === true,
              title: document.getElementById('autocalLiveTitle')?.textContent ?? null,
              rpm: document.getElementById('autocalLiveRpm')?.textContent ?? null,
              petrol: document.getElementById('autocalLivePetrol')?.textContent ?? null,
              map: document.getElementById('autocalLiveMap')?.textContent ?? null,
              hasLevelsMetric: document.getElementById('autocalLiveLevel') !== null,
              geometry: (() => {
                const chart = document.getElementById('autocalReferenceChart')?.getBoundingClientRect();
                const live = document.querySelector('.autocal-live-strip')?.getBoundingClientRect();
                const hero = document.querySelector('.autocal-hero')?.getBoundingClientRect();
                const inspector = document.getElementById('autocalChartInspector')?.getBoundingClientRect();
                return {
                  chartHeight: chart?.height ?? 0,
                  chartWidth: chart?.width ?? 0,
                  chartTop: chart?.top ?? 0,
                  chartBottom: chart?.bottom ?? 0,
                  liveTop: live?.top ?? 0,
                  liveBottom: live?.bottom ?? 0,
                  heroTop: hero?.top ?? 0,
                  heroHeight: hero?.height ?? 0,
                  viewportHeight: window.innerHeight,
                  inspectorWidth: inspector?.width ?? 0,
                  inspectorTop: inspector?.top ?? 0,
                  inspectorBottom: inspector?.bottom ?? 0
                };
              })()
            })
            """.trimIndent(),
        )

    private fun autocalReferenceDom(scenario: ActivityScenario<MainActivity>): JSONObject =
        evalJson(
            scenario,
            """
            JSON.stringify((() => {
              const petrolPath = document.querySelector('.autocal-reference-line.petrol:not(.previous)');
              const gasPath = document.querySelector('.autocal-reference-line.gas:not(.previous)');
              const equivalentPath = document.querySelector('.autocal-equivalence-line');
              const live = document.querySelector('.autocal-live-layer');
              const currentBand = document.querySelector('[data-autocal-current-band]');
              const chart = document.getElementById('autocalReferenceChart')?.getBoundingClientRect();
              const rail = document.querySelector('.autocal-live-strip')?.getBoundingClientRect();
              return {
                svg: document.querySelector('.autocal-reference-svg') !== null,
                empty: document.querySelector('#autocalReferenceChart .chart-empty') !== null,
                petrolPath: petrolPath?.getAttribute('d') ?? '',
                gasPath: gasPath?.getAttribute('d') ?? '',
                equivalentPath: equivalentPath?.getAttribute('d') ?? '',
                petrolPoints: document.querySelectorAll('.autocal-reference-point.petrol').length,
                gasPoints: document.querySelectorAll('.autocal-reference-point.gas').length,
                equivalentPoints: document.querySelectorAll('.autocal-equivalence-point').length,
                liveVisible: !!live && live.getAttribute('display') !== 'none',
                currentBandVisible: !!currentBand && currentBand.getAttribute('display') !== 'none',
                currentBandHeight: Number(currentBand?.getAttribute('height') ?? 0),
                currentBandY: Number(currentBand?.getAttribute('y') ?? -1),
                count: document.getElementById('autocalReferenceCount')?.textContent ?? '',
                chartHeight: chart?.height ?? 0,
                chartWidth: chart?.width ?? 0,
                railBottom: rail?.bottom ?? 0,
                viewportHeight: window.innerHeight
              };
            })())
            """.trimIndent(),
        )

    private fun curvePositiveDom(scenario: ActivityScenario<MainActivity>): JSONObject =
        evalJson(
            scenario,
            """
            JSON.stringify((() => {
              const root = document.querySelector('[data-screen="curve"]');
              const body = root?.innerText ?? '';
              const actual = document.querySelector('#curveChart .curve-line.actual');
              return {
                active: root?.classList.contains('active') === true,
                source: document.getElementById('curveSourceStatus')?.textContent ?? null,
                actualPath: actual?.getAttribute('d') ?? '',
                pointCount: document.querySelectorAll('#curveChart [data-curve-index]').length,
                bodyHasNaN: /\\bNaN\\b/.test(body),
                bodyHasUndefined: /\\bundefined\\b/i.test(body)
              };
            })())
            """.trimIndent(),
        )

    private fun globalRouteDom(
        scenario: ActivityScenario<MainActivity>,
        route: String,
    ): JSONObject = evalJson(
        scenario,
        """
        JSON.stringify((() => {
          const active = document.querySelector('[data-screen="$route"]')?.classList.contains('active') === true;
          const body = document.querySelector('[data-screen="$route"]')?.innerText ?? '';
          return {
            active,
            bodyHasNaN: /\\bNaN\\b/.test(body),
            bodyHasUndefined: /\\bundefined\\b/i.test(body),
            learningLive: document.getElementById('learningLiveLabel')?.textContent ?? null,
            learningCoverage: document.getElementById('learningCoverageSummary')?.textContent ?? null,
            mapLive: document.getElementById('mapLiveLabel')?.textContent ?? null,
            mapCell: document.getElementById('mapLiveCell')?.textContent ?? null,
            mapSource: document.getElementById('mapSourceStatus')?.textContent ?? null,
            mapGridText: document.getElementById('mapGrid')?.innerText ?? null,
            curveSource: document.getElementById('curveSourceStatus')?.textContent ?? null,
            curveReading: document.querySelector('[data-screen="curve"]')?.classList.contains('is-reading') === true,
            obdStatus: document.getElementById('obdStatusPill')?.textContent ?? null,
            obdStft: document.getElementById('obdStft')?.textContent ?? null,
            obdRpm: document.getElementById('obdRpm')?.textContent ?? null,
            obdConnection: document.getElementById('obdConnection')?.textContent ?? null,
            obdDecision: document.getElementById('obdLiveDecision')?.textContent ?? null
          };
        })())
        """.trimIndent(),
    )

    @Test
    fun learningFreshMp48ContextRender() {
        val scenario = launch()
        try {
            val fixture = liveFixture()
            activateRoute(scenario, "learning")
            injectFresh(scenario, fixture, settleMs = 900L)
            val dom = globalRouteDom(scenario, "learning")
            saveEvidence("learning-fresh-context", dom, scenario)
            assertTrue("Learning route must activate", dom.getBoolean("active"))
            assertTrue("Learning must receive live RPM through PresentSnapshot", dom.optString("learningLive").contains("869 RPM"))
            assertTrue("Learning must receive live Petrol Injection through PresentSnapshot", dom.optString("learningLive").contains("4,54 ms"))
            assertTrue("Learning must never render NaN", !dom.getBoolean("bodyHasNaN"))
            assertTrue("Learning must never render undefined", !dom.getBoolean("bodyHasUndefined"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun mapFreshMp48ContextRender() {
        val scenario = launch()
        try {
            val fixture = liveFixture()
            activateRoute(scenario, "map", settleMs = 500L)
            injectFresh(scenario, fixture, settleMs = 900L)
            val dom = globalRouteDom(scenario, "map")
            saveEvidence("map-fresh-context", dom, scenario)
            assertTrue("Map route must activate", dom.getBoolean("active"))
            assertTrue("Map must receive live RPM through PresentSnapshot", dom.optString("mapLive").contains("869 RPM"))
            assertTrue("Map must receive live Petrol Injection through PresentSnapshot", dom.optString("mapLive").contains("4,54 ms"))
            assertTrue("Map live cell must stay explicit instead of fabricating undefined coordinates", !dom.optString("mapCell").contains("undefined", ignoreCase = true))
            assertEquals("Offline ECU read must settle as not confirmed", "Mapa não confirmado", dom.optString("mapSource"))
            assertTrue("Failed map read must clear the stale loading spinner", !dom.optString("mapGridText").contains("Lendo Mapa K da ECU"))
            assertTrue("Map must never render NaN", !dom.getBoolean("bodyHasNaN"))
            assertTrue("Map must never render undefined", !dom.getBoolean("bodyHasUndefined"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun curveOfflineDoesNotFabricateEcuRead() {
        val scenario = launch()
        try {
            activateRoute(scenario, "curve", settleMs = 850L)
            waitFor(5_000L) {
                globalRouteDom(scenario, "curve").optString("curveSource") == "Curva não confirmada"
            }
            val dom = globalRouteDom(scenario, "curve")
            saveEvidence("curve-offline-honest", dom, scenario)
            assertTrue("Curve route must activate", dom.getBoolean("active"))
            assertEquals("Offline Curve read must settle honestly", "Curva não confirmada", dom.optString("curveSource"))
            assertTrue("Offline Curve must leave the reading state", !dom.getBoolean("curveReading"))
            assertTrue("Emulator without ECU must not claim a confirmed curve", !dom.optString("curveSource").contains("ECU confirmada", ignoreCase = true))
            assertTrue("Curve must never render NaN", !dom.getBoolean("bodyHasNaN"))
            assertTrue("Curve must never render undefined", !dom.getBoolean("bodyHasUndefined"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun curveOriginalLognovoRendersThirtyDecodedPoints() {
        val scenario = launch()
        try {
            activateRoute(scenario, "curve", settleMs = 850L)
            val provenance = installOriginalKFactorCurve(scenario)
            val dom = curvePositiveDom(scenario)
            saveEvidence("curve-original-lognovo", dom, scenario, provenance)
            assertTrue("Curve route must activate", dom.getBoolean("active"))
            assertTrue(
                "Original captured ACK replay must drive the production confirmed-curve state",
                dom.optString("source").contains("ECU confirmada", ignoreCase = true),
            )
            assertEquals("Original Curve K replay must expose all 30 points", 30, dom.getInt("pointCount"))
            assertTrue("Original Curve K line must be drawable", dom.getString("actualPath").length > 20)
            assertTrue("Curve must never render NaN", !dom.getBoolean("bodyHasNaN"))
            assertTrue("Curve must never render undefined", !dom.getBoolean("bodyHasUndefined"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun obdOfflineIsHonest() {
        val scenario = launch()
        try {
            activateRoute(scenario, "obd", settleMs = 850L)
            val dom = globalRouteDom(scenario, "obd")
            saveEvidence("obd-offline-honest", dom, scenario)
            assertTrue("OBD route must activate", dom.getBoolean("active"))
            assertEquals("OBD offline", dom.optString("obdStatus"))
            assertEquals("—", dom.optString("obdStft"))
            assertEquals("Offline MP48/OBD must not masquerade as measured zero RPM", "—", dom.optString("obdRpm"))
            assertTrue("OBD connection copy must remain disconnected without ELM hardware", dom.optString("obdConnection").contains("desconectado", ignoreCase = true))
            assertTrue("OBD must never render NaN", !dom.getBoolean("bodyHasNaN"))
            assertTrue("OBD must never render undefined", !dom.getBoolean("bodyHasUndefined"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun sessionChangeInvalidatesOldTelemetry() {
        val scenario = launch()
        try {
            val fixture = liveFixture()
            activateRoute(scenario, "dashboard", settleMs = 350L)
            injectFresh(scenario, fixture, settleMs = 700L)
            val before = dashboardDom(scenario)
            assertTrue("Precondition: Dashboard route must be active", before.getBoolean("route"))
            assertTrue("Precondition: first session must have live petrol injection", before.optString("petrol") != "—")

            scenario.onActivity { activity ->
                val service = activity.serviceOrNull() ?: error("service unavailable")
                service.telemetryStore.beginSession(9002L)
            }
            SystemClock.sleep(1_000L)

            val after = dashboardDom(scenario)
            assertTrue("Session invalidation must not navigate away from Dashboard", after.getBoolean("route"))
            saveEvidence("dashboard-session-invalidated", after, scenario)
            assertEquals("New session must invalidate old Petrol Injection", "—", after.optString("petrol"))
            assertEquals("New session must invalidate old MAP", "—", after.optString("map"))
            assertEquals("New session must invalidate old LEVELS RAW", "—", after.optString("levelsRaw"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun sessionReconnectRecoversFreshTelemetry() {
        val scenario = launch()
        try {
            val fixture = liveFixture()
            activateRoute(scenario, "dashboard", settleMs = 350L)

            injectFresh(scenario, fixture, settleMs = 650L, sessionId = 9101L)
            val first = dashboardDom(scenario)
            assertTrue("Initial session must expose Petrol Injection", first.optString("petrol") != "—")
            assertTrue("Initial session must expose MAP", first.optString("map") != "—")

            scenario.onActivity { activity ->
                val service = activity.serviceOrNull() ?: error("service unavailable")
                service.telemetryStore.beginSession(9102L)
                activity.refreshWebUi()
            }
            SystemClock.sleep(650L)
            val invalidated = dashboardDom(scenario)
            assertEquals("Session switch must invalidate old Petrol Injection", "—", invalidated.optString("petrol"))
            assertEquals("Session switch must invalidate old MAP", "—", invalidated.optString("map"))

            injectFresh(scenario, fixture, settleMs = 700L, sessionId = 9103L)
            val recovered = dashboardDom(scenario)
            saveEvidence("dashboard-session-recovered", recovered, scenario)
            assertTrue("Recovered session must keep Dashboard active", recovered.getBoolean("route"))
            assertTrue("Recovered session must expose fresh Petrol Injection again", recovered.optString("petrol") != "—")
            assertTrue("Recovered session must expose fresh MAP again", recovered.optString("map") != "—")
            assertTrue("Recovered session must expose fresh RPM again", recovered.optString("rpm") != "—")
        } finally {
            scenario.close()
        }
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
            assertEquals("Invalid telemetry must not masquerade as zero petrol injection", "—", dom.optString("petrol"))
            assertEquals("Invalid telemetry must not masquerade as zero MAP", "—", dom.optString("map"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun historicalPreLevelsDashboardOmitsRawLevels() {
        val scenario = launch()
        try {
            val fixture = liveFixture()
            injectFresh(scenario, fixture)
            val dom = dashboardDom(scenario)
            saveEvidence("dashboard-historical-pre-levels", dom, scenario)
            assertTrue("Historical Dashboard route must render", dom.getBoolean("route"))
            assertTrue("Historical pre-fix Dashboard must omit LEVELS RAW label", !dom.getBoolean("hasLevelsRawLabel"))
            assertTrue("Historical pre-fix Dashboard body must omit LEVELS RAW", !dom.getBoolean("bodyHasLevelsRaw"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun autocalFreshTelemetryControl() {
        val scenario = launch()
        try {
            val fixture = liveFixture()
            activateAutocal(scenario)
            injectFresh(scenario, fixture, settleMs = 700L)
            val dom = autocalDom(scenario)
            saveEvidence("autocal-fresh-control", dom, scenario)
            assertTrue("AutoCal route must activate", dom.getBoolean("active"))
            assertTrue("LEVELS belongs to Dashboard/AGORA, not AutoCal", !dom.getBoolean("hasLevelsMetric"))
            assertTrue("AutoCal live RPM must be present", dom.optString("rpm") != "—")
            assertTrue("AutoCal live Petrol Injection must be present", dom.optString("petrol") != "—")
            assertTrue("AutoCal live MAP must be present", dom.optString("map") != "—")
            val geometry = dom.getJSONObject("geometry")
            assertTrue("Acquisition chart must dominate vertically", geometry.getDouble("chartHeight") >= 300.0)
            assertTrue("Acquisition chart must use the available horizontal canvas", geometry.getDouble("chartWidth") >= 760.0)
            assertTrue("Live telemetry rail must sit below the chart", geometry.getDouble("liveTop") >= geometry.getDouble("chartBottom"))
            assertTrue("Live telemetry rail must remain visible without scrolling", geometry.getDouble("liveBottom") <= geometry.getDouble("viewportHeight"))
            assertTrue("Operational hero must follow the instrument surface", geometry.getDouble("heroTop") >= geometry.getDouble("liveBottom"))
            assertTrue("Operational hero must stay compact relative to chart", geometry.getDouble("heroHeight") < geometry.getDouble("chartHeight"))
            assertTrue("Point inspector must sit below the plot instead of overlaying it", geometry.getDouble("inspectorTop") >= geometry.getDouble("chartBottom"))
            assertTrue("Live telemetry rail must follow the point inspector", geometry.getDouble("liveTop") >= geometry.getDouble("inspectorBottom"))
            assertTrue("Point inspector may use chart width without narrowing the plot", geometry.getDouble("inspectorWidth") >= geometry.getDouble("chartWidth") * 0.70)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun autocalReferenceCurvesRenderFixture() {
        val scenario = launch()
        try {
            val live = liveFixture()
            val provenance = installOriginalAutoCalReferenceFixture(scenario)
            activateAutocal(scenario)
            injectFresh(scenario, live, settleMs = 850L)
            val dom = autocalReferenceDom(scenario)
            saveEvidence("autocal-reference-curves", dom, scenario, provenance)
            assertTrue("Production projection must render an SVG reference chart", dom.getBoolean("svg"))
            assertTrue("Reference chart must not fall back to empty state", !dom.getBoolean("empty"))
            assertTrue("Gasoline reference line must have a drawable path", dom.getString("petrolPath").length > 20)
            assertTrue("GNV reference line must have a drawable path", dom.getString("gasPath").length > 20)
            assertEquals("Complete render fixture must expose all 30 gasoline points", 30, dom.getInt("petrolPoints"))
            assertEquals("Complete render fixture must expose all 30 GNV points", 30, dom.getInt("gasPoints"))
            assertTrue("Fresh AGORA cursor must remain on the same chart", dom.getBoolean("liveVisible"))
            assertTrue("CurrentBand must render from original MNFLD_PRESS_THD plus live MAP", dom.getBoolean("currentBandVisible"))
            assertTrue("CurrentBand must have positive rendered height", dom.getDouble("currentBandHeight") > 0.0)
            assertTrue("CurrentBand must stay inside the SVG plot", dom.getDouble("currentBandY") >= 0.0)
            assertTrue("Reference chart remains dominant", dom.getDouble("chartHeight") >= 300.0)
            assertTrue("Reference chart remains wide", dom.getDouble("chartWidth") >= 760.0)
            assertTrue("Live rail remains above the fold", dom.getDouble("railBottom") <= dom.getDouble("viewportHeight"))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun autocalShiftedEquivalenceRendersHorizontalProjection() {
        val scenario = launch()
        try {
            val live = liveFixture()
            val provenance = installTestOnlyAutoCalReferenceFixture(
                scenario = scenario,
                fixtureName = "autocal_snapshot_shifted_equivalence.json",
            )
            activateAutocal(scenario)
            injectFresh(scenario, live, settleMs = 850L)
            val dom = autocalReferenceDom(scenario)
            saveEvidence("autocal-equivalence-shifted", dom, scenario, provenance)
            assertTrue("Shifted fixture must render production reference chart", dom.getBoolean("svg"))
            assertTrue("Shifted fixture must keep gasoline reference", dom.getString("petrolPath").length > 20)
            assertTrue("Shifted fixture must keep GNV reference", dom.getString("gasPath").length > 20)
            assertTrue("Horizontal same-pressure equivalence must render a drawable path", dom.getString("equivalentPath").length > 20)
            assertTrue("Horizontal same-pressure equivalence must expose multiple points", dom.getInt("equivalentPoints") >= 20)
            assertTrue("AGORA remains a live overlay, not acquired evidence", dom.getBoolean("liveVisible"))
            assertTrue("Live rail remains above the fold", dom.getDouble("railBottom") <= dom.getDouble("viewportHeight"))
        } finally {
            scenario.close()
        }
    }

    private fun saveEvidence(
        name: String,
        dom: JSONObject,
        scenario: ActivityScenario<MainActivity>,
        fixtureProvenance: JSONObject? = null,
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
        if (fixtureProvenance != null) {
            receipt.put("fixtureProvenance", fixtureProvenance)
        }
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
