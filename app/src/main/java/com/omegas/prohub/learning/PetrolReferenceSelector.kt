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
 * RPM e MAP são sempre usados. Água participa somente quando conhecida nos dois
 * lados. Temperatura do gás e pressão são preservadas como contexto/proveniência,
 * mas não viram gate nativo nem peso científico por este owner.
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

    enum class ContextFreshness {
        CURRENT,
        OBSERVED,
        STALE,
        UNKNOWN,
    }

    enum class ContextKnownness {
        KNOWN,
        UNAVAILABLE,
        STALE,
        IMPLAUSIBLE,
    }

    data class EnvironmentalContext(
        val waterC: Double? = null,
        val waterFreshness: ContextFreshness = ContextFreshness.UNKNOWN,
        val waterSource: String = "UNKNOWN",
        val waterKnownness: ContextKnownness = temperatureKnownness(waterC, waterFreshness),
        val gasTemperatureC: Double? = null,
        val gasTemperatureFreshness: ContextFreshness = ContextFreshness.UNKNOWN,
        val gasTemperatureSource: String = "UNKNOWN",
        val gasTemperatureKnownness: ContextKnownness = finiteKnownness(gasTemperatureC, gasTemperatureFreshness),
        val pressureDiffBar: Double? = null,
        val gasPressureAbsBar: Double? = null,
        val pressureFreshness: ContextFreshness = ContextFreshness.UNKNOWN,
        val pressureSource: String = "UNKNOWN",
        val pressureKnownness: ContextKnownness = finiteKnownness(pressureDiffBar, pressureFreshness),
        val gasPressureAbsKnownness: ContextKnownness = finiteKnownness(gasPressureAbsBar, pressureFreshness),
        val mapSource: String = "MP48_RUNTIME",
    ) {
        fun waterKnown(): Boolean = waterKnownness == ContextKnownness.KNOWN
        fun gasTemperatureKnown(): Boolean = gasTemperatureKnownness == ContextKnownness.KNOWN
        fun pressureKnown(): Boolean = pressureKnownness == ContextKnownness.KNOWN
        fun gasPressureAbsoluteKnown(): Boolean = gasPressureAbsKnownness == ContextKnownness.KNOWN

        fun toJson(): JSONObject = JSONObject()
            .put("water_c", waterC ?: JSONObject.NULL)
            .put("water_known", waterKnown())
            .put("water_knownness", waterKnownness.name)
            .put("water_freshness", waterFreshness.name)
            .put("water_source", waterSource)
            .put("gas_temperature_c", gasTemperatureC ?: JSONObject.NULL)
            .put("gas_temperature_known", gasTemperatureKnown())
            .put("gas_temperature_knownness", gasTemperatureKnownness.name)
            .put("gas_temperature_freshness", gasTemperatureFreshness.name)
            .put("gas_temperature_source", gasTemperatureSource)
            .put("gas_temperature_role", "OMEGAS_CONTEXT_ONLY")
            .put("pressure_diff_bar", pressureDiffBar ?: JSONObject.NULL)
            .put("gas_pressure_abs_bar", gasPressureAbsBar ?: JSONObject.NULL)
            .put("pressure_known", pressureKnown())
            .put("pressure_knownness", pressureKnownness.name)
            .put("gas_pressure_abs_known", gasPressureAbsoluteKnown())
            .put("gas_pressure_abs_knownness", gasPressureAbsKnownness.name)
            .put("pressure_freshness", pressureFreshness.name)
            .put("pressure_source", pressureSource)
            .put("pressure_role", "CONTEXT_WITH_NATIVE_PRESSURE_MAP_EVIDENCE")
            .put("map_source", mapSource)
    }

    data class Region(
        val id: String,
        val rpm: Double,
        val mapBar: Double,
        val waterC: Double,
        val petrolMs: Double,
        val confidence: Double,
        val sampleCount: Int,
        val updatedAtMs: Long = 0L,
        val environment: EnvironmentalContext = EnvironmentalContext(
            waterC = waterC,
            waterFreshness = if (knownTemperature(waterC)) ContextFreshness.OBSERVED else ContextFreshness.UNKNOWN,
            waterSource = if (knownTemperature(waterC)) "LANDI_ECU_REGION" else "UNKNOWN",
        ),
    )

    data class Request(
        val rpm: Double,
        val mapBar: Double,
        val waterC: Double,
        val environment: EnvironmentalContext = EnvironmentalContext(
            waterC = waterC,
            waterFreshness = if (knownTemperature(waterC)) ContextFreshness.CURRENT else ContextFreshness.UNKNOWN,
            waterSource = if (knownTemperature(waterC)) "LANDI_ECU_CURRENT" else "UNKNOWN",
        ),
    )

    data class SelectedRegionContext(
        val regionId: String,
        val updatedAtMs: Long,
        val environment: EnvironmentalContext,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("region_id", regionId)
            .put("updated_at_ms", updatedAtMs)
            .put("environment", environment.toJson())
    }

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
        val requestEnvironment: EnvironmentalContext? = null,
        val selectedRegionContexts: List<SelectedRegionContext> = emptyList(),
        val referenceAvailability: ReferenceAvailabilityMetric = PetrolReferenceAvailability.record(available, reasonCode),
    ) {
        fun selectionReason(): LearningSelectionReason = LearningSelectionReason.fromReference(
            available = available,
            detailReasonCode = reasonCode,
            geometryKnown = LearningGridProjection.gridJson().optBoolean("geometryKnown", false),
        )

        fun toJson(): JSONObject = JSONObject()
            .put("available", available)
            .put("reason_code", reasonCode)
            .put("detail_reason_code", reasonCode)
            .put("selection_reason_code", selectionReason().name)
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
            .put("request_environment", requestEnvironment?.toJson() ?: JSONObject.NULL)
            .put("selected_region_contexts", JSONArray(selectedRegionContexts.map { it.toJson() }))
            .put("reference_wait_state", referenceAvailability.state)
            .put("time_to_reference_ms", referenceAvailability.timeToReferenceMs)
            .put("reference_block_reason", referenceAvailability.blockReason ?: JSONObject.NULL)
            .put("reference_timing_origin", referenceAvailability.measurementOrigin)
            .put("gas_temperature_used_as_native_gate", false)
            .put("pressure_used_as_native_gate", false)
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
            return Result(
                false,
                "NO_PETROL_REGIONS",
                "Nenhuma referência física de gasolina foi armazenada.",
                requestEnvironment = request.environment,
            )
        }
        if (!request.rpm.isFinite() || !request.mapBar.isFinite() || request.rpm < 0.0 || request.mapBar < 0.0) {
            return Result(
                available = false,
                reasonCode = "INVALID_CNG_CONDITION",
                message = "A condição GNV atual não possui RPM e MAP válidos.",
                totalPetrolRegions = validRegions.size,
                requestEnvironment = request.environment,
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
                requestEnvironment = request.environment,
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
                requestEnvironment = request.environment,
                selectedRegionContexts = selected.map { SelectedRegionContext(it.region.id, it.region.updatedAtMs, it.region.environment) },
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
                    requestEnvironment = request.environment,
                    selectedRegionContexts = listOf(SelectedRegionContext(closest.region.id, closest.region.updatedAtMs, closest.region.environment)),
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
                requestEnvironment = request.environment,
                selectedRegionContexts = selected.map { SelectedRegionContext(it.region.id, it.region.updatedAtMs, it.region.environment) },
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
            requestEnvironment = request.environment,
            selectedRegionContexts = selected.map { SelectedRegionContext(it.region.id, it.region.updatedAtMs, it.region.environment) },
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
        val regionWater = region.environment.waterC.takeIf { region.environment.waterKnown() }
        val requestWater = request.environment.waterC.takeIf { request.environment.waterKnown() }
        val compareTemperature = regionWater != null && requestWater != null
        val waterDelta = if (compareTemperature) abs(regionWater!! - requestWater!!) else 0.0
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

    private fun temperatureKnownness(value: Double?, freshness: ContextFreshness): ContextKnownness = when {
        value == null -> ContextKnownness.UNAVAILABLE
        !knownTemperature(value) -> ContextKnownness.IMPLAUSIBLE
        freshness == ContextFreshness.STALE -> ContextKnownness.STALE
        freshness == ContextFreshness.CURRENT || freshness == ContextFreshness.OBSERVED -> ContextKnownness.KNOWN
        else -> ContextKnownness.UNAVAILABLE
    }

    private fun finiteKnownness(value: Double?, freshness: ContextFreshness): ContextKnownness = when {
        value == null -> ContextKnownness.UNAVAILABLE
        !value.isFinite() -> ContextKnownness.IMPLAUSIBLE
        freshness == ContextFreshness.STALE -> ContextKnownness.STALE
        freshness == ContextFreshness.CURRENT || freshness == ContextFreshness.OBSERVED -> ContextKnownness.KNOWN
        else -> ContextKnownness.UNAVAILABLE
    }

    private fun knownTemperature(value: Double?): Boolean =
        value != null && value.isFinite() && value != UNKNOWN_TEMPERATURE_C && value > MIN_REALISTIC_WATER_C

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
