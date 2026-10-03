package com.omegas.prohub.runtime

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Latest-only in-memory state cache for UI consumers. */
class RuntimeSnapshotBus {
    private val lock = Any()
    private val presentRevision = AtomicLong(0L)
    private var presentData = JSONObject()

    fun publishPresent(payload: JSONObject): Long = synchronized(lock) {
        presentData = JSONObject(payload.toString())
        presentRevision.incrementAndGet()
    }

    fun presentJson(): JSONObject = synchronized(lock) {
        JSONObject()
            .put("ok", true)
            .put("revision", presentRevision.get())
            .put("data", JSONObject(presentData.toString()))
    }
}
