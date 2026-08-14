package com.omegas.prohub.obd

import org.json.JSONObject

/**
 * Contrato de apresentação OBD para a UI NEXT.
 *
 * Não conecta Bluetooth, não escreve ECU e não qualifica Learning. Apenas
 * transforma o status técnico já produzido por [ObdAssistManager] em estados
 * humanos com freshness explícito, preservando a diferença entre zero, null e
 * dado antigo.
 */
object ObdUiProjection {
    enum class State {
        OFF,
        CONECTANDO,
        VALIDO,
        STALE,
        SEM_PID,
        ERRO,
    }

    fun project(status: JSONObject, nowMs: Long = System.currentTimeMillis()): JSONObject {
        val mode = status.optString("mode", "off").lowercase()
        val rawState = status.optString("state", "").uppercase()
        val connected = status.optBoolean("connected", false)
        val updatedAt = status.optLong("updatedAt", 0L)
        val ageMs = if (updatedAt > 0L && nowMs >= updatedAt) nowMs - updatedAt else Long.MAX_VALUE
        val stale = connected && ageMs > FRESH_MAX_MS
        val hasPrimaryPid = hasNumber(status, "stft") || hasNumber(status, "rpm") || hasNumber(status, "mapKpa") || hasNumber(status, "load")

        val state = when {
            mode == "off" || rawState == "DESATIVADO" -> State.OFF
            rawState.contains("CONECTANDO") -> State.CONECTANDO
            rawState.contains("ERRO") || rawState.contains("PERMISS") -> State.ERRO
            stale -> State.STALE
            connected && !hasPrimaryPid -> State.SEM_PID
            connected -> State.VALIDO
            else -> State.OFF
        }

        return JSONObject()
            .put("schema", "omegas-next-obd-v1")
            .put("state", state.name)
            .put("connected", connected)
            .put("mode", mode)
            .put("updatedAt", updatedAt.takeIf { it > 0L } ?: JSONObject.NULL)
            .put("ageMs", if (ageMs == Long.MAX_VALUE) JSONObject.NULL else ageMs)
            .put("reason", status.optString("reason", ""))
            .put("quality", status.optString("quality", ""))
            .put("fuel", nullableString(status, "fuel"))
            .put("stftPct", freshNumber(status, "stft", stale))
            .put("ltftPct", freshNumber(status, "ltft", stale))
            .put("rpm", freshNumber(status, "rpm", stale))
            .put("mapKpa", freshNumber(status, "mapKpa", stale))
            .put("loadPct", freshNumber(status, "load", stale))
            .put("coolantC", freshNumber(status, "coolant", stale))
            .put("closedLoop", status.optBoolean("closedLoop", false))
            .put("pidAvailability", JSONObject()
                .put("stft", hasNumber(status, "stft"))
                .put("ltft", hasNumber(status, "ltft"))
                .put("rpm", hasNumber(status, "rpm"))
                .put("map", hasNumber(status, "mapKpa"))
                .put("load", hasNumber(status, "load")))
            .put("observationalOnly", true)
            .put("ecuAuthority", false)
            .put("learningAuthority", false)
            .put("automaticCalibration", false)
    }

    private fun hasNumber(root: JSONObject, key: String): Boolean =
        root.has(key) && !root.isNull(key) && root.opt(key) is Number

    private fun freshNumber(root: JSONObject, key: String, stale: Boolean): Any =
        if (!stale && hasNumber(root, key)) root.optDouble(key) else JSONObject.NULL

    private fun nullableString(root: JSONObject, key: String): Any =
        root.optString(key).takeIf { it.isNotBlank() } ?: JSONObject.NULL

    const val FRESH_MAX_MS = 5_000L
}
