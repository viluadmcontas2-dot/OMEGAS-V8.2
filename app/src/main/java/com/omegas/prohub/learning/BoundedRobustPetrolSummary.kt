package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Resumo robusto e estritamente bounded do tempo de gasolina de uma região.
 *
 * O agregado histórico (média/segundo momento) continua existindo para
 * compatibilidade. Este objeto guarda somente uma pequena cauda recente para
 * mediana/MAD e nunca pode crescer com a duração da sessão.
 */
internal class BoundedRobustPetrolSummary private constructor(
    private val retained: ArrayDeque<Double>,
    var totalObserved: Long,
        private set,
    private var seedPendingFirstObserve: Boolean = false,
) {
    companion object {
        /** Resource budget; não é limiar físico nem número OEM. */
        const val MAX_RETAINED_SAMPLES = 31

        fun empty(): BoundedRobustPetrolSummary =
            BoundedRobustPetrolSummary(ArrayDeque(), 0L)

        /**
         * Pré-carrega o valor usado por LearningRegion.fromSample. O mesmo sample
         * é entregue em seguida a update(); a primeira observe confirma o seed
         * sem duplicá-lo nem aumentar o suporte duas vezes.
         */
        fun seed(value: Double): BoundedRobustPetrolSummary =
            if (value.isFinite()) {
                BoundedRobustPetrolSummary(ArrayDeque<Double>().apply { addLast(value) }, 1L, true)
            } else {
                empty()
            }

        fun fromJson(raw: JSONObject?, fallback: Double? = null): BoundedRobustPetrolSummary {
            if (raw == null) {
                return if (fallback?.isFinite() == true) {
                    BoundedRobustPetrolSummary(ArrayDeque<Double>().apply { addLast(fallback) }, 1L, false)
                } else {
                    empty()
                }
            }
            val values = raw.optJSONArray("retained") ?: JSONArray()
            val retained = ArrayDeque<Double>()
            repeat(values.length()) { index ->
                val value = values.optDouble(index, Double.NaN)
                if (value.isFinite()) {
                    retained.addLast(value)
                    while (retained.size > MAX_RETAINED_SAMPLES) retained.removeFirst()
                }
            }
            val observed = raw.optLong("total_observed", retained.size.toLong())
                .coerceAtLeast(retained.size.toLong())
            if (retained.isEmpty() && fallback?.isFinite() == true) retained.addLast(fallback)
            return BoundedRobustPetrolSummary(retained, observed.coerceAtLeast(retained.size.toLong()))
        }

        private fun saturatingAdd(left: Long, right: Long): Long = when {
            right <= 0L -> left
            left >= Long.MAX_VALUE - right -> Long.MAX_VALUE
            else -> left + right
        }
    }

    fun observe(value: Double) {
        if (!value.isFinite()) return
        if (seedPendingFirstObserve) {
            seedPendingFirstObserve = false
            if (retained.size == 1 && retained.last() == value) return
        }
        retained.addLast(value)
        while (retained.size > MAX_RETAINED_SAMPLES) retained.removeFirst()
        totalObserved = saturatingAdd(totalObserved, 1L)
    }

    fun merge(other: BoundedRobustPetrolSummary) {
        seedPendingFirstObserve = false
        other.retained.forEach(::observe)
        val historicalOnly = (other.totalObserved - other.retained.size).coerceAtLeast(0L)
        totalObserved = saturatingAdd(totalObserved, historicalOnly)
            .coerceAtLeast(retained.size.toLong())
    }

    fun retainedCount(): Int = retained.size

    fun median(fallback: Double = 0.0): Double = quantile(0.50) ?: fallback

    fun mad(): Double {
        if (retained.isEmpty()) return 0.0
        val center = median()
        return quantileOf(retained.map { abs(it - center) }, 0.50) ?: 0.0
    }

    fun interquartileRange(): Double {
        val q25 = quantile(0.25) ?: return 0.0
        val q75 = quantile(0.75) ?: return 0.0
        return (q75 - q25).coerceAtLeast(0.0)
    }

    fun copySummary(): BoundedRobustPetrolSummary =
        BoundedRobustPetrolSummary(ArrayDeque(retained), totalObserved, seedPendingFirstObserve)

    fun toJson(): JSONObject = JSONObject()
        .put("policy", "RECENT_ROBUST_BOUNDED")
        .put("max_retained_samples", MAX_RETAINED_SAMPLES)
        .put("retained", JSONArray(retained.toList()))
        .put("retained_count", retained.size)
        .put("total_observed", totalObserved)
        .put("median_ms", median())
        .put("mad_ms", mad())
        .put("iqr_ms", interquartileRange())

    private fun quantile(fraction: Double): Double? = quantileOf(retained.toList(), fraction)

    private fun quantileOf(values: List<Double>, fraction: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val position = (sorted.lastIndex * fraction.coerceIn(0.0, 1.0))
        val low = position.toInt()
        val high = kotlin.math.ceil(position).toInt()
        if (low == high) return sorted[low]
        val weight = position - low
        return sorted[low] * (1.0 - weight) + sorted[high] * weight
    }
}
