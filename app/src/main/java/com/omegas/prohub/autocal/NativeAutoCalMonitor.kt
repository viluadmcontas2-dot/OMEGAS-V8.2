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
import java.security.MessageDigest

/**
 * Observa a Auto Calibration nativa sem possuir timer, thread serial ou writer.
 *
 * O serviço chama [tick] em uma cadência compartilhada; o monitor não possui
 * thread nem timer. Toda I/O passa pelo scheduler MP48 único. O probe 48 0B
 * acompanha status global; um grupo leve renova contadores/zonas em ~2 s e um
 * grupo de referência renova eixos/curvas/MUL_ACT em ~4 s. Snapshot completo
 * continua reservado a bootstrap/eventos científicos.
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
        val status: Int,
        val payload: ByteArray,
    )

    private data class AcquisitionRefresh(
        val snapshot: AutoCalSnapshot,
        val gasProbe: MaturityProbe,
        val observedAtElapsedMs: Long,
    )

    private data class ReferenceRefresh(
        val snapshot: AutoCalSnapshot,
        val observedAtElapsedMs: Long,
    )

    private val lock = Any()
    private val maturityTracker = NativeAutoCalMaturityTracker()
    private val refreshPlanner = NativeAutoCalRefreshPlanner()

    @Volatile private var sessionId = 0L
    @Volatile private var latestSnapshot = JSONObject().put("available", false)
    @Volatile private var state = baseState("IDLE", "AutoCal nativo aguardando ECU")

    private var sessionStartedAtElapsedMs = 0L
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
            sessionStartedAtElapsedMs = if (newSessionId > 0L) SystemClock.elapsedRealtime() else 0L
            lastProbe = null
            lastMulActHash = ""
            gasLowThreshold = null
            gasNormalThreshold = null
            autoCalEnabled = null
            pendingMaturity = emptyList()
            maturityTracker.reset()
            refreshPlanner.reset()
            // Agenda o bootstrap, mas tick() preserva o gate SESSION_SETTLE_MS antes
            // de qualquer leitura pesada. Assim o primeiro probe estável sempre
            // produz um snapshot completo e a UI não fica presa sem thresholds.
            snapshotRequested = newSessionId > 0L
            snapshotReason = if (newSessionId > 0L) "SESSION_BOOTSTRAP" else ""
            latestSnapshot = JSONObject().put("available", false).put("sessionId", newSessionId)
            state = baseState("WAITING_TELEMETRY_SETTLE", "Aguardando telemetria estabilizar antes do AutoCal")
                .put("sessionId", newSessionId)
                .put("settleMs", SESSION_SETTLE_MS)
        }
        onStateChanged()
    }

    fun endUsbSession() {
        synchronized(lock) {
            sessionId = 0L
            sessionStartedAtElapsedMs = 0L
            lastProbe = null
            lastMulActHash = ""
            gasLowThreshold = null
            gasNormalThreshold = null
            autoCalEnabled = null
            pendingMaturity = emptyList()
            maturityTracker.reset()
            refreshPlanner.reset()
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

        val previousProbe = synchronized(lock) { lastProbe }
        val probe = probe(currentSession) ?: return
        val countIncreased = previousProbe != null && probe.autoMatchCount > previousProbe.autoMatchCount
        // O primeiro probe apenas estabelece baseline. Não autoriza snapshot pesado.
        val probeChanged = previousProbe != null && (
            previousProbe.autoMatchCount != probe.autoMatchCount ||
                previousProbe.nativeFlag13 != probe.nativeFlag13
        )

        val thresholds = synchronized(lock) { Triple(gasLowThreshold, gasNormalThreshold, autoCalEnabled) }
        val thresholdsReady = thresholds.first != null && thresholds.second != null && thresholds.third == 1
        val refreshDue = refreshPlanner.due(SystemClock.elapsedRealtime())
        val fullSnapshotAlreadyDue = synchronized(lock) { snapshotRequested } || probeChanged
        val acquisitionRefresh = if (!fullSnapshotAlreadyDue && thresholdsReady && refreshDue.acquisition) {
            refreshAcquisitionGroup(currentSession)
        } else null
        val maturityEvents = acquisitionRefresh?.gasProbe?.let { observed ->
            maturityTracker.observe(
                counters = observed.counters,
                gasLowThreshold = thresholds.first,
                gasNormalThreshold = thresholds.second,
                enabled = true,
                observedAtElapsedMs = observed.observedAtElapsedMs,
            ).map { transition -> PendingMaturity(transition, observed.payloadHex) }
        }.orEmpty()

        if (acquisitionRefresh != null) {
            mergeOperationalFields(
                patch = acquisitionRefresh.snapshot,
                refreshedAtElapsedMs = acquisitionRefresh.observedAtElapsedMs,
            )
            refreshPlanner.markAcquisition(acquisitionRefresh.observedAtElapsedMs)
        }

        val referenceRefresh = if (!fullSnapshotAlreadyDue && maturityEvents.isEmpty() && refreshDue.reference) {
            refreshReferenceGroup(currentSession)
        } else null
        if (referenceRefresh != null) {
            mergeReferenceFields(
                patch = referenceRefresh.snapshot,
                refreshedAtElapsedMs = referenceRefresh.observedAtElapsedMs,
            )
            refreshPlanner.markReference(referenceRefresh.observedAtElapsedMs)
        }

        synchronized(lock) {
            lastProbe = probe
            state = baseState("MONITORING", "AutoCal nativo monitorado")
                .put("sessionId", currentSession)
                .put("nativeFlag13", probe.nativeFlag13)
                .put("autoMatchCount", probe.autoMatchCount)
                .put("fallback", probe.nativeFlag13 < 0)
                .put("thresholdsReady", thresholdsReady)
                .put("maturityProbe", acquisitionRefresh != null)
                .put("acquisitionRefresh", acquisitionRefresh != null)
                .put("referenceRefresh", referenceRefresh != null)
                .put("referenceRefreshDue", refreshDue.reference && referenceRefresh == null)
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
            purgeBefore = false,
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
                status = reply.status,
                payload = reply.payload.copyOf(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun refreshAcquisitionGroup(expectedSessionId: Long): AcquisitionRefresh? {
        val startedAtMs = System.currentTimeMillis()
        val observations = mutableListOf<AutoCalReadObservation>()

        fun read(field: AutoCalProtocol.Field, reason: String): Boolean {
            val reply = serial.transaction(
                request = AutoCalProtocol.read(field),
                reason = reason,
                timeoutMs = 900,
                purgeBefore = false,
                expectedSessionId = expectedSessionId,
                workClass = Mp48WorkClass.READ_ONLY,
            )
            observations += AutoCalReadObservation(
                field = field,
                status = reply.status.takeIf { it >= 0 },
                payload = reply.payload.takeIf { it.isNotEmpty() },
                capturedAtMs = System.currentTimeMillis(),
                error = if (reply.ok) null else reply.error.ifBlank { "Campo não confirmado" },
            )
            return reply.ok
        }

        if (!read(AutoCalProtocol.NUM_BUF_UPD_PETR, "AutoCal maturidade gasolina")) return null
        val gasProbe = probeMaturityCounters(expectedSessionId) ?: return null
        observations += AutoCalReadObservation(
            field = AutoCalProtocol.NUM_BUF_UPD_GAS,
            status = gasProbe.status,
            payload = gasProbe.payload.copyOf(),
            capturedAtMs = System.currentTimeMillis(),
        )
        if (!read(AutoCalProtocol.ACQUIRED_ZONES_PETROL, "AutoCal zonas gasolina")) return null
        if (!read(AutoCalProtocol.ACQUIRED_ZONES_GAS, "AutoCal zonas GNV")) return null

        val finishedAtMs = System.currentTimeMillis()
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = observations,
            expectedFields = ACQUISITION_REFRESH_FIELDS,
            sessionId = "AUTOCAL-ACQ-$expectedSessionId-$finishedAtMs",
            source = AutoCalSnapshotSource.ECU_READ,
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs,
        )
        if (snapshot.partial || snapshot.validFieldCount != ACQUISITION_REFRESH_FIELDS.size) return null
        return AcquisitionRefresh(
            snapshot = snapshot,
            gasProbe = gasProbe,
            observedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun refreshReferenceGroup(expectedSessionId: Long): ReferenceRefresh? {
        val startedAtMs = System.currentTimeMillis()
        val observations = REFERENCE_REFRESH_FIELDS.map { field ->
            val reply = serial.transaction(
                request = AutoCalProtocol.read(field),
                reason = "AutoCal referência ${field.key}",
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
        val finishedAtMs = System.currentTimeMillis()
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = observations,
            expectedFields = REFERENCE_REFRESH_FIELDS,
            sessionId = "AUTOCAL-REF-$expectedSessionId-$finishedAtMs",
            source = AutoCalSnapshotSource.ECU_READ,
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs,
        )
        if (snapshot.partial ||
            snapshot.validFieldCount != REFERENCE_REFRESH_FIELDS.size ||
            !snapshot.temporalCoherent
        ) return null
        return ReferenceRefresh(
            snapshot = snapshot,
            observedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun mergeOperationalFields(
        patch: AutoCalSnapshot,
        refreshedAtElapsedMs: Long,
    ) = mergeRefreshedFields(
        patch = patch,
        refreshedAtElapsedMs = refreshedAtElapsedMs,
        group = "acquisition",
        reviseSnapshotHashOnChange = false,
    )

    private fun mergeReferenceFields(
        patch: AutoCalSnapshot,
        refreshedAtElapsedMs: Long,
    ) = mergeRefreshedFields(
        patch = patch,
        refreshedAtElapsedMs = refreshedAtElapsedMs,
        group = "reference",
        reviseSnapshotHashOnChange = true,
    )

    private fun mergeRefreshedFields(
        patch: AutoCalSnapshot,
        refreshedAtElapsedMs: Long,
        group: String,
        reviseSnapshotHashOnChange: Boolean,
    ) {
        val patchFields = patch.toJson().optJSONArray("fields") ?: return
        synchronized(lock) {
            if (!latestSnapshot.optBoolean("available", false)) return
            val current = JSONObject(latestSnapshot.toString())
            val currentFields = current.optJSONArray("fields") ?: JSONArray()
            val existingByKey = linkedMapOf<String, JSONObject>()
            repeat(currentFields.length()) { index ->
                val field = currentFields.optJSONObject(index) ?: return@repeat
                existingByKey[field.optString("key")] = field
            }
            val replacements = linkedMapOf<String, JSONObject>()
            repeat(patchFields.length()) { index ->
                val field = patchFields.optJSONObject(index) ?: return@repeat
                replacements[field.optString("key")] = JSONObject(field.toString())
            }
            val changed = replacements.any { (key, replacement) ->
                val previous = existingByKey[key]
                previous == null ||
                    previous.optString("status") != replacement.optString("status") ||
                    previous.optString("rawPayloadHex") != replacement.optString("rawPayloadHex")
            }

            val merged = JSONArray()
            val seen = mutableSetOf<String>()
            repeat(currentFields.length()) { index ->
                val existing = currentFields.optJSONObject(index) ?: return@repeat
                val key = existing.optString("key")
                val replacement = replacements[key]
                merged.put(if (replacement != null) JSONObject(replacement.toString()) else JSONObject(existing.toString()))
                seen += key
            }
            replacements.forEach { (key, field) ->
                if (key !in seen) merged.put(JSONObject(field.toString()))
            }
            current
                .put("fields", merged)
                .put("operationalUpdatedAtElapsedMs", refreshedAtElapsedMs)
                .put("${group}RefreshAtElapsedMs", refreshedAtElapsedMs)
                .put("incrementalRefreshGroup", group)
                .put("operationalRefreshOnly", true)
            if (reviseSnapshotHashOnChange && changed) {
                current.put(
                    "snapshotHash",
                    incrementalReferenceRevision(
                        previousHash = current.optString("snapshotHash", ""),
                        replacements = replacements,
                    ),
                )
            }
            latestSnapshot = current
        }
    }

    private fun incrementalReferenceRevision(
        previousHash: String,
        replacements: Map<String, JSONObject>,
    ): String {
        val canonical = replacements.toSortedMap().entries.joinToString("|") { (key, field) ->
            "$key:${field.optString("status")}:${field.optString("rawPayloadHex")}"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest("$previousHash|reference|$canonical".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
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
                maturityTracker.recordCorrelationResult(
                    bandIndex = transition.bandIndex,
                    correlated = correlation.state == "CORRELATED",
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
                        .put("correlationRetry", transition.correlationRetry)
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
            currentCounters?.let {
                maturityTracker.baseline(
                    counters = it,
                    observedAtElapsedMs = SystemClock.elapsedRealtime(),
                    gasLowThreshold = newGasLowThreshold,
                    gasNormalThreshold = newGasNormalThreshold,
                    enabled = enabled == 1,
                )
            }
        }
        decorated.put("nativeCorrelationState", correlationStateJson())

        val previousMul = synchronized(lock) { lastMulActHash }
        synchronized(lock) {
            latestSnapshot = decorated
            refreshPlanner.markFullSnapshot(SystemClock.elapsedRealtime())
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

    private fun correlationStateJson(): JSONObject = JSONObject()
        .put("correlatedBands", intArrayJson(maturityTracker.correlatedBandIndexes()))
        .put("retryableBands", intArrayJson(maturityTracker.retryableCorrelationBandIndexes()))

    private fun intArrayJson(values: IntArray): JSONArray = JSONArray().apply {
        values.forEach { put(it) }
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
        private val ACQUISITION_REFRESH_FIELDS = listOf(
            AutoCalProtocol.NUM_BUF_UPD_PETR,
            AutoCalProtocol.NUM_BUF_UPD_GAS,
            AutoCalProtocol.ACQUIRED_ZONES_PETROL,
            AutoCalProtocol.ACQUIRED_ZONES_GAS,
        )
        private val REFERENCE_REFRESH_FIELDS = listOf(
            AutoCalProtocol.PETR_INJ_TBP,
            AutoCalProtocol.MNFLD_PRESS_THD,
            AutoCalProtocol.MUL_ACT,
            AutoCalProtocol.PETR_MNFLD_PRESS_RV,
            AutoCalProtocol.GAS_MNFLD_PRESS_RV,
        )
    }
}