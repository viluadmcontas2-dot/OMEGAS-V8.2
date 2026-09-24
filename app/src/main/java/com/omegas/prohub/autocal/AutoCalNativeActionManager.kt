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
            "Permite que a própria ECU continue a aquisição por zonas e execute AutoMatch quando seus critérios forem atendidos.",
            true,
            1,
            true,
        ),
        DISABLE_AUTO_CAL(
            AutoCalProtocol.setEnabled(false),
            "Pausar Auto Calibration",
            "Pausa a aquisição nativa sem apagar os buffers já coletados.",
            false,
            0,
            true,
        ),
        RESET_PETROL(
            Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, 0x01)),
            "Readquirir gasolina",
            "Usa a ação nativa dedicada Reset petrol point do ProgBase 4.2.0.6 (modo 0x01). A Curva K usa outro caminho. Após o ACK, o OMEGAS relê a ECU para atualizar o estado. Nenhum backup automático é exigido.",
            true,
        ),
        RESET_GAS(
            Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, 0x02)),
            "Readquirir GNV",
            "Usa a ação nativa dedicada Reset gas point do ProgBase 4.2.0.6 (modo 0x02). A Curva K usa outro caminho. Após o ACK, o OMEGAS relê a ECU para atualizar o estado. Nenhum backup automático é exigido.",
            true,
        ),
        RESET_ALL(
            Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, 0x04)),
            "Nova aquisição completa",
            "Usa a ação nativa Reset all do ProgBase 4.2.0.6 (modo 0x04). É uma redefinição ampla e permanece separada da readquisição de um único combustível.",
            true,
        ),
        DELETE_POINT(
            byteArrayOf(),
            "Readquirir este ponto",
            "Replica ChartDataClickSeries + ActionDeleteSelectedPointsExecute do ProgBase para um único ponto adquirido.",
            false,
        );
    }

    // ProgBase 4.2.0.6 canônico, raw RTTI + wrappers (Atlas):
    // 0x08 = Manual AutoMatch; 0x01 = Reset petrol; 0x02 = Reset gas; 0x04 = Reset all.
    // Modify Map Refs e Reset K Factor são caminhos separados no código original.
    // OMEGAS preserva confirmação humana, ACK e readback. Backup é uma ação manual separada.
    private data class Preparation(
        val id: String,
        val action: Action,
        val sessionId: Long,
        val createdAtMs: Long,
        val expiresAtMs: Long,
        val pointDeleteTarget: AutoCalPointDeleteProtocol.Target? = null,
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
        prepareInternal(action, null)
    } catch (error: Exception) {
        failure(error.message ?: "Ação AutoCal inválida")
    }

    fun preparePointDelete(fuelName: String, index: Int): JSONObject = try {
        val target = AutoCalPointDeleteProtocol.Target(
            fuel = AutoCalPointDeleteProtocol.Fuel.parse(fuelName),
            index = index,
        )
        prepareInternal(Action.DELETE_POINT, target)
    } catch (error: Exception) {
        failure(error.message ?: "Ponto AutoCal inválido")
    }

    private fun prepareInternal(
        action: Action,
        pointDeleteTarget: AutoCalPointDeleteProtocol.Target?,
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
            pointDeleteTarget = pointDeleteTarget,
        )
        val label = pointDeleteTarget?.let { "Readquirir ${it.toLabel()}" } ?: action.label
        val description = pointDeleteTarget?.let {
            "Apaga somente este ponto adquirido usando os masks nativos do ProgBase; os outros pontos ficam marcados para preservar."
        } ?: action.description
        val details = pointDeleteTarget?.let(::pointTargetJson) ?: JSONObject()
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
            .put("commandHex", if (pointDeleteTarget == null) action.request.hex() else "MASK U8[18] GNV + gasolina → 01 24 05 2A")
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
        try {
            if (prepared.action == Action.DELETE_POINT) {
                executePointDelete(prepared, startedAt)
            } else {
                executeFixedAction(prepared, startedAt)
            }
        } catch (error: Exception) {
            update("FAILED", error.message ?: "Ação AutoCal interrompida", 100, prepared)
        } finally {
            busy.set(false)
            synchronized(lock) { status.put("busy", false) }
            onStateChanged()
        }
    }

    private fun executeFixedAction(prepared: Preparation, startedAt: Long) {
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
        confirm(prepared, reply, after, startedAt)
    }

    private fun executePointDelete(prepared: Preparation, startedAt: Long) {
        val target = requireNotNull(prepared.pointDeleteTarget) { "Ponto para readquirir não foi preparado" }
        val plan = AutoCalPointDeleteProtocol.singlePointPlan(target)
        val maskFrames = plan.dropLast(1)
        update("SENDING_ACTION", "Readquirindo ${target.toLabel()}", 8, prepared, pointTargetJson(target))
        maskFrames.forEachIndexed { step, request ->
            ensureSession(prepared)
            val reply = transaction(
                request,
                "AutoCal point mask ${step + 1}/${maskFrames.size}",
                1_200,
                prepared.sessionId,
            )
            requireAck(reply, "A ECU não confirmou o mask do ponto ${step + 1}/${maskFrames.size}")
            update(
                "SENDING_ACTION",
                "Preparando ponto na ECU",
                8 + ((step + 1) * 54 / maskFrames.size),
                prepared,
                pointTargetJson(target),
            )
        }
        ensureSession(prepared)
        val commitReply = transaction(
            plan.last(),
            "AutoCal point delete commit",
            1_500,
            prepared.sessionId,
        )
        requireAck(commitReply, "A ECU não confirmou o commit da readquisição do ponto")
        Thread.sleep(500L)
        ensureSession(prepared)
        update("READING_AFTER", "Atualizando ponto após o commit", 78, prepared, pointTargetJson(target))
        val after = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ)
        confirm(prepared, commitReply, after, startedAt)
    }

    private fun confirm(
        prepared: Preparation,
        reply: UsbProtocolReply,
        after: AutoCalSnapshot,
        startedAt: Long,
    ) {
        val receipt = receipt(prepared, reply, after, startedAt)
        appendReceipt(receipt)
        try { onConfirmed(receipt) } catch (_: Exception) {}
        update(
            "CONFIRMED",
            if (prepared.action == Action.DELETE_POINT) {
                "Ponto liberado para nova aquisição; estado da ECU atualizado"
            } else {
                "ACK confirmado; estado da ECU atualizado"
            },
            100,
            prepared,
            receipt,
        )
    }

    private fun requireAck(reply: UsbProtocolReply, fallback: String) {
        require(reply.ok && reply.status == Mp48Protocol.STATUS_ACK) {
            reply.error.ifBlank { fallback }
        }
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
    ): JSONObject = JSONObject()
        .put("id", "RECEIPT-${UUID.randomUUID()}")
        .put("preparationId", prepared.id)
        .put("action", prepared.action.name)
        .put("label", prepared.action.label)
        .put(
            "commandHex",
            if (prepared.pointDeleteTarget == null) prepared.action.request.hex()
            else "MASK U8[18] GNV + gasolina → 01 24 05 2A",
        )
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
        .put("pointDelete", prepared.pointDeleteTarget?.let(::pointTargetJson) ?: JSONObject.NULL)

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

    private fun failure(message: String): JSONObject = JSONObject()
        .put("ok", false)
        .put("error", message)
        .put("automatic", false)
        .put("manualOnly", true)

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    companion object {
        private const val PREPARATION_TTL_MS = 120_000L
        private const val MAX_RECEIPTS = 200

    }
}