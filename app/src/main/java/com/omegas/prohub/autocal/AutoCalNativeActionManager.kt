package com.omegas.prohub.autocal

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
 * Executa somente ações AutoCal nativas escolhidas e confirmadas pelo operador.
 * Não possui agenda, gatilho automático ou ligação com sugestões.
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
) {
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
        MANUAL_AUTOMATCH(
            AutoCalProtocol.manualAction(AutoCalProtocol.ManualActionMode.MANUAL_AUTOMATCH),
            "AutoMatch manual",
            "Replica ActionAutoMatchExecute do ProgBase (modo 0x08). É uma ação explícita do operador e permanece separada do AutoMatch nativo observado automaticamente na ECU.",
            true,
        ),
        FINISH_AUTOCAL(
            byteArrayOf(),
            "Finalizar AutoCal",
            "Replica ActionFinishAutocalExecute: confirma MAX_AUTOMATCH em NUM_AUTOMATCH_EXECUTED, aguarda 100 ms e exige readback antes de concluir.",
            false,
        ),
        FINISH_AUTOMATCH(
            byteArrayOf(),
            "Finalizar AutoMatch",
            "Replica BtnFinishAutomatchClick: confirma MAX_AUTOMATCH em NUM_AUTOMATCH_EXECUTED sem o settle final do AutoCal completo.",
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
            "Usa a ação nativa Reset all do ProgBase 4.2.0.6 (modo 0x04). É uma redefinição ampla e permanece separada da readquisição de um único combustível.",
            false,
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
    )

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-autocal-native-action").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)
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
        prepareInternal(Action.DELETE_POINT, listOf(target))
    } catch (error: Exception) {
        failure(error.message ?: "Ponto AutoCal inválido")
    }

    fun preparePointDeletes(targets: Collection<AutoCalPointDeleteProtocol.Target>): JSONObject = try {
        val normalized = targets.distinctBy { it.fuel to it.index }
        require(normalized.isNotEmpty()) { "Selecione ao menos um ponto AutoCal" }
        prepareInternal(Action.DELETE_POINT, normalized)
    } catch (error: Exception) {
        failure(error.message ?: "Seleção AutoCal inválida")
    }

    private fun prepareInternal(
        action: Action,
        pointDeleteTargets: List<AutoCalPointDeleteProtocol.Target>,
    ): JSONObject {
        require(!busy.get()) { "Outra ação AutoCal está em andamento" }
        require(isConnected()) { "USB desconectado" }
        require(!otherCalibrationBusy()) { "Outra operação de calibração está em andamento" }
        if (!action.operationalToggle) {
            unsafeMutationReason()?.let { throw IllegalStateException(it) }
        }
        val sessionId = currentSessionId()
        require(sessionId > 0L) { "Sessão USB inválida" }
        val now = System.currentTimeMillis()
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
            if (System.currentTimeMillis() > current.expiresAtMs) {
                preparation = null
                return failure("A preparação expirou; revise a ação novamente")
            }
            if (!isConnected() || currentSessionId() != current.sessionId) {
                preparation = null
                return failure("A sessão USB mudou; prepare a ação novamente")
            }
            if (otherCalibrationBusy()) return failure("Outra operação de calibração está em andamento")
            if (!current.action.operationalToggle) {
                unsafeMutationReason()?.let {
                    preparation = null
                    return failure(it)
                }
            }
            if (!busy.compareAndSet(false, true)) return failure("Outra ação AutoCal está em andamento")
            preparation = null
            current
        }
        update("QUEUED", "Ação confirmada; enviando para a ECU", 0, prepared)
        executor.execute { executePrepared(prepared) }
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
        executor.shutdownNow()
    }

    private fun executePrepared(prepared: Preparation) {
        val startedAt = System.currentTimeMillis()
        var before: AutoCalSnapshot? = null
        try {
            before = if (prepared.action.mayChangeMulAct) {
                update("READING_BEFORE", "Capturando Curva K antes da ação", 4, prepared)
                readMulActSnapshot(prepared)
            } else null
            when (prepared.action) {
                Action.DELETE_POINT -> executePointDelete(prepared, startedAt)
                Action.FINISH_AUTOCAL, Action.FINISH_AUTOMATCH -> executeFinish(prepared, startedAt)
                Action.RESET_K_FACTOR -> executeResetKFactor(prepared, startedAt, before)
                else -> executeFixedAction(prepared, startedAt, before)
            }
        } catch (error: Exception) {
            val message = error.message ?: "Ação AutoCal interrompida"
            val recovery = AutoCalRecoveryPolicy.classify(message)
            val failedFromState = synchronized(lock) { status.optString("state", "UNKNOWN") }
            val mutationMayHaveStarted = failedFromState in MUTATION_MAY_HAVE_STARTED_STATES
            val failureReceipt = failureReceipt(
                prepared = prepared,
                startedAt = startedAt,
                failedFromState = failedFromState,
                message = message,
                recovery = recovery,
                mutationMayHaveStarted = mutationMayHaveStarted,
                before = before,
            )
            appendReceipt(failureReceipt)
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
        Thread.sleep(250L)
        ensureSession(prepared)
        update("READING_AFTER", "Atualizando estado da ECU", 72, prepared)
        val after = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ)
        validateActionReadback(prepared.action, after)
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
        val after = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ)
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
        val after = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ)
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

    private fun executePointDelete(prepared: Preparation, startedAt: Long) {
        val targets = prepared.pointDeleteTargets
        require(targets.isNotEmpty()) { "Pontos para readquirir não foram preparados" }
        val plan = AutoCalPointDeleteProtocol.multiPointPlan(targets)
        val maskFrames = plan.dropLast(1)
        val targetDetails = pointTargetsJson(targets)
        val actionLabel = if (targets.size == 1) targets.single().toLabel() else "${targets.size} pontos selecionados"
        update("SENDING_ACTION", "Readquirindo $actionLabel", 8, prepared, targetDetails)
        maskFrames.forEachIndexed { step, request ->
            ensureSession(prepared)
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
                prepared,
                targetDetails,
            )
        }
        ensureSession(prepared)
        val commitReply = transaction(
            plan.last(),
            "AutoCal point delete commit",
            1_500,
            prepared.sessionId,
        )
        requireAck(commitReply, "A ECU não confirmou o commit da readquisição")
        Thread.sleep(500L)
        ensureSession(prepared)
        update("READING_AFTER", "Atualizando aquisição após o commit", 78, prepared, targetDetails)
        val after = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ)
        confirm(prepared, commitReply, after, startedAt, targetDetails)
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
            prepared.action == Action.DELETE_POINT -> {
                val count = prepared.pointDeleteTargets.size
                if (count <= 1) "Ponto liberado para nova aquisição; estado da ECU atualizado"
                else "$count pontos liberados para nova aquisição; estado da ECU atualizado"
            }
            prepared.action == Action.FINISH_AUTOCAL || prepared.action == Action.FINISH_AUTOMATCH -> {
                val max = details.optInt("maxAutomatch", -1)
                val committed = details.optInt("committedValue", -1)
                if (max >= 0 && committed >= 0) {
                    "AutoCal finalizado · $committed/$max confirmado pela ECU"
                } else {
                    "AutoCal finalizado e confirmado pela ECU"
                }
            }
            else -> "ACK confirmado; estado da ECU atualizado"
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

    private fun readSnapshot(prepared: Preparation, source: AutoCalSnapshotSource): AutoCalSnapshot {
        val started = System.currentTimeMillis()
        val observations = fieldsForReceipt.distinctBy { it.identity }.map { field ->
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
            expectedFields = fieldsForReceipt,
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
            .put("humanConfirmed", true)
            .put("automatic", false)
            .put("manualOnly", true)
            .put("readbackValid", true)
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
            .put("humanConfirmed", true)
            .put("automatic", false)
            .put("manualOnly", true)
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

    private fun validateActionReadback(action: Action, after: AutoCalSnapshot) {
        val expected = action.expectedEnableReadback ?: return
        val actual = after.field(AutoCalProtocol.AUTO_CAL_ENABLE)
            ?.takeIf { it.status == AutoCalFieldStatus.VALID }
            ?.rawValues
            ?.singleOrNull()
        require(actual == expected) {
            "Readback AUTO_CAL_ENABLE divergente: esperado $expected, ECU ${actual ?: "sem dado"}"
        }
    }

    private fun ensureSession(prepared: Preparation) {
        require(isConnected()) { "USB desconectado durante a ação AutoCal" }
        require(currentSessionId() == prepared.sessionId) { "Sessão USB mudou durante a ação AutoCal" }
        require(!otherCalibrationBusy()) { "Outra calibração assumiu a sessão" }
        if (!prepared.action.operationalToggle) {
            unsafeMutationReason()?.let { throw IllegalStateException(it) }
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
        val current = loadReceipts().put(receipt)
        val trimmed = JSONArray()
        val start = (current.length() - MAX_RECEIPTS).coerceAtLeast(0)
        for (index in start until current.length()) trimmed.put(current.get(index))
        atomicWrite(receiptFile, trimmed.toString(2))
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

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    companion object {
        private const val PREPARATION_TTL_MS = 120_000L
        private const val MAX_RECEIPTS = 200
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