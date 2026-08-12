package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Seleciona a referência de gasolina pela condição física do motor.
 *
 * RPM e MAP são sempre usados. Temperatura participa somente quando existe dos
 * dois lados; o sentinela -273,15 significa "não disponível" e nunca pode
 * eliminar uma equivalência fisicamente válida. O tempo de injeção é comparado
 * apenas depois que a vizinhança física foi escolhida.
 */
internal object PetrolReferenceSelector {
    private const val MAX_NEIGHBORS = 4
    private const val DIRECT_DISTANCE_WINDOW = 0.75
    private const val EXTRAPOLATION_DISTANCE_WINDOW = 0.50
    private const val MAX_EXTRAPOLATION_RPM_UNITS = 3.00
    private const val MAX_EXTRAPOLATION_MAP_UNITS = 2.50
    private const val MAX_EXTRAPOLATION_WATER_UNITS = 2.00
    private const val HARD_DIRECT_SPREAD_MULTIPLIER = 2.50
    private const val UNKNOWN_TEMPERATURE_C = -273.15
    private const val MIN_REALISTIC_WATER_C = -80.0

    data class Region(
        val id: String,
        val rpm: Double,
        val mapBar: Double,
        val waterC: Double,
        val petrolMs: Double,
        val confidence: Double,
        val sampleCount: Int,
    )

    data class Request(
        val rpm: Double,
        val mapBar: Double,
        val waterC: Double,
    )

    data class Result(
        val available: Boolean,
        val reasonCode: String,
        val message: String,
        val petrolTargetMs: Double? = null,
        val spreadMs: Double? = null,
        val quality: Double = 0.0,
        val regionIds: List<String> = emptyList(),
        val stage: String = "OBSERVED",
        val extrapolated: Boolean = false,
        val totalPetrolRegions: Int = 0,
        val boundedCandidates: Int = 0,
        val directCandidates: Int = 0,
        val selectedCandidates: Int = 0,
        val nearestDistance: Double? = null,
        val nearestRpmDelta: Double? = null,
        val nearestMapDelta: Double? = null,
        val nearestWaterDelta: Double? = null,
        val temperatureCompared: Boolean = false,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("available", available)
            .put("reason_code", reasonCode)
            .put("message", message)
            .put("petrol_target_ms", petrolTargetMs ?: JSONObject.NULL)
            .put("spread_ms", spreadMs ?: JSONObject.NULL)
            .put("quality", quality)
            .put("region_ids", JSONArray(regionIds))
            .put("stage", stage)
            .put("extrapolated", extrapolated)
            .put("total_petrol_regions", totalPetrolRegions)
            .put("bounded_candidates", boundedCandidates)
            .put("direct_candidates", directCandidates)
            .put("selected_candidates", selectedCandidates)
            .put("nearest_distance", nearestDistance ?: JSONObject.NULL)
            .put("nearest_rpm_delta", nearestRpmDelta ?: JSONObject.NULL)
            .put("nearest_map_delta", nearestMapDelta ?: JSONObject.NULL)
            .put("nearest_water_delta", nearestWaterDelta ?: JSONObject.NULL)
            .put("temperature_compared", temperatureCompared)
            .put("method", "LOCAL_RPM_MAP_OPTIONAL_WATER_NEIGHBORHOOD")
    }

    fun estimate(
        regions: List<Region>,
        request: Request,
        policy: LearningTolerancePolicy = LearningToleranceSettings.current,
    ): Result {
        val validRegions = regions.filter {
            it.rpm.isFinite() && it.mapBar.isFinite() && it.petrolMs.isFinite() &&
                it.petrolMs > 0.05 && it.rpm >= 0.0 && it.mapBar >= 0.0
        }
        if (validRegions.isEmpty()) {
            return Result(false, "NO_PETROL_REGIONS", "Nenhuma referência física de gasolina foi armazenada.")
        }
        if (!request.rpm.isFinite() || !request.mapBar.isFinite() || request.rpm < 0.0 || request.mapBar < 0.0) {
            return Result(
                available = false,
                reasonCode = "INVALID_CNG_CONDITION",
                message = "A condição GNV atual não possui RPM e MAP válidos.",
                totalPetrolRegions = validRegions.size,
            )
        }

        val candidates = validRegions.map { candidate(it, request, policy) }
        val bounded = candidates.filter {
            it.rpmUnits <= MAX_EXTRAPOLATION_RPM_UNITS &&
                it.mapUnits <= MAX_EXTRAPOLATION_MAP_UNITS &&
                (!it.temperatureCompared || it.waterUnits <= MAX_EXTRAPOLATION_WATER_UNITS)
        }
        val nearest = candidates.minByOrNull { it.distance }
        if (bounded.isEmpty()) {
            return Result(
                available = false,
                reasonCode = "NO_LOCAL_PETROL_REFERENCE",
                message = "Há gasolina aprendida, mas nenhuma região está próxima desta condição de RPM e MAP.",
                totalPetrolRegions = validRegions.size,
                nearestDistance = nearest?.distance,
                nearestRpmDelta = nearest?.rpmDelta,
                nearestMapDelta = nearest?.mapDelta,
                nearestWaterDelta = nearest?.waterDelta,
                temperatureCompared = nearest?.temperatureCompared == true,
            )
        }

        val direct = bounded.filter {
            it.rpmUnits <= 1.0 && it.mapUnits <= 1.0 && (!it.temperatureCompared || it.waterUnits <= 1.0)
        }
        val extrapolated = direct.isEmpty()
        val source = if (direct.isNotEmpty()) direct else bounded
        val closest = source.minByOrNull { it.distance }!!
        val cutoff = closest.distance + if (extrapolated) EXTRAPOLATION_DISTANCE_WINDOW else DIRECT_DISTANCE_WINDOW
        val selected = source.asSequence()
            .filter { it.distance <= cutoff }
            .sortedBy { it.distance }
            .take(MAX_NEIGHBORS)
            .toList()
            .ifEmpty { listOf(closest) }

        val weighted = selected.map { item ->
            val confidence = item.region.confidence.coerceIn(0.05, 1.0)
            val kernel = exp(-0.5 * item.distance * item.distance)
            val inverseDistance = 1.0 / (0.15 + item.distance)
            item to (confidence * kernel * inverseDistance).coerceAtLeast(1e-9)
        }
        val totalWeight = weighted.sumOf { it.second }
        if (!totalWeight.isFinite() || totalWeight <= 0.0) {
            return Result(
                false,
                "REFERENCE_WEIGHT_INVALID",
                "As referências próximas não produziram peso numérico válido.",
                totalPetrolRegions = validRegions.size,
                boundedCandidates = bounded.size,
                directCandidates = direct.size,
                selectedCandidates = selected.size,
                nearestDistance = closest.distance,
                nearestRpmDelta = closest.rpmDelta,
                nearestMapDelta = closest.mapDelta,
                nearestWaterDelta = closest.waterDelta,
                temperatureCompared = closest.temperatureCompared,
            )
        }

        val target = weighted.sumOf { it.first.region.petrolMs * it.second } / totalWeight
        val spread = sqrt(weighted.sumOf {
            val delta = it.first.region.petrolMs - target
            delta * delta * it.second
        } / totalWeight)
        val nearestDominance = weighted.firstOrNull { it.first === closest }?.second?.div(totalWeight) ?: 0.0
        val spreadLimit = policy.referenceMaximumSpreadMs.coerceAtLeast(0.05)
        val hardSpreadLimit = if (extrapolated) spreadLimit else spreadLimit * HARD_DIRECT_SPREAD_MULTIPLIER
        if (spread > hardSpreadLimit && nearestDominance < 0.60) {
            if (closest.distance <= DIRECT_DISTANCE_WINDOW) {
                val distanceQuality = exp(-0.35 * closest.distance).coerceIn(0.10, 1.0)
                val quality = (closest.region.confidence.coerceIn(0.05, 1.0) * distanceQuality).coerceIn(0.0, 1.0)
                val density = closest.region.sampleCount.coerceAtLeast(1).toDouble()
                return Result(
                    available = true,
                    reasonCode = "NEAREST_LOCAL_REFERENCE",
                    message = "Referências vizinhas divergiram; foi preservada a referência física local mais próxima.",
                    petrolTargetMs = closest.region.petrolMs,
                    spreadMs = 0.0,
                    quality = quality,
                    regionIds = listOf(closest.region.id),
                    stage = confidenceStage(density, 0.0, policy),
                    extrapolated = false,
                    totalPetrolRegions = validRegions.size,
                    boundedCandidates = bounded.size,
                    directCandidates = direct.size,
                    selectedCandidates = 1,
                    nearestDistance = closest.distance,
                    nearestRpmDelta = closest.rpmDelta,
                    nearestMapDelta = closest.mapDelta,
                    nearestWaterDelta = closest.waterDelta,
                    temperatureCompared = closest.temperatureCompared,
                )
            }
            return Result(
                false,
                "REFERENCE_SPREAD_EXCEEDED",
                "As referências próximas de gasolina divergem ${"%.2f".format(spread)} ms; limite ${"%.2f".format(hardSpreadLimit)} ms.",
                spreadMs = spread,
                totalPetrolRegions = validRegions.size,
                boundedCandidates = bounded.size,
                directCandidates = direct.size,
                selectedCandidates = selected.size,
                nearestDistance = closest.distance,
                nearestRpmDelta = closest.rpmDelta,
                nearestMapDelta = closest.mapDelta,
                nearestWaterDelta = closest.waterDelta,
                temperatureCompared = closest.temperatureCompared,
            )
        }

        val meanConfidence = weighted.sumOf { it.first.region.confidence.coerceIn(0.05, 1.0) * it.second } / totalWeight
        val spreadQuality = exp(-spread / spreadLimit).coerceIn(0.08, 1.0)
        val distanceQuality = exp(-0.35 * closest.distance).coerceIn(0.10, 1.0)
        val extrapolationFactor = if (extrapolated) 0.35 else 1.0
        val quality = (meanConfidence * spreadQuality * distanceQuality * extrapolationFactor).coerceIn(0.0, 1.0)
        val density = selected.sumOf { it.region.sampleCount.coerceAtLeast(1) }.toDouble()
        val stage = confidenceStage(density, spread * spread, policy)
        return Result(
            available = true,
            reasonCode = if (extrapolated) "BOUNDED_EXTRAPOLATION" else "LOCAL_REFERENCE_AVAILABLE",
            message = if (extrapolated) {
                "Referência equivalente calculada por extrapolação limitada de RPM e MAP."
            } else {
                "Referência equivalente calculada pela vizinhança física local."
            },
            petrolTargetMs = target,
            spreadMs = spread,
            quality = quality,
            regionIds = selected.map { it.region.id },
            stage = stage,
            extrapolated = extrapolated,
            totalPetrolRegions = validRegions.size,
            boundedCandidates = bounded.size,
            directCandidates = direct.size,
            selectedCandidates = selected.size,
            nearestDistance = closest.distance,
            nearestRpmDelta = closest.rpmDelta,
            nearestMapDelta = closest.mapDelta,
            nearestWaterDelta = closest.waterDelta,
            temperatureCompared = closest.temperatureCompared,
        )
    }

    private fun candidate(region: Region, request: Request, policy: LearningTolerancePolicy): Candidate {
        val rpmLimit = max(
            policy.historicalRpmMinimum,
            max(abs(region.rpm), abs(request.rpm)) * policy.historicalRpmPercent / 100.0,
        ).coerceAtLeast(1.0)
        val mapLimit = policy.historicalMapBar.coerceAtLeast(0.001)
        val rpmDelta = abs(region.rpm - request.rpm)
        val mapDelta = abs(region.mapBar - request.mapBar)
        val rpmUnits = rpmDelta / rpmLimit
        val mapUnits = mapDelta / mapLimit
        val compareTemperature = knownTemperature(region.waterC) && knownTemperature(request.waterC)
        val waterDelta = if (compareTemperature) abs(region.waterC - request.waterC) else 0.0
        val waterUnits = if (compareTemperature) {
            waterDelta / policy.historicalTemperatureC.coerceAtLeast(1.0)
        } else 0.0
        val distance = sqrt(rpmUnits * rpmUnits + mapUnits * mapUnits + 0.25 * waterUnits * waterUnits)
        return Candidate(
            region,
            rpmDelta,
            mapDelta,
            waterDelta,
            rpmUnits,
            mapUnits,
            waterUnits,
            distance,
            compareTemperature,
        )
    }

    private fun knownTemperature(value: Double): Boolean =
        value.isFinite() && value != UNKNOWN_TEMPERATURE_C && value > MIN_REALISTIC_WATER_C

    private fun confidenceStage(density: Double, variance: Double, policy: LearningTolerancePolicy): String = when {
        density >= policy.confidenceSampleTarget * 0.8 && variance < policy.referenceMaximumSpreadMs * policy.referenceMaximumSpreadMs * 0.5 -> "CONFIRMED"
        density >= policy.confidenceSampleTarget * 0.5 && variance < policy.referenceMaximumSpreadMs * policy.referenceMaximumSpreadMs -> "ACCEPTED"
        density >= policy.confidenceSampleTarget * 0.2 -> "PROVISIONAL"
        else -> "OBSERVED"
    }

    private data class Candidate(
        val region: Region,
        val rpmDelta: Double,
        val mapDelta: Double,
        val waterDelta: Double,
        val rpmUnits: Double,
        val mapUnits: Double,
        val waterUnits: Double,
        val distance: Double,
        val temperatureCompared: Boolean,
    )
}
