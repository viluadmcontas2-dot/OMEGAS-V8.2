package com.omegas.prohub.obd

/** One physically bounded OBD acquisition made only from ELM Mode 01 data. */
data class ObdPidCycle(
    val rpm: Double,
    val mapBar: Double,
    val stftPct: Double,
    val acquisitionSpanMs: Long,
    val observedAtMs: Long,
    val complete: Boolean = true,
) {
    companion object {
        const val MAX_ACQUISITION_SPAN_MS = 750L

        fun decode(
            rpmBytes: List<Int>?,
            mapBytes: List<Int>?,
            stftBytes: List<Int>?,
            startedAtMs: Long,
            endedAtMs: Long,
        ): ObdPidCycle? {
            if (startedAtMs < 0L || endedAtMs < startedAtMs) return null
            val span = endedAtMs - startedAtMs
            if (span > MAX_ACQUISITION_SPAN_MS) return null
            if (rpmBytes == null || rpmBytes.size < 2 || mapBytes.isNullOrEmpty() || stftBytes.isNullOrEmpty()) return null
            val a = rpmBytes[0]
            val b = rpmBytes[1]
            val mapRaw = mapBytes[0]
            val stftRaw = stftBytes[0]
            if (a !in 0..255 || b !in 0..255 || mapRaw !in 0..255 || stftRaw !in 0..255) return null
            val rpm = (a * 256.0 + b) / 4.0
            val mapBar = mapRaw / 100.0
            val stft = ObdStftCodec.percent(stftRaw)
            return fromValues(rpm, mapBar, stft, span, endedAtMs)
        }

        fun fromValues(
            rpm: Double,
            mapBar: Double,
            stftPct: Double,
            acquisitionSpanMs: Long,
            observedAtMs: Long,
        ): ObdPidCycle? {
            if (!rpm.isFinite() || rpm !in 400.0..8_000.0) return null
            if (!mapBar.isFinite() || mapBar !in 0.10..1.60) return null
            if (!stftPct.isFinite() || kotlin.math.abs(stftPct) > 50.0) return null
            if (acquisitionSpanMs !in 0..MAX_ACQUISITION_SPAN_MS || observedAtMs < 0L) return null
            return ObdPidCycle(rpm, mapBar, stftPct, acquisitionSpanMs, observedAtMs)
        }
    }
}
