package com.omegas.prohub.stats

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Estado estatístico combinável. A fusão usa Welford ponderado e não faz
 * "média das médias". O objeto é seguro para persistência, OBD e OMEGAS Link.
 */
data class WeightedStat(
    var effectiveWeight: Double = 0.0,
    var weightSqSum: Double = 0.0,
    var mean: Double = 0.0,
    var m2: Double = 0.0,
    var physicalSamples: Long = 0,
    var updates: Long = 0,
    var accepted: Long = 0,
    var rejected: Long = 0,
    var minimum: Double? = null,
    var maximum: Double? = null,
    var lastValue: Double? = null,
    var updatedAt: Long = 0L,
) {
    fun copyDeep(): WeightedStat = copy()

    fun update(value: Double, weight: Double = 1.0, physical: Boolean = true, now: Long = System.currentTimeMillis()) {
        if (!value.isFinite() || !weight.isFinite() || weight <= 0.0) return
        val oldWeight = effectiveWeight
        val newWeight = oldWeight + weight
        val delta = value - mean
        val newMean = if (newWeight > 0.0) mean + (weight / newWeight) * delta else value
        m2 = max(0.0, m2 + weight * delta * (value - newMean))
        mean = newMean
        effectiveWeight = newWeight
        weightSqSum += weight * weight
        updates += 1
        accepted += 1
        if (physical) physicalSamples += 1
        minimum = minimum?.let { minOf(it, value) } ?: value
        maximum = maximum?.let { maxOf(it, value) } ?: value
        lastValue = value
        updatedAt = now
    }

    fun merge(other: WeightedStat): WeightedStat {
        if (other.effectiveWeight <= 0.0) return this
        if (effectiveWeight <= 0.0) {
            effectiveWeight = other.effectiveWeight
            weightSqSum = other.weightSqSum
            mean = other.mean
            m2 = other.m2
            physicalSamples = other.physicalSamples
            updates = other.updates
            accepted = other.accepted
            rejected = other.rejected
            minimum = other.minimum
            maximum = other.maximum
            lastValue = other.lastValue
            updatedAt = other.updatedAt
            return this
        }
        val w1 = effectiveWeight
        val w2 = other.effectiveWeight
        val total = w1 + w2
        val delta = other.mean - mean
        mean += delta * w2 / total
        m2 = max(0.0, m2 + other.m2 + delta * delta * w1 * w2 / total)
        effectiveWeight = total
        weightSqSum += other.weightSqSum
        physicalSamples += other.physicalSamples
        updates += other.updates
        accepted += other.accepted
        rejected += other.rejected
        minimum = listOfNotNull(minimum, other.minimum).minOrNull()
        maximum = listOfNotNull(maximum, other.maximum).maxOrNull()
        if (other.updatedAt >= updatedAt) lastValue = other.lastValue
        updatedAt = maxOf(updatedAt, other.updatedAt)
        return this
    }

    val variance: Double get() = if (effectiveWeight > 0.0) max(0.0, m2 / effectiveWeight) else 0.0
    val standardDeviation: Double get() = sqrt(variance)
    val effectiveSamples: Double get() = if (weightSqSum > 0.0) effectiveWeight * effectiveWeight / weightSqSum else 0.0

    fun toJson(): JSONObject = JSONObject()
        .put("effective_weight", effectiveWeight)
        .put("weight_sq_sum", weightSqSum)
        .put("mean", mean)
        .put("m2", m2)
        .put("physical_samples", physicalSamples)
        .put("updates", updates)
        .put("accepted", accepted)
        .put("rejected", rejected)
        .put("minimum", minimum ?: JSONObject.NULL)
        .put("maximum", maximum ?: JSONObject.NULL)
        .put("last_value", lastValue ?: JSONObject.NULL)
        .put("updated_at", updatedAt)

    companion object {
        fun fromJson(json: JSONObject?): WeightedStat {
            if (json == null) return WeightedStat()
            val weight = json.optDouble("effective_weight", json.optDouble("weight", 0.0))
            return WeightedStat(
                effectiveWeight = weight,
                weightSqSum = json.optDouble("weight_sq_sum", if (weight > 0.0) weight else 0.0),
                mean = json.optDouble("mean", json.optDouble("value", 0.0)),
                m2 = json.optDouble("m2", 0.0),
                physicalSamples = json.optLong("physical_samples", json.optLong("physical", 0L)),
                updates = json.optLong("updates", 0L),
                accepted = json.optLong("accepted", 0L),
                rejected = json.optLong("rejected", 0L),
                minimum = json.opt("minimum")?.takeUnless { it == JSONObject.NULL }?.toString()?.toDoubleOrNull(),
                maximum = json.opt("maximum")?.takeUnless { it == JSONObject.NULL }?.toString()?.toDoubleOrNull(),
                lastValue = json.opt("last_value")?.takeUnless { it == JSONObject.NULL }?.toString()?.toDoubleOrNull(),
                updatedAt = json.optLong("updated_at", 0L),
            )
        }
    }
}

