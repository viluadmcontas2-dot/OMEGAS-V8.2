package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/**
 * A ECU é a verdade sobre a aquisição do AutoMatch. Ela guarda as bandas, as zonas e o contador e entrega tudo ao
 * conectar; o que o app fez ou deixou de fazer não entra aqui. Só observa (nada grava, nada vai para a ECU).
 *
 * Entrada: o que [AutoCalAcquisition.fromSnapshot] já leu (18 bandas por combustível, cada uma com contador e limiar
 * de maturidade que a própria ECU informa) e o contador/máximo/ligado do AutoCal nativo.
 *
 * Regras (uma só definição, usada pelo piloto, pela projeção do AutoCal e pela ponte):
 *  - Banda **madura** = a ECU a deu como adquirida (`ZONA_ADQUIRIDA`) ou o contador dela já alcançou o limiar de
 *    maturidade da própria ECU (contador ≥ limiar > 0). Limiar desconhecido nunca vira "madura".
 *  - Zona **coberta** = a ECU marcou a zona (flag 1) ou a maioria das bandas da zona (≥ metade, arredondado para cima)
 *    está madura. A flag da ECU é zerada a cada AutoMatch executado e a leitura das bandas continua válida: as
 *    bandas lidas valem tanto quanto a flag. Nunca se inventa zona: sem leitura da ECU tudo é desconhecido (`null`).
 *  - Só **falta** o que a ECU ainda não tem: zona nem marcada nem coberta pelas bandas. E, se a ECU já entregou
 *    AutoMatch (contador ≥ 1) ou as 4 zonas dos dois combustíveis, nada "falta adquirir": o que sobra é o ciclo
 *    seguinte dela, que a ECU decide sozinha.
 */
object EcuAcquisitionTruth {
    const val ZONES = 4
    const val BANDS = 18
    private val ZONE_BANDS = intArrayOf(6, 4, 4, 4)
    private val ZONE_NAMES = arrayOf("1", "2", "3", "4")

    /** Uma banda "tem dado real" de acquisition. */
    private fun mature(point: JSONObject): Boolean {
        if (point.optString("state") == "ZONA_ADQUIRIDA" || point.optString("state") == "VALIDO") return true
        if (point.isNull("counter") || point.isNull("threshold")) return false
        val counter = point.optInt("counter", -1)
        val threshold = point.optInt("threshold", -1)
        return threshold > 0 && counter >= threshold
    }

    private fun active(point: JSONObject): Boolean =
        point.optString("state") in setOf("ZONA_ADQUIRIDA", "ATIVIDADE", "VALIDO", "COLETANDO")

    /** Flags de zona lidas da ECU (4 booleanos) ou nulo se o campo não veio. */
    private fun flags(acquisition: JSONObject, key: String, points: List<JSONObject>): List<Boolean>? {
        acquisition.optJSONObject("zoneFlags")?.optJSONArray(key)?.let { array ->
            return List(ZONES) { array.optBoolean(it, false) }
        }
        if (acquisition.optJSONObject("zoneFlags")?.has(key) == true) return null
        // Formato sem `zoneFlags` (testes/arquivos antigos): a flag de cada zona está nos pontos.
        if (points.isEmpty()) return null
        return List(ZONES) { zone -> points.any { it.optInt("zone", -1) == zone && (it.optBoolean("zoneAcquired") || it.optString("state") == "VALIDO") } }
    }

    class Fuel(
        val flags: List<Boolean>?,
        val covered: List<Boolean>?,
        val basis: List<String>,
        val bandsActive: Int?,
        val bandsMature: Int?,
    ) {
        val zonesFlagged: Int? get() = flags?.count { it }
        val zonesCovered: Int? get() = covered?.count { it }
        val complete: Boolean? get() = covered?.all { it }
        val missingZones: List<Int> get() = covered?.mapIndexedNotNull { i, ok -> if (ok) null else i + 1 } ?: emptyList()

        fun json(): JSONObject = JSONObject()
            .put("zoneFlags", flags?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("zonesFlagged", zonesFlagged ?: JSONObject.NULL)
            .put("zoneCovered", covered?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("zonesCovered", zonesCovered ?: JSONObject.NULL)
            .put("zonesTotal", ZONES)
            .put("basis", JSONArray(basis))
            .put("bandsActive", bandsActive ?: JSONObject.NULL)
            .put("bandsMature", bandsMature ?: JSONObject.NULL)
            .put("bandsTotal", BANDS)
            .put("complete", complete ?: JSONObject.NULL)
            .put("missingZones", JSONArray(missingZones))
    }

    fun fuel(acquisition: JSONObject?, fuel: String): Fuel {
        val all = acquisition?.optJSONArray("points")
        val points = ArrayList<JSONObject>()
        if (all != null) for (i in 0 until all.length()) {
            val p = all.optJSONObject(i) ?: continue
            if (p.optString("fuel") == fuel && !p.optBoolean("previous")) points += p
        }
        if (acquisition == null || points.isEmpty()) return Fuel(null, null, List(ZONES) { "DESCONHECIDA" }, null, null)
        val key = if (fuel == "GASOLINA") "petrol" else "gas"
        val zoneFlags = flags(acquisition, key, points)
        // Todos os pontos SEM_DADO e sem flags = a leitura dos buffers falhou, não "a ECU tem 0 de 4": desconhecido, nunca 0.
        if (zoneFlags == null && points.all { it.optString("state") == "SEM_DADO" }) return Fuel(null, null, List(ZONES) { "DESCONHECIDA" }, null, null)
        val basis = ArrayList<String>()
        val covered = ArrayList<Boolean>()
        for (zone in 0 until ZONES) {
            val inZone = points.filter { it.optInt("zone", -1) == zone }
            val matureCount = inZone.count { mature(it) }
            val need = (ZONE_BANDS[zone] + 1) / 2
            val flag = zoneFlags?.get(zone) == true
            val byBands = matureCount >= need
            covered += flag || byBands
            basis += when {
                flag -> "FLAG_DA_ECU"
                byBands -> "BANDAS_LIDAS"
                zoneFlags == null && inZone.isEmpty() -> "DESCONHECIDA"
                else -> "NAO_ADQUIRIDA"
            }
        }
        return Fuel(zoneFlags, covered, basis, points.count { active(it) }, points.count { mature(it) })
    }

    /** Estado completo da ECU (JSON) para o piloto/Refino/AutoCal. */
    fun fromAcquisition(acquisition: JSONObject?, autoMatchCount: Int?, maxAutomatch: Int?, autoCalEnabled: Int? = null): JSONObject {
        val petrol = fuel(acquisition, "GASOLINA")
        val gas = fuel(acquisition, "GNV")
        val read = petrol.covered != null || gas.covered != null || autoMatchCount != null
        val bothComplete = if (petrol.complete == null || gas.complete == null) null else petrol.complete == true && gas.complete == true
        val delivered = (autoMatchCount != null && autoMatchCount >= 1) || bothComplete == true
        val missing = JSONArray()
        // Contador AutoMatch (1/3, 2/3...) não descreve a cobertura da rodada atual.
        // Zonas faltantes continuam individuais depois de qualquer execução do AutoMatch.
        for ((name, f) in listOf("GASOLINA" to petrol, "GNV" to gas)) {
            if (f.missingZones.isNotEmpty()) {
                missing.put(JSONObject().put("fuel", name).put("zones", JSONArray(f.missingZones))
                    .put("text", "${if (name == "GNV") "GNV" else "Gasolina"}: ${zonesText(f.missingZones)}"))
            }
        }
        val out = JSONObject()
            .put("read", read)
            .put("autoMatchCount", autoMatchCount ?: JSONObject.NULL)
            .put("maxAutomatch", maxAutomatch ?: JSONObject.NULL)
            .put("autoCalEnabled", autoCalEnabled ?: JSONObject.NULL)
            .put("petrol", petrol.json())
            .put("gas", gas.json())
            .put("allZonesCovered", bothComplete ?: JSONObject.NULL)
            .put("delivered", delivered)
            .put("missing", missing)
        out.put("summary", summary(petrol, gas, autoMatchCount, maxAutomatch, delivered, missing))
        return out
    }

    private fun zonesText(zones: List<Int>): String =
        if (zones.size == 1) "zona ${ZONE_NAMES[zones[0] - 1]}"
        else "zonas " + zones.dropLast(1).joinToString(", ") { ZONE_NAMES[it - 1] } + " e " + ZONE_NAMES[zones.last() - 1]

    /** Frase humana: o que a ECU tem. Nunca "falta" quando a ECU já entregou. */
    private fun summary(petrol: Fuel, gas: Fuel, count: Int?, max: Int?, delivered: Boolean, missing: JSONArray): String {
        if (petrol.covered == null && gas.covered == null && count == null) return "Lendo o que a ECU guarda."
        val auto = if (count != null) "AutoMatch da ECU: $count" + (if (max != null) " de $max" else "") + ". " else ""
        fun part(label: String, f: Fuel) = if (f.zonesCovered == null) "$label —" else "$label ${f.zonesCovered}/$ZONES zonas"
        val zones = "${part("Gasolina", petrol)} · ${part("GNV", gas)}."
        return when {
            missing.length() > 0 -> {
                val list = (0 until missing.length()).joinToString("; ") { missing.getJSONObject(it).getString("text") }
                "${auto}Aquisição atual: $zones Sem confirmação neste ciclo: $list."
            }
            delivered -> "${auto}AutoMatch executado. Aquisição atual: $zones"
            else -> "${auto}$zones"
        }
    }
}
