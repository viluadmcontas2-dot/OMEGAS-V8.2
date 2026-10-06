package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln

/**
 * Detecta uma aquisição nativa isolada que nasceu em contexto de marcha lenta.
 *
 * A geometria não usa um corte percentual solto: reutiliza exatamente o ajuste
 * isotônico robusto do Refino ([AutoMatchRefinedEngine.monotoneFit]). A mutação
 * só é autorizada quando a própria maturidade nativa foi correlacionada com um
 * agrupamento físico estável de telemetria e esse agrupamento ocorreu abaixo do
 * regime de condução. Assim, divergência repetida em carga continua sendo
 * evidência real, não "ruído" apagado por conveniência.
 */
object AutoCalNativeOutlierDetector {
    const val IDLE_RPM_CEILING = 1_100
    const val MIN_CORRELATION_CONFIDENCE = 0.70
    const val MIN_RPM_CONFIDENCE = 0.50
    const val MIN_MATCHED_FRAMES = 2
    const val MIN_MATURE_SUPPORT = 5
    const val REASON_IDLE_OUTLIER = "ISOLATED_NATIVE_POINT_AT_IDLE"

    data class MaturityEvidence(
        val bandIndex: Int,
        val rpm: Int?,
        val correlationConfidence: Double,
        val rpmConfidence: Double,
        val matchedFrames: Int,
    )

    data class Candidate(
        val target: AutoCalPointDeleteProtocol.Target,
        val reason: String,
        val robustResidual: Double,
        val rpm: Int,
        val correlationConfidence: Double,
        val rpmConfidence: Double,
        val matchedFrames: Int,
    ) {
        fun evidenceJson(): JSONObject = JSONObject()
            .put("reason", reason)
            .put("fuel", target.fuel.wireName)
            .put("bandIndex", target.index)
            .put("zone", target.zone)
            .put("rpm", rpm)
            .put("robustResidualLog", robustResidual)
            .put("correlationConfidence", correlationConfidence)
            .put("rpmConfidence", rpmConfidence)
            .put("matchedFrames", matchedFrames)
            .put("automatic", true)
            .put("criterion", "REFINO_ROBUST_GEOMETRY_PLUS_IDLE_CONTEXT")
    }

    fun detectGas(
        timeRaw: IntArray,
        mapRaw: IntArray,
        counts: IntArray,
        evidence: List<MaturityEvidence>,
    ): List<Candidate> {
        if (timeRaw.size < AutoMatchRefinedEngine.BAND_COUNT ||
            mapRaw.size < AutoMatchRefinedEngine.BAND_COUNT ||
            counts.size < AutoMatchRefinedEngine.BAND_COUNT
        ) return emptyList()

        val mature = AutoMatchRefinedEngine
            .bandPoints(
                timeRaw.copyOf(AutoMatchRefinedEngine.BAND_COUNT),
                mapRaw.copyOf(AutoMatchRefinedEngine.BAND_COUNT),
                counts.copyOf(AutoMatchRefinedEngine.BAND_COUNT),
            )
            .filter { it.count >= AutoMatchRefinedEngine.BAND_MATURE_COUNT }

        if (mature.size < MIN_MATURE_SUPPORT) return emptyList()
        val (accepted, rejected) = AutoMatchRefinedEngine.monotoneFit(mature)
        if (accepted.size < 2 || rejected.isEmpty()) return emptyList()

        val rejectedByBand = rejected.associateBy { it.band }
        val xs = accepted.map { it.mapBar }
        val ys = accepted.map { ln(it.fitTimeMs ?: it.timeMs) }

        return evidence
            .asSequence()
            .filter { it.bandIndex in 0 until AutoMatchRefinedEngine.BAND_COUNT }
            .distinctBy { it.bandIndex }
            .mapNotNull { context ->
                val rpm = context.rpm ?: return@mapNotNull null
                if (rpm > IDLE_RPM_CEILING) return@mapNotNull null
                if (context.correlationConfidence < MIN_CORRELATION_CONFIDENCE ||
                    context.rpmConfidence < MIN_RPM_CONFIDENCE ||
                    context.matchedFrames < MIN_MATCHED_FRAMES
                ) return@mapNotNull null
                val point = rejectedByBand[context.bandIndex] ?: return@mapNotNull null
                if (point.count < AutoMatchRefinedEngine.BAND_MATURE_COUNT) return@mapNotNull null

                val expectedLog = AutoMatchRefinedEngine.interp(point.mapBar, xs, ys)
                val residual = abs(ln(point.timeMs) - expectedLog)
                if (!residual.isFinite() || residual <= 0.0) return@mapNotNull null

                Candidate(
                    target = AutoCalPointDeleteProtocol.Target(
                        AutoCalPointDeleteProtocol.Fuel.GAS,
                        context.bandIndex,
                    ),
                    reason = REASON_IDLE_OUTLIER,
                    robustResidual = residual,
                    rpm = rpm,
                    correlationConfidence = context.correlationConfidence,
                    rpmConfidence = context.rpmConfidence,
                    matchedFrames = context.matchedFrames,
                )
            }
            .sortedByDescending { it.robustResidual }
            .toList()
    }

    /** Lê diretamente o snapshot decorado do [NativeAutoCalMonitor]. */
    fun detectSnapshot(snapshot: JSONObject): List<Candidate> {
        val events = snapshot.optJSONArray("nativeMaturityEvents") ?: JSONArray()
        val evidence = buildList {
            repeat(events.length()) { index ->
                val event = events.optJSONObject(index) ?: return@repeat
                if (event.optString("fuel").uppercase() !in setOf("GNV", "GAS", "CNG")) return@repeat
                if (event.optString("correlationState") != "CORRELATED") return@repeat
                val band = event.optInt("bandIndex", -1)
                if (band !in 0 until AutoMatchRefinedEngine.BAND_COUNT) return@repeat
                val rpm = event.opt("rpm").let { value ->
                    when (value) {
                        is Number -> value.toInt()
                        else -> null
                    }
                }
                add(
                    MaturityEvidence(
                        bandIndex = band,
                        rpm = rpm,
                        correlationConfidence = event.optDouble("correlationConfidence", 0.0),
                        rpmConfidence = event.optDouble("rpmConfidence", 0.0),
                        matchedFrames = event.optInt("matchedTelemetryFrames", 0),
                    ),
                )
            }
        }
        if (evidence.isEmpty()) return emptyList()
        return detectGas(
            timeRaw = rawVector(snapshot, "PETR_INJ_TBUF_GAS"),
            mapRaw = rawVector(snapshot, "MNFLD_PRESS_BUF_GAS"),
            counts = rawVector(snapshot, "NUM_BUF_UPD_GAS"),
            evidence = evidence,
        )
    }

    private fun rawVector(snapshot: JSONObject, key: String): IntArray {
        val fields = snapshot.optJSONArray("fields") ?: return intArrayOf()
        repeat(fields.length()) { index ->
            val field = fields.optJSONObject(index) ?: return@repeat
            if (field.optString("key") != key || field.optString("status") != "VALID") return@repeat
            val values = field.optJSONArray("rawValues") ?: return intArrayOf()
            return IntArray(values.length()) { values.optInt(it) }
        }
        return intArrayOf()
    }
}
