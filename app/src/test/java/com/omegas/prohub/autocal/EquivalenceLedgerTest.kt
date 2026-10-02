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
    fun `curva alterada por fora do app descarta GNV mas a gravada pelo app nao`() {
        val ledger = EquivalenceLedger(null)
        var t = drive(ledger, "GASOLINA", 2000.0, 0.6, 5.0, 0, 10)
        ledger.alignCurve("A")
        t = drive(ledger, "GNV", 2000.0, 0.6, 5.5, t + 1_000, 10)
        ledger.adoptCurve("B")
        ledger.alignCurve("B")
        assertEquals(8, ledger.index().getInt("gasObservations"))
        ledger.alignCurve("C")
        assertEquals(0, ledger.index().getInt("gasObservations"))
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
}
