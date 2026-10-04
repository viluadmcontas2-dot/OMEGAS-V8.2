package com.omegas.prohub.autocal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

class EquivalenceLedgerTest {
    private fun drive(ledger: EquivalenceLedger, fuel: String, rpm: Double, map: Double, ms: Double, start: Long, n: Int, gasMs: Double = 0.0): Long {
        var t = start
        repeat(n) { ledger.accept(EquivalenceLedger.Frame(t, fuel, rpm, map, ms, gasMs)); t += 280 }
        return t
    }

    @Test
    fun `combustivel vivo e o do ultimo quadro recente - velho ou ausente e desconhecido`() {
        var now = 1_000_000L
        val ledger = EquivalenceLedger(null) { now }
        assertNull(ledger.liveFuel())
        ledger.accept(EquivalenceLedger.Frame(now - 1_000L, "GASOLINA", 2000.0, 0.6, 5.0))
        assertEquals("GASOLINA", ledger.liveFuel())
        // Quadro inválido para o livro (rpm 0) ainda diz o combustível; transição e motor desligado também.
        ledger.accept(EquivalenceLedger.Frame(now - 900L, "TRANSICAO", 0.0, 0.0, 0.0))
        assertEquals("TRANSICAO", ledger.liveFuel())
        ledger.accept(EquivalenceLedger.Frame(now - 800L, "GNV", 2000.0, 0.6, 5.5))
        assertEquals("GNV", ledger.liveFuel())
        now += EquivalenceLedger.LIVE_FUEL_MAX_AGE_MS + 1_000L
        assertNull(ledger.liveFuel())
        ledger.accept(EquivalenceLedger.Frame(now, "", 2000.0, 0.6, 5.5))
        assertNull(ledger.liveFuel())
    }

    @Test
    fun `leitura estavel exige tres quadros e o indice compara GNV com gasolina no mesmo ponto`() {
        val ledger = EquivalenceLedger(null)
        var t = drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, 0, 10)
        t = drive(ledger, "GNV", 2000.0, 0.60, 5.5, t + 5_000, 10)
        val pairs = ledger.pairs()
        assertEquals(8, pairs.size)
        assertTrue(pairs.all { abs(it.petrolRefMs - 5.0) < 1e-9 && abs(it.gasPetrolMs - 5.5) < 1e-9 })
        val index = ledger.index()
        assertEquals(1.1, index.getDouble("ratio"), 1e-9)
        val band = index.getJSONArray("bands").getJSONObject(1)
        assertEquals(4.5, band.getDouble("fromMs"), 0.0)
        assertEquals(8, band.getInt("samples"))
    }

    @Test
    fun `transiente e corte nao viram evidencia`() {
        val ledger = EquivalenceLedger(null)
        var t = 0L
        listOf(1500.0, 1800.0, 2100.0, 2400.0).forEach { rpm -> ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", rpm, 0.5, 4.0)); t += 280 }
        ledger.accept(EquivalenceLedger.Frame(t, "CUTOFF", 2000.0, 0.2, 0.0))
        assertEquals(0, ledger.index().getInt("petrolObservations"))
    }

    @Test
    fun `marcha lenta fica fora do indice de conducao`() {
        val ledger = EquivalenceLedger(null)
        var t = drive(ledger, "GASOLINA", 870.0, 0.35, 4.0, 0, 10)
        drive(ledger, "GNV", 870.0, 0.35, 4.6, t + 1_000, 10)
        assertTrue(ledger.pairs().isNotEmpty())
        assertEquals(0, ledger.index().getInt("samples"))
        assertTrue(ledger.index().isNull("ratio"))
    }

    @Test
    fun `curva gravada descarta o GNV antigo e preserva a gasolina`() {
        val ledger = EquivalenceLedger(null)
        var t = drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10, gasMs = 12.0)
        assertTrue(ledger.gasPerAir() != null)
        ledger.resetGas("CURVA_K_GRAVADA")
        val index = ledger.index()
        assertEquals(0, index.getInt("gasObservations"))
        assertEquals(8, index.getInt("petrolObservations"))
        assertEquals("CURVA_K_GRAVADA", index.getString("gasEpochReason"))
        assertNull(ledger.gasPerAir())
    }

    @Test
    fun `curva alterada por fora do app descarta o GNV medido com a curva antiga`() {
        val ledger = EquivalenceLedger(null)
        var t = drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        ledger.alignCurve("A")
        drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        assertEquals(8, ledger.index().getInt("gasObservations"))
        ledger.alignCurve("C")
        assertEquals(0, ledger.index().getInt("gasObservations"))
        assertEquals(8, ledger.index().getInt("petrolObservations"))
    }

    @Test
    fun `curva gravada pelo app tambem descarta o GNV medido com a curva anterior mas nao o medido depois`() {
        var now = 0L
        val ledger = EquivalenceLedger(null) { now }
        var t = drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        ledger.alignCurve("A")
        t = drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        assertEquals(8, ledger.index().getInt("gasObservations"))
        // Quem grava esqueceu o resetGas: adoptCurve descarta, de forma explícita, o GNV medido sob o K antigo.
        ledger.adoptCurve("B")
        assertEquals(0, ledger.index().getInt("gasObservations"))
        assertEquals("CURVA_K_GRAVADA_PELO_APP", ledger.index().getString("gasEpochReason"))
        assertTrue(ledger.pairs().isEmpty())
        assertEquals(8, ledger.index().getInt("petrolObservations"))
        // O GNV medido DEPOIS, já com a curva B, vale; e re-adotar a mesma curva não apaga nada.
        drive(ledger, "GNV", 2000.0, 0.6, 5.6, t + 1_000, 10)
        ledger.adoptCurve("B")
        ledger.alignCurve("B")
        now += EquivalenceLedger.INDEX_MIN_INTERVAL_MS + 1 // o índice só recalcula depois do intervalo mínimo
        assertEquals(8, ledger.index().getInt("gasObservations"))
    }

    @Test
    fun `fluxo do servico resetGas e depois adoptCurve preserva a causa do reset`() {
        val ledger = EquivalenceLedger(null)
        var t = drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        ledger.alignCurve("A")
        drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        ledger.resetGas("CURVA_K_GRAVADA")
        ledger.adoptCurve("B")
        assertEquals("CURVA_K_GRAVADA", ledger.index().getString("gasEpochReason"))
    }

    @Test
    fun `indice so e recalculado com revisao nova e ao menos 1 s depois, e mudanca estrutural fura`() {
        var now = 1_000_000L
        val ledger = EquivalenceLedger(null) { now }
        var t = drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        t = drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        val first = ledger.index()
        assertEquals(8, first.getInt("gasObservations"))
        // Mais leituras estáveis dentro de 1 s: o índice devolvido é o mesmo (nada de reordenar tudo a cada janela).
        t = drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        assertTrue(ledger.revision() > first.getLong("revision"))
        now += 500
        assertEquals(first.getInt("gasObservations"), ledger.index().getInt("gasObservations"))
        assertEquals(first.getLong("revision"), ledger.index().getLong("revision"))
        // Passou 1 s e a revisão mudou: recalcula.
        now += 600
        val second = ledger.index()
        assertTrue(second.getInt("gasObservations") > first.getInt("gasObservations"))
        assertEquals(ledger.revision(), second.getLong("revision"))
        // Sem revisão nova, nunca recalcula (mesmo passado muito tempo).
        now += 60_000
        assertEquals(second.getLong("revision"), ledger.index().getLong("revision"))
        // Mudança estrutural (reset do GNV) vale na hora, sem esperar 1 s.
        drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        ledger.resetGas("TESTE")
        assertEquals(0, ledger.index().getInt("gasObservations"))
    }

    @Test
    fun `arquivo no formato antigo de um array por leitura ainda carrega`() {
        val dir = Files.createTempDirectory("ledger-legacy").toFile()
        val file = File(dir, "equivalence_ledger.json")
        val petrol = org.json.JSONArray()
        val gas = org.json.JSONArray()
        for (i in 0 until 6) {
            petrol.put(org.json.JSONArray().put(1_000L + i * 400).put(2000.0).put(0.6).put(5.0))
            gas.put(org.json.JSONArray().put(9_000L + i * 400).put(2000.0).put(0.6).put(5.5))
        }
        file.writeText(JSONObject().put("format", EquivalenceLedger.FORMAT).put("curveFingerprint", "abc")
            .put("gasEpochReason", "ANTIGO").put("gasEpochAt", 42L).put("gasUsefulRpmMs", 12.5).put("airRpmBar", 3.0)
            .put("petrol", petrol).put("gas", gas).toString())
        val loaded = EquivalenceLedger(file)
        val index = loaded.index()
        assertEquals(6, index.getInt("petrolObservations"))
        assertEquals(6, index.getInt("gasObservations"))
        assertEquals("ANTIGO", index.getString("gasEpochReason"))
        assertEquals(6, loaded.pairs().size)
        assertTrue(loaded.pairs().all { abs(it.petrolRefMs - 5.0) < 1e-9 && abs(it.gasPetrolMs - 5.5) < 1e-9 })
        assertEquals(12.5 / 3.0, loaded.gasPerAir()!!, 1e-9)
        // Salvar de novo grava o formato plano e ele volta igual.
        loaded.alignCurve("abc")
        loaded.flush()
        val saved = JSONObject(file.readText())
        assertEquals(EquivalenceLedger.LAYOUT_FLAT, saved.getString("layout"))
        assertEquals(6, saved.getJSONObject("petrol").getJSONArray("t").length())
        assertEquals(6, EquivalenceLedger(file).pairs().size)
        dir.deleteRecursively()
    }

    @Test
    fun `salvar e compacto e o arquivo plano volta com os mesmos pares`() {
        val dir = Files.createTempDirectory("ledger-flat").toFile()
        val file = File(dir, "equivalence_ledger.json")
        val ledger = EquivalenceLedger(file)
        val rnd = java.util.Random(11)
        var t = 0L
        repeat(600) {
            val rpm = 1100.0 + rnd.nextDouble() * 3000
            val map = 0.25 + rnd.nextDouble() * 0.7
            repeat(3) { ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", rpm, map, 2.0 + map * 10 + 1.0 / 3.0)); t += 100 }
            t += 5_000
        }
        ledger.flush()
        val text = file.readText()
        val flatSize = text.length
        val saved = JSONObject(text)
        val n = saved.getJSONObject("petrol").getJSONArray("t").length()
        assertEquals(n, saved.getJSONObject("petrol").getJSONArray("ms").length())
        assertTrue("sem arrays aninhados", !text.contains("[["))
        // Cada leitura ocupa bem menos que as 4 doubles de 17 dígitos do formato antigo (~75 bytes).
        assertTrue("bytes por leitura ${flatSize / n}", flatSize / n < 50)
        val reloaded = EquivalenceLedger(file)
        assertEquals(ledger.index().getInt("petrolObservations"), reloaded.index().getInt("petrolObservations"))
        dir.deleteRecursively()
    }

    @Test
    fun `persistencia sobrevive a reinicio do app`() {
        val dir = Files.createTempDirectory("ledger").toFile()
        val file = File(dir, "equivalence_ledger.json")
        val ledger = EquivalenceLedger(file)
        val t = drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        ledger.flush()
        val reloaded = EquivalenceLedger(file)
        assertEquals(ledger.pairs().size, reloaded.pairs().size)
        assertEquals(EquivalenceLedger.FORMAT, JSONObject(file.readText()).getString("format"))
        dir.deleteRecursively()
    }

    @Test
    fun `busca em grade da o mesmo resultado da busca exaustiva`() {
        val ledger = EquivalenceLedger(null)
        val rnd = java.util.Random(7)
        val petrol = ArrayList<DoubleArray>()
        var t = 0L
        repeat(1200) {
            val rpm = 900.0 + rnd.nextDouble() * 3600
            val map = 0.25 + rnd.nextDouble() * 0.7
            val ms = 2.0 + map * 10 + rnd.nextDouble()
            repeat(3) { ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", rpm, map, ms)); t += 100 }
            petrol += doubleArrayOf(rpm, map, ms)
            t += 5_000
        }
        val gas = ArrayList<DoubleArray>()
        repeat(400) {
            val rpm = 900.0 + rnd.nextDouble() * 3600
            val map = 0.25 + rnd.nextDouble() * 0.7
            val ms = 2.0 + map * 11
            repeat(3) { ledger.accept(EquivalenceLedger.Frame(t, "GNV", rpm, map, ms)); t += 100 }
            gas += doubleArrayOf(rpm, map, ms)
            t += 5_000
        }
        val expected = gas.mapNotNull { g ->
            val m = petrol.filter { abs(it[0] - g[0]) <= 150.0 && abs(it[1] - g[1]) <= 0.02 }.map { it[2] }.sorted()
            if (m.size < 2) null else m[m.size / 2] to g[2]
        }
        val actual = ledger.pairs().map { it.petrolRefMs to it.gasPetrolMs }
        assertTrue(expected.size > 50)
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.first, a.first, 1e-9)
            assertEquals(e.second, a.second, 1e-9)
        }
    }

    @Test
    fun `uma regiao muito visitada nao apaga as outras regioes`() {
        val ledger = EquivalenceLedger(null)
        var t = drive(ledger, "GASOLINA", 3000.0, 0.80, 8.0, 0, 6)
        // marcha lenta / cruzeiro repetido por muito tempo na mesma região
        t = drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, t + 5_000, 3_000)
        val index = ledger.index()
        assertTrue(index.getInt("petrolObservations") <= 2 * EquivalenceLedger.CELL_CAP)
        val dense = ledger.denseBandsJson(0.025, 1).getJSONArray("petrol")
        assertTrue((0 until dense.length()).any { abs(dense.getJSONObject(it).getDouble("tpetMs") - 8.0) < 1e-9 })
    }

    @Test
    fun `observacoes por combustivel saem na ordem de chegada e o resetGas limpa so o GNV`() {
        val ledger = EquivalenceLedger(null)
        val t = drive(ledger, "GASOLINA", 2000.0, 0.50, 5.0, 0, 6)
        drive(ledger, "GNV", 2000.0, 0.50, 5.5, t + 5_000, 6)
        assertEquals(4, ledger.petrolObservations().size)
        assertEquals(4, ledger.gasObservations().size)
        assertTrue(ledger.petrolObservations().zipWithNext().all { (a, b) -> a.t < b.t })
        assertTrue(ledger.gasObservations().all { abs(it.petrolMs - 5.5) < 1e-9 })
        ledger.resetGas("TESTE")
        assertEquals(4, ledger.petrolObservations().size)
        assertEquals(0, ledger.gasObservations().size)
    }
}
