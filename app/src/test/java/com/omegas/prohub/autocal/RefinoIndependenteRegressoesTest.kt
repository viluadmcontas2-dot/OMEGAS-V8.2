package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Contrato do dono: AutoCal só libera a gravação; o Refino mede pontos próprios
 * independentemente da cobertura nativa, inclusive acima de 12/13 ms.
 */
class RefinoIndependenteRegressoesTest {
    @Test fun `aprendizado local aceita regioes antes de 3 e alem de 22 ms`() {
        val ledger = EquivalenceLedger()
        var t = 10_000L
        for ((ms, rpm, map) in listOf(1.5 to 1400.0 to 0.42, 13.5 to 1800.0 to 0.63, 22.5 to 2300.0 to 0.89)
            .map { Triple(it.first.first, it.first.second, it.second) }) {
            for (fuel in listOf("GASOLINA", "GNV")) {
                repeat(15) {
                    ledger.accept(EquivalenceLedger.Frame(
                        t = t, fuel = fuel, rpm = rpm, map = map,
                        petrolMs = if (fuel == "GNV") ms * 1.06 else ms,
                        gasMs = if (fuel == "GNV") ms * 2.1 else 0.0,
                    ))
                    t += 350
                }
                t += 4_000
            }
        }
        val list = ledger.betweenPointsJson()
        val measured = (0 until list.length()).map { list.getJSONObject(it) }
        assertEquals(3, measured.size)
        assertTrue(measured.all { it.getString("kind") == "local" })
        for (ms in listOf(1.5, 13.5, 22.5)) {
            assertTrue("Região real de $ms ms precisa aparecer", measured.any {
                ms >= it.getDouble("fromMs") && ms < it.getDouble("toMs")
            })
        }
        assertTrue(measured.all { it.getInt("samples") >= 3 })
    }

    private fun action() = JSONObject().put("available", true)
        .put("index", JSONObject.NULL)
        .put("nextAction", JSONObject().put("kind", "APPLY")
            .put("pointIndexes", JSONArray().put(11)))

    @Test fun `uma regiao independente pode liberar proposta apos AutoCal 3 de 3 ativo`() {
        val single = JSONArray().put(JSONObject()
            .put("kind", "local").put("state", "coletado")
            .put("fromMs", 13.25).put("toMs", 13.5).put("samples", 20))
        val done = JSONObject().put("phase", "COLETANDO_NOSSOS")
            .put("autoMatchCount", 3).put("maxAutomatch", 3)
            .put("autoCalEnabled", 1).put("ecuDone", true)
        val result = RefinoState.build(done, action(), single, null, "GNV")
        assertTrue(result.getBoolean("canAct"))
        assertEquals(1, result.getJSONObject("counts").getInt("regionsConfirmed"))
        assertEquals(0, result.getJSONObject("counts").getInt("regionsLearning"))
        for (denied in listOf(
            JSONObject(done.toString()).put("ecuDone", false).put("autoMatchCount", 1),
            JSONObject(done.toString()).put("autoCalEnabled", 0)
        )) {
            assertFalse(RefinoState.build(denied, action(), single, null, "GNV").getBoolean("canAct"))
        }
    }

    @Test fun `uma unica faixa propria com pares suficientes habilita calculo sem nativa`() {
        val axis = IntArray(30) { (1.0 + it * 0.8).times(512).toInt() }
        val result = AutoMatchRefinedEngine.refine(AutoMatchRefinedEngine.Input(
            axisRaw = axis, mulActRaw = IntArray(30) { 16384 },
            petrolTimeRaw = null, petrolMapRaw = null, petrolCounts = null,
            gasTimeRaw = null, gasMapRaw = null, gasCounts = null,
            telemetryPairs = List(12) { (13.23 + it * 0.007) to (13.23 + it * 0.007) * 1.07 }
        ))
        assertEquals(AutoMatchRefinedEngine.Mode.EQUIVALENCE, result.mode)
        assertTrue(result.telemetryOnly)
        assertTrue(result.telemetryPairsUsed > 0)
    }
}
