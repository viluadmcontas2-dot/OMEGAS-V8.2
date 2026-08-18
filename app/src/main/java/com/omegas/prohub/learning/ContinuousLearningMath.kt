package com.omegas.prohub.learning

import kotlin.math.exp

/**
 * Regras matemáticas do aprendizado contínuo.
 *
 * Os pontos do mapa K são pontos de controle de uma superfície. Uma amostra
 * nunca é presa à célula mais próxima: ela é repartida entre os quatro pontos
 * que a cercam, proporcionalmente à distância real.
 */
object ContinuousLearningMath {
    data class AxisBlend(val lower: Int, val upper: Int, val fraction: Double)

    data class BilinearContribution(
        val row: Int,
        val column: Int,
        val weight: Double,
    )

    data class TrilinearContribution(
        val row: Int,
        val column: Int,
        val mapIndex: Int,
        val weight: Double,
    )

    private data class CurrentAxes(
        val rpm: DoubleArray,
        val petrolMs: DoubleArray,
    )

    val defaultMapBins = doubleArrayOf(0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00)

    private fun currentAxesOrFixture(): CurrentAxes? {
        val binding = LearningCalibrationAuthority.snapshot()
        if (binding != null && binding.geometryKnown()) {
            return CurrentAxes(
                rpm = binding.rpmAxis.map(Int::toDouble).toDoubleArray(),
                petrolMs = binding.petrolAxisMs.toDoubleArray(),
            )
        }
        if (LearningCalibrationAuthority.requiresKnownGeometry()) return null
        return CurrentAxes(
            rpm = LearningGridProjection.rpmBins.map(Int::toDouble).toDoubleArray(),
            petrolMs = LearningGridProjection.petrolBins,
        )
    }

    fun blend(values: DoubleArray, value: Double): AxisBlend {
        require(values.isNotEmpty()) { "Eixo sem pontos de controle" }
        if (values.size == 1) return AxisBlend(0, 0, 0.0)
        if (value <= values.first()) return AxisBlend(0, 0, 0.0)
        if (value >= values.last()) {
            val last = values.lastIndex
            return AxisBlend(last, last, 0.0)
        }
        val upper = values.indexOfFirst { it >= value }.coerceAtLeast(1)
        val lower = upper - 1
        val span = values[upper] - values[lower]
        val fraction = if (span <= 0.0) 0.0 else ((value - values[lower]) / span).coerceIn(0.0, 1.0)
        return AxisBlend(lower, upper, fraction)
    }

    /**
     * Todos os consumidores antigos deste overload também obedecem à geometria
     * atual. Durante sessão física sem geometry KNOWN, não existe peso por célula.
     */
    fun bilinearWeights(rpm: Double, petrolMs: Double): List<BilinearContribution> {
        val axes = currentAxesOrFixture() ?: return emptyList()
        return bilinearWeights(
            rpm = rpm,
            petrolMs = petrolMs,
            rpmAxis = axes.rpm,
            petrolAxisMs = axes.petrolMs,
        )
    }

    fun bilinearWeights(
        rpm: Double,
        petrolMs: Double,
        rpmAxis: DoubleArray,
        petrolAxisMs: DoubleArray,
    ): List<BilinearContribution> {
        require(rpmAxis.size == 12) { "Eixo RPM exige 12 pontos" }
        require(petrolAxisMs.size == 12) { "Eixo Tpet exige 12 pontos" }
        val x = blend(rpmAxis, rpm)
        val y = blend(petrolAxisMs, petrolMs)
        val candidates = listOf(
            BilinearContribution(y.lower, x.lower, (1.0 - x.fraction) * (1.0 - y.fraction)),
            BilinearContribution(y.lower, x.upper, x.fraction * (1.0 - y.fraction)),
            BilinearContribution(y.upper, x.lower, (1.0 - x.fraction) * y.fraction),
            BilinearContribution(y.upper, x.upper, x.fraction * y.fraction),
        )
        return candidates
            .filter { it.weight > 0.0 }
            .groupBy { it.row to it.column }
            .map { (_, values) -> values.reduce { a, b -> a.copy(weight = a.weight + b.weight) } }
            .map { it.copy(weight = it.weight.coerceIn(0.0, 1.0)) }
    }

    /**
     * Interpolação trilinear / multivariada 3D contínua para RPM, Petrol Inj Time e MAP.
     * Reparte a amostra continuamente entre os 8 pontos de controle do cubo 3D sem saltos discretos.
     */
    fun trilinearWeights(
        rpm: Double,
        petrolMs: Double,
        mapBar: Double,
        mapBins: DoubleArray = defaultMapBins,
    ): List<TrilinearContribution> {
        val axes = currentAxesOrFixture() ?: return emptyList()
        return trilinearWeights(
            rpm = rpm,
            petrolMs = petrolMs,
            mapBar = mapBar,
            rpmAxis = axes.rpm,
            petrolAxisMs = axes.petrolMs,
            mapBins = mapBins,
        )
    }

    fun trilinearWeights(
        rpm: Double,
        petrolMs: Double,
        mapBar: Double,
        rpmAxis: DoubleArray,
        petrolAxisMs: DoubleArray,
        mapBins: DoubleArray = defaultMapBins,
    ): List<TrilinearContribution> {
        require(rpmAxis.size == 12) { "Eixo RPM exige 12 pontos" }
        require(petrolAxisMs.size == 12) { "Eixo Tpet exige 12 pontos" }
        val x = blend(rpmAxis, rpm)
        val y = blend(petrolAxisMs, petrolMs)
        val z = blend(mapBins, mapBar)
        val candidates = listOf(
            TrilinearContribution(y.lower, x.lower, z.lower, (1.0 - x.fraction) * (1.0 - y.fraction) * (1.0 - z.fraction)),
            TrilinearContribution(y.lower, x.upper, z.lower, x.fraction * (1.0 - y.fraction) * (1.0 - z.fraction)),
            TrilinearContribution(y.upper, x.lower, z.lower, (1.0 - x.fraction) * y.fraction * (1.0 - z.fraction)),
            TrilinearContribution(y.upper, x.upper, z.lower, x.fraction * y.fraction * (1.0 - z.fraction)),
            TrilinearContribution(y.lower, x.lower, z.upper, (1.0 - x.fraction) * (1.0 - y.fraction) * z.fraction),
            TrilinearContribution(y.lower, x.upper, z.upper, x.fraction * (1.0 - y.fraction) * z.fraction),
            TrilinearContribution(y.upper, x.lower, z.upper, (1.0 - x.fraction) * y.fraction * z.fraction),
            TrilinearContribution(y.upper, x.upper, z.upper, x.fraction * y.fraction * z.fraction),
        )
        return candidates
            .filter { it.weight > 0.0 }
            .groupBy { Triple(it.row, it.column, it.mapIndex) }
            .map { (_, values) -> values.reduce { a, b -> a.copy(weight = a.weight + b.weight) } }
            .map { it.copy(weight = it.weight.coerceIn(0.0, 1.0)) }
    }

    /**
     * Interpolação contínua 3D de valores (RPM x Petrol Inj x MAP) em uma superfície sem descontinuidades.
     */
    fun interpolate3D(
        rpm: Double,
        petrolMs: Double,
        mapBar: Double,
        getValue: (row: Int, col: Int, mapIdx: Int) -> Double,
        mapBins: DoubleArray = defaultMapBins,
    ): Double {
        val weights = trilinearWeights(rpm, petrolMs, mapBar, mapBins)
        if (weights.isEmpty()) return 0.0
        val totalWeight = weights.sumOf { it.weight }
        if (totalWeight <= 0.0) return 0.0
        return weights.sumOf { getValue(it.row, it.column, it.mapIndex) * it.weight } / totalWeight
    }

    /** Peso de uma permanência: cresce no início e satura para evitar sobre-voto. */
    fun dwellWeight(durationMs: Long, timeConstantMs: Long = 2_000L): Double {
        if (durationMs <= 0L) return 0.0
        return (1.0 - exp(-durationMs.toDouble() / timeConstantMs.coerceAtLeast(1L))).coerceIn(0.0, 1.0)
    }

    fun effectiveSampleSize(weights: Iterable<Double>): Double {
        val positive = weights.filter { it > 0.0 }.toList()
        val sum = positive.sum()
        val squareSum = positive.sumOf { it * it }
        return if (squareSum <= 0.0) 0.0 else sum * sum / squareSum
    }

    fun weightedMean(values: Iterable<Pair<Double, Double>>): Double? {
        val valid = values.filter { it.second > 0.0 && it.first.isFinite() }.toList()
        val total = valid.sumOf { it.second }
        return if (total <= 0.0) null else valid.sumOf { it.first * it.second } / total
    }
}
