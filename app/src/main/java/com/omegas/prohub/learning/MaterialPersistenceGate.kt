package com.omegas.prohub.learning

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Persistência nasce de revisão científica material, nunca da mera chegada de frame.
 * Diagnóstico pode mudar em RAM sem criar snapshot; fronteiras explícitas podem forçar
 * uma fotografia final sem fabricar uma nova revisão científica.
 */
internal class MaterialPersistenceGate {
    private val materialRevision = AtomicLong(0L)
    private val requestedRevision = AtomicLong(0L)
    private val materialChanges = AtomicLong(0L)
    private val skippedRedundantRequests = AtomicLong(0L)
    private val forcedBoundaryRequests = AtomicLong(0L)

    fun markMaterialChange(): Long {
        materialChanges.incrementAndGet()
        return materialRevision.incrementAndGet()
    }

    fun shouldRequest(forceBoundary: Boolean = false): Boolean {
        if (forceBoundary) {
            forcedBoundaryRequests.incrementAndGet()
            requestedRevision.accumulateAndGet(materialRevision.get()) { current, incoming -> maxOf(current, incoming) }
            return true
        }
        while (true) {
            val material = materialRevision.get()
            val requested = requestedRevision.get()
            if (material <= requested) {
                skippedRedundantRequests.incrementAndGet()
                return false
            }
            if (requestedRevision.compareAndSet(requested, material)) return true
        }
    }

    fun metricsJson(): JSONObject = JSONObject()
        .put("policy", "PERSIST_AFTER_MATERIAL_EVIDENCE_REVISION")
        .put("materialRevision", materialRevision.get())
        .put("requestedRevision", requestedRevision.get())
        .put("materialChanges", materialChanges.get())
        .put("skippedRedundantRequests", skippedRedundantRequests.get())
        .put("forcedBoundaryRequests", forcedBoundaryRequests.get())
}
