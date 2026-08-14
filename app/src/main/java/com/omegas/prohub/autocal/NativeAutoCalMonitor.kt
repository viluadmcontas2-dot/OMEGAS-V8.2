package com.omegas.prohub.autocal

import android.os.SystemClock
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.learning.LearningToleranceSettings
import com.omegas.prohub.learning.NativeAutoCalAnchorCorrelator
import org.json.JSONArray
import org.json.JSONObject

/**
 * Observa a Auto Calibration nativa sem possuir timer, thread serial ou writer.
 *
 * O serviço chama [tick] em seu health tick já existente. Toda I/O passa pelo
 * scheduler MP48 único. O probe 48 0B acompanha status global; NUM_BUF_UPD_GAS
 * detecta maturidade por banda; snapshot completo só é lido por evento.
 * AutoMatch continua sendo executado exclusivamente pela ECU.
 */
class NativeAutoCalMonitor(
    private val serial: Mp48SerialScheduler,
    private val calibrationBusy: () -> Boolean,
    private val onFreshSnapshot: (JSONObject) -> Unit = {},
    private val onNativeCalibrationObserved: (JSONObject) -> Unit = {},
    private val onStateChanged: () -> Unit = {},
) {
    private data class PendingMaturity(
        val transition: NativeAutoCalMaturityTracker.Transition,
        val counterPayloadHex: String,
    )

    private data class MaturityProbe(
        val counters: IntArray,
        val payloadHex: String,
        val observedAtElapsedMs: Long,
    )

    private val lock = Any()
    private val maturityTracker = NativeAutoCalMaturityTracker()

    @Volatile private var sessionId = 0L
    @Volatile private var latestSnapshot = JSONObject().put("available", false)
    @Volatile private var state = baseState("IDLE", "AutoCal nativo aguardando ECU")

    private var lastProbe: AutoCalProtocol.NativeStatus? = null
    private var lastMulActHash = ""
    private var snapshotRequested = false
    private var snapshotReason = ""
    private var gasLowThreshold: Int? = null
    private var gasNormalThreshold: Int? = null
    private var autoCalEnabled: Int? = null
    private var pendingMaturity = emptyList<PendingMaturity>()

    fun beginUsbSession(newSessionId: Long) {
        synchronized(lock) {
            sessionId = newSessionId
            lastProbe = null
            lastMulActHash = ""
            gasLowThreshold = null
            gasNormalThreshold = null
            autoCalEnabled = null
            pendingMaturity = emptyList()
            maturityTracker.reset()
            snapshotRequested = newSessionId > 0L
            snapshotReason = "USB_SESSION_STARTED"
            latestSnapshot = JSONObject().put("available", false).put("sessionId", newSessionId)
            state = baseState("WAITING_PROBE", "Aguardando status AutoCal da sessão")
                .put("sessionId", newSessionId)
        }
        onStateChanged()
    }

    fun endUsbSession() {
        synchronized(lock) {
            sessionId = 0L
            lastProbe = null
            lastMulActHash = ""
            gasLowThreshold = null
            gasNormalThreshold = null
            autoCalEnabled = null
            pendingMaturity = emptyList()
            maturityTracker.reset()
            snapshotRequested = false
            snapshotReason = ""
            latestSnapshot = JSONObject().put("available", false)
            state = baseState("DISCONNECTED", "USB desconectado")
        }
        onStateChanged()
    }

    fun requestSnapshot(reason: String) {
        synchronized(lock) {
            snapshotRequested = true
            snapshotReason = reason.take(80)
        }
    }

    fun onManualActionConfirmed(receipt: JSONObject) {
        requestSnapshot("ACTION_${receipt.optString("action", "UNKNOWN")}")
        val beforeMul = mulActRawFromSnapshot(receipt.optJSONObject("before"))
        val afterMul = mulActRawFromSnapshot(receipt.optJSONObject("after"))
        if (beforeMul.isNotBlank() && afterMul.isNotBlank() && beforeMul != afterMul &&
            receipt.optBoolean("readbackValid", false)
        ) {
            onNativeCalibrationObserved(
                JSONObject()
                    .put("source", SOURCE_NATIVE_AUTOCAL)
                    .put("calibrationType", "K_FACTOR")
                    .put("cause", "MANUAL_AUTOCAL_ACTION")
                    .put("action", receipt.optString("action"))
                    .put("oldHash", beforeMul)
                    .put("newHash", afterMul)
                    .put("readbackValid", true)
                    .put("humanConfirmed", true)
                    .put("ecuNativeObserved", true)
                    .put("appWritePerformed", true)
                    .put("appAutomaticWrite", false),
            )
        }
    }

    fun tick() {
        if (!serial.isConnected()) {
            if (sessionId != 0L) endUsbSession()
            return
        }
        val currentSession = serial.currentSessionId()
        if (currentSession <= 0L) return
        if (currentSession != sessionId) beginUsbSession(currentSession)
        if (calibrationBusy()) return

        val previousProbe = synchronized(lock) { lastProbe }
        val probe = probe(currentSession) ?: return
        val countIncreased = previousProbe != null && probe.autoMatchCount > previousProbe.autoMatchCount
        val probeChanged = previousProbe == null ||
            previousProbe.autoMatchCount != probe.autoMatchCount ||
            previousProbe.nativeFlag13 != probe.nativeFlag13

        val maturityProbe = probeMaturityCounters(currentSession)
        val maturityEvents = maturityProbe?.let { observed ->
            val thresholds = synchronized(lock) { Triple(gasLowThreshold, gasNormalThreshold, autoCalEnabled) }
            maturityTracker.observe(
                counters = observed.counters,
                gasLowThreshold = thresholds.first,
                gasNormalThreshold = thresholds.second,
                enabled = thresholds.third == 1,
                observedAtElapsedMs = observed.observedAtElapsedMs,
            ).map { transition -> PendingMaturity(transition, observed.payloadHex) }
        }.orEmpty()

        synchronized(lock) {
            lastProbe = probe
            state = baseState("MONITORING", "AutoCal nativo monitorado")
                .put("sessionId", currentSession)
                .put("nativeFlag13", probe.nativeFlag13)
                .put("autoMatchCount", probe.autoMatchCount)
                .put("fallback", probe.nativeFlag13 < 0)
                .put("maturityProbe", maturityProbe != null)
            if (maturityEvents.isNotEmpty()) {
                pendingMaturity = maturityEvents
                snapshotRequested = true
                snapshotReason = "NATIVE_BAND_MATURED"
            } else if (probeChanged) {
                snapshotRequested = true
                snapshotReason = if (countIncreased) "AUTOMATCH_COUNT_CHANGED" else "NATIVE_STATUS_CHANGED"
            }
        }

        val shouldSnapshot = synchronized(lock) { snapshotRequested }
        if (shouldSnapshot) {
            readFullSnapshot(currentSession, probe, countIncreased)
        } else {
            onStateChanged()
        }
    }

    fun statusJson(): JSONObject = synchronized(lock) {
        JSONObject(state.toString())
            .put("latestSnapshot", JSONObject(latestSnapshot.toString()))
            .put("snapshotRequested", snapshotRequested)
            .put("snapshotReason", snapshotReason)
            .put("appAutomaticWrite", false)
            .put("manualAutoMatchExposed", false)
    }

    fun latestSnapshotJson(): JSONObject = synchronized(lock) { JSONObject(latestSnapshot.toString()) }

    private fun probe(expectedSessionId: Long): AutoCalProtocol.NativeStatus? {
        val compact = serial.transaction(
            request = AutoCalProtocol.CMD_NATIVE_STATUS,
            reason = "AutoCal status leve",
            timeoutMs = 700,
            purgeBefore = false,
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
        )
        if (compact.ok) {
            try {
                return AutoCalProtocol.decodeNativeStatus(compact.status, compact.payload)
            } catch (_: Exception) {
                // Fallback abaixo: não atribuir semântica a payload divergente.
            }
        }

        val explicit = serial.transaction(
            request = AutoCalProtocol.read(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED),
            reason = "AutoCal fallback contador 0x0174",
            timeoutMs = 900,
            purgeBefore = true,
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
        )
        if (!explicit.ok) {
            synchronized(lock) {
                state = baseState("PROBE_FAILED", explicit.error.ifBlank { "Status AutoCal indisponível" })
                    .put("sessionId", expectedSessionId)
            }
            onStateChanged()
            return null
        }
        return try {
            val decoded = AutoCalProtocol.decode(
                AutoCalProtocol.NUM_AUTOMATCH_EXECUTED,
                explicit.status,
                explicit.payload,
            )
            AutoCalProtocol.NativeStatus(
                nativeFlag13 = -1,
                autoMatchCount = decoded.rawValues.single(),
                rawPayload = explicit.payload.copyOf(),
            )
        } catch (error: Exception) {
            synchronized(lock) {
                state = baseState("PROBE_FAILED", error.message ?: "Fallback AutoCal inválido")
                    .put("sessionId", expectedSessionId)
            }
            onStateChanged()
            null
        }
    }

    private fun probeMaturityCounters(expectedSessionId: Long): MaturityProbe? {
        val reply = serial.transaction(
            request = AutoCalProtocol.read(AutoCalProtocol.NUM_BUF_UPD_GAS),
            reason = "AutoCal maturidade GNV",
            timeoutMs = 900,
            purgeBefore = true,
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
        )
        if (!reply.ok) return null
        return try {
            val decoded = AutoCalProtocol.decode(
                AutoCalProtocol.NUM_BUF_UPD_GAS,
                reply.status,
                reply.payload,
            )
            MaturityProbe(
                counters = decoded.rawValues.copyOf(),
                payloadHex = reply.payload.toHex(),
                observedAtElapsedMs = SystemClock.elapsedRealtime(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readFullSnapshot(
        expectedSessionId: Long,
        probe: AutoCalProtocol.NativeStatus,
        countIncreased: Boolean,
    ) {
        val reason = synchronized(lock) { snapshotReason }
        val started = System.currentTimeMillis()
        val observations = AutoCalProtocol.READ_ONLY_FIELDS.distinctBy { it.identity }.map { field ->
            val reply = serial.transaction(
                request = AutoCalProtocol.read(field),
                reason = "AutoCal snapshot ${field.key}",
                timeoutMs = 1_200,
                purgeBefore = true,
                expectedSessionId = expectedSessionId,
                workClass = Mp48WorkClass.READ_ONLY,
            )
            AutoCalReadObservation(
                field = field,
                status = reply.status.takeIf { it >= 0 },
                payload = reply.payload.takeIf { it.isNotEmpty() },
                capturedAtMs = System.currentTimeMillis(),
                error = if (reply.ok) null else reply.error.ifBlank { "Campo não confirmado" },
            )
        }
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = observations,
            expectedFields = AutoCalProtocol.READ_ONLY_FIELDS,
            sessionId = "AUTOCAL-$expectedSessionId-${System.currentTimeMillis()}",
            source = AutoCalSnapshotSource.ECU_READ,
            startedAtMs = started,
            finishedAtMs = System.currentTimeMillis(),
        )
        val enabled = scalar(snapshot, AutoCalProtocol.AUTO_CAL_ENABLE)
        val maxAutomatch = scalar(snapshot, AutoCalProtocol.MAX_AUTOMATCH)
        val mulActHash = snapshot.field(AutoCalProtocol.MUL_ACT)
            ?.takeIf { it.status == AutoCalFieldStatus.VALID }
            ?.rawPayloadHex
            .orEmpty()
        val decorated = snapshot.toJson()
            .put("available", true)
            .put("nativeAutoCal", true)
            .put("nativeStatus", JSONObject()
                .put("nativeFlag13", probe.nativeFlag13)
                .put("autoMatchCount", probe.autoMatchCount))
            .put("autoCalEnabled", enabled ?: JSONObject.NULL)
            .put("maxAutomatch", maxAutomatch ?: JSONObject.NULL)
            .put("frozen", enabled == 0)
            .put("freshAcquisition", enabled == 1)
            .put("snapshotReason", reason)
            .put("appAutomaticWrite", false)
            .put("manualAutoMatchExposed", false)

        val acquisition = AutoCalAcquisition.fromSnapshot(decorated)
        val thresholds = acquisition.optJSONObject("thresholds") ?: JSONObject()
        val newGasLowThreshold = thresholds.nullableInt("gasLow")
        val newGasNormalThreshold = thresholds.nullableInt("gasNormal")
        val pending = synchronized(lock) { pendingMaturity.toList() }
        val maturityEvents = JSONArray()
        if (enabled == 1) {
            pending.forEach { pendingEvent ->
                val transition = pendingEvent.transition
                val point = acquisition.findCurrentGasPoint(transition.bandIndex)
                val nativePetrolMs = point?.nullableDouble("timeMs")
                val nativeMapBar = point?.nullableDouble("mapBar")
                val frames = serial.recentTelemetryFrames(
                    fromElapsedMs = transition.previousObservedAtElapsedMs,
                    toElapsedMs = transition.observedAtElapsedMs,
                )
                val correlation = NativeAutoCalAnchorCorrelator.correlate(
                    frames = frames,
                    nativePetrolMs = nativePetrolMs,
                    nativeMapBar = nativeMapBar,
                    observedAtElapsedMs = transition.observedAtElapsedMs,
                    policy = LearningToleranceSettings.current,
                    sessionId = expectedSessionId,
                )
                maturityEvents.put(
                    JSONObject()
                        .put("eventType", "NATIVE_BAND_MATURED")
                        .put("source", SOURCE_NATIVE_AUTOCAL)
                        .put("sessionId", expectedSessionId)
                        .put("snapshotId", decorated.optString("sessionId"))
                        .put("snapshotHash", snapshot.snapshotHash)
                        .put("fuel", "GNV")
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
                        .put("correlatedFuel", correlation.fuel ?: JSONObject.NULL)
                        .put("correlatedFrameElapsedMs", correlation.correlatedFrameElapsedMs ?: JSONObject.NULL)
                        .put("correlationLagMs", correlation.lagMs ?: JSONObject.NULL)
                        .put("firstTelemetrySequence", correlation.firstSequence ?: JSONObject.NULL)
                        .put("lastTelemetrySequence", correlation.lastSequence ?: JSONObject.NULL)
                        .put("matchedTelemetryFrames", correlation.matchedFrames)
                        .put("rawOnly", correlation.state != "CORRELATED")
                        .put("appWritePerformed", false)
                        .put("appAutomaticWrite", false),
                )
            }
        }
        decorated
            .put("nativeMaturityEvents", maturityEvents)
            .put("nativeMaturityEventCount", maturityEvents.length())

        val currentCounters = vector(snapshot, AutoCalProtocol.NUM_BUF_UPD_GAS)
        if (pending.isEmpty() || enabled != 1) {
            currentCounters?.let { maturityTracker.baseline(it, SystemClock.elapsedRealtime()) }
        }

        val previousMul = synchronized(lock) { lastMulActHash }
        synchronized(lock) {
            latestSnapshot = decorated
            if (mulActHash.isNotBlank()) lastMulActHash = mulActHash
            gasLowThreshold = newGasLowThreshold
            gasNormalThreshold = newGasNormalThreshold
            autoCalEnabled = enabled
            pendingMaturity = emptyList()
            snapshotRequested = false
            snapshotReason = ""
            state = baseState(if (enabled == 0) "PAUSED" else "READY", if (enabled == 0) "AutoCal pausado; dados congelados" else "AutoCal nativo acompanhado")
                .put("sessionId", expectedSessionId)
                .put("nativeFlag13", probe.nativeFlag13)
                .put("autoMatchCount", probe.autoMatchCount)
                .put("maxAutomatch", maxAutomatch ?: JSONObject.NULL)
                .put("autoCalEnabled", enabled ?: JSONObject.NULL)
                .put("nativeMaturityEventCount", maturityEvents.length())
                .put("snapshotHash", snapshot.snapshotHash)
        }

        if (enabled == 1) {
            try { onFreshSnapshot(decorated) } catch (_: Exception) {}
        }
        if (countIncreased && previousMul.isNotBlank() && mulActHash.isNotBlank() && previousMul != mulActHash) {
            try {
                onNativeCalibrationObserved(
                    JSONObject()
                        .put("source", SOURCE_NATIVE_AUTOCAL)
                        .put("calibrationType", "K_FACTOR")
                        .put("cause", "ECU_AUTOMATCH_COUNT_CHANGED")
                        .put("oldHash", previousMul)
                        .put("newHash", mulActHash)
                        .put("nativeAutoMatchCount", probe.autoMatchCount)
                        .put("maxAutomatch", maxAutomatch ?: JSONObject.NULL)
                        .put("nativeFlag13", probe.nativeFlag13)
                        .put("readbackValid", true)
                        .put("humanConfirmed", false)
                        .put("ecuNativeObserved", true)
                        .put("appWritePerformed", false)
                        .put("ecuNativeAutomatic", true)
                        .put("appAutomaticWrite", false),
                )
            } catch (_: Exception) {}
        }
        onStateChanged()
    }

    private fun scalar(snapshot: AutoCalSnapshot, field: AutoCalProtocol.Field): Int? =
        snapshot.field(field)
            ?.takeIf { it.status == AutoCalFieldStatus.VALID }
            ?.rawValues
            ?.singleOrNull()

    private fun vector(snapshot: AutoCalSnapshot, field: AutoCalProtocol.Field): IntArray? =
        snapshot.field(field)
            ?.takeIf { it.status == AutoCalFieldStatus.VALID }
            ?.rawValues
            ?.copyOf()

    private fun JSONObject.findCurrentGasPoint(bandIndex: Int): JSONObject? {
        val points = optJSONArray("points") ?: return null
        repeat(points.length()) { index ->
            val point = points.optJSONObject(index) ?: return@repeat
            if (point.optString("fuel") == "GNV" && !point.optBoolean("previous", false) &&
                point.optInt("index", -1) == bandIndex
            ) return point
        }
        return null
    }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }

    private fun mulActRawFromSnapshot(snapshot: JSONObject?): String {
        val fields = snapshot?.optJSONArray("fields") ?: return ""
        repeat(fields.length()) { index ->
            val field = fields.optJSONObject(index) ?: return@repeat
            if (field.optString("key") == AutoCalProtocol.MUL_ACT.key && field.optString("status") == AutoCalFieldStatus.VALID.name) {
                return field.optString("rawPayloadHex")
            }
        }
        return ""
    }

    private fun baseState(name: String, message: String): JSONObject = JSONObject()
        .put("state", name)
        .put("message", message)
        .put("updatedAt", System.currentTimeMillis())
        .put("appAutomaticWrite", false)
        .put("nativeAutoMatchInsideEcu", true)

    companion object {
        const val SOURCE_NATIVE_AUTOCAL = "ECU_NATIVE_AUTOCAL"
    }
}
