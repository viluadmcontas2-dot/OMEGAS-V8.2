package com.omegas.prohub.blue

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Deterministic, versioned identity for the physical RPM x MAP region.
 * Evidence UUID/visit identity is deliberately separate from this scientific key.
 * Causal matching uses continuous physical distance, so adjacent quantization
 * buckets never create a fake discontinuity by themselves.
 */
data class BlueScientificRegion(
    val rpmBucket: Int,
    val mapBucket: Int,
    val rpmCenter: Double,
    val mapCenterBar: Double,
) {
    val id: String get() = "$SCHEMA:r$rpmBucket:m$mapBucket"

    fun physicallyMatches(
        rpm: Double,
        mapBar: Double,
        minimumRpmWindow: Double = 120.0,
        relativeRpmWindow: Double = 0.06,
        mapWindowBar: Double = 0.08,
    ): Boolean {
        if (!rpm.isFinite() || !mapBar.isFinite()) return false
        val rpmWindow = max(minimumRpmWindow, max(rpmCenter, rpm) * relativeRpmWindow)
        return abs(rpmCenter - rpm) <= rpmWindow && abs(mapCenterBar - mapBar) <= mapWindowBar
    }

    companion object {
        const val SCHEMA = "rpm-map-v1"
        private const val RPM_QUANTUM = 50.0
        private const val MAP_QUANTUM = 0.01

        fun from(rpm: Double, mapBar: Double): BlueScientificRegion {
            require(rpm.isFinite() && rpm > 0.0) { "RPM inválido" }
            require(mapBar.isFinite() && mapBar > 0.0) { "MAP inválido" }
            val rpmBucket = floor(rpm / RPM_QUANTUM).toInt()
            val mapBucket = floor((mapBar + 1e-9) / MAP_QUANTUM).toInt()
            return BlueScientificRegion(
                rpmBucket = rpmBucket,
                mapBucket = mapBucket,
                rpmCenter = (rpmBucket + 0.5) * RPM_QUANTUM,
                mapCenterBar = (mapBucket + 0.5) * MAP_QUANTUM,
            )
        }

        fun isVersionedId(value: String): Boolean = value.startsWith("$SCHEMA:r") && value.contains(":m")

        fun parse(value: String): BlueScientificRegion? {
            if (!isVersionedId(value)) return null
            val match = Regex("^${Regex.escape(SCHEMA)}:r(-?\\d+):m(-?\\d+)$").matchEntire(value) ?: return null
            val rpmBucket = match.groupValues[1].toIntOrNull() ?: return null
            val mapBucket = match.groupValues[2].toIntOrNull() ?: return null
            return BlueScientificRegion(
                rpmBucket = rpmBucket,
                mapBucket = mapBucket,
                rpmCenter = (rpmBucket + 0.5) * RPM_QUANTUM,
                mapCenterBar = (mapBucket + 0.5) * MAP_QUANTUM,
            )
        }

        fun physicallyMatches(
            firstRpm: Double,
            firstMapBar: Double,
            secondRpm: Double,
            secondMapBar: Double,
        ): Boolean = from(firstRpm, firstMapBar).physicallyMatches(secondRpm, secondMapBar)
    }
}
