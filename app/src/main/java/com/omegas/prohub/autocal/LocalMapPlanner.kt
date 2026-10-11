package com.omegas.prohub.autocal

import com.omegas.prohub.calibration.KMapPhysicalAxes
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Proposta de ajuste local no Mapa K a partir dos grupos que [EquivalenceAttribution] separou (o que a curva não
 * explica naquela coluna de RPM). Células: a coluna do grupo × as linhas (Petrol Inj.) que cobrem
 * [t_gasolina, t_GNV] do grupo (a ECU passa por esse caminho ao desfazer a compensação). Valor: a mediana do
 * resíduo do grupo (grupo − outras colunas no mesmo t) em %, no máximo ±[MAX_STEP_PERCENT] por gravação.
 *
 * Puro e só candidato: nunca grava nem fala com a ECU. O alvo exato sai do mapa lido da ECU na tela do Mapa K,
 * com revisão, conferência, ACK e readback do fluxo manual.
 */
object LocalMapPlanner {
    /** No máximo ±15% por gravação (o mesmo passo máximo da curva, AutoMatchRefinedEngine.MAX_STEP_LOG). */
    const val MAX_STEP_PERCENT = 15.0

    private fun round1(value: Double): Double = (value * 10.0).roundToLong() / 10.0

    /** Linhas do Mapa K que cobrem [loMs, hiMs] (a linha abaixo de lo até a linha acima de hi). */
    fun rowsCovering(loMs: Double, hiMs: Double): List<Int> {
        val bins = KMapPhysicalAxes.petrolBins()
        val lo = min(loMs, hiMs)
        val hi = max(loMs, hiMs)
        val first = bins.indexOfLast { it <= lo }.coerceAtLeast(0)
        val last = bins.indexOfFirst { it >= hi }.let { if (it < 0) bins.lastIndex else it }
        return (first..maxOf(first, last)).toList()
    }

    /** Um item por grupo local: onde (coluna × linhas), quanto (%) e a evidência. */
    fun plan(groups: List<EquivalenceAttribution.Group>): JSONObject {
        val out = JSONArray()
        groups.filter { it.local }.forEach { g ->
            val residual = g.residualLog ?: return@forEach
            val raw = (exp(residual) - 1.0) * 100.0
            val delta = round1(raw.coerceIn(-MAX_STEP_PERCENT, MAX_STEP_PERCENT))
            val rows = rowsCovering(g.petrolMs, g.gasMs)
            out.put(g.json()
                .put("deltaPercent", delta)
                .put("limited", abs(raw) > MAX_STEP_PERCENT)
                .put("rows", JSONArray(rows))
                .put("cells", JSONArray(rows.map { JSONObject().put("row", it).put("column", g.key.column).put("deltaPercent", delta) })))
        }
        return JSONObject().put("groups", out).put("automatic", false).put("manualOnly", true)
    }

    /** Junta as células dos grupos mantidos; célula pedida por dois grupos fica com a média ponderada pelos pares. */
    fun cells(groups: List<JSONObject>): JSONArray {
        val sum = LinkedHashMap<Pair<Int, Int>, DoubleArray>()
        groups.forEach { g ->
            val weight = g.optInt("pairs", 1).coerceAtLeast(1).toDouble()
            val cells = g.optJSONArray("cells") ?: return@forEach
            for (i in 0 until cells.length()) {
                val c = cells.optJSONObject(i) ?: continue
                val d = c.optDouble("deltaPercent", Double.NaN)
                if (!d.isFinite()) continue
                val acc = sum.getOrPut(c.optInt("row") to c.optInt("column")) { DoubleArray(2) }
                acc[0] += d * weight
                acc[1] += weight
            }
        }
        return JSONArray(sum.entries.sortedWith(compareBy({ it.key.second }, { it.key.first })).map { (cell, acc) ->
            JSONObject().put("row", cell.first).put("column", cell.second).put("deltaPercent", round1(acc[0] / acc[1]))
        })
    }
}
