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
import java.util.zip.GZIPInputStream

/**
 * Ciclo do refino (classe 2, sintético, com diário e piloto reais): cada transição e os cenários do dono.
 * O "motor" aqui é uma função de proposta sobre a curva lida da ECU (o motor real não muda e tem teste
 * próprio); o último teste usa o motor real numa sessão de referência gravada no carro (classe 3).
 */
class RefinementCycleTest {
    private var now = 5_000_000L
    private val clock = { now }
    private val axisRaw = IntArray(30) { if (it < 20) 256 * (it + 1) else 5632 + 512 * (it - 20) }
    private fun ms(i: Int) = axisRaw[i] / 512.0
    private val monitorDone = JSONObject().put("autoMatchCount", 3).put("maxAutomatch", 3).put("autoCalEnabled", 1)

    private fun index(vararg bands: Pair<Double?, Int>): JSONObject = JSONObject()
        .put("samples", bands.sumOf { it.second }).put("petrolObservations", 500).put("gasObservations", 200).put("ratio", 1.0)
        .put("bands", JSONArray(bands.mapIndexed { i, (ratio, n) ->
            JSONObject().put("fromMs", EquivalenceLedger.BANDS[i].first).put("toMs", EquivalenceLedger.BANDS[i].second)
                .put("samples", n).put("ratio", ratio ?: JSONObject.NULL)
        }))

    private val empty get() = index(null to 0, null to 0, null to 0, null to 0, null to 0)

    /** Diário + piloto reais; a ECU guarda a curva; a leitura da ECU (snapshot) atrasa até [readEcu]. */
    private inner class Car {
        val journal = RefinementJournal(null, clock)
        val pilot = RefinementAutopilot(null, clock)
        var curve = IntArray(30) { 16384 }
        var readAt = 0L
        var readCurve = curve.copyOf()
        var engine: (Int, IntArray) -> Int = { i, c -> c[i] }
        var index = index(1.05 to 20, 1.05 to 20, 1.05 to 20, 1.05 to 20, 1.05 to 20)
        var online = true
        var last = JSONObject()

        fun readEcu() { readAt = now; readCurve = curve.copyOf() }

        fun analysis(): JSONObject {
            val points = JSONArray()
            for (i in 0 until 30) {
                val target = engine(i, readCurve)
                points.put(JSONObject().put("index", i).put("referenceTimeMs", ms(i)).put("currentRaw", readCurve[i])
                    .put("calculatedRaw", target).put("origin", if (target == readCurve[i]) "HELD" else "MEASURED"))
            }
            return JSONObject().put("ok", true).put("available", true).put("snapshotHash", "S$readAt")
                .put("capturedAtMs", readAt).put("mulActCapturedAtMs", readAt).put("points", points)
        }

        fun tick(): JSONObject {
            now += 3_000
            val since: (Long) -> JSONObject = { index }
            journal.evaluate(index, online, since)
            last = pilot.observe(online, monitorDone, null, index, journal.json(), journal.restorePoints().length(),
                journal.proposalFrom(analysis(), index, since))
            return last
        }

        /** Toque em "Gravar na ECU": grava exatamente a lista, com o motivo que a tela usa; GNV daquela região zera. */
        fun write(points: JSONArray, reason: String): JSONObject {
            val before = curve.copyOf()
            for (k in 0 until points.length()) {
                val p = points.getJSONObject(k)
                assertEquals("lista velha gravada no ponto ${p.getInt("index")}", curve[p.getInt("index")], p.getInt("currentRaw"))
                curve[p.getInt("index")] = p.getInt("targetRaw")
            }
            now += 1_000
            journal.recordCurveWrite(before, curve.copyOf(), axisRaw, index, "KF-$now", RefinementJournal.kindFromReason(reason))
            index = empty
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

    @Test
    fun `motivo da gravacao define o tipo e o resto e ajuste manual`() {
        assertEquals(RefinementJournal.KIND_PROPOSTA, RefinementJournal.kindFromReason(propose))
        assertEquals(RefinementJournal.KIND_RESTAURAR, RefinementJournal.kindFromReason(restore))
        assertEquals(RefinementJournal.KIND_RESTAURAR, RefinementJournal.kindFromReason("Refino OMEGAS: restaurar trecho que piorou"))
        assertEquals(RefinementJournal.KIND_DESFAZER, RefinementJournal.kindFromReason(undo))
        assertEquals(RefinementJournal.KIND_MANUAL, RefinementJournal.kindFromReason("Ajuste manual confirmado na UI clean-slate"))
        assertEquals(RefinementJournal.KIND_MANUAL, RefinementJournal.kindFromReason("Restaurar backup Curva K curva.json"))
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
        car.readEcu()
        val s0 = car.tick()
        assertEquals("PROPOSTA_PRONTA", s0.getString("phase"))
        car.write(car.proposalPoints(), propose)
        car.readEcu(); wave = 2
        car.index = index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.05 to 12, 1.03 to 3) // 9–12 ms nunca chega a 8 leituras
        repeat(290) { car.tick() } // ~14,5 min
        assertEquals("VERIFICANDO", car.last.getString("phase"))
        assertTrue("pontos da faixa em medição ficam fora da proposta", car.proposedIndexes().isEmpty())
        assertEquals("none", car.action().getString("kind"))
        repeat(20) { car.tick() } // passa de 15 min de ECU online
        assertFalse(car.journal.json().getJSONObject("latest").getString("status") == RefinementJournal.STATUS_VERIFYING)
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        assertTrue(car.proposedIndexes().all { ms(it) in 9.0..12.0 })
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
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu()
        car.index = index(1.0 to 12, 1.0 to 12, 1.0 to 12, null to 3, null to 0)
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
        car.readEcu()
        car.tick()
        val written = car.write(car.proposalPoints(), propose)
        // Leitura da ECU ainda é a de antes da gravação: nada é proposto sobre a curva velha.
        assertEquals("VERIFICANDO", written.getString("phase"))
        assertEquals("none", written.getJSONObject("action").getString("kind"))
        assertEquals(0, written.getJSONObject("proposal").getInt("count"))
        assertEquals("LENDO_CURVA_NOVA", written.getJSONObject("proposal").getString("reason"))
        // Desfazer já aparece como opção (secundária) enquanto mede; não existe "Entendi".
        assertNotNull(car.secondary("undo"))
        assertNull(car.secondary("ack"))
        assertFalse(written.toString().contains("Entendi"))
    }

    @Test
    fun `piorou oferece restaurar so os pontos que pioraram e restaurar nao ensina o ganho`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.05).toInt() else c[i] }
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu()
        car.index = index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.12 to 12, 1.0 to 12)
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
        car.index = index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.0 to 12, 1.0 to 12)
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
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu()
        car.index = index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.12 to 12, 1.0 to 12)
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
        car.index = index(1.0 to 12, 1.0 to 12, 1.0 to 12, 1.02 to 12, 1.0 to 12)
        repeat(3) { car.tick() }
        val proposed = car.proposalPoints()
        for (k in 0 until proposed.length()) {
            val p = proposed.getJSONObject(k)
            val worse = bad[p.getInt("index")] ?: continue
            assertTrue("ponto ${p.getInt("index")} voltaria ao valor que piorou", p.getInt("targetRaw") < worse)
        }
        // Metade do passo (o ganho já aprendido com o "piorou" da proposta) é permitida; o valor ruim nunca.
        car.engine = { i, c -> if (i in bad.keys) (c[i] * 1.02).toInt() else c[i] }
        repeat(2) { car.tick() }
        assertTrue(car.proposedIndexes().any { it in bad.keys })
        assertTrue(car.proposalPoints().let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .filter { it.getInt("index") in bad.keys }.all { it.getInt("targetRaw") < bad.getValue(it.getInt("index")) })
    }

    @Test
    fun `restauracao medida pior nao oferece restaurar de novo nem volta ao valor ruim`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.10).toInt() else c[i] }
        car.readEcu(); car.tick()
        car.index = index(1.05 to 20, 1.06 to 20, 1.05 to 20, 1.02 to 20, 1.03 to 20)
        car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu()
        car.index = index(0.99 to 10, 1.01 to 10, 1.0 to 10, 1.09 to 10, 1.0 to 10)
        car.tick()
        val restorePoints = car.journal.restorePoints()
        car.write(restorePoints, restore)
        car.readEcu()
        // A medição da restauração sai pior: antes isso pedia "restaurar" de volta o valor ruim (laço).
        car.index = index(0.99 to 10, 1.01 to 10, 1.0 to 10, 1.20 to 10, 1.0 to 10)
        car.tick()
        assertEquals(0, car.journal.restorePoints().length())
        assertFalse(car.last.getString("phase") == "RESTAURAR_TRECHO")
    }

    @Test
    fun `desfazer nao regrava o erro nem reaparece e o ciclo continua`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.04).toInt() else c[i] }
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
        car.index = index(1.04 to 12, 1.04 to 12, 1.04 to 12, 1.04 to 12, 1.04 to 12)
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
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu(); wave = 2
        car.index = index(1.03 to 12, 1.03 to 12, null to 0, null to 2, null to 0)
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
        car.engine = { i, c -> if (wave == 1 && ms(i) in 7.5..9.0) (c[i] * 1.05).toInt() else if (wave == 2 && ms(i) in 3.0..5.0) (c[i] * 1.03).toInt() else c[i] }
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu(); wave = 2
        car.index = index(1.03 to 12, 1.03 to 12, 1.0 to 12, 1.12 to 12, 1.0 to 12)
        car.tick()
        assertEquals("RESTAURAR_TRECHO", car.last.getString("phase"))
        assertNotNull("proposta dos outros pontos ao lado do Restaurar", car.secondary("review"))
        // O dono não restaura; a medição seguinte mostra a faixa de volta dentro da margem: a oferta some sozinha.
        car.index = index(1.03 to 30, 1.03 to 30, 1.0 to 30, 1.03 to 30, 1.0 to 30)
        car.tick()
        assertEquals(0, car.journal.restorePoints().length())
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        assertNull("o resultado fechou sozinho: Desfazer some", car.secondary("undo"))
    }

    @Test
    fun `curva mudou por fora invalida desfazer restaurar e bloqueios`() {
        val car = Car()
        car.engine = { i, c -> if (ms(i) in 3.0..12.0) (c[i] * 1.05).toInt() else c[i] }
        car.readEcu(); car.tick()
        car.write(car.proposalPoints(), propose)
        car.readEcu(); car.tick()
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
    }

    @Test
    fun `histerese faixa entre 2,9 e 3,1 por cento e proposta intermitente nao piscam nem repetem aviso`() {
        val car = Car()
        var k = 0
        car.engine = { i, c -> if (k % 2 == 0 && ms(i) in 6.0..7.5) (c[i] * 1.03).toInt() else c[i] }
        car.readEcu()
        var flips = 0
        var alerts = 0
        var lastPhase = ""
        repeat(200) {
            k++
            car.index = index(1.0 to 20, 1.0 to 20, (if (k % 2 == 0) 1.029 else 1.031) to 20, null to 0, null to 0)
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
        car.readEcu()
        now += 3_000
        val working = car.pilot.observe(true, JSONObject().put("autoMatchCount", 1).put("maxAutomatch", 3).put("autoCalEnabled", 1), null,
            car.index, car.journal.json(), 0, car.journal.proposalFrom(car.analysis(), car.index))
        assertEquals("ECU_TRABALHANDO", working.getString("phase"))
        assertEquals("waiting", working.getJSONObject("action").getString("kind"))
        assertFalse(working.getJSONObject("action").getBoolean("enabled"))
        // Estável: faixas dentro de ±3%, sem proposta escondida virando botão.
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
        car.readEcu(); car.tick()
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        car.online = false
        car.tick()
        assertEquals("PROPOSTA_PRONTA", car.last.getString("phase"))
        assertFalse(car.action().getBoolean("enabled"))
        assertEquals("Conecte a ECU para gravar", car.action().getString("label"))
    }

    @Test
    fun `motor real na sessao de referencia vira a mesma lista que a revisao grava`() {
        val file = listOf("../fixtures/autocal/real/ref_2026-10-01_1719.json.gz", "fixtures/autocal/real/ref_2026-10-01_1719.json.gz")
            .map(::File).first { it.exists() }
        val snapshots = JSONObject(GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }).getJSONArray("snapshots")
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

    @Test
    fun `leitura incompleta da ECU nunca parece aquisicao nova de gasolina`() {
        fun acquisition(vararg points: JSONObject) = JSONObject().put("points", JSONArray(points.toList()))
        fun petrol(state: String, draw: Boolean) = JSONObject().put("fuel", "GASOLINA").put("previous", false).put("state", state).put("draw", draw)
        assertNull(EcuPetrolReference.petrolActivity(null))
        assertNull(EcuPetrolReference.petrolActivity(acquisition(petrol("SEM_DADO", false), petrol("AGUARDANDO", false))))
        assertEquals(2, EcuPetrolReference.petrolActivity(acquisition(petrol("ZONA_ADQUIRIDA", true), petrol("ATIVIDADE", true), petrol("AGUARDANDO", false))))
        assertEquals(0, EcuPetrolReference.petrolActivity(acquisition(petrol("AGUARDANDO", false), petrol("AGUARDANDO", false))))
        // Curva de gasolina da ECU: só zonas adquiridas, nunca o buffer anterior.
        val ref = EcuPetrolReference.fromAcquisition(acquisition(
            petrol("ZONA_ADQUIRIDA", true).put("timeMs", 5.2).put("mapBar", 0.6),
            petrol("ATIVIDADE", true).put("timeMs", 6.0).put("mapBar", 0.7),
            petrol("ZONA_ADQUIRIDA", true).put("timeMs", 4.4).put("mapBar", 0.5).put("previous", true),
        ))
        assertEquals(listOf(0.6 to 5.2), ref)
    }
}
