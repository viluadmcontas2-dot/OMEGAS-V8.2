package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.learning.LearningTolerancePolicy
import com.omegas.prohub.learning.NativeAutoCalEventCorrelator
import org.json.JSONArray
import org.json.JSONObject

/**
 * Projeta transições nativas já observadas em eventos AutoCal correlacionados.
 * Não faz I/O de protocolo, não agenda trabalho e não produz target/write.
 * O scheduler entra apenas como fonte read-only da microjanela de telemetria.
 */
object NativeAutoCalMaturityEventProjector {
    fun project(
        pending: List<NativeAutoCalDualFuelMaturityObserver.Event>,
        acquisition: JSONObject,
        telemetry: Mp48SerialScheduler,
        policy: LearningTolerancePolicy,
        sessionId: Long,
        snapshotId: String,
        snapshotHash: String,
    ): JSONArray {
        val result = JSONArray()
        pending.forEach { pendingEvent ->
            val transition = pendingEvent.transition
            val fuelLabel = when (pendingEvent.sourceFuel) {
                NativeAutoCalEventCorrelator.SourceFuel.PETROL -> "GASOLINA"
                NativeAutoCalEventCorrelator.SourceFuel.CNG -> "GNV"
            }
            val point = acquisition.findPoint(fuelLabel, transition.bandIndex)
            val nativePetrolMs = point?.nullableDouble("timeMs")
            val nativeMapBar = point?.nullableDouble("mapBar")
            val frames = telemetry.recentTelemetryFrames(
                fromElapsedMs = transition.previousObservedAtElapsedMs,
                toElapsedMs = transition.observedAtElapsedMs,
            )
            val correlation = NativeAutoCalEventCorrelator.correlate(
                frames = frames,
                sourceFuel = pendingEvent.sourceFuel,
                nativePetrolMs = nativePetrolMs,
                nativeMapBar = nativeMapBar,
                observedAtElapsedMs = transition.observedAtElapsedMs,
                windowFromElapsedMs = transition.previousObservedAtElapsedMs,
                windowToElapsedMs = transition.observedAtElapsedMs,
                policy = policy,
                sessionId = sessionId,
            )
            if (correlation.state == "CORRELATED") {
                AutoCal122ATargetMetrics.markFirstAnchor(
                    currentSessionId = sessionId,
                    observedAtElapsedMs = correlation.correlatedFrameElapsedMs ?: transition.observedAtElapsedMs,
                )
            }
            result.put(
                JSONObject()
                    .put("eventType", "NATIVE_BAND_MATURED")
                    .put("source", NativeAutoCalMonitor.SOURCE_NATIVE_AUTOCAL)
                    .put("sourceFuel", pendingEvent.sourceFuel.name)
                    .put("sessionId", sessionId)
                    .put("snapshotId", snapshotId)
                    .put("snapshotHash", snapshotHash)
                    .put("fuel", fuelLabel)
                    .put("bandIndex", transition.bandIndex)
                    .put("zone", transition.zone)
                    .put("previousCounter", transition.previousCounter)
                    .put("counter", transition.counter)
                    .put("threshold", transition.threshold)
                    .put("previousObservedAtElapsedMs", transition.previousObservedAtElapsedMs)
                    .put("observedAtElapsedMs", transition.observedAtElapsedMs)
                    .put("counterPayloadHex", pendingEvent.counterPayloadHex)
                    .put("timeRaw", point?.opt("timeRaw") ?: JSONObject.NULL)
                    .put("timeMs", nativePetrolMs ?: JSONObject.NULL)
                    .put("mapRaw", point?.opt("mapRaw") ?: JSONObject.NULL)
                    .put("mapBar", nativeMapBar ?: JSONObject.NULL)
                    .put("nativeState", point?.optString("state") ?: "VALIDO_POR_CONTADOR")
                    .put("nativeValidity", true)
                    .put("correlationState", correlation.state)
                    .put("correlationReason", correlation.reason)
                    .put("correlationConfidence", correlation.confidence)
                    .put("rpmConfidence", correlation.rpmConfidence)
                    .put("rpm", correlation.rpm ?: JSONObject.NULL)
                    .put("correlatedMapBar", correlation.mapBar ?: JSONObject.NULL)
                    .put("correlatedPetrolMs", correlation.petrolMs ?: JSONObject.NULL)
                    .put("correlatedGasMs", correlation.gasMsDiagnostic ?: JSONObject.NULL)
                    .put("correlatedFuel", correlation.sourceFuel.name)
                    .put("correlatedFrameElapsedMs", correlation.correlatedFrameElapsedMs ?: JSONObject.NULL)
                    .put("correlationLagMs", correlation.lagMs ?: JSONObject.NULL)
                    .put("windowFromElapsedMs", correlation.windowFromElapsedMs)
                    .put("windowToElapsedMs", correlation.windowToElapsedMs)
                    .put("firstTelemetrySequence", correlation.firstSequence ?: JSONObject.NULL)
                    .put("lastTelemetrySequence", correlation.lastSequence ?: JSONObject.NULL)
                    .put("matchedTelemetryFrames", correlation.matchedFrames)
                    .put("overlapKey", correlation.overlapKey ?: JSONObject.NULL)
                    .put("canCloseWindowEarly", correlation.canCloseWindowEarly)
                    .put("rawOnly", correlation.state != "CORRELATED")
                    .put("appWritePerformed", false)
                    .put("appAutomaticWrite", false),
            )
        }
        return result
    }

    private fun JSONObject.findPoint(fuel: String, bandIndex: Int): JSONObject? {
        val points = optJSONArray("points") ?: return null
        repeat(points.length()) { index ->
            val point = points.optJSONObject(index) ?: return@repeat
            if (point.optString("fuel") == fuel && !point.optBoolean("previous", false) &&
                point.optInt("index", -1) == bandIndex
            ) return point
        }
        return null
    }

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null
}
