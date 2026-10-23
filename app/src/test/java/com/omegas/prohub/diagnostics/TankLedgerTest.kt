package com.omegas.prohub.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Livro-caixa de tanques: ciclos entre abastecimentos, idempotente por sessão, PROXY rotulado. */
class TankLedgerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val base = 1_790_000_000_000L

    /** Sessão sintética de [seconds] s: nível sobe de [from] a [to] (cilindro esvaziando), 36 km/h pela multimídia. */
    private fun session(
        id: String, startMs: Long, from: Int?, to: Int?, seconds: Int = 100, speed: Boolean = true,
        suspect: Boolean = false, refuelAt: Pair<Int, Int>? = null,
    ): JSONObject {
        val ctx = SummaryContext(sessionId = id, sessionUuid = "uuid-$id", startMs = if (suspect) 1_760_000_000_000L else startMs)
        val b = SessionSummaryBuilder(ctx)
        val w0 = ctx.startMs
        for (i in 0..seconds) {
            val level = when {
                from == null || to == null -> null
                refuelAt != null && i >= refuelAt.first -> refuelAt.second
                else -> from + (to - from) * (i.coerceIn(10, seconds - 10) - 10) / (seconds - 20)
            }
            val d = JSONObject().put("captured_elapsed_ms", 1_000_000L + i * 1000L).put("rpm", 1500)
                .put("fuel", "GNV").put("gas_ms_diagnostic", 8.0).put("gas_pulse_present", true)
            if (level != null) d.put("level_raw", level)
            b.observe(i * 2L + 1, "telemetry", "mp48", d, w0 + i * 1000L)
            if (speed) b.observe(i * 2L + 2, "car_info", "multimidia", JSONObject().put("speed", 36).put("complete", true), w0 + i * 1000L)
        }
        b.observe(9_999, "session_stopped", "native", JSONObject().put("reason", "manual"), w0 + seconds * 1000L + 10)
        return b.summary(final = true)
    }

    private fun ledger() = TankLedger(File(temporary.root, "tank_ledger.json"))

    @Test
    fun `duas sessoes seguidas formam um ciclo e a terceira apos abastecimento abre outro`() {
        val l = ledger()
        l.record(session("s1", base, 60, 100))
        l.record(session("s2", base + 1_000_000, 100, 140))
        l.record(session("s3", base + 2_000_000, 50, 70)) // 140 -> 50: queda de 90 = abastecimento
        val j = l.json()
        val cycles = j.getJSONArray("cycles")
        assertEquals(2, cycles.length())
        val c0 = cycles.getJSONObject(0)
        assertEquals("FIRST_SESSION", c0.getString("startedBy"))
        assertEquals(2, c0.getInt("sessions"))
        assertEquals(60, c0.getInt("startRaw"))
        assertEquals(140, c0.getInt("lastRaw"))
        assertEquals(80, c0.getInt("levelConsumedRaw"))
        assertEquals(2.0, c0.getJSONObject("km").getDouble("MM"), 0.01)
        assertEquals("REFUEL_BETWEEN_SESSIONS", cycles.getJSONObject(1).getString("startedBy"))
        assertEquals("s3", cycles.getJSONObject(1).getString("firstSessionId"))
        assertEquals(70, j.getInt("lastEndRaw"))
    }

    @Test
    fun `reprocessar a mesma sessao nao duplica e a versao nova substitui a velha`() {
        val l = ledger()
        val s1 = session("s1", base, 60, 100)
        l.record(s1)
        val once = l.json().toString()
        l.record(s1)
        l.record(JSONObject(s1.toString()))
        assertEquals(once, l.json().toString())
        assertEquals(1, l.json().getInt("sessionCount"))
        l.record(session("s1", base, 60, 120, seconds = 200))
        assertEquals(1, l.json().getInt("sessionCount"))
        assertEquals(120, l.json().getInt("lastEndRaw"))
        // arquivo em disco recarrega igual
        assertEquals(l.json().toString(), TankLedger(File(temporary.root, "tank_ledger.json")).json().toString())
    }

    @Test
    fun `km por unidade de nivel so aparece com dados suficientes e e rotulado PROXY`() {
        val l = ledger()
        l.record(session("s1", base, 60, 70)) // consumiu so 10: pouco
        val few = l.json().getJSONArray("cycles").getJSONObject(0)
        assertEquals(false, few.getBoolean("enoughData"))
        assertTrue(few.isNull("kmPerRawUnit"))
        assertTrue(few.isNull("injectorMinutesPer100Level"))
        l.record(session("s2", base + 1_000_000, 70, 130))
        val ok = l.json().getJSONArray("cycles").getJSONObject(0)
        assertEquals(true, ok.getBoolean("enoughData"))
        val kpl = ok.getJSONObject("kmPerRawUnit")
        assertEquals("PROXY", kpl.getString("label"))
        assertEquals("MM", kpl.getString("source"))
        assertEquals(2.0 / 70.0, kpl.getDouble("value"), 0.0005)
        assertEquals("PROXY", ok.getJSONObject("injectorMinutesPer100Level").getString("label"))
        assertTrue(ok.getJSONObject("injectorMinutesPer100Level").getDouble("value") > 0.0)
    }

    @Test
    fun `sem velocidade o km do ciclo e null e nao zero`() {
        val l = ledger()
        l.record(session("s1", base, 60, 130, speed = false))
        val c = l.json().getJSONArray("cycles").getJSONObject(0)
        assertTrue(c.getJSONObject("km").isNull("MM"))
        assertTrue(c.getJSONObject("km").isNull("GPS"))
        assertTrue(c.isNull("kmPerRawUnit"))
        assertEquals(1, c.getJSONObject("coverage").getInt("sessions"))
        assertEquals(0, c.getJSONObject("coverage").getInt("withMM"))
    }

    @Test
    fun `sessao sem nivel conhecido nao abre ciclo nem apaga a ultima leitura`() {
        val l = ledger()
        l.record(session("s1", base, 60, 100))
        l.record(session("s2", base + 1_000_000, null, null))
        assertEquals(1, l.json().getJSONArray("cycles").length())
        assertEquals(100, l.json().getInt("lastEndRaw"))
        assertEquals(2, l.json().getJSONArray("cycles").getJSONObject(0).getInt("sessions"))
    }

    @Test
    fun `abastecimento no meio da sessao abre ciclo dentro da propria sessao`() {
        val l = ledger()
        l.record(session("s1", base, 150, 160, seconds = 80, refuelAt = 40 to 50))
        val cycles = l.json().getJSONArray("cycles")
        assertEquals(2, cycles.length())
        assertEquals("REFUEL_IN_SESSION", cycles.getJSONObject(1).getString("startedBy"))
        assertEquals(50, cycles.getJSONObject(1).getInt("startRaw"))
    }

    @Test
    fun `relogio suspeito marca a ordem como incerta`() {
        val l = ledger()
        l.record(session("s1", base, 60, 100))
        l.record(session("s2", base + 1_000_000, 100, 140, suspect = true))
        val flagged = l.json().getJSONArray("cycles").getJSONObject(0)
        assertEquals(true, flagged.getBoolean("orderUncertain"))
    }

    @Test
    fun `tres sessoes curtas no mesmo dia sem abastecimento e uma com abastecimento no meio fecham o ciclo certo em qualquer ordem`() {
        // Sessão = qualquer intervalo de conexão. Nada exige abastecimento, parada ou ignição; ciclo é camada derivada.
        val a = session("a", base, 60, 75, seconds = 40)
        val b = session("b", base + 600_000, 75, 90, seconds = 40)
        val c = session("c", base + 1_200_000, 90, 105, seconds = 40)
        val d = session("d", base + 1_800_000, 40, 60, seconds = 40, refuelAt = null) // 105 -> 40: abastecimento entre sessões
        fun run(order: List<JSONObject>, dup: Boolean): JSONObject {
            val l = TankLedger(File(temporary.newFolder(), "tank_ledger.json"))
            order.forEach { l.record(it) }
            if (dup) l.record(order.first())
            return l.json()
        }
        val ordens = listOf(listOf(a, b, c, d), listOf(d, c, b, a), listOf(b, d, a, c))
        val ref = run(ordens[0], false).getJSONArray("cycles").toString()
        ordens.forEach { o -> assertEquals(ref, run(o, true).getJSONArray("cycles").toString()) }
        val cycles = run(ordens[0], false).getJSONArray("cycles")
        assertEquals(2, cycles.length())
        assertEquals(3, cycles.getJSONObject(0).getInt("sessions"))
        assertEquals(false, cycles.getJSONObject(0).getBoolean("open"))
        assertEquals("REFUEL_BETWEEN_SESSIONS", cycles.getJSONObject(1).getString("startedBy"))
        assertEquals(true, cycles.getJSONObject(1).getBoolean("open"))
        // sem nenhum abastecimento: um ciclo só, aberto, e nenhuma sessão é "fechada" por isso
        val l = TankLedger(File(temporary.newFolder(), "t.json"))
        listOf(a, b, c).forEach { l.record(it) }
        val only = l.json().getJSONArray("cycles")
        assertEquals(1, only.length()); assertEquals(true, only.getJSONObject(0).getBoolean("open"))
    }

    @Test
    fun `ordem de gravacao nao muda o resultado`() {
        val a = ledger()
        val s1 = session("s1", base, 60, 100)
        val s2 = session("s2", base + 1_000_000, 100, 140)
        a.record(s2); a.record(s1)
        val other = TankLedger(File(temporary.root, "outro.json"))
        other.record(s1); other.record(s2)
        assertEquals(other.json().getJSONArray("cycles").toString(), a.json().getJSONArray("cycles").toString())
        assertNotNull(a.lastEndRaw())
        assertNull(TankLedger(File(temporary.root, "novo.json")).lastEndRaw())
    }
}
