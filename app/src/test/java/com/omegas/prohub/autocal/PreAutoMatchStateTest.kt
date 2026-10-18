package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Evidência que faltava (LANDI 17: "condição de disparo do AutoMatch: DESCONHECIDA"): as leituras do corpus ficam de 15 a 217 s
 * longe do AutoMatch. O app guarda a última leitura boa e a grava na sessão no instante em que a ECU muda a contagem.
 */
class PreAutoMatchStateTest {
    private fun field(key: String, values: List<Int>, capturedAtMs: Long = 1_000L, status: String = "VALID") = JSONObject()
        .put("key", key).put("status", status).put("capturedAtMs", capturedAtMs).put("rawValues", JSONArray(values))

    private fun snapshot(vararg fields: JSONObject, available: Boolean = true) = JSONObject()
        .put("available", available).put("fields", JSONArray(fields.toList()))

    // Sessão 053 do corpus: 9 bandas cheias e zonas ainda zeradas quando o AutoMatch veio.
    private val counters053 = listOf(0, 0, 10, 10, 10, 10, 10, 10, 10, 10, 6, 5, 10, 0, 0, 0, 0, 0)

    @Test fun `resume as bandas do GNV no instante do AutoMatch`() {
        val state = PreAutoMatchState.capture(
            snapshot(
                field("NUM_BUF_UPD_GAS", counters053),
                field("ACQUIRED_ZONES_GAS", listOf(0, 0, 0, 0)),
                field("NUM_AUTOMATCH_EXECUTED", listOf(0)),
            ),
            countAfter = 1,
            nowMs = 4_000L,
        )
        assertNotNull(state)
        state!!
        assertEquals(1, state.getInt("countAfter"))
        assertEquals(0, state.getInt("countBefore"))
        assertEquals(11, state.getInt("gasBandsAtLeast1"))
        assertEquals(11, state.getInt("gasBandsAtLeast3"))
        assertEquals(9, state.getInt("gasBandsFull"))
        assertEquals(101, state.getInt("gasCounterSum"))
        assertEquals(0, state.getInt("gasZonesAcquired"))
        assertEquals(3_000L, state.getLong("counterAgeMs"))
    }

    @Test fun `conta zonas adquiridas e guarda os vetores crus`() {
        val state = PreAutoMatchState.capture(
            snapshot(
                field("NUM_BUF_UPD_GAS", listOf(10, 3)),
                field("ACQUIRED_ZONES_GAS", listOf(1, 0, 1, 1)),
                field("MUL_ACT", listOf(16384, 16384)),
            ),
            countAfter = 2,
            nowMs = 1_500L,
        )!!
        assertEquals(3, state.getInt("gasZonesAcquired"))
        assertEquals(listOf(10, 3), (0 until state.getJSONArray("gasCounters").length()).map { state.getJSONArray("gasCounters").getInt(it) })
        assertEquals(2, state.getJSONArray("mulActRaw").length())
    }

    @Test fun `sem snapshot disponivel nao inventa estado`() {
        assertNull(PreAutoMatchState.capture(snapshot(available = false), 1, 1_000L))
    }

    @Test fun `contadores invalidos nao viram zero`() {
        val state = PreAutoMatchState.capture(
            snapshot(field("NUM_BUF_UPD_GAS", counters053, status = "ERROR")),
            countAfter = 1,
            nowMs = 2_000L,
        )
        assertNull(state)
    }

    @Test fun `contagem anterior desconhecida fica nula e nao zero`() {
        val state = PreAutoMatchState.capture(snapshot(field("NUM_BUF_UPD_GAS", counters053)), 3, 2_000L)!!
        assertEquals(true, state.isNull("countBefore"))
    }
}
