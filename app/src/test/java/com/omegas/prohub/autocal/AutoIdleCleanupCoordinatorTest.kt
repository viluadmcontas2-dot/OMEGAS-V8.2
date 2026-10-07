package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Fuel
import com.omegas.prohub.ecu.Mp48TelemetryWindowSource
import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 2: sequências sintéticas da spec 2026-10-07 rev2 (fora da curva → combustível certo → execução → recibo). */
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
    fun `GNV fora da curva com carro em GNV apaga com evidencia fora da curva`() {
        buffers(Fuel.GAS, outlierBand = 6)
        buffers(Fuel.PETROL)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(listOf(AutoCalPointDeleteProtocol.Target(Fuel.GAS, 6)), calls.single())
        val evidence = evidences.single()
        assertEquals("fora_da_curva", evidence.getString("reason"))
        assertEquals("GAS", evidence.getString("fuel"))
        val band = evidence.getJSONArray("bands").getJSONObject(0)
        assertEquals(6, band.getInt("band"))
        assertTrue(band.has("counterAfter") && band.has("timeRaw") && band.has("mapRaw"))
    }

    @Test
    fun `curva sem outlier nao apaga nada`() {
        buffers(Fuel.GAS)
        buffers(Fuel.PETROL)
        drive("GNV")
        coordinator.evaluate()
        drive("GASOLINA")
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `gasolina fora da curva com carro em GNV espera e com carro em gasolina apaga so a gasolina`() {
        buffers(Fuel.GAS)
        buffers(Fuel.PETROL, outlierBand = 6)
        drive("GNV")
        coordinator.evaluate()
        assertTrue("carro em GNV: a gasolina espera", calls.isEmpty())
        now += 300
        drive("GASOLINA")
        coordinator.evaluate()
        assertEquals(listOf(AutoCalPointDeleteProtocol.Target(Fuel.PETROL, 6)), calls.single())
    }

    @Test
    fun `os dois fora da curva apagam um combustivel por vez com 5 s entre eles`() {
        buffers(Fuel.GAS, outlierBand = 6)
        buffers(Fuel.PETROL, outlierBand = 9)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6))
        now += 2_000
        drive("GASOLINA")
        coordinator.evaluate()
        assertEquals("intervalo global", 1, calls.size)
        now += 3_000
        drive("GASOLINA")
        coordinator.evaluate()
        assertEquals(listOf(AutoCalPointDeleteProtocol.Target(Fuel.PETROL, 9)), calls[1])
    }

    @Test
    fun `mesmo ponto fora da curva voltando igual tres vezes e apagado as tres vezes`() {
        // Decisão do dono: repetição não é "forma real"; parado o carro injeta mais e o ponto volta no mesmo lugar.
        buffers(Fuel.GAS)
        repeat(3) { cycle ->
            buffers(Fuel.GAS, outlierBand = 6, outlierCounter = 1 + cycle)
            drive("GNV")
            coordinator.evaluate()
            assertEquals("ciclo $cycle", cycle + 1, calls.size)
            coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6))
            now += 6_000
        }
        assertTrue(calls.all { it == listOf(AutoCalPointDeleteProtocol.Target(Fuel.GAS, 6)) })
    }

    @Test
    fun `colisao tentar depois nao consome intervalo`() {
        buffers(Fuel.GAS, outlierBand = 6)
        drive("GNV")
        nextResult = JSONObject().put("ok", false).put("retryLater", true).put("error", "Aguarde")
        coordinator.evaluate()
        nextResult = JSONObject().put("ok", true).put("started", true)
        now += 300
        drive("GNV")
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `sessao nova AUTO_CAL_ENABLE 0 e reconexao estabilizando nao apagam`() {
        buffers(Fuel.GAS, outlierBand = 6)
        coordinator.onSessionChanged(2L)
        drive("GNV")
        coordinator.evaluate()
        assertTrue("sessão nova esquece as leituras", calls.isEmpty())
        enabled = 0
        buffers(Fuel.GAS, outlierBand = 6, session = 2L)
        drive("GNV")
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
        enabled = 1
        sessionAge = 2_000
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `invalidacao do round esquece as leituras`() {
        buffers(Fuel.GAS, outlierBand = 6)
        coordinator.onRoundInvalidated()
        drive("GNV")
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `falha com mutacao possivel bloqueia ate releitura e 10 s`() {
        buffers(Fuel.GAS, outlierBand = 6)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionFailed(failed(mutation = true))
        now += 11_000
        drive("GNV")
        coordinator.evaluate()
        assertEquals("sem releitura ainda não", 1, calls.size)
        buffers(Fuel.GAS, outlierBand = 6)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `falha repetida na releitura de antes nao vira Delete a cada 500 ms e 5 seguidas pausam`() {
        buffers(Fuel.GAS, outlierBand = 6)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionFailed(failed(mutation = false))
        repeat(8) { now += 500; drive("GNV"); coordinator.evaluate() }
        assertEquals(1, calls.size)
        repeat(4) {
            now += 6_000
            drive("GNV")
            coordinator.evaluate()
            coordinator.onActionFailed(failed(mutation = false))
        }
        assertEquals(5, calls.size)
        now += 6_000
        drive("GNV")
        coordinator.evaluate()
        assertEquals(5, calls.size)
        assertEquals(AutoIdleCleanupCoordinator.PauseCode.REPEATED_FAILURES, coordinator.uiSummary().pauseCode)
        assertTrue(records.any { it.first == "autocal_auto_idle_disabled" })
    }

    @Test
    fun `guarda do outro combustivel pausa na conexao com motivo proprio`() {
        buffers(Fuel.PETROL, outlierBand = 6)
        drive("GASOLINA")
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmed(Fuel.PETROL, 6, otherAbnormal = true))
        assertFalse(coordinator.uiSummary().enabled)
        assertEquals(AutoIdleCleanupCoordinator.PauseCode.OTHER_FUEL_GUARD, coordinator.uiSummary().pauseCode)
        assertTrue(records.any { it.first == "autocal_auto_idle_disabled" && it.second.getString("reason").contains("GNV") })
        buffers(Fuel.GAS, outlierBand = 6)
        now += 10_000
        drive("GNV")
        coordinator.evaluate()
        assertEquals(1, calls.size)
        coordinator.onSessionChanged(2L)
        assertTrue(coordinator.uiSummary().enabled)
    }

    @Test
    fun `readback ineficaz pausa e ambiguo so espera releitura`() {
        buffers(Fuel.GAS, outlierBand = 6)
        drive("GNV")
        coordinator.evaluate()
        val ambiguous = confirmed(Fuel.GAS, 6)
        ambiguous.getJSONObject("details").put("readbackAmbiguous", true)
            .put("effect", JSONArray().put(JSONObject().put("index", 6).put("result", "AMBIGUOUS")))
        coordinator.onActionConfirmed(ambiguous)
        assertTrue(coordinator.uiSummary().enabled)
        assertEquals(0, coordinator.uiSummary().relearnedThisSession)
        assertTrue(coordinator.json().getJSONObject("policy").getBoolean("needsReread"))
        coordinator.onActionFailed(failed(mutation = true, effective = false))
        assertEquals(AutoIdleCleanupCoordinator.PauseCode.READBACK_INEFFECTIVE, coordinator.uiSummary().pauseCode)
    }

    @Test
    fun `sem controle local do MP48 e apagamento em voo nao disparam`() {
        buffers(Fuel.GAS, outlierBand = 6)
        actBlock = "Este aparelho não possui o controle principal do MP48"
        drive("GNV")
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
        actBlock = null
        coordinator.evaluate()
        now += 100
        drive("GNV")
        coordinator.evaluate()
        assertEquals(1, calls.size)
    }

    @Test
    fun `resumo da tela traz o combustivel de cada apagamento`() {
        buffers(Fuel.GAS, outlierBand = 6)
        buffers(Fuel.PETROL, outlierBand = 9)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6).put("id", "R-1").put("finishedAtMs", 1_234L))
        now += 6_000
        drive("GASOLINA")
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmed(Fuel.PETROL, 9).put("id", "R-2").put("finishedAtMs", 2_345L))
        val summary = coordinator.uiSummary()
        assertEquals(2, summary.relearnedThisSession)
        assertEquals(listOf(Fuel.GAS, Fuel.PETROL), summary.recentDeletes.map { it.fuel })
        assertEquals(listOf(listOf(6), listOf(9)), summary.recentDeletes.map { it.indexes })
    }

    // ---- apoio ----

    private fun mapRaw(band: Int) = 300 + 40 * band
    private fun timeRaw(band: Int) = ((1.5 + 0.25 * band) * 512).toInt()

    private var at = 1_000L

    private fun buffers(fuel: Fuel, outlierBand: Int? = null, outlierCounter: Int = 5, session: Long = 1L) {
        at = maxOf(at + 1, now)
        val counters = IntArray(18) { if (it < 16) 5 else 0 }
        val time = IntArray(18) { if (it < 16) timeRaw(it) else 0 }
        val map = IntArray(18) { if (it < 16) mapRaw(it) else 0 }
        if (outlierBand != null) {
            time[outlierBand] = (timeRaw(outlierBand) * 1.3).toInt() + outlierCounter // muda o buffer a cada ciclo
            counters[outlierBand] = outlierCounter
        }
        coordinator.onBuffers(AutoIdleCleanupCoordinator.Buffers(session, fuel, counters, time, map, at))
    }

    private fun drive(fuel: String) {
        frames += NativeAnchorTelemetryWindow.Frame(frames.size + 1L, now, 2_300, 0.7, 5.0, fuel)
    }

    private fun confirmed(fuel: Fuel, band: Int, otherAbnormal: Boolean = false) = JSONObject()
        .put("outcome", "CONFIRMED")
        .put("action", "DELETE_POINT")
        .put("automatic", true)
        .put("humanConfirmed", false)
        .put(
            "details",
            JSONObject()
                .put("fuel", fuel.wireName)
                .put("targets", JSONArray().put(JSONObject().put("fuel", fuel.wireName).put("index", band)))
                .put("effective", true)
                .put("otherFuelGuard", JSONObject().put("abnormal", otherAbnormal).put("changed", otherAbnormal)),
        )

    private fun failed(mutation: Boolean, effective: Boolean = true) = JSONObject()
        .put("outcome", "FAILED")
        .put("action", "DELETE_POINT")
        .put("automatic", true)
        .put("mutationMayHaveStarted", mutation)
        .put("pointDelete", JSONObject().put("fuel", "GAS").put("index", 6))
        .also { if (!effective) it.put("effective", false) }
}
