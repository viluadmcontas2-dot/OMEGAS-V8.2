package com.omegas.v7.runtime

import org.json.JSONArray
import org.json.JSONObject
import java.util.WeakHashMap

/**
 * Serialização compacta e somente leitura para a WebView.
 *
 * A Store consulta o estado contextual repetidamente. Como comparações V7 são
 * imutáveis depois de criadas, a projeção científica pode ser reutilizada enquanto
 * revisão/quantidade/identidade das comparações não mudar. Isso evita recomputar a
 * grade consolidada quando somente telemetria ao vivo mudou.
 */
object LearningStabilityJsonV7 {
    private data class Signature(
        val curveRevision: Long,
        val mapRevision: Long,
        val count: Int,
        val firstId: String,
        val lastId: String,
        val lastCreatedAtMs: Long,
    )

    private data class Cached(
        val signature: Signature,
        val payload: JSONObject,
    )

    private val cache = WeakHashMap<V7SessionRuntime, Cached>()

    @Synchronized
    fun from(runtime: V7SessionRuntime): JSONObject {
        val comparisons = runtime.state.activeComparisons()
        val revision = runtime.state.calibration.revision
        val signature = Signature(
            curveRevision = revision.curveK,
            mapRevision = revision.mapK,
            count = comparisons.size,
            firstId = comparisons.firstOrNull()?.id.orEmpty(),
            lastId = comparisons.lastOrNull()?.id.orEmpty(),
            lastCreatedAtMs = comparisons.lastOrNull()?.createdAtMs ?: 0L,
        )
        cache[runtime]?.takeIf { it.signature == signature }?.let { return it.payload }

        val map = LearningStabilityProjectionV7.mapWithEvidence(comparisons)
        val curve = LearningStabilityProjectionV7.curveWithEvidence(comparisons)
        val payload = JSONObject()
            .put("mode", "CONSOLIDATED_REVALIDATION")
            .put("immutableVisits", true)
            .put("projectionCached", true)
            .put("activeComparisons", comparisons.size)
            .put("map", JSONArray(map.map { (key, value) ->
                val parts = key.split(':')
                snapshotJson(value)
                    .put("row", parts[0].toInt())
                    .put("column", parts[1].toInt())
                    .put("key", key)
            }))
            .put("curve", JSONArray(curve.map { (index, value) ->
                snapshotJson(value).put("index", index)
            }))
        cache[runtime] = Cached(signature, payload)
        return payload
    }

    private fun snapshotJson(value: LearningStabilitySnapshotV7): JSONObject = JSONObject()
        .put("state", value.state.name)
        .put("generation", value.generation)
        .put("consolidatedErrorPercent", value.consolidatedErrorPercent ?: JSONObject.NULL)
        .put("recentErrorPercent", value.recentErrorPercent ?: JSONObject.NULL)
        .put("confidence", value.confidence)
        .put("consolidatedEffectiveVisits", value.consolidatedEffectiveVisits)
        .put("recentEffectiveVisits", value.recentEffectiveVisits)
        .put("consolidatedUniqueVisits", value.consolidatedUniqueVisits)
        .put("recentUniqueVisits", value.recentUniqueVisits)
        .put("rpmBandCount", value.rpmBandCount)
        .put("mapBandCount", value.mapBandCount)
        .put("direction", value.direction)
        .put("reason", value.reason)
}
