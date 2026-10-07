package com.omegas.prohub

import android.graphics.Bitmap
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omegas.prohub.autocal.AutoCalAcquisition
import com.omegas.prohub.autocal.AutoCalReadObservation
import com.omegas.prohub.autocal.AutoCalSnapshotBuilder
import com.omegas.prohub.autocal.AutoCalSnapshotSource
import com.omegas.prohub.autocal.EcuPetrolReference
import com.omegas.prohub.autocal.EquivalencePhases
import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.NativeAutoCalAcquisitionEpoch
import com.omegas.prohub.autocal.StallWatch
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.service.TelemetryForegroundService
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

/**
 * Evidência de render do Refino (classe 4: o APK real, WebView real, 1280×720, com print).
 *
 * Cada fase do piloto é conduzida pelos objetos reais do serviço (livro de pontos, diário, piloto,
 * detector de apagão) alimentados pelo REPLAY das sessões reais do proprietário (fixtures/autocal/real).
 * Onde o corpus não tem a situação (estável, restaurar trecho, apagão completo), o recibo diz
 * SYNTHETIC_NON_SCIENTIFIC. Nada aqui valida ECU ou veículo.
 */
@RunWith(AndroidJUnit4::class)
class RefinoRenderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // ------------------------------------------------------------------ infraestrutura

    private fun launch(): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(12_000L) { var ready = false; scenario.onActivity { ready = it.serviceOrNull() != null }; ready }
        SystemClock.sleep(2_500L)
        scenario.onActivity { it.serviceOrNull()!!.refinementFrozenForRender = true }
        return scenario
    }

    private fun service(scenario: ActivityScenario<MainActivity>): TelemetryForegroundService {
        var service: TelemetryForegroundService? = null
        scenario.onActivity { service = it.serviceOrNull() }
        return checkNotNull(service) { "service unavailable" }
    }

    /**
     * O empacotamento do APK de teste tira o ".gz" do nome do asset (a listagem mostrou
     * "automatch_....json"); por isso tenta os nomes com e sem ".gz" e decide por gzip pelo
     * conteúdo (bytes 1f 8b), não pelo nome.
     */
    private fun corpus(name: String): JSONObject {
        val assets = instrumentation.context.assets
        val candidates = listOf("$name.json.gz", "$name.json", "real/$name.json.gz", "real/$name.json")
        val raw = candidates.firstNotNullOfOrNull { path -> runCatching { assets.open(path).use { it.readBytes() } }.getOrNull() }
            ?: error("corpus real ausente no APK de teste: $candidates; raiz=${assets.list("")?.sorted()}; real=${assets.list("real")?.sorted()}")
        val gzipped = raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()
        val bytes = if (gzipped) GZIPInputStream(raw.inputStream()).use { it.readBytes() } else raw
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private fun feedLedger(service: TelemetryForegroundService, root: JSONObject, onlyFuel: String? = null) {
        val telemetry = root.getJSONArray("telemetry")
        for (i in 0 until telemetry.length()) {
            val f = telemetry.getJSONObject(i)
            val fuel = f.optString("fuel", "")
            if (onlyFuel != null && fuel != onlyFuel) continue
            service.equivalence.accept(
                EquivalenceLedger.Frame(
                    f.getLong("t"), fuel, f.optDouble("rpm", 0.0), f.optDouble("load_bar", 0.0),
                    f.optDouble("petrol_ms", 0.0), f.optDouble("gas_ms_diagnostic", 0.0),
                ),
            )
        }
    }

    private fun feedStalls(service: TelemetryForegroundService, root: JSONObject): Long {
        val telemetry = root.getJSONArray("telemetry")
        var last = 0L
        for (i in 0 until telemetry.length()) {
            val f = telemetry.getJSONObject(i)
            last = f.getLong("t")
            service.stallWatch.accept(
                StallWatch.Frame(last, f.optString("fuel", ""), f.optDouble("rpm", 0.0), f.optDouble("load_bar", 0.0), f.optDouble("petrol_ms", 0.0)),
            )
        }
        return last
    }

    /** Frames sintéticos (rotulados no recibo): condução estável por faixa, para estados que o corpus não tem. */
    private val cells = listOf(
        Triple(2_000.0, 0.40, 3.6), Triple(2_200.0, 0.50, 5.0), Triple(2_500.0, 0.60, 6.5),
        Triple(2_800.0, 0.70, 8.0), Triple(3_200.0, 0.85, 10.0),
    )
    private var synthT = 5_000_000_000_000L

    private fun synth(service: TelemetryForegroundService, fuel: String, ratio: Double = 1.0) {
        cells.forEach { (rpm, map, ms) ->
            repeat(10) {
                service.equivalence.accept(EquivalenceLedger.Frame(synthT, fuel, rpm, map, ms * ratio))
                synthT += 280
            }
            synthT += 5_000
        }
    }

    private fun payloadFor(field: AutoCalProtocol.Field, values: IntArray): ByteArray = when (field.encoding) {
        AutoCalProtocol.Encoding.U8 -> ByteArray(values.size) { values[it].toByte() }
        else -> ByteArray(values.size * 2).also { payload ->
            values.forEachIndexed { index, value ->
                val raw = value and 0xFFFF
                payload[index * 2] = (raw and 0xFF).toByte()
                payload[index * 2 + 1] = ((raw ushr 8) and 0xFF).toByte()
            }
        }
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun getPrivateField(target: Any, name: String): Any? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }

    /** Publica o snapshot nativo real do corpus pelo decodificador de produção e libera a época de aquisição. */
    private fun publishSnapshot(service: TelemetryForegroundService, root: JSONObject, sequence: Int, sessionId: Long = 9001L): JSONObject {
        val snapshots = root.getJSONArray("snapshots")
        var reduced: JSONObject? = null
        for (i in 0 until snapshots.length()) if (snapshots.getJSONObject(i).getInt("sequence") == sequence) reduced = snapshots.getJSONObject(i)
        val source = checkNotNull(reduced) { "snapshot $sequence ausente" }
        val fields = source.getJSONArray("fields")
        val now = System.currentTimeMillis()
        val observations = ArrayList<AutoCalReadObservation>()
        val expected = ArrayList<AutoCalProtocol.Field>()
        for (i in 0 until fields.length()) {
            val f = fields.getJSONObject(i)
            val descriptor = AutoCalProtocol.READ_ONLY_FIELDS.firstOrNull { it.key == f.getString("key") } ?: continue
            if (descriptor.encoding == AutoCalProtocol.Encoding.U8_OR_U16_LE || f.optString("status") != "VALID") continue
            val raw = f.getJSONArray("rawValues")
            observations += AutoCalReadObservation(descriptor, Mp48Protocol.STATUS_ACK, payloadFor(descriptor, IntArray(raw.length()) { raw.getInt(it) }), now)
            expected += descriptor
        }
        // O fixture reduzido omite MAX_AUTOMATCH, embora o grupo de coerência o exija.
        // O máximo 3 já é parâmetro explícito destes cenários; completar o monitor não inventa medidas.
        if (expected.none { it.identity == AutoCalProtocol.MAX_AUTOMATCH.identity }) {
            expected += AutoCalProtocol.MAX_AUTOMATCH
            observations += AutoCalReadObservation(AutoCalProtocol.MAX_AUTOMATCH, Mp48Protocol.STATUS_ACK,
                payloadFor(AutoCalProtocol.MAX_AUTOMATCH, intArrayOf(3)), now)
        }
        val snapshot = AutoCalSnapshotBuilder.build(observations, expected, "AUTOCAL-$sessionId-REFINO", AutoCalSnapshotSource.REPLAY, now, now)
        check(snapshot.temporalCoherent) { "Replay decodificado deve manter grupos coerentes" }
        val decorated = snapshot.toJson()
            .put("available", true)
            .put("nativeAutoCal", true)
            .put("autoCalEnabled", 1)
            .put("maxAutomatch", 3)
            .put("nativeMaturityEvents", JSONArray())
            .put("nativeCorrelationState", JSONObject().put("correlatedBands", JSONArray()).put("retryableBands", JSONArray()))
        val state = JSONObject().put("state", "READY").put("message", "Replay real do corpus").put("sessionId", sessionId)
            .put("autoCalEnabled", 1).put("autoMatchCount", 0).put("appAutomaticWrite", false)
        setPrivateField(service.nativeAutoCal, "latestSnapshot", decorated)
        setPrivateField(service.nativeAutoCal, "state", state)
        // Época de aquisição coerente com o snapshot publicado (sem isso a projeção mascara a referência).
        val epoch = getPrivateField(service.nativeAutoCal, "acquisitionEpoch") as NativeAutoCalAcquisitionEpoch
        epoch.reset(sessionId)
        epoch.nativeCounter(sessionId, 0)
        epoch.acquisitionGroup(sessionId, 0, IntArray(18) { if (it < 6) 5 else 0 }, IntArray(18) { if (it < 6) 5 else 0 })
        epoch.referenceGroup(sessionId, 0)
        return decorated
    }

    private fun observe(service: TelemetryForegroundService, snapshot: JSONObject, count: Int?, max: Int = 3): JSONObject {
        val acquisition = AutoCalAcquisition.fromSnapshot(snapshot)
        // O serviço está congelado para render: alimenta também o cérebro real consumido por refinoState.
        // Replay não tem uma ECU conectada; não inventa uma transação nem invalida dados pela conexão simulada.
        val brain = service.equivalenceRuntime.evaluate(service.equivalence, service.equivalencePhases,
            snapshot, acquisition, false, service.refinementJournal::pointGainScale)
        check(brain != null) { "Replay precisa conter Curva K coerente: ${snapshot}" }
        return service.equivalencePhases.observe(
            ecuOnline = true,
            monitor = JSONObject().put("autoMatchCount", count ?: JSONObject.NULL).put("maxAutomatch", max).put("autoCalEnabled", 1),
            acquisition = acquisition,
            index = service.equivalence.index(),
            journal = service.refinementJournal.json(),
            restoreCount = service.refinementJournal.restorePoints().length(),
        )
    }

    private fun openRefino(scenario: ActivityScenario<MainActivity>) {
        evalRaw(scenario, "document.querySelector('[data-route=\"refino\"]')?.click(); 'ok';")
        SystemClock.sleep(700L)
        refreshRefino(scenario)
    }

    private fun refreshRefino(scenario: ActivityScenario<MainActivity>) {
        val expectedPhase = service(scenario).equivalencePhases.json().optString("phase")
        val expectedAvailable = service(scenario).equivalenceRuntime.last() != null
        // A ponte mantém memo assíncrono; espera a versão nova em vez de fotografar o cache do lançamento.
        waitFor(12_000L) {
            val value = evalJson(scenario, "OmegasAutoCal.getEquivalence()")
            value.optJSONObject("autopilot")?.optString("phase") == expectedPhase &&
                value.optJSONObject("equivalence")?.optBoolean("available", false) == expectedAvailable
        }
        evalRaw(scenario, "window.OmegasApp?.refino?.refresh?.(true, true); 'ok';")
        SystemClock.sleep(900L)
    }

    private fun refinoDom(scenario: ActivityScenario<MainActivity>): JSONObject = evalJson(
        scenario,
        """
        JSON.stringify((() => {
          const q = s => document.querySelector(s);
          const screen = q('[data-screen="refino"]');
          const primary = q('[data-refino-primary]');
          const legend = q('#refinoLegend');
          const legendRect = legend ? legend.getBoundingClientRect() : null;
          const live = q('[data-refino-live]');
          const liveCircle = live ? live.querySelector('circle') : null;
          const stalls = q('#refinoStalls');
          const body = screen ? screen.innerText : '';
          return {
            active: !!screen && screen.classList.contains('active'),
            canonical: JSON.parse(OmegasAutoCal.getEquivalence()),
            chip: q('#refinoPhaseChip')?.textContent ?? null,
            headline: q('#refinoHeadline')?.textContent ?? null,
            headlineVisible: (() => {
              const e = q('#refinoHeadline'); const r = e?.getBoundingClientRect();
              return !!r && r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== 'hidden';
            })(),
            headlineFont: Number.parseFloat(getComputedStyle(q('#refinoHeadline')).fontSize),
            nextVisible: (() => {
              const e = q('#refinoNext'); const r = e?.getBoundingClientRect();
              return !!r && r.width > 0 && r.height > 0 && r.bottom <= window.innerHeight;
            })(),
            next: q('#refinoNext')?.textContent ?? null,
            ratio: q('#refinoRatio')?.textContent ?? null,
            ecuPoints: q('#refinoEcuPoints')?.textContent ?? null,
            ourPoints: q('#refinoDetailCounts')?.textContent ?? null,
            steps: [...document.querySelectorAll('#refinoSteps li')].map(li => li.dataset.state + (li.dataset.problem ? '!' : '')),
            primaryHidden: primary ? primary.hidden : null,
            primaryText: primary ? primary.textContent : null,
            primaryKind: primary ? primary.dataset.kind ?? null : null,
            stallsVisible: !!stalls && !stalls.hidden,
            stallsText: stalls ? stalls.innerText : '',
            journalText: q('#refinoJournal')?.innerText ?? '',
            techText: q('#refinoProposals')?.textContent ?? '',
            ourSquares: document.querySelectorAll('#refinoChart .chart-between.collected').length,
            ecuDots: document.querySelectorAll('#refinoChart .autocal-acquired-point').length,
            stallMarks: document.querySelectorAll('.refino-stall-mark').length,
            referenceLines: document.querySelectorAll('#refinoChart .autocal-reference-line').length,
            svg: !!q('#refinoChart .autocal-reference-svg'),
            liveVisible: !!live && !live.hasAttribute('hidden'),
            liveCx: liveCircle ? Number(liveCircle.getAttribute('cx')) : null,
            liveCy: liveCircle ? Number(liveCircle.getAttribute('cy')) : null,
            legendInViewport: !!legendRect && legendRect.width > 0 && legendRect.bottom <= window.innerHeight,
            legendBottom: legendRect ? legendRect.bottom : null,
            viewportHeight: window.innerHeight,
            screenScrollWidth: screen ? screen.scrollWidth : 0,
            screenClientWidth: screen ? screen.clientWidth : 0,
            horizontalOverflow: [...screen.querySelectorAll('*')].filter(e => {
              const r = e.getBoundingClientRect();
              const style = getComputedStyle(e);
              if (!r.width || !r.height || style.visibility === 'hidden') return false;
              return ((style.overflowX === 'auto' || style.overflowX === 'scroll') && e.scrollWidth > e.clientWidth + 1)
                || r.right > window.innerWidth + 1 || r.left < -1;
            }).map(e => ({ tag: e.tagName, id: e.id, classes: e.className?.baseVal ?? e.className,
              width: e.clientWidth, scrollWidth: e.scrollWidth })),
            documentOverflow: document.documentElement.scrollWidth > window.innerWidth + 1,
            bodyHasNaN: /\bNaN\b/.test(body),
            bodyHasUndefined: /\bundefined\b/i.test(body)
          };
        })())
        """.trimIndent(),
    )

    private fun assertClean(dom: JSONObject) {
        assertTrue("Refino precisa estar ativo", dom.getBoolean("active"))
        assertTrue("documento não pode ultrapassar viewport", !dom.getBoolean("documentOverflow"))
        assertEquals("nenhuma rolagem interna ou conteúdo fora da tela: ${dom.optJSONArray("horizontalOverflow")}", 0, dom.getJSONArray("horizontalOverflow").length())
        assertTrue("sem NaN na tela", !dom.getBoolean("bodyHasNaN"))
        assertTrue("sem undefined na tela", !dom.getBoolean("bodyHasUndefined"))
        assertTrue(
            "sem rolagem horizontal (scroll=${dom.getInt("screenScrollWidth")} client=${dom.getInt("screenClientWidth")})",
            dom.getInt("screenScrollWidth") <= dom.getInt("screenClientWidth") + 1,
        )
    }

    private fun provenance(kind: String, corpus: String, note: String) = JSONObject()
        .put("classification", kind)
        .put("corpus", corpus)
        .put("note", note)
        .put("physicalValidationClaimed", false)
        .put("monitorMaxAutomatch", "SYNTHETIC_SCENARIO_VALUE_3; campo omitido do fixture reduzido")

    // ------------------------------------------------------------------ cenários (uma fase do piloto por print)



    @Test
    fun startWaitsForStopThatIsSealingJournalDelivery() {
        val scenario = launch()
        var recorder: com.omegas.prohub.diagnostics.SessionRecorder? = null
        try {
            val service = service(scenario)
            recorder = service.sessionRecorder
            assertTrue(recorder.start("EVIDENCE_START_STOP_RACE").optBoolean("ok"))
            synth(service, "GASOLINA")
            synth(service, "GNV", ratio = 1.12)
            val journalLock = TelemetryForegroundService::class.java.getDeclaredField("journalRecordingLock").apply {
                isAccessible = true
            }.get(service)
            val axis = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
            val transition = Thread {
                service.refinementJournal.recordCurveWrite(
                    IntArray(30) { 16384 }, IntArray(30) { 17000 }, axis,
                    service.equivalence.index(), "SIMULATED_WRITE_DURING_START_STOP",
                )
            }
            val stopped = AtomicBoolean(false)
            val restarted = AtomicBoolean(false)
            val stop = Thread { service.stopSessionRecording("EVIDENCE_STOP"); stopped.set(true) }
            val start = Thread { service.startSessionRecording("EVIDENCE_RESTART"); restarted.set(true) }
            synchronized(journalLock) {
                transition.start()
                waitFor(1_000L) { transition.state == Thread.State.BLOCKED }
                stop.start()
                waitFor(1_000L) { stop.state == Thread.State.BLOCKED }
                start.start()
                SystemClock.sleep(250L)
                assertTrue("novo início não pode atravessar uma parada que ainda sela o Journal", !restarted.get())
            }
            transition.join(5_000L)
            stop.join(5_000L)
            start.join(5_000L)
            assertTrue(stopped.get())
            assertTrue(restarted.get())
        } finally { recorder?.stop("EVIDENCE_CLEANUP"); scenario.close() }
    }

    @Test
    fun stopSessionWaitsForTransitionAlreadyInProgress() {
        val scenario = launch()
        var recorder: com.omegas.prohub.diagnostics.SessionRecorder? = null
        try {
            val service = service(scenario)
            recorder = service.sessionRecorder
            assertTrue(recorder.start("EVIDENCE_STOP_RACE").optBoolean("ok"))
            synth(service, "GASOLINA")
            synth(service, "GNV", ratio = 1.12)
            val journalLock = TelemetryForegroundService::class.java.getDeclaredField("journalRecordingLock").apply {
                isAccessible = true
            }.get(service)
            val axis = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
            val enteredJournal = CountDownLatch(1)
            val transition = Thread {
                enteredJournal.countDown()
                service.refinementJournal.recordCurveWrite(
                    IntArray(30) { 16384 }, IntArray(30) { 17000 }, axis,
                    service.equivalence.index(), "SIMULATED_WRITE_DURING_STOP",
                )
            }
            val stopFinished = AtomicBoolean(false)
            val stop = Thread {
                service.stopSessionRecording("EVIDENCE_STOP")
                stopFinished.set(true)
            }
            synchronized(journalLock) {
                transition.start()
                assertTrue("transição deve iniciar", enteredJournal.await(1, TimeUnit.SECONDS))
                waitFor(1_000L) { transition.state == Thread.State.BLOCKED }
                stop.start()
                SystemClock.sleep(250L)
                assertTrue("parada não pode fechar a sessão enquanto a transição anterior espera", !stopFinished.get())
            }
            transition.join(5_000L)
            stop.join(5_000L)
            assertTrue("parada deve completar após entregar a transição", stopFinished.get())
            val stopped = recorder.statusObject()
            val directory = File(stopped.getString("directory"))
            val events = directory.listFiles()?.filter { it.name.startsWith("events_") && it.name.endsWith(".jsonl") }
                .orEmpty().flatMap { it.readLines() }.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            assertTrue("decisão iniciada antes da parada deve sobreviver", events.any {
                it.optString("type") == "refinement_decision" &&
                    it.optJSONObject("data")?.optString("reasonCode") == "MANUAL_WRITE_CONFIRMED"
            })
        } finally { recorder?.stop("EVIDENCE_CLEANUP"); scenario.close() }
    }

    @Test
    fun refinoJournalTransitionsReachSessionBeforeNextTick() {
        val scenario = launch()
        var recorder: com.omegas.prohub.diagnostics.SessionRecorder? = null
        try {
            val service = service(scenario)
            recorder = service.sessionRecorder
            assertTrue(recorder.start("EVIDENCE_BETWEEN_TICKS").optBoolean("ok"))
            synth(service, "GASOLINA")
            synth(service, "GNV", ratio = 1.12)
            val axis = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
            service.refinementJournal.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { 17000 },
                axis, service.equivalence.index(), "SIMULATED_CONFIRMED_WRITE_ONE")
            service.refinementJournal.interrupt("NATIVE_AUTOMATCH")
            service.refinementJournal.recordCurveWrite(IntArray(30) { 17000 }, IntArray(30) { 18000 },
                axis, service.equivalence.index(), "SIMULATED_CONFIRMED_WRITE_TWO")
            service.equivalence.resetGas("SIMULATED_WRITE")
            synth(service, "GNV")
            service.refinementJournal.evaluate(service.equivalence.index())
            // Nenhum registrador/healthTick é invocado: parar antes do tick não pode perder decisões.
            val stopped = recorder.stop("EVIDENCE_DONE_BEFORE_TICK")
            val directory = File(stopped.getString("directory"))
            val events = directory.listFiles()?.filter { it.name.startsWith("events_") && it.name.endsWith(".jsonl") }
                .orEmpty().flatMap { it.readLines() }.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            val decisions = events.filter { it.optString("type") in setOf("refinement_decision", "refinement_diagnostic") &&
                it.optJSONObject("data")?.optString("component") == "JOURNAL" }
            val verdicts = events.filter { it.optString("type") == "refinement_verdict" }
            val reasons = decisions.map { it.getJSONObject("data").getString("reasonCode") }
            val md = File(directory, "RESUMO.md").readText()
            service.equivalencePhases.observe(true,
                JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1),
                null, service.equivalence.index(), service.refinementJournal.json(), 0)
            openRefino(scenario)
            val dom = refinoDom(scenario).put("decisionReasons", JSONArray(reasons))
                .put("sessionVerdictCount", verdicts.size).put("resumo", md)
            saveEvidence("refino-decisoes-entre-ticks", dom, scenario,
                provenance("SYNTHETIC_NON_SCIENTIFIC", "none", "Journal real→service→worker→JSONL antes do healthTick; nenhuma USB/escrita"))
            assertEquals(listOf("MANUAL_WRITE_CONFIRMED", "EXPERIMENT_INVALIDATED",
                "MANUAL_WRITE_CONFIRMED", "BAND_VERIFICATION_COMPLETE"), reasons)
            assertEquals("dois encerramentos distintos precisam sobreviver", 2, verdicts.size)
            assertEquals(listOf("INTERROMPIDO", "VERIFICADO"), verdicts.map { it.getJSONObject("data").getString("status") })
            assertTrue(md, md.contains("EXPERIMENT_INVALIDATED"))
            assertTrue(md, md.contains("O que aconteceu de estranho"))
        } finally { recorder?.stop("EVIDENCE_CLEANUP"); scenario.close() }
    }

    @Test
    fun refinoFirstVerdictReachesSessionWorkerAndResumo() {
        val scenario = launch()
        var recorder: com.omegas.prohub.diagnostics.SessionRecorder? = null
        try {
            val service = service(scenario)
            recorder = service.sessionRecorder
            val started = recorder.start("EVIDENCE_FIRST_EXPERIMENT")
            assertTrue(started.toString(), started.optBoolean("ok"))
            synth(service, "GASOLINA")
            synth(service, "GNV", ratio = 1.12)
            val axis = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
            service.refinementJournal.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { 17000 },
                axis, service.equivalence.index(), "SIMULATED_CONFIRMED_MANUAL_WRITE")
            service.equivalence.resetGas("SIMULATED_WRITE")
            synth(service, "GNV")
            service.refinementJournal.evaluate(service.equivalence.index())
            assertEquals("VERIFICADO", service.refinementJournal.json().getJSONObject("latest").getString("status"))
            // Evento encerrou antes do primeiro healthTick: reproduz o latch de baseline antigo.
            TelemetryForegroundService::class.java.getDeclaredField("verdictBaselineSet").apply {
                isAccessible = true; setBoolean(service, false)
            }
            TelemetryForegroundService::class.java.getDeclaredField("lastVerdictRecordedId").apply {
                isAccessible = true; set(service, "")
            }
            TelemetryForegroundService::class.java.getDeclaredMethod("recordJournalDecision").apply {
                isAccessible = true; invoke(service)
            }
            TelemetryForegroundService::class.java.getDeclaredMethod("recordVerdictIfClosed").apply {
                isAccessible = true; invoke(service)
            }
            val stopped = recorder.stop("EVIDENCE_DONE")
            val directory = File(stopped.getString("directory"))
            val events = directory.listFiles()?.filter { it.name.startsWith("events_") && it.name.endsWith(".jsonl") }
                .orEmpty().flatMap { it.readLines() }.mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
            val verdicts = events.filter { it.optString("type") == "refinement_verdict" }
            val md = File(directory, "RESUMO.md").readText()
            service.equivalencePhases.observe(true,
                JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1),
                null, service.equivalence.index(), service.refinementJournal.json(), 0)
            openRefino(scenario)
            val dom = refinoDom(scenario).put("sessionVerdictCount", verdicts.size)
                .put("sessionHasReason", verdicts.firstOrNull()?.optJSONObject("data")?.optString("reasonCode").orEmpty())
                .put("resumo", md)
            saveEvidence("refino-primeiro-veredito-sessao", dom, scenario,
                provenance("SYNTHETIC_NON_SCIENTIFIC", "none", "service→session worker→JSONL→RESUMO real; nenhuma USB/escrita"))
            assertEquals("primeiro veredito não pode sumir no baseline", 1, verdicts.size)
            assertEquals("BAND_VERIFICATION_COMPLETE", verdicts.single().getJSONObject("data").getString("reasonCode"))
            assertTrue(md, md.contains("verificação concluída"))
            assertTrue("números da decisão na sessão", events.any {
                it.optString("type") == "refinement_decision" &&
                    it.optJSONObject("data")?.optString("component") == "JOURNAL"
            })
        } finally { recorder?.stop("EVIDENCE_CLEANUP"); scenario.close() }
    }

    @Test
    fun refinoWatchdogHonest() {
        val scenario = launch()
        try {
            val service = service(scenario)
            var duration = 1_000L
            val p = EquivalencePhases(null, durationClock = { duration }, clock = { 1_000_000L })
            val empty = JSONObject().put("samples", 0).put("bands", JSONArray())
            val noJournal = JSONObject().put("latest", JSONObject.NULL)
            p.observe(true, null, null, empty, noJournal, 0)
            duration += 30_000L
            val expired = p.observe(true, null, null, empty, noJournal, 0)
            assertEquals("TENTATIVA_ENCERRADA", expired.getString("phase"))
            TelemetryForegroundService::class.java.getDeclaredField("equivalencePhases").apply {
                isAccessible = true; set(service, p)
            }
            openRefino(scenario)
            val dom = refinoDom(scenario)
            saveEvidence("refino-watchdog-honesto", dom, scenario,
                provenance("SYNTHETIC_NON_SCIENTIFIC", "none", "watchdog real, prazo simulado 30000 ms, nenhuma escrita"))
            assertClean(dom)
            assertEquals("Etapa pausada", dom.getString("chip"))
            assertTrue(dom.getString("headline"), dom.getString("headline").contains("não respondeu a tempo"))
            assertTrue("frase da falha precisa ser visível", dom.getBoolean("headlineVisible"))
            assertTrue("texto essencial >=12px", dom.getDouble("headlineFont") >= 12.0)
            assertTrue("próximo passo precisa estar visível", dom.getBoolean("nextVisible"))
            assertTrue("sem proposta vencida", dom.getBoolean("primaryHidden"))
            assertEquals("—", dom.getString("ratio"))
        } finally { scenario.close() }
    }

    @Test
    fun refinoOfflineRetainsHistoryWithoutCurrentSuccess() {
        val scenario = launch()
        try {
            val service = service(scenario)
            val snapshot = prepareCurvaPronta(service)
            service.equivalencePhases.observe(false,
                JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1),
                AutoCalAcquisition.fromSnapshot(snapshot), service.equivalence.index(),
                service.refinementJournal.json(), service.refinementJournal.restorePoints().length())
            openRefino(scenario)
            val dom = refinoDom(scenario)
            saveEvidence("refino-offline-honesto", dom, scenario,
                provenance("REAL_REPLAY_WITH_SYNTHETIC_DISCONNECTION", "ref_2026-10-01_1719",
                    "medições reais preservadas, offline injetado, não valida ECU/carros"))
            assertClean(dom)
            assertEquals("Sem ECU", dom.getString("chip"))
            assertTrue(dom.getString("headline"), dom.getString("headline").contains("Conecte a ECU"))
            assertTrue("estado sem conexão visível", dom.getBoolean("headlineVisible"))
            assertEquals("valor antigo não é equivalência atual", "—", dom.getString("ratio"))
            assertTrue("sem ação de gravação offline", dom.optString("primaryKind") != "review")
        } finally { scenario.close() }
    }

    @Test
    fun refinoEcuNoAutomatico() {
        val scenario = launch()
        try {
            val service = service(scenario)
            val root = corpus("automatch_2026-10-01_1301")
            feedLedger(service, root)
            val snap = publishSnapshot(service, root, 1716)
            observe(service, snap, count = 1)
            openRefino(scenario)
            val dom = refinoDom(scenario)
            saveEvidence("refino-ecu-automatico", dom, scenario, provenance("REAL_REPLAY", "automatch_2026-10-01_1301", "contador 1 de 3, snapshot 1716"))
            assertClean(dom)
            assertEquals("ECU no automático", dom.getString("chip"))
            assertTrue(dom.getString("headline"), dom.getString("headline").contains("automático 1 de 3"))
            assertEquals("active", dom.getJSONArray("steps").getString(0))
            assertTrue("não oferece gravar enquanto a ECU trabalha", dom.optString("primaryKind") != "review")
        } finally { scenario.close() }
    }

    @Test
    fun refinoColetando() {
        val scenario = launch()
        try {
            val service = service(scenario)
            val root = corpus("ref_2026-10-01_1719")
            feedLedger(service, root, onlyFuel = "GASOLINA")
            val snap = publishSnapshot(service, root, 962)
            observe(service, snap, count = 3)
            openRefino(scenario)
            val dom = refinoDom(scenario)
            saveEvidence("refino-coletando", dom, scenario, provenance("REAL_REPLAY", "ref_2026-10-01_1719", "só a gasolina da sessão; GNV ainda não medido"))
            assertClean(dom)
            // O replay alimenta o livro de pontos, não publica combustível vivo.
            // Sem telemetria recente a tela não pode afirmar que o carro está no GNV.
            assertEquals("Medindo", dom.getString("chip"))
            assertTrue(dom.getString("headline"), dom.getString("headline").contains("aprendendo seu motor"))
            assertTrue(dom.getString("ourPoints"), dom.getString("ourPoints").contains("regiões medidas"))
        } finally { scenario.close() }
    }

    private fun prepareCurvaPronta(service: TelemetryForegroundService): JSONObject {
        val root = corpus("ref_2026-10-01_1719")
        feedLedger(service, root)
        val snap = publishSnapshot(service, root, 962)
        observe(service, snap, count = 3)
        return snap
    }

    @Test
    fun refinoCurvaPronta() {
        val scenario = launch()
        try {
            val service = service(scenario)
            prepareCurvaPronta(service)
            openRefino(scenario)
            val dom = refinoDom(scenario)
            saveEvidence("refino-corpus-proposta-bloqueada", dom, scenario,
                provenance("REAL_REPLAY", "ref_2026-10-01_1719",
                    "o corpus permite cálculo legado, mas o cérebro atual ainda pede coleta; a UI não oferece gravação"))
            assertClean(dom)
            // Histórico com GNV não comprova o combustível atual sem quadro vivo.
            assertEquals("Medindo", dom.getString("chip"))
            assertEquals("COLLECT", dom.getJSONObject("canonical").getJSONObject("nextAction").getString("kind"))
            assertTrue("contrato impede gravar com evidência ainda insuficiente",
                !dom.getJSONObject("canonical").getJSONObject("refinoState").getBoolean("canAct"))
            assertEquals("none", dom.getString("primaryKind"))
            assertTrue("ação de gravação não pode vazar de uma proposta antiga", dom.getBoolean("primaryHidden"))
            assertTrue("curva da ECU desenhada", dom.getInt("referenceLines") >= 1)
            assertTrue("gráfico disponível durante coleta", dom.getBoolean("svg"))
        } finally { scenario.close() }
    }

    /**
     * O caso do carro: app recém-instalado, a ECU já fez o AutoMatch (3 de 3) e já tem a curva de gasolina,
     * e este app nunca mediu gasolina. O Refino lê a ECU e não pede gasolina nem espera AutoMatch.
     */
    @Test
    fun refinoAppNovoEcuPronta() {
        val scenario = launch()
        try {
            val service = service(scenario)
            val root = corpus("ref_2026-10-01_1719")
            feedLedger(service, root, onlyFuel = "GNV") // nenhuma gasolina medida por este app
            val snap = publishSnapshot(service, root, 2183) // a ECU entrega a curva de gasolina madura
            val ecuPoints = EcuPetrolReference.fromAcquisition(AutoCalAcquisition.fromSnapshot(snap))
            check(ecuPoints.size >= 10) { "a ECU do corpus precisa ter a curva de gasolina madura (${ecuPoints.size} pontos)" }
            service.equivalence.setEcuPetrolReference(ecuPoints)
            observe(service, snap, count = 3)
            openRefino(scenario)
            val dom = refinoDom(scenario)
            saveEvidence("refino-app-novo-ecu-pronta", dom, scenario, provenance("REAL_REPLAY", "ref_2026-10-01_1719", "só o GNV da sessão + curva de gasolina da ECU (snapshot 2183); AutoMatch 3 de 3"))
            assertClean(dom)
            val words = (dom.optString("headline") + " " + dom.optString("next"))
            assertTrue("não espera AutoMatch que a ECU já fez: $words", !words.contains("está no automático"))
            assertTrue("não pede gasolina que a ECU já tem: $words", !words.contains("na gasolina para criar"))
            assertTrue("o detalhe diz que a gasolina é a da ECU: ${dom.getString("techText")}", dom.getString("techText").contains("curva de gasolina que a ECU já tem"))
            assertTrue("a fase não é de espera: ${dom.optString("chip")}", dom.optString("chip") !in listOf("ECU no automático", "Sem ECU", "Lendo a ECU"))
        } finally { scenario.close() }
    }

    @Test
    fun refinoVerificando() {
        val scenario = launch()
        try {
            val service = service(scenario)
            val snap = prepareCurvaPronta(service)
            // O proprietário gravou a curva proposta (aqui o diário recebe o antes/depois, sem escrever na ECU).
            openRefino(scenario)
            val analysis = JSONObject(evalRaw(scenario, "OmegasAutoCal.getRefinedAnalysis()").let { JSONTokener(it).nextValue() as String })
            val points = analysis.getJSONArray("points")
            val axis = IntArray(30) { (points.getJSONObject(it).getDouble("referenceTimeMs") * 512.0).toInt() }
            val before = IntArray(30) { points.getJSONObject(it).getInt("currentRaw") }
            val after = IntArray(30) { points.getJSONObject(it).getInt("calculatedRaw") }
            service.refinementJournal.recordCurveWrite(before, after, axis, service.equivalence.index(), "RENDER_REPLAY")
            service.equivalence.resetGas("CURVA_K_GRAVADA")
            observe(service, snap, count = 3)
            refreshRefino(scenario)
            val dom = refinoDom(scenario)
            saveEvidence("refino-verificando", dom, scenario, provenance("REAL_REPLAY", "ref_2026-10-01_1719", "diário recebeu antes/depois da proposta real; nada foi escrito na ECU"))
            assertClean(dom)
            assertEquals("Verificando", dom.getString("chip"))
            assertTrue(dom.getString("headline"), dom.getString("headline").contains("confiro se o GNV chegou na gasolina"))
            assertEquals("medição não oferece uma gravação nem um botão sem ação", "none", dom.getString("primaryKind"))
        } finally { scenario.close() }
    }

    @Test
    fun refinoEstavel() {
        val scenario = launch()
        try {
            val service = service(scenario)
            val root = corpus("ref_2026-10-01_1719")
            synth(service, "GASOLINA"); synth(service, "GNV", ratio = 1.0)
            val snap = publishSnapshot(service, root, 962)
            observe(service, snap, count = 3)
            openRefino(scenario)
            val dom = refinoDom(scenario)
            saveEvidence("refino-estavel", dom, scenario, provenance("SYNTHETIC_NON_SCIENTIFIC", "ref_2026-10-01_1719", "o corpus não tem sessão estável; pares sintéticos GNV = gasolina em 5 faixas"))
            assertClean(dom)
            assertEquals("Estável", dom.getString("chip"))
            assertEquals("stable", dom.getString("primaryKind"))
        } finally { scenario.close() }
    }

    @Test
    fun refinoRestaurarTrecho() {
        val scenario = launch()
        try {
            val service = service(scenario)
            val root = corpus("ref_2026-10-01_1719")
            synth(service, "GASOLINA"); synth(service, "GNV", ratio = 1.08)
            val snap = publishSnapshot(service, root, 962)
            val axis = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
            service.refinementJournal.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { if (it in 5..23) 17000 else 16384 }, axis, service.equivalence.index(), "RENDER_SYNTH")
            service.equivalence.resetGas("CURVA_K_GRAVADA")
            synth(service, "GNV", ratio = 1.25) // depois da gravação o GNV ficou mais longe da gasolina
            service.refinementJournal.evaluate(service.equivalence.index(), ecuOnline = true)
            observe(service, snap, count = 3)
            openRefino(scenario)
            val dom = refinoDom(scenario)
            saveEvidence("refino-restaurar-trecho", dom, scenario, provenance("SYNTHETIC_NON_SCIENTIFIC", "ref_2026-10-01_1719", "o corpus não tem gravação que piorou; antes/depois sintéticos"))
            assertClean(dom)
            assertEquals("Trecho piorou", dom.getString("chip"))
            assertEquals("restore", dom.getString("primaryKind"))
            assertTrue(dom.getString("primaryText"), dom.getString("primaryText").contains("Restaurar trecho que piorou"))
        } finally { scenario.close() }
    }

    @Test
    fun refinoApagoes() {
        val scenario = launch()
        try {
            val service = service(scenario)
            val root = corpus("ref_2026-10-01_1719")
            feedLedger(service, root)
            var t = feedStalls(service, root) + 10_000L
            // O corpus real só tem quase-apagões; um apagão completo é sintético e vem rotulado.
            repeat(25) { i -> service.stallWatch.accept(StallWatch.Frame(t, "GNV", 1_800.0 - i * 40.0, 0.30, 2.1, 22.0)); t += 100 }
            repeat(12) { service.stallWatch.accept(StallWatch.Frame(t, "GNV", 0.0, 0.9, 0.0, 15.0)); t += 100 }
            repeat(6) { service.stallWatch.accept(StallWatch.Frame(t, "GNV", 900.0, 0.4, 3.0, 5.0)); t += 100 }
            val snap = publishSnapshot(service, root, 962)
            observe(service, snap, count = 3)
            openRefino(scenario)
            val dom = refinoDom(scenario)
            saveEvidence("refino-apagoes", dom, scenario, provenance("REAL_REPLAY+SYNTHETIC", "ref_2026-10-01_1719", "3 quase-apagões reais do corpus + 1 apagão sintético que religou"))
            assertClean(dom)
            assertTrue("painel de apagões visível", dom.getBoolean("stallsVisible"))
            assertTrue(dom.getString("stallsText"), dom.getString("stallsText").contains("quase apagou 3 vezes"))
            assertTrue(dom.getString("stallsText"), dom.getString("stallsText").contains("apagou 1 vez"))
            assertTrue(dom.getString("stallsText"), dom.getString("stallsText").contains("religou 1"))
            assertTrue("marcas ✕ no gráfico", dom.getInt("stallMarks") >= 1)
        } finally { scenario.close() }
    }

    // ------------------------------------------------------------------ o bug do carro: AGORA do Refino congelado

    private fun injectLive(service: TelemetryForegroundService, payload: ByteArray, session: Long) {
        val captured = SystemClock.elapsedRealtime()
        val live = Mp48Protocol.decodeTelemetry(payload, captured).toJson().put("session_id", session).put("captured_elapsed_ms", captured)
        check(service.telemetryStore.updateFromEngineEvent(JSONObject().put("event", "telemetry").put("session_id", session).put("data", live)) != null) {
            "TelemetryStateStore rejected replay"
        }
    }

    private fun livePayloads(): List<ByteArray> {
        val raw = instrumentation.context.assets.open("portmon-autocal-cycle-v1.json").bufferedReader().use { it.readText() }
        val rows = JSONObject(raw).getJSONArray("transactions")
        val out = ArrayList<ByteArray>()
        for (i in 0 until rows.length()) {
            val tx = rows.getJSONObject(i)
            if (tx.getString("request") != "48 01 49") continue
            val request = hex(tx.getString("request"))
            val response = hex(tx.getString("response"))
            val size = response[request.size + 1].toInt() and 0xFF
            out += response.copyOfRange(request.size + 2, request.size + 2 + size)
        }
        return out
    }

    @Test
    fun refinoAgoraAcompanhaATelemetria() {
        val scenario = launch()
        try {
            val service = service(scenario)
            val root = corpus("ref_2026-10-01_1719")
            feedLedger(service, root)
            val snap = publishSnapshot(service, root, 962)
            observe(service, snap, count = 3)
            val frames = livePayloads()
            val decoded = frames.map { Mp48Protocol.decodeTelemetry(it, 1L).toJson() }
            val a = frames[0]
            val basePetrol = decoded[0].getDouble("petrol_ms")
            // Os quadros reais do portmon são todos da mesma condição (marcha lenta): o segundo quadro é o
            // real com o contador de Petrol Inj. alterado (variação sintética rotulada no recibo).
            val b = (0..a.size - Mp48Protocol.TELEMETRY_PAYLOAD_SIZE).firstNotNullOfOrNull { i ->
                val copy = a.copyOf()
                val raw = (copy[i + 8].toInt() and 0xFF) or ((copy[i + 9].toInt() and 0xFF) shl 8)
                val bumped = (raw * 3 / 2 + 1_200).coerceAtMost(0xFFFF)
                copy[i + 8] = bumped.toByte(); copy[i + 9] = (bumped shr 8).toByte()
                val d = runCatching { Mp48Protocol.decodeTelemetry(copy, 1L).toJson() }.getOrNull()
                if (d != null && kotlin.math.abs(d.getDouble("petrol_ms") - basePetrol) > 0.4) copy else null
            }
            checkNotNull(b) { "não foi possível variar o Petrol Inj. do quadro real (base $basePetrol ms)" }
            service.telemetryStore.beginSession(9001L)
            openRefino(scenario)
            // A tela esconde a bolinha de telemetria velha: cada quadro é injetado logo antes da leitura.
            injectLive(service, a, 9001L)
            scenario.onActivity { it.refreshWebUi() }
            SystemClock.sleep(500L)
            val first = refinoDom(scenario)
            injectLive(service, b, 9001L)
            scenario.onActivity { it.refreshWebUi() }
            SystemClock.sleep(500L)
            val second = refinoDom(scenario)
            saveEvidence("refino-agora-acompanha", second, scenario, provenance("REAL_REPLAY+SYNTHETIC_VARIATION", "portmon-autocal-cycle-v1", "um quadro MP48 real e o mesmo quadro com o contador de Petrol Inj. alterado").put("firstCx", first.opt("liveCx")).put("secondCx", second.opt("liveCx")))
            assertClean(second)
            assertTrue("bolinha AGORA visível com telemetria fresca (liveVisible=${first.opt("liveVisible")} cx=${first.opt("liveCx")} chip=${first.opt("chip")} headline=${first.opt("headline")})", first.getBoolean("liveVisible"))
            assertNotEquals("a bolinha AGORA do Refino precisa mexer quando a telemetria muda", first.optDouble("liveCx"), second.optDouble("liveCx"), 0.5)
        } finally { scenario.close() }
    }

    @Test
    fun refinoLatenciaDaPonte() {
        val scenario = launch()
        try {
            val service = service(scenario)
            val root = corpus("ref_2026-10-01_1719")
            feedLedger(service, root)
            val snap = publishSnapshot(service, root, 962)
            observe(service, snap, count = 3)
            openRefino(scenario)
            // Aquecimento: a primeira chamada calcula; as seguintes devolvem o valor pronto.
            evalRaw(scenario, "OmegasAutoCal.getEquivalence(); OmegasAutoCal.getUiProjection(); OmegasAutoCal.getRefinedAnalysis(); 'ok';")
            SystemClock.sleep(1_500L)
            val stats = evalJson(
                scenario,
                """
                JSON.stringify((() => {
                  const times = [];
                  for (let i = 0; i < 40; i += 1) {
                    const start = performance.now();
                    OmegasAutoCal.getEquivalence();
                    OmegasAutoCal.getUiProjection();
                    OmegasAutoCal.getRefinedAnalysis();
                    times.push(performance.now() - start);
                  }
                  times.sort((a, b) => a - b);
                  return { median: times[20], p95: times[37], max: times[39] };
                })())
                """.trimIndent(),
            )
            val dom = refinoDom(scenario)
            saveEvidence("refino-latencia-da-ponte", dom, scenario, provenance("REAL_REPLAY", "ref_2026-10-01_1719", "3 chamadas da ponte por iteração, 40 iterações, medidas dentro da WebView do emulador").put("bridgeLatencyMs", stats))
            assertClean(dom)
            assertTrue("mediana da ponte ${stats.getDouble("median")} ms (limite 30)", stats.getDouble("median") < 30.0)
            assertTrue("p95 da ponte ${stats.getDouble("p95")} ms (limite 100)", stats.getDouble("p95") < 100.0)
        } finally { scenario.close() }
    }

    // ------------------------------------------------------------------ Ferramentas e balão flutuante

    /**
     * O carro mostrou a tela de Ferramentas congelada. Aqui há 25 sessões salvas sem resumo (a pior hora:
     * cada uma precisa ser lida por inteiro) e a tela precisa responder na hora, ler a lista em segundo
     * plano e explicar a retenção. Também prova a pergunta de primeiro uso do balão flutuante.
     */
    @Test
    fun ferramentasEBalaoFlutuante() {
        val scenario = launch()
        val sessionsRoot = com.omegas.prohub.storage.AppPaths(instrumentation.targetContext).sessionLogsRoot
        val fakes = ArrayList<File>()
        try {
            repeat(25) { i ->
                val dir = File(sessionsRoot, "session_2026-09-%02d_10-00-00_fake%02d".format(1 + i % 28, i)).apply { mkdirs() }
                fakes += dir
                val start = 1_790_000_000_000L + i * 3_600_000L
                File(dir, "manifest.json").writeText(JSONObject().put("sessionId", dir.name).put("createdAtMs", start)
                    .put("stoppedAtMs", start + 1_800_000L).put("reason", "sessão de teste").toString())
                File(dir, "events_0001.jsonl").bufferedWriter().use { w ->
                    repeat(8_000) { n ->
                        w.write(JSONObject().put("sequence", n + 1L).put("recordedAtMs", start + n * 220L).put("type", "telemetry")
                            .put("source", "mp48").put("data", JSONObject().put("rpm", 1_500 + n % 300).put("fuel", if (n % 2 == 0) "GNV" else "GASOLINA")).toString())
                        w.newLine()
                    }
                }
                File(dir, ".documents_mirrored").writeText("1") // já copiada: o teste não publica nem apaga nada
            }
            // A primeira chamada da lista não pode esperar a leitura das 25 sessões.
            val first = evalJson(scenario, "JSON.stringify((() => { const t = performance.now(); const r = window.OmegasApp.api.sessions(); return { ms: performance.now() - t, kind: r === null ? 'null' : Array.isArray(r) ? 'array' : typeof r }; })())")
            evalRaw(scenario, "document.querySelector('[data-route=\"tools\"]')?.click(); 'ok';")
            waitFor(90_000L) { evalRaw(scenario, "Array.isArray(window.OmegasApp.store.get().sessions) && window.OmegasApp.store.get().sessions.length >= 8").trim() == "true" }
            SystemClock.sleep(1_500L)
            val latency = evalJson(scenario, "JSON.stringify((() => { const t = []; for (let i = 0; i < 30; i += 1) { const s = performance.now(); window.OmegasApp.api.sessions(); window.OmegasApp.api.sessionStatus(); t.push(performance.now() - s); } t.sort((a, b) => a - b); return { median: t[15], max: t[29] }; })())")
            evalRaw(scenario, "window.OmegasApp.promptOverlay(true); 'ok';")
            SystemClock.sleep(500L)
            val dom = evalJson(
                scenario,
                """
                JSON.stringify((() => {
                  const rect = n => { const r = n ? n.getBoundingClientRect() : null; return r ? { top: r.top, bottom: r.bottom, left: r.left, right: r.right, width: r.width, height: r.height } : null; };
                  const prompt = document.getElementById('overlayPrompt');
                  const row = document.querySelector('[data-overlay-state]');
                  return {
                    sessionItems: document.querySelectorAll('.recorded-session-item').length,
                    retentionText: document.querySelector('.diagnostic-settings')?.textContent ?? '',
                    overlayState: row ? row.dataset.overlayState : null,
                    overlayTitle: row ? row.querySelector('b')?.textContent : null,
                    authorizeButton: rect(document.querySelector('[data-tool-overlay-request]')),
                    promptVisible: !!prompt,
                    promptCard: rect(document.querySelector('.overlay-prompt-card')),
                    promptYes: rect(document.querySelector('[data-overlay-prompt="yes"]')),
                    promptNo: rect(document.querySelector('[data-overlay-prompt="no"]')),
                    viewportHeight: window.innerHeight, viewportWidth: window.innerWidth,
                    bodyHasNaN: /NaN/.test(document.body.innerText)
                  };
                })())
                """.trimIndent(),
            )
            saveEvidence("ferramentas-balao-prompt", JSONObject().put("tools", dom).put("firstSessionsCall", first).put("bridgeLatencyMs", latency).put("active", true)
                .put("steps", JSONArray()).put("screenScrollWidth", 0).put("screenClientWidth", 0), scenario,
                provenance("SYNTHETIC_NON_SCIENTIFIC", "25 sessões salvas de teste", "prova a tela de Ferramentas com 25 sessões sem resumo e a pergunta de primeiro uso do balão"))
            assertTrue("a primeira leitura da lista volta na hora (${first.getDouble("ms")} ms) e ainda sem lista (${first.getString("kind")})", first.getDouble("ms") < 100.0)
            assertTrue("lista + estado respondem pronto: mediana ${latency.getDouble("median")} ms", latency.getDouble("median") < 30.0)
            assertTrue("a lista apareceu na tela", dom.getInt("sessionItems") >= 8)
            assertTrue("a retenção diz onde fica o ZIP", dom.getString("retentionText").contains("um só arquivo ZIP") && dom.getString("retentionText").contains("Download/Omegas"))
            assertEquals("sem autorização o estado diz isso", "needs-permission", dom.getString("overlayState"))
            assertTrue("botão Autorizar existe e é grande", dom.getJSONObject("authorizeButton").getDouble("height") >= 44.0)
            assertTrue("a pergunta de primeiro uso aparece", dom.getBoolean("promptVisible"))
            assertTrue("pergunta dentro da tela", dom.getJSONObject("promptCard").getDouble("bottom") <= dom.getDouble("viewportHeight") && dom.getJSONObject("promptCard").getDouble("right") <= dom.getDouble("viewportWidth"))
            assertTrue("botões da pergunta grandes", dom.getJSONObject("promptYes").getDouble("height") >= 48.0 && dom.getJSONObject("promptNo").getDouble("height") >= 48.0)
            assertTrue("sem NaN", !dom.getBoolean("bodyHasNaN"))
            evalRaw(scenario, "document.querySelector('[data-overlay-prompt=\"no\"]')?.click(); 'ok';")
            assertEquals("Agora não fecha a pergunta", "false", evalRaw(scenario, "String(!!document.getElementById('overlayPrompt'))").trim('"'))
        } finally {
            scenario.close()
            fakes.forEach { it.deleteRecursively() }
        }
    }

    // ------------------------------------------------------------------ utilitários

    private fun saveEvidence(name: String, dom: JSONObject, scenario: ActivityScenario<MainActivity>, provenance: JSONObject) {
        var web = JSONObject()
        scenario.onActivity { activity ->
            val view = activity.findViewById<WebView>(R.id.hubWebView)
            web = JSONObject().put("webViewWidth", view.width).put("webViewHeight", view.height)
        }
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "omegas-evidence")
        check(dir.mkdirs() || dir.isDirectory)
        File(dir, "$name.json").writeText(
            JSONObject().put("scenario", name).put("sourceSha", BuildConfig.OMEGAS_BUILD_COMMIT)
                .put("displayWidth", metrics.widthPixels).put("displayHeight", metrics.heightPixels).put("densityDpi", metrics.densityDpi)
                .put("dom", dom).put("webView", web).put("fixtureProvenance", provenance).toString(2),
        )
        val windows = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("dumpsys window"),
        ).bufferedReader().use { it.readText() }
        File(dir, "$name-window-manager.txt").writeText(windows)
        val focusedWindow = windows.lineSequence().firstOrNull { it.contains("mCurrentFocus=") }.orEmpty()
        val foreground = Regex("""mCurrentFocus=Window\{[^}]*\s([^\s/]+)/""").find(focusedWindow)?.groupValues?.get(1)
        assertEquals("a captura deve mostrar o app, sem diálogo externo cobrindo a evidência: $focusedWindow", instrumentation.targetContext.packageName, foreground)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(dir, "$name.png")).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun evalJson(scenario: ActivityScenario<MainActivity>, script: String): JSONObject {
        val raw = evalRaw(scenario, script)
        return when (val value = JSONTokener(raw).nextValue()) {
            is String -> JSONObject(value)
            is JSONObject -> value
            else -> error("unexpected JS result: $raw")
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
        check(latch.await(15, TimeUnit.SECONDS)) { "JavaScript evaluation timeout" }
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
        value.trim().split(Regex("\\s+")).filter(String::isNotBlank).map { it.toInt(16).toByte() }.toByteArray()
}
