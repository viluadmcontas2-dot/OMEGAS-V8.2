package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/**
 * Revisão adversarial do refino (scratchpad/audit/revisao-refino.md), um teste por defeito.
 * Cada teste falhava no HEAD 748aa9ba (o número do item da revisão está no nome).
 */
class RefinementReviewTest {
    private var now = 1_760_000_000_000L
    private val clock = { now }
    private val axisRaw = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
    private fun ms(i: Int) = axisRaw[i] / 512.0
    private val monitorDone = JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1)
    private val noJournal = JSONObject().put("latest", JSONObject.NULL)

    private fun index(vararg bands: Pair<Double?, Int>, share: Double = 0.0): JSONObject = JSONObject()
        .put("samples", bands.sumOf { it.second }).put("petrolObservations", 500).put("gasObservations", 200).put("ratio", 1.0)
        .put("bands", JSONArray(bands.mapIndexed { i, (ratio, n) ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                .put("samples", n).put("ratio", ratio ?: JSONObject.NULL).put("ecuShare", share)
        }))

    private val empty get() = index(null to 0, null to 0, null to 0, null to 0, null to 0)

    /** Quadros estáveis a cada 280 ms; o relógio do diário anda junto (o "depois" é por instante real). */
    private fun drive(ledger: EquivalenceLedger, fuel: String, rpm: Double, map: Double, petrolMs: Double, frames: Int) {
        repeat(frames) { ledger.accept(EquivalenceLedger.Frame(now, fuel, rpm, map, petrolMs, 0.0)); now += 280 }
    }

    private fun ecuCurve() = listOf(0.30, 0.40, 0.50, 0.60, 0.70, 0.80).map { it to it * 5.0 / 0.60 }

    private fun curve(vararg changes: Pair<IntRange, Int>, base: Int = 16384) = IntArray(30) { base }.also { c ->
        changes.forEach { (range, value) -> range.forEach { c[it] = value } }
    }

    private fun analysis(current: IntArray, targets: Map<Int, Int>, capturedAt: Long, mode: String = "EQUIVALENCE"): JSONObject {
        val points = JSONArray()
        for (i in 0 until 30) {
            val target = targets[i] ?: current[i]
            points.put(JSONObject().put("index", i).put("referenceTimeMs", ms(i)).put("currentRaw", current[i]).put("calculatedRaw", target)
                .put("origin", if (target == current[i]) "HELD" else "MEASURED"))
        }
        return JSONObject().put("ok", true).put("available", true).put("snapshotHash", "S$capturedAt").put("capturedAtMs", capturedAt)
            .put("mulActCapturedAtMs", capturedAt).put("refinementMode", mode).put("points", points)
    }

    private fun indexes(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it).getInt("index") }

    private fun snapshot(mulAct: IntArray, capturedAt: Long) = JSONObject().put("snapshotHash", "H${mulAct.contentHashCode()}-$capturedAt")
        .put("capturedAtMs", capturedAt).put("fields", JSONArray()
            .put(JSONObject().put("key", "PETR_INJ_TBP").put("status", "VALID").put("rawValues", JSONArray(axisRaw.toList())).put("capturedAtMs", capturedAt))
            .put(JSONObject().put("key", "MUL_ACT").put("status", "VALID").put("rawValues", JSONArray(mulAct.toList())).put("capturedAtMs", capturedAt)))

    private fun cycle() = RefinementCycle(EquivalenceLedger(null, clock), RefinementJournal(null, clock), RefinementAutopilot(null, clock))

    // ------------------------------------------------------------------ CRÍTICO 1

    @Test
    fun `1 referencia da ECU nao some com leitura desconhecida e sobrevive ao reinicio`() {
        val dir = Files.createTempDirectory("ledger-ref").toFile()
        val file = File(dir, "equivalence_ledger.json")
        val ledger = EquivalenceLedger(file, clock)
        ledger.setEcuPetrolReference(ecuCurve())
        drive(ledger, "GNV", 2000.0, 0.60, 5.1, 12)
        assertTrue(ledger.pairs().isNotEmpty() && ledger.pairs().all { it.ecuRef })
        // Reconexão (snapshot ainda não lido) e campo UNAVAILABLE (pontos SEM_DADO): desconhecido, não "sem referência".
        ledger.setEcuPetrolReference(EcuPetrolReference.fromAcquisition(null))
        assertTrue("leitura nula apagou a referência da ECU", ledger.pairs().isNotEmpty() && ledger.pairs().all { it.ecuRef })
        val unavailable = JSONObject().put("points", JSONArray((0 until 18).map {
            JSONObject().put("index", it).put("fuel", "GASOLINA").put("previous", false).put("state", "SEM_DADO")
                .put("timeMs", JSONObject.NULL).put("mapBar", JSONObject.NULL)
        }))
        ledger.setEcuPetrolReference(EcuPetrolReference.fromAcquisition(unavailable))
        assertTrue("erro de transporte apagou a referência da ECU", ledger.pairs().isNotEmpty() && ledger.pairs().all { it.ecuRef })
        ledger.flush()
        val reloaded = EquivalenceLedger(file, clock)
        assertTrue("app reiniciado perdeu a referência da ECU", reloaded.pairs().isNotEmpty() && reloaded.pairs().all { it.ecuRef })
        dir.deleteRecursively()
    }

    @Test
    fun `1 referencia que troca entre o antes e o depois nao vira piorou nem ensina nem oferece restaurar`() {
        val ledger = EquivalenceLedger(null, clock)
        val journal = RefinementJournal(null, clock)
        ledger.setEcuPetrolReference(ecuCurve()) // ECU: 5,0 ms em 0,60 bar
        drive(ledger, "GNV", 2000.0, 0.60, 5.1, 12) // antes: GNV +2% sobre a gasolina da ECU
        val before = ledger.index()
        val b = curve()
        val a = curve(8..10 to 16700) // 4,5–5,5 ms
        journal.recordCurveWrite(b, a, axisRaw, before, "KF-1", RefinementJournal.KIND_PROPOSTA)
        ledger.invalidateGas("CURVA_K_GRAVADA", RefinementJournal.affectedRangesMs(axisRaw, b, a))
        // Reconexão com leitura incompleta, depois partida a frio na gasolina (própria = 5,5 ms, enriquecida).
        ledger.setEcuPetrolReference(EcuPetrolReference.fromAcquisition(null))
        drive(ledger, "GASOLINA", 2000.0, 0.60, 5.5, 10)
        drive(ledger, "GNV", 2000.0, 0.60, 5.0, 12) // depois: GNV = gasolina real (ECU)
        now += 3_000
        journal.evaluate(ledger.index(), true) { ledger.index(it) }
        val band = journal.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(1)
        assertNotEquals("referência trocou e virou piorou", "PIOROU", band.getString("verdict"))
        assertEquals(1.0, journal.json().getJSONArray("bandScale").getDouble(1), 1e-9)
        assertEquals(0, journal.restorePoints().length())
    }

    // ------------------------------------------------------------------ ALTO 2

    @Test
    fun `2 diario do APK antigo sem tipo nao traz de volta o piorou de mais 2 por cento`() {
        val dir = Files.createTempDirectory("journal-legacy").toFile()
        val file = File(dir, "refinement_journal.json")
        val b = listOf(3.0 to 4.5, 4.5 to 6.0, 6.0 to 7.5, 7.5 to 9.0, 9.0 to 12.0)
        val bandsBefore = JSONArray(); val verdicts = JSONArray()
        b.forEachIndexed { i, (lo, hi) ->
            bandsBefore.put(JSONObject().put("fromMs", lo).put("toMs", hi).put("samples", 20).put("ratio", if (i == 3) 1.01 else 1.0))
            verdicts.put(JSONObject().put("fromMs", lo).put("toMs", hi).put("samplesBefore", 20).put("samplesAfter", 9)
                .put("ratioBefore", if (i == 3) 1.01 else 1.0).put("ratioAfter", if (i == 3) 1.035 else 1.0)
                .put("verdict", if (i == 3) "PIOROU" else if (i == 2) "CONFIRMADA" else "NAO_ALTERADA"))
        }
        val exp = JSONObject().put("id", "EXP-1759000000000").put("appliedAt", 1_759_000_000_000L).put("source", "KF-x")
            .put("axisRaw", JSONArray(axisRaw.toList())).put("beforeRaw", JSONArray(curve().toList())).put("afterRaw", JSONArray(curve(14..16 to 16900).toList()))
            .put("indexBefore", JSONObject().put("bands", bandsBefore)).put("status", "PIOROU_EM_PARTE").put("bands", verdicts)
            .put("closedAt", 1_759_000_100_000L)
        file.writeText(JSONObject().put("format", RefinementJournal.FORMAT).put("bandScale", JSONArray(listOf(1.0, 1.0, 1.0, 0.5, 1.0)))
            .put("experiments", JSONArray().put(exp)).toString())
        val journal = RefinementJournal(file, clock)
        assertEquals("oferta de restaurar logo ao abrir o APK novo", 0, journal.restorePoints().length())
        now += 3_000
        journal.evaluate(index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.035 to 5, 1.0 to 20))
        assertEquals("oferta com 5 leituras em +3,5%", 0, journal.restorePoints().length())
        assertNotEquals("+2,5% não é piorou", "PIOROU", journal.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(3).getString("verdict"))
        assertEquals("ganho aprendido com o piorou falso volta a 1,0", 1.0, journal.json().getJSONArray("bandScale").getDouble(3), 1e-9)
        dir.deleteRecursively()
    }

    // ------------------------------------------------------------------ ALTO 3

    @Test
    fun `3 proposta do servico alinha a curva lida e descarta o GNV medido com a curva velha`() {
        val c = cycle()
        val ledger = c.equivalence
        val curveA = curve(10..20 to 17000)
        c.proposal(snapshot(curveA, now), ledger.index())
        drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, 10)
        drive(ledger, "GNV", 2000.0, 0.60, 5.4, 12)
        c.proposal(snapshot(curveA, now), ledger.index())
        assertEquals(10, ledger.index().getInt("gasObservations"))
        // ProgBase mudou a curva por fora: GNV medido com a curva A não vale para a B.
        now += 4_000
        c.proposal(snapshot(curveA.copyOf().also { it[12] = 15000 }, now), ledger.index())
        assertEquals("GNV da curva velha alimentou a proposta da curva nova", 0, ledger.index().getInt("gasObservations"))
    }

    // ------------------------------------------------------------------ ALTO 4

    private fun worsenedJournal(laterWrite: Boolean): Pair<RefinementJournal, IntArray> {
        val journal = RefinementJournal(null, clock)
        val base = curve()
        val e1 = curve(14..16 to 17000) // 7,5–8,5 ms (faixa 7,5–9)
        journal.recordCurveWrite(base, e1, axisRaw, index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.005 to 20, 1.0 to 20), "KF-1", RefinementJournal.KIND_PROPOSTA)
        now += 3_000
        journal.evaluate(index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.08 to 24, 1.0 to 12)) // piorou na faixa 7,5–9
        if (!laterWrite) return journal to e1
        val e2 = e1.copyOf().also { for (i in 4..5) it[i] = 16900 } // outra gravação em 2,5–3,0 ms
        now += 3_000
        journal.recordCurveWrite(e1, e2, axisRaw, index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.08 to 24, 1.0 to 12), "KF-2", RefinementJournal.KIND_PROPOSTA)
        return journal to e2
    }

    @Test
    fun `4 restaurar parcial com a fonte sendo a ultima gravacao mantem a oferta so dos pontos que ainda estao piores`() {
        val (journal, ecuCurve) = worsenedJournal(laterWrite = false)
        assertEquals(listOf(14, 15, 16), indexes(journal.restorePoints()))
        // "Restaurar 3 pontos": o 14 recebeu ACK e o 15 falhou no transporte (BATCH_FAILED, sem experimento).
        val ecu = ecuCurve.copyOf().also { it[14] = 16384 }
        now += 5_000
        journal.noteEcuCurve(ecu, now)
        assertEquals("15 e 16 continuam piores na ECU", listOf(15, 16), indexes(journal.restorePoints()))
        assertTrue((0 until journal.restorePoints().length()).all { journal.restorePoints().getJSONObject(it).getInt("currentRaw") == 17000 })
    }

    @Test
    fun `4 restaurar parcial com outra gravacao depois nunca oferece o ponto que a ECU ja nao tem`() {
        val (journal, ecuCurve) = worsenedJournal(laterWrite = true)
        val ecu = ecuCurve.copyOf().also { it[14] = 16384 }
        now += 5_000
        journal.noteEcuCurve(ecu, now)
        // Antes: a oferta seguia com cur=17000 no ponto 14 e todo toque falhava na conferência (laço).
        assertEquals(listOf(15, 16), indexes(journal.restorePoints()))
    }

    @Test
    fun `4 BATCH_FAILED parcial conta como gravacao do app`() {
        val c = cycle()
        val journal = c.journal
        val base = curve()
        val e1 = curve(14..16 to 17000)
        c.proposal(snapshot(base, now), empty)
        journal.recordCurveWrite(base, e1, axisRaw, index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.005 to 20, 1.0 to 20), "KF-1", RefinementJournal.KIND_PROPOSTA)
        now += 3_000
        journal.evaluate(index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.08 to 24, 1.0 to 12))
        val ecu = e1.copyOf().also { it[14] = 16384 }
        val failed = JSONObject().put("state", "BATCH_FAILED").put("details", JSONObject().put("adjustmentId", "KF-2").put("partial", true)
            .put("confirmedEvents", JSONArray().put(JSONObject().put("index", 14).put("beforeRaw", 17000).put("afterRaw", 16384)
                .put("reason", "Refino OMEGAS: restaurar pontos que pioraram"))))
        now += 2_000
        assertTrue(c.noteCurveOperation(failed, snapshot(e1, now - 10_000)))
        assertFalse("o mesmo status não vira dois experimentos", c.noteCurveOperation(failed, snapshot(e1, now - 10_000)))
        val latest = journal.json().getJSONObject("latest")
        assertEquals(RefinementJournal.KIND_RESTAURAR, latest.getString("kind"))
        assertEquals(listOf(15, 16), indexes(journal.restorePoints()))
        now += 4_000
        c.proposal(snapshot(ecu, now), empty) // a releitura confirma: não é "mudou por fora"
        assertFalse(journal.json().getJSONObject("latest").has("curveChangedOutside"))
        assertEquals(listOf(15, 16), indexes(journal.restorePoints()))
    }

    // ------------------------------------------------------------------ ALTO 5

    @Test
    fun `5 SEM_BASE segura os pontos ate medir a faixa depois da gravacao`() {
        val journal = RefinementJournal(null, clock)
        val b = curve()
        val a = curve(18..21 to 17200) // 9,5–12 ms: faixa 9–12 sem leitura antes
        journal.recordCurveWrite(b, a, axisRaw, index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20, null to 3), "KF", RefinementJournal.KIND_PROPOSTA)
        val applied = now
        now += 3_000
        journal.evaluate(empty, true) { empty }
        now += 4_000
        val same = (18..21).associateWith { Math.round(17200 * 1.043).toInt() }
        val early = journal.proposalFrom(analysis(a, same, now), empty) { empty }
        assertEquals("mesma correção de novo, ${(now - applied) / 1000} s depois, sem leitura", 0, early.getInt("count"))
        // Medida a faixa depois da gravação (≥ 8 leituras): a proposta volta.
        val measured = index(null to 0, null to 0, null to 0, null to 0, 1.04 to 10)
        now += 3_000
        journal.evaluate(measured, true) { measured }
        val later = journal.proposalFrom(analysis(a, same, now), measured) { measured }
        assertEquals(4, later.getInt("count"))
    }

    // ------------------------------------------------------------------ MÉDIO

    @Test
    fun `6 proposta calculada antes da gravacao nao volta com o diario novo`() {
        val p = RefinementAutopilot(null, clock)
        val idx = index(1.07 to 20, 1.07 to 20, 1.07 to 20, null to 0, null to 0)
        val pre = JSONObject().put("known", true).put("count", 2).put("basedOn", "").put("signature", "4:16384>17000;5:16384>17000")
            .put("points", JSONArray().put(JSONObject().put("index", 4).put("currentRaw", 16384).put("targetRaw", 17000))
                .put(JSONObject().put("index", 5).put("currentRaw", 16384).put("targetRaw", 17000)))
        p.observe(true, monitorDone, null, idx, noJournal, 0, pre)
        val after = JSONObject().put("latest", JSONObject().put("id", "EXP-1").put("status", "VERIFICANDO")).put("measuringBands", JSONArray().put(0))
        now += 3_000
        val r = p.observe(true, monitorDone, null, idx, after, 0, pre) // proposta velha + diário novo no mesmo tick
        assertEquals(0, r.getJSONObject("proposal").getInt("count"))
        assertFalse(r.getString("phase") == "PROPOSTA_PRONTA")
    }

    @Test
    fun `7 margem do dono entra acima de 5 por cento sai em 4 e o latch persiste`() {
        val dir = Files.createTempDirectory("pilot-latch").toFile()
        val file = File(dir, "refinement_autopilot.json")
        val none = JSONObject().put("known", true).put("count", 0).put("points", JSONArray())
        val p = RefinementAutopilot(file, clock)
        val r1 = p.observe(true, monitorDone, null, index(1.0 to 20, 1.0 to 20, 1.045 to 20, null to 0, null to 0), noJournal, 0, none)
        assertEquals("4,5% está dentro da margem aceita", 0, r1.getJSONArray("bandsOff").length())
        // Até 5% é aceito (dono): 4,5% sem nunca passar de 5% é estável, não fica "coletando" para sempre.
        assertEquals("ESTAVEL", r1.getString("phase"))
        now += 3_000
        p.observe(true, monitorDone, null, index(1.0 to 20, 1.0 to 20, 1.06 to 20, null to 0, null to 0), noJournal, 0, none)
        now += 3_000
        val r2 = p.observe(true, monitorDone, null, index(1.0 to 20, 1.0 to 20, 1.045 to 20, null to 0, null to 0), noJournal, 0, none)
        assertEquals("passou de 5%: fica fora até voltar a 4%", 1, r2.getJSONArray("bandsOff").length())
        assertFalse("faixa fora não é estável", r2.getString("phase") == "ESTAVEL")
        val restarted = RefinementAutopilot(file, clock)
        now += 3_000
        val r3 = restarted.observe(true, monitorDone, null, index(1.0 to 20, 1.0 to 20, 1.045 to 20, null to 0, null to 0), noJournal, 0, none)
        assertEquals("app reiniciado esqueceu o latch", 1, r3.getJSONArray("bandsOff").length())
        now += 3_000
        val r4 = restarted.observe(true, monitorDone, null, index(1.0 to 20, 1.0 to 20, 1.035 to 20, null to 0, null to 0), noJournal, 0, none)
        assertEquals("ESTAVEL", r4.getString("phase"))
        // Diário: 6% → 3,5% é "chegou" (dentro de ±4%).
        val journal = RefinementJournal(null, clock)
        journal.recordCurveWrite(curve(), curve(12..14 to 16900), axisRaw, index(1.0 to 20, 1.0 to 20, 1.06 to 20, null to 0, null to 0), "KF", RefinementJournal.KIND_PROPOSTA)
        now += 3_000
        journal.evaluate(index(1.0 to 20, 1.0 to 20, 1.035 to 20, 1.0 to 20, null to 0))
        assertEquals("CONFIRMADA", journal.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(2).getString("verdict"))
        dir.deleteRecursively()
    }

    @Test
    fun `8 prazo da verificacao so anda com o motor no GNV`() {
        assertFalse(RefinementCycle.driving(2000.0, 5.0, "GASOLINA"))
        assertTrue(RefinementCycle.driving(2000.0, 5.0, "GNV"))
        // Ajustado (dono, 2026-10-11: a curva aprende tudo): a lenta no GNV também conta; parado não.
        assertTrue(RefinementCycle.driving(800.0, 5.0, "GNV"))
        assertFalse(RefinementCycle.driving(0.0, 5.0, "GNV"))
    }

    @Test
    fun `9 relogio que volta no reboot nao prende a proposta e leitura velha nunca vira base`() {
        val journal = RefinementJournal(null, clock)
        val b = curve()
        val a = curve(9..9 to 16900)
        journal.recordCurveWrite(b, a, axisRaw, index(1.07 to 20, 1.07 to 20, 1.07 to 20, null to 0, null to 0), "KF", RefinementJournal.KIND_PROPOSTA)
        now -= 6L * 3_600_000L // multimídia sem RTC: relógio 6 h atrás
        val fresh = journal.proposalFrom(analysis(a, mapOf(3 to 17000), now), empty) { empty }
        assertTrue("leitura nova da ECU ficou presa em ${fresh.optString("reason")}", fresh.getBoolean("known"))
        // Leitura com horário "depois" mas conteúdo da curva antiga: ainda é a curva velha.
        val stale = journal.proposalFrom(analysis(b, mapOf(3 to 17000), now + 60 * 3_600_000L), empty) { empty }
        assertEquals("LENDO_CURVA_NOVA", stale.optString("reason"))
    }

    @Test
    fun `10 oferta de restaurar nao expira por tempo e o Mapa K anula`() {
        val (journal, _) = worsenedJournal(laterWrite = false)
        assertEquals(3, journal.restorePoints().length())
        repeat(620) { now += 3_000; journal.evaluate(index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.08 to 24, 1.0 to 12)) } // horas depois
        assertEquals("a evidência ainda diz que piorou: a oferta fica", 3, journal.restorePoints().length())
        val (other, _) = worsenedJournal(laterWrite = false)
        assertEquals(3, other.restorePoints().length())
        other.interrupt("MAPA_K_GRAVADO")
        assertEquals("Mapa K gravado não anulou a oferta", 0, other.restorePoints().length())
        val (empty3, _) = worsenedJournal(laterWrite = false)
        now += 3_000
        empty3.evaluate(empty)
        assertEquals("oferta sem evidência atual", 0, empty3.restorePoints().length())
    }

    @Test
    fun `11 gasolina readquirida descongela mesmo sem ver zero e depois de reiniciar`() {
        val ledger = EquivalenceLedger(null, clock)
        drive(ledger, "GASOLINA", 2000.0, 0.60, 5.5, 10)
        ledger.notePetrolAcquisition(18)
        ledger.notePetrolAcquisition(2) // a ECU já readquiriu 2 bandas quando o serviço olhou
        drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, 10)
        assertTrue("gasolina antiga congelada", ledger.pairsPetrolMedian(2000.0, 0.60) < 5.25)
        val dir = Files.createTempDirectory("ledger-petrol").toFile()
        val file = File(dir, "equivalence_ledger.json")
        val first = EquivalenceLedger(file, clock)
        drive(first, "GASOLINA", 2000.0, 0.60, 5.5, 10)
        first.notePetrolAcquisition(18)
        first.flush()
        val restarted = EquivalenceLedger(file, clock)
        restarted.notePetrolAcquisition(0) // primeiro tick depois de reiniciar já com a gasolina zerada
        assertEquals(0, restarted.index().getInt("petrolObservations"))
        dir.deleteRecursively()
    }

    private fun EquivalenceLedger.pairsPetrolMedian(rpm: Double, map: Double): Double {
        drive(this, "GNV", rpm, map, 5.0, 5)
        val refs = pairs().map { it.petrolRefMs }.sorted()
        return refs[refs.size / 2]
    }

    @Test
    fun `12 gasolina propria vem primeiro e a ECU so completa fora da lenta`() {
        val ledger = EquivalenceLedger(null, clock)
        drive(ledger, "GASOLINA", 2000.0, 0.60, 5.5, 10)
        ledger.setEcuPetrolReference(ecuCurve())
        drive(ledger, "GNV", 2000.0, 0.60, 5.5, 12)
        drive(ledger, "GNV", 2000.0, 0.75, 6.25, 12)
        val own = ledger.pairs().filter { abs(it.gasPetrolMs - 5.5) < 1e-9 }
        val ecu = ledger.pairs().filter { abs(it.gasPetrolMs - 6.25) < 1e-9 }
        assertTrue("havia gasolina própria e a da ECU tomou o lugar", own.isNotEmpty() && own.all { !it.ecuRef && abs(it.petrolRefMs - 5.5) < 1e-9 })
        assertTrue(ecu.isNotEmpty() && ecu.all { it.ecuRef })
        // Bandas 0–5 da ECU (lenta, limiar petrolIdleMinUpdate) não viram referência de condução.
        val acq = JSONObject().put("points", JSONArray((0 until 18).map { i ->
            JSONObject().put("index", i).put("fuel", "GASOLINA").put("previous", false).put("state", "ZONA_ADQUIRIDA").put("draw", true)
                .put("timeMs", 2.0 + i * 0.6).put("mapBar", 0.20 + i * 0.05)
        }))
        val ref = EcuPetrolReference.fromAcquisition(acq)
        assertEquals(12, ref!!.size)
        assertTrue(ref.all { it.first >= 0.20 + 6 * 0.05 - 1e-9 })
        // Faixa medida pela curva da ECU: ±6%.
        val p = RefinementAutopilot(null, clock)
        val none = JSONObject().put("known", true).put("count", 0).put("points", JSONArray())
        val r = p.observe(true, monitorDone, null, index(1.055 to 20, 1.0 to 20, 1.0 to 20, null to 0, null to 0, share = 1.0), noJournal, 0, none)
        assertEquals(0, r.getJSONArray("bandsOff").length())
    }

    @Test
    fun `15 modo POLISH sem faixa fora nao avisa pronta`() {
        val p = RefinementAutopilot(null, clock)
        val polish = JSONObject().put("known", true).put("count", 2).put("basedOn", "").put("mode", "POLISH").put("signature", "x")
            .put("points", JSONArray().put(JSONObject().put("index", 4).put("currentRaw", 16384).put("targetRaw", 16500))
                .put(JSONObject().put("index", 5).put("currentRaw", 16384).put("targetRaw", 16500)))
        val r = p.observe(true, monitorDone, null, index(1.02 to 20, 1.01 to 20, null to 0, null to 0, null to 0), noJournal, 0, polish)
        assertFalse(r.getString("phase") == "PROPOSTA_PRONTA")
        assertTrue(p.takeAlert() == null)
    }

    @Test
    fun `16 desfazer vale ate a proxima gravacao mesmo com a medicao fechada`() {
        val journal = RefinementJournal(null, clock)
        val b = curve()
        val a = curve(12..14 to 17000)
        journal.recordCurveWrite(b, a, axisRaw, index(1.06 to 20, 1.06 to 20, 1.06 to 20, 1.06 to 20, null to 0), "KF", RefinementJournal.KIND_PROPOSTA)
        now += 3_000
        journal.evaluate(index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20, null to 0))
        val latest = journal.json().getJSONObject("latest")
        assertEquals("VERIFICADO", latest.getString("status"))
        assertTrue("Desfazer sumiu quando a medição fechou", latest.getBoolean("undoable"))
        journal.recordCurveWrite(a, a.copyOf().also { it[3] = 16000 }, axisRaw, index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20, null to 0), "KF-2", RefinementJournal.KIND_MANUAL)
        assertFalse(journal.json().getJSONObject("latest").getBoolean("undoable"))
    }

    @Test
    fun `17 pontos independentes so segura o que cruza o trecho alterado`() {
        val journal = RefinementJournal(null, clock)
        val b = curve()
        val a = curve(14..14 to 17000) // 7,5 ms
        journal.recordCurveWrite(b, a, axisRaw, index(1.06 to 20, 1.06 to 20, 1.06 to 20, 1.06 to 20, 1.06 to 20), "KF", RefinementJournal.KIND_PROPOSTA)
        now += 3_000
        journal.evaluate(empty, true) { empty }
        val wants = listOf(12, 13, 15, 16).associateWith { 17000 }
        val p = journal.proposalFrom(analysis(a, wants, now), empty) { empty }
        assertEquals(listOf(12, 16), indexes(p.getJSONArray("points")))
        assertEquals(listOf(13, 15), indexes(p.getJSONArray("held")))
    }

    @Test
    fun `18 reset da Curva K e restaurar backup nao ensinam nem pedem restaurar`() {
        assertEquals("RESET", RefinementJournal.kindFromReason("Reset Curva K · ProgBase MUL_ACT=1.0"))
        assertEquals("RESET", RefinementJournal.kindFromReason("Neutralizar MUL_ACT live em 1.0 · OMEGAS"))
        assertEquals("BACKUP", RefinementJournal.kindFromReason("Restaurar backup Curva K curva.json"))
        val journal = RefinementJournal(null, clock)
        journal.recordCurveWrite(curve(14..16 to 17000), curve(), axisRaw, index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.005 to 20, 1.0 to 20), "KF",
            RefinementJournal.kindFromReason("Reset Curva K · ProgBase MUL_ACT=1.0"))
        now += 3_000
        journal.evaluate(index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.08 to 24, 1.0 to 12))
        assertEquals(1.0, journal.json().getJSONArray("bandScale").getDouble(3), 1e-9)
        assertEquals(0, journal.restorePoints().length())
    }

    @Test
    fun `19 diario so regrava com mudanca e nao guarda indexAfter`() {
        val dir = Files.createTempDirectory("journal-save").toFile()
        val file = File(dir, "refinement_journal.json")
        val journal = RefinementJournal(file, clock)
        journal.recordCurveWrite(curve(), curve(12..14 to 17000), axisRaw, index(1.06 to 20, 1.06 to 20, 1.06 to 20, 1.06 to 20, null to 0), "KF", RefinementJournal.KIND_PROPOSTA)
        now += 3_000
        journal.evaluate(index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20, null to 0))
        assertFalse(file.readText().contains("indexAfter"))
        file.delete()
        repeat(60) { now += 3_000; journal.evaluate(index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.0 to 20, null to 0), driving = false) }
        assertFalse("regravou sem mudança", file.exists())
        dir.deleteRecursively()
    }

    @Test
    fun `21 aquisicao desconhecida nao vira zero pontos da ECU`() {
        val r = RefinementAutopilot(null, clock).observe(true, monitorDone, null, empty, noJournal, 0)
        assertTrue(r.isNull("petrolValid"))
        assertTrue(r.isNull("gasValid"))
    }
}
