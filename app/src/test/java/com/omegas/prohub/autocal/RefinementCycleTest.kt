package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * Ciclo do refino (classe 2, sintético) pelo mesmo caminho do serviço: RefinementCycle (cola do serviço) com
 * EquivalenceLedger, RefinementJournal e RefinementAutopilot reais. A telemetria entra quadro a quadro no
 * acumulador (gasolina e GNV no mesmo RPM×MAP), o "depois" de cada gravação é o GNV medido a partir do
 * instante real da gravação e a gravação passa pelo payload do KFactorManager (BATCH_CONFIRMED).
 * O "motor" aqui é uma função de proposta sobre a curva lida da ECU (o motor real não muda e tem teste
 * próprio); os últimos testes usam o motor real em sessões gravadas no carro (classe 3).
 *
 * Ajustado na rodada 2 (2026-10-11, revisão scratchpad/audit/revisao-refino.md, item 20): antes o "carro" usava
 * um índice sintético (o "depois" ignorava o instante e a gravação zerava tudo), sem acumulador nem cola.
 * Margem do dono: faixa "fora" só acima de 5% (os cenários usam 7% onde antes usavam 5%).
 */
class RefinementCycleTest {
    private var now = 5_000_000L
    private val clock = { now }
    private val axisRaw = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
    private fun ms(i: Int) = axisRaw[i] / 512.0
    private val monitorDone = JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1)

    /** Índice sintético: só para os testes do piloto e do diário puros. */
    private fun index(vararg bands: Pair<Double?, Int>): JSONObject = JSONObject()
        .put("samples", bands.sumOf { it.second }).put("petrolObservations", 500).put("gasObservations", 200).put("ratio", 1.0)
        .put("bands", JSONArray(bands.mapIndexed { i, (ratio, n) ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                .put("samples", n).put("ratio", ratio ?: JSONObject.NULL)
        }))

    private val empty get() = index(null to 0, null to 0, null to 0, null to 0, null to 0)

    /** Gasolina de referência de cada faixa (ms) e o MAP onde o carro roda nela (2000 rpm). */
    private val bandPetrolMs = doubleArrayOf(3.75, 5.25, 6.75, 8.25, 10.5)
    private val bandMap = doubleArrayOf(0.40, 0.55, 0.70, 0.85, 1.00)

    /** O carro: ECU guarda a curva; a leitura da ECU (MUL_ACT) atrasa até [readEcu]; o serviço é a cola real. */
    private inner class Car(journalFile: File? = null) {
        val ledger = EquivalenceLedger(null, clock)
        val journal = RefinementJournal(journalFile, clock)
        val pilot = RefinementAutopilot(null, clock)
        var curve = IntArray(30) { 16384 }
        var readAt = 0L
        var readCurve = curve.copyOf()
        var engine: (Int, IntArray) -> Int = { i, c -> c[i] }
        var online = true
        var last = JSONObject()
        val cycle = RefinementCycle(ledger, journal, pilot, monotonicMs = { now }) { snapshot, _, _ -> analysisOf(snapshot) }

        init {
            // Gasolina própria quente em cada faixa (8 leituras estáveis: madura e congelada).
            bandPetrolMs.indices.forEach { b -> frames("GASOLINA", bandMap[b], bandPetrolMs[b], 10) }
        }

        private fun frames(fuel: String, map: Double, petrolMs: Double, n: Int, rpm: Double = 2000.0) {
            now += 2_000 // quadros de faixas/chamadas diferentes nunca formam a mesma janela estável
            repeat(n) { ledger.accept(EquivalenceLedger.Frame(now, fuel, rpm, map, petrolMs, 0.0)); now += 280 }
        }

        /** Rodar no GNV: [n] leituras estáveis por faixa com t_no_GNV = t_gasolina × razão. */
        fun drive(vararg bands: Pair<Double?, Int>) {
            bands.forEachIndexed { b, (ratio, n) -> if (n > 0) frames("GNV", bandMap[b], bandPetrolMs[b] * (ratio ?: 1.0), n + 2) }
        }

        val index: JSONObject get() = ledger.index()

        fun readEcu() { readAt = now; readCurve = curve.copyOf() }

        fun snapshot(): JSONObject = JSONObject().put("snapshotHash", "S$readAt").put("capturedAtMs", readAt).put("fields", JSONArray()
            .put(JSONObject().put("key", "PETR_INJ_TBP").put("status", "VALID").put("rawValues", JSONArray(axisRaw.toList())).put("capturedAtMs", readAt))
            .put(JSONObject().put("key", "MUL_ACT").put("status", "VALID").put("rawValues", JSONArray(readCurve.toList())).put("capturedAtMs", readAt)))

        private fun analysisOf(snapshot: JSONObject): JSONObject {
            val mul = (0 until snapshot.getJSONArray("fields").length()).map { snapshot.getJSONArray("fields").getJSONObject(it) }
                .first { it.getString("key") == "MUL_ACT" }.getJSONArray("rawValues")
            val current = IntArray(30) { mul.getInt(it) }
            return analysis(current, snapshot.getLong("capturedAtMs"))
        }

        fun analysis(current: IntArray = readCurve, capturedAt: Long = readAt): JSONObject {
            val points = JSONArray()
            for (i in 0 until 30) {
                val target = engine(i, current)
                points.put(JSONObject().put("index", i).put("referenceTimeMs", ms(i)).put("currentRaw", current[i])
                    .put("calculatedRaw", target).put("origin", if (target == current[i]) "HELD" else "MEASURED"))
            }
            return JSONObject().put("ok", true).put("available", true).put("snapshotHash", "S$capturedAt").put("refinementMode", "EQUIVALENCE")
                .put("capturedAtMs", capturedAt).put("mulActCapturedAtMs", capturedAt).put("points", points)
        }

        /** Tick do serviço: avaliação (healthTick) e piloto (observeRefinement). Sem cabo a leitura some. */
        fun tick(): JSONObject {
            now += 3_000
            cycle.evaluate(online)
            last = cycle.observe(online, online, monitorDone, if (online) snapshot() else JSONObject().put("available", false))
            return last
        }

        /** Toque em "Gravar na ECU": grava exatamente a lista; o KFactorManager confirma (ACK + readback) e o serviço registra. */
        fun write(points: JSONArray, reason: String): JSONObject {
            val listed = JSONArray()
            for (k in 0 until points.length()) {
                val p = points.getJSONObject(k)
                assertEquals("lista velha gravada no ponto ${p.getInt("index")}", curve[p.getInt("index")], p.getInt("currentRaw"))
                listed.put(JSONObject().put("index", p.getInt("index")).put("currentRaw", p.getInt("currentRaw")).put("targetRaw", p.getInt("targetRaw")))
                curve[p.getInt("index")] = p.getInt("targetRaw")
            }
            now += 1_000
            cycle.recordConfirmedCurve(JSONObject().put("adjustmentId", "KF-$now").put("points", listed)
                .put("confirmedEvents", JSONArray().put(JSONObject().put("reason", reason)))
                .put("curve", JSONObject().put("axisRaw", JSONArray(axisRaw.toList())).put("factorsRaw", JSONArray(curve.toList()))))
            return tick() // o serviço observa na hora, ainda com a leitura velha da ECU
        }

        fun action() = last.getJSONObject("action")
        fun secondary(kind: String) = last.getJSONArray("secondary").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.getString("kind") == kind } }
        fun proposalPoints() = last.getJSONObject("proposal").getJSONArray("points")
        fun proposedIndexes() = proposalPoints().let { a -> (0 until a.length()).map { a.getJSONObject(it).getInt("index") } }
    }

    private val propose = "Refino OMEGAS: curva refinada confirmada"
    private val restore = "Refino OMEGAS: restaurar pontos que pioraram"
    private val undo = "Refino OMEGAS: desfazer última gravação"
    private val off = arrayOf(1.07 to 20, 1.07 to 20, 1.07 to 20, 1.07 to 20, 1.07 to 20)

    @Test
    fun `motivo da gravacao define o tipo e o resto e ajuste manual`() {
        assertEquals(RefinementJournal.KIND_PROPOSTA, RefinementJournal.kindFromReason(propose))
        assertEquals(RefinementJournal.KIND_RESTAURAR, RefinementJournal.kindFromReason(restore))
        assertEquals(RefinementJournal.KIND_RESTAURAR, RefinementJournal.kindFromReason("Refino OMEGAS: restaurar trecho que piorou"))
        assertEquals(RefinementJournal.KIND_DESFAZER, RefinementJournal.kindFromReason(undo))
        assertEquals(RefinementJournal.KIND_MANUAL, RefinementJournal.kindFromReason("Ajuste manual confirmado na UI clean-slate"))
        // Rodada 2 (item 18): reset da Curva K e backup restaurado são decisão do dono, não ensinam nem pedem restaurar.
        assertEquals(RefinementJournal.KIND_BACKUP, RefinementJournal.kindFromReason("Restaurar backup Curva K curva.json"))
        assertEquals(RefinementJournal.KIND_RESET, RefinementJournal.kindFromReason("Reset Curva K · ProgBase MUL_ACT=1.0"))
        assertEquals(RefinementJournal.KIND_MANUAL, RefinementJournal.kindFromReason(null))
    }

    @Test
    fun `trechos afetados vao do vizinho anterior ao seguinte de cada ponto alterado`() {
        val before = IntArray(30) { 16384 }
        val after = before.copyOf().also { it[10] = 17000; it[11] = 17000; it[29] = 17000 }
        val ranges = RefinementJournal.affectedRangesMs(axisRaw, before, after)
        assertEquals(2, ranges.size)
        assertEquals(ms(9), ranges[0].first, 1e-9)
        assertEquals(ms(12), ranges[0].second, 1e-9)
        assertEquals(ms(28), ranges[1].first, 1e-9)
        assertTrue(ranges[1].second.isInfinite())
    }

    @Test
    fun `verificacao com faixa nunca visitada fecha em 15 min de ECU online e a proposta volta`() {
        val car = Car()
        var wave = 1
        car.engine = { i, c -> if (wave == 1 && ms(i) in 3.0..12.0) (c[i] * 1.05).toInt() else if (wave == 2 && ms(i) in 9.0..12.0) (c[i] * 1.03).toInt() else c[i] }
        car.drive(*off)
        car.readEcu()
        val s0 = car.tick()
        assertEquals("PROPOSTA_PRONTA", s0.getString("phase"))
        car.write(car.proposalPoints(), propose)
        car.readEcu(); wave = 2
        car.drive(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.05 to 12, 1.03 to 3) // 9–12 ms nunca chega a 8 leituras
        repeat(290) { car.tick() } // ~14,5 min
        assertEquals("VERIFICANDO", car.last.getString("phase"))
        assertTrue("pontos da faixa em medição ficam fora da proposta", car.proposedIndexes().isEmpty())
        assertEquals("none", car.action().getString("kind"))
        repeat(20) { car.tick() } // passa de 15 min de ECU online
        assertFalse(car.journal.json().getJSONObject("latest").getString("status") == RefinementJournal.STATUS_VERIFYING)
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        assertTrue(car.proposedIndexes().isNotEmpty() && car.proposedIndexes().all { ms(it) in 9.0..12.0 })
    }

    @Test
    fun `sem medicao anterior nas faixas tocadas fecha SEM_BASE`() {
        val journal = RefinementJournal(null, clock)
        journal.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { 17000 }, axisRaw, index(null to 0, null to 3, null to 5, null to 0, null to 0), "KF", RefinementJournal.KIND_PROPOSTA)
        now += 3_000
        journal.evaluate(index(1.0 to 30, 1.0 to 30, 1.0 to 30, 1.0 to 30, 1.0 to 30))
        assertEquals("SEM_BASE", journal.json().getJSONObject("latest").getString("status"))
        assertTrue(journal.measuringBands().isEmpty())
    }

    @Test
    fun `prazo da verificacao so anda rodando e parado ou em lenta nao conta`() {
        val journal = RefinementJournal(null, clock)
        journal.recordCurveWrite(IntArray(30) { 16384 }, IntArray(30) { 17000 }, axisRaw, index(1.05 to 20, 1.05 to 20, 1.05 to 20, 1.05 to 20, 1.05 to 20), "KF")
        repeat(1200) { now += 3_000; journal.evaluate(empty, driving = false) } // 1 h parado/lenta
        assertEquals(RefinementJournal.STATUS_VERIFYING, journal.json().getJSONObject("latest").getString("status"))
        assertEquals(0L, journal.json().getJSONObject("verifying").getLong("drivingMs"))
        repeat(310) { now += 3_000; journal.evaluate(empty, driving = true) } // ~15,5 min rodando
        // Passado o prazo parcial sem nenhuma faixa medida: nada fica retido (a proposta segue) e fecha sozinho em 40 min.
        assertTrue(journal.measuringBands().isEmpty())
        repeat(500) { now += 3_000; journal.evaluate(empty, driving = true) } // > 40 min rodando
        assertEquals("INCONCLUSIVO", journal.json().getJSONObject("latest").getString("status"))
    }

    @Test
    fun `verificando diz o progresso em palavras e nunca fica mudo`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.05).toInt() else c[i] }
        car.drive(*off)
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu()
        car.drive(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.0 to 3, null to 0)
        repeat(120) { car.tick() } // 6 min rodando
        assertEquals("VERIFICANDO", car.last.getString("phase"))
        val headline = car.last.getString("headline")
        assertTrue(headline, headline.startsWith("Medindo a gravação: faltam 2 faixas · 6 de 15 min rodando"))
        car.online = false
        repeat(100) { car.tick() } // parado: o tempo não anda
        assertTrue(car.last.getString("headline").contains("6 de 15 min rodando"))
    }

    @Test
    fun `quatro por cento nao e piorou e so fora da margem do dono vira restaurar`() {
        fun verdictFor(before: Double, after: Double): String {
            val journal = RefinementJournal(null, clock)
            val points = IntArray(30) { 16384 }.also { for (i in 14..16) it[i] = 17000 } // 7,5–8,5 ms (faixa 7,5–9)
            journal.recordCurveWrite(IntArray(30) { 16384 }, points, axisRaw, index(1.0 to 20, 1.0 to 20, 1.0 to 20, before to 20, 1.0 to 20), "KF", RefinementJournal.KIND_PROPOSTA)
            now += 3_000
            journal.evaluate(index(1.0 to 12, 1.0 to 12, 1.0 to 12, after to 12, 1.0 to 12))
            return journal.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(3).getString("verdict")
        }
        assertFalse(verdictFor(1.005, 1.042) == "PIOROU") // 4,1%: dentro da margem do dono
        assertFalse(verdictFor(1.01, 1.049) == "PIOROU")  // até 5% aceito
        assertFalse(verdictFor(1.03, 1.06) == "PIOROU")   // piorou só 3%: ruído
        assertEquals("PIOROU", verdictFor(1.005, 1.08))   // 8% e 7,5% pior: piorou de verdade
        // Oferta de restaurar só enquanto a faixa ainda está fora da margem agora.
        val journal = RefinementJournal(null, clock)
        val points = IntArray(30) { 16384 }.also { for (i in 14..16) it[i] = 17000 }
        journal.recordCurveWrite(IntArray(30) { 16384 }, points, axisRaw, index(1.0 to 20, 1.0 to 20, 1.0 to 20, 1.005 to 20, 1.0 to 20), "KF", RefinementJournal.KIND_PROPOSTA)
        now += 3_000
        journal.evaluate(index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.08 to 12, 1.0 to 12))
        assertTrue(journal.restorePoints().length() > 0)
        now += 3_000
        journal.evaluate(index(1.0 to 30, 1.0 to 30, 1.0 to 30, 1.03 to 30, 1.0 to 30)) // medição nova voltou para dentro
        assertEquals(0, journal.restorePoints().length())
    }

    @Test
    fun `depois de gravar a proposta velha nao volta e o piloto observa sem botao`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.05).toInt() else c[i] }
        car.drive(*off)
        car.readEcu()
        car.tick()
        val written = car.write(car.proposalPoints(), propose)
        // Leitura da ECU ainda é a de antes da gravação: nada é proposto sobre a curva velha.
        assertEquals("VERIFICANDO", written.getString("phase"))
        assertEquals("none", written.getJSONObject("action").getString("kind"))
        assertEquals(0, written.getJSONObject("proposal").getInt("count"))
        assertEquals("LENDO_CURVA_NOVA", written.getJSONObject("proposal").getString("reason"))
        // A leitura velha não é "mudou por fora": a verificação continua.
        assertEquals(RefinementJournal.STATUS_VERIFYING, car.journal.json().getJSONObject("latest").getString("status"))
        // Desfazer já aparece como opção (secundária); não existe "Entendi".
        assertNotNull(car.secondary("undo"))
        assertNull(car.secondary("ack"))
        assertFalse(written.toString().contains("Entendi"))
    }

    @Test
    fun `piorou oferece restaurar so os pontos que pioraram e restaurar nao ensina o ganho`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.05).toInt() else c[i] }
        car.drive(*off)
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu()
        car.drive(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.12 to 12, 1.0 to 12)
        car.tick()
        assertEquals("RESTAURAR_TRECHO", car.last.getString("phase"))
        val restorePoints = car.journal.restorePoints()
        assertTrue(restorePoints.length() > 0)
        assertTrue((0 until restorePoints.length()).all { ms(restorePoints.getJSONObject(it).getInt("index")) in 7.0..9.5 })
        assertEquals("Restaurar ${restorePoints.length()} pontos que pioraram", car.action().getString("label"))
        assertTrue(car.last.getString("headline").contains("pioraram"))
        // Sem "Entendi": os outros pontos continuam com "Revisar e gravar" ao lado (nenhum estado sem saída),
        // e a revisão nunca mistura os pontos oferecidos para restaurar.
        assertNotNull(car.secondary("review"))
        val offered = (0 until restorePoints.length()).map { restorePoints.getJSONObject(it).getInt("index") }.toSet()
        assertTrue(car.proposedIndexes().none { it in offered })
        val scaleAfterWorse = car.journal.json().getJSONArray("bandScale").getDouble(3)
        assertEquals(0.5, scaleAfterWorse, 1e-9)

        car.write(restorePoints, restore)
        car.readEcu()
        car.drive(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.0 to 12, 1.0 to 12)
        repeat(3) { car.tick() }
        assertEquals("restaurar não ensina o ganho", scaleAfterWorse, car.journal.json().getJSONArray("bandScale").getDouble(3), 1e-9)
        assertEquals(0, car.journal.restorePoints().length())
        // Os pontos que melhoraram ficaram com o valor gravado.
        assertTrue((0 until 30).filter { ms(it) in 3.0..6.4 }.all { car.curve[it] == (16384 * 1.05).toInt() })
    }

    @Test
    fun `restaurado nao volta na mesma direcao sem evidencia nova e nunca no valor que piorou`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.05).toInt() else c[i] }
        car.drive(*off)
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu()
        car.drive(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.12 to 12, 1.0 to 12)
        car.tick()
        val restored = car.journal.restorePoints()
        val bad = (0 until restored.length()).associate { restored.getJSONObject(it).getInt("index") to restored.getJSONObject(it).getInt("currentRaw") }
        car.write(restored, restore)
        car.readEcu()
        // Sem leituras novas na faixa: o motor (inalterado) quer a mesma direção de novo e fica de fora.
        car.tick()
        assertTrue(car.proposedIndexes().none { it in bad.keys })
        val held = car.last.getJSONObject("proposal").getJSONArray("held")
        assertTrue((0 until held.length()).any { held.getJSONObject(it).getInt("index") in bad.keys })
        // Evidência nova: a faixa foi medida de novo com a curva restaurada.
        car.drive(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.02 to 12, 1.0 to 12)
        repeat(3) { car.tick() }
        val proposed = car.proposalPoints()
        for (k in 0 until proposed.length()) {
            val p = proposed.getJSONObject(k)
            val worse = bad[p.getInt("index")] ?: continue
            assertTrue("ponto ${p.getInt("index")} voltaria ao valor que piorou", p.getInt("targetRaw") < worse)
        }
        // Metade do passo é permitida; o valor ruim nunca. (Motor novo = leitura nova: o motor é função da leitura.)
        car.engine = { i, c -> if (i in bad.keys) (c[i] * 1.02).toInt() else c[i] }
        car.readEcu()
        repeat(2) { car.tick() }
        assertTrue(car.proposedIndexes().any { it in bad.keys })
        assertTrue(car.proposalPoints().let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .filter { it.getInt("index") in bad.keys }.all { it.getInt("targetRaw") < bad.getValue(it.getInt("index")) })
    }

    @Test
    fun `restauracao medida pior nao oferece restaurar de novo nem volta ao valor ruim`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.10).toInt() else c[i] }
        car.drive(1.07 to 20, 1.08 to 20, 1.07 to 20, 1.02 to 20, 1.03 to 20)
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu()
        car.drive(0.99 to 10, 1.01 to 10, 1.0 to 10, 1.09 to 10, 1.0 to 10)
        car.tick()
        val restorePoints = car.journal.restorePoints()
        assertTrue(restorePoints.length() > 0)
        car.write(restorePoints, restore)
        car.readEcu()
        // A medição da restauração sai pior: antes isso pedia "restaurar" de volta o valor ruim (laço).
        car.drive(0.99 to 10, 1.01 to 10, 1.0 to 10, 1.20 to 10, 1.0 to 10)
        car.tick()
        assertEquals(0, car.journal.restorePoints().length())
        assertFalse(car.last.getString("phase") == "RESTAURAR_TRECHO")
    }

    @Test
    fun `desfazer nao regrava o erro nem reaparece e o ciclo continua`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.04).toInt() else c[i] }
        car.drive(*off)
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu(); car.tick()
        val latest = car.journal.json().getJSONObject("latest")
        assertTrue(latest.getBoolean("undoable"))
        assertNotNull(car.secondary("undo"))
        car.write(latest.getJSONArray("undoPoints"), undo)
        car.readEcu(); car.tick()
        val after = car.journal.json().getJSONObject("latest")
        assertFalse("o desfazer não é desfazível (sem laço)", after.getBoolean("undoable"))
        assertEquals(0, after.getJSONArray("undoPoints").length())
        assertNull(car.secondary("undo"))
        assertEquals(RefinementJournal.KIND_DESFAZER, after.getString("kind"))
        assertEquals(0, car.journal.restorePoints().length())
        // O motor quer de novo o que foi desfeito: fica de fora até a faixa ser medida com a curva de volta.
        assertEquals(0, car.last.getJSONObject("proposal").getInt("count"))
        assertTrue(car.journal.json().getJSONArray("history").let { h -> (0 until h.length()).any { h.getJSONObject(it).getString("status") == "DESFEITO" } })
        car.drive(1.06 to 12, 1.06 to 12, 1.06 to 12, 1.06 to 12, 1.06 to 12)
        repeat(4) { car.tick() }
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        // Desfazer não ensina o ganho.
        val scale = car.journal.json().getJSONArray("bandScale")
        assertTrue((0 until scale.length()).all { scale.getDouble(it) == 1.0 })
    }

    @Test
    fun `verificando nao esconde proposta de pontos que nao tocam a faixa em medicao`() {
        val car = Car()
        var wave = 1
        car.engine = { i, c -> if (wave == 1 && ms(i) in 7.5..9.0) (c[i] * 1.05).toInt() else if (wave == 2 && ms(i) in 3.0..5.0) (c[i] * 1.03).toInt() else c[i] }
        car.drive(*off)
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu(); wave = 2
        car.drive(1.03 to 12, 1.03 to 12, null to 0, 1.0 to 2, null to 0)
        car.tick()
        assertEquals(RefinementJournal.STATUS_VERIFYING, car.journal.json().getJSONObject("latest").getString("status"))
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        assertTrue(car.proposedIndexes().isNotEmpty())
        assertTrue(car.proposedIndexes().all { ms(it) in 3.0..5.0 })
    }

    @Test
    fun `gravacao em outros pontos nao interrompe a medicao em curso`() {
        val journal = RefinementJournal(null, clock)
        val base = IntArray(30) { 16384 }
        val first = base.copyOf().also { for (i in 14..16) it[i] = 17200 } // 7,5–8,5 ms
        journal.recordCurveWrite(base, first, axisRaw, index(1.05 to 20, 1.05 to 20, 1.05 to 20, 1.05 to 20, 1.05 to 20), "KF-1", RefinementJournal.KIND_PROPOSTA)
        now += 3_000
        journal.evaluate(index(1.0 to 12, 1.0 to 12, null to 0, null to 0, null to 0))
        val second = first.copyOf().also { for (i in 5..7) it[i] = 16800 } // 3,0–4,0 ms
        now += 3_000
        journal.recordCurveWrite(first, second, axisRaw, index(1.0 to 12, 1.0 to 12, null to 0, null to 0, null to 0), "KF-2", RefinementJournal.KIND_PROPOSTA)
        val history = journal.json().getJSONArray("history")
        assertEquals(RefinementJournal.STATUS_VERIFYING, history.getJSONObject(history.length() - 2).getString("status"))
        now += 3_000
        journal.evaluate(index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.12 to 12, 1.0 to 12))
        val firstStatus = journal.json().getJSONArray("history").let { it.getJSONObject(it.length() - 2).getString("status") }
        assertEquals("a primeira gravação terminou de medir as faixas dela", "PIOROU_EM_PARTE", firstStatus)
        val offer = journal.restorePoints()
        assertTrue(offer.length() > 0)
        assertTrue((0 until offer.length()).all { offer.getJSONObject(it).getInt("index") in 14..16 })
    }

    @Test
    fun `sem Entendi a oferta de restaurar some sozinha quando a faixa volta e o piloto segue sugerindo`() {
        val car = Car()
        var wave = 1
        car.engine = { i, c -> if (wave == 1 && ms(i) in 7.5..9.0) (c[i] * 1.05).toInt() else if (wave == 2 && ms(i) in 3.0..5.0) (c[i] * 1.05).toInt() else c[i] }
        car.drive(*off)
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu(); wave = 2
        car.drive(1.07 to 12, 1.07 to 12, 1.0 to 12, 1.12 to 12, 1.0 to 12)
        car.tick()
        assertEquals("RESTAURAR_TRECHO", car.last.getString("phase"))
        assertNotNull("proposta dos outros pontos ao lado do Restaurar", car.secondary("review"))
        // O dono não restaura; a medição seguinte mostra a faixa de volta dentro da margem: a oferta some sozinha.
        car.drive(1.07 to 30, 1.07 to 30, 1.0 to 30, 1.03 to 30, 1.0 to 30)
        car.tick()
        assertEquals(0, car.journal.restorePoints().length())
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        // Rodada 2 (item 16): Desfazer vale até a próxima gravação (antes sumia quando a medição fechava).
        assertNotNull(car.secondary("undo"))
    }

    @Test
    fun `curva mudou por fora invalida desfazer restaurar e bloqueios e descarta o GNV`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.05).toInt() else c[i] }
        car.drive(*off)
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu(); car.tick()
        car.drive(1.0 to 12, 1.0 to 12, null to 0, null to 0, null to 0)
        assertTrue(car.journal.json().getJSONObject("latest").getBoolean("undoable"))
        // ProgBase/reset mexeu na curva: a leitura nova da ECU não bate com o que o app gravou.
        car.curve[10] = 15000
        now += 3_000
        car.readEcu()
        assertTrue(car.journal.noteEcuCurve(car.readCurve, car.readAt))
        assertFalse(car.journal.noteEcuCurve(car.readCurve, car.readAt))
        val latest = car.journal.json().getJSONObject("latest")
        assertFalse(latest.getBoolean("undoable"))
        assertEquals("INTERROMPIDO", latest.getString("status"))
        assertEquals(0, car.journal.json().getJSONArray("blocks").length())
        // O serviço alinha o acumulador na mesma leitura: o GNV medido com a curva do app não vale para a nova.
        car.tick()
        assertEquals(0, car.index.getInt("gasObservations"))
    }

    @Test
    fun `histerese na margem do dono e proposta intermitente nao piscam nem repetem aviso`() {
        val car = Car()
        var k = 0
        car.engine = { i, c -> if (k % 2 == 0 && ms(i) in 6.0..7.5) (c[i] * 1.03).toInt() else c[i] }
        car.drive(1.0 to 20, 1.0 to 20, null to 0, null to 0, null to 0)
        var flips = 0
        var alerts = 0
        var lastPhase = ""
        repeat(200) {
            k++
            car.drive(null to 0, null to 0, (if (k % 2 == 0) 1.045 else 1.055) to 20, null to 0, null to 0)
            car.readEcu() // leitura nova a cada tick: o motor responde à proposta intermitente
            val s = car.tick()
            if (lastPhase.isNotEmpty() && s.getString("phase") != lastPhase) flips++
            lastPhase = s.getString("phase")
            if (car.pilot.takeAlert() != null) alerts++
        }
        assertEquals("PROPOSTA_PRONTA", lastPhase)
        assertTrue("fase trocou $flips vezes", flips <= 1)
        assertTrue("avisou $alerts vezes", alerts <= 1)
        // Sai com folga: só depois de 3 observações seguidas sem proposta.
        car.engine = { i, c -> c[i] }
        car.readEcu()
        car.tick(); car.tick()
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        car.tick()
        assertFalse(car.last.getString("phase") == "PROPOSTA_PRONTA")
    }

    @Test
    fun `cada fase tem uma frase e no maximo um botao principal`() {
        // SEM_ECU
        val idle = RefinementAutopilot(null, clock).observe(false, null, null, empty, JSONObject().put("latest", JSONObject.NULL), 0)
        assertEquals("SEM_ECU", idle.getString("phase"))
        assertEquals("none", idle.getJSONObject("action").getString("kind"))
        assertTrue(idle.getString("headline").isNotBlank())
        // ECU no automático com proposta: aparece desabilitada.
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..5.0) (c[i] * 1.03).toInt() else c[i] }
        car.drive(*off)
        car.readEcu()
        now += 3_000
        val working = car.pilot.observe(true, JSONObject().put("autoMatchCount", 1).put("maxAutomatch", 3).put("autoCalEnabled", 1), null,
            car.index, car.journal.json(), 0, car.journal.proposalFrom(car.analysis(), car.index))
        assertEquals("ECU_TRABALHANDO", working.getString("phase"))
        assertEquals("waiting", working.getJSONObject("action").getString("kind"))
        assertFalse(working.getJSONObject("action").getBoolean("enabled"))
        // Estável: faixas dentro de ±4%, sem proposta escondida virando botão.
        val stable = RefinementAutopilot(null, clock).observe(true, monitorDone, null, index(1.01 to 20, 0.99 to 20, 1.02 to 20, null to 0, null to 0),
            JSONObject().put("latest", JSONObject.NULL), 0, car.journal.proposalFrom(car.analysis(), car.index))
        assertEquals("ESTAVEL", stable.getString("phase"))
        assertEquals("stable", stable.getJSONObject("action").getString("kind"))
        assertTrue(stable.getBoolean("canDisconnect"))
        // Coletando: nada a gravar, sem botão.
        val collecting = RefinementAutopilot(null, clock).observe(true, monitorDone, null, index(null to 2, 1.1 to 3, null to 0, null to 0, null to 0),
            JSONObject().put("latest", JSONObject.NULL), 0, JSONObject().put("known", true).put("count", 0).put("points", JSONArray()))
        assertEquals("COLETANDO_NOSSOS", collecting.getString("phase"))
        assertEquals("none", collecting.getJSONObject("action").getString("kind"))
        listOf(working, stable, collecting).forEach { assertTrue(it.getString("headline").isNotBlank()) }
    }

    @Test
    fun `ECU fora mantem a fase e desabilita o botao`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.05).toInt() else c[i] }
        car.drive(*off)
        car.readEcu(); car.tick()
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        car.online = false
        car.tick()
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        assertFalse(car.action().getBoolean("enabled"))
        assertEquals("Conecte a ECU para gravar", car.action().getString("label"))
    }

    @Test
    fun `referencia que troca no meio da medicao nao julga nem ensina nem pede restaurar`() {
        val car = Car()
        // Faixa 7,5–9 sem gasolina própria: a referência ali é a curva de gasolina da ECU.
        car.ledger.setEcuPetrolReference(listOf(0.30 to 2.8, 0.40 to 3.6, 0.50 to 4.4, 0.60 to 5.2, 0.70 to 6.0, 0.80 to 6.8, 0.90 to 7.9, 1.10 to 9.6))
        car.engine = { i, c -> if (ms(i) in 7.5..8.5) (c[i] * 1.05).toInt() else c[i] }
        now += 2_000
        repeat(14) { car.ledger.accept(EquivalenceLedger.Frame(now, "GNV", 2000.0, 0.95, 8.35 * 1.07, 0.0)); now += 280 }
        car.drive(1.0 to 12, 1.0 to 12, 1.0 to 12, null to 0, null to 0)
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu()
        // Depois: o dono rodou na gasolina ali (quente) e a gasolina própria passa a valer na mesma faixa.
        now += 2_000
        repeat(10) { car.ledger.accept(EquivalenceLedger.Frame(now, "GASOLINA", 2000.0, 0.95, 8.0, 0.0)); now += 280 }
        now += 2_000
        repeat(14) { car.ledger.accept(EquivalenceLedger.Frame(now, "GNV", 2000.0, 0.95, 8.35 * 1.07, 0.0)); now += 280 }
        car.tick()
        val band = car.journal.json().getJSONObject("latest").getJSONArray("bands").getJSONObject(3)
        assertEquals("COLETANDO", band.getString("verdict"))
        assertTrue(band.optBoolean("referenceChanged"))
        assertEquals(1.0, car.journal.json().getJSONArray("bandScale").getDouble(3), 1e-9)
        assertEquals(0, car.journal.restorePoints().length())
    }

    @Test
    fun `diario de versao antiga carregado no carro nao vira restaurar`() {
        val dir = Files.createTempDirectory("journal-legacy-car").toFile()
        val file = File(dir, "refinement_journal.json")
        val bands = JSONArray(); val before = JSONArray()
        EquivalenceLedger.BANDS.forEachIndexed { i, (lo, hi) ->
            before.put(JSONObject().put("fromMs", lo).put("toMs", hi).put("samples", 20).put("ratio", if (i == 3) 1.01 else 1.0))
            bands.put(JSONObject().put("fromMs", lo).put("toMs", hi).put("samplesBefore", 20).put("samplesAfter", 9)
                .put("ratioBefore", if (i == 3) 1.01 else 1.0).put("ratioAfter", if (i == 3) 1.035 else 1.0)
                .put("verdict", if (i == 3) "PIOROU" else "NAO_ALTERADA"))
        }
        val after = IntArray(30) { if (it in 14..16) 16900 else 16384 }
        file.writeText(JSONObject().put("format", RefinementJournal.FORMAT).put("experiments", JSONArray().put(JSONObject()
            .put("id", "EXP-1").put("appliedAt", 1L).put("axisRaw", JSONArray(axisRaw.toList())).put("beforeRaw", JSONArray(IntArray(30) { 16384 }.toList()))
            .put("afterRaw", JSONArray(after.toList())).put("indexBefore", JSONObject().put("bands", before))
            .put("status", "PIOROU_EM_PARTE").put("bands", bands))).toString())
        val car = Car(file)
        car.curve = after.copyOf()
        car.drive(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.035 to 12, 1.0 to 12)
        car.readEcu(); car.tick()
        assertFalse(car.last.getString("phase") == "RESTAURAR_TRECHO")
        assertEquals(0, car.journal.restorePoints().length())
        assertEquals(RefinementJournal.KIND_LEGADO, car.journal.json().getJSONObject("latest").getString("kind"))
        dir.deleteRecursively()
    }

    private fun referenceSession(): JSONObject {
        val file = listOf("../fixtures/autocal/real/ref_2026-10-01_1719.json.gz", "fixtures/autocal/real/ref_2026-10-01_1719.json.gz")
            .map(::File).first { it.exists() }
        return JSONObject(GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() })
    }

    @Test
    fun `motor real na sessao de referencia vira a mesma lista que a revisao grava`() {
        val snapshots = referenceSession().getJSONArray("snapshots")
        val snapshot = (0 until snapshots.length()).map { snapshots.getJSONObject(it) }.first { it.getInt("sequence") == 95 }
        val analysis = AutoMatchSnapshotAnalysis.analyzeRefined(snapshot)
        assertTrue(analysis.getBoolean("available"))
        assertTrue(analysis.has("mulActCapturedAtMs"))
        val engineChanged = analysis.getJSONArray("points").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .filter { it.optString("origin") != "HELD" && it.getInt("calculatedRaw") != it.getInt("currentRaw") }
        val journal = RefinementJournal(null, clock)
        val proposal = journal.proposalFrom(analysis, empty)
        assertEquals(engineChanged.size, proposal.getInt("count"))
        val points = proposal.getJSONArray("points")
        engineChanged.forEachIndexed { k, p ->
            assertEquals(p.getInt("index"), points.getJSONObject(k).getInt("index"))
            assertEquals("valor do motor sem mudança", p.getInt("calculatedRaw"), points.getJSONObject(k).getInt("targetRaw"))
        }
        val pilot = RefinementAutopilot(null, clock)
        val decided = pilot.observe(true, monitorDone, null, index(1.06 to 20, 1.05 to 20, 1.0 to 20, null to 0, null to 0),
            journal.json(), 0, proposal)
        assertEquals("PROPOSTA_PRONTA", decided.getString("phase"))
        assertEquals("Revisar e gravar ${engineChanged.size} pontos", decided.getJSONObject("action").getString("label"))
        assertEquals(proposal.getString("signature"), decided.getJSONObject("proposal").getString("signature"))
    }

    /**
     * Pedido do líder (rodada 2): replay da sessão de referência pelo caminho do serviço (acumulador real, cola real,
     * motor real) não propõe nada por causa da lenta. Uma hora a mais de lenta (gasolina e GNV, GNV 20% rico, o
     * cenário do "dente de serra") não muda um par nem um ponto da proposta.
     */
    @Test
    fun `replay da sessao de referencia nao propoe nada por causa da lenta`() {
        val session = referenceSession()
        val telemetry = session.getJSONArray("telemetry")
        val snapshot = session.getJSONArray("snapshots").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.first { it.getInt("sequence") == 95 }
        fun replay(extraIdle: Boolean): Pair<List<EquivalenceLedger.EvidencePair>, JSONObject> {
            val ledger = EquivalenceLedger(null, clock)
            val cycle = RefinementCycle(ledger, RefinementJournal(null, clock), RefinementAutopilot(null, clock), monotonicMs = { now })
            var last = 0L
            for (i in 0 until telemetry.length()) {
                val f = telemetry.getJSONObject(i)
                last = f.getLong("t")
                ledger.accept(EquivalenceLedger.Frame(last, f.optString("fuel").uppercase(), f.optDouble("rpm", 0.0).takeIf { it.isFinite() } ?: 0.0,
                    f.optDouble("load_bar", 0.0).takeIf { it.isFinite() } ?: 0.0, f.optDouble("petrol_ms", 0.0).takeIf { it.isFinite() } ?: 0.0))
            }
            if (extraIdle) {
                var t = last + 60_000
                repeat(6_000) { k -> // ~28 min de cada combustível em lenta (850–1300 rpm)
                    val rpm = 850.0 + (k % 10) * 50
                    ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", rpm, 0.40, 3.8)); t += 280
                }
                t += 5_000
                repeat(6_000) { k ->
                    val rpm = 850.0 + (k % 10) * 50
                    ledger.accept(EquivalenceLedger.Frame(t, "GNV", rpm, 0.40, 3.8 * 1.20)); t += 280
                }
            }
            now = last + 10_000_000
            return ledger.pairs() to cycle.proposal(snapshot, ledger.index())
        }
        val (pairs, proposal) = replay(extraIdle = false)
        val (idlePairs, idleProposal) = replay(extraIdle = true)
        assertTrue(pairs.isNotEmpty())
        assertTrue("par abaixo de ${EquivalenceLedger.DRIVING_MIN_RPM} rpm", pairs.all { it.rpm >= EquivalenceLedger.DRIVING_MIN_RPM })
        assertEquals("a lenta mudou os pares da curva", pairs, idlePairs)
        assertTrue(proposal.getBoolean("known"))
        assertEquals("a lenta mudou a proposta", proposal.getJSONArray("points").toString(), idleProposal.getJSONArray("points").toString())
    }

    @Test
    fun `leitura incompleta da ECU nunca parece aquisicao nova de gasolina`() {
        fun acquisition(vararg points: JSONObject) = JSONObject().put("points", JSONArray(points.toList()))
        fun petrol(state: String, draw: Boolean) = JSONObject().put("fuel", "GASOLINA").put("previous", false).put("state", state).put("draw", draw)
        assertNull(EcuPetrolReference.petrolActivity(null))
        assertNull(EcuPetrolReference.petrolActivity(acquisition(petrol("SEM_DADO", false), petrol("AGUARDANDO", false))))
        assertEquals(2, EcuPetrolReference.petrolActivity(acquisition(petrol("ZONA_ADQUIRIDA", true), petrol("ATIVIDADE", true), petrol("AGUARDANDO", false))))
        assertEquals(0, EcuPetrolReference.petrolActivity(acquisition(petrol("AGUARDANDO", false), petrol("AGUARDANDO", false))))
        // Curva de gasolina da ECU: só zonas adquiridas, nunca o buffer anterior; leitura incompleta = desconhecida.
        val ref = EcuPetrolReference.fromAcquisition(acquisition(
            petrol("ZONA_ADQUIRIDA", true).put("timeMs", 5.2).put("mapBar", 0.6),
            petrol("ATIVIDADE", true).put("timeMs", 6.0).put("mapBar", 0.7),
            petrol("ZONA_ADQUIRIDA", true).put("timeMs", 4.4).put("mapBar", 0.5).put("previous", true),
        ))
        assertEquals(listOf(0.6 to 5.2), ref)
        assertNull(EcuPetrolReference.fromAcquisition(null))
        assertNull(EcuPetrolReference.fromAcquisition(acquisition(petrol("SEM_DADO", false).put("index", 9))))
    }
}
