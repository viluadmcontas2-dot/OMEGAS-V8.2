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
 * O serviço chama [tick] em cadência curta (~100 ms); o monitor não possui
 * thread nem timer. Toda I/O passa pelo scheduler MP48 único. O probe 48 0B
 * acompanha status global. Lote D: a aquisição deixou de ser uma unidade inseparável
 * de ~10 leituras (~0,5 s com a porta presa): cada tick lê no máximo UM grupo de <= 3
 * leituras (G2 gasolina, G3 GNV anterior, G4 GNV, G6 zonas; referência G5 MUL_ACT/eixos,
 * G7/G8 curvas RV) e o [SlotArbiter] exige >= 3 quadros vivos entre dois grupos. A
 * rodada operacional dura ~2 s; a referência vem a cada 2ª rodada (~4 s) ou já em evento.
 * Cada grupo só é aceito quando o status compacto ANTES/DEPOIS (probe de confirmação no
 * slot seguinte) permanece na mesma época nativa: um grupo com época diferente é descartado,
 * nunca misturado. Snapshot completo continua reservado a bootstrap/eventos científicos
 * (também fatiado em grupos de 3). AutoMatch continua sendo executado exclusivamente pela ECU.
 *
 * Instrumentação: [statusJson] ganhou `acquisitionTiming` (idade do vivo, idade/duração por grupo,
 * fatia de barramento estimada), `slotArbiter` e `tablesRevision`.
 */
class NativeAutoCalMonitor(
    private val serial: Mp48SerialScheduler,
    private val calibrationBusy: () -> Boolean,
    private val onFreshSnapshot: (JSONObject) -> Unit = {},
    private val onNativeCalibrationObserved: (JSONObject) -> Unit = {},
    private val onNativeAutoMatchObserved: (JSONObject) -> Unit = {},
    private val onStateChanged: () -> Unit = {},
    /** Dispara (fora do lock) quando uma tabela AutoCal realmente mudou (resposta diferente da em cache). */
    private val onTablesChanged: () -> Unit = {},
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() },
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

    /** Grupo lido (<= 3 leituras) que ainda espera o probe de confirmação de época do slot seguinte. */
    private data class GroupRead(
        val group: NativeAutoCalRefreshPlanner.Group,
        val snapshot: AutoCalSnapshot,
        val gasProbe: MaturityProbe?,
        val observedAtElapsedMs: Long,
    )

    private data class PendingGroup(
        val read: GroupRead,
        val beforeEpoch: AutoCalProtocol.NativeStatus,
        val sessionId: Long,
        /** [WriteFence] amostrada quando a leitura do grupo começou. */
        val writeGeneration: Long = 0L,
    )

    private data class ProbeObservation(
        val counterEvent: NativeAutoMatchCounterTracker.Event?,
        val countIncreased: Boolean,
        val changed: Boolean,
    )

    private val lock = Any()
    private val maturityTracker = NativeAutoCalMaturityTracker()
    private val autoMatchCounterTracker = NativeAutoMatchCounterTracker()
    private val refreshPlanner = NativeAutoCalRefreshPlanner()
    private val acquisitionEpoch = NativeAutoCalAcquisitionEpoch()
    private val arbiter = SlotArbiter(
        clock = clockMs,
        liveFrames = { serial.liveFrameCount() },
    )
    private val duty = AcquisitionDuty(clockMs)
    private val probeBackoff = ProbeBackoff()
    private val stepper = SliceStepper(refreshPlanner, arbiter, duty)
    private val scratch = RoundScratch<PendingGroup, GroupRead>()
    /** Sobe a cada gravação K / ação AutoCal confirmada: leitura que atravessou uma escrita é descartada. */
    private val writeFence = WriteFence()
    /** Grupos de referência (G5, G7, G8) já confirmados, retidos até o round fechar: a curva entra atômica ou não entra. */
    private var lastProbeAtElapsedMs = 0L
    /** Revisão das tabelas: só sobe quando uma resposta difere da guardada (hash de status+payload por campo). */
    @Volatile private var tablesRevisionValue = 0L

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
            resetRoundState()
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
            resetRoundState()
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

    /** Sob [lock]: zera o round em voo, o árbitro e a instrumentação (sessão USB nova/encerrada). */
    private fun resetRoundState() {
        scratch.pending = null
        scratch.petrolCounters = null
        scratch.hold.clear()
        lastProbeAtElapsedMs = 0L
        probeBackoff.reset()
        arbiter.reset()
        duty.reset()
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

    /**
     * Uma gravação K / ação AutoCal foi confirmada: tudo que o round tinha lido ANTES dela deixa de valer
     * (grupo pendente, G5/G7 retidos, contadores de gasolina, round do planejador). A referência é relida já.
     * Só zera estado de leitura; não envia nada à ECU.
     */
    fun invalidateRound() {
        writeFence.bump()
        synchronized(lock) { scratch.invalidate() }
        refreshPlanner.abandonRound()
        refreshPlanner.requestReferenceNow()
    }

    fun onManualActionConfirmed(receipt: JSONObject) {
        invalidateRound()
        synchronized(lock) {
            val receiptSessionId = receipt.optLong("sessionId", sessionId)
            if (receiptSessionId == sessionId) {
                acquisitionEpoch.manualAction(sessionId, receipt.optString("action"))
            }
        }
        requestSnapshot("ACTION_${receipt.optString("action", "UNKNOWN")}")
        // O dono acabou de ligar/desligar a aquisição (ACK + readback no gerenciador de ações): a tela mostra já;
        // o próximo snapshot completo confirma (ou corrige) o valor.
        val readBack = enabledFromSnapshotJson(receipt.optJSONObject("after"))
        when {
            readBack != null -> { enabledCheckedAtMs = clockMs(); applyOptimisticEnabled(readBack) }
            receipt.optString("action") == "ENABLE_AUTO_CAL" -> applyOptimisticEnabled(1)
            receipt.optString("action") == "DISABLE_AUTO_CAL" -> applyOptimisticEnabled(0)
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

        // Decisão pura (SliceStepper): confirmar o grupo anterior, ler UM grupo (SlotArbiter), ou só o probe.
        val pending = synchronized(lock) { scratch.pending }
        val snapshotWanted = synchronized(lock) { snapshotDue() }
        val nowMs = clockMs()
        val (knownProbe, probeAt) = synchronized(lock) { lastProbe to lastProbeAtElapsedMs }
        val decision = stepper.decide(
            nowMs = nowMs,
            hasPending = pending != null,
            snapshotWanted = snapshotWanted,
            // Pausa (AutoCal desabilitado) fecha só a aquisição; a referência segue (MUL_ACT/Curva K).
            acquisitionEnabled = synchronized(lock) { autoCalEnabled } == 1,
            probeAgeMs = if (knownProbe != null && probeAt > 0L) nowMs - probeAt else -1L,
            probeBackoffUntilMs = probeBackoff.untilMs,
        )
        when (decision.step) {
            SliceStepper.Step.CONFIRM_PROBE -> {
                if (pending != null) confirmPendingGroup(currentSession, pending)
                return
            }
            SliceStepper.Step.RUN_GROUP -> {
                val group = decision.group
                if (group != null && knownProbe != null) {
                    runGroup(currentSession, group, knownProbe)
                } else {
                    arbiter.end() // defensivo: slot reservado sem grupo/probe
                }
                return
            }
            SliceStepper.Step.IDLE -> return
            SliceStepper.Step.PROBE -> Unit
        }

        // 3) Probe (status leve 48 0B): contador AutoMatch/flag; muda → snapshot completo.
        val probe = probe(currentSession)
        if (probe == null) {
            // A ECU não respondeu ao status leve: recua (2 s → 30 s) em vez de repetir a cada 100 ms.
            probeBackoff.onFailure(clockMs())
            return
        }
        probeBackoff.onSuccess()
        val observed = observeProbe(currentSession, probe)
        // A ECU entrega o AUTO_CAL_ENABLE em tempo real: relê o flag (leitura já existente) em vez de confiar no cache do
        // último snapshot completo. Pausa feita por fora (outro aparelho, firmware) aparece em segundos, não "nunca".
        recheckEnabled(currentSession)
        val thresholds = synchronized(lock) { Triple(gasLowThreshold, gasNormalThreshold, autoCalEnabled) }
        val thresholdsReady = thresholds.first != null && thresholds.second != null && thresholds.third == 1
        val referenceDue = refreshPlanner.due(clockMs()).reference
        synchronized(lock) {
            state = monitoringState(
                currentSession, probe, thresholdsReady,
                acquisitionRefresh = false, referenceRefresh = false,
                referenceDue = referenceDue, counterEvent = observed.counterEvent != null,
            ).put("autoCalEnabled", autoCalEnabled ?: JSONObject.NULL).put("autoCalEnabledAtMs", enabledCheckedAtMs)
            if (observed.changed) {
                snapshotRequested = true
                snapshotReason = if (observed.countIncreased) "AUTOMATCH_COUNT_CHANGED" else "NATIVE_STATUS_CHANGED"
            }
        }

        val shouldSnapshot = synchronized(lock) { snapshotDue() }
        if (shouldSnapshot) {
            // Evidência de uma tentativa anterior abortada (época mudou no meio / transporte) entra junto.
            val (event, increased) = synchronized(lock) {
                val carried = pendingCounterEvent
                val current = observed.counterEvent
                val merged = if (carried != null && current != null) {
                    current.copy(beforeCount = carried.beforeCount, delta = current.afterCount - carried.beforeCount)
                } else carried ?: current
                merged to (observed.countIncreased || pendingCountIncreased)
            }
            readFullSnapshot(currentSession, probe, increased, event)
        } else {
            onStateChanged()
        }
    }

    /** Registra um probe: contador nativo, época de aquisição e "mudou?" frente ao probe anterior. */
    private fun observeProbe(currentSession: Long, probe: AutoCalProtocol.NativeStatus): ProbeObservation {
        val previousProbe = synchronized(lock) { lastProbe }
        val observedAt = SystemClock.elapsedRealtime()
        val counterEvent = autoMatchCounterTracker.observe(
            currentSessionId = currentSession,
            count = probe.autoMatchCount,
            observedAtElapsedMs = observedAt,
        )
        synchronized(lock) {
            acquisitionEpoch.nativeCounter(currentSession, probe.autoMatchCount)
            lastProbe = probe
            lastProbeAtElapsedMs = clockMs()
        }
        val countIncreased = previousProbe != null && probe.autoMatchCount > previousProbe.autoMatchCount
        // O primeiro probe apenas estabelece baseline. Não autoriza snapshot pesado.
        val changed = previousProbe != null && (
            previousProbe.autoMatchCount != probe.autoMatchCount ||
                previousProbe.nativeFlag13 != probe.nativeFlag13
        )
        return ProbeObservation(counterEvent, countIncreased, changed)
    }

    private fun monitoringState(
        currentSession: Long,
        probe: AutoCalProtocol.NativeStatus,
        thresholdsReady: Boolean,
        acquisitionRefresh: Boolean,
        referenceRefresh: Boolean,
        referenceDue: Boolean,
        counterEvent: Boolean,
    ): JSONObject = baseState("MONITORING", "AutoCal nativo monitorado")
        .put("sessionId", currentSession)
        .put("nativeFlag13", probe.nativeFlag13)
        .put("autoMatchCount", probe.autoMatchCount)
        .put("fallback", probe.nativeFlag13 < 0)
        .put("thresholdsReady", thresholdsReady)
        .put("maturityProbe", acquisitionRefresh)
        .put("acquisitionRefresh", acquisitionRefresh)
        .put("referenceRefresh", referenceRefresh)
        .put("referenceRefreshDue", referenceDue && !referenceRefresh)
        .put("nativeAutoMatchCounterEvent", counterEvent)

    /** Lê UM grupo (<= 3 leituras) segurando a porta só por ele; o slot do árbitro já foi reservado. */
    private fun runGroup(
        currentSession: Long,
        group: NativeAutoCalRefreshPlanner.Group,
        beforeEpoch: AutoCalProtocol.NativeStatus,
    ) {
        val startedAt = clockMs()
        val generation = writeFence.current()
        var read: GroupRead? = null
        var error = ""
        try {
            read = if (group.family == NativeAutoCalRefreshPlanner.Family.ACQUISITION) {
                refreshAcquisitionGroup(currentSession, group)
            } else {
                refreshReferenceGroup(currentSession, group)
            }
        } catch (interrupted: InterruptedException) {
            throw interrupted // desligamento do serviço: sobe para o autoCalTick (o finally libera o slot)
        } catch (failure: Exception) {
            // Falha de transporte/admissão não derruba o monitor: o grupo é pulado e recua (B4).
            error = failure.message ?: failure.javaClass.simpleName
        } finally {
            arbiter.end()
        }
        val groupRead = read
        duty.record(group.label, startedAt, clockMs() - startedAt, groupRead != null, error)
        if (groupRead == null) {
            failGroup(group)
            return
        }
        synchronized(lock) { scratch.pending = PendingGroup(groupRead, beforeEpoch, currentSession, generation) }
    }

    /** Grupo falhou/descartado: recuo do tipo (B4) e, se era referência, a retenção do round morre junto. */
    private fun failGroup(group: NativeAutoCalRefreshPlanner.Group) {
        refreshPlanner.groupFailed(group, clockMs())
        if (group.family == NativeAutoCalRefreshPlanner.Family.REFERENCE) {
            synchronized(lock) { scratch.hold.clear() }
        }
    }

    /**
     * Fecha o round de referência: exige G5+G7+G8 retidos e lidos dentro do limite de coerência das curvas
     * (o mesmo `MAX_AUTOMATCH_GROUP_SKEW_MS` do snapshot). Só então mescla (MUL_ACT estável incluído).
     */
    private fun commitReferenceRound(currentSession: Long, probe: AutoCalProtocol.NativeStatus): Boolean {
        val held = synchronized(lock) { scratch.hold.toList().also { scratch.hold.clear() } }
        val complete = NativeAutoCalRefreshPlanner.Group.values()
            .filter { it.family == NativeAutoCalRefreshPlanner.Family.REFERENCE }
            .all { wanted -> held.any { it.group == wanted } }
        val span = (held.maxOfOrNull { it.observedAtElapsedMs } ?: 0L) - (held.minOfOrNull { it.observedAtElapsedMs } ?: 0L)
        if (!complete || span > AutoCalSnapshotBuilder.MAX_AUTOMATCH_GROUP_SKEW_MS) {
            refreshPlanner.groupFailed(NativeAutoCalRefreshPlanner.Group.G8_GAS_RV, clockMs())
            return false
        }
        held.forEach { read ->
            mergeReferenceFields(
                patch = read.snapshot,
                refreshedAtElapsedMs = read.observedAtElapsedMs,
                slice = read.group.label,
            )
            if (read.group == NativeAutoCalRefreshPlanner.Group.G5_MUL_ACT) {
                stableMulAct(
                    snapshot = read.snapshot,
                    sessionId = currentSession,
                    autoMatchCount = probe.autoMatchCount,
                    capturedAtElapsedMs = read.observedAtElapsedMs,
                )?.let { stable ->
                    synchronized(lock) {
                        lastStableMulAct = stable
                        if (stable.rawPayloadHex.isNotBlank()) lastMulActHash = stable.rawPayloadHex
                    }
                }
            }
        }
        // Conjunto RV30 deste round (todo sob a mesma época confirmada) está fresco.
        synchronized(lock) { acquisitionEpoch.referenceGroup(currentSession, probe.autoMatchCount) }
        return true
    }

    /** Probe de confirmação: mesma época antes/depois aceita o grupo; qualquer divergência o DESCARTA. */
    private fun confirmPendingGroup(currentSession: Long, pending: PendingGroup) {
        val group = pending.read.group
        if (writeFence.changedSince(pending.writeGeneration)) {
            // Uma gravação K / ação AutoCal confirmou depois da leitura: o grupo é velho. Descarta, sem falha de ECU.
            synchronized(lock) { scratch.invalidate() }
            refreshPlanner.abandonRound()
            return
        }
        if (pending.sessionId != currentSession ||
            clockMs() - pending.read.observedAtElapsedMs > PENDING_GROUP_MAX_AGE_MS
        ) {
            synchronized(lock) { scratch.pending = null }
            refreshPlanner.abandonRound() // sessão trocou ou o dado envelheceu: não é falha da ECU
            return
        }
        val probe = probe(currentSession)
        synchronized(lock) { scratch.pending = null }
        if (probe == null) {
            failGroup(group)
            return
        }
        val observed = observeProbe(currentSession, probe)
        if (observed.changed) {
            synchronized(lock) {
                snapshotRequested = true
                snapshotReason = if (observed.countIncreased) "AUTOMATCH_COUNT_CHANGED" else "NATIVE_STATUS_CHANGED"
            }
        }
        if (observed.counterEvent != null || observed.countIncreased) {
            carryEvidence(observed.counterEvent, observed.countIncreased)
        }
        if (!NativeAutoCalEpochGuard.sameEpoch(pending.beforeEpoch, probe)) {
            // Época diferente: este grupo nunca é misturado ao snapshot; recua como falha do tipo.
            failGroup(group)
            onStateChanged()
            return
        }
        if (writeFence.changedSince(pending.writeGeneration)) {
            synchronized(lock) { scratch.invalidate() }
            refreshPlanner.abandonRound()
            return
        }
        commitGroup(currentSession, pending.read, probe, observed.counterEvent != null)
    }

    private fun commitGroup(
        currentSession: Long,
        read: GroupRead,
        probe: AutoCalProtocol.NativeStatus,
        counterEvent: Boolean,
    ) {
        val group = read.group
        val acquisition = group.family == NativeAutoCalRefreshPlanner.Family.ACQUISITION
        var maturityEvents = emptyList<PendingMaturity>()
        var roundRejected = false
        if (acquisition) {
            mergeOperationalFields(
                patch = read.snapshot,
                refreshedAtElapsedMs = read.observedAtElapsedMs,
                slice = group.label,
            )
            when (group) {
                NativeAutoCalRefreshPlanner.Group.G2_PETROL_BUFFERS -> synchronized(lock) {
                    scratch.petrolCounters = vector(read.snapshot, AutoCalProtocol.NUM_BUF_UPD_PETR)
                }
                NativeAutoCalRefreshPlanner.Group.G4_GAS -> {
                    val thresholds = synchronized(lock) { Triple(gasLowThreshold, gasNormalThreshold, autoCalEnabled) }
                    synchronized(lock) {
                        acquisitionEpoch.acquisitionGroup(
                            currentSession,
                            probe.autoMatchCount,
                            scratch.petrolCounters,
                            vector(read.snapshot, AutoCalProtocol.NUM_BUF_UPD_GAS),
                        )
                    }
                    maturityEvents = read.gasProbe?.let { observed ->
                        maturityTracker.observe(
                            counters = observed.counters,
                            gasLowThreshold = thresholds.first,
                            gasNormalThreshold = thresholds.second,
                            enabled = true,
                            observedAtElapsedMs = observed.observedAtElapsedMs,
                        ).map { transition -> PendingMaturity(transition, observed.payloadHex) }
                    }.orEmpty()
                }
                else -> Unit
            }
        } else {
            // Referência: retém G5/G7/G8 (todos sob a mesma época, cada um confirmado) e publica o round inteiro de uma vez.
            synchronized(lock) {
                if (group == NativeAutoCalRefreshPlanner.Group.G5_MUL_ACT) scratch.hold.clear()
                scratch.hold += read
            }
            if (group == NativeAutoCalRefreshPlanner.Group.G8_GAS_RV) {
                roundRejected = !commitReferenceRound(currentSession, probe)
            }
        }

        if (maturityEvents.isNotEmpty()) {
            // Banda maturou: o snapshot completo (que carrega o evento) passa à frente; o round em voo morre.
            synchronized(lock) {
                pendingMaturity = maturityEvents
                snapshotRequested = true
                snapshotReason = "NATIVE_BAND_MATURED"
            }
            refreshPlanner.abandonRound()
            synchronized(lock) {
                scratch.petrolCounters = null
                scratch.hold.clear()
            }
        } else if (!roundRejected) {
            refreshPlanner.groupDone(group) // (round rejeitado já recuou via groupFailed: não zerar o recuo)
        }
        val roundClosed = !refreshPlanner.roundInProgress()
        if (roundClosed) {
            synchronized(lock) { scratch.petrolCounters = null }
            duty.roundCompleted(clockMs())
        }
        val thresholds = synchronized(lock) { Triple(gasLowThreshold, gasNormalThreshold, autoCalEnabled) }
        val thresholdsReady = thresholds.first != null && thresholds.second != null && thresholds.third == 1
        val referenceDue = refreshPlanner.due(clockMs()).reference
        synchronized(lock) {
            state = monitoringState(
                currentSession, probe, thresholdsReady,
                acquisitionRefresh = acquisition, referenceRefresh = !acquisition,
                referenceDue = referenceDue, counterEvent = counterEvent,
            )
        }
        if (roundClosed || maturityEvents.isNotEmpty()) onStateChanged()
    }

    fun statusJson(): JSONObject = synchronized(lock) {
        val liveEpoch = acquisitionEpochJson()
        JSONObject(state.toString())
            .put("latestSnapshot", JSONObject(latestSnapshot.toString()).put("liveAcquisitionEpoch", liveEpoch))
            .put("liveAcquisitionEpoch", liveEpoch)
            .put("snapshotRequested", snapshotRequested)
            .put("snapshotReason", snapshotReason)
            .put("tablesRevision", tablesRevisionValue)
            .put("acquisitionTiming", duty.json(serial.liveFrameAgeMs(), serial.liveFrameCount()))
            .put("slotArbiter", arbiter.json())
            .put("roundRemaining", JSONArray().also { array -> refreshPlanner.roundRemaining().forEach { array.put(it.label) } })
            .put("appAutomaticWrite", false)
            .put("manualAutoMatchExposed", false)
    }

    /**
     * Revisão das tabelas AutoCal (campos de aquisição/referência/snapshot completo): só sobe quando
     * uma resposta da ECU difere da que já estava em cache (status + payload por campo).
     */
    fun tablesRevision(): Long = tablesRevisionValue

    private var acquisitionMemo: Pair<JSONObject, JSONObject>? = null
    private var acquisitionMemoBlocked = false

    /**
     * Leve, para o piloto do refino (a cada tick do serviço): contador vivo de AutoMatch,
     * MAX/habilitado do último snapshot e a aquisição por banda, recalculada só quando o
     * snapshot muda (evita copiar o snapshot inteiro a cada 3 s).
     */
    fun autoMatchProgressJson(): JSONObject = synchronized(lock) {
        val read = state.optInt("autoMatchCount", -1).takeIf { state.has("autoMatchCount") && it >= 0 }
        val now = clockMs()
        val session = state.optLong("sessionId", 0L)
        val stateName = state.optString("state")
        if (read != null) { lastGoodCount = read; lastGoodCountAt = now; lastGoodCountSession = session }
        else if (stateName == "DISCONNECTED" || stateName == "IDLE" || session != lastGoodCountSession) lastGoodCount = null
        // A ECU entrega o contador continuamente: um probe que falhou é erro do app/transporte, não estado da ECU.
        // Por um curto prazo a última leitura boa da mesma sessão continua valendo (marcada `countStale`); depois, nulo.
        val probeFailed = stateName == "PROBE_FAILED"
        val stale = read == null && probeFailed && lastGoodCount != null && now - lastGoodCountAt <= COUNT_GRACE_MS
        val count = read ?: (if (stale) lastGoodCount else null)
        val snapshot = latestSnapshot
        // Época: depois de RESET_GAS/AutoMatch a aquisição antiga não vale para comparação. A aba AutoCal já mascara na
        // projeção; o Refino/piloto lia o snapshot cru e contava zonas velhas. Mesma máscara aqui (B4, 2026-10-05).
        val epoch = acquisitionEpochJson()
        val blocked = !epoch.optBoolean("comparisonAllowed", false)
        val acquisition = if (snapshot.has("fields")) {
            val source = if (blocked) AutoCalUiProjection.maskedAcquisition(snapshot, epoch, false) else snapshot
            acquisitionMemo?.takeIf { it.first === snapshot && acquisitionMemoBlocked == blocked }?.second
                ?: AutoCalAcquisition.fromSnapshot(source).also { acquisitionMemo = snapshot to it; acquisitionMemoBlocked = blocked }
        } else null
        JSONObject()
            .put("autoMatchCount", count ?: JSONObject.NULL)
            .put("countStale", stale)
            .put("readFailure", if (probeFailed) state.optString("message").ifBlank { "Leitura do AutoCal falhou" } else JSONObject.NULL)
            .put("maxAutomatch", snapshot.opt("maxAutomatch") ?: JSONObject.NULL)
            .put("autoCalEnabled", snapshot.opt("autoCalEnabled") ?: JSONObject.NULL)
            .put("acquisition", acquisition ?: JSONObject.NULL)
    }

    /** Última leitura boa do contador de AutoMatch (sob [lock]); sobrevive a um probe falho por [COUNT_GRACE_MS]. */
    private var lastGoodCount: Int? = null
    private var lastGoodCountAt = 0L
    private var lastGoodCountSession = 0L

    fun latestSnapshotJson(): JSONObject = synchronized(lock) {
        JSONObject(latestSnapshot.toString()).put("liveAcquisitionEpoch", acquisitionEpochJson())
    }

    /** Quando o AUTO_CAL_ENABLE foi relido pela última vez (elapsedRealtime); 0 = nunca nesta sessão. */
    @Volatile private var enabledCheckedAtMs = 0L

    /**
     * Relê só o AUTO_CAL_ENABLE (uma leitura READ_ONLY já existente, sem bytes novos) no máximo a cada
     * [ENABLE_RECHECK_MS]. Mudou por fora → snapshot completo, para a aquisição seguir o estado real.
     */
    private fun recheckEnabled(expectedSessionId: Long) {
        val now = clockMs()
        if (enabledCheckedAtMs > 0L && now - enabledCheckedAtMs < ENABLE_RECHECK_MS) return
        val read = serial.transaction(
            request = AutoCalProtocol.read(AutoCalProtocol.AUTO_CAL_ENABLE),
            reason = "AutoCal AUTO_CAL_ENABLE vivo",
            timeoutMs = 700,
            purgeBefore = false,
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
        )
        if (!read.ok) return
        val value = try {
            AutoCalProtocol.decode(AutoCalProtocol.AUTO_CAL_ENABLE, read.status, read.payload).rawValues.single()
        } catch (_: Exception) { return }
        if (value != 0 && value != 1) return
        enabledCheckedAtMs = now
        val changed: Boolean
        synchronized(lock) {
            changed = autoCalEnabled != null && autoCalEnabled != value
            autoCalEnabled = value
            if (latestSnapshot.optBoolean("available", false)) {
                latestSnapshot = JSONObject(latestSnapshot.toString())
                    .put("autoCalEnabled", value).put("frozen", value == 0).put("freshAcquisition", value == 1)
                    .put("autoCalEnabledOptimistic", false)
            }
            state = JSONObject(state.toString()).put("autoCalEnabled", value).put("autoCalEnabledAtMs", now)
            if (changed) { snapshotRequested = true; snapshotReason = "AUTO_CAL_ENABLE_CHANGED" }
        }
        if (changed) onStateChanged()
    }

    /** AUTO_CAL_ENABLE lido de verdade no `after` do recibo (readback da ação), ou nulo. */
    private fun enabledFromSnapshotJson(snapshot: JSONObject?): Int? {
        val fields = snapshot?.optJSONArray("fields") ?: return null
        for (i in 0 until fields.length()) {
            val f = fields.optJSONObject(i) ?: continue
            if (f.optString("key") != AutoCalProtocol.AUTO_CAL_ENABLE.key || f.optString("status") != AutoCalFieldStatus.VALID.name) continue
            val raw = f.optJSONArray("rawValues") ?: return null
            return if (raw.length() >= 1) raw.optInt(0, -1).takeIf { it == 0 || it == 1 } else null
        }
        return null
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
        group: NativeAutoCalRefreshPlanner.Group,
    ): GroupRead? = serial.unit(
        reason = "AutoCal aquisição operacional agrupada",
        expectedSessionId = expectedSessionId,
        workClass = Mp48WorkClass.READ_ONLY,
        telemetryAfter = true,
        waitTimeoutMs = GROUP_WAIT_MS,
    ) { unit ->
        readGroupFields(unit, group, expectedSessionId, label = "AutoCal aquisição", timeoutMs = 300, idPrefix = "AUTOCAL-ACQ")
    }

    private fun refreshReferenceGroup(
        expectedSessionId: Long,
        group: NativeAutoCalRefreshPlanner.Group,
    ): GroupRead? = serial.unit(
        reason = "AutoCal referência agrupada",
        expectedSessionId = expectedSessionId,
        workClass = Mp48WorkClass.READ_ONLY,
        telemetryAfter = true,
        waitTimeoutMs = GROUP_WAIT_MS,
    ) { unit ->
        readGroupFields(unit, group, expectedSessionId, label = "AutoCal referência", timeoutMs = 350, idPrefix = "AUTOCAL-REF")
    }

    /** As leituras do grupo (mesmos comandos de sempre, mesma ordem) dentro da unidade; sem probe aqui. */
    private fun readGroupFields(
        unit: Mp48SerialUnit,
        group: NativeAutoCalRefreshPlanner.Group,
        expectedSessionId: Long,
        label: String,
        timeoutMs: Int,
        idPrefix: String,
    ): GroupRead? {
        val startedAtMs = System.currentTimeMillis()
        val observations = ArrayList<AutoCalReadObservation>(group.fields.size)
        var gasProbe: MaturityProbe? = null
        for (field in group.fields) {
            val reply = unit.transaction(
                request = AutoCalProtocol.read(field),
                reason = "$label ${field.key}",
                timeoutMs = timeoutMs,
                purgeBefore = false,
            )
            observations += AutoCalReadObservation(
                field = field,
                status = reply.status.takeIf { it >= 0 },
                payload = reply.payload.takeIf { it.isNotEmpty() },
                capturedAtMs = System.currentTimeMillis(),
                error = if (reply.ok) null else reply.error.ifBlank { "Campo não confirmado" },
            )
            if (!reply.ok) return null
            if (field == AutoCalProtocol.NUM_BUF_UPD_GAS) {
                val decoded = try {
                    AutoCalProtocol.decode(AutoCalProtocol.NUM_BUF_UPD_GAS, reply.status, reply.payload)
                } catch (_: Exception) {
                    return null
                }
                gasProbe = MaturityProbe(
                    counters = decoded.rawValues.copyOf(),
                    payloadHex = reply.payload.toHex(),
                    observedAtElapsedMs = SystemClock.elapsedRealtime(),
                    status = reply.status,
                    payload = reply.payload.copyOf(),
                )
            }
        }
        val finishedAtMs = System.currentTimeMillis()
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = observations,
            expectedFields = group.fields,
            sessionId = "$idPrefix-$expectedSessionId-$finishedAtMs-${group.label}",
            source = AutoCalSnapshotSource.ECU_READ,
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs,
        )
        if (snapshot.partial || snapshot.validFieldCount != group.fields.size) return null
        // A coerência temporal das curvas de referência (<= 2 s) é exigida no ROUND (G5+G7+G8), não por grupo:
        // um grupo isolado traz só parte dos campos e o construtor o marcaria "incoerente" por construção.
        return GroupRead(
            group = group,
            snapshot = snapshot,
            gasProbe = gasProbe,
            observedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun mergeOperationalFields(
        patch: AutoCalSnapshot,
        refreshedAtElapsedMs: Long,
        slice: String,
    ) = mergeRefreshedFields(
        patch = patch,
        refreshedAtElapsedMs = refreshedAtElapsedMs,
        slice = slice,
        group = "acquisition",
        refreshTimestampKey = "acquisitionRefreshAtElapsedMs",
        reviseSnapshotHashOnChange = false,
    )

    private fun mergeReferenceFields(
        patch: AutoCalSnapshot,
        refreshedAtElapsedMs: Long,
        slice: String,
    ) = mergeRefreshedFields(
        patch = patch,
        refreshedAtElapsedMs = refreshedAtElapsedMs,
        slice = slice,
        group = "reference",
        refreshTimestampKey = "referenceRefreshAtElapsedMs",
        reviseSnapshotHashOnChange = true,
    )

    private fun mergeRefreshedFields(
        patch: AutoCalSnapshot,
        refreshedAtElapsedMs: Long,
        slice: String,
        group: String,
        refreshTimestampKey: String,
        reviseSnapshotHashOnChange: Boolean,
    ) {
        val patchFields = patch.toJson().optJSONArray("fields") ?: return
        var tablesChanged = false
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
                .put("incrementalRefreshSlice", slice)
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
            if (changed) {
                tablesRevisionValue += 1
                tablesChanged = true
            }
        }
        if (tablesChanged) {
            try { onTablesChanged() } catch (_: Exception) {}
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
        val writeGenerationAtStart = writeFence.current()
        val started = System.currentTimeMillis()
        val observations = ArrayList<AutoCalReadObservation>()
        var consecutiveTimeouts = 0
        // Também fatiado: grupos de <= 3 leituras, cada um precedido do slot do árbitro (>= 3 quadros vivos antes).
        var sliceReads = 0
        var sliceOpen = false
        var sliceOwned = false
        try {
            for (field in AutoCalProtocol.READ_ONLY_FIELDS.distinctBy { it.identity }) {
                if (!sliceOpen) {
                    // Se o árbitro não abrir a tempo (vivo parado), lê mesmo assim: o snapshot não pode travar.
                    sliceOwned = arbiter.awaitSlot(SNAPSHOT_SLICE_MAX_WAIT_MS)
                    sliceOpen = true
                    sliceReads = 0
                }
                val reply = serial.transaction(
                    request = AutoCalProtocol.read(field),
                    reason = "AutoCal snapshot ${field.key}",
                    timeoutMs = 350,
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
                sliceReads += 1
                if (sliceReads >= SNAPSHOT_SLICE_READS) {
                    if (sliceOwned) arbiter.end()
                    sliceOwned = false
                    sliceOpen = false
                    sliceReads = 0
                }
            }
        } finally {
            if (sliceOpen && sliceOwned) arbiter.end()
        }
        if (consecutiveTimeouts >= SNAPSHOT_MAX_CONSECUTIVE_TIMEOUTS) {
            // ECU muda: não monta snapshot parcial nem repete a varredura inteira a cada segundo.
            carryEvidence(autoMatchCounterEvent, countIncreased)
            backOffSnapshot()
            onStateChanged()
            return
        }
        if (writeFence.changedSince(writeGenerationAtStart)) {
            // Uma gravação K / ação AutoCal confirmou no meio da varredura: as fatias são de antes e de depois.
            // Descarta o snapshot inteiro; refaz no próximo round com o recuo de sempre.
            carryEvidence(autoMatchCounterEvent, countIncreased)
            backOffSnapshot()
            synchronized(lock) {
                snapshotRequested = true
                snapshotReason = "WRITE_DURING_SNAPSHOT"
            }
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
        var tablesChangedByFullSnapshot = false
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
            if (tableDigest(latestSnapshot) != tableDigest(decorated)) {
                tablesRevisionValue += 1
                tablesChangedByFullSnapshot = true
            }
            latestSnapshot = decorated
            scratch.petrolCounters = null
            scratch.hold.clear()
            scratch.pending = null
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

        if (tablesChangedByFullSnapshot) {
            try { onTablesChanged() } catch (_: Exception) {}
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

    /** Resumo (chave:status:payload por campo) para detectar se alguma tabela realmente mudou. */
    private fun tableDigest(snapshot: JSONObject): String {
        val fields = snapshot.optJSONArray("fields") ?: return ""
        val entries = ArrayList<String>(fields.length())
        repeat(fields.length()) { index ->
            val field = fields.optJSONObject(index) ?: return@repeat
            entries += field.optString("key") + ":" + field.optString("status") + ":" + field.optString("rawPayloadHex")
        }
        entries.sort()
        return entries.joinToString("|")
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
        /** Quanto tempo a última leitura boa do contador vale enquanto o probe falha (poucos ciclos); depois é desconhecido. */
        const val COUNT_GRACE_MS = 30_000L
        /** Intervalo mínimo entre releituras vivas do AUTO_CAL_ENABLE (o flag que decide "Pausar"/"Iniciar"). */
        const val ENABLE_RECHECK_MS = 5_000L
        /** ECU silenciosa: 2 timeouts de transporte seguidos abortam a varredura do snapshot completo. */
        private const val SNAPSHOT_MAX_CONSECUTIVE_TIMEOUTS = 2
        /** Época nativa mudou durante o snapshot completo: refaz logo (a ECU respondeu; não é falha de transporte). */
        private const val EPOCH_RETRY_MS = 1_000L
        private const val SNAPSHOT_BACKOFF_BASE_MS = 2_000L
        private const val SNAPSHOT_BACKOFF_CAP_MS = 60_000L
        private const val SNAPSHOT_BACKOFF_MAX_EXPONENT = 6
        /** Grupo lido que não obteve o probe de confirmação nesse prazo é descartado (dado velho não entra). */
        private const val PENDING_GROUP_MAX_AGE_MS = 3_000L
        /** Espera máxima pela fila do scheduler para uma unidade de <= 3 leituras. */
        private const val GROUP_WAIT_MS = 4_000L
        /** Snapshot completo fatiado: de quantas em quantas leituras o árbitro é consultado. */
        private const val SNAPSHOT_SLICE_READS = 3
        private const val SNAPSHOT_SLICE_MAX_WAIT_MS = 1_200L
    }
}
