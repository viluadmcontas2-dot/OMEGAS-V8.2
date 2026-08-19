package com.omegas.prohub.autocal

import android.os.SystemClock
import com.omegas.prohub.calibration.CalibrationIdentity
import com.omegas.prohub.calibration.CompositeCalibrationReader
import com.omegas.prohub.calibration.CompositeCalibrationSnapshot
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.learning.LearningToleranceSettings
import com.omegas.prohub.learning.NativeAutoCalAnchorCorrelator
import com.omegas.prohub.usb.UsbProtocolReply
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
    private val autoMatchCounterTracker = NativeAutoMatchCounterTracker()
    private val calibrationBootstrapReader = CompositeCalibrationReader(serial)
    private val probeMetrics = AutoCalProbeMetrics()
    private val probeCadencePolicy = AutoCalProbeCadencePolicy()

    @Volatile private var sessionId = 0L
    @Volatile private var latestSnapshot = JSONObject().put("available", false)
    @Volatile private var state = baseState("IDLE", "AutoCal nativo aguardando ECU")
    @Volatile private var calibrationIdentity: CalibrationIdentity? = null
    @Volatile private var latestAutoMatchEvent: JSONObject? = null

    private var sessionStartedAtElapsedMs = 0L
    private var calibrationBootstrapAttempted = false
    private var lastProbe: AutoCalProtocol.NativeStatus? = null
    private var nextStatusProbeDueAtElapsedMs = 0L
    private var lastMulActHash = ""
    private var snapshotRequested = false
    private var snapshotReason = ""
    private var gasLowThreshold: Int? = null
    private var gasNormalThreshold: Int? = null
    private var autoCalEnabled: Int? = null
    private var pendingMaturity = emptyList<PendingMaturity>()

    fun beginUsbSession(newSessionId: Long) {
        probeMetrics.reset()
        autoMatchCounterTracker.reset()
        synchronized(lock) {
            sessionId = newSessionId
            sessionStartedAtElapsedMs = if (newSessionId > 0L) SystemClock.elapsedRealtime() else 0L
            calibrationBootstrapAttempted = false
            calibrationIdentity = null
            lastProbe = null
            latestAutoMatchEvent = null
            nextStatusProbeDueAtElapsedMs = 0L
            lastMulActHash = ""
            gasLowThreshold = null
            gasNormalThreshold = null
            autoCalEnabled = null
            pendingMaturity = emptyList()
            maturityTracker.reset()
            // Nunca iniciar uma aquisição pesada só porque a porta acabou de abrir.
            // Primeiro a telemetria precisa provar que a sessão está estável.
            snapshotRequested = false
            snapshotReason = ""
            latestSnapshot = JSONObject().put("available", false).put("sessionId", newSessionId)
            state = baseState("WAITING_TELEMETRY_SETTLE", "Aguardando telemetria estabilizar antes do AutoCal")
                .put("sessionId", newSessionId)
                .put("settleMs", SESSION_SETTLE_MS)
        }
        onStateChanged()
    }

    fun endUsbSession() {
        probeMetrics.reset()
        autoMatchCounterTracker.reset()
        synchronized(lock) {
            sessionId = 0L
            sessionStartedAtElapsedMs = 0L
            calibrationBootstrapAttempted = false
            calibrationIdentity = null
            lastProbe = null
            latestAutoMatchEvent = null
            nextStatusProbeDueAtElapsedMs = 0L
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
        synchronized(lock) {
            // ACK/readback da ação prova o writer, mas a identidade composta precisa
            // ser reconciliada novamente antes de qualquer gate físico futuro.
            calibrationIdentity = null
        }
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

        resolvePendingTelemetryGap()

        val startedAt = synchronized(lock) { sessionStartedAtElapsedMs }
        val ageMs = if (startedAt > 0L) (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L) else Long.MAX_VALUE
        if (ageMs < SESSION_SETTLE_MS) {
            synchronized(lock) {
                state = baseState("WAITING_TELEMETRY_SETTLE", "Aguardando telemetria estabilizar antes do AutoCal")
                    .put("sessionId", currentSession)
                    .put("settleRemainingMs", (SESSION_SETTLE_MS - ageMs).coerceAtLeast(0L))
            }
            onStateChanged()
            return
        }

        val shouldBootstrap = synchronized(lock) {
            if (!calibrationBootstrapAttempted) {
                calibrationBootstrapAttempted = true
                true
            } else false
        }
        if (shouldBootstrap) {
            try {
                val raw = calibrationBootstrapReader.readAtSessionStart(currentSession)
                val composite = CompositeCalibrationSnapshot.promote(raw)
                val identity = CalibrationIdentity.fromComposite(
                    composite = composite,
                    capturedAtMs = System.currentTimeMillis(),
                    mapRevision = null,
                    curveRevision = null,
                )
                synchronized(lock) {
                    calibrationIdentity = identity
                    state = baseState("CALIBRATION_READY", "Calibração física confirmada para esta sessão")
                        .put("sessionId", currentSession)
                        .put("calibrationFingerprint", identity.functionFingerprint)
                        .put("calibrationCompleteness", identity.completeness.name)
                        .put("calibrationFreshness", identity.freshness.name)
                }
            } catch (error: Exception) {
                synchronized(lock) {
                    calibrationIdentity = null
                    state = baseState("CALIBRATION_BOOTSTRAP_FAILED", error.message ?: "Falha ao confirmar calibração física")
                        .put("sessionId", currentSession)
                }
                onStateChanged()
                return
            }
        }

        val previousProbe = synchronized(lock) { lastProbe }
        val now = SystemClock.elapsedRealtime()
        val statusProbeDue = previousProbe == null || synchronized(lock) {
            nextStatusProbeDueAtElapsedMs <= 0L || now >= nextStatusProbeDueAtElapsedMs
        }
        val freshProbe = if (statusProbeDue) probe(currentSession) ?: return else null
        val probe = freshProbe ?: previousProbe ?: return
        val autoMatchEvent = freshProbe?.let {
            autoMatchCounterTracker.observe(
                currentSessionId = currentSession,
                count = it.autoMatchCount,
                observedAtElapsedMs = now,
            )
        }
        val countIncreased = autoMatchEvent != null
        // O primeiro probe apenas estabelece baseline. Não autoriza snapshot pesado.
        val probeChanged = freshProbe != null && previousProbe != null && (
            previousProbe.autoMatchCount != probe.autoMatchCount ||
                previousProbe.nativeFlag13 != probe.nativeFlag13
        )
        if (probeChanged) probeMetrics.markMaterialChange()
        if (freshProbe != null) scheduleNextStatusProbe(SystemClock.elapsedRealtime())

        val thresholds = synchronized(lock) { Triple(gasLowThreshold, gasNormalThreshold, autoCalEnabled) }
        val thresholdsReady = thresholds.first != null && thresholds.second != null && thresholds.third == 1
        // A cadence adaptativa vale somente para o status compacto. Maturity continua
        // no health tick existente para não esconder progressão física das bandas.
        val maturityProbe = if (thresholdsReady) probeMaturityCounters(currentSession) else null
        val maturityEvents = maturityProbe?.let { observed ->
            maturityTracker.observe(
                counters = observed.counters,
                gasLowThreshold = thresholds.first,
                gasNormalThreshold = thresholds.second,
                enabled = true,
                observedAtElapsedMs = observed.observedAtElapsedMs,
            ).map { transition -> PendingMaturity(transition, observed.payloadHex) }
        }.orEmpty()

        synchronized(lock) {
            if (freshProbe != null) lastProbe = probe
            if (autoMatchEvent != null) latestAutoMatchEvent = autoMatchEventJson(autoMatchEvent)
            state = baseState("MONITORING", "AutoCal nativo monitorado")
                .put("sessionId", currentSession)
                .put("nativeFlag13", probe.nativeFlag13)
                .put("autoMatchCount", probe.autoMatchCount)
                .put("latestAutoMatchEvent", latestAutoMatchEvent?.let { JSONObject(it.toString()) } ?: JSONObject.NULL)
                .put("fallback", probe.nativeFlag13 < 0)
                .put("freshStatusProbe", freshProbe != null)
                .put("thresholdsReady", thresholdsReady)
                .put("maturityProbe", maturityProbe != null)
                .put("calibrationIdentityReady", calibrationIdentity?.materiallyUsable() == true)
                .put("calibrationFingerprint", calibrationIdentity?.functionFingerprint ?: JSONObject.NULL)
            if (maturityEvents.isNotEmpty()) {
                pendingMaturity = maturityEvents
                snapshotRequested = true
                snapshotReason = "NATIVE_BAND_MATURED"
            } else if (probeChanged) {
                snapshotRequested = true
                snapshotReason = if (autoMatchEvent != null) "AUTOMATCH_COUNT_CHANGED" else "NATIVE_STATUS_CHANGED"
                // Mudança nativa observada torna a identidade composta anterior obsoleta.
                calibrationIdentity = null
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
            .put("calibrationBootstrapAttempted", calibrationBootstrapAttempted)
            .put("calibrationIdentityReady", calibrationIdentity?.materiallyUsable() == true)
            .put("calibrationFingerprint", calibrationIdentity?.functionFingerprint ?: JSONObject.NULL)
            .put("latestAutoMatchEvent", latestAutoMatchEvent?.let { JSONObject(it.toString()) } ?: JSONObject.NULL)
            .put("probeMetrics", probeMetricsJson())
            .put("appAutomaticWrite", false)
            .put("manualAutoMatchExposed", false)
    }

    fun latestSnapshotJson(): JSONObject = synchronized(lock) { JSONObject(latestSnapshot.toString()) }

    private fun probe(expectedSessionId: Long): AutoCalProtocol.NativeStatus? {
        val cycleStarted = SystemClock.elapsedRealtime()
        val telemetryBefore = latestTelemetryElapsedMs(cycleStarted)
        var requestBytes = 0
        var responseBytes = 0
        var serialElapsedMs = 0L

        val compactRequest = AutoCalProtocol.CMD_NATIVE_STATUS
        requestBytes += compactRequest.size
        val compact = serial.transaction(
            request = compactRequest,
            reason = "AutoCal status leve",
            timeoutMs = 700,
            purgeBefore = false,
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
        )
        responseBytes += responseByteCount(compact)
        serialElapsedMs += compact.elapsedMs.coerceAtLeast(0L)
        if (compact.ok) {
            try {
                val decoded = AutoCalProtocol.decodeNativeStatus(compact.status, compact.payload)
                recordProbeCycle(
                    cycleStarted = cycleStarted,
                    requestBytes = requestBytes,
                    responseBytes = responseBytes,
                    serialElapsedMs = serialElapsedMs,
                    success = true,
                    fallbackUsed = false,
                    telemetryBefore = telemetryBefore,
                )
                return decoded
            } catch (_: Exception) {
                // Fallback abaixo: não atribuir semântica a payload divergente.
            }
        }

        val explicitRequest = AutoCalProtocol.read(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED)
        requestBytes += explicitRequest.size
        val explicit = serial.transaction(
            request = explicitRequest,
            reason = "AutoCal fallback contador 0x0174",
            timeoutMs = 900,
            purgeBefore = false,
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
        )
        responseBytes += responseByteCount(explicit)
        serialElapsedMs += explicit.elapsedMs.coerceAtLeast(0L)
        if (!explicit.ok) {
            recordProbeCycle(
                cycleStarted = cycleStarted,
                requestBytes = requestBytes,
                responseBytes = responseBytes,
                serialElapsedMs = serialElapsedMs,
                success = false,
                fallbackUsed = true,
                telemetryBefore = telemetryBefore,
            )
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
            val status = AutoCalProtocol.NativeStatus(
                nativeFlag13 = -1,
                autoMatchCount = decoded.rawValues.single(),
                rawPayload = explicit.payload.copyOf(),
            )
            recordProbeCycle(
                cycleStarted = cycleStarted,
                requestBytes = requestBytes,
                responseBytes = responseBytes,
                serialElapsedMs = serialElapsedMs,
                success = true,
                fallbackUsed = true,
                telemetryBefore = telemetryBefore,
            )
            status
        } catch (error: Exception) {
            recordProbeCycle(
                cycleStarted = cycleStarted,
                requestBytes = requestBytes,
                responseBytes = responseBytes,
                serialElapsedMs = serialElapsedMs,
                success = false,
                fallbackUsed = true,
                telemetryBefore = telemetryBefore,
            )
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
            purgeBefore = false,
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
                purgeBefore = false,
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
                .put("latestAutoMatchEvent", latestAutoMatchEvent?.let { JSONObject(it.toString()) } ?: JSONObject.NULL)
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

    private fun resolvePendingTelemetryGap() {
        val now = SystemClock.elapsedRealtime()
        val frames = serial.recentTelemetryFrames(
            fromElapsedMs = (now - PROBE_METRICS_WINDOW_MS).coerceAtLeast(0L),
            toElapsedMs = now,
        )
        probeMetrics.resolveTelemetryGap(frames.map { it.elapsedMs })
    }

    private fun latestTelemetryElapsedMs(now: Long): Long? =
        serial.recentTelemetryFrames(
            fromElapsedMs = (now - PROBE_METRICS_WINDOW_MS).coerceAtLeast(0L),
            toElapsedMs = now,
        ).lastOrNull()?.elapsedMs

    private fun recordProbeCycle(
        cycleStarted: Long,
        requestBytes: Int,
        responseBytes: Int,
        serialElapsedMs: Long,
        success: Boolean,
        fallbackUsed: Boolean,
        telemetryBefore: Long?,
    ) {
        probeMetrics.recordCycle(
            startedAtElapsedMs = cycleStarted,
            finishedAtElapsedMs = SystemClock.elapsedRealtime(),
            requestBytes = requestBytes,
            responseBytes = responseBytes,
            serialElapsedMs = serialElapsedMs,
            success = success,
            fallbackUsed = fallbackUsed,
            lastTelemetryBeforeMs = telemetryBefore,
        )
    }

    private fun scheduleNextStatusProbe(observedAtElapsedMs: Long) {
        val recommended = probeCadencePolicy.recommend(probeMetrics.snapshot()).recommendedCadenceMs.coerceAtLeast(1L)
        synchronized(lock) {
            val phaseBase = nextStatusProbeDueAtElapsedMs.takeIf { it > 0L } ?: observedAtElapsedMs
            val overdue = (observedAtElapsedMs - phaseBase).coerceAtLeast(0L)
            val steps = overdue / recommended + 1L
            nextStatusProbeDueAtElapsedMs = phaseBase + steps * recommended
        }
    }

    private fun responseByteCount(reply: UsbProtocolReply): Int =
        reply.echo.size + reply.rawResponse.size

    private fun autoMatchEventJson(event: NativeAutoMatchCounterTracker.Event): JSONObject = JSONObject()
        .put("eventType", event.eventType)
        .put("sessionId", event.sessionId)
        .put("observedAtElapsedMs", event.observedAtElapsedMs)
        .put("beforeCount", event.beforeCount)
        .put("afterCount", event.afterCount)
        .put("delta", event.delta)
        .put("mulActChangeConfirmed", event.mulActChangeConfirmed)
        .put("appWritePerformed", false)
        .put("appAutomaticWrite", false)

    private fun probeMetricsJson(): JSONObject {
        val metrics = probeMetrics.snapshot()
        val cadence = probeCadencePolicy.recommend(metrics)
        return JSONObject()
            .put("schema", "autocal-probe-cost-v2")
            .put("cycles", metrics.cycles)
            .put("successfulCycles", metrics.successfulCycles)
            .put("fallbackCycles", metrics.fallbackCycles)
            .put("materialChanges", metrics.materialChanges)
            .put("requestBytes", metrics.requestBytes)
            .put("responseBytes", metrics.responseBytes)
            .put("serialElapsedMs", metrics.serialElapsedMs)
            .put("wallElapsedMs", metrics.wallElapsedMs)
            .put("lastWallElapsedMs", metrics.lastWallElapsedMs)
            .put("maxWallElapsedMs", metrics.maxWallElapsedMs)
            .put("observationSpanMs", metrics.observationSpanMs)
            .put("averageWallElapsedMs", metrics.averageWallElapsedMs ?: JSONObject.NULL)
            .put("lastCadenceMs", metrics.lastCadenceMs ?: JSONObject.NULL)
            .put("lastTelemetryGapMs", metrics.lastTelemetryGapMs ?: JSONObject.NULL)
            .put("maxTelemetryGapMs", metrics.maxTelemetryGapMs ?: JSONObject.NULL)
            .put("pendingTelemetryGap", metrics.pendingTelemetryGap)
            .put("informationYield", metrics.informationYield)
            .put("lastCostShare", metrics.lastCostShare ?: JSONObject.NULL)
            .put("recommendedCadenceMs", cadence.recommendedCadenceMs)
            .put("averageProbeCostMs", cadence.averageProbeCostMs)
            .put("posteriorEventRatePerSecond", cadence.posteriorEventRatePerSecond)
            .put("costRatioToPrior", cadence.costRatioToPrior)
            .put("eventRateRatioToPrior", cadence.eventRateRatioToPrior)
            .put("priorMeanCadenceMs", cadence.priorMeanCadenceMs)
            .put("priorProvenance", cadence.priorProvenance)
            .put("nextStatusProbeDueAtElapsedMs", nextStatusProbeDueAtElapsedMs.takeIf { it > 0L } ?: JSONObject.NULL)
            .put("measurementAvailable", metrics.cycles > 0L)
            .put("policyApplied", true)
            .put("cadenceAuthority", "COST_INFORMATION_POLICY")
            .put("opportunityClock", "SERVICE_HEALTH_TICK")
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
        private const val SESSION_SETTLE_MS = 8_000L
        private const val PROBE_METRICS_WINDOW_MS = 10_000L
    }
}
