package com.omegas.prohub.autocal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/**
 * Ajustado (dono, 2026-10-16): a janela estável zera ao emitir uma leitura (3 quadros = 1 leitura, sem sobreposição),
 * então [drive] passa a pedir [n] LEITURAS (3·n quadros) e as contagens que antes eram 8 (10 quadros deslizando)
 * viram 10. O cache de pares vale só enquanto a revisão do ledger não muda (nenhum prazo de relógio).
 */
class EquivalenceLedgerTest {
    private var now = 0L

    /** [n] leituras estáveis (3 quadros a 280 ms cada) a partir de [start]; devolve o instante seguinte. */
    private fun drive(ledger: EquivalenceLedger, fuel: String, rpm: Double, map: Double, ms: Double, start: Long, n: Int, gasMs: Double = 0.0): Long {
        var t = start
        repeat(3 * n) { ledger.accept(EquivalenceLedger.Frame(t, fuel, rpm, map, ms, gasMs)); t += 280 }
        now = t
        return t
    }

    @Test
    fun `leitura estavel exige tres quadros e o indice compara GNV com gasolina no mesmo ponto`() {
        val ledger = EquivalenceLedger(null) { now }
        var t = drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, 0, 10)
        t = drive(ledger, "GNV", 2000.0, 0.60, 5.5, t + 5_000, 10)
        val pairs = ledger.pairs()
        assertEquals(10, pairs.size)
        assertTrue(pairs.all { abs(it.petrolRefMs - 5.0) < 1e-9 && abs(it.gasPetrolMs - 5.5) < 1e-9 })
        val index = ledger.index()
        assertEquals(1.1, index.getDouble("ratio"), 1e-9)
        val band = index.getJSONArray("bands").getJSONObject(1)
        assertEquals(4.5, band.getDouble("fromMs"), 0.0)
        assertEquals(10, band.getInt("samples"))
        assertEquals("uma região RPM×MAP só", 1, band.getInt("cells"))
    }

    @Test
    fun `leituras novas valem na hora - cache so pela revisao nunca pelo relogio`() {
        val ledger = EquivalenceLedger(null) { 0L } // relógio parado: só a revisão pode invalidar o cache
        var t = drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, 0, 10)
        t = drive(ledger, "GNV", 2000.0, 0.60, 5.5, t + 5_000, 5)
        assertEquals(5, ledger.pairs().size)
        drive(ledger, "GNV", 2000.0, 0.60, 5.5, t, 5)
        assertEquals(10, ledger.pairs().size)
        // mesmo retrato ⇒ mesma saída
        assertEquals(ledger.pairs().map { it.petrolRefMs to it.gasPetrolMs }, ledger.pairs().map { it.petrolRefMs to it.gasPetrolMs })
    }

    @Test
    fun `janela zera ao emitir uma leitura e nao emite duas leituras sobrepostas`() {
        val ledger = EquivalenceLedger(null) { now }
        var t = 0L
        repeat(5) { ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", 2000.0, 0.60, 5.0)); t += 280 }
        assertEquals("5 quadros = 1 leitura (antes a janela deslizante dava 3)", 1, ledger.index().getInt("petrolObservations"))
        ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", 2000.0, 0.60, 5.0)); t += 280
        assertEquals(2, ledger.index().getInt("petrolObservations"))
        // GNV: 6 quadros → 2 leituras, datadas no quadro do meio de cada trio, sem quadro compartilhado.
        val gasStart = t + 5_000
        t = gasStart
        repeat(6) { ledger.accept(EquivalenceLedger.Frame(t, "GNV", 2000.0, 0.60, 5.5)); t += 280 }
        now = t
        val pairs = ledger.pairs()
        assertEquals(2, pairs.size)
        assertEquals(listOf(gasStart + 280, gasStart + 4 * 280), pairs.map { it.t })
        // Regiões distintas por faixa: outra rotação (ainda casando com a gasolina de 2000 rpm) é outra região.
        repeat(3) { ledger.accept(EquivalenceLedger.Frame(t, "GNV", 2150.0, 0.60, 5.5)); t += 280 }
        now = t + 5_000
        assertEquals(2, ledger.index().getJSONArray("bands").getJSONObject(1).getInt("cells"))
    }

    @Test
    fun `transiente e corte nao viram evidencia`() {
        val ledger = EquivalenceLedger(null) { now }
        var t = 0L
        listOf(1500.0, 1800.0, 2100.0, 2400.0).forEach { rpm -> ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", rpm, 0.5, 4.0)); t += 280 }
        ledger.accept(EquivalenceLedger.Frame(t, "CUTOFF", 2000.0, 0.2, 0.0))
        assertEquals(0, ledger.index().getInt("petrolObservations"))
    }

    // Substitui "marcha lenta fica fora do índice de condução e de todos os pares do motor" (dono, 2026-10-11: a
    // curva aprende tudo): a lenta entra nos pares, no índice por faixas e na gasolina de referência. Sem
    // condução no mesmo t não há como separar: fica na curva.
    @Test
    fun `marcha lenta entra nos pares e no indice quando nao ha como separar`() {
        val ledger = EquivalenceLedger(null) { now }
        val t = drive(ledger, "GASOLINA", 870.0, 0.35, 4.0, 0, 10)
        drive(ledger, "GNV", 870.0, 0.35, 4.6, t + 1_000, 10)
        assertEquals(10, ledger.allPairs().size)
        assertEquals(10, ledger.pairs().size)
        assertEquals(10, ledger.index().getInt("samples"))
        assertEquals(1.15, ledger.index().getDouble("ratio"), 1e-9)
        assertEquals(0, ledger.index().getJSONArray("localGroups").length())
        // Continua no gráfico (pontos densos), marcada como lenta.
        assertTrue(ledger.denseBandsJson(0.025, 1).getJSONArray("gas").getJSONObject(0).getDouble("idleShare") > 0.99)
    }

    @Test
    fun `curva gravada descarta o GNV antigo e preserva a gasolina`() {
        val ledger = EquivalenceLedger(null) { now }
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
    fun `curva alterada por fora do app descarta GNV mas a gravada pelo app nao`() {
        val ledger = EquivalenceLedger(null) { now }
        var t = drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        ledger.alignCurve("A")
        t = drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        ledger.adoptCurve("B")
        ledger.alignCurve("B")
        assertEquals(10, ledger.index().getInt("gasObservations"))
        ledger.alignCurve("C")
        assertEquals(0, ledger.index().getInt("gasObservations"))
    }

    @Test
    fun `persistencia sobrevive a reinicio do app`() {
        val dir = Files.createTempDirectory("ledger").toFile()
        val file = File(dir, "equivalence_ledger.json")
        val ledger = EquivalenceLedger(file) { now }
        val t = drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        ledger.flush()
        val reloaded = EquivalenceLedger(file) { now }
        assertEquals(ledger.pairs().size, reloaded.pairs().size)
        assertEquals(EquivalenceLedger.FORMAT, JSONObject(file.readText()).getString("format"))
        dir.deleteRecursively()
    }

    @Test
    fun `arquivo vazio ou corrompido nao zera a memoria e volta da reserva bak`() {
        val dir = Files.createTempDirectory("ledger-bak").toFile()
        val file = File(dir, "equivalence_ledger.json")
        val ledger = EquivalenceLedger(file) { now }
        drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        ledger.flush()
        drive(ledger, "GNV", 2000.0, 0.6, 5.5, now + 1_000, 10)
        ledger.flush() // segunda gravação: a anterior vira a reserva .bak
        file.writeText("") // a ignição cortou no meio: arquivo principal vazio
        val fromBackup = EquivalenceLedger(file) { now }
        assertEquals(8, fromBackup.index().getInt("petrolObservations"))
        assertTrue(fromBackup.index().isNull("loadError"))
        // Nem o arquivo nem a reserva servem: nada é zerado nem apagado; o motivo vai no índice para a tela dizer.
        file.writeText("lixo")
        File(dir, "equivalence_ledger.json.bak").writeText("lixo")
        val broken = EquivalenceLedger(file) { now }
        assertEquals(0, broken.index().getInt("petrolObservations"))
        assertTrue(broken.index().getString("loadError").isNotBlank())
        assertEquals("lixo", file.readText())
        dir.deleteRecursively()
    }

    // Ajustado na rodada 2 (revisão, item 20): o teste tinha passado a conferir allPairs(); volta a conferir o
    // caminho de produção, pairs(), contra a busca exaustiva com as mesmas regras (gasolina congelada em 8
    // leituras por região). Ajustado de novo (dono, 2026-10-11: a curva aprende tudo): todas as rotações dos dois
    // lados; sem grupo local (dado aleatório sem coluna separável), pairs() = allPairs() = busca exaustiva.
    @Test
    fun `busca em grade da o mesmo resultado da busca exaustiva`() {
        val ledger = EquivalenceLedger(null) { now }
        val rnd = java.util.Random(7)
        val petrol = ArrayList<DoubleArray>()
        var t = 0L
        repeat(1200) {
            val rpm = 900.0 + rnd.nextDouble() * 3600
            val map = 0.25 + rnd.nextDouble() * 0.7
            val ms = 2.0 + map * 10 + rnd.nextDouble()
            repeat(3) { ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", rpm, map, ms)); t += 100 }
            // Mesma regra do acumulador: a região RPM×MAP congela com 8 leituras de gasolina.
            val cell = EquivalenceLedger.cellKey(rpm, map)
            if (petrol.count { EquivalenceLedger.cellKey(it[0], it[1]) == cell } < EquivalenceLedger.PETROL_FREEZE_SAMPLES) petrol += doubleArrayOf(rpm, map, ms)
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
        now = t
        val exhaustive = gas.mapNotNull { g ->
            val m = petrol.filter { abs(it[0] - g[0]) <= 150.0 && abs(it[1] - g[1]) <= 0.02 }
                .map { it[2] }.sorted()
            if (m.size < 2) null else m[m.size / 2] to g[2]
        }
        assertEquals(0, ledger.index().getJSONArray("localGroups").length())
        for ((expected, actual) in listOf(
            exhaustive to ledger.pairs().map { it.petrolRefMs to it.gasPetrolMs },
            exhaustive to ledger.allPairs().map { it.petrolRefMs to it.gasPetrolMs },
        )) {
            assertTrue(expected.size > 50)
            assertEquals(expected.size, actual.size)
            expected.zip(actual).forEach { (e, a) ->
                assertEquals(e.first, a.first, 1e-9)
                assertEquals(e.second, a.second, 1e-9)
            }
        }
    }

    @Test
    fun `uma regiao muito visitada nao apaga as outras regioes`() {
        val ledger = EquivalenceLedger(null) { now }
        var t = drive(ledger, "GASOLINA", 3000.0, 0.80, 8.0, 0, 6)
        // marcha lenta / cruzeiro repetido por muito tempo na mesma região
        t = drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, t + 5_000, 3_000)
        val index = ledger.index()
        assertTrue(index.getInt("petrolObservations") <= 2 * EquivalenceLedger.CELL_CAP)
        val dense = ledger.denseBandsJson(0.025, 1).getJSONArray("petrol")
        assertTrue((0 until dense.length()).any { abs(dense.getJSONObject(it).getDouble("tpetMs") - 8.0) < 1e-9 })
    }

    // ------------------------------------------------------------------ refino (2026-10-11)

    // Substitui "gasolina de referência em lenta não entra nem para casar com GNV de condução" (dono, 2026-10-11:
    // a curva aprende tudo): a gasolina própria de qualquer rotação é referência no mesmo RPM±150/MAP±0,02.
    @Test
    fun `gasolina de qualquer rotacao casa com o GNV no mesmo RPM e MAP`() {
        val ledger = EquivalenceLedger(null) { now }
        val t = drive(ledger, "GASOLINA", 1250.0, 0.40, 4.2, 0, 10)
        drive(ledger, "GNV", 1400.0, 0.40, 4.6, t + 1_000, 10)
        assertEquals(10, ledger.allPairs().size)
        assertEquals(ledger.allPairs(), ledger.pairs())
    }

    @Test
    fun `gravacao invalida so o GNV dos pontos que mudaram`() {
        val ledger = EquivalenceLedger(null) { now }
        var t = drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, 0, 10)
        t = drive(ledger, "GASOLINA", 3000.0, 0.90, 9.0, t + 1_000, 10)
        t = drive(ledger, "GNV", 2000.0, 0.60, 5.5, t + 1_000, 10)
        drive(ledger, "GNV", 3000.0, 0.90, 9.6, t + 1_000, 10)
        assertEquals(20, ledger.pairs().size)
        val removed = ledger.invalidateGas("CURVA_K_GRAVADA", listOf(8.5 to 11.0))
        assertEquals(10, removed)
        assertTrue(ledger.pairs().all { it.gasPetrolMs < 6.0 })
        assertEquals(10, ledger.index().getJSONArray("bands").getJSONObject(1).getInt("samples"))
        assertEquals("CURVA_K_GRAVADA", ledger.index().getJSONObject("gasInvalidation").getString("reason"))
    }

    @Test
    fun `indice depois de um instante conta so o GNV medido depois`() {
        val ledger = EquivalenceLedger(null) { now }
        var t = drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, 0, 10)
        t = drive(ledger, "GNV", 2000.0, 0.60, 5.5, t + 1_000, 10)
        val writeAt = t + 1_000
        drive(ledger, "GNV", 2000.0, 0.60, 5.1, writeAt + 1_000, 12)
        assertEquals(12, ledger.index(writeAt).getJSONArray("bands").getJSONObject(1).getInt("samples"))
        assertEquals(1.02, ledger.index(writeAt).getJSONArray("bands").getJSONObject(1).getDouble("ratio"), 1e-9)
    }

    @Test
    fun `leitura atrasada da ECU logo depois da gravacao do app nao descarta o GNV`() {
        val ledger = EquivalenceLedger(null) { now }
        val t = drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        ledger.alignCurve("A")
        drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        ledger.adoptCurve("B")
        assertEquals(false, ledger.alignCurve("A")) // leitura velha: ainda é a curva de antes da gravação
        assertEquals(10, ledger.index().getInt("gasObservations"))
        assertEquals(false, ledger.alignCurve("B"))
        assertEquals(true, ledger.alignCurve("C"))
        assertEquals(0, ledger.index().getInt("gasObservations"))
    }

    @Test
    fun `gasolina propria congela madura e so aquisicao nova de gasolina descongela`() {
        val ledger = EquivalenceLedger(null) { now }
        drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, 0, 10) // 8 leituras estáveis: madura
        drive(ledger, "GASOLINA", 2000.0, 0.60, 6.0, 20_000, 30) // depois do AutoMatch, com USB ligado
        assertEquals(EquivalenceLedger.PETROL_FREEZE_SAMPLES, ledger.index().getInt("petrolObservations"))
        val gasStart = 60_000L
        drive(ledger, "GNV", 2000.0, 0.60, 5.5, gasStart, 10)
        assertTrue(ledger.pairs().isNotEmpty() && ledger.pairs().all { abs(it.petrolRefMs - 5.0) < 1e-9 })
        // ECU com gasolina adquirida (4 zonas) e depois "Readquirir gasolina" (zonas a zero): descongela.
        ledger.notePetrolAcquisition(4)
        ledger.notePetrolAcquisition(4)
        assertEquals(8, ledger.index().getInt("petrolObservations"))
        ledger.notePetrolAcquisition(0)
        assertEquals(0, ledger.index().getInt("petrolObservations"))
        drive(ledger, "GASOLINA", 2000.0, 0.60, 6.0, 120_000, 10)
        assertEquals(8, ledger.index().getInt("petrolObservations"))
        // Recibo de "Readquirir gasolina" (geração do NativeAutoCalAcquisitionEpoch) na mesma sessão USB também descongela.
        val epoch = ledger.index().getInt("petrolEpoch")
        assertEquals(false, ledger.notePetrolGeneration(7L, 0))
        assertEquals(true, ledger.notePetrolGeneration(7L, 1))
        assertEquals(0, ledger.index().getInt("petrolObservations"))
        assertEquals(epoch + 1, ledger.index().getInt("petrolEpoch"))
        // Sessão USB nova recomeça a contagem: não é aquisição nova.
        drive(ledger, "GASOLINA", 2000.0, 0.60, 6.0, 180_000, 10)
        assertEquals(false, ledger.notePetrolGeneration(8L, 0))
        assertEquals(8, ledger.index().getInt("petrolObservations"))
    }

    @Test
    fun `gasolina com o motor frio nunca vira referencia`() {
        val ledger = EquivalenceLedger(null) { now }
        var t = 0L
        repeat(30) { ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", 2000.0, 0.60, 5.6, 0.0, engineHot = false)); t += 280 }
        assertEquals(0, ledger.index().getInt("petrolObservations"))
        repeat(30) { ledger.accept(EquivalenceLedger.Frame(t, "GASOLINA", 2000.0, 0.60, 5.0, 0.0, engineHot = true)); t += 280 }
        assertEquals(8, ledger.index().getInt("petrolObservations"))
    }

    // Ajustado na rodada 2 (revisão, item 12; decisão do dono via líder): o porte do 00f6fd02 tinha invertido a
    // precedência (ECU primeiro). Volta à Platina: gasolina própria de condução primeiro; a curva da ECU só onde
    // não há própria (na lenta a gasolina pede ~17% a mais que na condução no mesmo MAP).
    @Test
    fun `gasolina propria vem primeiro e a curva da ECU so completa onde nao ha propria`() {
        val ledger = EquivalenceLedger(null) { now }
        // Gasolina própria diz 5,0 ms em 0,60 bar; a curva da ECU diz 5,2 ms: vale a própria.
        var t = drive(ledger, "GASOLINA", 2000.0, 0.60, 5.0, 0, 10)
        ledger.setEcuPetrolReference(listOf(0.30 to 2.8, 0.40 to 3.6, 0.50 to 4.4, 0.60 to 5.2, 0.70 to 6.0, 0.80 to 6.8))
        t = drive(ledger, "GNV", 2000.0, 0.60, 5.72, t + 1_000, 10)
        drive(ledger, "GNV", 2000.0, 0.75, 6.9, t + 1_000, 10)
        val pairs = ledger.pairs()
        val own = pairs.filter { abs(it.gasPetrolMs - 5.72) < 1e-9 }
        val ecu = pairs.filter { abs(it.gasPetrolMs - 6.9) < 1e-9 }
        assertTrue(own.isNotEmpty() && own.all { !it.ecuRef && abs(it.petrolRefMs - 5.0) < 1e-9 })
        // 0,75 bar sem gasolina própria: a curva da ECU completa (6,4 ms interpolado).
        assertTrue(ecu.isNotEmpty() && ecu.all { it.ecuRef && abs(it.petrolRefMs - 6.4) < 1e-9 })
        assertEquals("MISTA", ledger.index().getString("petrolReference"))
        // Curva curta demais da ECU é ignorada.
        ledger.setEcuPetrolReference(listOf(0.60 to 5.2, 0.62 to 5.3))
        assertTrue(ledger.pairs().none { it.ecuRef })
    }
}
