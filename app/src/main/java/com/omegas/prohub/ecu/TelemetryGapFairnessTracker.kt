package com.omegas.prohub.ecu

import org.json.JSONArray
import org.json.JSONObject

/**
 * Observabilidade de fairness da porta MP48.
 *
 * Separa o intervalo normal de telemetria, o intervalo que contém uma unit/transação
 * planejada e o primeiro intervalo logo após essa unit. Não governa scheduler,
 * prioridade, ciência nem política de retry; apenas mede o que realmente aconteceu.
 */
class TelemetryGapFairnessTracker(
    private val capacityPerBucket: Int = 64,
) {
    init { require(capacityPerBucket > 0) }

    enum class Bucket { BASELINE, PLANNED_WORK, RECOVERY }

    data class Snapshot(
        val baseline: List<Long>,
        val plannedWork: List<Long>,
        val recovery: List<Long>,
    )

    private val lock = Any()
    private val baseline = ArrayDeque<Long>()
    private val planned = ArrayDeque<Long>()
    private val recovery = ArrayDeque<Long>()
    private var recoverNext = false

    fun reset() = synchronized(lock) {
        baseline.clear()
        planned.clear()
        recovery.clear()
        recoverNext = false
    }

    fun record(intervalMs: Long, plannedGap: Boolean): Bucket? = synchronized(lock) {
        if (intervalMs <= 0L) return@synchronized null
        val bucket = when {
            plannedGap -> {
                recoverNext = true
                Bucket.PLANNED_WORK
            }
            recoverNext -> {
                recoverNext = false
                Bucket.RECOVERY
            }
            else -> Bucket.BASELINE
        }
        when (bucket) {
            Bucket.BASELINE -> addBounded(baseline, intervalMs)
            Bucket.PLANNED_WORK -> addBounded(planned, intervalMs)
            Bucket.RECOVERY -> addBounded(recovery, intervalMs)
        }
        bucket
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(baseline.toList(), planned.toList(), recovery.toList())
    }

    fun toJson(): JSONObject {
        val snapshot = snapshot()
        return JSONObject()
            .put("policy", "OBSERVE_ONLY_BEFORE_DURING_AFTER")
            .put("capacityPerBucket", capacityPerBucket)
            .put("baseline", stats(snapshot.baseline))
            .put("plannedWork", stats(snapshot.plannedWork))
            .put("recovery", stats(snapshot.recovery))
    }

    private fun addBounded(target: ArrayDeque<Long>, value: Long) {
        target.addLast(value)
        while (target.size > capacityPerBucket) target.removeFirst()
    }

    private fun stats(values: List<Long>): JSONObject {
        if (values.isEmpty()) return JSONObject()
            .put("count", 0)
            .put("samplesMs", JSONArray())
        val sorted = values.sorted()
        fun percentile(fraction: Double): Long {
            val index = ((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }
        return JSONObject()
            .put("count", values.size)
            .put("minMs", sorted.first())
            .put("medianMs", percentile(0.50))
            .put("p95Ms", percentile(0.95))
            .put("maxMs", sorted.last())
            .put("meanMs", values.average())
            .put("samplesMs", JSONArray(values))
    }
}
