package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Abaixo de 1200 rpm a ECU está no regime de lenta (mesmo MAP dá +20–30% de ms nas sessões reais): um par
 * GNV × gasolina nunca pode juntar uma leitura de cada lado desse limite.
 */
class EvidencePairsRegimeTest {
    private fun obs(t: Long, rpm: Double, map: Double, ms: Double) = EquivalenceLedger.Obs(t, rpm, map, ms)

    @Test
    fun `limite de conducao e 1200 rpm`() {
        assertEquals(1_200.0, EquivalenceLedger.DRIVING_MIN_RPM, 0.0)
        assertEquals(EquivalenceLedger.DRIVING_MIN_RPM, EvidencePairs.REGIME_SPLIT_RPM, 0.0)
    }

    @Test
    fun `gasolina em lenta nao casa com GNV andando no mesmo MAP`() {
        // Gasolina a 1150 rpm (lenta, ms inflado) e GNV a 1250 rpm: dentro de ±150 rpm, mas regimes diferentes.
        val petrol = listOf(obs(1_000, 1_150.0, 0.40, 4.9), obs(2_000, 1_160.0, 0.40, 5.0))
        val gas = listOf(obs(3_000, 1_250.0, 0.40, 3.9))
        assertTrue(EvidencePairs.build(petrol, gas, emptyList()).isEmpty())
    }

    @Test
    fun `mesmo lado do limite continua casando`() {
        val petrol = listOf(obs(1_000, 1_300.0, 0.40, 3.8), obs(2_000, 1_320.0, 0.40, 3.9))
        val gas = listOf(obs(3_000, 1_250.0, 0.40, 3.9))
        val pairs = EvidencePairs.build(petrol, gas, emptyList())
        assertEquals(1, pairs.size)
        assertEquals(3.9, pairs[0].petrolRefMs, 1e-9)
        val idlePetrol = listOf(obs(1_000, 900.0, 0.40, 4.9), obs(2_000, 950.0, 0.40, 5.0))
        val idleGas = listOf(obs(3_000, 1_050.0, 0.40, 5.1))
        assertEquals(1, EvidencePairs.build(idlePetrol, idleGas, emptyList()).size)
    }
}
