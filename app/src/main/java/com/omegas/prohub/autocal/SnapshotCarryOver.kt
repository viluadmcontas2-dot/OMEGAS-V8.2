package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot completo parcial (ECU#5): um campo que voltou sem dado nesta varredura não apaga o valor
 * VALID que a MESMA sessão USB já tinha. O campo herdado vem marcado `carriedFromPreviousSnapshot`.
 */
object SnapshotCarryOver {
    fun mergeFields(previous: JSONObject?, fresh: JSONObject, usbSessionId: Long): JSONObject {
        val merged = JSONObject(fresh.toString())
        if (previous == null || !previous.optBoolean("available", false) ||
            previous.optLong("usbSessionId", -1L) != usbSessionId
        ) return merged.put("carriedFieldCount", 0)
        val previousFields = previous.optJSONArray("fields") ?: return merged.put("carriedFieldCount", 0)
        val validBefore = HashMap<String, JSONObject>()
        repeat(previousFields.length()) { index ->
            val field = previousFields.optJSONObject(index) ?: return@repeat
            if (field.optString("status") == AutoCalFieldStatus.VALID.name) validBefore[field.optString("key")] = field
        }
        val freshFields = merged.optJSONArray("fields") ?: JSONArray()
        val out = JSONArray()
        var carried = 0
        repeat(freshFields.length()) { index ->
            val field = freshFields.optJSONObject(index) ?: return@repeat
            val old = validBefore[field.optString("key")]
            if (field.optString("status") != AutoCalFieldStatus.VALID.name && old != null) {
                out.put(JSONObject(old.toString()).put("carriedFromPreviousSnapshot", true))
                carried += 1
            } else {
                out.put(field)
            }
        }
        return merged.put("fields", out).put("carriedFieldCount", carried)
    }

    /** Escalar não lido nesta varredura (nulo) mantém o valor anterior; lido substitui. */
    fun <T> keepIfUnread(fresh: T?, previous: T?): T? = fresh ?: previous
}

/**
 * Evidência de AutoMatch (contador nativo) que o snapshot completo ainda não entregou (ECU#6). O contador é
 * consumido uma vez só pelo tracker; se o snapshot não roda (recuo) ou falha, o evento espera a próxima tentativa.
 */
class PendingAutoMatchEvidence {
    private var event: NativeAutoMatchCounterTracker.Event? = null
    private var increased = false

    fun carry(newEvent: NativeAutoMatchCounterTracker.Event?, countIncreased: Boolean) {
        if (newEvent != null) {
            val carried = event
            event = if (carried != null) {
                newEvent.copy(beforeCount = carried.beforeCount, delta = newEvent.afterCount - carried.beforeCount)
            } else newEvent
        }
        if (countIncreased) increased = true
    }

    fun merge(
        current: NativeAutoMatchCounterTracker.Event?,
        countIncreased: Boolean,
    ): Pair<NativeAutoMatchCounterTracker.Event?, Boolean> {
        val carried = event
        val merged = if (carried != null && current != null) {
            current.copy(beforeCount = carried.beforeCount, delta = current.afterCount - carried.beforeCount)
        } else carried ?: current
        return merged to (countIncreased || increased)
    }

    fun clear() {
        event = null
        increased = false
    }
}
