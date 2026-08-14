package com.omegas.prohub.learning

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Âncora observacional criada somente quando uma maturidade AutoCal nativa
 * possui correlação física confiável na mesma sessão MP48.
 *
 * Não é comparação gasolina×GNV, não possui writer e não aumenta confiança por si só.
 * Maturidade nativa sem correlação permanece apenas como NativeEcuEvidence.
 */
data class NativeLearningAnchor(
    val fingerprint: String,
    val calibrationEpoch: Int,
    val scientificRevision: Long = 0L,
    val sessionId: Long,
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
    val rpm: Int,
    val petrolOnCngMs: Double,
    val gasMsDiagnostic: Double?,
    val mapBar: Double,
    val fuel: String,
    val firstTelemetrySequence: Long,
    val lastTelemetrySequence: Long,
    val matchedTelemetryFrames: Int,
    val eventElapsedMs: Long,
    val correlatedFrameElapsedMs: Long,
    val lagMs: Long,
) {
    init {
        require(fingerprint.isNotBlank())
        require(calibrationEpoch >= 1)
        require(scientificRevision >= 0L)
        require(sessionId > 0L)
        require(snapshotId.isNotBlank())
        require(bandIndex >= 0)
        require(counter >= 0)
        require(threshold >= 0)
        require(nativeValidity)
        require(correlationState == "CORRELATED")
        require(correlationConfidence > 0.0 && correlationConfidence <= 1.0)
        require(rpmConfidence > 0.0 && rpmConfidence <= 1.0)
        require(rpm >= 0)
        require(petrolOnCngMs.isFinite())
        require(mapBar.isFinite())
        require(fuel == "GNV")
        require(firstTelemetrySequence >= 0L)
        require(lastTelemetrySequence >= firstTelemetrySequence)
        require(matchedTelemetryFrames >= 2)
        require(eventElapsedMs >= 0L)
        require(correlatedFrameElapsedMs >= 0L)
        require(lagMs >= 0L)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("source", "ECU_NATIVE_AUTOCAL")
        .put("fingerprint", fingerprint)
        .put("calibrationEpoch", calibrationEpoch)
        .put("scientificRevision", scientificRevision)
        .put("snapshotId", snapshotId)
        .put("sessionId", sessionId)
        .put("snapshotHash", snapshotHash)
        .put("bandIndex", bandIndex)
        .put("zone", zone)
        .put("counter", counter)
        .put("threshold", threshold)
        .put("nativeValidity", nativeValidity)
        .put("correlationState", correlationState)
        .put("correlationConfidence", correlationConfidence)
        .put("rpmConfidence", rpmConfidence)
        .put("rpm", rpm)
        .put("petrolOnCngMs", petrolOnCngMs)
        .put("gasMsDiagnostic", gasMsDiagnostic ?: JSONObject.NULL)
        .put("mapBar", mapBar)
        .put("fuel", fuel)
        .put("firstTelemetrySequence", firstTelemetrySequence)
        .put("lastTelemetrySequence", lastTelemetrySequence)
        .put("matchedTelemetryFrames", matchedTelemetryFrames)
        .put("eventElapsedMs", eventElapsedMs)
        .put("correlatedFrameElapsedMs", correlatedFrameElapsedMs)
        .put("lagMs", lagMs)
        .put("comparisonVote", false)
        .put("automaticWrite", false)

    companion object {
        fun fromMaturityEvent(event: JSONObject, calibrationEpoch: Int): NativeLearningAnchor? {
            if (event.optString("eventType") != "NATIVE_BAND_MATURED") return null
            if (!event.optBoolean("nativeValidity", false)) return null
            if (event.optString("correlationState") != "CORRELATED") return null

            val sessionId = event.optLong("sessionId", 0L)
            val snapshotId = event.optString("snapshotId")
            val bandIndex = event.optInt("bandIndex", -1)
            val rpm = event.nullableInt("rpm") ?: return null
            val petrolOnCngMs = event.nullableDouble("correlatedPetrolMs") ?: return null
            val mapBar = event.nullableDouble("correlatedMapBar") ?: return null
            val firstSequence = event.nullableLong("firstTelemetrySequence") ?: return null
            val lastSequence = event.nullableLong("lastTelemetrySequence") ?: return null
            val eventElapsedMs = event.nullableLong("observedAtElapsedMs") ?: return null
            val correlatedFrameElapsedMs = event.nullableLong("correlatedFrameElapsedMs") ?: return null
            val lagMs = event.nullableLong("correlationLagMs") ?: return null
            val matchedFrames = event.optInt("matchedTelemetryFrames", 0)
            val fuel = event.optString("correlatedFuel", event.optString("fuel"))
            val correlationConfidence = event.optDouble("correlationConfidence", 0.0)
                .takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: return null
            val rpmConfidence = event.optDouble("rpmConfidence", 0.0)
                .takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: return null
            if (sessionId <= 0L || snapshotId.isBlank() || bandIndex < 0 ||
                firstSequence < 0L || lastSequence < firstSequence || matchedFrames < 2 || fuel != "GNV" ||
                correlationConfidence <= 0.0 || rpmConfidence <= 0.0
            ) return null

            val identity = listOf(
                calibrationEpoch.coerceAtLeast(1).toString(),
                sessionId.toString(),
                bandIndex.toString(),
                event.optInt("counter", 0).toString(),
                event.optLong("previousObservedAtElapsedMs", 0L).toString(),
                eventElapsedMs.toString(),
                firstSequence.toString(),
                lastSequence.toString(),
            ).joinToString("|")

            return NativeLearningAnchor(
                fingerprint = sha256(identity),
                calibrationEpoch = calibrationEpoch.coerceAtLeast(1),
                sessionId = sessionId,
                snapshotId = snapshotId,
                snapshotHash = event.optString("snapshotHash"),
                bandIndex = bandIndex,
                zone = event.optString("zone", "UNKNOWN"),
                counter = event.optInt("counter", 0).coerceAtLeast(0),
                threshold = event.optInt("threshold", 0).coerceAtLeast(0),
                nativeValidity = true,
                correlationState = "CORRELATED",
                correlationConfidence = correlationConfidence,
                rpmConfidence = rpmConfidence,
                rpm = rpm,
                petrolOnCngMs = petrolOnCngMs,
                gasMsDiagnostic = event.nullableDouble("correlatedGasMs"),
                mapBar = mapBar,
                fuel = "GNV",
                firstTelemetrySequence = firstSequence,
                lastTelemetrySequence = lastSequence,
                matchedTelemetryFrames = matchedFrames,
                eventElapsedMs = eventElapsedMs.coerceAtLeast(0L),
                correlatedFrameElapsedMs = correlatedFrameElapsedMs.coerceAtLeast(0L),
                lagMs = lagMs.coerceAtLeast(0L),
            )
        }

        fun fromJson(raw: JSONObject): NativeLearningAnchor? {
            val fingerprint = raw.optString("fingerprint")
            val snapshotId = raw.optString("snapshotId")
            val sessionId = raw.optLong("sessionId", 0L)
            val bandIndex = raw.optInt("bandIndex", -1)
            val rpm = raw.nullableInt("rpm") ?: return null
            val petrolOnCngMs = raw.nullableDouble("petrolOnCngMs") ?: return null
            val mapBar = raw.nullableDouble("mapBar") ?: return null
            val firstSequence = raw.nullableLong("firstTelemetrySequence") ?: return null
            val lastSequence = raw.nullableLong("lastTelemetrySequence") ?: return null
            val eventElapsedMs = raw.nullableLong("eventElapsedMs") ?: return null
            val correlatedFrameElapsedMs = raw.nullableLong("correlatedFrameElapsedMs") ?: return null
            val lagMs = raw.nullableLong("lagMs") ?: return null
            if (fingerprint.isBlank() || snapshotId.isBlank() || sessionId <= 0L || bandIndex < 0 ||
                raw.optString("correlationState") != "CORRELATED" || !raw.optBoolean("nativeValidity", false)
            ) return null

            return try {
                NativeLearningAnchor(
                    fingerprint = fingerprint,
                    calibrationEpoch = raw.optInt("calibrationEpoch", 1).coerceAtLeast(1),
                    scientificRevision = raw.optLong("scientificRevision", 0L).coerceAtLeast(0L),
                    sessionId = sessionId,
                    snapshotId = snapshotId,
                    snapshotHash = raw.optString("snapshotHash"),
                    bandIndex = bandIndex,
                    zone = raw.optString("zone", "UNKNOWN"),
                    counter = raw.optInt("counter", 0).coerceAtLeast(0),
                    threshold = raw.optInt("threshold", 0).coerceAtLeast(0),
                    nativeValidity = true,
                    correlationState = "CORRELATED",
                    correlationConfidence = raw.optDouble("correlationConfidence", 0.0).coerceIn(0.0, 1.0),
                    rpmConfidence = raw.optDouble("rpmConfidence", 0.0).coerceIn(0.0, 1.0),
                    rpm = rpm,
                    petrolOnCngMs = petrolOnCngMs,
                    gasMsDiagnostic = raw.nullableDouble("gasMsDiagnostic"),
                    mapBar = mapBar,
                    fuel = raw.optString("fuel"),
                    firstTelemetrySequence = firstSequence,
                    lastTelemetrySequence = lastSequence,
                    matchedTelemetryFrames = raw.optInt("matchedTelemetryFrames", 0),
                    eventElapsedMs = eventElapsedMs,
                    correlatedFrameElapsedMs = correlatedFrameElapsedMs,
                    lagMs = lagMs,
                )
            } catch (_: IllegalArgumentException) {
                null
            }
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
