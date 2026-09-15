package com.omegas.prohub.learning

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Fallback científico da referência de gasolina.
 *
 * O seletor físico existente continua autoritativo quando encontra suporte local.
 * Somente quando ele falha, esta camada consulta o prior F2 congelado e corrige
 * o prior com resíduos calculados exclusivamente a partir de regiões de gasolina
 * realmente observadas. A previsão nunca é devolvida como nova evidência.
 */
internal object AdaptivePetrolReference {
    private const val MIN_SUPPORT_REGIONS = 4
    private const val MAX_SUPPORT_REGIONS = 60
    private const val RPM_SCALE = 240.0
    private const val MAP_SCALE = 0.060
    private const val RIDGE = 0.001

    private const val DOMAIN_RPM_MIN = 800.0
    private const val DOMAIN_RPM_MAX = 3_200.0
    private const val DOMAIN_MAP_MIN = 0.15
    private const val DOMAIN_MAP_MAX = 0.95

    fun estimate(
        regions: List<PetrolReferenceSelector.Region>,
        request: PetrolReferenceSelector.Request,
        policy: LearningTolerancePolicy = LearningToleranceSettings.current,
    ): PetrolReferenceSelector.Result {
        val physical = PetrolReferenceSelector.estimate(regions, request, policy)
        if (physical.available) return physical
        if (!insideDomain(request.rpm, request.mapBar)) return physical

        val observed = regions.filter {
            it.rpm.isFinite() && it.mapBar.isFinite() && it.petrolMs.isFinite() &&
                it.petrolMs > 0.05 && insideDomain(it.rpm, it.mapBar)
        }
        if (observed.size < MIN_SUPPORT_REGIONS) return physical

        val scaleCandidates = observed.mapNotNull { region ->
            val prior = f2(region.rpm, region.mapBar)
            (region.petrolMs / prior)
                .takeIf { it.isFinite() && it in 0.60..1.60 }
        }
        if (scaleCandidates.size < MIN_SUPPORT_REGIONS) return physical
        val scale = median(scaleCandidates)

        val support = observed.map { region ->
            val prior = scale * f2(region.rpm, region.mapBar)
            val rpmUnits = (region.rpm - request.rpm) / RPM_SCALE
            val mapUnits = (region.mapBar - request.mapBar) / MAP_SCALE
            val distance = sqrt(rpmUnits * rpmUnits + mapUnits * mapUnits)
            ResidualPoint(
                region = region,
                rpmUnits = rpmUnits,
                mapUnits = mapUnits,
                distance = distance,
                residualMs = region.petrolMs - prior,
                baseWeight = region.confidence.coerceIn(0.05, 1.0) * exp(-0.5 * distance * distance),
            )
        }
            .sortedBy { it.distance }
            .take(MAX_SUPPORT_REGIONS)

        if (support.size < MIN_SUPPORT_REGIONS) return physical

        val weightedMean = weightedMeanResidual(support)
        val quadratic = if (support.size >= 6) localQuadraticResidual(support) else null
        val residualRange = support.map { it.residualMs }
        val residual = (quadratic ?: weightedMean)
            .coerceIn(residualRange.minOrNull() ?: weightedMean, residualRange.maxOrNull() ?: weightedMean)

        val priorAtRequest = scale * f2(request.rpm, request.mapBar)
        val target = priorAtRequest + residual
        if (!target.isFinite() || target <= 0.05) return physical

        val totalWeight = support.sumOf { it.baseWeight }.coerceAtLeast(1e-12)
        val meanConfidence = support.sumOf { it.region.confidence.coerceIn(0.05, 1.0) * it.baseWeight } / totalWeight
        val spread = sqrt(
            support.sumOf {
                val delta = it.residualMs - weightedMean
                delta * delta * it.baseWeight
            } / totalWeight,
        )
        val nearest = support.first()
        val densityFactor = (1.0 - exp(-support.size / 6.0)).coerceIn(0.0, 1.0)
        val distanceFactor = exp(-0.15 * nearest.distance).coerceIn(0.05, 1.0)
        val spreadFactor = exp(-spread / 0.50).coerceIn(0.10, 1.0)
        val quality = (meanConfidence * densityFactor * distanceFactor * spreadFactor * 0.75)
            .coerceIn(0.02, 0.75)

        return PetrolReferenceSelector.Result(
            available = true,
            reasonCode = "ADAPTIVE_PRIOR_RESIDUAL",
            message = "Referência contínua estimada pelo prior F2 corrigido somente por resíduos reais de gasolina.",
            petrolTargetMs = target,
            spreadMs = spread,
            quality = quality,
            regionIds = support.map { it.region.id },
            stage = "PRIOR_PLUS_RESIDUAL",
            extrapolated = true,
            totalPetrolRegions = observed.size,
            boundedCandidates = 0,
            directCandidates = 0,
            selectedCandidates = support.size,
            nearestDistance = nearest.distance,
            nearestRpmDelta = kotlin.math.abs(nearest.region.rpm - request.rpm),
            nearestMapDelta = kotlin.math.abs(nearest.region.mapBar - request.mapBar),
            nearestWaterDelta = null,
            temperatureCompared = false,
        )
    }

    /** Prior F2 congelado pela pesquisa offline; não é ajustado em runtime. */
    internal fun f2(rpm: Double, mapBar: Double): Double {
        val x = ln(mapBar / 0.40)
        val z = ln(rpm / 1_800.0)
        val x2 = x * x
        val z2 = z * z
        val polynomial =
            0.34083653 +
                1.87748601 * x +
                -0.66741389 * z +
                -0.54659976 * x2 +
                1.24856920 * z2 +
                -0.32307503 * x * z +
                1.27193350 * x2 * x +
                2.00071259 * z2 * z +
                0.47671656 * x2 * z +
                -0.25959427 * x * z2
        return 2.14396620 + exp(polynomial)
    }

    private fun insideDomain(rpm: Double, mapBar: Double): Boolean =
        rpm.isFinite() && mapBar.isFinite() &&
            rpm in DOMAIN_RPM_MIN..DOMAIN_RPM_MAX &&
            mapBar in DOMAIN_MAP_MIN..DOMAIN_MAP_MAX

    private fun weightedMeanResidual(points: List<ResidualPoint>): Double {
        val total = points.sumOf { it.baseWeight }.coerceAtLeast(1e-12)
        return points.sumOf { it.residualMs * it.baseWeight } / total
    }

    /**
     * Regressão quadrática local anisotrópica em coordenadas já normalizadas.
     * O valor consultado está na origem, logo somente o intercepto é necessário.
     * Ridge pequeno limita instabilidade e o resultado final ainda é limitado ao
     * envelope dos resíduos reais usados como suporte.
     */
    private fun localQuadraticResidual(points: List<ResidualPoint>): Double? {
        val matrix = Array(6) { DoubleArray(6) }
        val rhs = DoubleArray(6)
        points.forEach { point ->
            val r = point.rpmUnits
            val m = point.mapUnits
            val basis = doubleArrayOf(1.0, r, m, r * r, r * m, m * m)
            val weight = point.baseWeight.coerceAtLeast(1e-12)
            for (i in 0 until 6) {
                rhs[i] += basis[i] * point.residualMs * weight
                for (j in 0 until 6) matrix[i][j] += basis[i] * basis[j] * weight
            }
        }
        matrix[0][0] += RIDGE * 1e-6
        for (i in 1 until 6) matrix[i][i] += RIDGE
        return solve(matrix, rhs)?.firstOrNull()?.takeIf(Double::isFinite)
    }

    private fun solve(source: Array<DoubleArray>, sourceRhs: DoubleArray): DoubleArray? {
        val n = sourceRhs.size
        val a = Array(n) { row -> source[row].copyOf() }
        val b = sourceRhs.copyOf()
        for (pivot in 0 until n) {
            var best = pivot
            for (row in pivot + 1 until n) {
                if (kotlin.math.abs(a[row][pivot]) > kotlin.math.abs(a[best][pivot])) best = row
            }
            if (kotlin.math.abs(a[best][pivot]) < 1e-12) return null
            if (best != pivot) {
                val row = a[pivot]
                a[pivot] = a[best]
                a[best] = row
                val value = b[pivot]
                b[pivot] = b[best]
                b[best] = value
            }
            val divisor = a[pivot][pivot]
            for (column in pivot until n) a[pivot][column] /= divisor
            b[pivot] /= divisor
            for (row in 0 until n) {
                if (row == pivot) continue
                val factor = a[row][pivot]
                if (kotlin.math.abs(factor) < 1e-15) continue
                for (column in pivot until n) a[row][column] -= factor * a[pivot][column]
                b[row] -= factor * b[pivot]
            }
        }
        return b
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private data class ResidualPoint(
        val region: PetrolReferenceSelector.Region,
        val rpmUnits: Double,
        val mapUnits: Double,
        val distance: Double,
        val residualMs: Double,
        val baseWeight: Double,
    )
}
