package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.KFactorProtocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * Autoridade nativa única para a projeção AutoCal consumida pela HMI.
 *
 * O monitor é preferido quando possui referência física utilizável na geração
 * USB atual. O reader manual é fallback somente quando também pertence à
 * geração atual. Snapshot de sessão desconhecida falha fechado quando existe
 * uma geração USB conhecida.
 */
object AutoCalUiProjection {
    private const val SOURCE_NATIVE = "NATIVE_MONITOR"
    private const val SOURCE_MANUAL = "MANUAL_READER"
    private const val SOURCE_NONE = "NONE"

    fun project(
        nativeStatus: JSONObject,
        nativeSnapshot: JSONObject,
        manualStatus: JSONObject,
        manualSnapshot: JSONObject,
    ): JSONObject {
        val nativeSession = statusSessionId(nativeStatus)
        val manualSession = statusSessionId(manualStatus)
        val currentSession = nativeSession ?: manualSession

        val nativeCurrent = snapshotAvailable(nativeSnapshot) && sameSession(nativeSession, currentSession)
        val manualReady = manualStatus.optString("state").uppercase() in setOf("READY", "READY_PARTIAL")
        val manualCurrent = snapshotAvailable(manualSnapshot) && manualReady && sameSession(manualSession, currentSession)
        val nativeReference = nativeCurrent && hasNativeReference(nativeSnapshot)
        val manualReference = manualCurrent && hasNativeReference(manualSnapshot)

        val source = when {
            nativeReference -> SOURCE_NATIVE
            manualReference -> SOURCE_MANUAL
            nativeCurrent -> SOURCE_NATIVE
            manualCurrent -> SOURCE_MANUAL
            else -> SOURCE_NONE
        }
        val selected = when (source) {
            SOURCE_NATIVE -> copy(nativeSnapshot)
            SOURCE_MANUAL -> copy(manualSnapshot)
            else -> emptySnapshot()
        }
        val selectedStatus = when (source) {
            SOURCE_NATIVE -> nativeStatus
            SOURCE_MANUAL -> manualStatus
            else -> JSONObject()
        }
        val referenceUsable = hasNativeReference(selected)
        val staleCandidate =
            (snapshotAvailable(nativeSnapshot) && !sameSession(nativeSession, currentSession)) ||
            (snapshotAvailable(manualSnapshot) && manualReady && !sameSession(manualSession, currentSession))
        val analysis = if (referenceUsable) {
            AutoMatchSnapshotAnalysis.analyze(selected)
        } else {
            JSONObject()
                .put("ok", true)
                .put("available", false)
                .put("message", "Referência física AutoCal ainda indisponível")
        }

        return JSONObject()
            .put("ok", true)
            .put("source", source)
            .put("sessionId", currentSession ?: JSONObject.NULL)
            .put("sourceSessionId", statusSessionId(selectedStatus) ?: JSONObject.NULL)
            .put("sourceUpdatedAt", selectedStatus.optLong("updatedAt", 0L))
            .put(
                "freshness",
                when {
                    source != SOURCE_NONE -> "CURRENT_SESSION"
                    staleCandidate -> "STALE_SESSION"
                    else -> "MISSING"
                },
            )
            .put("snapshotAvailable", source != SOURCE_NONE && snapshotAvailable(selected))
            .put("referenceAvailable", referenceUsable)
            .put("referenceUsable", referenceUsable)
            .put("snapshotHash", selected.optString("snapshotHash", ""))
            .put("revision", selected.optString("snapshotHash", ""))
            .put("snapshot", selected)
            .put("analysis", analysis)
            .put("acquisitionZones", acquisitionZones(selected))
            .put("correlation", selected.optJSONArray("nativeMaturityEvents") ?: JSONArray())
            .put("nativeStatus", copy(nativeStatus))
            .put("nativeSnapshot", copy(nativeSnapshot))
            .put("manualStatus", copy(manualStatus))
            .put("manualSnapshot", copy(manualSnapshot))
    }

    private fun acquisitionZones(snapshot: JSONObject): JSONObject = JSONObject()
        .put("petrol", zoneVector(snapshot, AutoCalProtocol.ACQUIRED_ZONES_PETROL.key))
        .put("gas", zoneVector(snapshot, AutoCalProtocol.ACQUIRED_ZONES_GAS.key))

    private fun zoneVector(snapshot: JSONObject, key: String): JSONArray {
        val field = findValidField(snapshot, key) ?: return JSONArray()
        val values = field.optJSONArray("rawValues") ?: return JSONArray()
        return JSONArray().apply {
            repeat(values.length().coerceAtMost(4)) { index -> put(values.optInt(index, 0) > 0) }
        }
    }

    private fun snapshotAvailable(snapshot: JSONObject): Boolean {
        if (snapshot.optBoolean("available", false)) return true
        if (snapshot.optInt("validFieldCount", 0) > 0) return true
        return (snapshot.optJSONArray("fields")?.length() ?: 0) > 0
    }

    private fun hasNativeReference(snapshot: JSONObject): Boolean =
        listOf(
            AutoCalProtocol.PETR_INJ_TBP.key,
            AutoCalProtocol.PETR_MNFLD_PRESS_RV.key,
            AutoCalProtocol.GAS_MNFLD_PRESS_RV.key,
        ).all { key -> validVector(snapshot, key, KFactorProtocol.POINT_COUNT) }

    private fun validVector(snapshot: JSONObject, key: String, expected: Int): Boolean {
        val field = findValidField(snapshot, key) ?: return false
        val raw = field.optJSONArray("rawValues")
        val physical = field.optJSONArray("physicalValues")
        return raw?.length() == expected || physical?.length() == expected
    }

    private fun findValidField(snapshot: JSONObject, key: String): JSONObject? {
        val fields = snapshot.optJSONArray("fields") ?: return null
        repeat(fields.length()) { index ->
            val field = fields.optJSONObject(index) ?: return@repeat
            if (field.optString("key") == key && field.optString("status") == "VALID") return field
        }
        return null
    }

    private fun statusSessionId(status: JSONObject): Long? {
        if (!status.has("sessionId") || status.isNull("sessionId")) return null
        val value = status.optLong("sessionId", 0L)
        return value.takeIf { it > 0L }
    }

    private fun sameSession(candidate: Long?, current: Long?): Boolean =
        if (current == null) true else candidate != null && candidate == current

    private fun emptySnapshot(): JSONObject = JSONObject()
        .put("available", false)
        .put("fields", JSONArray())

    private fun copy(value: JSONObject): JSONObject = JSONObject(value.toString())
}
