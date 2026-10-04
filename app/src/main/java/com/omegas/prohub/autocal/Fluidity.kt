package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Fluidez por combustível para a aba Diagnóstico: a gasolina deve ser LINEAR (ms de injeção cresce de forma suave com o MAP);
 * o GNV com solavancos mostra degraus e desvios da linha. Só leitura: sai das barras de MAP que o livro já agrega
 * (`denseBandsJson`: MAP × ms mediano, ≥ 5 leituras por barra), sem tocar a ECU.
 *
 * Por combustível:
 *  - `deviationPct`: desvio quadrático médio dos ms em relação à reta ms = a + b·MAP (ponderada por leituras), em % do ms médio;
 *  - `jerkPct`: variação de ms por varredura de MAP = média de |segunda diferença| dos ms nas barras vizinhas, em % do ms médio
 *    (0 numa reta; sobe com degraus/solavancos);
 *  - `msPerBar`: inclinação da reta.
 * `verdict`: SEM_DADOS (< [MIN_BINS] barras), LINEAR ou SOLAVANCOS. O GNV só é SOLAVANCOS se o solavanco passa de
 * [JERK_LIMIT_PCT] E é maior que [RELATIVE_LIMIT]× o da gasolina (quando a gasolina tem dado). Limiar de diagnóstico, não de gravação.
 * Limite conhecido: o ms também depende do RPM; a mistura de RPMs numa barra de MAP entra como dispersão. Não provado no carro.
 */
object Fluidity {
    const val MIN_BINS = 6
    const val JERK_LIMIT_PCT = 4.0
    const val RELATIVE_LIMIT = 1.5

    private class Lane(val n: Int, val bins: Int, val deviation: Double?, val jerk: Double?, val slope: Double?)

    private fun analyse(array: JSONArray?): Lane {
        val bins = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
            .filter { it.optDouble("mapBar", Double.NaN).isFinite() && it.optDouble("tpetMs", Double.NaN).isFinite() }
            .sortedBy { it.optDouble("mapBar") }
        val n = bins.sumOf { it.optInt("samples") }
        if (bins.size < MIN_BINS) return Lane(n, bins.size, null, null, null)
        val x = bins.map { it.optDouble("mapBar") }
        val y = bins.map { it.optDouble("tpetMs") }
        val w = bins.map { it.optInt("samples").coerceAtLeast(1).toDouble() }
        val sw = w.sum()
        val mx = x.indices.sumOf { w[it] * x[it] } / sw
        val my = y.indices.sumOf { w[it] * y[it] } / sw
        val sxx = x.indices.sumOf { w[it] * (x[it] - mx) * (x[it] - mx) }
        val b = if (sxx <= 1e-12) 0.0 else x.indices.sumOf { w[it] * (x[it] - mx) * (y[it] - my) } / sxx
        val a = my - b * mx
        val dev = sqrt(x.indices.sumOf { w[it] * (y[it] - (a + b * x[it])).let { r -> r * r } } / sw) / my * 100.0
        val jerk = (1 until y.size - 1).map { abs(y[it + 1] - 2.0 * y[it] + y[it - 1]) }.average() / my * 100.0
        return Lane(n, bins.size, dev, jerk, b)
    }

    private fun r1(v: Double?): Any = if (v == null || !v.isFinite()) JSONObject.NULL else Math.round(v * 10.0) / 10.0

    fun fromDense(dense: JSONObject?): JSONObject {
        val petrol = analyse(dense?.optJSONArray("petrol"))
        val gas = analyse(dense?.optJSONArray("gas"))
        fun verdict(lane: Lane, other: Lane?): String {
            val jerk = lane.jerk ?: return "SEM_DADOS"
            val limited = jerk > JERK_LIMIT_PCT
            val vs = other?.jerk
            return if (limited && (vs == null || jerk > RELATIVE_LIMIT * vs)) "SOLAVANCOS" else "LINEAR"
        }
        val petrolVerdict = verdict(petrol, null)
        val gasVerdict = verdict(gas, petrol)
        fun lane(l: Lane, v: String, label: String) = JSONObject()
            .put("samples", l.n).put("bins", l.bins)
            // `index` 0..1 = linearidade (1 = reta; 0 = solavancos ≥ 2× o limite); `jerks` = jerkPct (nomes que a aba Diagnóstico lê).
            .put("index", l.jerk?.let { Math.round((1.0 - minOf(1.0, it / (2.0 * JERK_LIMIT_PCT))) * 100.0) / 100.0 } ?: JSONObject.NULL)
            .put("jerks", r1(l.jerk))
            .put("deviationPct", r1(l.deviation)).put("jerkPct", r1(l.jerk)).put("msPerBar", r1(l.slope))
            .put("verdict", v)
            .put("text", when (v) {
                "SEM_DADOS" -> "$label: ainda sem leituras suficientes em cargas diferentes."
                "LINEAR" -> "$label: resposta linear e suave."
                else -> "$label: resposta com solavancos (degraus na injeção ao subir a carga)."
            })
        val summary = when {
            gasVerdict == "SOLAVANCOS" && petrolVerdict == "LINEAR" -> "A gasolina é linear e o GNV tem solavancos: a Curva K tem degraus a suavizar."
            gasVerdict == "SOLAVANCOS" -> "O GNV tem solavancos; ainda não há gasolina linear medida para comparar."
            gasVerdict == "LINEAR" && petrolVerdict == "LINEAR" -> "Gasolina e GNV respondem de forma linear."
            else -> "Ainda coletando para comparar a fluidez da gasolina e do GNV."
        }
        val petrolJson = lane(petrol, petrolVerdict, "Gasolina")
        val gasJson = lane(gas, gasVerdict, "GNV")
        // `gasolina`/`gnv` são apelidos de `petrol`/`gas` (a UI de Diagnóstico lê os nomes em português).
        return JSONObject().put("petrol", petrolJson).put("gas", gasJson)
            .put("gasolina", JSONObject(petrolJson.toString())).put("gnv", JSONObject(gasJson.toString()))
            .put("summary", summary)
            .put("criteria", JSONObject().put("minBins", MIN_BINS).put("jerkLimitPct", JERK_LIMIT_PCT).put("relativeLimit", RELATIVE_LIMIT))
    }
}
