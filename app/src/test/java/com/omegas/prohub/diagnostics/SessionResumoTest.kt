package com.omegas.prohub.diagnostics

import com.omegas.prohub.autocal.StallWatch
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.TimeZone

/**
 * Classe 2: o RESUMO.md é montado dos mesmos eventos que a sessão grava, e uma sessão que
 * morreu sem fechar é reconstruída dos arquivos com o mesmo conteúdo.
 */
class SessionResumoTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val dir: File = Files.createTempDirectory("resumo").toFile()
    private var sequence = 0L
    private val t0 = 1_760_000_000_000L // 2025-10-09 08:53:20 UTC
    private val live = SessionResumo("session_teste", t0, utc)

    @After fun cleanup() { dir.deleteRecursively() }

    /** Faz o que o SessionRecorder faz: escreve a linha no events_0001.jsonl e alimenta o resumo vivo. */
    private fun log(type: String, data: JSONObject, at: Long) {
        sequence += 1
        val line = JSONObject().put("format", "omegas-session-log-v1").put("sequence", sequence)
            .put("recordedAtMs", at).put("type", type).put("source", "test").put("data", data)
        File(dir, "events_0001.jsonl").appendText(line.toString() + "\n")
        live.observe(type, data, at)
    }

    private fun telemetryNoise(from: Long, count: Int) {
        repeat(count) { log("telemetry", JSONObject().put("rpm", 1500).put("fuel", "GNV"), from + it * 250L) }
    }

    private fun manifest(stoppedAt: Long? = null) {
        File(dir, "manifest.json").writeText(
            JSONObject().put("sessionId", "session_teste").put("startedAtMs", t0)
                .put("stoppedAtMs", stoppedAt ?: 0L).toString(),
        )
    }

    /** Uma sessão inteira com tudo que o Refino conta: fases, quase-apagão, apagão que religa, gravação, veredito. */
    private fun driveSession() {
        manifest()
        log("session_started", JSONObject().put("reason", "USB"), t0)
        telemetryNoise(t0 + 1_000, 200)
        log("refinement_phase", JSONObject().put("phase", "ECU_TRABALHANDO").put("headline", "A ECU está no automático"), t0 + 60_000)
        log("refinement_phase", JSONObject().put("phase", "COLETANDO_NOSSOS").put("headline", "Dirija para medir"), t0 + 600_000)
        log("refinement_phase", JSONObject().put("phase", "COLETANDO_NOSSOS").put("headline", "repetida"), t0 + 610_000) // não repete

        // Apagão real passando pelo detector de verdade, como o serviço faz.
        val watch = StallWatch(null)
        var t = t0 + 700_000
        fun frame(rpm: Double, speed: Double? = null) {
            val event = watch.accept(StallWatch.Frame(t, "GNV", rpm, 0.31, 2.2, speed))
            if (event != null) log("engine_stall", event, t)
            watch.drainAnnotations().forEach { log("engine_stall_after", it, t) }
            t += 100
        }
        repeat(25) { frame(1_800.0 - it * 40.0, speed = 24.0) }
        repeat(12) { frame(0.0, speed = 18.0) }
        repeat(8) { frame(900.0) }

        log("k_factor_batch_confirmed", JSONObject().put("adjustmentId", "KF-7").put("points", JSONArray(List(30) { it })), t0 + 900_000)
        log("refinement_verdict", JSONObject().put("id", "exp1").put("status", "PIOROU_EM_PARTE").put("appliedAt", t0 + 900_000)
            .put("ratioBefore", 1.05).put("ratioAfter", 1.08)
            .put("bands", JSONArray()
                .put(JSONObject().put("fromMs", 3.0).put("toMs", 4.5).put("verdict", "CONFIRMADA"))
                .put(JSONObject().put("fromMs", 4.5).put("toMs", 6.0).put("verdict", "PIOROU"))
                .put(JSONObject().put("fromMs", 6.0).put("toMs", 7.5).put("verdict", "NAO_ALTERADA"))), t0 + 1_500_000)
        log("refinement_verdict", JSONObject().put("id", "exp1").put("status", "PIOROU_EM_PARTE"), t0 + 1_510_000) // mesmo id: uma vez só
        telemetryNoise(t0 + 1_520_000, 100)
    }

    @Test
    fun `o resumo conta fases, apagao e o que veio depois, gravacao e veredito em palavras simples`() {
        driveSession()
        log("session_stopped", JSONObject().put("reason", "MP48 desconectado"), t0 + 2_000_000)
        val md = live.markdown()
        assertTrue(md, md.contains("Estado: FECHADA (MP48 desconectado)"))
        assertTrue(md, md.contains("ECU trabalhando no automático: A ECU está no automático"))
        assertTrue(md, md.contains("coletando os pontos do OMEGAS: Dirija para medir"))
        assertEquals("fase repetida não aparece duas vezes", 1, Regex("coletando os pontos").findAll(md).count())
        assertTrue(md, md.contains("Apagou: 1 (religou: 1). Quase apagou: 0."))
        assertTrue(md, md.contains("APAGOU a 2,20 ms"))
        assertTrue(md, md.contains("24 km/h"))
        assertTrue(md, md.contains("depois: o motor religou em"))
        assertTrue(md, md.contains("Curva K: 30 pontos, ajuste KF-7"))
        assertTrue(md, md.contains("piorou em parte (trecho a restaurar)"))
        assertTrue(md, md.contains("razão GNV/gasolina 1,050 para 1,080"))
        assertTrue(md, md.contains("faixa 4,5 a 6,0 ms: piorou"))
        assertFalse("faixa não alterada não entra", md.contains("6,0 a 7,5"))
        assertEquals("o mesmo veredito não entra duas vezes", 1, Regex("gravação das").findAll(md).count())
    }

    @Test
    fun `quase apagao que vira apagao e um acontecimento so`() {
        val resumo = SessionResumo("s", t0, utc)
        resumo.observe("engine_stall", JSONObject().put("kind", "QUASE_APAGOU").put("at", t0 + 1_000).put("petrolMs", 2.0).put("mapBar", 0.3).put("rpmBefore", 1500).put("rpmMin", 450), t0 + 1_000)
        resumo.observe("engine_stall", JSONObject().put("kind", "APAGOU").put("at", t0 + 3_000).put("petrolMs", 2.0).put("mapBar", 0.3).put("rpmBefore", 1500).put("rpmMin", 0), t0 + 3_000)
        val md = resumo.markdown()
        assertTrue(md, md.contains("Apagou: 1 (religou: 0). Quase apagou: 0."))
        assertTrue("apagão sem anotação diz que a sessão acabou antes", md.contains("sem anotação"))
    }

    @Test
    fun `sessao morta sem fechar e reconstruida dos eventos com o mesmo conteudo`() {
        driveSession() // nunca chega session_stopped: o app foi morto
        // A última linha foi cortada no meio pela queda de energia.
        File(dir, "events_0001.jsonl").appendText("{\"format\":\"omegas-session-log-v1\",\"sequence\":99999,\"type\":\"refinement_pha")
        assertFalse(File(dir, SessionResumo.FILE_NAME).exists())

        assertTrue(SessionResumo.rebuildIfOpen(dir, utc))
        val rebuilt = File(dir, SessionResumo.FILE_NAME).readText()
        assertTrue(rebuilt, rebuilt.contains(SessionResumo.OPEN_MARK))
        assertTrue(rebuilt, rebuilt.contains("Último registro"))

        fun body(md: String) = md.lines().filter { it.startsWith("- ") || it.startsWith("  - ") }
        assertEquals("reconstruído = o que a sessão viva contaria", body(live.markdown()), body(rebuilt))
        assertTrue(body(rebuilt).any { it.contains("APAGOU") })
        assertTrue(body(rebuilt).any { it.contains("Curva K: 30 pontos") })
    }

    @Test
    fun `sessao ja fechada nao e reescrita pela reconstrucao`() {
        driveSession()
        log("session_stopped", JSONObject().put("reason", "manual"), t0 + 2_000_000)
        File(dir, SessionResumo.FILE_NAME).writeText(live.markdown())
        val before = File(dir, SessionResumo.FILE_NAME).readText()
        assertFalse(SessionResumo.rebuildIfOpen(dir, utc))
        assertEquals(before, File(dir, SessionResumo.FILE_NAME).readText())
    }

    @Test
    fun `sessao sem nada para contar diz isso em vez de ficar em branco`() {
        manifest()
        log("session_started", JSONObject(), t0)
        telemetryNoise(t0 + 1_000, 50)
        SessionResumo.rebuildIfOpen(dir, utc)
        val md = File(dir, SessionResumo.FILE_NAME).readText()
        assertTrue(md, md.contains("Nenhuma mudança de fase nesta sessão."))
        assertTrue(md, md.contains("Apagou: 0 (religou: 0). Quase apagou: 0."))
        assertTrue(md, md.contains("Nenhuma gravação confirmada nesta sessão."))
        assertTrue(md, md.contains("Nenhuma verificação terminou nesta sessão."))
    }
}
