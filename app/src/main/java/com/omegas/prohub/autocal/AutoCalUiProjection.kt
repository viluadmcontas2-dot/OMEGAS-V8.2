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
    private data class ReferenceTiming(
        val known: Boolean,
        val coherent: Boolean,
        val spanMs: Long?,
    )

    private val referenceKeys = listOf(
        AutoCalProtocol.PETR_INJ_TBP.key,
        AutoCalProtocol.PETR_MNFLD_PRESS_RV.key,
        AutoCalProtocol.GAS_MNFLD_PRESS_RV.key,
    )

    private const val SOURCE_NATIVE = "NATIVE_MONITOR"
    private const val SOURCE_MANUAL = "MANUAL_READER"
    private const val SOURCE_NONE = "NONE"

    fun project(
        nativeStatus: JSONObject,
        nativeSnapshot: JSONObject,
        manualStatus: JSONObject,
        manualSnapshot: JSONObject,
        telemetryStatus: JSONObject = JSONObject(),
    ): JSONObject {
        val nativeSession = statusSessionId(nativeStatus)
        val manualSession = statusSessionId(manualStatus)
        val currentSession = nativeSession ?: manualSession

        val nativeCurrent = snapshotAvailable(nativeSnapshot) && sameSession(nativeSession, currentSession)
        val manualReady = manualStatus.optString("state").uppercase() in setOf("READY", "READY_PARTIAL")
        val manualCurrent = snapshotAvailable(manualSnapshot) && manualReady && sameSession(manualSession, currentSession)
        val nativeTiming = referenceTiming(nativeSnapshot)
        val manualTiming = referenceTiming(manualSnapshot)
        val nativeReference =
            nativeCurrent && hasNativeReference(nativeSnapshot) && nativeTiming.coherent
        val manualReference =
            manualCurrent && hasNativeReference(manualSnapshot) && manualTiming.coherent

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
        val referenceShapeAvailable = hasNativeReference(selected)
        val selectedTiming = when (source) {
            SOURCE_NATIVE -> nativeTiming
            SOURCE_MANUAL -> manualTiming
            else -> ReferenceTiming(known = false, coherent = false, spanMs = null)
        }
        val referenceTimingCoherent = referenceShapeAvailable && selectedTiming.coherent
        val referenceUsable = referenceShapeAvailable && referenceTimingCoherent
        val staleCandidate =
            (snapshotAvailable(nativeSnapshot) && !sameSession(nativeSession, currentSession)) ||
            (snapshotAvailable(manualSnapshot) && manualReady && !sameSession(manualSession, currentSession))
        val analysis = JSONObject()
            .put("ok", true)
            .put("available", false)
            .put("mode", "NATIVE_AUTOCAL_ONLY")
            .put("inferredPredictorAttached", false)
            .put(
                "message",
                when {
                    referenceShapeAvailable && !selectedTiming.coherent ->
                        "Referência física AutoCal fora da janela temporal; releia o snapshot"
                    !referenceShapeAvailable ->
                        "Referência física AutoCal ainda indisponível"
                    else ->
                        "AutoCal nativo: predictor inferido permanece separado desta projeção."
                },
            )
        val correlationSource = if (nativeCurrent) nativeSnapshot else selected
        val correlationState = correlationSource.optJSONObject("nativeCorrelationState")
            ?.let(::copy)
            ?: emptyCorrelationState()
        val correlationEvents = correlationSource.optJSONArray("nativeMaturityEvents")
            ?.let(::copy)
            ?: JSONArray()
        val zones = acquisitionZones(nativeSnapshot.takeIf { nativeCurrent }, selected)
        val instrument = AutoCalInstrumentProjection.project(
            referenceSnapshot = selected,
            nativeCurrentSnapshot = nativeSnapshot.takeIf { nativeCurrent },
            telemetryStatus = telemetryStatus,
            currentSessionId = currentSession,
            acquisitionZones = zones,
        )

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
            .put("referenceTimingKnown", referenceShapeAvailable && selectedTiming.known)
            .put("referenceTimingCoherent", referenceTimingCoherent)
            .put("referenceTimingSpanMs", selectedTiming.spanMs ?: JSONObject.NULL)
            .put("referenceTimingLimitMs", AutoCalSnapshotBuilder.MAX_AUTOMATCH_GROUP_SKEW_MS)
            .put("snapshotHash", selected.optString("snapshotHash", ""))
            .put("revision", selected.optString("snapshotHash", ""))
            .put("snapshot", selected)
            .put("analysis", analysis)
            .put("acquisitionZones", zones)
            .put("instrument", instrument)
            .put("correlation", correlationEvents)
            .put("correlationState", correlationState)
            .put("nativeStatus", copy(nativeStatus))
            .put("nativeSnapshot", copy(nativeSnapshot))
            .put("manualStatus", copy(manualStatus))
            .put("manualSnapshot", copy(manualSnapshot))
    }

    private fun acquisitionZones(nativeCurrent: JSONObject?, selected: JSONObject): JSONObject = JSONObject()
        .put(
            "petrol",
            preferredZoneVector(nativeCurrent, selected, AutoCalProtocol.ACQUIRED_ZONES_PETROL.key),
        )
        .put(
            "gas",
            preferredZoneVector(nativeCurrent, selected, AutoCalProtocol.ACQUIRED_ZONES_GAS.key),
        )

    private fun preferredZoneVector(nativeCurrent: JSONObject?, selected: JSONObject, key: String): JSONArray {
        val native = nativeCurrent?.let { zoneVector(it, key) }
        return if (native != null && native.length() > 0) native else zoneVector(selected, key)
    }

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
        referenceKeys.all { key -> validVector(snapshot, key, KFactorProtocol.POINT_COUNT) }

    /**
     * O gráfico usa três vetores físicos. A coerência deles é independente dos
     * campos adicionais exigidos pelo cálculo completo (bandas/MUL_ACT).
     *
     * Snapshots legados sem timestamp continuam visíveis somente quando não se
     * declaram como ECU_READ. O runtime atual ECU_READ falha fechado se perder
     * proveniência temporal.
     */
    private fun referenceTiming(snapshot: JSONObject): ReferenceTiming {
        val times = referenceKeys.mapNotNull { key ->
            findValidField(snapshot, key)
                ?.optLong("capturedAtMs", 0L)
                ?.takeIf { it > 0L }
        }
        if (times.size != referenceKeys.size) {
            val currentRuntime = snapshot.optString("source").uppercase() == AutoCalSnapshotSource.ECU_READ.name
            return ReferenceTiming(
                known = false,
                coherent = !currentRuntime,
                spanMs = null,
            )
        }
        val spanMs = (times.maxOrNull() ?: 0L) - (times.minOrNull() ?: 0L)
        return ReferenceTiming(
            known = true,
            coherent = spanMs <= AutoCalSnapshotBuilder.MAX_AUTOMATCH_GROUP_SKEW_MS,
            spanMs = spanMs,
        )
    }

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

    private fun emptyCorrelationState(): JSONObject = JSONObject()
        .put("correlatedBands", JSONArray())
        .put("retryableBands", JSONArray())

    private fun copy(value: JSONObject): JSONObject = JSONObject(value.toString())

    private fun copy(value: JSONArray): JSONArray = JSONArray(value.toString())
}
