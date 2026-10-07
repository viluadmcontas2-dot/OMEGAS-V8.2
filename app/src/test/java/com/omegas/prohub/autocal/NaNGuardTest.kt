package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.EquivalencePoint
import com.omegas.prohub.equivalence.PointState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Achado 10: NaN/infinito nunca entra no livro nem nos bins finos. */
class NaNGuardTest {
    private var now = 0L

    @Test
    fun `quadro com NaN nunca vira leitura do livro`() {
        val ledger = EquivalenceLedger(null) { now }
        var t = 0L
        for (bad in listOf(
            EquivalenceLedger.Frame(0, "GASOLINA", 2_000.0, Double.NaN, 5.0),
            EquivalenceLedger.Frame(0, "GASOLINA", Double.NaN, 0.5, 5.0),
            EquivalenceLedger.Frame(0, "GASOLINA", 2_000.0, 0.5, Double.NaN),
            EquivalenceLedger.Frame(0, "GASOLINA", 2_000.0, Double.POSITIVE_INFINITY, 5.0),
        )) repeat(3) { ledger.accept(bad.copy(t = t)); t += 100 }
        assertEquals(0, ledger.petrolObservations().size)
    }

    @Test
    fun `par com GNV NaN ou infinito nao entra nos bins finos`() {
        val pairs = listOf(
            EquivalenceLedger.EvidencePair(5.0, 5.0, 2_000.0, episode = 1),
            EquivalenceLedger.EvidencePair(5.0, Double.NaN, 2_000.0, episode = 1),
            EquivalenceLedger.EvidencePair(5.0, Double.POSITIVE_INFINITY, 2_000.0, episode = 1),
        )
        val bin = FineBins.aggregate(pairs).first { it.n > 0 }
        assertEquals(1, bin.n)
        assertTrue(bin.medianLn!!.isFinite())
    }
}
