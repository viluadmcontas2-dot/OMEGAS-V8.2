package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import com.omegas.prohub.ecu.Mp48TelemetryWindowSource
import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 2: sequências sintéticas da spec 2026-10-07 (detecção → política → execução → recibo). */
class AutoIdleCleanupCoordinatorTest {
    private var now = 100_000L
    private val frames = mutableListOf<NativeAnchorTelemetryWindow.Frame>()
    private val calls = mutableListOf<List<AutoCalPointDeleteProtocol.Target>>()
    private val evidences = mutableListOf<JSONObject>()
    private val records = mutableListOf<Pair<String, JSONObject>>()
    private var nextResult: JSONObject = JSONObject().put("ok", true).put("started", true)
    private var enabled: Int? = 1
    private var sessionAge = 60_000L
    private var actBlock: String? = null

    private val coordinator = AutoIdleCleanupCoordinator(
        telemetry = object : Mp48TelemetryWindowSource {
            override fun recentTelemetryFrames(fromElapsedMs: Long, toElapsedMs: Long) =
                frames.filter { it.elapsedMs in fromElapsedMs..toElapsedMs }
        },
        executeDelete = { targets, evidence -> calls += targets; evidences += evidence; nextResult },
        autoCalEnabled = { enabled },
        sessionAgeMs = { sessionAge },
        canAct = { actBlock },
        record = { type, payload -> records += type to payload },
        clock = { now },
        executor = { it.run() },
    )

    @Test
    fun `lenta depois andou apaga com evidencia lenta`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        assertEquals(1, calls.size)
        assertEquals(listOf(AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.GAS, 4)), calls.single())
        val evidence = evidences.single()
        assertEquals("lenta", evidence.getString("reason"))
        val band = evidence.getJSONArray("bands").getJSONObject(0)
        assertEquals(4, band.getInt("band"))
        assertTrue(band.getInt("frames") >= 3)
        assertEquals(1.0, band.getDouble("idleFraction"), 1e-9)
        assertTrue(band.has("mapBar"))
    }

    @Test
    fun `aquisicao andando nao apaga`() {
        reading(counters = 3, at = now)
        addFrames(rpm = 2_200, count = 6)
        now += 2_000
        reading(counters = 4, at = now)
        drive()
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `segundo disparo antes de 5 s espera e depois apaga`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmedReceipt(4))
        // Readquiriu de novo na lenta logo em seguida.
        markIdle(band = 4, startCounter = 0)
        drive()
        coordinator.evaluate()
        assertEquals(1, calls.size)
        now += 5_000
        drive()
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `colisao tentar depois nao consome intervalo`() {
        markIdle(band = 4)
        drive()
        nextResult = JSONObject().put("ok", false).put("retryLater", true).put("error", "Aguarde")
        coordinator.evaluate()
        nextResult = JSONObject().put("ok", true).put("started", true)
        now += 300
        drive()
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `sessao nova reconexao e AUTO_CAL_ENABLE 0 nao apagam`() {
        markIdle(band = 4)
        coordinator.onSessionChanged(2L)
        drive()
        coordinator.evaluate()
        assertTrue("sessão nova esquece as marcas", calls.isEmpty())

        markIdle(band = 4)
        enabled = 0
        drive()
        coordinator.evaluate()
        assertTrue(calls.isEmpty())

        enabled = 1
        sessionAge = 2_000 // reconexão ainda estabilizando
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `invalidacao do round e acao manual zeram as marcas`() {
        markIdle(band = 4)
        coordinator.onRoundInvalidated()
        drive()
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `falha com mutacao possivel bloqueia ate releitura e 10 s`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        coordinator.onActionFailed(failedReceipt(mutation = true))
        drive()
        coordinator.evaluate()
        assertEquals(1, calls.size)
        now += 11_000
        drive()
        coordinator.evaluate()
        assertEquals("sem releitura dos buffers ainda não", 1, calls.size)
        reading(counters = 4, at = now) // releitura confirmada (contador inalterado, marca segue)
        drive()
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `gasolina anormal no readback desliga o automatico na sessao e registra`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmedReceipt(4, petrolAbnormal = true))
        assertTrue(records.any { it.first == "autocal_auto_idle_disabled" })
        markIdle(band = 5, startCounter = 1)
        now += 10_000
        drive()
        coordinator.evaluate()
        assertEquals(1, calls.size)
        assertFalse(coordinator.json().getJSONObject("policy").getBoolean("enabled"))
    }

    @Test
    fun `readback ineficaz desliga o automatico na sessao`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        coordinator.onActionFailed(failedReceipt(mutation = true, effective = false))
        assertFalse(coordinator.json().getJSONObject("policy").getBoolean("enabled"))
        assertTrue(records.any { it.first == "autocal_auto_idle_disabled" })
    }

    @Test
    fun `sem controle local do MP48 nao apaga`() {
        markIdle(band = 4)
        actBlock = "Este aparelho não possui o controle principal do MP48"
        drive()
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `um apagamento em voo nao dispara outro`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        now += 100
        drive()
        coordinator.evaluate()
        assertEquals(1, calls.size)
    }

    @Test
    fun `apagamento na vespera do automatch apaga normalmente`() {
        // O coordenador não consulta contador/MAX do AutoMatch: a banda é apagada mesmo com a cota quase cheia.
        markIdle(band = 7)
        drive()
        coordinator.evaluate()
        assertEquals(7, calls.single().single().index)
    }

    @Test
    fun `recibo manual nao mexe no intervalo do automatico`() {
        markIdle(band = 4)
        coordinator.onActionConfirmed(confirmedReceipt(4).put("automatic", false).put("humanConfirmed", true))
        drive()
        coordinator.evaluate()
        assertEquals(1, calls.size)
    }

    @Test
    fun `R2 falha repetida na releitura de antes nao vira Delete a cada 500 ms`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        coordinator.onActionFailed(failedReceipt(mutation = false))
        repeat(8) { now += 500; drive(); coordinator.evaluate() }
        assertEquals("só depois do intervalo mínimo", 1, calls.size)
        now += 1_500
        drive()
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `R2 falhas persistentes desligam o automatico na sessao com registro`() {
        markIdle(band = 4)
        repeat(5) {
            drive()
            coordinator.evaluate()
            coordinator.onActionFailed(failedReceipt(mutation = false))
            now += 6_000
        }
        assertEquals(5, calls.size)
        drive()
        coordinator.evaluate()
        assertEquals(5, calls.size)
        assertFalse(coordinator.json().getJSONObject("policy").getBoolean("enabled"))
        assertTrue(records.any { it.first == "autocal_auto_idle_disabled" })
    }

    @Test
    fun `R1 readback ambiguo nao desliga e espera releitura`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmedReceipt(4).also { it.getJSONObject("details").put("readbackAmbiguous", true) })
        assertTrue(coordinator.json().getJSONObject("policy").getBoolean("enabled"))
        assertTrue(coordinator.json().getJSONObject("policy").getBoolean("needsReread"))
    }

    @Test
    fun `R3 banda pulada por ter mudado perde a marca`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        coordinator.onActionFailed(failedReceipt(mutation = false).put("skippedChanged", JSONArray().put(4)))
        now += 6_000
        drive()
        coordinator.evaluate()
        assertEquals(1, calls.size)
    }

    @Test
    fun `resumo para a tela conta pontos reaprendidos e lista o apagamento recente`() {
        val start = coordinator.uiSummary()
        assertTrue(start.enabled)
        assertEquals(0, start.relearnedThisSession)
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmedReceipt(4).put("id", "R-1").put("finishedAtMs", 1_234L))
        val summary = coordinator.uiSummary()
        assertTrue(summary.active)
        assertTrue(summary.enabled)
        assertEquals(null, summary.pauseCode)
        assertEquals(1, summary.relearnedThisSession)
        val recent = summary.recentDeletes.single()
        assertEquals("R-1", recent.receiptId)
        assertEquals(listOf(4), recent.indexes)
        assertEquals(1_234L, recent.atMs)
    }

    @Test
    fun `resumo nao conta banda de readback ambiguo`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        val receipt = confirmedReceipt(4).put("id", "R-2")
        receipt.getJSONObject("details")
            .put("readbackAmbiguous", true)
            .put("effect", JSONArray().put(JSONObject().put("index", 4).put("result", "AMBIGUOUS")))
        coordinator.onActionConfirmed(receipt)
        val summary = coordinator.uiSummary()
        assertEquals(0, summary.relearnedThisSession)
        assertTrue(summary.recentDeletes.isEmpty())
    }

    @Test
    fun `resumo diz o motivo simples da pausa e a sessao nova religa`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmedReceipt(4, petrolAbnormal = true))
        assertFalse(coordinator.uiSummary().enabled)
        assertEquals(AutoIdleCleanupCoordinator.PauseCode.PETROL_GUARD, coordinator.uiSummary().pauseCode)

        coordinator.onSessionChanged(2L)
        val fresh = coordinator.uiSummary()
        assertTrue(fresh.enabled)
        assertEquals(null, fresh.pauseCode)
        assertEquals(0, fresh.relearnedThisSession)
        assertTrue(fresh.recentDeletes.isEmpty())
    }

    @Test
    fun `resumo distingue readback ineficaz de falhas seguidas`() {
        markIdle(band = 4)
        drive()
        coordinator.evaluate()
        coordinator.onActionFailed(failedReceipt(mutation = true, effective = false))
        assertEquals(AutoIdleCleanupCoordinator.PauseCode.READBACK_INEFFECTIVE, coordinator.uiSummary().pauseCode)

        coordinator.onSessionChanged(3L)
        markIdle(band = 4)
        repeat(5) {
            drive()
            coordinator.evaluate()
            coordinator.onActionFailed(failedReceipt(mutation = false))
            now += 6_000
        }
        assertEquals(AutoIdleCleanupCoordinator.PauseCode.REPEATED_FAILURES, coordinator.uiSummary().pauseCode)
    }

    // ---- apoio ----

    /** Duas leituras confirmadas: a segunda adquire a banda com telemetria de lenta no intervalo. */
    private fun markIdle(band: Int, startCounter: Int = 3) {
        reading(counters = startCounter, at = now, band = band)
        addFrames(rpm = 850, count = 6)
        now += 2_000
        reading(counters = startCounter + 1, at = now, band = band)
    }

    private fun reading(counters: Int, at: Long, band: Int = 4) {
        val c = IntArray(18).also { it[band] = counters }
        val map = IntArray(18).also { it[band] = MAP_RAW }
        coordinator.onGasBuffers(
            AutoIdleCleanupCoordinator.GasBuffers(
                sessionId = 1L, counters = c, timeRaw = IntArray(18), mapRaw = map, observedAtElapsedMs = at,
            ),
        )
    }

    private fun addFrames(rpm: Int, count: Int) {
        repeat(count) { i ->
            frames += NativeAnchorTelemetryWindow.Frame(frames.size + 1L, now + 100L + i * 200L, rpm, MAP_RAW / 1024.0, 3.0, "GNV")
        }
    }

    private fun drive() {
        frames += NativeAnchorTelemetryWindow.Frame(frames.size + 1L, now, 2_300, 0.7, 5.0, "GNV")
    }

    private fun confirmedReceipt(band: Int, petrolAbnormal: Boolean = false) = JSONObject()
        .put("outcome", "CONFIRMED")
        .put("action", "DELETE_POINT")
        .put("automatic", true)
        .put("humanConfirmed", false)
        .put(
            "details",
            JSONObject()
                .put("targets", JSONArray().put(JSONObject().put("fuel", "GAS").put("index", band)))
                .put("index", band)
                .put("effective", true)
                .put("petrolGuard", JSONObject().put("abnormal", petrolAbnormal).put("changed", petrolAbnormal)),
        )

    private fun failedReceipt(mutation: Boolean, effective: Boolean = true) = JSONObject()
        .put("outcome", "FAILED")
        .put("action", "DELETE_POINT")
        .put("automatic", true)
        .put("mutationMayHaveStarted", mutation)
        .also { if (!effective) it.put("effective", false) }

    companion object {
        private const val MAP_RAW = 410
    }
}
