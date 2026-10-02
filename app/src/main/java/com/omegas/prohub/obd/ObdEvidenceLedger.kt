package com.omegas.prohub.obd

import org.json.JSONArray
import org.json.JSONObject

/**
 * Histórico mínimo e puro da evidência OBD.
 *
 * Uma época delimita o antes/depois de uma alteração que o operador confirmou
 * e cuja releitura da ECU foi válida. Ela não conhece USB, Bluetooth nem
 * writers; a camada de serviço entrega apenas recibos já confirmados.
 */
class ObdEvidenceLedger {
    data class Epoch(
        val mapEpochId: String,
        val curveEpochId: String,
        val mapReadbackHash: String,
        val curveReadbackHash: String,
        val startedAtMs: Long,
    )

    data class OpenResult(val opened: Boolean, val epoch: Epoch?, val reason: String)

    private var mapSequence = 0
    private var curveSequence = 0
    private var mapHash = "UNCONFIRMED"
    private var curveHash = "UNCONFIRMED"
    private var startedAtMs = 0L
    private val rejectionCounts = linkedMapOf<String, Long>()
    private var lastRejectionReason = ""
    private val history = mutableListOf<Epoch>()

    fun current(): Epoch = Epoch("map-$mapSequence", "curve-$curveSequence", mapHash, curveHash, startedAtMs)

    fun recordRejection(reason: String) {
        if (reason.isBlank()) return
        rejectionCounts[reason] = (rejectionCounts[reason] ?: 0L) + 1L
        lastRejectionReason = reason
    }

    fun openAfterConfirmedReadback(
        kind: String,
        humanConfirmed: Boolean,
        readbackValid: Boolean,
        readbackHash: String?,
        nowMs: Long,
    ): OpenResult {
        val hash = readbackHash?.trim().orEmpty()
        if (!humanConfirmed) return OpenResult(false, null, "aguardando confirmação manual")
        if (!readbackValid || hash.isBlank()) return OpenResult(false, null, "readback inválido ou ausente")
        when (kind) {
            "MAP_K" -> if (hash == mapHash) return OpenResult(false, null, "mapa K sem alteração confirmada")
            "K_FACTOR" -> if (hash == curveHash) return OpenResult(false, null, "curva K sem alteração confirmada")
            else -> return OpenResult(false, null, "tipo de ajuste desconhecido")
        }
        if (kind == "MAP_K") {
            mapSequence += 1
            mapHash = hash
        } else {
            curveSequence += 1
            curveHash = hash
        }
        startedAtMs = nowMs
        val epoch = current()
        history += epoch
        return OpenResult(true, epoch, "época aberta após confirmação e readback")
    }

    fun metricsJson(): JSONObject = JSONObject()
        .put("rejected", rejectionCounts.values.sum())
        .put("lastReason", if (lastRejectionReason.isBlank()) JSONObject.NULL else lastRejectionReason)
        .also { output -> rejectionCounts.forEach { (reason, count) -> output.put(reason, count) } }

    private fun rejectionCountsJson(): JSONObject = JSONObject().also { output ->
        rejectionCounts.forEach { (reason, count) -> output.put(reason, count) }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("mapSequence", mapSequence)
        .put("curveSequence", curveSequence)
        .put("mapHash", mapHash)
        .put("curveHash", curveHash)
        .put("startedAtMs", startedAtMs)
        .put("lastRejectionReason", lastRejectionReason)
        .put("rejections", rejectionCountsJson())
        .put("history", JSONArray().also { list -> history.forEach { list.put(epochJson(it)) } })

    fun load(json: JSONObject?) {
        if (json == null) return
        mapSequence = json.optInt("mapSequence", 0).coerceAtLeast(0)
        curveSequence = json.optInt("curveSequence", 0).coerceAtLeast(0)
        mapHash = json.optString("mapHash", "UNCONFIRMED")
        curveHash = json.optString("curveHash", "UNCONFIRMED")
        startedAtMs = json.optLong("startedAtMs", 0L)
        lastRejectionReason = json.optString("lastRejectionReason", "")
        rejectionCounts.clear()
        json.optJSONObject("rejections")?.keys()?.forEach { reason ->
            rejectionCounts[reason] = json.optJSONObject("rejections")!!.optLong(reason, 0L)
        }
        history.clear()
        json.optJSONArray("history")?.let { entries ->
            repeat(entries.length()) { index -> epochFromJson(entries.optJSONObject(index))?.let(history::add) }
        }
    }

    private fun epochJson(epoch: Epoch): JSONObject = JSONObject()
        .put("mapEpochId", epoch.mapEpochId)
        .put("curveEpochId", epoch.curveEpochId)
        .put("mapReadbackHash", epoch.mapReadbackHash)
        .put("curveReadbackHash", epoch.curveReadbackHash)
        .put("startedAtMs", epoch.startedAtMs)

    private fun epochFromJson(json: JSONObject?): Epoch? = json?.let {
        Epoch(
            it.optString("mapEpochId"), it.optString("curveEpochId"),
            it.optString("mapReadbackHash"), it.optString("curveReadbackHash"),
            it.optLong("startedAtMs", 0L),
        )
    }
}
