package com.omegas.prohub.learning

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Âncora observacional criada quando a ECU confirma maturidade nativa de uma banda AutoCal.
 *
 * Não é comparação gasolina×GNV, não possui writer e não aumenta confiança por si só.
 * A validade nativa e a confiança da posição física (RPM) permanecem separadas.
 */
data class NativeLearningAnchor(
    val fingerprint: String,
    val calibrationEpoch: Int,
    val snapshotId: String,
    val snapshotHash: String,
    val bandIndex: Int,
    val zone: String,
    val counter: Int,
    val threshold: Int,
    val nativeValidity: Boolean,
    val correlationState: String,
    val correlationConfidence: Double,
    val rpmConfidence: Double,
    val rpm: Int?,
    val petrolOnCngMs: Double?,
    val mapBar: Double?,
    val firstTelemetrySequence: Long?,
    val lastTelemetrySequence: Long?,
    val matchedTelemetryFrames: Int,
    val observedAtElapsedMs: Long,
) {
    init {
        require(fingerprint.isNotBlank())
        require(calibrationEpoch >= 1)
        require(snapshotId.isNotBlank())
        require(bandIndex >= 0)
        require(counter >= 0)
        require(threshold >= 0)
        require(correlationConfidence in 0.0..1.0)
        require(rpmConfidence in 0.0..1.0)
        require(matchedTelemetryFrames >= 0)
        if (rpm == null) require(rpmConfidence == 0.0)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("source", "ECU_NATIVE_AUTOCAL")
        .put("fingerprint", fingerprint)
        .put("calibrationEpoch", calibrationEpoch)
        .put("snapshotId", snapshotId)
        .put("snapshotHash", snapshotHash)
        .put("bandIndex", bandIndex)
        .put("zone", zone)
        .put("counter", counter)
        .put("threshold", threshold)
        .put("nativeValidity", nativeValidity)
        .put("correlationState", correlationState)
        .put("correlationConfidence", correlationConfidence)
        .put("rpmConfidence", rpmConfidence)
        .put("rpm", rpm ?: JSONObject.NULL)
        .put("petrolOnCngMs", petrolOnCngMs ?: JSONObject.NULL)
        .put("mapBar", mapBar ?: JSONObject.NULL)
        .put("firstTelemetrySequence", firstTelemetrySequence ?: JSONObject.NULL)
        .put("lastTelemetrySequence", lastTelemetrySequence ?: JSONObject.NULL)
        .put("matchedTelemetryFrames", matchedTelemetryFrames)
        .put("observedAtElapsedMs", observedAtElapsedMs)
        .put("comparisonVote", false)
        .put("automaticWrite", false)

    companion object {
        fun fromMaturityEvent(event: JSONObject, calibrationEpoch: Int): NativeLearningAnchor? {
            if (event.optString("eventType") != "NATIVE_BAND_MATURED") return null
            if (!event.optBoolean("nativeValidity", false)) return null
            val snapshotId = event.optString("snapshotId")
            val bandIndex = event.optInt("bandIndex", -1)
            if (snapshotId.isBlank() || bandIndex < 0) return null

            val correlationState = event.optString("correlationState", "NO_RELIABLE_CORRELATION")
            val correlated = correlationState == "CORRELATED"
            val rpm = event.nullableInt("rpm").takeIf { correlated }
            val rpmConfidence = if (rpm == null) 0.0 else event.optDouble("rpmConfidence", 0.0).coerceIn(0.0, 1.0)
            val firstSequence = event.nullableLong("firstTelemetrySequence").takeIf { correlated }
            val lastSequence = event.nullableLong("lastTelemetrySequence").takeIf { correlated }
            val observedAt = event.optLong("observedAtElapsedMs", 0L).coerceAtLeast(0L)

            val identity = listOf(
                calibrationEpoch.toString(),
                bandIndex.toString(),
                event.optInt("counter", 0).toString(),
                event.optLong("previousObservedAtElapsedMs", 0L).toString(),
                observedAt.toString(),
                firstSequence?.toString().orEmpty(),
                lastSequence?.toString().orEmpty(),
            ).joinToString("|")

            return NativeLearningAnchor(
                fingerprint = sha256(identity),
                calibrationEpoch = calibrationEpoch.coerceAtLeast(1),
                snapshotId = snapshotId,
                snapshotHash = event.optString("snapshotHash"),
                bandIndex = bandIndex,
                zone = event.optString("zone", "UNKNOWN"),
                counter = event.optInt("counter", 0).coerceAtLeast(0),
                threshold = event.optInt("threshold", 0).coerceAtLeast(0),
                nativeValidity = true,
                correlationState = correlationState,
                correlationConfidence = if (correlated) event.optDouble("correlationConfidence", 0.0).coerceIn(0.0, 1.0) else 0.0,
                rpmConfidence = rpmConfidence,
                rpm = rpm,
                petrolOnCngMs = event.nullableDouble("correlatedPetrolMs").takeIf { correlated },
                mapBar = event.nullableDouble("correlatedMapBar").takeIf { correlated },
                firstTelemetrySequence = firstSequence,
                lastTelemetrySequence = lastSequence,
                matchedTelemetryFrames = if (correlated) event.optInt("matchedTelemetryFrames", 0).coerceAtLeast(0) else 0,
                observedAtElapsedMs = observedAt,
            )
        }

        fun fromJson(raw: JSONObject): NativeLearningAnchor? {
            val fingerprint = raw.optString("fingerprint")
            val snapshotId = raw.optString("snapshotId")
            val bandIndex = raw.optInt("bandIndex", -1)
            if (fingerprint.isBlank() || snapshotId.isBlank() || bandIndex < 0) return null
            val rpm = raw.nullableInt("rpm")
            return NativeLearningAnchor(
                fingerprint = fingerprint,
                calibrationEpoch = raw.optInt("calibrationEpoch", 1).coerceAtLeast(1),
                snapshotId = snapshotId,
                snapshotHash = raw.optString("snapshotHash"),
                bandIndex = bandIndex,
                zone = raw.optString("zone", "UNKNOWN"),
                counter = raw.optInt("counter", 0).coerceAtLeast(0),
                threshold = raw.optInt("threshold", 0).coerceAtLeast(0),
                nativeValidity = raw.optBoolean("nativeValidity", false),
                correlationState = raw.optString("correlationState", "NO_RELIABLE_CORRELATION"),
                correlationConfidence = raw.optDouble("correlationConfidence", 0.0).coerceIn(0.0, 1.0),
                rpmConfidence = if (rpm == null) 0.0 else raw.optDouble("rpmConfidence", 0.0).coerceIn(0.0, 1.0),
                rpm = rpm,
                petrolOnCngMs = raw.nullableDouble("petrolOnCngMs"),
                mapBar = raw.nullableDouble("mapBar"),
                firstTelemetrySequence = raw.nullableLong("firstTelemetrySequence"),
                lastTelemetrySequence = raw.nullableLong("lastTelemetrySequence"),
                matchedTelemetryFrames = raw.optInt("matchedTelemetryFrames", 0).coerceAtLeast(0),
                observedAtElapsedMs = raw.optLong("observedAtElapsedMs", 0L).coerceAtLeast(0L),
            )
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        private fun JSONObject.nullableInt(key: String): Int? =
            if (has(key) && !isNull(key)) optInt(key) else null

        private fun JSONObject.nullableLong(key: String): Long? =
            if (has(key) && !isNull(key)) optLong(key) else null

        private fun JSONObject.nullableDouble(key: String): Double? =
            if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null
    }
}

/** Registro bounded/deduplicado; a mesma passagem física nunca vira duas âncoras. */
class NativeLearningAnchorRegistry(
    private val maxEntries: Int = 256,
) {
    private val anchors = linkedMapOf<String, NativeLearningAnchor>()

    fun upsert(anchor: NativeLearningAnchor): Boolean {
        if (anchors.containsKey(anchor.fingerprint)) return false
        anchors[anchor.fingerprint] = anchor
        while (anchors.size > maxEntries.coerceAtLeast(1)) {
            anchors.remove(anchors.keys.first())
        }
        return true
    }

    fun replaceAll(items: List<NativeLearningAnchor>) {
        anchors.clear()
        items.takeLast(maxEntries.coerceAtLeast(1)).forEach { anchors[it.fingerprint] = it }
    }

    fun snapshot(): List<NativeLearningAnchor> = anchors.values.toList()

    fun clear() = anchors.clear()
}
