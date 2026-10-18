package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/**
 * Estado das bandas logo antes de um AutoMatch da ECU. Só leitura: resume a última leitura boa do monitor (≤ ~1 s antes do
 * momento em que a contagem mudou) para a sessão responder "o que a ECU tinha quando decidiu?". Valor desconhecido fica nulo, nunca 0.
 */
object PreAutoMatchState {
    private const val FULL = 10

    fun capture(snapshot: JSONObject, countAfter: Int, nowMs: Long): JSONObject? {
        if (!snapshot.optBoolean("available", false)) return null
        val fields = snapshot.optJSONArray("fields") ?: return null
        val byKey = HashMap<String, JSONObject>()
        for (i in 0 until fields.length()) {
            val field = fields.optJSONObject(i) ?: continue
            if (field.optString("status") == "VALID") byKey[field.optString("key")] = field
        }
        val gas = ints(byKey["NUM_BUF_UPD_GAS"]) ?: return null
        val petrol = ints(byKey["NUM_BUF_UPD_PETR"])
        val zones = ints(byKey["ACQUIRED_ZONES_GAS"])
        val capturedAt = byKey["NUM_BUF_UPD_GAS"]?.optLong("capturedAtMs", -1L) ?: -1L
        val out = JSONObject()
            .put("countAfter", countAfter)
            .put("countBefore", ints(byKey["NUM_AUTOMATCH_EXECUTED"])?.firstOrNull() ?: JSONObject.NULL)
            .put("counterAgeMs", if (capturedAt >= 0L) (nowMs - capturedAt).coerceAtLeast(0L) else JSONObject.NULL)
            .put("gasCounters", JSONArray(gas.toList()))
            .put("gasBandsAtLeast1", gas.count { it >= 1 })
            .put("gasBandsAtLeast3", gas.count { it >= 3 })
            .put("gasBandsFull", gas.count { it >= FULL })
            .put("gasCounterSum", gas.sum())
            .put("gasZonesAcquired", zones?.count { it != 0 } ?: JSONObject.NULL)
        if (zones != null) out.put("gasZones", JSONArray(zones.toList()))
        if (petrol != null) {
            out.put("petrolCounters", JSONArray(petrol.toList()))
                .put("petrolBandsFull", petrol.count { it >= FULL })
        }
        for ((key, name) in listOf(
            "PETR_INJ_TBUF_GAS" to "gasTimeRaw",
            "MNFLD_PRESS_BUF_GAS" to "gasMapRaw",
            "MUL_ACT" to "mulActRaw",
            "CALIBRATION_VAL_1" to "calibrationVal1",
            "MAX_AUTOMATCH" to "maxAutomatch",
        )) {
            ints(byKey[key])?.let { out.put(name, JSONArray(it.toList())) }
        }
        return out
    }

    private fun ints(field: JSONObject?): List<Int>? {
        val raw = field?.optJSONArray("rawValues") ?: return null
        return List(raw.length()) { raw.getInt(it) }
    }
}
