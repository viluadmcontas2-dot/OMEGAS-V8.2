package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Auditoria USB→tela: pareamento por temperatura da água (F14), validação de combustível por dynamic_correction
 * (raw19), janela estável pelo relógio de captura e limiar único de regime.
 */
class ThermalFuelGateTest {
    private fun obs(t: Long, rpm: Double, map: Double, ms: Double, water: Double = Double.NaN) =
        EquivalenceLedger.Obs(t, rpm, map, ms, waterC = water)

    private fun frame(
        t: Long, fuel: String, ms: Double, water: Double = Double.NaN, raw19: Int = -1, captured: Long = -1L,
        rpm: Double = 2000.0, map: Double = 0.6,
    ) = EquivalenceLedger.Frame(t, fuel, rpm, map, ms, 0.0, water, raw19, captured)

    // ---------------------------------------------------------------- 1. temperatura da água

    @Test
    fun `gasolina fria nao casa com GNV quente - diferenca de agua acima de 8 graus`() {
        val petrol = listOf(obs(1_000, 2000.0, 0.60, 5.0, 40.0), obs(2_000, 2000.0, 0.60, 5.0, 42.0))
        val gas = listOf(obs(3_000, 2000.0, 0.60, 6.0, 78.0))
        assertTrue(EvidencePairs.build(petrol, gas, emptyList()).isEmpty())
    }

    @Test
    fun `agua dentro de 8 graus casa - e no limite exato ainda vale`() {
        assertEquals(8.0, EvidencePairs.MAX_WATER_DELTA_C, 0.0)
        val petrol = listOf(obs(1_000, 2000.0, 0.60, 5.0, 70.0), obs(2_000, 2000.0, 0.60, 5.0, 70.0))
        assertEquals(1, EvidencePairs.build(petrol, listOf(obs(3_000, 2000.0, 0.60, 6.0, 78.0)), emptyList()).size)
        assertTrue(EvidencePairs.build(petrol, listOf(obs(3_000, 2000.0, 0.60, 6.0, 78.1)), emptyList()).isEmpty())
    }

    @Test
    fun `agua desconhecida nao derruba evidencia antiga`() {
        val petrol = listOf(obs(1_000, 2000.0, 0.60, 5.0), obs(2_000, 2000.0, 0.60, 5.0, 40.0))
        assertEquals(1, EvidencePairs.build(petrol + obs(2_500, 2000.0, 0.60, 5.0), listOf(obs(3_000, 2000.0, 0.60, 6.0, 80.0)), emptyList()).size)
    }

    @Test
    fun `leitura estavel guarda a media da agua dos tres quadros e o arquivo preserva`() {
        val file = Files.createTempFile("ledger", ".json").toFile().also { it.deleteOnExit() }
        val ledger = EquivalenceLedger(file)
        var t = 0L
        listOf(60.0, 62.0, 64.0).forEach { w -> ledger.accept(frame(t, "GASOLINA", 5.0, w)); t += 280 }
        val o = ledger.petrolObservations().single()
        assertEquals(62.0, o.waterC, 1e-9)
        ledger.flush()
        assertEquals(62.0, EquivalenceLedger(file).petrolObservations().single().waterC, 0.05)
        // Quadro sem água: leitura com água desconhecida (NaN), gravada e relida como desconhecida.
        val ledger2 = EquivalenceLedger(file)
        repeat(3) { ledger2.accept(frame(t, "GNV", 5.0)); t += 280 }
        ledger2.flush()
        assertTrue(EquivalenceLedger(file).gasObservations().single().waterC.isNaN())
    }

    @Test
    fun `par do livro junta so agua compativel`() {
        val ledger = EquivalenceLedger(null)
        var t = 0L
        repeat(8) { ledger.accept(frame(t, "GASOLINA", 5.0, 40.0)); t += 280 }
        t += 5_000
        repeat(8) { ledger.accept(frame(t, "GNV", 6.0, 80.0)); t += 280 }
        assertTrue(ledger.pairs().isEmpty())
        t += 5_000
        repeat(8) { ledger.accept(frame(t, "GNV", 6.0, 46.0)); t += 280 }
        assertTrue(ledger.pairs().isNotEmpty())
    }

    // ---------------------------------------------------------------- 2. dynamic_correction (raw19)

    @Test
    fun `quadro GASOLINA com raw19 diferente de zero e descartado`() {
        val ledger = EquivalenceLedger(null)
        var t = 0L
        repeat(6) { ledger.accept(frame(t, "GASOLINA", 5.0, raw19 = 223)); t += 280 }
        assertEquals(0, ledger.petrolObservations().size)
    }

    @Test
    fun `quadro GNV com raw19 zero e descartado`() {
        val ledger = EquivalenceLedger(null)
        var t = 0L
        repeat(6) { ledger.accept(frame(t, "GNV", 5.0, raw19 = 0)); t += 280 }
        assertEquals(0, ledger.gasObservations().size)
    }

    @Test
    fun `raw19 coerente ou desconhecido passa`() {
        val ledger = EquivalenceLedger(null)
        var t = 0L
        repeat(4) { ledger.accept(frame(t, "GASOLINA", 5.0, raw19 = 0)); t += 280 }
        repeat(4) { ledger.accept(frame(t, "GNV", 5.0, raw19 = 220)); t += 280 }
        repeat(4) { ledger.accept(frame(t, "GNV", 5.0, raw19 = -1)); t += 280 }
        assertTrue(ledger.petrolObservations().isNotEmpty())
        assertTrue(ledger.gasObservations().size >= 2)
    }

    // ---------------------------------------------------------------- 3. relógio de captura

    @Test
    fun `quadros entregues em rajada mas capturados espacados nao formam janela estavel`() {
        val ledger = EquivalenceLedger(null)
        // Relógio de parede: 3 quadros em 26 ms (fila latest-only entregando junto); captura real: 700 ms entre quadros.
        listOf(0L, 13L, 26L).forEachIndexed { i, wall -> ledger.accept(frame(wall, "GASOLINA", 5.0, captured = 10_000L + i * 700L)) }
        assertEquals(0, ledger.petrolObservations().size)
    }

    @Test
    fun `quadros capturados juntos formam janela mesmo com entrega espalhada`() {
        val ledger = EquivalenceLedger(null)
        listOf(0L, 700L, 1_400L).forEachIndexed { i, wall -> ledger.accept(frame(wall, "GASOLINA", 5.0, captured = 10_000L + i * 280L)) }
        assertEquals(1, ledger.petrolObservations().size)
    }

    @Test
    fun `sem relogio de captura vale o relogio de parede como antes`() {
        val ledger = EquivalenceLedger(null)
        listOf(0L, 700L, 1_400L).forEach { wall -> ledger.accept(frame(wall, "GASOLINA", 5.0)) }
        assertEquals(0, ledger.petrolObservations().size)
    }
}
