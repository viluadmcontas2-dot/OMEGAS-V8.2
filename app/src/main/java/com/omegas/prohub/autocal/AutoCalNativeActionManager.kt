package com.omegas.prohub.autocal

import com.omegas.prohub.calibration.SerialWriteGuard
import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executa ações AutoCal nativas escolhidas e confirmadas pelo operador.
 *
 * Exceção única (spec 2026-10-07-autocal-apagar-lenta, rev2): [executeAutomaticPointDelete] apaga sozinho
 * pontos FORA DA CURVA do GNV e da gasolina, um combustível por comando, sem confirmação humana, pelo mesmo
 * writer, trava serial, ACK e readback do manual. A máscara do outro combustível vai inteira KEEP; os campos
 * dele são relidos antes/depois e qualquer mudança anormal é relatada no recibo. Curva K continua só com o dono.
 * Este gerenciador não possui agenda: quem decide quando apagar é o [AutoIdleCleanupCoordinator].
 */
class AutoCalNativeActionManager(
    private val receiptFile: File,
    private val isConnected: () -> Boolean,
    private val currentSessionId: () -> Long,
    private val otherCalibrationBusy: () -> Boolean,
    /** Mesma autoridade usada pelas demais mutações manuais da ECU. */
    private val unsafeMutationReason: () -> String? = { null },
    private val transaction: (
        request: ByteArray,
        reason: String,
        timeoutMs: Int,
        expectedSessionId: Long,
    ) -> UsbProtocolReply,
    private val fieldsForReceipt: List<AutoCalProtocol.Field> = AutoCalProtocol.READ_ONLY_FIELDS,
    private val onConfirmed: (JSONObject) -> Unit = {},
    private val onStateChanged: () -> Unit = {},
    /** Trava única da serial: ação AutoCal e escrita K nunca rodam juntas. */
    private val guard: SerialWriteGuard = SerialWriteGuard.shared,
    /** Recibo FAILED (manual ou automático), já gravado; inclui `mutationMayHaveStarted`. */
    private val onFailed: (JSONObject) -> Unit = {},
    /**
     * Último vetor VALID conhecido de um campo AutoCal (snapshot do monitor), ou nulo. Serve de "antes"
     * do readback manual (DELETE_POINT e RESET_*) e para recusar ponto que já está vazio.
     */
    private val lastKnownVector: (AutoCalProtocol.Field) -> IntArray? = { null },
    /** Relógio de parede das preparações (validade de 120 s); injetável nos testes. */
    private val wallClock: () -> Long = System::currentTimeMillis,
    /**
     * Guarda de contexto do apagamento AUTOMÁTICO em voo (revisão 2026-10-07 #2): motivo humano para abortar
     * (combustível trocou, rpm caiu, desarmado, USB mudou) ou nulo. Consultada antes das máscaras e antes do
     * commit; sem I/O. O caminho manual (do dono) nunca a consulta. Não muda nenhum byte dos comandos.
     */
    private val automaticContextReason: (AutoCalPointDeleteProtocol.Fuel) -> String? = { null },
) {
    /** Readback mostrou que a ECU aceitou o comando mas não apagou o ponto. */
    private class IneffectiveReadbackException(message: String, val details: JSONObject) : IllegalStateException(message)

    /** Releitura antes do apagamento automático: nenhum alvo tinha dado (nada a apagar, nada escrito). */
    private class PointsAlreadyEmptyException(val bands: List<Int>) :
        IllegalStateException("Este ponto já está vazio")

    /** Toda banda alvo mudou desde a marca (a ECU readquiriu): nada a apagar, nada escrito. */
    private class PointsChangedSinceMarkException(val bands: List<Int>) :
        IllegalStateException("O ponto foi readquirido desde a marca; nada foi apagado")

    /** O que a execução de um DELETE_POINT apurou, para o recibo FAILED usar os alvos e o "antes" reais. */
    private class PointDeleteRun {
        var effective: Preparation? = null
        var before: AutoCalSnapshot? = null
    }

    enum class Action(
        val request: ByteArray,
        val label: String,
        val description: String,
        val mayChangeMulAct: Boolean,
        val expectedEnableReadback: Int? = null,
        val operationalToggle: Boolean = false,
    ) {
        ENABLE_AUTO_CAL(
            AutoCalProtocol.setEnabled(true),
            "Habilitar Auto Calibration",
            "Escreve AUTO_CAL_ENABLE=1 e confirma por readback. Aquisição e AutoMatch permanecem lógica nativa da ECU.",
            false,
            1,
            true,
        ),
        DISABLE_AUTO_CAL(
            AutoCalProtocol.setEnabled(false),
            "Desabilitar Auto Calibration",
            "Escreve AUTO_CAL_ENABLE=0 e confirma por readback. Os buffers não são apagados por este comando; o efeito operacional mais amplo permanece nativo da ECU.",
            false,
            0,
            true,
        ),
        FINISH_AUTOCAL(
            byteArrayOf(),
            "Encerrar cota AutoMatch (técnico)",
            "Replica a ActionFinishAutocalExecute originalmente desabilitada no DFM: copia MAX_AUTOMATCH para NUM_AUTOMATCH_EXECUTED, aguarda 100 ms e exige readback. É compatibilidade técnica, não etapa normal do AutoCal e não desabilita AUTO_CAL_ENABLE.",
            false,
        ),
        FINISH_AUTOMATCH(
            byteArrayOf(),
            "Encerrar AutoMatch (debug)",
            "Replica o BtnFinishAutomatchClick localizado no PanelDbg oculto do ProgBase: copia MAX_AUTOMATCH para NUM_AUTOMATCH_EXECUTED sem o settle de 100 ms.",
            false,
        ),
        RESET_K_FACTOR(
            byteArrayOf(),
            "Reset Curva K (ProgBase)",
            "Replica ActionResetKFactorExecute: define os 30 elementos de MUL_ACT como 1.0 (Q14 0x4000), ponto a ponto, e exige readback completo.",
            true,
        ),
        RESET_PETROL(
            AutoCalProtocol.manualAction(AutoCalProtocol.ManualActionMode.RESET_PETROL),
            "Readquirir gasolina",
            "Usa a ação nativa dedicada Reset petrol point do ProgBase 4.2.0.6 (modo 0x01). A Curva K usa outro caminho. Após o ACK, o OMEGAS relê a ECU para atualizar o estado. Nenhum backup automático é exigido.",
            false,
        ),
        RESET_GAS(
            AutoCalProtocol.manualAction(AutoCalProtocol.ManualActionMode.RESET_GAS),
            "Readquirir GNV",
            "Usa a ação nativa dedicada Reset gas point do ProgBase 4.2.0.6 (modo 0x02). A Curva K usa outro caminho. Após o ACK, o OMEGAS relê a ECU para atualizar o estado. Nenhum backup automático é exigido.",
            false,
        ),
        RESET_ALL(
            AutoCalProtocol.manualAction(AutoCalProtocol.ManualActionMode.RESET_ALL),
            "Nova aquisição completa",
            "Usa a ação nativa Reset all do ProgBase 4.2.0.6 (modo 0x04). O Portmon canônico observou efeito amplo, inclusive sobre MUL_ACT; o OMEGAS captura K antes/depois e não promete seletividade além do que a ECU confirma.",
            true,
        ),
        DELETE_POINT(
            byteArrayOf(),
            "Readquirir este ponto",
            "Replica ChartDataClickSeries + ActionDeleteSelectedPointsExecute do ProgBase para um único ponto adquirido.",
            false,
        );
    }

    // ProgBase canônico + forensics byte-grounded:
    // command 0x24 / sub-op 0x04: 0x08=Manual AutoMatch, 0x01=Reset petrol,
    // 0x02=Reset gas, 0x04=Reset all. Modify Map Refs e Reset K Factor são separados.
    // OMEGAS preserva confirmação humana, ACK e readback. Backup é uma ação manual separada.
    private data class Preparation(
        val id: String,
        val action: Action,
        val sessionId: Long,
        val createdAtMs: Long,
        val expiresAtMs: Long,
        val pointDeleteTargets: List<AutoCalPointDeleteProtocol.Target> = emptyList(),
        val automatic: Boolean = false,
        val automationEvidence: JSONObject = JSONObject(),
    )

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-autocal-native-action").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)
    @Volatile private var safetyCheckedPreparationId: String? = null
    private val lock = Any()
    private var preparation: Preparation? = null
    @Volatile private var status = baseStatus("IDLE", "Nenhuma ação nativa preparada", 0)

    fun isBusy(): Boolean = busy.get()

    fun statusJson(): JSONObject = synchronized(lock) { JSONObject(status.toString()) }

    fun receiptsJson(): JSONArray = loadReceipts()

    fun prepare(actionName: String): JSONObject = try {
        val action = Action.valueOf(actionName.trim().uppercase())
        require(action != Action.DELETE_POINT) { "Readquirir ponto exige combustível e índice" }
        prepareInternal(action, emptyList())
    } catch (error: Exception) {
        failure(error.message ?: "Ação AutoCal inválida")
    }

    fun preparePointDelete(fuelName: String, index: Int): JSONObject = try {
        val target = AutoCalPointDeleteProtocol.Target(
            fuel = AutoCalPointDeleteProtocol.Fuel.parse(fuelName),
            index = index,
        )
        require(!knownEmpty(target)) { "Este ponto já está vazio" }
        prepareInternal(Action.DELETE_POINT, listOf(target))
    } catch (error: Exception) {
        failure(error.message ?: "Ponto AutoCal inválido")
    }

    fun preparePointDeletes(targets: Collection<AutoCalPointDeleteProtocol.Target>): JSONObject = try {
        val normalized = targets.distinctBy { it.fuel to it.index }
        require(normalized.isNotEmpty()) { "Selecione ao menos um ponto AutoCal" }
        // Seleção fantasma: ponto com contador 0 na última leitura não é enviado (nada a apagar).
        val (empty, withData) = normalized.partition(::knownEmpty)
        require(withData.isNotEmpty()) { "Este ponto já está vazio" }
        prepareInternal(Action.DELETE_POINT, withData)
            .put("skippedEmpty", JSONArray(empty.map(::pointTargetJson)))
    } catch (error: Exception) {
        failure(error.message ?: "Seleção AutoCal inválida")
    }

    private fun counterField(fuel: AutoCalPointDeleteProtocol.Fuel): AutoCalProtocol.Field = when (fuel) {
        AutoCalPointDeleteProtocol.Fuel.PETROL -> AutoCalProtocol.NUM_BUF_UPD_PETR
        AutoCalPointDeleteProtocol.Fuel.GAS -> AutoCalProtocol.NUM_BUF_UPD_GAS
    }

    private fun knownEmpty(target: AutoCalPointDeleteProtocol.Target): Boolean {
        val counters = try { lastKnownVector(counterField(target.fuel)) } catch (_: Exception) { null } ?: return false
        return counters.getOrNull(target.index) == 0
    }

    /**
     * Única mutação automática permitida (spec 2026-10-07-autocal-apagar-lenta, rev2): liberar bandas fora da
     * curva de UM combustível (GNV ou gasolina) para a ECU readquirir. Mesmo writer, trava serial, ACK e readback do manual; só
     * não há confirmação humana. Porta ocupada, preparação manual pendente ou outra calibração →
     * `retryLater=true`, nada enviado (o coordenador não consome o intervalo).
     */
    fun executeAutomaticPointDelete(
        targets: Collection<AutoCalPointDeleteProtocol.Target>,
        evidence: JSONObject,
    ): JSONObject {
        return try {
            automaticStart(targets, evidence)
        } catch (error: Exception) {
            automaticFailure(error.message ?: "Apagamento automático indisponível")
        }
    }

    private fun automaticStart(
        targets: Collection<AutoCalPointDeleteProtocol.Target>,
        evidence: JSONObject,
    ): JSONObject {
        val normalized = targets.distinctBy { it.fuel to it.index }.sortedBy { it.index }
        if (normalized.isEmpty()) return automaticFailure("Nenhum ponto GNV para apagar", retryLater = false)
        // Um combustível por comando: a máscara do outro vai toda KEEP (spec rev2).
        if (normalized.map { it.fuel }.distinct().size != 1) {
            return automaticFailure("O apagamento automático toca um combustível por vez", retryLater = false)
        }
        val prepared = synchronized(lock) {
            // Preparação manual esquecida e já expirada não segura o automático (revisão 2026-10-07 #4).
            preparation?.let { pending ->
                if (wallClock() > pending.expiresAtMs) {
                    preparation = null
                    status = baseStatus("IDLE", "Preparação expirada descartada; nenhuma ação enviada", 0)
                }
            }
            if (preparation != null) {
                return automaticFailure("Há uma ação manual preparada; o apagamento automático vai aguardar")
            }
            if (busy.get()) return automaticFailure("Outra ação AutoCal está em andamento")
            if (!isConnected()) return automaticFailure("USB desconectado")
            if (otherCalibrationBusy()) return automaticFailure("Outra operação de calibração está em andamento")
            unsafeMutationReason()?.let { return automaticFailure(it) }
            val sessionId = currentSessionId()
            if (sessionId <= 0L) return automaticFailure("Sessão USB inválida")
            if (!busy.compareAndSet(false, true)) return automaticFailure("Outra ação AutoCal está em andamento")
            if (!guard.tryAcquire(SerialWriteGuard.OWNER_AUTOCAL)) {
                busy.set(false)
                return automaticFailure("Aguarde: ${SerialWriteGuard.label(guard.holder())} em andamento")
            }
            val now = wallClock()
            Preparation(
                id = "ACA-AUTO-$now-${UUID.randomUUID().toString().take(8)}",
                action = Action.DELETE_POINT,
                sessionId = sessionId,
                createdAtMs = now,
                expiresAtMs = now + PREPARATION_TTL_MS,
                pointDeleteTargets = normalized,
                automatic = true,
                automationEvidence = JSONObject(evidence.toString()),
            )
        }
        var submitted = false
        try {
            update("QUEUED", "Ponto fora da curva; liberando para nova aquisição", 0, prepared, prepared.automationEvidence)
            executor.execute { executePrepared(prepared) }
            submitted = true
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            return automaticFailure("O executor do AutoCal foi encerrado; reabra o aplicativo", retryLater = false)
        } finally {
            if (!submitted) {
                guard.release(SerialWriteGuard.OWNER_AUTOCAL)
                busy.set(false)
                synchronized(lock) { status.put("busy", false) }
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("action", prepared.action.name)
            .put("automatic", true)
            .put("manualOnly", false)
            .put("humanConfirmed", false)
            .put("preparationId", prepared.id)
            .put("automationEvidence", JSONObject(prepared.automationEvidence.toString()))
    }

    private fun prepareInternal(
        action: Action,
        pointDeleteTargets: List<AutoCalPointDeleteProtocol.Target>,
    ): JSONObject {
        require(!busy.get()) { "Outra ação AutoCal está em andamento" }
        require(isConnected()) { "USB desconectado" }
        require(!otherCalibrationBusy()) { "Outra operação de calibração está em andamento" }
        unsafeMutationReason()?.let { throw IllegalStateException(it) }
        val sessionId = currentSessionId()
        require(sessionId > 0L) { "Sessão USB inválida" }
        val now = wallClock()
        val prepared = Preparation(
            id = "ACA-$now-${UUID.randomUUID().toString().take(8)}",
            action = action,
            sessionId = sessionId,
            createdAtMs = now,
            expiresAtMs = now + PREPARATION_TTL_MS,
            pointDeleteTargets = pointDeleteTargets,
        )
        val label = if (pointDeleteTargets.isNotEmpty()) {
            if (pointDeleteTargets.size == 1) "Readquirir ${pointDeleteTargets.single().toLabel()}"
            else "Readquirir ${pointDeleteTargets.size} pontos selecionados"
        } else action.label
        val description = if (pointDeleteTargets.isNotEmpty()) {
            if (pointDeleteTargets.size == 1) {
                "Apaga somente este ponto adquirido usando os masks nativos do ProgBase; os outros pontos ficam marcados para preservar."
            } else {
                "Apaga somente os pontos selecionados, inclusive entre gasolina e GNV, em um único par de masks nativos antes do commit."
            }
        } else action.description
        val details = when (pointDeleteTargets.size) {
            0 -> JSONObject()
            1 -> pointTargetJson(pointDeleteTargets.single())
            else -> pointTargetsJson(pointDeleteTargets)
        }
        synchronized(lock) {
            preparation = prepared
            status = baseStatus("PREPARED", label, 0)
                .put("preparationId", prepared.id)
                .put("action", action.name)
                .put("sessionId", sessionId)
                .put("expiresAtMs", prepared.expiresAtMs)
                .put("details", details)
        }
        onStateChanged()
        return JSONObject()
            .put("ok", true)
            .put("prepared", true)
            .put("preparationId", prepared.id)
            .put("action", action.name)
            .put("label", label)
            .put("description", description)
            .put(
                "commandHex",
                when {
                    pointDeleteTargets.isNotEmpty() -> "MASK U8[18] GNV + gasolina → 01 24 05 2A"
                    action == Action.FINISH_AUTOCAL || action == Action.FINISH_AUTOMATCH ->
                        "READ MAX_AUTOMATCH 0x0165:2 → WRITE NUM_AUTOMATCH_EXECUTED 0x0174 → READBACK"
                    action == Action.RESET_K_FACTOR ->
                        "30 × SetNumber MUL_ACT 0x0161[index] = 1.0 (Q14 0x4000) → READBACK"
                    else -> action.request.hex()
                },
            )
            .put("sessionId", sessionId)
            .put("expiresAtMs", prepared.expiresAtMs)
            .put("ecuMutation", true)
            .put("mayChangeMulAct", action.mayChangeMulAct)
            .put("readbackWitnesses", JSONArray(actionReadbackWitnesses(prepared).map { it.key }))
            .put("requiresCriticalConfirmation", !action.operationalToggle)
            .put("operationalOneTouch", action.operationalToggle)
            .put("details", details)
            .put("automatic", false)
            .put("manualOnly", true)
            .put("automaticBackup", false)
    }

    fun execute(preparationId: String): JSONObject {
        val prepared = synchronized(lock) {
            val current = preparation ?: return failure("Prepare a ação antes de confirmar")
            if (current.id != preparationId) return failure("Confirmação não corresponde à ação preparada")
            if (wallClock() > current.expiresAtMs) {
                preparation = null
                return failure("A preparação expirou; revise a ação novamente")
            }
            if (!isConnected() || currentSessionId() != current.sessionId) {
                preparation = null
                return failure("A sessão USB mudou; prepare a ação novamente")
            }
            if (otherCalibrationBusy()) return failure("Outra operação de calibração está em andamento")
            unsafeMutationReason()?.let {
                preparation = null
                return failure(it)
            }
            if (!busy.compareAndSet(false, true)) return failure("Outra ação AutoCal está em andamento")
            if (!guard.tryAcquire(SerialWriteGuard.OWNER_AUTOCAL)) {
                busy.set(false)
                return failure("Aguarde: ${SerialWriteGuard.label(guard.holder())} em andamento")
            }
            preparation = null
            current
        }
        // Trava e `busy` já foram adquiridos: nada entre aqui e a entrega ao executor pode vazá-los.
        var submitted = false
        try {
            update("QUEUED", "Ação confirmada; enviando para a ECU", 0, prepared)
            executor.execute { executePrepared(prepared) }
            submitted = true
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            return failure("O executor do AutoCal foi encerrado; reabra o aplicativo")
        } finally {
            if (!submitted) {
                guard.release(SerialWriteGuard.OWNER_AUTOCAL)
                busy.set(false)
                synchronized(lock) { status.put("busy", false) }
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("action", prepared.action.name)
            .put("automatic", false)
            .put("manualOnly", true)
            .put("humanConfirmed", true)
    }

    fun clearPreparation(): JSONObject {
        synchronized(lock) {
            if (busy.get()) return failure("A ação já foi enviada e não pode ser desfeita pelo aplicativo")
            preparation = null
            status = baseStatus("IDLE", "Preparação descartada; nenhuma ação enviada", 0)
        }
        onStateChanged()
        return JSONObject()
            .put("ok", true)
            .put("cleared", true)
            .put("writesStarted", false)
            .put("automatic", false)
            .put("manualOnly", true)
    }

    fun close() {
        synchronized(lock) { preparation = null }
        // Nunca interrompe uma ação em andamento (o reset K intercala 30 transações): com a ação
        // ativa o executor só deixa de aceitar trabalho novo e termina a que já começou.
        if (busy.get()) executor.shutdown() else executor.shutdownNow()
    }

    private fun executePrepared(prepared: Preparation) {
        val startedAt = System.currentTimeMillis()
        var before: AutoCalSnapshot? = null
        val pointRun = PointDeleteRun()
        try {
            before = if (prepared.action.mayChangeMulAct) {
                update("READING_BEFORE", "Capturando Curva K antes da ação", 4, prepared)
                readMulActSnapshot(prepared)
            } else null
            when (prepared.action) {
                Action.DELETE_POINT -> executePointDelete(prepared, startedAt, pointRun)
                Action.FINISH_AUTOCAL, Action.FINISH_AUTOMATCH -> executeFinish(prepared, startedAt)
                Action.RESET_K_FACTOR -> executeResetKFactor(prepared, startedAt, before)
                else -> executeFixedAction(prepared, startedAt, before)
            }
        } catch (error: Exception) {
            val message = error.message ?: "Ação AutoCal interrompida"
            val recovery = AutoCalRecoveryPolicy.classify(message).let { classified ->
                when (error) {
                    is PointsAlreadyEmptyException -> classified.copy(
                        reasonCode = "POINT_ALREADY_EMPTY",
                        retryable = false,
                        nextActionCode = "NOTHING_TO_DELETE",
                        nextAction = "O ponto já estava vazio na releitura; nada foi enviado.",
                    )
                    is PointsChangedSinceMarkException -> classified.copy(
                        reasonCode = "POINT_CHANGED_SINCE_MARK",
                        retryable = false,
                        nextActionCode = "NOTHING_TO_DELETE",
                        nextAction = "O ponto foi aprendido de novo desde a marca; nada foi enviado.",
                    )
                    else -> classified
                }
            }
            val failedFromState = synchronized(lock) { status.optString("state", "UNKNOWN") }
            val mutationMayHaveStarted = failedFromState in MUTATION_MAY_HAVE_STARTED_STATES
            val failureReceipt = failureReceipt(
                prepared = pointRun.effective ?: prepared,
                startedAt = startedAt,
                failedFromState = failedFromState,
                message = message,
                recovery = recovery,
                mutationMayHaveStarted = mutationMayHaveStarted,
                before = before ?: pointRun.before,
            )
            when (error) {
                is IneffectiveReadbackException -> failureReceipt
                    .put("effective", false)
                    .put("details", error.details)
                is PointsAlreadyEmptyException -> failureReceipt
                    .put("emptyBands", JSONArray(error.bands))
                is PointsChangedSinceMarkException -> failureReceipt
                    .put("skippedChanged", JSONArray(error.bands))
            }
            appendReceipt(failureReceipt)
            try { onFailed(JSONObject(failureReceipt.toString())) } catch (_: Exception) {}
            update("FAILED", message, 100, prepared, recovery.toJson()
                .put("failedFromState", failedFromState)
                .put("mutationMayHaveStarted", mutationMayHaveStarted)
                .put("receiptId", failureReceipt.getString("id")))
            synchronized(lock) {
                status
                    .put("reasonCode", recovery.reasonCode)
                    .put("recovery", recovery.toJson())
                    .put("failedFromState", failedFromState)
                    .put("mutationMayHaveStarted", mutationMayHaveStarted)
                    .put("receiptId", failureReceipt.getString("id"))
            }
        } finally {
            guard.release(SerialWriteGuard.OWNER_AUTOCAL)
            busy.set(false)
            synchronized(lock) { status.put("busy", false) }
            onStateChanged()
        }
    }

    private fun executeFixedAction(
        prepared: Preparation,
        startedAt: Long,
        before: AutoCalSnapshot?,
    ) {
        ensureSession(prepared)
        update("SENDING_ACTION", prepared.action.label, 18, prepared)
        val reply = transaction(
            prepared.action.request,
            "AutoCal ${prepared.action.name}",
            1_500,
            prepared.sessionId,
        )
        requireAck(reply, "A ECU não confirmou ${prepared.action.label}")
        if (prepared.action in setOf(
                Action.RESET_PETROL, Action.RESET_GAS, Action.RESET_ALL,
            )
        ) {
            Thread.sleep(HOST_MODE_SETTLE_MS)
        }
        ensureSession(prepared)
        update("READING_AFTER", "Atualizando estado da ECU", 72, prepared)
        val after = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ, actionReadbackWitnesses(prepared))
        validateActionReadback(prepared, after)
        confirm(prepared, reply, after, startedAt, before = before)
    }

    private fun executeFinish(prepared: Preparation, startedAt: Long) {
        ensureSession(prepared)
        update("READING_FINISH_SOURCE", "Lendo máximo e contador AutoMatch antes de finalizar", 12, prepared)

        val maxReply = transaction(
            AutoCalProtocol.read(AutoCalProtocol.MAX_AUTOMATCH),
            "AutoCal finish MAX_AUTOMATCH",
            1_200,
            prepared.sessionId,
        )
        requireAck(maxReply, "A ECU não confirmou MAX_AUTOMATCH")
        val maxAutomatch = AutoCalProtocol.decode(
            AutoCalProtocol.MAX_AUTOMATCH,
            maxReply.status,
            maxReply.payload,
        ).rawValues.single()

        ensureSession(prepared)
        val beforeCounterReply = transaction(
            AutoCalProtocol.read(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED),
            "AutoCal finish contador antes",
            1_200,
            prepared.sessionId,
        )
        requireAck(beforeCounterReply, "A ECU não confirmou NUM_AUTOMATCH_EXECUTED antes do Finish")
        val beforeCounter = AutoCalProtocol.decode(
            AutoCalProtocol.NUM_AUTOMATCH_EXECUTED,
            beforeCounterReply.status,
            beforeCounterReply.payload,
        ).rawValues.single()
        val counterWidthBytes = beforeCounterReply.payload.size
        require(counterWidthBytes == 1 || counterWidthBytes == 2) {
            "Largura inesperada de NUM_AUTOMATCH_EXECUTED: $counterWidthBytes"
        }

        ensureSession(prepared)
        val commitFrame = AutoCalProtocol.finishAutoCalCommit(maxAutomatch, counterWidthBytes)
        update(
            "SENDING_FINISH_COMMIT",
            "Finalizando ciclo AutoMatch nativo",
            42,
            prepared,
            JSONObject()
                .put("finishSource", "MAX_AUTOMATCH")
                .put("finishTarget", "NUM_AUTOMATCH_EXECUTED")
                .put("beforeCounter", beforeCounter)
                .put("maxAutomatch", maxAutomatch)
                .put("counterWidthBytes", counterWidthBytes)
                .put("commandHex", commitFrame.hex()),
        )
        val writeReply = transaction(
            commitFrame,
            "AutoCal ${prepared.action.name} commit",
            1_500,
            prepared.sessionId,
        )
        requireAck(writeReply, "A ECU não confirmou o commit de finalização")

        if (prepared.action == Action.FINISH_AUTOCAL) Thread.sleep(100L)

        ensureSession(prepared)
        update("VERIFYING_FINISH", "Confirmando contador final da ECU", 68, prepared)
        val targetReply = transaction(
            AutoCalProtocol.read(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED),
            "AutoCal finish contador readback",
            1_200,
            prepared.sessionId,
        )
        requireAck(targetReply, "A ECU não confirmou o readback de NUM_AUTOMATCH_EXECUTED")
        val committed = AutoCalProtocol.decode(
            AutoCalProtocol.NUM_AUTOMATCH_EXECUTED,
            targetReply.status,
            targetReply.payload,
        ).rawValues.single()
        require(committed == maxAutomatch) {
            "Finish AutoCal não persistiu: MAX_AUTOMATCH=$maxAutomatch, contador=$committed"
        }

        ensureSession(prepared)
        update("READING_AFTER", "Finalização confirmada; atualizando AutoCal", 82, prepared)
        val after = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ, actionReadbackWitnesses(prepared))
        validateActionReadback(prepared, after)
        confirm(
            prepared = prepared,
            reply = writeReply,
            after = after,
            startedAt = startedAt,
            details = JSONObject()
                .put("finishSource", "MAX_AUTOMATCH")
                .put("finishTarget", "NUM_AUTOMATCH_EXECUTED")
                .put("beforeCounter", beforeCounter)
                .put("maxAutomatch", maxAutomatch)
                .put("committedValue", committed)
                .put("counterWidthBytes", counterWidthBytes)
                .put("settleMs", if (prepared.action == Action.FINISH_AUTOCAL) 100 else 0)
                .put("commandHex", commitFrame.hex())
                .put("readbackValid", true),
        )
    }

    private fun executeResetKFactor(
        prepared: Preparation,
        startedAt: Long,
        before: AutoCalSnapshot?,
    ) {
        val frames = AutoCalProtocol.resetKFactorMulActFrames()
        var lastReply: UsbProtocolReply? = null
        frames.forEachIndexed { index, frame ->
            ensureSession(prepared)
            update(
                "RESETTING_K",
                "Neutralizando Curva K · ${index + 1}/${frames.size}",
                8 + ((index + 1) * 62 / frames.size),
                prepared,
                JSONObject()
                    .put("index", index)
                    .put("valueRaw", 0x4000)
                    .put("value", 1.0)
                    .put("commandHex", frame.hex()),
            )
            val reply = transaction(
                frame,
                "AutoCal RESET_K_FACTOR MUL_ACT[$index]",
                1_200,
                prepared.sessionId,
            )
            requireAck(reply, "A ECU não confirmou o ponto K ${index + 1}/${frames.size}")
            lastReply = reply
        }

        ensureSession(prepared)
        update("VERIFYING_K_RESET", "Confirmando Curva K neutra na ECU", 78, prepared)
        val after = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ, actionReadbackWitnesses(prepared))
        validateActionReadback(prepared, after)
        val actual = after.field(AutoCalProtocol.MUL_ACT)
            ?.takeIf { it.status == AutoCalFieldStatus.VALID }
            ?.rawValues
        require(actual != null && actual.size == frames.size && actual.all { it == 0x4000 }) {
            "Reset K não persistiu: MUL_ACT não retornou 30 fatores 1.0"
        }
        confirm(
            prepared = prepared,
            reply = requireNotNull(lastReply),
            after = after,
            startedAt = startedAt,
            details = JSONObject()
                .put("finishTarget", "MUL_ACT")
                .put("pointCount", frames.size)
                .put("neutralRaw", 0x4000)
                .put("neutralFactor", 1.0)
                .put("readbackValid", true),
            before = before,
        )
    }

    private fun executePointDelete(prepared: Preparation, startedAt: Long, run: PointDeleteRun = PointDeleteRun()) {
        require(prepared.pointDeleteTargets.isNotEmpty()) { "Pontos para readquirir não foram preparados" }
        // "Antes" do readback: no automático, releitura fresca (contador/tempo/MAP do combustível alvo + do OUTRO
        // combustível, que serve de guarda); no manual, o último vetor válido do monitor (sem bytes antes da máscara).
        val targetFuel = prepared.pointDeleteTargets.first().fuel
        val otherFuel = AutoCalPointDeleteProtocol.Fuel.entries.first { it != targetFuel }
        val beforeFields = bufferFields(targetFuel) + bufferFields(otherFuel)
        val beforeSnapshot = if (prepared.automatic) {
            ensureSession(prepared)
            update("READING_BEFORE", "Relendo GNV e gasolina antes de apagar", 4, prepared, prepared.automationEvidence)
            readSnapshot(
                prepared,
                AutoCalSnapshotSource.ECU_READ,
                beforeFields,
            ).also { snapshot ->
                run.before = snapshot
                val missing = beforeFields
                    .filter { snapshot.field(it)?.status != AutoCalFieldStatus.VALID }
                require(missing.isEmpty()) {
                    "A ECU não confirmou a releitura antes do apagamento: " + missing.joinToString(", ") { it.key }
                }
            }
        } else null
        fun beforeVector(field: AutoCalProtocol.Field): IntArray? =
            beforeSnapshot?.field(field)?.takeIf { it.status == AutoCalFieldStatus.VALID }?.rawValues
                ?: if (beforeSnapshot == null) try { lastKnownVector(field) } catch (_: Exception) { null } else null
        val before: Map<AutoCalPointDeleteProtocol.Fuel, BandVectors> =
            AutoCalPointDeleteProtocol.Fuel.entries.associateWith { fuel ->
                BandVectors(
                    counters = beforeVector(counterField(fuel)),
                    time = beforeVector(timeField(fuel)),
                    map = beforeVector(mapField(fuel)),
                )
            }
        val skippedChanged = mutableListOf<Int>()
        val targets = if (prepared.automatic) {
            val gas = before.getValue(targetFuel)
            val marked = markedValues(prepared.automationEvidence)
            val (withData, empty) = prepared.pointDeleteTargets.partition { (gas.counters?.getOrNull(it.index) ?: 0) > 0 }
            // Revisão 2026-10-07 #3: a banda readquiriu (andando) entre a marca e o disparo → pula e a marca sai.
            val (unchanged, changed) = withData.partition { target ->
                val mark = marked[target.index] ?: return@partition true
                gas.counters?.getOrNull(target.index) == mark.counter &&
                    (mark.time == 0 || gas.time?.getOrNull(target.index) == mark.time) &&
                    (mark.map == 0 || gas.map?.getOrNull(target.index) == mark.map)
            }
            skippedChanged += changed.map { it.index }
            if (unchanged.isEmpty()) {
                if (changed.isNotEmpty()) throw PointsChangedSinceMarkException(changed.map { it.index })
                throw PointsAlreadyEmptyException(empty.map { it.index })
            }
            unchanged
        } else prepared.pointDeleteTargets
        // Recibo, status e mensagem usam só os alvos realmente enviados (revisão #6).
        val effective = prepared.copy(pointDeleteTargets = targets)
        run.effective = effective
        val plan = AutoCalPointDeleteProtocol.multiPointPlan(targets)
        val maskFrames = plan.dropLast(1)
        val targetDetails = pointTargetsJson(targets)
        val actionLabel = if (targets.size == 1) targets.single().toLabel() else "${targets.size} pontos selecionados"
        // Automático: o contexto da decisão (combustível, rpm, armado, USB) é revalidado AGORA, antes de qualquer
        // máscara; ainda em READING_BEFORE, logo a falha não afirma mutação.
        if (prepared.automatic) requireAutomaticContext(targetFuel)
        update("SENDING_ACTION", "Readquirindo $actionLabel", 8, effective, targetDetails)
        maskFrames.forEachIndexed { step, request ->
            ensureSession(effective)
            val reply = transaction(
                request,
                "AutoCal point mask ${step + 1}/${maskFrames.size}",
                1_200,
                prepared.sessionId,
            )
            requireAck(reply, "A ECU não confirmou o mask ${step + 1}/${maskFrames.size}")
            update(
                "SENDING_ACTION",
                "Preparando seleção na ECU",
                8 + ((step + 1) * 54 / maskFrames.size),
                effective,
                targetDetails,
            )
        }
        ensureSession(effective)
        // Automático: revalida de novo antes do commit (máscaras já na ECU: a falha registra mutação possível e o
        // coordenador exige releitura; o commit nunca é reenviado).
        if (prepared.automatic) requireAutomaticContext(targetFuel)
        val commitReply = transaction(
            plan.last(),
            "AutoCal point delete commit",
            1_500,
            prepared.sessionId,
        )
        requireAck(commitReply, "A ECU não confirmou o commit da readquisição")
        Thread.sleep(POINT_DELETE_SETTLE_MS)
        ensureSession(effective)
        update("READING_AFTER", "Atualizando aquisição após o commit", 78, effective, targetDetails)
        val after = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ, actionReadbackWitnesses(prepared))
        validateActionReadback(prepared, after)
        val effect = pointDeleteEffect(targets, before, after)
        targetDetails
            .put("effective", true)
            .put("readbackAmbiguous", effect.ambiguous)
            .put("targetsRequested", JSONArray(prepared.pointDeleteTargets.map(::pointTargetJson)))
            .put("skippedChanged", JSONArray(skippedChanged))
            .put("effect", effect.rows)
        if (prepared.automatic) {
            targetDetails
                .put("automatic", true)
                .put("reason", prepared.automationEvidence.optString("reason", "fora_da_curva"))
                .put("fuel", targetFuel.wireName)
                .put("automationEvidence", JSONObject(prepared.automationEvidence.toString()))
                .put("otherFuelGuard", otherFuelGuard(otherFuel, requireNotNull(beforeSnapshot), after))
        }
        confirm(effective, commitReply, after, startedAt, targetDetails, before = beforeSnapshot)
    }

    private class BandVectors(val counters: IntArray?, val time: IntArray?, val map: IntArray?)

    private class MarkedValues(val counter: Int, val time: Int, val map: Int)

    private class PointDeleteEffect(val rows: JSONArray, val ambiguous: Boolean)

    /** Contador/tempo/MAP de cada banda na leitura que a marcou fora da curva (evidência do coordenador). */
    private fun markedValues(evidence: JSONObject): Map<Int, MarkedValues> {
        val bands = evidence.optJSONArray("bands") ?: return emptyMap()
        val out = HashMap<Int, MarkedValues>()
        repeat(bands.length()) { index ->
            val band = bands.optJSONObject(index) ?: return@repeat
            if (!band.has("counterAfter")) return@repeat
            out[band.optInt("band", -1)] = MarkedValues(
                counter = band.optInt("counterAfter"),
                time = band.optInt("timeRaw", 0),
                map = band.optInt("mapRaw", 0),
            )
        }
        return out
    }

    /** Contador + tempo + MAP de um combustível (releitura de antes do automático e guarda do outro combustível). */
    private fun bufferFields(fuel: AutoCalPointDeleteProtocol.Fuel): List<AutoCalProtocol.Field> =
        listOf(counterField(fuel), timeField(fuel), mapField(fuel))

    private fun timeField(fuel: AutoCalPointDeleteProtocol.Fuel): AutoCalProtocol.Field = when (fuel) {
        AutoCalPointDeleteProtocol.Fuel.PETROL -> AutoCalProtocol.PETR_INJ_TBUF
        AutoCalPointDeleteProtocol.Fuel.GAS -> AutoCalProtocol.PETR_INJ_TBUF_GAS
    }

    private fun mapField(fuel: AutoCalPointDeleteProtocol.Fuel): AutoCalProtocol.Field = when (fuel) {
        AutoCalPointDeleteProtocol.Fuel.PETROL -> AutoCalProtocol.MNFLD_PRESS_BUF
        AutoCalPointDeleteProtocol.Fuel.GAS -> AutoCalProtocol.MNFLD_PRESS_BUF_GAS
    }

    /**
     * Readback do DELETE_POINT, por alvo (revisão 2026-10-07 #1):
     * - contador 0 → DELETED;
     * - contador menor que o anterior, ou contador <= 1 com tempo/MAP diferentes do anterior → a ECU apagou e
     *   readquiriu em seguida (carro andando na MAP da banda) → DELETED_AND_REACQUIRED;
     * - anterior conhecido, contador >= anterior e tempo/MAP iguais (onde ambos conhecidos) → NOT_DELETED:
     *   a ECU aceitou mas não apagou → effective=false;
     * - o resto (anterior nulo ou velho, valores que mudaram por outro motivo) → AMBIGUOUS: confirma com
     *   `readbackAmbiguous`, o automático espera uma releitura; nunca vira FAILED à toa.
     */
    private fun pointDeleteEffect(
        targets: List<AutoCalPointDeleteProtocol.Target>,
        before: Map<AutoCalPointDeleteProtocol.Fuel, BandVectors>,
        after: AutoCalSnapshot,
    ): PointDeleteEffect {
        fun afterValue(field: AutoCalProtocol.Field, index: Int): Int? =
            after.field(field)?.takeIf { it.status == AutoCalFieldStatus.VALID }?.rawValues?.getOrNull(index)
        val rows = JSONArray()
        val stuck = mutableListOf<AutoCalPointDeleteProtocol.Target>()
        var ambiguous = false
        targets.forEach { target ->
            val old = before[target.fuel]
            val i = target.index
            val now = afterValue(counterField(target.fuel), i)
            val oldCounter = old?.counters?.getOrNull(i)
            val timeBefore = old?.time?.getOrNull(i)
            val mapBefore = old?.map?.getOrNull(i)
            val timeAfter = afterValue(timeField(target.fuel), i)
            val mapAfter = afterValue(mapField(target.fuel), i)
            val valuesDiffer = (timeBefore != null && timeAfter != null && timeBefore != timeAfter) ||
                (mapBefore != null && mapAfter != null && mapBefore != mapAfter)
            val result = when {
                now == null -> "NOT_DELETED"
                now == 0 -> "DELETED"
                oldCounter != null && now < oldCounter -> "DELETED_AND_REACQUIRED"
                now <= 1 && valuesDiffer -> "DELETED_AND_REACQUIRED"
                oldCounter != null && now >= oldCounter && !valuesDiffer -> "NOT_DELETED"
                else -> "AMBIGUOUS"
            }
            if (result == "NOT_DELETED") stuck += target
            if (result == "AMBIGUOUS") ambiguous = true
            rows.put(
                pointTargetJson(target)
                    .put("counterBefore", oldCounter ?: JSONObject.NULL)
                    .put("counterAfter", now ?: JSONObject.NULL)
                    .put("timeBefore", timeBefore ?: JSONObject.NULL)
                    .put("timeAfter", timeAfter ?: JSONObject.NULL)
                    .put("mapBefore", mapBefore ?: JSONObject.NULL)
                    .put("mapAfter", mapAfter ?: JSONObject.NULL)
                    .put("result", result)
                    .put("effective", result != "NOT_DELETED"),
            )
        }
        if (stuck.isNotEmpty()) {
            throw IneffectiveReadbackException(
                "Readback: a ECU aceitou o comando, mas " + stuck.joinToString(", ") { it.toLabel() } +
                    " não foi apagado",
                JSONObject().put("effective", false).put("effect", rows),
            )
        }
        return PointDeleteEffect(rows, ambiguous)
    }

    /**
     * Guarda do OUTRO combustível (o que não foi alvo), antes/depois do apagamento automático. Contador subiu
     * (ou contador igual com valor novo) = aprendizado normal da ECU. Contador caiu ou tempo/MAP zerou = anormal
     * → o coordenador pausa o automático na conexão e registra.
     */
    private fun otherFuelGuard(
        fuel: AutoCalPointDeleteProtocol.Fuel,
        before: AutoCalSnapshot,
        after: AutoCalSnapshot,
    ): JSONObject {
        fun values(snapshot: AutoCalSnapshot, field: AutoCalProtocol.Field): IntArray? =
            snapshot.field(field)?.takeIf { it.status == AutoCalFieldStatus.VALID }?.rawValues
        val counterBefore = values(before, counterField(fuel))
        val counterAfter = values(after, counterField(fuel))
        val timeBefore = values(before, timeField(fuel))
        val timeAfter = values(after, timeField(fuel))
        val mapBefore = values(before, mapField(fuel))
        val mapAfter = values(after, mapField(fuel))
        val complete = listOf(counterBefore, counterAfter, timeBefore, timeAfter, mapBefore, mapAfter).all { it != null }
        val bands = JSONArray()
        var abnormal = !complete
        if (complete) {
            for (index in 0 until AutoCalPointDeleteProtocol.POINT_COUNT) {
                val cb = counterBefore!!.getOrElse(index) { 0 }
                val ca = counterAfter!!.getOrElse(index) { 0 }
                val tb = timeBefore!!.getOrElse(index) { 0 }
                val ta = timeAfter!!.getOrElse(index) { 0 }
                val mb = mapBefore!!.getOrElse(index) { 0 }
                val ma = mapAfter!!.getOrElse(index) { 0 }
                if (cb == ca && tb == ta && mb == ma) continue
                val bandAbnormal = ca < cb || (tb != 0 && ta == 0) || (mb != 0 && ma == 0)
                if (bandAbnormal) abnormal = true
                bands.put(
                    JSONObject()
                        .put("band", index)
                        .put("counterBefore", cb).put("counterAfter", ca)
                        .put("timeBefore", tb).put("timeAfter", ta)
                        .put("mapBefore", mb).put("mapAfter", ma)
                        .put("learning", !bandAbnormal)
                        .put("abnormal", bandAbnormal),
                )
            }
        }
        return JSONObject()
            .put("complete", complete)
            .put("changed", bands.length() > 0)
            .put("abnormal", abnormal)
            .put("bands", bands)
            .put("fuel", fuel.wireName)
            .put("mask", "KEEP_ALL")
    }

    private fun confirm(
        prepared: Preparation,
        reply: UsbProtocolReply,
        after: AutoCalSnapshot,
        startedAt: Long,
        details: JSONObject = JSONObject(),
        before: AutoCalSnapshot? = null,
    ) {
        val receipt = receipt(prepared, reply, after, startedAt, details, before)
        appendReceipt(receipt)
        try { onConfirmed(receipt) } catch (_: Exception) {}
        val confirmedMessage = when {
            prepared.action == Action.DELETE_POINT && prepared.automatic ->
                "${prepared.pointDeleteTargets.first().fuel.label}: ${prepared.pointDeleteTargets.size} ponto(s) fora da curva liberado(s) automaticamente; readback confirmado"
            prepared.action == Action.DELETE_POINT -> {
                val count = prepared.pointDeleteTargets.size
                if (count <= 1) "Ponto liberado para nova aquisição; estado da ECU atualizado"
                else "$count pontos liberados para nova aquisição; estado da ECU atualizado"
            }
            prepared.action == Action.FINISH_AUTOCAL || prepared.action == Action.FINISH_AUTOMATCH -> {
                val max = details.optInt("maxAutomatch", -1)
                val committed = details.optInt("committedValue", -1)
                if (max >= 0 && committed >= 0) {
                    "Cota AutoMatch ajustada · $committed/$max confirmado pela ECU · aquisição não foi pausada"
                } else {
                    "Contador AutoMatch ajustado e confirmado pela ECU · aquisição não foi pausada"
                }
            }
            prepared.action.expectedEnableReadback != null || prepared.action == Action.RESET_K_FACTOR ->
                "ACK + readback específico confirmados pela ECU"
            // Reset de aquisição só confere campos-testemunha; contadores e zonas não são comparados.
            else -> "A ECU respondeu (ACK) e o estado foi relido"
        }
        update(
            "CONFIRMED",
            confirmedMessage,
            100,
            prepared,
            receipt,
        )
    }

    private fun requireAck(reply: UsbProtocolReply, fallback: String) {
        require(reply.ok && reply.status == Mp48Protocol.STATUS_ACK) {
            reply.error.trim().takeIf { it.isNotBlank() }?.let { detail ->
                "$fallback: $detail"
            } ?: fallback
        }
    }

    private fun readMulActSnapshot(prepared: Preparation): AutoCalSnapshot {
        val started = System.currentTimeMillis()
        ensureSession(prepared)
        val field = AutoCalProtocol.MUL_ACT
        val reply = transaction(
            AutoCalProtocol.read(field),
            "Recibo AutoCal MUL_ACT antes da ação",
            1_200,
            prepared.sessionId,
        )
        requireAck(reply, "A ECU não confirmou MUL_ACT antes da ação")
        val snapshot = AutoCalSnapshotBuilder.build(
            observations = listOf(
                AutoCalReadObservation(
                    field = field,
                    status = reply.status,
                    payload = reply.payload,
                    capturedAtMs = System.currentTimeMillis(),
                    error = null,
                ),
            ),
            expectedFields = listOf(field),
            sessionId = "${prepared.id}-BEFORE_MUL_ACT",
            source = AutoCalSnapshotSource.ECU_READ,
            startedAtMs = started,
            finishedAtMs = System.currentTimeMillis(),
        )
        val raw = snapshot.field(field)
            ?.takeIf { it.status == AutoCalFieldStatus.VALID }
            ?.rawValues
        require(raw != null && raw.size == 30) {
            "MUL_ACT antes da ação não retornou 30 fatores válidos"
        }
        return snapshot
    }

    private fun readSnapshot(
        prepared: Preparation,
        source: AutoCalSnapshotSource,
        fields: List<AutoCalProtocol.Field>,
    ): AutoCalSnapshot {
        val started = System.currentTimeMillis()
        val selectedFields = fields.distinctBy { it.identity }
        val observations = selectedFields.map { field ->
            ensureSession(prepared)
            val reply = transaction(
                AutoCalProtocol.read(field),
                "Recibo AutoCal ${field.key}",
                1_200,
                prepared.sessionId,
            )
            AutoCalReadObservation(
                field = field,
                status = reply.status.takeIf { it >= 0 },
                payload = reply.payload.takeIf { it.isNotEmpty() },
                capturedAtMs = System.currentTimeMillis(),
                error = if (reply.ok) null else reply.error.ifBlank { "Campo não confirmado" },
            )
        }
        return AutoCalSnapshotBuilder.build(
            observations = observations,
            expectedFields = selectedFields,
            sessionId = "${prepared.id}-${source.name}",
            source = source,
            startedAtMs = started,
            finishedAtMs = System.currentTimeMillis(),
        )
    }

    private fun receipt(
        prepared: Preparation,
        reply: UsbProtocolReply,
        after: AutoCalSnapshot,
        startedAt: Long,
        details: JSONObject = JSONObject(),
        before: AutoCalSnapshot? = null,
    ): JSONObject {
        val receipt = JSONObject()
            .put("id", "RECEIPT-${UUID.randomUUID()}")
            .put("preparationId", prepared.id)
            .put("action", prepared.action.name)
            .put("label", prepared.action.label)
            .put("outcome", "CONFIRMED")
            .put(
                "commandHex",
                when {
                    details.optString("commandHex").isNotBlank() -> details.optString("commandHex")
                    prepared.pointDeleteTargets.isNotEmpty() -> "MASK U8[18] GNV + gasolina → 01 24 05 2A"
                    else -> prepared.action.request.hex()
                },
            )
            .put("details", details)
            .put("ackStatus", reply.status)
            .put("sessionId", prepared.sessionId)
            .put("startedAtMs", startedAt)
            .put("finishedAtMs", System.currentTimeMillis())
            .put("afterHash", after.snapshotHash)
            .put("afterPartial", after.partial)
            .put("after", after.toJson())
            .put("ecuMutation", true)
            .put("mayChangeMulAct", prepared.action.mayChangeMulAct)
            .put("humanConfirmed", !prepared.automatic)
            .put("automatic", prepared.automatic)
            .put("manualOnly", !prepared.automatic)
            .put(
                "automationEvidence",
                if (prepared.automatic) JSONObject(prepared.automationEvidence.toString()) else JSONObject.NULL,
            )
            .put("readbackValid", true)
            .put("readbackWitnesses", JSONArray(actionReadbackWitnesses(prepared).map { it.key }))
            .put("preMutationBackup", JSONObject.NULL)
            .put("automaticBackup", false)
            .put("automaticRollback", false)
            .put(
                "pointDelete",
                when (prepared.pointDeleteTargets.size) {
                    0 -> JSONObject.NULL
                    1 -> pointTargetJson(prepared.pointDeleteTargets.single())
                    else -> pointTargetsJson(prepared.pointDeleteTargets)
                },
            )

        if (before != null) {
            receipt
                .put("beforeHash", before.snapshotHash)
                .put("before", before.toJson())
        }
        return receipt
    }

    private fun failureReceipt(
        prepared: Preparation,
        startedAt: Long,
        failedFromState: String,
        message: String,
        recovery: AutoCalRecoveryPolicy.Recovery,
        mutationMayHaveStarted: Boolean,
        before: AutoCalSnapshot?,
    ): JSONObject {
        val receipt = JSONObject()
            .put("id", "RECEIPT-${UUID.randomUUID()}")
            .put("preparationId", prepared.id)
            .put("action", prepared.action.name)
            .put("label", prepared.action.label)
            .put("outcome", "FAILED")
            .put("failureMessage", message)
            .put("failedFromState", failedFromState)
            .put("reasonCode", recovery.reasonCode)
            .put("recovery", recovery.toJson())
            .put("mutationMayHaveStarted", mutationMayHaveStarted)
            .put("automaticRetry", false)
            .put("sessionId", prepared.sessionId)
            .put("startedAtMs", startedAt)
            .put("finishedAtMs", System.currentTimeMillis())
            .put("humanConfirmed", !prepared.automatic)
            .put("automatic", prepared.automatic)
            .put("manualOnly", !prepared.automatic)
            .put(
                "automationEvidence",
                if (prepared.automatic) JSONObject(prepared.automationEvidence.toString()) else JSONObject.NULL,
            )
            .put("automaticRollback", false)
            .put("automaticBackup", false)
            .put(
                "pointDelete",
                when (prepared.pointDeleteTargets.size) {
                    0 -> JSONObject.NULL
                    1 -> pointTargetJson(prepared.pointDeleteTargets.single())
                    else -> pointTargetsJson(prepared.pointDeleteTargets)
                },
            )
        if (before != null) {
            receipt
                .put("beforeHash", before.snapshotHash)
                .put("before", before.toJson())
        }
        return receipt
    }

    private fun pointTargetsJson(targets: Collection<AutoCalPointDeleteProtocol.Target>): JSONObject {
        val normalized = targets.distinctBy { it.fuel to it.index }
        return JSONObject()
            .put("count", normalized.size)
            .put("petrolCount", normalized.count { it.fuel == AutoCalPointDeleteProtocol.Fuel.PETROL })
            .put("gasCount", normalized.count { it.fuel == AutoCalPointDeleteProtocol.Fuel.GAS })
            .put("targets", JSONArray(normalized.map(::pointTargetJson)))
            .put("automaticBackup", false)
    }

    private fun pointTargetJson(target: AutoCalPointDeleteProtocol.Target): JSONObject = JSONObject()
        .put("fuel", target.fuel.wireName)
        .put("fuelLabel", target.fuel.label)
        .put("index", target.index)
        .put("point", target.index + 1)
        .put("zone", target.zone)
        .put("automaticBackup", false)

    private fun petrolAcquisitionReadbackFields(): List<AutoCalProtocol.Field> = listOf(
        AutoCalProtocol.NUM_BUF_UPD_PETR,
        AutoCalProtocol.PETR_INJ_TBUF,
        AutoCalProtocol.MNFLD_PRESS_BUF,
        AutoCalProtocol.ACQUIRED_ZONES_PETROL,
    )

    private fun gasAcquisitionReadbackFields(): List<AutoCalProtocol.Field> = listOf(
        AutoCalProtocol.NUM_BUF_UPD_GAS,
        AutoCalProtocol.PETR_INJ_TBUF_GAS,
        AutoCalProtocol.MNFLD_PRESS_BUF_GAS,
        AutoCalProtocol.ACQUIRED_ZONES_GAS,
    )

    private fun actionReadbackWitnesses(prepared: Preparation): List<AutoCalProtocol.Field> {
        val witnesses = when (prepared.action) {
            Action.ENABLE_AUTO_CAL, Action.DISABLE_AUTO_CAL -> listOf(AutoCalProtocol.AUTO_CAL_ENABLE)
            Action.RESET_PETROL -> petrolAcquisitionReadbackFields()
            Action.RESET_GAS -> gasAcquisitionReadbackFields()
            Action.RESET_ALL -> petrolAcquisitionReadbackFields() +
                gasAcquisitionReadbackFields() +
                AutoCalProtocol.MUL_ACT
            Action.FINISH_AUTOCAL, Action.FINISH_AUTOMATCH -> listOf(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED)
            Action.RESET_K_FACTOR -> listOf(AutoCalProtocol.MUL_ACT)
            Action.DELETE_POINT -> {
                val scoped = mutableListOf<AutoCalProtocol.Field>()
                // Automático (um combustível por vez): o outro combustível também é relido para provar que não mudou.
                if (prepared.automatic ||
                    prepared.pointDeleteTargets.any { it.fuel == AutoCalPointDeleteProtocol.Fuel.PETROL }
                ) {
                    scoped += petrolAcquisitionReadbackFields()
                }
                if (prepared.automatic ||
                    prepared.pointDeleteTargets.any { it.fuel == AutoCalPointDeleteProtocol.Fuel.GAS }
                ) {
                    scoped += gasAcquisitionReadbackFields()
                }
                scoped
            }
        }
        return witnesses.distinctBy { it.identity }
    }

    private fun validateActionReadback(prepared: Preparation, after: AutoCalSnapshot) {
        // Snapshot parcial continua permitido para observação, mas uma mutação só
        // vira CONFIRMED quando as superfícies específicas daquela ação voltam
        // válidas da mesma sessão. Não inferimos seletividade/zero quando o corpus
        // original não prova esse pós-estado.
        val witnesses = actionReadbackWitnesses(prepared)
        require(witnesses.isNotEmpty()) {
            "Readback obrigatório sem testemunha definida para ${prepared.action.label}"
        }
        val missing = witnesses.filter { after.field(it)?.status != AutoCalFieldStatus.VALID }
        require(missing.isEmpty()) {
            "Readback obrigatório ausente para ${prepared.action.label}: " +
                missing.joinToString(", ") { it.key }
        }

        // Reler GNV/gasolina: "concluído" só quando a releitura mostra o que o comando pediu (decisão do dono,
        // 2026-10-05). Compara com o "antes" (último vetor válido do monitor): os contadores caíram e as flags de
        // zona zeraram. Tolera UMA banda readquirindo logo após o reset (contador 1, ou menor que o anterior) e a
        // flag da zona dela; mais que isso é dado antigo, não conclusão.
        val resetPairs = when (prepared.action) {
            Action.RESET_GAS -> listOf(AutoCalProtocol.NUM_BUF_UPD_GAS to AutoCalProtocol.ACQUIRED_ZONES_GAS)
            Action.RESET_PETROL -> listOf(AutoCalProtocol.NUM_BUF_UPD_PETR to AutoCalProtocol.ACQUIRED_ZONES_PETROL)
            Action.RESET_ALL -> listOf(
                AutoCalProtocol.NUM_BUF_UPD_GAS to AutoCalProtocol.ACQUIRED_ZONES_GAS,
                AutoCalProtocol.NUM_BUF_UPD_PETR to AutoCalProtocol.ACQUIRED_ZONES_PETROL,
            )
            else -> emptyList()
        }
        val notZero = mutableListOf<AutoCalProtocol.Field>()
        resetPairs.forEach { (counterField, zonesField) ->
            val counters = after.field(counterField)?.rawValues ?: IntArray(0)
            val before = try { lastKnownVector(counterField) } catch (_: Exception) { null }
            val filled = counters.indices.filter { counters[it] != 0 }
            val tolerated = filled.singleOrNull()?.takeIf { index ->
                counters[index] <= 1 || (before?.getOrNull(index)?.let { counters[index] < it } == true)
            }
            if (filled.isNotEmpty() && tolerated == null) notZero += counterField
            val toleratedZone = tolerated?.let { AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.GAS, it).zone - 1 }
            val zones = after.field(zonesField)?.rawValues ?: IntArray(0)
            if (zones.indices.any { zones[it] != 0 && it != toleratedZone }) notZero += zonesField
        }
        require(notZero.isEmpty()) {
            "A ECU aceitou o comando, mas a releitura ainda mostra leituras antigas (" +
                notZero.joinToString(", ") { it.key } + " não zerados). Toque de novo em alguns segundos."
        }

        val expected = prepared.action.expectedEnableReadback ?: return
        val actual = after.field(AutoCalProtocol.AUTO_CAL_ENABLE)
            ?.takeIf { it.status == AutoCalFieldStatus.VALID }
            ?.rawValues
            ?.singleOrNull()
        require(actual == expected) {
            "Readback AUTO_CAL_ENABLE divergente: esperado $expected, ECU ${actual ?: "sem dado"}"
        }
    }
    private fun requireAutomaticContext(fuel: AutoCalPointDeleteProtocol.Fuel) {
        val reason = try { automaticContextReason(fuel) } catch (error: Exception) {
            "Contexto do apagamento automático indisponível: ${error.message ?: "falha"}"
        }
        if (reason != null) throw IllegalStateException(reason)
    }

    private fun ensureSession(prepared: Preparation) {
        require(isConnected()) { "USB desconectado durante a ação AutoCal" }
        require(currentSessionId() == prepared.sessionId) { "Sessão USB mudou durante a ação AutoCal" }
        require(!otherCalibrationBusy()) { "Outra calibração assumiu a sessão" }
        // A segurança (idade da telemetria etc.) é conferida UMA vez, antes do primeiro quadro.
        // Depois que o lote começou, abortar entre quadros deixaria a ECU pela metade.
        if (safetyCheckedPreparationId != prepared.id) {
            unsafeMutationReason()?.let { throw IllegalStateException(it) }
            safetyCheckedPreparationId = prepared.id
        }
    }

    private fun update(
        stateName: String,
        message: String,
        progress: Int,
        prepared: Preparation,
        details: JSONObject = JSONObject(),
    ) {
        synchronized(lock) {
            status = baseStatus(stateName, message, progress)
                .put("action", prepared.action.name)
                .put("preparationId", prepared.id)
                .put("sessionId", prepared.sessionId)
                .put("details", details)
                .put("automatic", prepared.automatic)
                .put("manualOnly", !prepared.automatic)
                .put("humanConfirmed", !prepared.automatic)
        }
        onStateChanged()
    }

    private fun baseStatus(stateName: String, message: String, progress: Int): JSONObject = JSONObject()
        .put("state", stateName)
        .put("busy", busy.get())
        .put("message", message)
        .put("progress", progress.coerceIn(0, 100))
        .put("updatedAt", System.currentTimeMillis())
        .put("automatic", false)
        .put("manualOnly", true)

    private fun loadReceipts(): JSONArray = try {
        if (receiptFile.isFile) JSONArray(receiptFile.readText(Charsets.UTF_8)) else JSONArray()
    } catch (_: Exception) {
        JSONArray()
    }

    private fun appendReceipt(receipt: JSONObject) {
        atomicWrite(receiptFile, trimReceipts(loadReceipts().put(receipt), MAX_RECEIPTS).toString(2))
    }

    private fun atomicWrite(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun failure(message: String): JSONObject {
        val recovery = AutoCalRecoveryPolicy.classify(message)
        return JSONObject()
            .put("ok", false)
            .put("error", message)
            .put("reasonCode", recovery.reasonCode)
            .put("recovery", recovery.toJson())
            .put("automatic", false)
            .put("manualOnly", true)
    }

    private fun automaticFailure(message: String, retryLater: Boolean = true): JSONObject = failure(message)
        .put("automatic", true)
        .put("manualOnly", false)
        .put("humanConfirmed", false)
        .put("retryLater", retryLater)
        .put("writesStarted", false)

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    companion object {
        private const val PREPARATION_TTL_MS = 120_000L
        private const val HOST_MODE_SETTLE_MS = 1_000L
        private const val POINT_DELETE_SETTLE_MS = 500L
        private const val MAX_RECEIPTS = 200

        /**
         * Mantém os últimos [max] recibos manuais e os últimos [max] automáticos, na ordem original.
         * O apagamento automático pode disparar muitas vezes na cidade; sem a separação ele empurraria
         * o histórico das ações do dono para fora do arquivo.
         */
        internal fun trimReceipts(all: JSONArray, max: Int): JSONArray {
            val automatic = BooleanArray(all.length()) { all.optJSONObject(it)?.optBoolean("automatic", false) == true }
            var dropAuto = automatic.count { it }.minus(max).coerceAtLeast(0)
            var dropManual = automatic.count { !it }.minus(max).coerceAtLeast(0)
            val out = JSONArray()
            for (index in 0 until all.length()) {
                if (automatic[index]) {
                    if (dropAuto > 0) { dropAuto--; continue }
                } else if (dropManual > 0) { dropManual--; continue }
                out.put(all.get(index))
            }
            return out
        }
        private val MUTATION_MAY_HAVE_STARTED_STATES = setOf(
            "SENDING_ACTION",
            "READING_AFTER",
            "SENDING_FINISH_COMMIT",
            "VERIFYING_FINISH",
            "RESETTING_K",
            "VERIFYING_K_RESET",
        )
    }
}