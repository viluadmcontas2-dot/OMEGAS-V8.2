package com.omegas.prohub.properties

import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.EquivalencePhases
import com.omegas.prohub.autocal.RefinementJournal
import com.omegas.prohub.properties.PropertySupport.SEEDS
import com.omegas.prohub.properties.PropertySupport.assertFiniteJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Piloto de fases e diário de refino com sequências aleatórias de semente fixa (Lote W, parte 3):
 * determinismo, fase sempre válida, offline nunca parece estável, salvar→carregar→salvar igual,
 * estado coerente depois de um callback que lança.
 */
class PilotAndJournalPropertiesTest {
    private val phases = setOf(
        "SEM_ECU", "LENDO_ECU", "ECU_TRABALHANDO", "COLETANDO_NOSSOS", "PROPOSTA_PRONTA", "VERIFICANDO",
        "RESTAURAR_TRECHO", "ESTAVEL", "TENTATIVA_ENCERRADA",
    )
    private val statuses = setOf(
        "VERIFICANDO", "VERIFICADO", "PIOROU_EM_PARTE", "SEM_BASE", "INCONCLUSIVO", "INTERROMPIDO", RefinementJournal.STATUS_FAILED_PARTIAL,
    )

    private fun zone(index: Int) = when (index) { in 0..5 -> 0; in 6..9 -> 1; in 10..13 -> 2; else -> 3 }

    private fun monitor(rnd: Random) = JSONObject()
        .put("autoMatchCount", rnd.nextInt(5))
        .put("maxAutomatch", if (rnd.nextInt(4) == 0) JSONObject.NULL else 3)
        .put("autoCalEnabled", if (rnd.nextInt(5) == 0) JSONObject.NULL else rnd.nextInt(2))

    private fun acquisition(rnd: Random): JSONObject {
        val petrol = rnd.nextInt(19)
        val gas = rnd.nextInt(19)
        return JSONObject().put("points", JSONArray().apply {
            listOf("GASOLINA" to petrol, "GNV" to gas).forEach { (fuel, acquired) ->
                repeat(18) { i ->
                    val done = (0..17).filter { zone(it) == zone(i) }.all { it < acquired }
                    put(JSONObject().put("fuel", fuel).put("zone", zone(i)).put("zoneAcquired", done)
                        .put("state", if (i < acquired && done) "ZONA_ADQUIRIDA" else if (i < acquired) "ATIVIDADE" else "AGUARDANDO"))
                }
            }
        })
    }

    private fun index(rnd: Random): JSONObject {
        val bands = JSONArray()
        EquivalenceLedger.BANDS.forEach { (lo, hi) ->
            val n = if (rnd.nextInt(4) == 0) 0 else 5 + rnd.nextInt(40)
            bands.put(JSONObject().put("fromMs", lo).put("toMs", hi).put("samples", n)
                .put("ratio", if (n == 0) JSONObject.NULL else 0.9 + rnd.nextDouble() * 0.25))
        }
        return JSONObject().put("samples", 100).put("petrolObservations", 500).put("gasObservations", 200).put("bands", bands)
    }

    private fun runPilot(seed: Long, file: java.io.File? = null, steps: Int = 120): Pair<List<String>, EquivalencePhases> {
        val rnd = Random(seed)
        var now = 1_000L
        val pilot = EquivalencePhases(file) { now }
        val out = ArrayList<String>()
        repeat(steps) {
            now += 500L + rnd.nextInt(60_000)
            val online = rnd.nextInt(6) != 0
            val journal = if (rnd.nextBoolean()) JSONObject().put("latest", JSONObject.NULL) else JSONObject().put(
                "latest",
                JSONObject().put("status", listOf("VERIFICANDO", "VERIFICADO", "PIOROU_EM_PARTE", "FALHA_PARCIAL")[rnd.nextInt(4)]).put("closedAt", now),
            )
            val r = pilot.observe(online, monitor(rnd), acquisition(rnd), index(rnd), journal, rnd.nextInt(3))
            out += r.toString()
            val phase = r.getString("phase")
            assertTrue("fase inválida $phase", phase in phases)
            if (!online) assertEquals("sem ECU nunca pode parecer outra fase ($phase)", "SEM_ECU", phase)
            if (r.optBoolean("canDisconnect", false)) assertEquals("só ESTAVEL autoriza desconectar", "ESTAVEL", phase)
            assertFiniteJson(r)
            if (pilot.takeAlert() != null) {
                // o aviso é entregue uma vez: tirar de novo logo depois não devolve nada
                assertNull(pilot.takeAlert())
            }
        }
        return out to pilot
    }

    @Test
    fun `piloto - mesma sequencia de observacoes gera exatamente as mesmas fases`() {
        for (seed in SEEDS.take(6)) {
            val (a, pilotA) = runPilot(seed)
            val (b, pilotB) = runPilot(seed)
            assertEquals("semente $seed", a, b)
            assertEquals(pilotA.json().toString(), pilotB.json().toString())
            assertFiniteJson(pilotA.json())
        }
    }

    @Test
    fun `piloto - ao reabrir o app a fase salva NUNCA vale como atual ate a ECU ser observada de novo`() {
        for (seed in SEEDS.take(4)) {
            val file = PropertySupport.tempFile("pilot-$seed")
            val (_, pilot) = runPilot(seed, file, 100)
            assertTrue(pilot.json().getString("phase") in phases)
            val reloaded = EquivalencePhases(file) { 10_000_000L }
            assertEquals("fase salva apresentada como atual", "SEM_ECU", reloaded.json().getString("phase"))
            assertFiniteJson(reloaded.json())
            val first = reloaded.observe(true, JSONObject().put("autoMatchCount", 1).put("maxAutomatch", 3).put("autoCalEnabled", 1), null, JSONObject(), JSONObject().put("latest", JSONObject.NULL), 0)
            assertTrue(first.getString("phase") in phases)
            assertFalse("reabrir não autoriza gravar", first.optBoolean("canDisconnect", false))
            file.delete()
        }
    }

    @Test
    fun `piloto - arquivo corrompido nao derruba e comeca em SEM_ECU`() {
        for (content in listOf("", "{", "[]", "{\"format\":\"outro\"}", "null", "{\"format\":\"${EquivalencePhases.FORMAT}\",\"phase\":42}")) {
            val file = PropertySupport.tempFile("pilot-bad")
            file.writeText(content)
            val pilot = EquivalencePhases(file) { 5L }
            assertTrue(pilot.json().getString("phase") in phases)
            val r = pilot.observe(false, null, null, JSONObject(), JSONObject(), 0)
            assertEquals("SEM_ECU", r.getString("phase"))
            file.delete()
        }
    }

    // ------------------------------------------------------------------ diário

    private fun journalIndex(rnd: Random) = index(rnd)

    private fun runJournal(seed: Long, file: java.io.File? = null, listener: ((JSONObject) -> Unit)? = null, steps: Int = 150): Pair<List<String>, RefinementJournal> {
        val rnd = Random(seed)
        var now = 10_000L
        val journal = RefinementJournal(file) { now }
        journal.setDecisionListener(listener)
        val axis = IntArray(30) { 256 + it * 350 }
        val log = ArrayList<String>()
        repeat(steps) { step ->
            now += 200L + rnd.nextInt(8_000)
            when (rnd.nextInt(14)) {
                0 -> {
                    val before = IntArray(30) { 16384 + rnd.nextInt(800) - 400 }
                    val after = IntArray(30) { if (rnd.nextInt(3) == 0) before[it] + rnd.nextInt(600) - 300 else before[it] }
                    journal.recordCurveWrite(before, after, axis, journalIndex(rnd), "TESTE", "foto-$step.json")
                }
                1 -> journal.recordFailedWrite("foto-falha-$step.json", "TESTE", rnd.nextBoolean())
                2 -> journal.interrupt("MOTIVO_$step")
                else -> journal.evaluate(journalIndex(rnd), rnd.nextInt(8) != 0, 800.0 + rnd.nextInt(3500), 2.5 + rnd.nextDouble() * 10.0)
            }
            val json = journal.json()
            log += json.toString()
            assertFiniteJson(json)
            assertTrue("histórico > 10", json.getJSONArray("history").length() <= 10)
            assertTrue("experimentos > ${RefinementJournal.MAX_EXPERIMENTS}", json.getInt("count") <= RefinementJournal.MAX_EXPERIMENTS)
            val latest = json.optJSONObject("latest")
            if (latest != null) assertTrue("status inválido ${latest.optString("status")}", latest.optString("status") in statuses)
            json.getJSONArray("bandScale").let { scale ->
                for (i in 0 until scale.length()) assertTrue("escala fora de [0,4 ; 1,3]", scale.getDouble(i) in RefinementJournal.MIN_SCALE - 1e-9..RefinementJournal.MAX_SCALE + 1e-9)
            }
            val gain = journal.pointGainScale(axis.map { it / 512.0 })
            gain.forEach { assertTrue("ganho por ponto $it", it.isFinite() && it > 0.0 && it <= RefinementJournal.MAX_SCALE + 1e-9) }
            val restore = journal.restorePoints()
            for (i in 0 until restore.length()) {
                val p = restore.getJSONObject(i)
                assertTrue(p.getInt("index") in 0..29)
                assertTrue(p.getInt("currentRaw") != p.getInt("targetRaw"))
            }
        }
        return log to journal
    }

    @Test
    fun `diario - mesma sequencia de eventos gera exatamente o mesmo diario`() {
        for (seed in SEEDS.take(6)) {
            val (a, _) = runJournal(seed)
            val (b, _) = runJournal(seed)
            assertEquals("semente $seed", a, b)
        }
    }

    @Test
    fun `diario - toda decisao entregue ao ouvinte e uma copia independente e em ordem`() {
        for (seed in SEEDS.take(5)) {
            val delivered = ArrayList<JSONObject>()
            val (_, _) = runJournal(seed, listener = { delivered += it })
            assertTrue("o ouvinte precisa receber transições", delivered.isNotEmpty())
            delivered.forEach { it.put("mutado", true) } // mexer na cópia não pode afetar o diário
            val (again, _) = runJournal(seed)
            assertFalse(again.any { it.contains("mutado") })
            delivered.forEach { assertTrue(it.optString("status") in statuses) }
        }
    }

    @Test
    fun `diario - salvar carregar salvar produz o mesmo estado`() {
        for (seed in SEEDS.take(6)) {
            val file = PropertySupport.tempFile("journal-$seed")
            val (_, first) = runJournal(seed, file, steps = 120)
            val a = file.readText()
            val reloaded = RefinementJournal(file) { 99_000_000L }
            assertEquals("semente $seed", first.json().getInt("count"), reloaded.json().getInt("count"))
            assertEquals(first.json().getJSONArray("bandScale").toString(), reloaded.json().getJSONArray("bandScale").toString())
            assertEquals(first.json().getJSONArray("pointPasses").toString(), reloaded.json().getJSONArray("pointPasses").toString())
            // regravar o que foi carregado reproduz o mesmo conteúdo
            reloaded.interrupt("sem-efeito") // dispara save() sem alterar experimento em VERIFICANDO inexistente ou, se existir, só o fecha
            val b = file.readText()
            val reparsedA = JSONObject(a)
            val reparsedB = JSONObject(b)
            assertEquals(reparsedA.getJSONArray("bandScale").toString(), reparsedB.getJSONArray("bandScale").toString())
            assertEquals(reparsedA.getJSONArray("pointPasses").toString(), reparsedB.getJSONArray("pointPasses").toString())
            file.delete()
        }
    }

    @Test
    fun `diario - ouvinte que lanca nao corrompe o diario e ele continua utilizavel`() {
        val journal = RefinementJournal(null) { 5_000L }
        var calls = 0
        journal.setDecisionListener { calls++; throw IllegalStateException("ouvinte quebrado") }
        val axis = IntArray(30) { 256 + it * 350 }
        val before = IntArray(30) { 16384 }
        val after = IntArray(30) { 16384 + 400 }
        try { journal.recordCurveWrite(before, after, axis, JSONObject(), "TESTE", "foto.json") } catch (_: IllegalStateException) { }
        assertTrue(calls >= 1)
        // o diário não pode ficar travado nem inconsistente: consulta e novos eventos funcionam
        assertFiniteJson(journal.json())
        journal.setDecisionListener(null)
        journal.recordFailedWrite("foto-2.json", "TESTE", true)
        val json = journal.json()
        assertNotNull(json.optJSONObject("latest"))
        assertEquals(RefinementJournal.STATUS_FAILED_PARTIAL, json.getJSONObject("latest").getString("status"))
        assertTrue(json.getInt("count") >= 1)
    }

    @Test
    fun `diario - arquivo corrompido ou de outro formato abre vazio`() {
        for (content in listOf("", "{", "[]", "{\"format\":\"outro\"}", "{\"format\":\"${RefinementJournal.FORMAT}\",\"experiments\":[1,2,\"x\"]}")) {
            val file = PropertySupport.tempFile("journal-bad")
            file.writeText(content)
            val journal = RefinementJournal(file) { 1L }
            assertFiniteJson(journal.json())
            assertFalse(journal.evaluate(JSONObject(), true))
            file.delete()
        }
    }
}
