package com.omegas.prohub.learning

import com.omegas.prohub.calibration.KMapPhysicalAxes
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Confiança espacial pura do Predictor.
 *
 * Não existe raio fixo por quantidade de células nem threshold inventado.
 * Geometria é normalizada pelos eixos físicos RPM × Petrol Inj.; repetição da
 * mesma trajetória não infla densidade e extrapolação fora da malha cercada
 * permanece sem suporte para previsão.
 */
object PredictorSpatialConfidence {
    data class SupportPoint(
        val id: String,
        val rpm: Double,
        val petrolMs: Double,
        val targetK: Double,
        val quality: Double,
        val trajectoryId: String,
    ) {
        init {
            require(id.isNotBlank())
            require(rpm.isFinite() && petrolMs.isFinite() && targetK.isFinite())
            require(quality in 0.0..1.0)
            require(trajectoryId.isNotBlank())
        }
    }

    data class Result(
        val supported: Boolean,
        val confidence: Double,
        val physicalDistanceScore: Double,
        val densityScore: Double,
        val qualityScore: Double,
        val coherenceScore: Double,
        val independenceScore: Double,
        val extrapolationPenalty: Double,
        val supportCount: Int,
        val distinctTrajectories: Int,
        val reason: String,
    )

    fun evaluate(
        targetRpm: Double,
        targetPetrolMs: Double,
        support: List<SupportPoint>,
    ): Result {
        if (!targetRpm.isFinite() || !targetPetrolMs.isFinite()) return unsupported("INVALID_TARGET")
        val usable = support.filter {
            it.rpm.isFinite() && it.petrolMs.isFinite() && it.targetK.isFinite() && it.quality > 0.0
        }
        if (usable.isEmpty()) return unsupported("NO_SUPPORT")

        val target = normalized(targetRpm, targetPetrolMs)
        val geometry = usable
            .map { it to normalized(it.rpm, it.petrolMs) }
            .distinctBy { (_, point) -> quantizedGeometryKey(point) }
        if (geometry.size < 3) {
            return unsupported(
                reason = "INSUFFICIENT_GEOMETRIC_SUPPORT",
                supportCount = usable.size,
                distinctTrajectories = usable.map { it.trajectoryId }.distinct().size,
            )
        }

        val hull = convexHull(geometry.map { it.second })
        if (hull.size < 3 || !insideConvexPolygon(target, hull)) {
            return unsupported(
                reason = "EXTRAPOLATION_OUTSIDE_SUPPORT_HULL",
                supportCount = usable.size,
                distinctTrajectories = usable.map { it.trajectoryId }.distinct().size,
                extrapolationPenalty = 0.0,
            )
        }

        val distinctTrajectories = usable.map { it.trajectoryId }.distinct().size
        if (distinctTrajectories < 2) {
            return unsupported(
                reason = "INSUFFICIENT_TRAJECTORY_INDEPENDENCE",
                supportCount = usable.size,
                distinctTrajectories = distinctTrajectories,
            )
        }

        val weighted = usable.map { point ->
            val distance = distance(target, normalized(point.rpm, point.petrolMs))
            val proximity = 1.0 / (1.0 + distance)
            WeightedSupport(point, distance, proximity, point.quality * proximity)
        }
        val totalWeight = weighted.sumOf { it.weight }.coerceAtLeast(1e-12)
        val qualityScore = (weighted.sumOf { it.point.quality * it.proximity } /
            weighted.sumOf { it.proximity }.coerceAtLeast(1e-12)).coerceIn(0.0, 1.0)
        val physicalDistanceScore = weighted.maxOf { it.proximity }.coerceIn(0.0, 1.0)

        // Apenas a melhor contribuição de cada trajetória entra na densidade.
        // Rodar várias vezes pelo mesmo caminho não cria certeza artificial.
        val independentContribution = weighted
            .groupBy { it.point.trajectoryId }
            .values
            .sumOf { group -> group.maxOf { it.weight } }
        val densityScore = (1.0 - exp(-independentContribution)).coerceIn(0.0, 1.0)

        val meanK = weighted.sumOf { it.point.targetK * it.weight } / totalWeight
        val variance = weighted.sumOf { item ->
            val delta = item.point.targetK - meanK
            delta * delta * item.weight
        } / totalWeight
        val standardDeviation = sqrt(variance.coerceAtLeast(0.0))
        val coherenceScale = max(abs(meanK), 1.0)
        val coherenceScore = (1.0 / (1.0 + standardDeviation / coherenceScale)).coerceIn(0.0, 1.0)
        val independenceScore = (distinctTrajectories.toDouble() / usable.size.toDouble()).coerceIn(0.0, 1.0)
        val extrapolationPenalty = 1.0

        // Média geométrica: um fator fraco limita o resultado; nenhum fator pode
        // ser compensado indefinidamente por contagem de pontos.
        val product = physicalDistanceScore * densityScore * qualityScore * coherenceScore * independenceScore
        val confidence = product.coerceIn(0.0, 1.0).pow(1.0 / 5.0)
        return Result(
            supported = true,
            confidence = confidence.coerceIn(0.0, 1.0),
            physicalDistanceScore = physicalDistanceScore,
            densityScore = densityScore,
            qualityScore = qualityScore,
            coherenceScore = coherenceScore,
            independenceScore = independenceScore,
            extrapolationPenalty = extrapolationPenalty,
            supportCount = usable.size,
            distinctTrajectories = distinctTrajectories,
            reason = "SUPPORTED_INSIDE_PHYSICAL_HULL",
        )
    }

    /** Distância euclidiana após normalizar pelos spans físicos reais dos eixos. */
    fun physicalDistance(
        rpmA: Double,
        petrolMsA: Double,
        rpmB: Double,
        petrolMsB: Double,
    ): Double = distance(normalized(rpmA, petrolMsA), normalized(rpmB, petrolMsB))

    private fun normalized(rpm: Double, petrolMs: Double): Point {
        val rpmBins = KMapPhysicalAxes.rpmBins()
        val petrolBins = KMapPhysicalAxes.petrolBins()
        val rpmMin = rpmBins.first().toDouble()
        val rpmSpan = (rpmBins.last() - rpmBins.first()).toDouble().coerceAtLeast(1.0)
        val petrolMin = petrolBins.first()
        val petrolSpan = (petrolBins.last() - petrolBins.first()).coerceAtLeast(1e-9)
        return Point(
            x = (rpm - rpmMin) / rpmSpan,
            y = (petrolMs - petrolMin) / petrolSpan,
        )
    }

    private fun quantizedGeometryKey(point: Point): String =
        "${(point.x * 1_000_000.0).toLong()}:${(point.y * 1_000_000.0).toLong()}"

    private fun convexHull(points: List<Point>): List<Point> {
        val sorted = points.distinct().sortedWith(compareBy<Point> { it.x }.thenBy { it.y })
        if (sorted.size <= 2) return sorted
        val lower = mutableListOf<Point>()
        sorted.forEach { point ->
            while (lower.size >= 2 && cross(lower[lower.lastIndex - 1], lower.last(), point) <= 0.0) {
                lower.removeAt(lower.lastIndex)
            }
            lower += point
        }
        val upper = mutableListOf<Point>()
        sorted.asReversed().forEach { point ->
            while (upper.size >= 2 && cross(upper[upper.lastIndex - 1], upper.last(), point) <= 0.0) {
                upper.removeAt(upper.lastIndex)
            }
            upper += point
        }
        return (lower.dropLast(1) + upper.dropLast(1))
    }

    private fun insideConvexPolygon(target: Point, polygon: List<Point>): Boolean {
        if (polygon.size < 3) return false
        var sign = 0
        polygon.indices.forEach { index ->
            val a = polygon[index]
            val b = polygon[(index + 1) % polygon.size]
            val value = cross(a, b, target)
            if (abs(value) <= 1e-12) return@forEach
            val current = if (value > 0.0) 1 else -1
            if (sign == 0) sign = current else if (sign != current) return false
        }
        return true
    }

    private fun cross(a: Point, b: Point, c: Point): Double =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun unsupported(
        reason: String,
        supportCount: Int = 0,
        distinctTrajectories: Int = 0,
        extrapolationPenalty: Double = 0.0,
    ) = Result(
        supported = false,
        confidence = 0.0,
        physicalDistanceScore = 0.0,
        densityScore = 0.0,
        qualityScore = 0.0,
        coherenceScore = 0.0,
        independenceScore = 0.0,
        extrapolationPenalty = extrapolationPenalty,
        supportCount = supportCount,
        distinctTrajectories = distinctTrajectories,
        reason = reason,
    )

    private data class Point(val x: Double, val y: Double)
    private data class WeightedSupport(
        val point: SupportPoint,
        val distance: Double,
        val proximity: Double,
        val weight: Double,
    )
}
