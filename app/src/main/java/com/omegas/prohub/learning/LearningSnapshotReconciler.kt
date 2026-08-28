package com.omegas.prohub.learning

import com.omegas.prohub.ecu.Mp48Fuel
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.sqrt

/**
 * Reconcilia a memória persistida usada pela interface.
 *
 * A coleta continua permissiva: regiões são preservadas mesmo sem par. Este
 * componente apenas revisita GNV da época ativa quando uma superfície de
 * gasolina fisicamente compatível passa a existir.
 */
internal object LearningSnapshotReconciler {
    fun reconcile(snapshot: JSONObject): JSONObject {
        val root = JSONObject(snapshot.toString())
        val regions = root.optJSONArray("regions") ?: JSONArray()
        val epoch = root.optInt("epoch", 1).coerceAtLeast(1)
        val existing = root.optJSONArray("comparisons") ?: JSONArray()
        val output = JSONArray()
        val seen = linkedSetOf<String>()

        repeat(existing.length()) { index ->
            val item = existing.optJSONObject(index) ?: return@repeat
            val copy = JSONObject(item.toString())
            val key = existingComparisonKey(copy, index)
            if (seen.add(key)) {
                if (copy.optString("dedupe_key").isBlank()) copy.put("dedupe_key", key)
                output.put(copy)
            }
        }

        val petrol = buildList {
            repeat(regions.length()) { index ->
                val raw = regions.optJSONObject(index) ?: return@repeat
                if (!isFuel(raw, Mp48Fuel.PETROL)) return@repeat
                val petrolMs = raw.optDouble("petrol_ms", 0.0)
                if (!petrolMs.isFinite() || petrolMs <= 0.05) return@repeat
                add(
                    PetrolReferenceSelector.Region(
                        id = raw.optString("id", "petrol-$index"),
                        rpm = raw.optDouble("rpm", 0.0),
                        mapBar = raw.optDouble("map_bar", 0.0),
                        waterC = raw.optDouble("water_c", UNKNOWN_TEMPERATURE_C),
                        petrolMs = petrolMs,
                        confidence = raw.optDouble("confidence", raw.optDouble("quality", 0.25)).coerceIn(0.0, 1.0),
                        sampleCount = raw.optInt("samples", 1).coerceAtLeast(1),
                        updatedAtMs = raw.optLong("updated_at", 0L),
                        environment = PetrolReferenceEnvironmentBridge.region(
                            waterC = raw.optDouble("water_c", UNKNOWN_TEMPERATURE_C),
                            gasTemperatureC = raw.optDouble("gas_c", UNKNOWN_TEMPERATURE_C),
                            pressureDiffBar = raw.optDouble("pressure_diff_bar", Double.NaN),
                        ),
                    ),
                )
            }
        }

        var pending = 0
        var reconciled = 0
        val rejectionCounts = linkedMapOf<String, Int>()
        repeat(regions.length()) { index ->
            val cng = regions.optJSONObject(index) ?: return@repeat
            if (!isFuel(cng, Mp48Fuel.CNG) || cng.optInt("epoch", epoch) != epoch) return@repeat
            val visits = visitIds(cng, index)
            val requestEnvironment = PetrolReferenceEnvironmentBridge.request(
                waterC = cng.optDouble("water_c", UNKNOWN_TEMPERATURE_C),
                gasTemperatureC = cng.optDouble("gas_c", UNKNOWN_TEMPERATURE_C),
                pressureDiffBar = cng.optDouble("pressure_diff_bar", Double.NaN),
            )
            val result = PetrolReferenceSelector.estimate(
                regions = petrol,
                request = PetrolReferenceSelector.Request(
                    rpm = cng.optDouble("rpm", 0.0),
                    mapBar = cng.optDouble("map_bar", 0.0),
                    waterC = cng.optDouble("water_c", UNKNOWN_TEMPERATURE_C),
                    environment = requestEnvironment,
                ),
            )
            if (!result.available) {
                pending += visits.size
                rejectionCounts[result.reasonCode] = (rejectionCounts[result.reasonCode] ?: 0) + visits.size
                return@repeat
            }

            val petrolTarget = result.petrolTargetMs ?: return@repeat
            val petrolOnCng = cng.optDouble("petrol_ms", 0.0)
            if (!petrolOnCng.isFinite() || petrolOnCng <= 0.05) return@repeat
            visits.forEach { visitId ->
                val referenceIds = result.regionIds.sorted()
                val dedupe = "$epoch:RETROACTIVE_PERSISTED_SURFACE:$visitId:${referenceIds.joinToString(",")}" 
                if (!seen.add(dedupe)) return@forEach
                val comparison = comparisonJson(
                    cng = cng,
                    epoch = epoch,
                    visitId = visitId,
                    referenceIds = referenceIds,
                    dedupe = dedupe,
                    petrolTarget = petrolTarget,
                    petrolOnCng = petrolOnCng,
                    referenceQuality = result.quality,
                    referenceContexts = result.selectedRegionContexts,
                    requestEnvironment = result.requestEnvironment ?: requestEnvironment,
                    extrapolated = result.extrapolated,
                )
                if (comparison == null) {
                    pending += 1
                    rejectionCounts["INVALID_EQUIVALENCE"] = (rejectionCounts["INVALID_EQUIVALENCE"] ?: 0) + 1
                } else {
                    output.put(comparison)
                    reconciled += 1
                }
            }
        }

        return root
            .put("comparisons", output)
            .put(
                "reconciliation",
                JSONObject()
                    .put("source", "PERSISTED_REGIONS")
                    .put("petrol_regions", petrol.size)
                    .put("active_cng_regions", countActiveCng(regions, epoch))
                    .put("existing_comparisons", existing.length())
                    .put("preserved_existing_comparisons", output.length() - reconciled)
                    .put("reconciled_comparisons", reconciled)
                    .put("pending_cng_visits", pending)
                    .put("rejection_reasons", JSONObject(rejectionCounts as Map<*, *>))
                    .put("temperature_unknown_is_neutral", true),
            )
    }

    /**
     * Arquivos antigos usavam apenas visit_id. A chave abaixo é estável entre
     * leituras e não altera os valores científicos da comparação.
     */
    private fun existingComparisonKey(item: JSONObject, index: Int): String {
        item.optString("dedupe_key").takeIf(String::isNotBlank)?.let { return it }
        item.optString("id").takeIf(String::isNotBlank)?.let { return "EXISTING_ID:$it" }
        val canonical = listOf(
            item.optString("visit_id", item.optString("visitId")),
            item.optInt("epoch", 0).toString(),
            item.optDouble("petrol_target_ms", item.optDouble("petrolTargetMs", Double.NaN)).toString(),
            item.optDouble("petrol_on_cng_ms", item.optDouble("petrolOnCngMs", Double.NaN)).toString(),
            item.optDouble("rpm", Double.NaN).toString(),
            item.optDouble("map_bar", item.optDouble("mapBar", Double.NaN)).toString(),
            item.optString("origin"),
            index.toString(),
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return "EXISTING_LEGACY:$digest"
    }

    private fun comparisonJson(
        cng: JSONObject,
        epoch: Int,
        visitId: String,
        referenceIds: List<String>,
        dedupe: String,
        petrolTarget: Double,
        petrolOnCng: Double,
        referenceQuality: Double,
        referenceContexts: List<PetrolReferenceSelector.SelectedRegionContext>,
        requestEnvironment: PetrolReferenceSelector.EnvironmentalContext,
        extrapolated: Boolean,
    ): JSONObject? {
        val equivalence = FuelEquivalenceObjective.evaluate(
            referenceMs = petrolTarget,
            petrolOnCngMs = petrolOnCng,
            minimumReferenceMs = MotorLearningMemory.LEGACY_MIN_REFERENCE_MS,
            policy = LearningToleranceSettings.current,
        )
        if (!equivalence.valid) return null
        val difference = requireNotNull(equivalence.differenceMs)
        val errorRatio = requireNotNull(equivalence.errorRatio)
        val errorPct = requireNotNull(equivalence.errorPercent)
        val direction = when (equivalence.state) {
            FuelEquivalenceState.WITHIN_POLICY_DEADBAND -> "EQUIVALENT"
            FuelEquivalenceState.PETROL_ON_CNG_ABOVE_REFERENCE -> "INCREASE_CNG_DELIVERY"
            FuelEquivalenceState.PETROL_ON_CNG_BELOW_REFERENCE -> "DECREASE_CNG_DELIVERY"
            FuelEquivalenceState.INVALID -> return null
        }
        val rpm = cng.optDouble("rpm", 0.0)
        val map = cng.optDouble("map_bar", 0.0)
        val cngCell = LearningGridProjection.cellFor(rpm, petrolOnCng, map)
        val referenceCell = LearningGridProjection.cellFor(rpm, petrolTarget, map)
        val cngQuality = cng.optDouble("quality", cng.optDouble("confidence", 0.25)).coerceIn(0.0, 1.0)
        val quality = sqrt(referenceQuality.coerceIn(0.0, 1.0) * cngQuality).coerceIn(0.0, 1.0)
        val capturedAt = cng.optLong("updated_at", System.currentTimeMillis())
        val referenceUpdatedAt = referenceContexts.maxOfOrNull { it.updatedAtMs } ?: 0L
        return JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("dedupe_key", dedupe)
            .put("visit_id", visitId)
            .put("reference_region_id", referenceIds.joinToString(","))
            .put("reference_region_ids", JSONArray(referenceIds))
            .put("reference_updated_at_wall_ms", if (referenceUpdatedAt > 0L) referenceUpdatedAt else JSONObject.NULL)
            .put("reference_contexts", JSONArray(referenceContexts.map { it.toJson() }))
            .put("request_environment", requestEnvironment.toJson())
            .put("origin", "RETROACTIVE_PERSISTED_SURFACE")
            .put("captured_at", capturedAt)
            .put("comparison_captured_at_wall_ms", capturedAt)
            .put("timestamp_domains", JSONObject()
                .put("reference_updated_at_wall_ms", "wall_clock_ms")
                .put("comparison_captured_at_wall_ms", "wall_clock_ms"))
            .put("rpm", rpm)
            .put("map_bar", map)
            .put("water_c", cng.optDouble("water_c", UNKNOWN_TEMPERATURE_C))
            .put("gas_c", cng.optDouble("gas_c", UNKNOWN_TEMPERATURE_C))
            .put("pressure_diff_bar", cng.optDouble("pressure_diff_bar", 0.0))
            .put("cng_cell_row", cngCell.getInt("row"))
            .put("cng_cell_column", cngCell.getInt("column"))
            .put("reference_cell_row", referenceCell.getInt("row"))
            .put("reference_cell_column", referenceCell.getInt("column"))
            .put("continuous_cell_weights", cngCell.optJSONArray("continuousWeights") ?: JSONArray())
            .put("equivalence_valid", true)
            .put("equivalence_state", equivalence.state.name)
            .put("equivalence_reason_code", equivalence.reasonCode)
            .put("petrol_target_ms", petrolTarget)
            .put("reference_denominator_ms", petrolTarget)
            .put("petrol_on_cng_ms", petrolOnCng)
            .put("difference_ms", difference)
            .put("error_ratio", errorRatio)
            .put("error_pct", errorPct)
            .put("reference_unit", "ms")
            .put("observed_unit", "ms")
            .put("difference_unit", "ms")
            .put("error_ratio_unit", "ratio")
            .put("error_percent_unit", "percent")
            .put("direction", direction)
            .put("quality", quality)
            .put("reference_extrapolated", extrapolated)
            .put("epoch", epoch)
            .put("observation_count", 1)
    }

    private fun visitIds(region: JSONObject, index: Int): List<String> {
        val visits = region.optJSONArray("visits")
        return buildList {
            if (visits != null) repeat(visits.length()) {
                visits.optString(it).takeIf(String::isNotBlank)?.let(::add)
            }
            if (isEmpty()) add(region.optString("id", "cng-$index"))
        }.distinct()
    }

    private fun isFuel(region: JSONObject, fuel: Mp48Fuel): Boolean {
        val value = region.optString("fuel").uppercase()
        return when (fuel) {
            Mp48Fuel.PETROL -> value in setOf("PETROL", "GASOLINA")
            Mp48Fuel.CNG -> value in setOf("CNG", "GNV", "GAS")
            else -> false
        }
    }

    private fun countActiveCng(regions: JSONArray, epoch: Int): Int {
        var count = 0
        repeat(regions.length()) { index ->
            val region = regions.optJSONObject(index) ?: return@repeat
            if (isFuel(region, Mp48Fuel.CNG) && region.optInt("epoch", epoch) == epoch) count += 1
        }
        return count
    }

    private const val UNKNOWN_TEMPERATURE_C = -273.15
}
