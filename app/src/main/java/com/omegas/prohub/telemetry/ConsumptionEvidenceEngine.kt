package com.omegas.prohub.telemetry

import kotlin.math.max

enum class ConsumptionEvidenceOrigin {
    REFUEL_CONFIRMED,
    PRESSURE_ESTIMATE,
}

data class ConsumptionEvidence(
    val accepted: Boolean,
    val confirmed: Boolean,
    val origin: ConsumptionEvidenceOrigin,
    val timestampMs: Long,
    val kmPerM3: Double? = null,
    val estimatedRemainingM3: Double? = null,
    val quality: Double = 0.0,
    val uncertaintyM3: Double = 0.0,
    val reason: String = "",
)

/**
 * Android-free evidence engine. It is deterministic for a given call sequence
 * and never reads preferences, sensors, files or wall clock by itself.
 */
class ConsumptionEvidenceEngine {
    private var lastTimestampMs: Long = Long.MIN_VALUE

    fun confirmRefuel(timestampMs: Long, distanceKm: Double, addedM3: Double): ConsumptionEvidence {
        val orderError = rejectOutOfOrder(timestampMs, ConsumptionEvidenceOrigin.REFUEL_CONFIRMED)
        if (orderError != null) return orderError
        if (!distanceKm.isFinite() || distanceKm <= 0.0 || !addedM3.isFinite() || addedM3 <= 0.0) {
            return rejected(timestampMs, ConsumptionEvidenceOrigin.REFUEL_CONFIRMED, "INVALID_REFUEL_EVIDENCE")
        }
        lastTimestampMs = timestampMs
        return ConsumptionEvidence(
            accepted = true,
            confirmed = true,
            origin = ConsumptionEvidenceOrigin.REFUEL_CONFIRMED,
            timestampMs = timestampMs,
            // Confirmed economy is intentionally independent from pressure.
            kmPerM3 = distanceKm / addedM3,
            quality = 1.0,
            uncertaintyM3 = 0.0,
            reason = "DISTANCE_AND_ADDED_VOLUME_CONFIRMED",
        )
    }

    fun estimateFromPressure(
        timestampMs: Long,
        normalizedPressure: Double,
        capacityM3: Double,
    ): ConsumptionEvidence {
        val orderError = rejectOutOfOrder(timestampMs, ConsumptionEvidenceOrigin.PRESSURE_ESTIMATE)
        if (orderError != null) return orderError
        if (!normalizedPressure.isFinite() || normalizedPressure !in 0.0..255.0 ||
            !capacityM3.isFinite() || capacityM3 <= 0.0
        ) {
            return rejected(timestampMs, ConsumptionEvidenceOrigin.PRESSURE_ESTIMATE, "INVALID_PRESSURE_EVIDENCE")
        }
        lastTimestampMs = timestampMs
        val fraction = (normalizedPressure / 255.0).coerceIn(0.0, 1.0)
        return ConsumptionEvidence(
            accepted = true,
            confirmed = false,
            origin = ConsumptionEvidenceOrigin.PRESSURE_ESTIMATE,
            timestampMs = timestampMs,
            estimatedRemainingM3 = fraction * capacityM3,
            quality = 0.35,
            uncertaintyM3 = max(0.5, capacityM3 * 0.15),
            reason = "PRESSURE_ONLY_ESTIMATE",
        )
    }

    private fun rejectOutOfOrder(timestampMs: Long, origin: ConsumptionEvidenceOrigin): ConsumptionEvidence? {
        if (timestampMs < 0L || timestampMs < lastTimestampMs) {
            return rejected(timestampMs, origin, "OUT_OF_ORDER_TIMESTAMP")
        }
        return null
    }

    private fun rejected(
        timestampMs: Long,
        origin: ConsumptionEvidenceOrigin,
        reason: String,
    ) = ConsumptionEvidence(
        accepted = false,
        confirmed = false,
        origin = origin,
        timestampMs = timestampMs,
        reason = reason,
    )
}
