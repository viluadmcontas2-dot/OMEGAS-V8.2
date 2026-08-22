package com.omegas.prohub.runtime

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Latest-only in-memory state cache for UI consumers. */
class RuntimeSnapshotBus {
    private val lock = Any()
    private val presentRevision = AtomicLong(0L)
    private val scienceRevision = AtomicLong(0L)
    private var presentData = JSONObject()
    private var scienceData = JSONObject()
    private var scienceToken = ""

    fun publishPresent(payload: JSONObject): Long = synchronized(lock) {
        presentData = JSONObject(payload.toString())
        presentRevision.incrementAndGet()
    }

    fun publishScience(payload: JSONObject, revisionToken: String): Long = synchronized(lock) {
        if (revisionToken == scienceToken && scienceRevision.get() > 0L) return@synchronized scienceRevision.get()
        scienceData = JSONObject(payload.toString())
        scienceToken = revisionToken
        scienceRevision.incrementAndGet()
    }

    fun presentJson(): JSONObject = synchronized(lock) {
        JSONObject()
            .put("ok", true)
            .put("revision", presentRevision.get())
            .put("data", JSONObject(presentData.toString()))
    }

    fun scienceJsonSince(lastRevision: Long): JSONObject = synchronized(lock) {
        val current = scienceRevision.get()
        if (lastRevision >= current) {
            JSONObject().put("ok", true).put("changed", false).put("revision", current)
        } else {
            JSONObject()
                .put("ok", true)
                .put("changed", true)
                .put("revision", current)
                .put("token", scienceToken)
                .put("data", JSONObject(scienceData.toString()))
        }
    }
}
