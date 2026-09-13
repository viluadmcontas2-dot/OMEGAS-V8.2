package com.omegas.prohub.learning

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

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

    /**
     * Ponto físico de uma superfície contínua arbitrária. `x` e `y` devem usar
     * a mesma normalização da vizinhança que selecionou a evidência.
     */
    data class SurfacePoint(
        val x: Double,
        val y: Double,
        val value: Double,
        val weight: Double = 1.0,
    )

    val defaultMapBins = doubleArrayOf(0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00)

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

    fun bilinearWeights(rpm: Double, petrolMs: Double): List<BilinearContribution> {
        val x = blend(LearningGridProjection.rpmBins.map(Int::toDouble).toDoubleArray(), rpm)
        val y = blend(LearningGridProjection.petrolBins, petrolMs)
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
     * Interpola apenas dentro do suporte geométrico observado.
     *
     * - pontos coincidentes são agregados pelo peso;
     * - um ponto exatamente observado domina naquele endereço;
     * - suporte 2D usa coordenadas baricêntricas dentro de um triângulo local;
     * - suporte 1D usa interpolação no segmento que realmente contém o alvo;
     * - fora do casco/segmento observado, abstém em vez de extrapolar.
     *
     * Isso permite uma superfície contínua sobre vizinhanças irregulares sem
     * transformar proximidade em autorização para extrapolação.
     */
    fun interpolateSupported2D(
        points: List<SurfacePoint>,
        targetX: Double = 0.0,
        targetY: Double = 0.0,
        epsilon: Double = 1e-9,
    ): Double? {
        if (!targetX.isFinite() || !targetY.isFinite() || epsilon <= 0.0) return null
        val nodes = mutableListOf<MutableSurfaceNode>()
        points.asSequence()
            .filter { it.x.isFinite() && it.y.isFinite() && it.value.isFinite() && it.weight.isFinite() && it.weight > 0.0 }
            .forEach { point ->
                val existing = nodes.firstOrNull {
                    abs(it.x - point.x) <= epsilon && abs(it.y - point.y) <= epsilon
                }
                if (existing == null) {
                    nodes += MutableSurfaceNode(
                        x = point.x,
                        y = point.y,
                        weightedValue = point.value * point.weight,
                        weight = point.weight,
                    )
                } else {
                    existing.weightedValue += point.value * point.weight
                    existing.weight += point.weight
                }
            }
        if (nodes.isEmpty()) return null

        val compact = nodes.map { node ->
            SurfaceNode(node.x, node.y, node.weightedValue / node.weight)
        }

        compact.minByOrNull { distance(it.x, it.y, targetX, targetY) }
            ?.takeIf { distance(it.x, it.y, targetX, targetY) <= epsilon }
            ?.let { return it.value }

        var bestTriangle: SurfaceEstimate? = null
        for (aIndex in 0 until compact.size - 2) {
            for (bIndex in aIndex + 1 until compact.size - 1) {
                for (cIndex in bIndex + 1 until compact.size) {
                    val a = compact[aIndex]
                    val b = compact[bIndex]
                    val c = compact[cIndex]
                    val denominator =
                        (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
                    if (abs(denominator) <= epsilon) continue

                    val wa = ((b.y - c.y) * (targetX - c.x) + (c.x - b.x) * (targetY - c.y)) / denominator
                    val wb = ((c.y - a.y) * (targetX - c.x) + (a.x - c.x) * (targetY - c.y)) / denominator
                    val wc = 1.0 - wa - wb
                    if (wa < -epsilon || wb < -epsilon || wc < -epsilon ||
                        wa > 1.0 + epsilon || wb > 1.0 + epsilon || wc > 1.0 + epsilon
                    ) continue

                    val distances = listOf(a, b, c).map { distance(it.x, it.y, targetX, targetY) }
                    val estimate = SurfaceEstimate(
                        value = wa * a.value + wb * b.value + wc * c.value,
                        radius = distances.maxOrNull() ?: Double.POSITIVE_INFINITY,
                        span = distances.sum(),
                    )
                    if (estimate.isBetterThan(bestTriangle, epsilon)) bestTriangle = estimate
                }
            }
        }
        bestTriangle?.let { return it.value }

        var bestSegment: SurfaceEstimate? = null
        for (aIndex in 0 until compact.size - 1) {
            for (bIndex in aIndex + 1 until compact.size) {
                val a = compact[aIndex]
                val b = compact[bIndex]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val lengthSquared = dx * dx + dy * dy
                if (lengthSquared <= epsilon * epsilon) continue

                val t = ((targetX - a.x) * dx + (targetY - a.y) * dy) / lengthSquared
                if (t < -epsilon || t > 1.0 + epsilon) continue

                val projectedX = a.x + t * dx
                val projectedY = a.y + t * dy
                if (distance(projectedX, projectedY, targetX, targetY) > epsilon) continue

                val estimate = SurfaceEstimate(
                    value = a.value + t * (b.value - a.value),
                    radius = maxOf(
                        distance(a.x, a.y, targetX, targetY),
                        distance(b.x, b.y, targetX, targetY),
                    ),
                    span = sqrt(lengthSquared),
                )
                if (estimate.isBetterThan(bestSegment, epsilon)) bestSegment = estimate
            }
        }
        return bestSegment?.value
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
        val x = blend(LearningGridProjection.rpmBins.map(Int::toDouble).toDoubleArray(), rpm)
        val y = blend(LearningGridProjection.petrolBins, petrolMs)
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

    private fun distance(x: Double, y: Double, targetX: Double, targetY: Double): Double {
        val dx = x - targetX
        val dy = y - targetY
        return sqrt(dx * dx + dy * dy)
    }

    private fun SurfaceEstimate.isBetterThan(other: SurfaceEstimate?, epsilon: Double): Boolean =
        other == null || radius < other.radius - epsilon ||
            (abs(radius - other.radius) <= epsilon && span < other.span)

    private data class MutableSurfaceNode(
        val x: Double,
        val y: Double,
        var weightedValue: Double,
        var weight: Double,
    )

    private data class SurfaceNode(
        val x: Double,
        val y: Double,
        val value: Double,
    )

    private data class SurfaceEstimate(
        val value: Double,
        val radius: Double,
        val span: Double,
    )
}

