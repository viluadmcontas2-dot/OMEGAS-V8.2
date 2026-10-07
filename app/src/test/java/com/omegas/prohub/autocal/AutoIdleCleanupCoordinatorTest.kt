package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Fuel
import com.omegas.prohub.ecu.Mp48TelemetryWindowSource
import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Classe 2: sequências sintéticas da spec 2026-10-07 rev2 (fora da curva → combustível certo → execução → recibo),
 * mais o desenho aprovado de 2026-10-07: a limpeza começa DESARMADA em toda sessão USB e só o dono arma; um ponto
 * só vira candidato depois de DUAS leituras distintas e consecutivas do mesmo combustível.
 */
class AutoIdleCleanupCoordinatorTest {
    private var now = 100_000L
    private val frames = mutableListOf<NativeAnchorTelemetryWindow.Frame>()
    private val calls = mutableListOf<List<AutoCalPointDeleteProtocol.Target>>()
    private val evidences = mutableListOf<JSONObject>()
    private val records = mutableListOf<Pair<String, JSONObject>>()
    private var nextResult: JSONObject = JSONObject().put("ok", true).put("started", true)
    /** Identidade da última operação enfileirada (o gerenciador real devolve `preparationId` ao enfileirar). */
    private var lastPreparationId = ""
    private var enabled: Int? = 1
    private var sessionAge = 60_000L
    private var actBlock: String? = null
    private var changes = 0
    private var liveSession: Long? = null

    private val coordinator = AutoIdleCleanupCoordinator(
        telemetry = object : Mp48TelemetryWindowSource {
            override fun recentTelemetryFrames(fromElapsedMs: Long, toElapsedMs: Long) =
                frames.filter { it.elapsedMs in fromElapsedMs..toElapsedMs }
        },
        executeDelete = { targets, evidence ->
            calls += targets; evidences += evidence
            lastPreparationId = "ACA-AUTO-${calls.size}"
            JSONObject(nextResult.toString()).also { if (it.optBoolean("ok")) it.put("preparationId", lastPreparationId) }
        },
        autoCalEnabled = { enabled },
        sessionAgeMs = { sessionAge },
        canAct = { actBlock },
        record = { type, payload -> records += type to payload },
        clock = { now },
        executor = { it.run() },
        currentSessionId = { liveSession ?: 1L },
        onChanged = { changes += 1 },
    )

    /** Sessão USB 1 válida e o dono já tocou em "Ativar limpeza automática" (os cenários clássicos partem daqui). */
    @Before
    fun armedByOwner() {
        coordinator.onSessionChanged(1L)
        assertTrue(coordinator.setArmed(true, "teste").getBoolean("ok"))
    }

    private fun intList(array: JSONArray?): List<Int> = if (array == null) emptyList() else List(array.length()) { array.getInt(it) }

    // ---- desarmado por padrão / armar / desarmar ----

    @Test
    fun `sessao nova comeca desarmada e desarmada nao envia nenhum comando`() {
        liveSession = 2L
        coordinator.onSessionChanged(2L)
        assertFalse(coordinator.uiSummary().armed)
        assertFalse(coordinator.automaticEnabled())
        outlier(Fuel.GAS, 6, session = 2L)
        outlier(Fuel.PETROL, 9, session = 2L)
        drive("GNV")
        coordinator.evaluate()
        now += 6_000
        drive("GASOLINA")
        coordinator.evaluate()
        assertTrue("desarmado: zero comandos", calls.isEmpty())
        assertEquals(false, coordinator.json().getBoolean("armed"))
    }

    @Test
    fun `armar exige sessao valida e nao apaga no proprio toque`() {
        liveSession = 0L
        coordinator.onSessionChanged(0L)
        val refused = coordinator.setArmed(true, "teste")
        assertFalse(refused.getBoolean("ok"))
        assertFalse(coordinator.uiSummary().armed)
        liveSession = 3L
        coordinator.onSessionChanged(3L)
        outlier(Fuel.GAS, 6, session = 3L)
        drive("GNV")
        records.clear()
        val armed = coordinator.setArmed(true, "dono")
        assertTrue(armed.getBoolean("ok"))
        assertTrue(armed.getBoolean("armed"))
        assertTrue("o toque só arma; nada é enviado dentro dele", calls.isEmpty())
        val intent = records.single { it.first == "autocal_auto_cleanup_armed" }.second
        assertEquals(true, intent.getBoolean("armed"))
        assertEquals("dono", intent.getString("source"))
        assertEquals(3L, intent.getLong("sessionId"))
        coordinator.evaluate()
        assertEquals("a avaliação seguinte (armada) apaga", 1, calls.size)
    }

    @Test
    fun `desarmar impede disparos futuros e registra a intencao`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(1, calls.size)
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6))
        records.clear()
        assertTrue(coordinator.setArmed(false, "dono").getBoolean("ok"))
        assertFalse(coordinator.uiSummary().armed)
        assertEquals(false, records.single { it.first == "autocal_auto_cleanup_armed" }.second.getBoolean("armed"))
        now += 6_000
        outlier(Fuel.GAS, 6, counters = 20 to 21)
        drive("GNV")
        coordinator.evaluate()
        assertEquals("desarmado: nada mais sai", 1, calls.size)
    }

    @Test
    fun `sessao nova desarma`() {
        assertTrue(coordinator.uiSummary().armed)
        liveSession = 2L
        coordinator.onSessionChanged(2L)
        assertFalse(coordinator.uiSummary().armed)
        outlier(Fuel.GAS, 6, session = 2L)
        drive("GNV")
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `acao manual desarma e confirmacao automatica nao desarma`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6))
        assertTrue("a confirmação do próprio automático mantém a limpeza armada", coordinator.uiSummary().armed)
        coordinator.onRoundInvalidated()
        assertTrue("invalidação de leitura (round) não é toque do dono", coordinator.uiSummary().armed)
        coordinator.onManualMutation(JSONObject().put("action", "RESET_PETROL").put("outcome", "CONFIRMED"))
        assertFalse("reset/ação manual do dono desarma", coordinator.uiSummary().armed)
        now += 6_000
        outlier(Fuel.GAS, 6, counters = 20 to 21)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(1, calls.size)
    }

    @Test
    fun `falhas pausam e desarmam e rearmar limpa a pausa mas nao o bloqueio de mutacao incerta`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        repeat(4) {
            coordinator.onActionFailed(failed(mutation = false))
            assertFalse("toda falha real desarma", coordinator.uiSummary().armed)
            assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok")) // o dono rearma a cada falha
            now += 11_000
            outlier(Fuel.GAS, 6, counters = 30 + 2 * it to 31 + 2 * it)
            drive("GNV")
            coordinator.evaluate()
        }
        assertEquals(5, calls.size)
        coordinator.onActionFailed(failed(mutation = true)) // 5ª seguida, com mutação incerta: pausa + releitura obrigatória
        val paused = coordinator.uiSummary()
        assertFalse(paused.enabled)
        assertFalse("pausa por falha desarma: exige novo toque", paused.armed)
        assertEquals(AutoIdleCleanupCoordinator.PauseCode.REPEATED_FAILURES, paused.pauseCode)
        now += 11_000
        drive("GNV")
        coordinator.evaluate()
        assertEquals("pausada e desarmada: nada sai", 5, calls.size)

        val rearmed = coordinator.setArmed(true, "dono")
        assertTrue(rearmed.getBoolean("ok"))
        assertTrue(coordinator.uiSummary().armed)
        assertTrue(coordinator.uiSummary().enabled)
        assertNull(coordinator.uiSummary().pauseCode)
        drive("GNV")
        coordinator.evaluate()
        assertEquals("última falha com mutação incerta: sem releitura ainda não", 5, calls.size)
        outlier(Fuel.GAS, 6, counters = 50 to 51)
        drive("GNV")
        coordinator.evaluate()
        assertEquals("releitura dupla depois do rearme libera", 6, calls.size)
    }

    // ---- duas leituras distintas ----

    @Test
    fun `uma leitura nao basta e a mesma leitura com tempo novo confirma mas o mesmo tempo repetido nunca conta`() {
        buffers(Fuel.GAS, outlierBand = 6, outlierCounter = 5)
        drive("GNV")
        coordinator.evaluate()
        assertTrue("uma leitura só: espera", calls.isEmpty())
        // Mesmo timestamp (cache/repetição do poll): não é leitura nova.
        buffers(Fuel.GAS, outlierBand = 6, outlierCounter = 5, sameInstant = true)
        drive("GNV")
        coordinator.evaluate()
        assertTrue("o mesmo instante repetido não confirma", calls.isEmpty())
        // Poll fresco com conteúdo idêntico (anomalia estática): é a segunda leitura confirmada.
        buffers(Fuel.GAS, outlierBand = 6, outlierCounter = 5)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(listOf(AutoCalPointDeleteProtocol.Target(Fuel.GAS, 6)), calls.single())
    }

    @Test
    fun `leitura do outro combustivel no meio nao confirma a banda`() {
        buffers(Fuel.GAS, outlierBand = 6, outlierCounter = 5)
        buffers(Fuel.PETROL, outlierBand = 9, outlierCounter = 5)
        buffers(Fuel.PETROL, outlierBand = 9, outlierCounter = 6)
        drive("GNV")
        coordinator.evaluate()
        assertTrue("GNV só foi visto fora da curva uma vez", calls.isEmpty())
        drive("GASOLINA")
        coordinator.evaluate()
        assertEquals(listOf(AutoCalPointDeleteProtocol.Target(Fuel.PETROL, 9)), calls.single())
    }

    @Test
    fun `depois de apagar, uma banda nova fora da curva ainda precisa de duas leituras`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6))
        now += 6_000
        buffers(Fuel.GAS, outlierBand = 9, outlierCounter = 7, emptyBands = listOf(6))
        drive("GNV")
        coordinator.evaluate()
        assertEquals(1, calls.size)
        buffers(Fuel.GAS, outlierBand = 9, outlierCounter = 8, emptyBands = listOf(6))
        drive("GNV")
        coordinator.evaluate()
        assertEquals(listOf(AutoCalPointDeleteProtocol.Target(Fuel.GAS, 9)), calls[1])
    }

    @Test
    fun `apagamento nosso nao transforma ponto que era aceito em novo fora da curva so pela base menor`() {
        // Curva suave (base 5 ms), três bandas seguidas muito fora (6,7,8: teto de 3 rejeições do ajuste) e a banda 12
        // um pouco acima (7 %): na base cheia o ajuste gasta as 3 rejeições em 6,7,8 e aceita a 12.
        val wild = mapOf(6 to 1.3, 7 to 1.3, 8 to 1.3, 12 to 1.07)
        buffers(Fuel.GAS, factors = wild, timeBase = 5.0)
        buffers(Fuel.GAS, factors = wild, timeBase = 5.0)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(listOf(6, 7, 8), calls.single().map { it.index })
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, listOf(6, 7, 8)))
        now += 6_000
        // Bandas apagadas vazias e o resto com o MESMO dado: sem a base preservada, a 12 (e a vizinha 5, pelo buraco)
        // viraria "fora da curva" só porque a base encolheu.
        val shrunk = mapOf(12 to 1.07)
        repeat(3) {
            buffers(Fuel.GAS, factors = shrunk, timeBase = 5.0, emptyBands = listOf(6, 7, 8))
            drive("GNV")
            coordinator.evaluate()
        }
        assertEquals("só o encolhimento da base não apaga nada", 1, calls.size)
        assertEquals(listOf(6, 7, 8), intList(coordinator.json().getJSONObject("outliers").getJSONObject("GAS").optJSONArray("preservedBase")))
        // A ECU mediu a banda 12 de novo (dado novo, 30 % acima): julgada pelo dado atual, vira anomalia.
        buffers(Fuel.GAS, factors = mapOf(12 to 1.3), factorCounter = 9, timeBase = 5.0, emptyBands = listOf(6, 7, 8))
        buffers(Fuel.GAS, factors = mapOf(12 to 1.3), factorCounter = 9, timeBase = 5.0, emptyBands = listOf(6, 7, 8))
        drive("GNV")
        coordinator.evaluate()
        assertEquals(listOf(AutoCalPointDeleteProtocol.Target(Fuel.GAS, 12)), calls[1])
    }

    @Test
    fun `banda apagada readquirida volta ao dado atual e a base preservada e esquecida`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6))
        now += 6_000
        buffers(Fuel.GAS, emptyBands = listOf(6))
        assertEquals(listOf(6), intList(coordinator.json().getJSONObject("outliers").getJSONObject("GAS").optJSONArray("preservedBase")))
        outlier(Fuel.GAS, 6, counters = 20 to 21)
        assertEquals(0, coordinator.json().getJSONObject("outliers").getJSONObject("GAS").optJSONArray("preservedBase")?.length() ?: 0)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `escopo e motivo de espera em portugues simples`() {
        val policy = coordinator.json().getJSONObject("policy")
        assertEquals(listOf("GAS", "PETROL"), List(policy.getJSONArray("scope").length()) { policy.getJSONArray("scope").getString(it) })
        enabled = 0
        coordinator.evaluate()
        val waiting = coordinator.json().getJSONObject("policy").getString("lastDecision")
        assertFalse("sem código cru para o leigo: $waiting", waiting.contains("AUTO_CAL_ENABLE"))
        assertTrue(waiting, waiting.contains("aprendizado da ECU"))
    }

    // ---- motivo de espera para a tela / intenção manual / geração USB ----

    @Test
    fun `resumo da tela traz o motivo de espera em portugues simples e so muda quando algo muda`() {
        assertEquals("Aguardando a próxima leitura da ECU", coordinator.uiSummary().waitReason)
        outlier(Fuel.PETROL, 6)
        drive("GNV")
        coordinator.evaluate()
        val waiting = coordinator.uiSummary().waitReason
        assertEquals("Aguardando o carro rodar na gasolina", waiting)
        assertFalse(waiting, Regex("rpm|>=|ECU_|AUTO_CAL").containsMatchIn(waiting))
        val before = changes
        repeat(5) { coordinator.evaluate() }
        assertEquals("nada mudou: nenhuma revisão nova", before, changes)
        drive("GASOLINA")
        coordinator.evaluate()
        assertTrue("mudou (apagamento em voo): revisão nova", changes > before)
        assertEquals(1, calls.size)
    }

    @Test
    fun `desarmar e pausar assincronos publicam revisao para a tela`() {
        val before = changes
        coordinator.onManualMutation(JSONObject().put("action", "RESET_GAS").put("outcome", "CONFIRMED"))
        assertTrue(changes > before)
        assertFalse(coordinator.uiSummary().armed)
    }

    @Test
    fun `intencao manual desarma antes da escrita e a operacao automatica ja em voo ainda conclui`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(1, calls.size)
        assertTrue(coordinator.json().getBoolean("inFlight"))
        coordinator.onManualIntent("K_FACTOR_WRITE")
        assertFalse("o toque manual desarma na hora, antes de a escrita K começar", coordinator.uiSummary().armed)
        assertEquals("manual:K_FACTOR_WRITE", records.last { it.first == "autocal_auto_cleanup_armed" }.second.getString("source"))
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6))
        assertEquals("o recibo da operação em voo ainda conta", 1, coordinator.uiSummary().relearnedThisSession)
        assertFalse(coordinator.uiSummary().armed)
        now += 6_000
        outlier(Fuel.GAS, 6, counters = 20 to 21)
        drive("GNV")
        coordinator.evaluate()
        assertEquals("desarmado: nenhum disparo novo", 1, calls.size)
    }

    @Test
    fun `armar com a geracao USB diferente da sessao vista e recusado`() {
        liveSession = 2L // o cabo reconectou; o reset assíncrono da sessão ainda está na fila
        val refused = coordinator.setArmed(true, "dono")
        assertFalse(refused.getBoolean("ok"))
        assertFalse(coordinator.uiSummary().armed)
        assertTrue(refused.getString("error"), refused.getString("error").contains("USB"))
        coordinator.onSessionChanged(2L)
        assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok"))
        assertTrue(coordinator.uiSummary().armed)
    }

    // ---- cenários clássicos (spec rev2), agora com dono armado e duas leituras ----

    @Test
    fun `GNV fora da curva com carro em GNV apaga com evidencia fora da curva`() {
        outlier(Fuel.GAS, 6)
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
        buffers(Fuel.GAS, plainCounter = 6)
        buffers(Fuel.PETROL)
        buffers(Fuel.PETROL, plainCounter = 6)
        drive("GNV")
        coordinator.evaluate()
        drive("GASOLINA")
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `gasolina fora da curva com carro em GNV espera e com carro em gasolina apaga so a gasolina`() {
        buffers(Fuel.GAS)
        outlier(Fuel.PETROL, 6)
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
        outlier(Fuel.GAS, 6)
        outlier(Fuel.PETROL, 9)
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
    fun `mesmo ponto fora da curva voltando tres vezes e apagado as tres vezes, duas leituras por vez`() {
        // Decisão do dono: repetição não é "forma real"; parado o carro injeta mais e o ponto volta no mesmo lugar.
        buffers(Fuel.GAS)
        repeat(3) { cycle ->
            outlier(Fuel.GAS, 6, counters = 10 + 2 * cycle to 11 + 2 * cycle)
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
        outlier(Fuel.GAS, 6)
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
        outlier(Fuel.GAS, 6)
        liveSession = 2L
        coordinator.onSessionChanged(2L)
        assertTrue(coordinator.setArmed(true, "teste").getBoolean("ok"))
        drive("GNV")
        coordinator.evaluate()
        assertTrue("sessão nova esquece as leituras", calls.isEmpty())
        enabled = 0
        outlier(Fuel.GAS, 6, session = 2L)
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
        outlier(Fuel.GAS, 6)
        coordinator.onRoundInvalidated()
        drive("GNV")
        coordinator.evaluate()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `falha com mutacao possivel bloqueia ate releitura e 10 s`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionFailed(failed(mutation = true))
        assertTrue("rearme explícito do dono", coordinator.setArmed(true, "dono").getBoolean("ok"))
        now += 11_000
        drive("GNV")
        coordinator.evaluate()
        assertEquals("sem releitura ainda não", 1, calls.size)
        outlier(Fuel.GAS, 6, counters = 20 to 21)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    // ---- revisão 2026-10-07 (achados importantes): falha real desarma; releitura só do alvo, posterior e completa ----

    @Test
    fun `primeira falha real desarma ate novo toque e um tick fresco nao envia`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(1, calls.size)
        coordinator.onActionFailed(failed(mutation = false)) // primeira falha de transporte, nada enviado
        val summary = coordinator.uiSummary()
        assertFalse("primeira falha real desarma", summary.armed)
        assertTrue("sem pausa: é desarme, não pausa", summary.enabled)
        assertNull(summary.pauseCode)
        assertTrue(summary.waitReason, summary.waitReason.contains("falha") && summary.waitReason.contains("Ativar limpeza automática"))
        assertFalse(coordinator.json().getBoolean("inFlight"))
        assertEquals("failure", records.last { it.first == "autocal_auto_cleanup_armed" }.second.getString("source").substringBefore(':'))
        now += 11_000
        outlier(Fuel.GAS, 6, counters = 20 to 21)
        drive("GNV")
        coordinator.evaluate()
        assertEquals("desarmado: o tick fresco não envia", 1, calls.size)
        assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok"))
        assertEquals(AutoIdleCleanupCoordinator.ARMED_WAIT_REASON, coordinator.uiSummary().waitReason)
        drive("GNV")
        coordinator.evaluate()
        assertEquals("só o rearme explícito libera", 2, calls.size)
    }

    @Test
    fun `rearmar depois da falha preserva o intervalo de bloqueio e a releitura obrigatoria`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionFailed(failed(mutation = true))
        assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok"))
        now += 1_000
        outlier(Fuel.GAS, 6, counters = 20 to 21) // releitura completa do alvo, posterior à falha
        drive("GNV")
        coordinator.evaluate()
        assertEquals("bloqueio de 10 s preservado pelo rearme", 1, calls.size)
        assertFalse(coordinator.json().getJSONObject("policy").getBoolean("needsReread"))
        now += 10_000
        drive("GNV")
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `falha benigna sem nada enviado (ponto ja vazio ou readquirido) nao desarma`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionFailed(failed(mutation = false).put("emptyBands", JSONArray().put(6)))
        assertTrue("nada foi enviado e nada falhou na ECU: continua armada", coordinator.uiSummary().armed)
        now += 6_000
        buffers(Fuel.GAS, outlierBand = 9, outlierCounter = 7)
        buffers(Fuel.GAS, outlierBand = 9, outlierCounter = 8)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `recibo perdido (timeout) desarma, exige releitura do alvo e nao e esquecido pelo rearme`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        assertTrue(coordinator.json().getBoolean("inFlight"))
        now += AutoIdleCleanupCoordinator.IN_FLIGHT_TIMEOUT_MS + 1
        drive("GNV")
        coordinator.evaluate()
        val summary = coordinator.uiSummary()
        assertFalse(coordinator.json().getBoolean("inFlight"))
        assertFalse("resultado incerto não autoriza novo envio", summary.armed)
        assertTrue(summary.waitReason, summary.waitReason.contains("não respondeu"))
        assertTrue(coordinator.json().getJSONObject("policy").getBoolean("needsReread"))
        assertTrue(records.any { it.first == "autocal_auto_idle_timeout" })
        assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok"))
        now += 11_000
        outlier(Fuel.PETROL, 9) // outro combustível: não libera o GNV
        drive("GNV")
        coordinator.evaluate()
        assertEquals("sem releitura do alvo: nada sai", 1, calls.size)
        assertTrue(coordinator.json().getJSONObject("policy").getBoolean("needsReread"))
        outlier(Fuel.GAS, 6, counters = 20 to 21)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `releitura de outro combustivel, de instante velho ou igual, ou incompleta nao libera a releitura obrigatoria`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        val failedAt = now
        coordinator.onActionFailed(failed(mutation = true))
        assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok"))
        fun needsReread() = coordinator.json().getJSONObject("policy").getBoolean("needsReread")
        assertTrue(needsReread())
        buffers(Fuel.PETROL, outlierBand = 9)
        assertTrue("outro combustível", needsReread())
        buffers(Fuel.GAS, outlierBand = 6, outlierCounter = 20, instant = failedAt)
        assertTrue("mesmo instante da falha (replay)", needsReread())
        buffers(Fuel.GAS, outlierBand = 6, outlierCounter = 20, instant = failedAt - 50)
        assertTrue("instante anterior à falha", needsReread())
        now += 2_000
        buffers(Fuel.GAS, outlierBand = 6, outlierCounter = 20, incomplete = true)
        assertTrue("vetor incompleto (sem tempo/MAP)", needsReread())
        buffers(Fuel.GAS, outlierBand = 6, outlierCounter = 20)
        assertFalse("leitura completa, posterior e do alvo libera só a releitura", needsReread())
        drive("GNV")
        coordinator.evaluate()
        assertEquals("o bloqueio de 10 s continua", 1, calls.size)
        assertTrue(coordinator.uiSummary().waitReason, coordinator.uiSummary().waitReason.contains("depois de uma falha"))
        now += 9_000
        drive("GNV")
        coordinator.evaluate()
        assertEquals(2, calls.size)
    }

    @Test
    fun `contexto do apagamento em voo e revalidado pelo coordenador sem I-O`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        assertNull(coordinator.automaticContextReason(Fuel.GAS))
        assertTrue(coordinator.automaticContextReason(Fuel.PETROL)!!.contains("GNV"))
        drive("GASOLINA")
        assertTrue("combustível trocou", coordinator.automaticContextReason(Fuel.GAS)!!.contains("gasolina"))
        now += 100
        drive("GNV", rpm = 800)
        assertTrue("rpm caiu", coordinator.automaticContextReason(Fuel.GAS)!!.contains("rodar"))
        now += 100
        drive("GNV")
        assertNull(coordinator.automaticContextReason(Fuel.GAS))
        coordinator.onManualIntent("RESET_GAS")
        assertTrue("desarmado em voo", coordinator.automaticContextReason(Fuel.GAS)!!.contains("desligada"))
        assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok"))
        liveSession = 2L
        assertTrue("USB mudou em voo", coordinator.automaticContextReason(Fuel.GAS)!!.contains("USB"))
    }

    @Test
    fun `falha repetida na releitura de antes nao vira Delete a cada 500 ms e 5 seguidas pausam`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionFailed(failed(mutation = false))
        assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok")) // rearme explícito: o intervalo segue valendo
        repeat(8) { now += 500; drive("GNV"); coordinator.evaluate() }
        assertEquals(1, calls.size)
        repeat(4) {
            assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok"))
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
        assertFalse(coordinator.uiSummary().armed)
        assertTrue(records.any { it.first == "autocal_auto_idle_disabled" })
    }

    @Test
    fun `guarda do outro combustivel pausa na conexao com motivo proprio`() {
        outlier(Fuel.PETROL, 6)
        drive("GASOLINA")
        coordinator.evaluate()
        coordinator.onActionConfirmed(confirmed(Fuel.PETROL, 6, otherAbnormal = true))
        assertFalse(coordinator.uiSummary().enabled)
        assertFalse(coordinator.uiSummary().armed)
        assertEquals(AutoIdleCleanupCoordinator.PauseCode.OTHER_FUEL_GUARD, coordinator.uiSummary().pauseCode)
        assertTrue(records.any { it.first == "autocal_auto_idle_disabled" && it.second.getString("reason").contains("GNV") })
        outlier(Fuel.GAS, 6)
        now += 10_000
        drive("GNV")
        coordinator.evaluate()
        assertEquals(1, calls.size)
        liveSession = 2L
        coordinator.onSessionChanged(2L)
        assertTrue(coordinator.uiSummary().enabled)
        assertFalse("sessão nova: sem pausa, mas desarmada", coordinator.uiSummary().armed)
    }

    @Test
    fun `readback ineficaz pausa e ambiguo so espera releitura`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        val ambiguous = confirmed(Fuel.GAS, 6)
        ambiguous.getJSONObject("details").put("readbackAmbiguous", true)
            .put("effect", JSONArray().put(JSONObject().put("index", 6).put("result", "AMBIGUOUS")))
        coordinator.onActionConfirmed(ambiguous)
        assertTrue(coordinator.uiSummary().enabled)
        assertTrue(coordinator.uiSummary().armed)
        assertEquals(0, coordinator.uiSummary().relearnedThisSession)
        assertTrue(coordinator.json().getJSONObject("policy").getBoolean("needsReread"))
        coordinator.onActionFailed(failed(mutation = true, effective = false))
        assertEquals(AutoIdleCleanupCoordinator.PauseCode.READBACK_INEFFECTIVE, coordinator.uiSummary().pauseCode)
        assertFalse(coordinator.uiSummary().armed)
    }

    @Test
    fun `sem controle local do MP48 e apagamento em voo nao disparam`() {
        outlier(Fuel.GAS, 6)
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
        outlier(Fuel.GAS, 6)
        outlier(Fuel.PETROL, 9)
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

    // ---- identidade do recibo (revisão 2026-10-07 #5) ----

    @Test
    fun `recibo da sessao USB antiga nao conclui nem desarma a operacao da sessao nova`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        val oldPreparation = lastPreparationId
        liveSession = 2L
        coordinator.onSessionChanged(2L)
        assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok"))
        outlier(Fuel.PETROL, 9, session = 2L)
        drive("GASOLINA")
        coordinator.evaluate()
        assertEquals(2, calls.size)
        assertEquals("PETROL", coordinator.json().getString("inFlightFuel"))
        records.clear()
        coordinator.onActionFailed(failed(mutation = true, session = 1L, preparationId = oldPreparation))
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6, session = 1L, preparationId = oldPreparation))
        assertTrue("o voo da sessão nova continua", coordinator.json().getBoolean("inFlight"))
        assertTrue("recibo velho não desarma", coordinator.uiSummary().armed)
        assertFalse("recibo velho não bloqueia", coordinator.json().getJSONObject("policy").getBoolean("needsReread"))
        assertEquals(0, coordinator.uiSummary().relearnedThisSession)
        assertEquals(2, records.count { it.first == "autocal_auto_idle_receipt_ignored" })
        coordinator.onActionConfirmed(confirmed(Fuel.PETROL, 9, session = 2L))
        assertFalse(coordinator.json().getBoolean("inFlight"))
        assertEquals("o recibo certo conclui normalmente", 1, coordinator.uiSummary().relearnedThisSession)
        assertEquals(Fuel.PETROL, coordinator.uiSummary().recentDeletes.single().fuel)
    }

    @Test
    fun `recibo sem identidade ou de outra preparacao e ignorado e o voo segue`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        val noIdentity = confirmed(Fuel.GAS, 6).also { it.remove("preparationId"); it.remove("sessionId") }
        coordinator.onActionConfirmed(noIdentity)
        coordinator.onActionFailed(failed(mutation = false, preparationId = "ACA-AUTO-OUTRA"))
        assertTrue(coordinator.json().getBoolean("inFlight"))
        assertTrue(coordinator.uiSummary().armed)
        assertEquals(0, coordinator.uiSummary().relearnedThisSession)
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6))
        assertFalse(coordinator.json().getBoolean("inFlight"))
        assertEquals(1, coordinator.uiSummary().relearnedThisSession)
    }

    @Test
    fun `recibo atrasado de um voo que caiu no timeout nao apaga o bloqueio nem o voo seguinte`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        val timedOut = lastPreparationId
        now += AutoIdleCleanupCoordinator.IN_FLIGHT_TIMEOUT_MS + 1
        drive("GNV")
        coordinator.evaluate()
        assertFalse(coordinator.uiSummary().armed)
        assertTrue(coordinator.json().getJSONObject("policy").getBoolean("needsReread"))
        // Recibo atrasado do voo perdido: o resultado já virou incerto; não libera a releitura nem rearma.
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6, preparationId = timedOut))
        assertTrue(coordinator.json().getJSONObject("policy").getBoolean("needsReread"))
        assertFalse(coordinator.uiSummary().armed)
        assertEquals(0, coordinator.uiSummary().relearnedThisSession)
        assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok"))
        now += 11_000
        outlier(Fuel.GAS, 6, counters = 20 to 21)
        drive("GNV")
        coordinator.evaluate()
        assertEquals(2, calls.size)
        assertTrue(coordinator.json().getBoolean("inFlight"))
        coordinator.onActionFailed(failed(mutation = true, preparationId = timedOut))
        assertTrue("falha atrasada do voo antigo não derruba o voo novo", coordinator.json().getBoolean("inFlight"))
        assertTrue(coordinator.uiSummary().armed)
        coordinator.onActionConfirmed(confirmed(Fuel.GAS, 6))
        assertFalse(coordinator.json().getBoolean("inFlight"))
        assertEquals(1, coordinator.uiSummary().relearnedThisSession)
    }

    @Test
    fun `invalidacao de round ou confirmacao manual nao libera a releitura obrigatoria nem o bloqueio`() {
        outlier(Fuel.GAS, 6)
        drive("GNV")
        coordinator.evaluate()
        coordinator.onActionFailed(failed(mutation = true))
        coordinator.onRoundInvalidated()
        coordinator.onManualMutation(JSONObject().put("action", "RESET_PETROL").put("outcome", "CONFIRMED"))
        assertTrue(coordinator.setArmed(true, "dono").getBoolean("ok"))
        val policy = coordinator.json().getJSONObject("policy")
        assertTrue("invalidação de round não é leitura do alvo", policy.getBoolean("needsReread"))
        assertTrue(policy.getLong("blockedUntilElapsedMs") > now)
        now += 11_000
        outlier(Fuel.PETROL, 9)
        drive("GNV")
        coordinator.evaluate()
        assertEquals("sem releitura completa do GNV: nada sai", 1, calls.size)
        outlier(Fuel.GAS, 6, counters = 20 to 21) // leitura completa, nova e posterior do alvo
        drive("GNV")
        coordinator.evaluate()
        assertFalse(coordinator.json().getJSONObject("policy").getBoolean("needsReread"))
        assertEquals(2, calls.size)
    }

    private fun mapRaw(band: Int) = 300 + 40 * band
    private fun timeRaw(band: Int, base: Double = 1.5) = ((base + 0.25 * band) * 512).toInt()

    private var at = 1_000L

    /** Duas leituras distintas e consecutivas com a mesma banda fora da curva (o que a regra exige). */
    private fun outlier(fuel: Fuel, band: Int, session: Long = 1L, counters: Pair<Int, Int> = 5 to 6) {
        buffers(fuel, outlierBand = band, outlierCounter = counters.first, session = session)
        buffers(fuel, outlierBand = band, outlierCounter = counters.second, session = session)
    }

    private fun buffers(
        fuel: Fuel,
        outlierBand: Int? = null,
        outlierCounter: Int = 5,
        session: Long = 1L,
        plainCounter: Int = 5,
        emptyBands: List<Int> = emptyList(),
        /** Mesmo instante da leitura anterior (poll repetido/cache): não é leitura nova. */
        sameInstant: Boolean = false,
        /** Bandas com o tempo multiplicado (anomalias fixas, sem variar o buffer a cada leitura). */
        factors: Map<Int, Double> = emptyMap(),
        factorCounter: Int = 5,
        /** ms da banda 0 (curva mais suave = menos resíduo de concavidade nos buracos). */
        timeBase: Double = 1.5,
        /** Instante explícito da leitura (replay de leitura velha). */
        instant: Long? = null,
        /** Só contadores (sem tempo/MAP): leitura incompleta, não confirma nada. */
        incomplete: Boolean = false,
    ) {
        if (instant != null) at = instant else if (!sameInstant) at = maxOf(at + 1, now)
        val counters = IntArray(18) { if (it < 16) plainCounter else 0 }
        val time = IntArray(18) { if (it < 16) timeRaw(it, timeBase) else 0 }
        val map = IntArray(18) { if (it < 16) mapRaw(it) else 0 }
        factors.forEach { (band, factor) -> time[band] = (timeRaw(band, timeBase) * factor).toInt(); counters[band] = factorCounter }
        emptyBands.forEach { counters[it] = 0; time[it] = 0; map[it] = 0 }
        if (outlierBand != null) {
            time[outlierBand] = (timeRaw(outlierBand) * 1.3).toInt() + outlierCounter // muda o buffer a cada leitura
            counters[outlierBand] = outlierCounter
        }
        coordinator.onBuffers(
            AutoIdleCleanupCoordinator.Buffers(
                session, fuel, counters, if (incomplete) null else time, if (incomplete) null else map, at,
            ),
        )
    }

    private fun drive(fuel: String, rpm: Int = 2_300) {
        frames += NativeAnchorTelemetryWindow.Frame(frames.size + 1L, now, rpm, 0.7, 5.0, fuel)
    }

    private fun confirmed(
        fuel: Fuel,
        band: Int,
        otherAbnormal: Boolean = false,
        session: Long = 1L,
        preparationId: String = lastPreparationId,
    ) = confirmed(fuel, listOf(band), otherAbnormal, session, preparationId)

    /** Recibo real do gerenciador: sempre com `sessionId` (geração USB) e `preparationId` da operação. */
    private fun confirmed(
        fuel: Fuel,
        bands: List<Int>,
        otherAbnormal: Boolean = false,
        session: Long = 1L,
        preparationId: String = lastPreparationId,
    ) = JSONObject()
        .put("id", "RECEIPT-$preparationId")
        .put("outcome", "CONFIRMED")
        .put("action", "DELETE_POINT")
        .put("automatic", true)
        .put("humanConfirmed", false)
        .put("sessionId", session)
        .put("preparationId", preparationId)
        .put(
            "details",
            JSONObject()
                .put("fuel", fuel.wireName)
                .put("targets", JSONArray().also { array -> bands.forEach { array.put(JSONObject().put("fuel", fuel.wireName).put("index", it)) } })
                .put("effective", true)
                .put("otherFuelGuard", JSONObject().put("abnormal", otherAbnormal).put("changed", otherAbnormal)),
        )

    private fun failed(
        mutation: Boolean,
        effective: Boolean = true,
        session: Long = 1L,
        preparationId: String = lastPreparationId,
    ) = JSONObject()
        .put("id", "RECEIPT-$preparationId")
        .put("outcome", "FAILED")
        .put("action", "DELETE_POINT")
        .put("automatic", true)
        .put("sessionId", session)
        .put("preparationId", preparationId)
        .put("mutationMayHaveStarted", mutation)
        .put("pointDelete", JSONObject().put("fuel", "GAS").put("index", 6))
        .also { if (!effective) it.put("effective", false) }
}
