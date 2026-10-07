package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.EquivalencePoint
import com.omegas.prohub.equivalence.PointState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Achado 9: leitura velha da Curva K decidida pelo instante do snapshot, não por relógio fixo de 20 s. */
class LedgerEpochCaptureTest {
    private var now = 0L

    @Test
    fun `leitura velha da curva e decidida pelo instante do snapshot e nao por relogio fixo`() {
        val ledger = EquivalenceLedger(null) { now }
        now = 50_000L
        ledger.alignCurve("A", capturedAtMs = 49_000L)
        now = 100_000L
        ledger.adoptCurve("B") // o app gravou a curva B agora
        // Snapshot lido ANTES da gravação, entregue depois (mesmo 3 min depois): leitura velha, não mudança externa.
        now = 280_000L
        assertFalse(ledger.alignCurve("A", capturedAtMs = 95_000L))
        // Snapshot lido DEPOIS da gravação dizendo A (2 s depois): a curva mudou por fora de verdade, o GNV sai já.
        now = 104_000L
        ledger.accept(EquivalenceLedger.Frame(103_000L, "GNV", 2_000.0, 0.5, 5.0))
        assertTrue(ledger.alignCurve("A", capturedAtMs = 102_000L))
    }
}
