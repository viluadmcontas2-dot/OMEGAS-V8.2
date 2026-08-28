package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject

/** Proveniência mínima necessária para distinguir novidade real de sobreposição. */
data class EvidenceProvenance(
    val firstFrameSequence: Long,
    val lastFrameSequence: Long,
    val newFrameCount: Int,
    val reusedFrameCount: Int,
    val noveltyRatio: Double,
    val source: String = "APP_LIVE",
) {
    init {
        require(firstFrameSequence >= 0L)
        require(lastFrameSequence >= firstFrameSequence)
        require(newFrameCount >= 0 && reusedFrameCount >= 0)
        require(noveltyRatio in 0.0..1.0)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("firstFrameSequence", firstFrameSequence)
        .put("lastFrameSequence", lastFrameSequence)
        .put("newFrameCount", newFrameCount)
        .put("reusedFrameCount", reusedFrameCount)
        .put("noveltyRatio", noveltyRatio)
        .put("source", source)

    companion object {
        fun fromJson(raw: JSONObject) = EvidenceProvenance(
            firstFrameSequence = raw.optLong("firstFrameSequence", 0L),
            lastFrameSequence = raw.optLong("lastFrameSequence", 0L).coerceAtLeast(raw.optLong("firstFrameSequence", 0L)),
            newFrameCount = raw.optInt("newFrameCount", 0),
            reusedFrameCount = raw.optInt("reusedFrameCount", 0),
            noveltyRatio = raw.optDouble("noveltyRatio", 0.0).coerceIn(0.0, 1.0),
            source = raw.optString("source", "APP_LIVE"),
        )
    }
}

/** Evidência agregada de uma permanência física; nunca vira voto ilimitado. */
data class VisitComparisonAccumulator(
    val key: String,
    val weightedErrorSum: Double = 0.0,
    val weight: Double = 0.0,
    val squaredErrorSum: Double = 0.0,
    val independentWindowCount: Int = 0,
    val correlatedWindowCount: Int = 0,
    val firstSeenAt: Long = 0L,
    val lastSeenAt: Long = 0L,
    val saturated: Boolean = false,
) {
    init { require(key.isNotBlank()) }

    fun add(error: Double, sampleWeight: Double, independent: Boolean, nowMs: Long): VisitComparisonAccumulator {
        val safeWeight = sampleWeight.coerceIn(0.0, 1.0)
        if (safeWeight == 0.0) return this
        val nextWeight = (weight + safeWeight).coerceAtMost(MAX_VISIT_WEIGHT)
        val acceptedWeight = (nextWeight - weight).coerceAtLeast(0.0)
        val first = if (firstSeenAt == 0L) nowMs else firstSeenAt
        return copy(
            weightedErrorSum = weightedErrorSum + error * acceptedWeight,
            weight = nextWeight,
            squaredErrorSum = squaredErrorSum + error * error * acceptedWeight,
            independentWindowCount = independentWindowCount + if (independent) 1 else 0,
            correlatedWindowCount = correlatedWindowCount + if (independent) 0 else 1,
            firstSeenAt = first,
            lastSeenAt = nowMs,
            saturated = nextWeight >= MAX_VISIT_WEIGHT,
        )
    }

    fun meanError(): Double = if (weight <= 0.0) 0.0 else weightedErrorSum / weight
    fun variance(): Double = if (weight <= 0.0) 0.0 else (squaredErrorSum / weight - meanError() * meanError()).coerceAtLeast(0.0)
    fun toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("weightedErrorSum", weightedErrorSum)
        .put("weight", weight)
        .put("squaredErrorSum", squaredErrorSum)
        .put("independentWindowCount", independentWindowCount)
        .put("correlatedWindowCount", correlatedWindowCount)
        .put("firstSeenAt", firstSeenAt)
        .put("lastSeenAt", lastSeenAt)
        .put("saturated", saturated)
        .put("meanError", meanError())
        .put("variance", variance())

    companion object {
        const val MAX_VISIT_WEIGHT = 1.5

        fun fromJson(raw: JSONObject) = VisitComparisonAccumulator(
            key = raw.optString("key", "unknown"),
            weightedErrorSum = raw.optDouble("weightedErrorSum", 0.0),
            weight = raw.optDouble("weight", 0.0).coerceIn(0.0, MAX_VISIT_WEIGHT),
            squaredErrorSum = raw.optDouble("squaredErrorSum", 0.0),
            independentWindowCount = raw.optInt("independentWindowCount", 0),
            correlatedWindowCount = raw.optInt("correlatedWindowCount", 0),
            firstSeenAt = raw.optLong("firstSeenAt", 0L),
            lastSeenAt = raw.optLong("lastSeenAt", 0L),
            saturated = raw.optBoolean("saturated", false),
        )
    }
}

/** Evidência nativa é contexto inicial, não uma comparação inventada pelo telefone. */
data class NativeEcuEvidence(
    val snapshotId: String,
    val bandIndex: Int,
    val count: Int,
    val coverageQuality: Double,
    val petrolTimeRaw: Int? = null,
    val cngTimeRaw: Int? = null,
    val mapRaw: Int? = null,
    val historicalConditionKnown: Boolean = false,
) {
    init {
        require(snapshotId.isNotBlank())
        require(bandIndex >= 0)
        require(count >= 0)
        require(coverageQuality in 0.0..1.0)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("source", "ECU_NATIVE")
        .put("snapshotId", snapshotId)
        .put("bandIndex", bandIndex)
        .put("count", count)
        .put("coverageQuality", coverageQuality)
        .put("petrolTimeRaw", petrolTimeRaw ?: JSONObject.NULL)
        .put("cngTimeRaw", cngTimeRaw ?: JSONObject.NULL)
        .put("mapRaw", mapRaw ?: JSONObject.NULL)
        .put("historicalConditionKnown", historicalConditionKnown)
}

/** Métricas de desempenho do aprendizado, sem dados sensíveis e sem efeitos colaterais. */
data class LearningPerformanceMetrics(
    val framesReceived: Long = 0L,
    val validFrames: Long = 0L,
    val windowsEvaluated: Long = 0L,
    val earlySamplesAccepted: Long = 0L,
    val newFrames: Long = 0L,
    val reusedFrames: Long = 0L,
    val usefulWeight: Double = 0.0,
    val firstEstimateAtMs: Long? = null,
    val mediumConfidenceAtMs: Long? = null,
    val highConfidenceAtMs: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("framesReceived", framesReceived)
        .put("validFrames", validFrames)
        .put("windowsEvaluated", windowsEvaluated)
        .put("earlySamplesAccepted", earlySamplesAccepted)
        .put("newFrames", newFrames)
        .put("reusedFrames", reusedFrames)
        .put("usefulWeight", usefulWeight)
        .put("firstEstimateAtMs", firstEstimateAtMs ?: JSONObject.NULL)
        .put("mediumConfidenceAtMs", mediumConfidenceAtMs ?: JSONObject.NULL)
        .put("highConfidenceAtMs", highConfidenceAtMs ?: JSONObject.NULL)
        .put("utilizationRatio", if (validFrames == 0L) 0.0 else newFrames.toDouble() / validFrames.toDouble())

    companion object {
        fun fromJson(raw: JSONObject) = LearningPerformanceMetrics(
            framesReceived = raw.optLong("framesReceived", 0L),
            validFrames = raw.optLong("validFrames", 0L),
            windowsEvaluated = raw.optLong("windowsEvaluated", 0L),
            earlySamplesAccepted = raw.optLong("earlySamplesAccepted", 0L),
            newFrames = raw.optLong("newFrames", 0L),
            reusedFrames = raw.optLong("reusedFrames", 0L),
            usefulWeight = raw.optDouble("usefulWeight", 0.0),
            firstEstimateAtMs = raw.optLong("firstEstimateAtMs", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE },
            mediumConfidenceAtMs = raw.optLong("mediumConfidenceAtMs", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE },
            highConfidenceAtMs = raw.optLong("highConfidenceAtMs", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE },
        )
    }
}

fun JSONArray.toIntList(): List<Int> = List(length()) { optInt(it) }
