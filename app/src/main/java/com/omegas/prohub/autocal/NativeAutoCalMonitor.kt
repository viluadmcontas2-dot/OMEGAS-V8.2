package com.omegas.prohub.autocal

import android.os.SystemClock
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.ecu.LearningToleranceSettings
import com.omegas.prohub.ecu.NativeAutoCalAnchorCorrelator
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Observa a Auto Calibration nativa sem possuir timer, thread serial ou writer.
 *
 * O serviço chama [tick] em uma cadência compartilhada; o monitor não possui
 * thread nem timer. Toda I/O passa pelo scheduler MP48 único. O probe 48 0B
 * acompanha status global; um grupo leve renova contadores/zonas em ~1 s e um
 * grupo de referência renova eixos/curvas/MUL_ACT em ~4 s. Refreshes agrupados
 * só são aceitos quando o status compacto antes/depois permanece na mesma época
 * nativa, evitando misturar CURRENT/PREV/MUL durante um AutoMatch da ECU.
 * Snapshot completo continua reservado a bootstrap/eventos científicos.
 * AutoMatch continua sendo executado exclusivamente pela ECU.
 */
class NativeAutoCalMonitor(
    private val serial: Mp48SerialScheduler,
    private val calibrationBusy: () -> Boolean,
    private val onFreshSnapshot: (JSONObject) -> Unit = {},
    private val onNativeCalibrationObserved: (JSONObject) -> Unit = {},
    private val onNativeAutoMatchObserved: (JSONObject) -> Unit = {},
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
        val autoMatchCount: Int,
    )

    private val lock = Any()
    private val maturityTracker = NativeAutoCalMaturityTracker()
    private val autoMatchCounterTracker = NativeAutoMatchCounterTracker()
    private val refreshPlanner = NativeAutoCalRefreshPlanner()
    private val acquisitionEpoch = NativeAutoCalAcquisitionEpoch()

    @Volatile private var sessionId = 0L
    @Volatile private var latestSnapshot = JSONObject().put("available", false)
    @Volatile private var state = baseState("IDLE", "AutoCal nativo aguardando ECU")

    private var sessionStartedAtElapsedMs = 0L
    private var lastProbe: AutoCalProtocol.NativeStatus? = null
    private var lastMulActHash = ""
    private var lastStableMulAct: NativeAutoMatchEvidenceBracket.StableVector? = null
    private var snapshotRequested = false
    /** ECU muda: falhas de transporte seguidas do snapshot completo adiam o próximo pedido (exponencial, com teto). */
    private var snapshotFailures = 0
    private var snapshotBackoffUntilElapsedMs = 0L
    private var snapshotReason = ""
    private var gasLowThreshold: Int? = null
    private var gasNormalThreshold: Int? = null
    private var autoCalEnabled: Int? = null
    private var pendingMaturity = emptyList<PendingMaturity>()
    /**
     * Evidência de AutoMatch que o snapshot completo ainda não entregou: o contador é consumido uma vez só no
     * tick, então se o snapshot é abortado (época mudou de novo, transporte) o evento precisa esperar a próxima tentativa.
     */
    private var pendingCounterEvent: NativeAutoMatchCounterTracker.Event? = null
    private var pendingCountIncreased = false

    fun beginUsbSession(newSessionId: Long) {
        synchronized(lock) {
            sessionId = newSessionId
            sessionStartedAtElapsedMs = if (newSessionId > 0L) SystemClock.elapsedRealtime() else 0L
            lastProbe = null
            lastMulActHash = ""
            lastStableMulAct = null
            gasLowThreshold = null
            gasNormalThreshold = null
            autoCalEnabled = null
            pendingMaturity = emptyList()
            pendingCounterEvent = null
            pendingCountIncreased = false
            maturityTracker.reset()
            autoMatchCounterTracker.reset()
            refreshPlanner.reset()
            acquisitionEpoch.reset(newSessionId)
            // Agenda o bootstrap, mas tick() preserva o gate SESSION_SETTLE_MS antes
            // de qualquer leitura pesada. Assim o primeiro probe estável sempre
            // produz um snapshot completo e a UI não fica presa sem thresholds.
            snapshotRequested = newSessionId > 0L
            snapshotFailures = 0
            snapshotBackoffUntilElapsedMs = 0L
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
            lastStableMulAct = null
            gasLowThreshold = null
            gasNormalThreshold = null
            autoCalEnabled = null
            pendingMaturity = emptyList()
            pendingCounterEvent = null
            pendingCountIncreased = false
            maturityTracker.reset()
            autoMatchCounterTracker.reset()
            refreshPlanner.reset()
            acquisitionEpoch.reset(0L)
            snapshotRequested = false
            snapshotFailures = 0
            snapshotBackoffUntilElapsedMs = 0L
            snapshotReason = ""
            latestSnapshot = JSONObject().put("available", false)
            state = baseState("DISCONNECTED", "USB desconectado")
        }
        onStateChanged()
    }

    fun requestSnapshot(reason: String) {
        synchronized(lock) {
            snapshotRequested = true
            // Pedido explícito (dono/ação confirmada): fura o recuo.
            snapshotFailures = 0
            snapshotBackoffUntilElapsedMs = 0L
            snapshotReason = reason.take(80)
        }
    }

    fun onManualActionConfirmed(receipt: JSONObject) {
        synchronized(lock) {
            val receiptSessionId = receipt.optLong("sessionId", sessionId)
            if (receiptSessionId == sessionId) {
                acquisitionEpoch.manualAction(sessionId, receipt.optString("action"))
            }
        }
        requestSnapshot("ACTION_${receipt.optString("action", "UNKNOWN")}")
        // O dono acabou de ligar/desligar a aquisição (ACK + readback no gerenciador de ações): a tela mostra já;
        // o próximo snapshot completo confirma (ou corrige) o valor.
        when (receipt.optString("action")) {
            "ENABLE_AUTO_CAL" -> applyOptimisticEnabled(1)
            "DISABLE_AUTO_CAL" -> applyOptimisticEnabled(0)
        }
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

    /** Estado otimista do AUTO_CAL_ENABLE depois de uma ação confirmada; o snapshot completo seguinte o substitui. */
    private fun applyOptimisticEnabled(value: Int) {
        synchronized(lock) {
            autoCalEnabled = value
            if (latestSnapshot.optBoolean("available", false)) {
                latestSnapshot = JSONObject(latestSnapshot.toString())
                    .put("autoCalEnabled", value)
                    .put("frozen", value == 0)
                    .put("freshAcquisition", value == 1)
                    .put("autoCalEnabledOptimistic", true)
            }
            state = JSONObject(state.toString()).put("autoCalEnabled", value)
        }
        onStateChanged()
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
        val counterObservedAt = SystemClock.elapsedRealtime()
        val autoMatchCounterEvent = autoMatchCounterTracker.observe(
            currentSessionId = currentSession,
            count = probe.autoMatchCount,
            observedAtElapsedMs = counterObservedAt,
        )
        synchronized(lock) {
            acquisitionEpoch.nativeCounter(currentSession, probe.autoMatchCount)
        }
        val countIncreased = previousProbe != null && probe.autoMatchCount > previousProbe.autoMatchCount
        // O primeiro probe apenas estabelece baseline. Não autoriza snapshot pesado.
        val probeChanged = previousProbe != null && (
            previousProbe.autoMatchCount != probe.autoMatchCount ||
                previousProbe.nativeFlag13 != probe.nativeFlag13
        )

        val thresholds = synchronized(lock) { Triple(gasLowThreshold, gasNormalThreshold, autoCalEnabled) }
        val thresholdsReady = thresholds.first != null && thresholds.second != null && thresholds.third == 1
        val acquisitionEnabled = thresholds.third == 1
        val refreshDue = refreshPlanner.due(SystemClock.elapsedRealtime())
        val fullSnapshotAlreadyDue = synchronized(lock) { snapshotDue() } || probeChanged
        // Leitura operacional das 18 bandas permanece independente do disparo de maturidade.
        // O DUMP canônico já fecha os seletores gasLow=CALIBRATION_VAL_1[5] e
        // gasNormal=CALIBRATION_VAL_1[8]; counters/points/zones continuam vindo
        // diretamente da ECU, sem sintetizar estado nativo no host.
        val acquisitionAttempted = !fullSnapshotAlreadyDue && acquisitionEnabled && refreshDue.acquisition
        val acquisitionRefresh = if (acquisitionAttempted) refreshAcquisitionGroup(currentSession, probe) else null
        // Leitura que falha recua (exponencial, com teto): uma ECU/cabo ruim não é reperguntada a cada tick.
        if (acquisitionAttempted && acquisitionRefresh == null) refreshPlanner.markAcquisitionFailure(SystemClock.elapsedRealtime())
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
            synchronized(lock) {
                acquisitionEpoch.acquisitionGroup(
                    currentSession,
                    probe.autoMatchCount,
                    vector(acquisitionRefresh.snapshot, AutoCalProtocol.NUM_BUF_UPD_PETR),
                    vector(acquisitionRefresh.snapshot, AutoCalProtocol.NUM_BUF_UPD_GAS),
                )
            }
            refreshPlanner.markAcquisition(acquisitionRefresh.observedAtElapsedMs)
        }

        val referenceRefresh = if (!fullSnapshotAlreadyDue && maturityEvents.isEmpty() && refreshDue.reference) {
            refreshReferenceGroup(currentSession, probe)
        } else null
        // Mesma condição da tentativa: sem resposta válida, recua em vez de repetir a cada tick.
        if (!fullSnapshotAlreadyDue && maturityEvents.isEmpty() && refreshDue.reference && referenceRefresh == null) {
            refreshPlanner.markReferenceFailure(SystemClock.elapsedRealtime())
        }
        if (referenceRefresh != null) {
            mergeReferenceFields(
                patch = referenceRefresh.snapshot,
                refreshedAtElapsedMs = referenceRefresh.observedAtElapsedMs,
            )
            synchronized(lock) {
                acquisitionEpoch.referenceGroup(currentSession, probe.autoMatchCount)
            }
            stableMulAct(
                snapshot = referenceRefresh.snapshot,
                sessionId = currentSession,
                autoMatchCount = referenceRefresh.autoMatchCount,
                capturedAtElapsedMs = referenceRefresh.observedAtElapsedMs,
            )?.let { stable ->
                synchronized(lock) {
                    lastStableMulAct = stable
                    if (stable.rawPayloadHex.isNotBlank()) lastMulActHash = stable.rawPayloadHex
                }
            }
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
                .put("nativeAutoMatchCounterEvent", autoMatchCounterEvent != null)
            if (maturityEvents.isNotEmpty()) {
                pendingMaturity = maturityEvents
                snapshotRequested = true
                snapshotReason = "NATIVE_BAND_MATURED"
            } else if (probeChanged) {
                snapshotRequested = true
                snapshotReason = if (countIncreased) "AUTOMATCH_COUNT_CHANGED" else "NATIVE_STATUS_CHANGED"
            }
        }

        val shouldSnapshot = synchronized(lock) { snapshotDue() }
        if (shouldSnapshot) {
            // Evidência de uma tentativa anterior abortada (época mudou no meio / transporte) entra junto.
            val (event, increased) = synchronized(lock) {
                val carried = pendingCounterEvent
                val current = autoMatchCounterEvent
                val merged = if (carried != null && current != null) {
                    current.copy(beforeCount = carried.beforeCount, delta = current.afterCount - carried.beforeCount)
                } else carried ?: current
                merged to (countIncreased || pendingCountIncreased)
            }
            readFullSnapshot(currentSession, probe, increased, event)
        } else {
            onStateChanged()
        }
    }

    fun statusJson(): JSONObject = synchronized(lock) {
        val liveEpoch = acquisitionEpochJson()
        JSONObject(state.toString())
            .put("latestSnapshot", JSONObject(latestSnapshot.toString()).put("liveAcquisitionEpoch", liveEpoch))
            .put("liveAcquisitionEpoch", liveEpoch)
            .put("snapshotRequested", snapshotRequested)
            .put("snapshotReason", snapshotReason)
            .put("appAutomaticWrite", false)
            .put("manualAutoMatchExposed", false)
    }

    private var acquisitionMemo: Pair<JSONObject, JSONObject>? = null

    /**
     * Leve, para o piloto do refino (a cada tick do serviço): contador vivo de AutoMatch,
     * MAX/habilitado do último snapshot e a aquisição por banda, recalculada só quando o
     * snapshot muda (evita copiar o snapshot inteiro a cada 3 s).
     */
    fun autoMatchProgressJson(): JSONObject = synchronized(lock) {
        val count = state.optInt("autoMatchCount", -1).takeIf { state.has("autoMatchCount") && it >= 0 }
        val snapshot = latestSnapshot
        val acquisition = if (snapshot.has("fields")) {
            acquisitionMemo?.takeIf { it.first === snapshot }?.second
                ?: AutoCalAcquisition.fromSnapshot(snapshot).also { acquisitionMemo = snapshot to it }
        } else null
        JSONObject()
            .put("autoMatchCount", count ?: JSONObject.NULL)
            .put("maxAutomatch", snapshot.opt("maxAutomatch") ?: JSONObject.NULL)
            .put("autoCalEnabled", snapshot.opt("autoCalEnabled") ?: JSONObject.NULL)
            .put("acquisition", acquisition ?: JSONObject.NULL)
    }

    fun latestSnapshotJson(): JSONObject = synchronized(lock) {
        JSONObject(latestSnapshot.toString()).put("liveAcquisitionEpoch", acquisitionEpochJson())
    }

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

    private fun probe(unit: Mp48SerialUnit): AutoCalProtocol.NativeStatus? {
        val compact = unit.transaction(
            request = AutoCalProtocol.CMD_NATIVE_STATUS,
            reason = "AutoCal status leve · unidade",
            timeoutMs = 700,
            purgeBefore = false,
        )
        if (compact.ok) {
            try {
                return AutoCalProtocol.decodeNativeStatus(compact.status, compact.payload)
            } catch (_: Exception) {
                // Fallback explícito abaixo.
            }
        }
        val explicit = unit.transaction(
            request = AutoCalProtocol.read(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED),
            reason = "AutoCal fallback contador 0x0174 · unidade",
            timeoutMs = 900,
            purgeBefore = false,
        )
        if (!explicit.ok) return null
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
        } catch (_: Exception) {
            null
        }
    }

    private fun refreshAcquisitionGroup(
        expectedSessionId: Long,
        beforeEpoch: AutoCalProtocol.NativeStatus,
    ): AcquisitionRefresh? = serial.unit(
        reason = "AutoCal aquisição operacional agrupada",
        expectedSessionId = expectedSessionId,
        workClass = Mp48WorkClass.READ_ONLY,
        telemetryAfter = true,
        waitTimeoutMs = 8_000L,
    ) { unit ->
        val startedAtMs = System.currentTimeMillis()
        val observations = mutableListOf<AutoCalReadObservation>()

        fun read(field: AutoCalProtocol.Field, reason: String): Boolean {
            val reply = unit.transaction(
                request = AutoCalProtocol.read(field),
                reason = reason,
                timeoutMs = 900,
                purgeBefore = false,
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

        if (!read(AutoCalProtocol.PETR_INJ_TBUF, "AutoCal aquisição gasolina tempo")) return@unit null
        if (!read(AutoCalProtocol.MNFLD_PRESS_BUF, "AutoCal aquisição gasolina MAP")) return@unit null
        if (!read(AutoCalProtocol.NUM_BUF_UPD_PETR, "AutoCal maturidade gasolina")) return@unit null
        if (!read(AutoCalProtocol.PETR_INJ_TBUF_GAS_PREV, "AutoCal aquisição GNV anterior tempo")) return@unit null
        if (!read(AutoCalProtocol.MNFLD_PRESS_BUF_GAS_PREV, "AutoCal aquisição GNV anterior MAP")) return@unit null
        if (!read(AutoCalProtocol.PETR_INJ_TBUF_GAS, "AutoCal aquisição GNV tempo")) return@unit null
        if (!read(AutoCalProtocol.MNFLD_PRESS_BUF_GAS, "AutoCal aquisição GNV MAP")) return@unit null

        val gasReply = unit.transaction(
            request = AutoCalProtocol.read(AutoCalProtocol.NUM_BUF_UPD_GAS),
            reason = "AutoCal maturidade GNV",
            timeoutMs = 900,
            purgeBefore = false,
        )
        if (!gasReply.ok) return@unit null
        val gasDecoded = try {
            AutoCalProtocol.decode(AutoCalProtocol.NUM_BUF_UPD_GAS, gasReply.status, gasReply.payload)
        } catch (_: Exception) {
            return@unit null
        }
        val gasProbe = MaturityProbe(
            counters = gasDecoded.rawValues.copyOf(),
            payloadHex = gasReply.payload.toHex(),
            observedAtElapsedMs = SystemClock.elapsedRealtime(),
            status = gasReply.status,
            payload = gasReply.payload.copyOf(),
        )
        observations += AutoCalReadObservation(
            field = AutoCalProtocol.NUM_BUF_UPD_GAS,
            status = gasReply.status,
            payload = gasReply.payload.copyOf(),
            capturedAtMs = System.currentTimeMillis(),
        )

        if (!read(AutoCalProtocol.ACQUIRED_ZONES_PETROL, "AutoCal zonas gasolina")) return@unit null
        if (!read(AutoCalProtocol.ACQUIRED_ZONES_GAS, "AutoCal zonas GNV")) return@unit null

        val afterEpoch = probe(unit) ?: return@unit null
        if (!NativeAutoCalEpochGuard.sameEpoch(beforeEpoch, afterEpoch)) return@unit null

        val finishedAtMs = System.currentTimeMillis()
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = observations,
            expectedFields = ACQUISITION_REFRESH_FIELDS,
            sessionId = "AUTOCAL-ACQ-$expectedSessionId-$finishedAtMs",
            source = AutoCalSnapshotSource.ECU_READ,
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs,
        )
        if (snapshot.partial || snapshot.validFieldCount != ACQUISITION_REFRESH_FIELDS.size) return@unit null
        AcquisitionRefresh(
            snapshot = snapshot,
            gasProbe = gasProbe,
            observedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun refreshReferenceGroup(
        expectedSessionId: Long,
        beforeEpoch: AutoCalProtocol.NativeStatus,
    ): ReferenceRefresh? = serial.unit(
        reason = "AutoCal referência agrupada",
        expectedSessionId = expectedSessionId,
        workClass = Mp48WorkClass.READ_ONLY,
        telemetryAfter = true,
        waitTimeoutMs = 6_000L,
    ) { unit ->
        val startedAtMs = System.currentTimeMillis()
        val observations = REFERENCE_REFRESH_FIELDS.map { field ->
            val reply = unit.transaction(
                request = AutoCalProtocol.read(field),
                reason = "AutoCal referência ${field.key}",
                timeoutMs = 1_200,
                purgeBefore = false,
            )
            AutoCalReadObservation(
                field = field,
                status = reply.status.takeIf { it >= 0 },
                payload = reply.payload.takeIf { it.isNotEmpty() },
                capturedAtMs = System.currentTimeMillis(),
                error = if (reply.ok) null else reply.error.ifBlank { "Campo não confirmado" },
            )
        }
        val afterEpoch = probe(unit) ?: return@unit null
        if (!NativeAutoCalEpochGuard.sameEpoch(beforeEpoch, afterEpoch)) return@unit null

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
        ) return@unit null
        ReferenceRefresh(
            snapshot = snapshot,
            observedAtElapsedMs = SystemClock.elapsedRealtime(),
            autoMatchCount = beforeEpoch.autoMatchCount,
        )
    }

    private fun mergeOperationalFields(
        patch: AutoCalSnapshot,
        refreshedAtElapsedMs: Long,
    ) = mergeRefreshedFields(
        patch = patch,
        refreshedAtElapsedMs = refreshedAtElapsedMs,
        group = "acquisition",
        refreshTimestampKey = "acquisitionRefreshAtElapsedMs",
        reviseSnapshotHashOnChange = false,
    )

    private fun mergeReferenceFields(
        patch: AutoCalSnapshot,
        refreshedAtElapsedMs: Long,
    ) = mergeRefreshedFields(
        patch = patch,
        refreshedAtElapsedMs = refreshedAtElapsedMs,
        group = "reference",
        refreshTimestampKey = "referenceRefreshAtElapsedMs",
        reviseSnapshotHashOnChange = true,
    )

    private fun mergeRefreshedFields(
        patch: AutoCalSnapshot,
        refreshedAtElapsedMs: Long,
        group: String,
        refreshTimestampKey: String,
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
                .put(refreshTimestampKey, refreshedAtElapsedMs)
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

    private fun stableMulAct(
        snapshot: AutoCalSnapshot,
        sessionId: Long,
        autoMatchCount: Int,
        capturedAtElapsedMs: Long,
    ): NativeAutoMatchEvidenceBracket.StableVector? {
        val field = snapshot.field(AutoCalProtocol.MUL_ACT)
            ?.takeIf { it.status == AutoCalFieldStatus.VALID }
            ?: return null
        return NativeAutoMatchEvidenceBracket.stable(
            sessionId = sessionId,
            autoMatchCount = autoMatchCount,
            capturedAtElapsedMs = capturedAtElapsedMs,
            rawValues = field.rawValues,
            rawPayloadHex = field.rawPayloadHex,
        )
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

    /** Pedido pendente e fora do recuo. Chamar sob [lock]. */
    private fun snapshotDue(): Boolean =
        snapshotRequested && SystemClock.elapsedRealtime() >= snapshotBackoffUntilElapsedMs

    /** Guarda a evidência de AutoMatch de uma tentativa abortada para a próxima. */
    private fun carryEvidence(event: NativeAutoMatchCounterTracker.Event?, countIncreased: Boolean) {
        synchronized(lock) {
            if (event != null) pendingCounterEvent = event
            if (countIncreased) pendingCountIncreased = true
        }
    }

    /** Falha de transporte no snapshot completo: recuo exponencial (2 s, 4 s, ... teto 60 s). */
    private fun backOffSnapshot() {
        synchronized(lock) {
            snapshotFailures = (snapshotFailures + 1).coerceAtMost(SNAPSHOT_BACKOFF_MAX_EXPONENT)
            val delayMs = (SNAPSHOT_BACKOFF_BASE_MS shl (snapshotFailures - 1)).coerceAtMost(SNAPSHOT_BACKOFF_CAP_MS)
            snapshotBackoffUntilElapsedMs = SystemClock.elapsedRealtime() + delayMs
        }
    }

    private fun readFullSnapshot(
        expectedSessionId: Long,
        probe: AutoCalProtocol.NativeStatus,
        countIncreased: Boolean,
        autoMatchCounterEvent: NativeAutoMatchCounterTracker.Event?,
    ) {
        val reason = synchronized(lock) { snapshotReason }
        val started = System.currentTimeMillis()
        val observations = ArrayList<AutoCalReadObservation>()
        var consecutiveTimeouts = 0
        for (field in AutoCalProtocol.READ_ONLY_FIELDS.distinctBy { it.identity }) {
            val reply = serial.transaction(
                request = AutoCalProtocol.read(field),
                reason = "AutoCal snapshot ${field.key}",
                timeoutMs = 1_200,
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
            // Só silêncio de transporte (sem quadro algum) conta; resposta da ECU, mesmo recusa, não.
            consecutiveTimeouts = if (!reply.ok && reply.status < 0) consecutiveTimeouts + 1 else 0
            if (consecutiveTimeouts >= SNAPSHOT_MAX_CONSECUTIVE_TIMEOUTS) break
        }
        if (consecutiveTimeouts >= SNAPSHOT_MAX_CONSECUTIVE_TIMEOUTS) {
            // ECU muda: não monta snapshot parcial nem repete a varredura inteira a cada segundo.
            carryEvidence(autoMatchCounterEvent, countIncreased)
            backOffSnapshot()
            onStateChanged()
            return
        }
        val afterEpoch = probe(expectedSessionId)
        if (afterEpoch == null) {
            // Sem resposta ao probe final: falha de TRANSPORTE (recuo exponencial).
            carryEvidence(autoMatchCounterEvent, countIncreased)
            backOffSnapshot()
            return
        }
        if (!NativeAutoCalEpochGuard.sameEpoch(probe, afterEpoch)) {
            // A ECU respondeu: o AutoMatch avançou durante a leitura. Não é falha de transporte (sem recuo
            // exponencial) e a evidência do contador não se perde: refaz logo, com o evento guardado.
            carryEvidence(autoMatchCounterEvent, countIncreased)
            synchronized(lock) {
                snapshotRequested = true
                snapshotReason = "EPOCH_CHANGED_DURING_SNAPSHOT"
                snapshotBackoffUntilElapsedMs = SystemClock.elapsedRealtime() + EPOCH_RETRY_MS
            }
            onStateChanged()
            return
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
        val mulActField = snapshot.field(AutoCalProtocol.MUL_ACT)
            ?.takeIf { it.status == AutoCalFieldStatus.VALID }
        val afterMulActRaw = mulActField?.rawValues?.copyOf()
        val afterMulActCapturedAtElapsedMs = SystemClock.elapsedRealtime()
        val beforeStableMulAct = synchronized(lock) { lastStableMulAct }
        val autoMatchEvidence = autoMatchCounterEvent?.let { event ->
            NativeAutoMatchEvidenceBracket.evaluate(
                event = event,
                before = beforeStableMulAct,
                afterSessionId = expectedSessionId,
                afterAutoMatchCount = probe.autoMatchCount,
                afterCapturedAtElapsedMs = afterMulActCapturedAtElapsedMs,
                afterRaw = afterMulActRaw,
                afterPayloadHex = mulActField?.rawPayloadHex.orEmpty(),
            )
        }
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
            .put("nativeAutoMatchCounterEvent", autoMatchCounterEvent?.toJson() ?: JSONObject.NULL)
            .put("nativeAutoMatchCounterEventObserved", autoMatchCounterEvent != null)
            .put("nativeAutoMatchEvidence", autoMatchEvidence?.toJson() ?: JSONObject.NULL)
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


        if (autoMatchCounterEvent != null) {
            val epochEvidence = autoMatchEvidence?.toJson() ?: JSONObject()
                .put("state", NativeAutoMatchEvidenceBracket.State.INCONCLUSIVE.name)
                .put("reason", "EVIDENCE_NOT_AVAILABLE")
                .put("appWritePerformed", false)
                .put("appAutomaticWrite", false)
                .put("nativeFirmwareFormulaInferred", false)
            val epoch = JSONObject()
                .put("eventType", "NATIVE_AUTOMATCH_EPOCH")
                .put("source", SOURCE_NATIVE_AUTOCAL)
                .put("sessionId", expectedSessionId)
                .put("snapshotId", decorated.optString("sessionId"))
                .put("snapshotHash", snapshot.snapshotHash)
                .put("observedAtElapsedMs", autoMatchCounterEvent.observedAtElapsedMs)
                .put("beforeCount", autoMatchCounterEvent.beforeCount)
                .put("afterCount", autoMatchCounterEvent.afterCount)
                .put("counterDelta", autoMatchCounterEvent.delta)
                .put("evidence", epochEvidence)
                .put("acquisition", acquisition)
                .put("nativeStatus", JSONObject()
                    .put("nativeFlag13", probe.nativeFlag13)
                    .put("autoMatchCount", probe.autoMatchCount))
                .put("maxAutomatch", maxAutomatch ?: JSONObject.NULL)
                .put("appWritePerformed", false)
                .put("appAutomaticWrite", false)
                .put("nativeFirmwareFormulaInferred", false)
            try { onNativeAutoMatchObserved(epoch) } catch (_: Exception) {}
        }

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

        val stableAfter = NativeAutoMatchEvidenceBracket.stable(
            sessionId = expectedSessionId,
            autoMatchCount = probe.autoMatchCount,
            capturedAtElapsedMs = afterMulActCapturedAtElapsedMs,
            rawValues = afterMulActRaw,
            rawPayloadHex = mulActField?.rawPayloadHex.orEmpty(),
        )
        synchronized(lock) {
            acquisitionEpoch.acquisitionGroup(
                expectedSessionId,
                probe.autoMatchCount,
                vector(snapshot, AutoCalProtocol.NUM_BUF_UPD_PETR),
                vector(snapshot, AutoCalProtocol.NUM_BUF_UPD_GAS),
            )
            if (listOf(
                    AutoCalProtocol.PETR_INJ_TBP,
                    AutoCalProtocol.PETR_MNFLD_PRESS_RV,
                    AutoCalProtocol.GAS_MNFLD_PRESS_RV,
                ).all { snapshot.field(it)?.status == AutoCalFieldStatus.VALID }
            ) {
                acquisitionEpoch.referenceGroup(expectedSessionId, probe.autoMatchCount)
            }
            decorated.put("liveAcquisitionEpoch", acquisitionEpochJson())
            latestSnapshot = decorated
            refreshPlanner.markFullSnapshot(SystemClock.elapsedRealtime())
            if (mulActHash.isNotBlank()) lastMulActHash = mulActHash
            if (stableAfter != null) lastStableMulAct = stableAfter
            gasLowThreshold = newGasLowThreshold
            gasNormalThreshold = newGasNormalThreshold
            autoCalEnabled = enabled
            pendingMaturity = emptyList()
            pendingCounterEvent = null
            pendingCountIncreased = false
            snapshotRequested = false
            snapshotFailures = 0
            snapshotBackoffUntilElapsedMs = 0L
            snapshotReason = ""
            state = baseState(if (enabled == 0) "PAUSED" else "READY", if (enabled == 0) "AutoCal pausado; dados congelados" else "AutoCal nativo acompanhado")
                .put("sessionId", expectedSessionId)
                .put("nativeFlag13", probe.nativeFlag13)
                .put("autoMatchCount", probe.autoMatchCount)
                .put("maxAutomatch", maxAutomatch ?: JSONObject.NULL)
                .put("autoCalEnabled", enabled ?: JSONObject.NULL)
                .put("nativeMaturityEventCount", maturityEvents.length())
                .put("nativeAutoMatchCounterEvent", autoMatchCounterEvent != null)
                .put("nativeAutoMatchEvidenceState", autoMatchEvidence?.state?.name ?: JSONObject.NULL)
                .put("nativeAutoMatchChangedPoints", autoMatchEvidence?.changedPointCount ?: 0)
                .put("snapshotHash", snapshot.snapshotHash)
        }

        if (enabled == 1) {
            try { onFreshSnapshot(decorated) } catch (_: Exception) {}
        }
        if (countIncreased &&
            autoMatchEvidence?.state == NativeAutoMatchEvidenceBracket.State.FACTOR_CHANGE_CONFIRMED
        ) {
            try {
                onNativeCalibrationObserved(
                    autoMatchEvidence.toJson()
                        .put("source", SOURCE_NATIVE_AUTOCAL)
                        .put("calibrationType", "K_FACTOR")
                        .put("cause", "ECU_AUTOMATCH_COUNT_CHANGED")
                        .put("oldHash", beforeStableMulAct?.rawPayloadHex ?: JSONObject.NULL)
                        .put("newHash", mulActField?.rawPayloadHex ?: JSONObject.NULL)
                        .put("nativeAutoMatchCount", probe.autoMatchCount)
                        .put("maxAutomatch", maxAutomatch ?: JSONObject.NULL)
                        .put("nativeFlag13", probe.nativeFlag13)
                        .put("readbackValid", true)
                        .put("humanConfirmed", false)
                        .put("ecuNativeObserved", true)
                        .put("appWritePerformed", false)
                        .put("ecuNativeAutomatic", true)
                        .put("appAutomaticWrite", false)
                        .put("pointDeltas", autoMatchEvidence.toJson().getJSONArray("pointDeltas")),
                )
            } catch (_: Exception) {}
        }
        onStateChanged()
    }

    private fun acquisitionEpochJson(): JSONObject = acquisitionEpoch.view().let { epoch ->
        JSONObject()
            .put("usbSessionId", epoch.usbSessionId)
            .put("nativeAutoMatchCount", epoch.nativeAutoMatchCount ?: JSONObject.NULL)
            .put("petrolGeneration", epoch.petrolGeneration)
            .put("gasGeneration", epoch.gasGeneration)
            .put("petrolPending", epoch.petrolPending)
            .put("gasPending", epoch.gasPending)
            .put("referencePending", epoch.referencePending)
            .put("petrolReferencePending", epoch.petrolReferencePending)
            .put("gasReferencePending", epoch.gasReferencePending)
            .put("petrolSamples", epoch.petrolSamples)
            .put("gasSamples", epoch.gasSamples)
            .put("comparisonAllowed", epoch.comparisonAllowed)
            .put("reason", epoch.reason)
            .put("appWritePerformed", false)
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

    private fun NativeAutoMatchCounterTracker.Event.toJson(): JSONObject = JSONObject()
        .put("eventType", eventType)
        .put("source", SOURCE_NATIVE_AUTOCAL)
        .put("sessionId", sessionId)
        .put("observedAtElapsedMs", observedAtElapsedMs)
        .put("beforeCount", beforeCount)
        .put("afterCount", afterCount)
        .put("delta", delta)
        .put("mulActChangeConfirmed", mulActChangeConfirmed)
        .put("appWritePerformed", false)
        .put("appAutomaticWrite", false)

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
        /** ECU silenciosa: 2 timeouts de transporte seguidos abortam a varredura do snapshot completo. */
        private const val SNAPSHOT_MAX_CONSECUTIVE_TIMEOUTS = 2
        /** Época nativa mudou durante o snapshot completo: refaz logo (a ECU respondeu; não é falha de transporte). */
        private const val EPOCH_RETRY_MS = 1_000L
        private const val SNAPSHOT_BACKOFF_BASE_MS = 2_000L
        private const val SNAPSHOT_BACKOFF_CAP_MS = 60_000L
        private const val SNAPSHOT_BACKOFF_MAX_EXPONENT = 6
        private val ACQUISITION_REFRESH_FIELDS = listOf(
            // Unidade operacional consumida por AutoCalAcquisition: dados + MAP + contadores.
            AutoCalProtocol.PETR_INJ_TBUF,
            AutoCalProtocol.MNFLD_PRESS_BUF,
            AutoCalProtocol.NUM_BUF_UPD_PETR,
            AutoCalProtocol.PETR_INJ_TBUF_GAS_PREV,
            AutoCalProtocol.MNFLD_PRESS_BUF_GAS_PREV,
            AutoCalProtocol.PETR_INJ_TBUF_GAS,
            AutoCalProtocol.MNFLD_PRESS_BUF_GAS,
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
