package com.omegas.prohub.autocal

/** Seleção determinística linear; independente das medianas congeladas do motor. */
internal object PresentationMedian {
    fun of(values: List<Double>): Double {
        require(values.isNotEmpty() && values.all { it.isFinite() })
        val mid = values.size / 2
        return if (values.size % 2 == 1) select(values, mid)
        else (select(values, mid - 1) + select(values, mid)) / 2.0
    }
    private fun select(values: List<Double>, rank: Int): Double {
        if (values.size <= 5) return values.sorted()[rank]
        val medians = values.chunked(5).map { group -> group.sorted()[group.size / 2] }
        val pivot = select(medians, medians.size / 2)
        val lower = ArrayList<Double>(); val upper = ArrayList<Double>(); var equal = 0
        values.forEach { if (it < pivot) lower.add(it) else if (it > pivot) upper.add(it) else equal++ }
        return when { rank < lower.size -> select(lower, rank)
            rank < lower.size + equal -> pivot
            else -> select(upper, rank - lower.size - equal) }
    }
}
