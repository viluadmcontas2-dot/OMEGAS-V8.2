package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/**
 * Typed native-to-visual projection for the AutoCAL instrument.
 *
 * This object does not infer firmware science. It only turns fields already
 * proven by ProgBase/Portmon into stable HMI primitives so JavaScript never
 * has to rediscover producer/consumer semantics.
 */
object AutoCalInstrumentProjection {
    const val LIVE_STALE_MS = 2_500L

    fun project(
        referenceSnapshot: JSONObject,
        nativeCurrentSnapshot: JSONObject?,
        telemetryStatus: JSONObject,
        currentSessionId: Long?,
        acquisitionZones: JSONObject,
    ): JSONObject {
        val dynamic = nativeCurrentSnapshot ?: JSONObject()
        val kSource = if (hasValidField(dynamic, "MUL_ACT")) dynamic else referenceSnapshot

        return JSONObject()
            .put("schema", "omegas.autocal.instrument.v1")
            .put(
                "axes",
                JSONObject()
                    .put("x", JSONObject().put("key", "PETR_INJ_TBP").put("label", "Tempo de injeção").put("unit", "ms"))
                    .put("y", JSONObject().put("key", "MAP").put("label", "MAP").put("unit", "bar")),
            )
            .put(
                "reference",
                JSONObject()
                    .put("petrol", pairedPoints(referenceSnapshot, "PETR_INJ_TBP", "PETR_MNFLD_PRESS_RV"))
                    .put("gas", pairedPoints(referenceSnapshot, "PETR_INJ_TBP", "GAS_MNFLD_PRESS_RV"))
                    .put("capturedAtMs", maxFieldTimestamp(referenceSnapshot, listOf("PETR_INJ_TBP", "PETR_MNFLD_PRESS_RV", "GAS_MNFLD_PRESS_RV"))),
            )
            .put(
                "acquisition",
                JSONObject()
                    .put("petrolCurrent", pairedPoints(dynamic, "PETR_INJ_TBUF", "MNFLD_PRESS_BUF"))
                    .put("gasCurrent", pairedPoints(dynamic, "PETR_INJ_TBUF_GAS", "MNFLD_PRESS_BUF_GAS"))
                    .put("gasPrevious", pairedPoints(dynamic, "PETR_INJ_TBUF_GAS_PREV", "MNFLD_PRESS_BUF_GAS_PREV"))
                    .put("petrolCounter", rawVector(dynamic, "NUM_BUF_UPD_PETR"))
                    .put("gasCounter", rawVector(dynamic, "NUM_BUF_UPD_GAS")),
            )
            .put(
                "kCurve",
                JSONObject()
                    .put("points", pairedFactorPoints(kSource, "PETR_INJ_TBP", "MUL_ACT"))
                    .put("capturedAtMs", maxFieldTimestamp(kSource, listOf("PETR_INJ_TBP", "MUL_ACT"))),
            )
            .put("zones", JSONObject(acquisitionZones.toString()))
            .put("liveNow", livePoint(telemetryStatus, currentSessionId))
            .put(
                "freshness",
                JSONObject()
                    .put("liveAgeMs", telemetryStatus.optLong("ageMs", -1L))
                    .put("liveStaleAfterMs", LIVE_STALE_MS)
                    .put("referenceCapturedAtMs", maxFieldTimestamp(referenceSnapshot, listOf("PETR_INJ_TBP", "PETR_MNFLD_PRESS_RV", "GAS_MNFLD_PRESS_RV")))
                    .put("acquisitionCapturedAtMs", maxFieldTimestamp(dynamic, listOf(
                        "PETR_INJ_TBUF",
                        "MNFLD_PRESS_BUF",
                        "PETR_INJ_TBUF_GAS",
                        "MNFLD_PRESS_BUF_GAS",
                        "PETR_INJ_TBUF_GAS_PREV",
                        "MNFLD_PRESS_BUF_GAS_PREV",
                    ))),
            )
            .put(
                "authority",
                JSONObject()
                    .put("nativeCurves", "ECU_READ")
                    .put("nativePoints", "ECU_READ")
                    .put("liveNow", "MP48_LIVE")
                    .put("uiDerivesScience", false),
            )
    }

    private fun pairedPoints(snapshot: JSONObject, xKey: String, yKey: String): JSONArray {
        val x = physicalVector(snapshot, xKey)
        val y = physicalVector(snapshot, yKey)
        val count = minOf(x.size, y.size)
        return JSONArray().apply {
            repeat(count) { index ->
                val xv = x[index]
                val yv = y[index]
                if (xv.isFinite() && yv.isFinite()) {
                    put(
                        JSONObject()
                            .put("index", index)
                            .put("petrolMs", xv)
                            .put("mapBar", yv),
                    )
                }
            }
        }
    }

    private fun pairedFactorPoints(snapshot: JSONObject, xKey: String, factorKey: String): JSONArray {
        val x = physicalVector(snapshot, xKey)
        val factor = physicalVector(snapshot, factorKey)
        val count = minOf(x.size, factor.size)
        return JSONArray().apply {
            repeat(count) { index ->
                val xv = x[index]
                val fv = factor[index]
                if (xv.isFinite() && fv.isFinite()) {
                    put(
                        JSONObject()
                            .put("index", index)
                            .put("petrolMs", xv)
                            .put("factor", fv),
                    )
                }
            }
        }
    }

    private fun livePoint(telemetryStatus: JSONObject, currentSessionId: Long?): Any {
        if (!telemetryStatus.optBoolean("valid", false)) return JSONObject.NULL
        val ageMs = telemetryStatus.optLong("ageMs", -1L)
        if (ageMs < 0L || ageMs > LIVE_STALE_MS) return JSONObject.NULL

        if (currentSessionId != null) {
            val telemetrySession = telemetryStatus.optLong("sessionId", 0L)
            if (telemetrySession <= 0L || telemetrySession != currentSessionId) return JSONObject.NULL
        }

        val live = telemetryStatus.optJSONObject("live") ?: return JSONObject.NULL
        val petrolMs = finiteDouble(live, "petrol_ms") ?: return JSONObject.NULL
        val mapBar = finiteDouble(live, "load_bar") ?: return JSONObject.NULL
        val rpm = finiteDouble(live, "rpm") ?: return JSONObject.NULL
        if (petrolMs < 0.0 || mapBar < 0.0 || rpm < 0.0) return JSONObject.NULL

        return JSONObject()
            .put("petrolMs", petrolMs)
            .put("mapBar", mapBar)
            .put("rpm", rpm)
            .put("fuel", live.optString("fuel", ""))
            .put("gasMs", finiteDouble(live, "gas_ms_diagnostic") ?: finiteDouble(live, "gas_ms") ?: JSONObject.NULL)
            .put("capturedElapsedMs", live.optLong("captured_elapsed_ms", 0L))
            .put("ageMs", ageMs)
    }

    private fun physicalVector(snapshot: JSONObject, key: String): List<Double> {
        val field = findValidField(snapshot, key) ?: return emptyList()
        val values = field.optJSONArray("physicalValues") ?: return emptyList()
        return List(values.length()) { index -> values.optDouble(index, Double.NaN) }
    }

    private fun rawVector(snapshot: JSONObject, key: String): JSONArray {
        val field = findValidField(snapshot, key) ?: return JSONArray()
        val values = field.optJSONArray("rawValues") ?: return JSONArray()
        return JSONArray(values.toString())
    }

    private fun hasValidField(snapshot: JSONObject, key: String): Boolean = findValidField(snapshot, key) != null

    private fun findValidField(snapshot: JSONObject, key: String): JSONObject? {
        val fields = snapshot.optJSONArray("fields") ?: return null
        repeat(fields.length()) { index ->
            val field = fields.optJSONObject(index) ?: return@repeat
            if (field.optString("key") == key && field.optString("status") == "VALID") return field
        }
        return null
    }

    private fun maxFieldTimestamp(snapshot: JSONObject, keys: List<String>): Any {
        val values = keys.mapNotNull { key ->
            findValidField(snapshot, key)
                ?.optLong("capturedAtMs", 0L)
                ?.takeIf { it > 0L }
        }
        return values.maxOrNull() ?: JSONObject.NULL
    }

    private fun finiteDouble(source: JSONObject, key: String): Double? {
        if (!source.has(key) || source.isNull(key)) return null
        val value = source.optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() }
    }
}
