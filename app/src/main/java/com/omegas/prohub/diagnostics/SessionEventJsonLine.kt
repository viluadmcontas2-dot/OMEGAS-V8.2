package com.omegas.prohub.diagnostics

import org.json.JSONObject

/**
 * Codifica uma linha do recorder sem desserializar novamente o payload já
 * capturado. O campo data continua sendo JSON estrutural, não uma String JSON.
 */
internal object SessionEventJsonLine {
    fun encode(
        format: String,
        sequence: Long,
        recordedAtMs: Long,
        recordedAtUtc: String,
        type: String,
        source: String,
        dataJson: String,
    ): String = buildString(dataJson.length + 160) {
        append('{')
        append("\"format\":").append(JSONObject.quote(format))
        append(",\"sequence\":").append(sequence)
        append(",\"recordedAtMs\":").append(recordedAtMs)
        append(",\"recordedAtUtc\":").append(JSONObject.quote(recordedAtUtc))
        append(",\"type\":").append(JSONObject.quote(type))
        append(",\"source\":").append(JSONObject.quote(source))
        append(",\"data\":").append(dataJson)
        append("}\n")
    }
}
