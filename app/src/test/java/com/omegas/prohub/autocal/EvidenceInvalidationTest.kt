package com.omegas.prohub.autocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceInvalidationTest {
    private fun drive(ledger: EquivalenceLedger, fuel: String, ms: Double, start: Long, n: Int, gasMs: Double = 0.0): Long {
        var t = start
        repeat(n) { ledger.accept(EquivalenceLedger.Frame(t, fuel, 2000.0, 0.6, ms, gasMs)); t += 280 }
        return t
    }

    @Test
    fun `gravador que lanca nao deixa a evidencia antiga valida`() {
        val ledger = EquivalenceLedger(null)
        val t = drive(ledger, "GASOLINA", 5.0, 0, 10)
        drive(ledger, "GNV", 5.5, t + 1_000, 10, gasMs = 12.0)
        assertTrue(ledger.gasPerAir() != null)
        val warnings = ArrayList<String>()
        EvidenceInvalidation.run(
            invalidate = listOf(
                "resetGas" to { ledger.resetGas("MAPA_K_GRAVADO") },
                "falha" to { error("boom") },
            ),
            record = { throw java.io.IOException("disco cheio") },
            warn = { warnings += it },
        )
        assertTrue(ledger.gasPerAir() == null)
        assertEquals(2, warnings.size)
    }

    @Test
    fun `invalidacao roda antes do gravador`() {
        val order = ArrayList<String>()
        EvidenceInvalidation.run(listOf("a" to { order += "a" }), { order += "rec" }, { })
        assertEquals(listOf("a", "rec"), order)
    }
}
