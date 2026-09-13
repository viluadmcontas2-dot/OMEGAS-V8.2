package com.omegas.prohub.telemetry

import android.content.Context
import org.json.JSONObject

/**
 * Persists user-facing GNV consumption state while delegating the evidence math
 * to ConsumptionEvidenceEngine. Pressure is explicitly an estimate; confirmed
 * economy only comes from distance / added volume at a refuel.
 */
class ConsumptionTracker(context: Context) {
    private val prefs = context.getSharedPreferences("gnv_consumption", Context.MODE_PRIVATE)
    private val engine = ConsumptionEvidenceEngine()

    private var remainingM3 = prefs.getFloat("remaining_m3", 0f).toDouble()
    private var refuelDetected = prefs.getBoolean("refuel_detected", false)
    private var preRefuelPressure = prefs.getInt("pre_refuel_pressure", -1)
    private var currentFilteredPressure = prefs.getFloat("filtered_pressure", -1f).toDouble()
    private var lastConfirmedKmPerM3 = prefs.getFloat("last_confirmed_km_per_m3", Float.NaN).toDouble()
    private var lastConfirmedAtMs = prefs.getLong("last_confirmed_at_ms", -1L)
    private var lastTelemetryTimestampMs = prefs.getLong("last_telemetry_timestamp_ms", -1L)
    private var lastEstimate: ConsumptionEvidence? = null

    fun update(timestampMs: Long, rawPressure: Int) {
        if (rawPressure !in 0..255 || timestampMs < 0L) return
        val normalized = (255 - rawPressure).coerceIn(0, 255).toDouble()
        currentFilteredPressure = if (currentFilteredPressure < 0.0) {
            normalized
        } else {
            currentFilteredPressure * 0.82 + normalized * 0.18
        }
        val monotonicTimestamp = timestampMs >= lastTelemetryTimestampMs
        if (!monotonicTimestamp) return
        lastTelemetryTimestampMs = timestampMs

        val capacity = prefs.getFloat("capacity_m3", 0f).toDouble()
        if (capacity > 0.0) {
            val estimate = engine.estimateFromPressure(timestampMs, currentFilteredPressure, capacity)
            if (estimate.accepted) {
                lastEstimate = estimate
                remainingM3 = estimate.estimatedRemainingM3 ?: remainingM3
            }
        }

        val pressureNow = currentFilteredPressure.toInt()
        // Capture a baseline once and preserve it until a refuel is confirmed.
        if (preRefuelPressure < 0) preRefuelPressure = pressureNow
        if (pressureNow >= preRefuelPressure + REFUEL_PRESSURE_DELTA) refuelDetected = true
        persist()
    }

    fun registerRefuel(addedM3: Double, distanceKm: Double, capacityM3: Float): JSONObject {
        val timestampMs = maxOf(System.currentTimeMillis(), lastTelemetryTimestampMs + 1L)
        val evidence = engine.confirmRefuel(timestampMs, distanceKm, addedM3)
        if (!evidence.accepted) {
            return evidenceJson(evidence).put("ok", false)
        }

        val capacity = capacityM3.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: addedM3
        remainingM3 = capacity.coerceAtLeast(addedM3)
        lastConfirmedKmPerM3 = evidence.kmPerM3 ?: Double.NaN
        lastConfirmedAtMs = evidence.timestampMs
        refuelDetected = false
        if (currentFilteredPressure >= 0.0) preRefuelPressure = currentFilteredPressure.toInt()
        prefs.edit().putFloat("capacity_m3", capacity.toFloat()).apply()
        persist()
        return evidenceJson(evidence)
            .put("ok", true)
            .put("addedM3", addedM3)
            .put("distanceKm", distanceKm)
            .put("capacityM3", capacity)
            .put("remainingM3", remainingM3)
    }

    fun buildTelemetryJson(capacityM3: Float): JSONObject {
        val capacity = capacityM3.toDouble()
        val estimate = lastEstimate
        return JSONObject()
            .put("remainingM3", remainingM3.coerceAtLeast(0.0))
            .put("capacityM3", capacity.coerceAtLeast(0.0))
            .put("refuelDetected", refuelDetected)
            .put("preRefuelPressure", preRefuelPressure)
            .put("filteredPressure", currentFilteredPressure)
            .put("confirmedKmPerM3", if (lastConfirmedKmPerM3.isFinite()) lastConfirmedKmPerM3 else JSONObject.NULL)
            .put("confirmedAtMs", if (lastConfirmedAtMs >= 0L) lastConfirmedAtMs else JSONObject.NULL)
            .put("confirmedOrigin", if (lastConfirmedKmPerM3.isFinite()) ConsumptionEvidenceOrigin.REFUEL_CONFIRMED.name else JSONObject.NULL)
            .put("pressureEstimateM3", estimate?.estimatedRemainingM3 ?: JSONObject.NULL)
            .put("pressureEstimateQuality", estimate?.quality ?: 0.0)
            .put("pressureEstimateUncertaintyM3", estimate?.uncertaintyM3 ?: JSONObject.NULL)
            .put("pressureEstimateOrigin", estimate?.origin?.name ?: JSONObject.NULL)
    }

    private fun evidenceJson(value: ConsumptionEvidence): JSONObject = JSONObject()
        .put("accepted", value.accepted)
        .put("confirmed", value.confirmed)
        .put("origin", value.origin.name)
        .put("timestampMs", value.timestampMs)
        .put("kmPerM3", value.kmPerM3 ?: JSONObject.NULL)
        .put("estimatedRemainingM3", value.estimatedRemainingM3 ?: JSONObject.NULL)
        .put("quality", value.quality)
        .put("uncertaintyM3", value.uncertaintyM3)
        .put("reason", value.reason)

    private fun persist() {
        prefs.edit()
            .putFloat("remaining_m3", remainingM3.toFloat())
            .putBoolean("refuel_detected", refuelDetected)
            .putInt("pre_refuel_pressure", preRefuelPressure)
            .putFloat("filtered_pressure", currentFilteredPressure.toFloat())
            .putFloat("last_confirmed_km_per_m3", lastConfirmedKmPerM3.toFloat())
            .putLong("last_confirmed_at_ms", lastConfirmedAtMs)
            .putLong("last_telemetry_timestamp_ms", lastTelemetryTimestampMs)
            .apply()
    }

    companion object {
        private const val REFUEL_PRESSURE_DELTA = 10
    }
}
