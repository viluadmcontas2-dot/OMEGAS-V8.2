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

    /** Relative-field support reuses the same geometry authority without naming delta* as K. */
    data class RelativeSupportPoint(
        val id: String,
        val rpm: Double,
        val petrolMs: Double,
        val deltaStar: Double,
        val quality: Double,
        val trajectoryId: String,
    ) {
        init {
            require(id.isNotBlank())
            require(rpm.isFinite() && petrolMs.isFinite() && deltaStar.isFinite())
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
    ): Result = evaluateValues(
        targetRpm = targetRpm,
        targetPetrolMs = targetPetrolMs,
        support = support.map { point ->
            ValueSupport(
                id = point.id,
                rpm = point.rpm,
                petrolMs = point.petrolMs,
                value = point.targetK,
                quality = point.quality,
                trajectoryId = point.trajectoryId,
            )
        },
        axes = historicalFixtureAxes(),
    )

    fun evaluateRelative(
        targetRpm: Double,
        targetPetrolMs: Double,
        support: List<RelativeSupportPoint>,
    ): Result = evaluateValues(
        targetRpm = targetRpm,
        targetPetrolMs = targetPetrolMs,
        support = support.map(::relativeValueSupport),
        axes = historicalFixtureAxes(),
    )

    /** Canonical typed Predictor path: geometry is supplied by Calibration Identity binding. */
    fun evaluateRelative(
        targetRpm: Double,
        targetPetrolMs: Double,
        support: List<RelativeSupportPoint>,
        rpmAxis: DoubleArray,
        petrolReferenceAxisMs: DoubleArray,
    ): Result = evaluateValues(
        targetRpm = targetRpm,
        targetPetrolMs = targetPetrolMs,
        support = support.map(::relativeValueSupport),
        axes = AxisGeometry.create(rpmAxis, petrolReferenceAxisMs),
    )

    private fun relativeValueSupport(point: RelativeSupportPoint): ValueSupport = ValueSupport(
        id = point.id,
        rpm = point.rpm,
        petrolMs = point.petrolMs,
        value = point.deltaStar,
        quality = point.quality,
        trajectoryId = point.trajectoryId,
    )

    private fun evaluateValues(
        targetRpm: Double,
        targetPetrolMs: Double,
        support: List<ValueSupport>,
        axes: AxisGeometry,
    ): Result {
        if (!targetRpm.isFinite() || !targetPetrolMs.isFinite()) return unsupported("INVALID_TARGET")
        val usable = support.filter {
            it.rpm.isFinite() && it.petrolMs.isFinite() && it.value.isFinite() && it.quality > 0.0
        }
        if (usable.isEmpty()) return unsupported("NO_SUPPORT")

        val target = normalized(targetRpm, targetPetrolMs, axes)
        val geometry = usable
            .map { it to normalized(it.rpm, it.petrolMs, axes) }
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
            val distance = distance(target, normalized(point.rpm, point.petrolMs, axes))
            val proximity = 1.0 / (1.0 + distance)
            WeightedValueSupport(point, distance, proximity, point.quality * proximity)
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

        val meanValue = weighted.sumOf { it.point.value * it.weight } / totalWeight
        val variance = weighted.sumOf { item ->
            val delta = item.point.value - meanValue
            delta * delta * item.weight
        } / totalWeight
        val standardDeviation = sqrt(variance.coerceAtLeast(0.0))
        val coherenceScale = max(abs(meanValue), 1.0)
        val coherenceScore = (1.0 / (1.0 + standardDeviation / coherenceScale)).coerceIn(0.0, 1.0)
        val independenceScore = (distinctTrajectories.toDouble() / usable.size.toDouble()).coerceIn(0.0, 1.0)
        val extrapolationPenalty = 1.0

        // A geometria pode reduzir a autoridade científica upstream, nunca elevá-la.
        val product = physicalDistanceScore * densityScore * qualityScore * coherenceScore * independenceScore
        val geometricConfidence = product.coerceIn(0.0, 1.0).pow(1.0 / 5.0)
        val confidence = minOf(geometricConfidence, qualityScore)
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

    /** Distância euclidiana histórica/legada após normalizar pelos spans da fixture. */
    fun physicalDistance(
        rpmA: Double,
        petrolMsA: Double,
        rpmB: Double,
        petrolMsB: Double,
    ): Double = physicalDistance(rpmA, petrolMsA, rpmB, petrolMsB, historicalFixtureAxes())

    /** Canonical typed Predictor distance using the current runtime geometry spans. */
    fun physicalDistance(
        rpmA: Double,
        petrolMsA: Double,
        rpmB: Double,
        petrolMsB: Double,
        rpmAxis: DoubleArray,
        petrolReferenceAxisMs: DoubleArray,
    ): Double = physicalDistance(
        rpmA,
        petrolMsA,
        rpmB,
        petrolMsB,
        AxisGeometry.create(rpmAxis, petrolReferenceAxisMs),
    )

    private fun physicalDistance(
        rpmA: Double,
        petrolMsA: Double,
        rpmB: Double,
        petrolMsB: Double,
        axes: AxisGeometry,
    ): Double = distance(
        normalized(rpmA, petrolMsA, axes),
        normalized(rpmB, petrolMsB, axes),
    )

    private fun historicalFixtureAxes(): AxisGeometry = AxisGeometry.create(
        KMapPhysicalAxes.rpmBins().map(Int::toDouble).toDoubleArray(),
        KMapPhysicalAxes.petrolBins(),
    )

    private fun normalized(rpm: Double, petrolMs: Double, axes: AxisGeometry): Point = Point(
        x = (rpm - axes.rpmMin) / axes.rpmSpan,
        y = (petrolMs - axes.petrolMin) / axes.petrolSpan,
    )

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

    private data class AxisGeometry(
        val rpmMin: Double,
        val rpmSpan: Double,
        val petrolMin: Double,
        val petrolSpan: Double,
    ) {
        companion object {
            fun create(rpmAxis: DoubleArray, petrolAxisMs: DoubleArray): AxisGeometry {
                require(rpmAxis.size == 12) { "Eixo RPM exige 12 pontos" }
                require(petrolAxisMs.size == 12) { "Eixo Tpet exige 12 pontos" }
                require(rpmAxis.all { it.isFinite() })
                require(petrolAxisMs.all { it.isFinite() })
                val rpmMin = rpmAxis.first()
                val rpmSpan = (rpmAxis.last() - rpmMin).coerceAtLeast(1.0)
                val petrolMin = petrolAxisMs.first()
                val petrolSpan = (petrolAxisMs.last() - petrolMin).coerceAtLeast(1e-9)
                return AxisGeometry(rpmMin, rpmSpan, petrolMin, petrolSpan)
            }
        }
    }

    private data class ValueSupport(
        val id: String,
        val rpm: Double,
        val petrolMs: Double,
        val value: Double,
        val quality: Double,
        val trajectoryId: String,
    )
    private data class WeightedValueSupport(
        val point: ValueSupport,
        val distance: Double,
        val proximity: Double,
        val weight: Double,
    )
}
