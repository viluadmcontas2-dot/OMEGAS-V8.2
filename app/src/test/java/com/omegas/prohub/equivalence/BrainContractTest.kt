package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.autocal.RealSessionReplaySupport.REFERENCE
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CONTRATO DE FORMA (Kotlin serializa → JS lê): `index` é número escalar 0..1 ou null; `provisional`, `coverage` e
 * `judgedUsage` são irmãos planos de `index`. O JS lê o JSON que sai de [EquivalenceJson.result] depois de serializar
 * e reler como texto (o que a ponte entrega), então o teste faz o mesmo caminho.
 */
class BrainContractTest {
    private val axisRaw: IntArray = EquivalenceReplaySupport.curve(REFERENCE, 95).first
    private val flatK = IntArray(30) { 16384 }
    private val reference = Reference("REF", 1_000L, "fp", (2..10).map { RefPoint(0.1 * it, 10.0 * 0.1 * it, 10) })

    private fun obs(readings: Int): List<EquivalenceLedger.Obs> {
        val out = ArrayList<EquivalenceLedger.Obs>()
        for (cell in 5 until 45) {
            val c = OwnCurveFitter.center(cell)
            repeat(readings) { i -> out += EquivalenceLedger.Obs(i * 70_000L + cell, 2000.0, c, 10.0 * c) }
        }
        return out
    }

    private fun usage() = UsageMeter.Reading(DoubleArray(OwnCurveFitter.GRID_CELLS) { if (it in 5 until 45) 1.0 else 0.0 })

    private fun serialize(result: EquivalenceResult?): JSONObject =
        JSONObject(EquivalenceJson.result(result, reference, null, null, true, nowMs = 5_000L).toString())

    private fun evaluate(gasReadings: Int) = EquivalenceEngine.evaluate(
        EquivalenceInput(axisRaw, flatK, reference, null, obs(20), obs(gasReadings).mapIndexed { i, o ->
            // Leituras independentes com ruído pequeno; MAP normalizado já não fabrica dispersão.
            o.copy(petrolMs = o.petrolMs * (1.0 + 0.006 * kotlin.math.sin(i * 1.7)))
        }, ExperienceMeter(null).reading(), usage()),
    )

    private fun assertShape(json: JSONObject) {
        // index: número escalar 0..1 ou null, nunca objeto
        assertTrue("index existe", json.has("index"))
        val index = json.get("index")
        assertFalse("index não é objeto: $index", index is JSONObject)
        if (!json.isNull("index")) {
            assertTrue("index é número: ${index.javaClass}", index is Number)
            assertTrue(json.getDouble("index") in 0.0..1.0)
        }
        // irmãos planos
        assertTrue("provisional é booleano no mesmo nível", json.get("provisional") is Boolean)
        assertTrue("coverage é inteiro no mesmo nível", json.get("coverage") is Int)
        assertTrue("judgedUsage é número no mesmo nível", json.get("judgedUsage") is Number)
        assertFalse(json.optJSONObject("index")?.has("provisional") ?: false)
        assertFalse(json.optJSONObject("index")?.has("coverage") ?: false)
    }

    @Test
    fun `com evidencia o indice e um numero escalar e provisional e coverage sao irmaos`() {
        val json = serialize(evaluate(gasReadings = 20))
        assertShape(json)
        assertEquals(1.0, json.getDouble("index"), 1e-9)
        assertEquals(false, json.getBoolean("provisional"))
        assertTrue(json.getInt("coverage") > 0)
    }

    @Test
    fun `sem evidencia o indice e null e nunca zero`() {
        val json = serialize(evaluate(gasReadings = 2))
        assertShape(json)
        assertTrue("sem evidência é null ('—'), não 0", json.isNull("index"))
        assertEquals(0, json.getInt("coverage"))
    }

    @Test
    fun `curva nao lida mantem a mesma forma`() {
        val json = serialize(null)
        assertFalse(json.getBoolean("available"))
        assertShape(json)
        assertTrue(json.isNull("index"))
        assertEquals(0, json.getInt("coverage"))
    }

    @Test
    fun `referencia registra idade e deriva e nunca 0 no lugar de desconhecida`() {
        val ref = serialize(evaluate(20)).getJSONObject("reference")
        assertEquals(4_000L, ref.getLong("ageMs"))
        assertFalse(ref.getBoolean("stale"))
        val none = JSONObject(EquivalenceJson.result(null, null, null, null, false, nowMs = 5_000L).toString()).getJSONObject("reference")
        assertTrue(none.isNull("ageMs"))
        val drifted = JSONObject(EquivalenceJson.result(null, reference, 0.20, null, false, nowMs = 5_000L).toString()).getJSONObject("reference")
        assertTrue(drifted.getBoolean("stale"))
    }
}
