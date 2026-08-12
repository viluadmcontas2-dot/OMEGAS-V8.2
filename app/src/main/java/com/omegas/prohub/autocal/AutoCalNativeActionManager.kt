package com.omegas.prohub.autocal

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
        val mode: Int,
        val label: String,
        val description: String,
        val mayChangeMulAct: Boolean,
    ) {
        RESET_PETROL(0x01, "Resetar aquisição gasolina", "Apaga somente os dados AutoCal de gasolina.", false),
        RESET_GAS(0x02, "Resetar aquisição GNV", "Apaga somente os dados AutoCal de GNV.", false),
        RESET_ALL(0x04, "Resetar aquisições AutoCal", "Apaga as aquisições AutoCal de gasolina e GNV.", false),
        NATIVE_AUTOMATCH(0x08, "Executar AutoMatch nativo", "Executa o modo 8 original dentro da ECU.", true);

        val request: ByteArray get() = Mp48Protocol.frame(byteArrayOf(0x02, 0x24, 0x04, mode.toByte()))
    }

    private data class Preparation(
        val id: String,
        val action: Action,
        val sessionId: Long,
        val createdAtMs: Long,
        val expiresAtMs: Long,
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
        require(!busy.get()) { "Outra ação AutoCal está em andamento" }
        require(isConnected()) { "USB desconectado" }
        require(!otherCalibrationBusy()) { "Outra operação de calibração está em andamento" }
        unsafeMutationReason()?.let { throw IllegalStateException(it) }
        val sessionId = currentSessionId()
        require(sessionId > 0L) { "Sessão USB inválida" }
        val now = System.currentTimeMillis()
        val prepared = Preparation(
            id = "ACA-$now-${UUID.randomUUID().toString().take(8)}",
            action = action,
            sessionId = sessionId,
            createdAtMs = now,
            expiresAtMs = now + PREPARATION_TTL_MS,
        )
        synchronized(lock) {
            preparation = prepared
            status = baseStatus("PREPARED", action.label, 0)
                .put("preparationId", prepared.id)
                .put("action", action.name)
                .put("sessionId", sessionId)
                .put("expiresAtMs", prepared.expiresAtMs)
        }
        onStateChanged()
        JSONObject()
            .put("ok", true)
            .put("prepared", true)
            .put("preparationId", prepared.id)
            .put("action", action.name)
            .put("label", action.label)
            .put("description", action.description)
            .put("commandHex", action.request.hex())
            .put("sessionId", sessionId)
            .put("expiresAtMs", prepared.expiresAtMs)
            .put("ecuMutation", true)
            .put("mayChangeMulAct", action.mayChangeMulAct)
            .put("requiresCriticalConfirmation", true)
            .put("automatic", false)
            .put("manualOnly", true)
    } catch (error: Exception) {
        failure(error.message ?: "Ação AutoCal inválida")
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
            unsafeMutationReason()?.let {
                preparation = null
                return failure(it)
            }
            if (!busy.compareAndSet(false, true)) return failure("Outra ação AutoCal está em andamento")
            preparation = null
            current
        }
        update("QUEUED", "Ação confirmada; iniciando recibo antes/depois", 0, prepared)
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
            ensureSession(prepared)
            update("READING_BEFORE", "Lendo snapshot anterior", 8, prepared)
            val before = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ)
            ensureSession(prepared)

            update("SENDING_ACTION", prepared.action.label, 48, prepared)
            val reply = transaction(
                prepared.action.request,
                "AutoCal ${prepared.action.name}",
                1_500,
                prepared.sessionId,
            )
            require(reply.ok && reply.status == Mp48Protocol.STATUS_ACK) {
                reply.error.ifBlank { "A ECU não confirmou ${prepared.action.label}" }
            }

            Thread.sleep(250L)
            ensureSession(prepared)
            update("READING_AFTER", "Lendo snapshot posterior", 70, prepared)
            val after = readSnapshot(prepared, AutoCalSnapshotSource.ECU_READ)
            val receipt = receipt(prepared, reply, before, after, startedAt)
            appendReceipt(receipt)
            try { onConfirmed(receipt) } catch (_: Exception) {}
            update("CONFIRMED", "Ação confirmada por ACK e recibo antes/depois", 100, prepared, receipt)
        } catch (error: Exception) {
            update("FAILED", error.message ?: "Ação AutoCal interrompida", 100, prepared)
        } finally {
            busy.set(false)
            synchronized(lock) { status.put("busy", false) }
            onStateChanged()
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
        before: AutoCalSnapshot,
        after: AutoCalSnapshot,
        startedAt: Long,
    ): JSONObject {
        val changed = JSONArray()
        fieldsForReceipt.distinctBy { it.identity }.sortedWith(compareBy<AutoCalProtocol.Field> { it.address }.thenBy { it.index ?: -1 }).forEach { field ->
            val old = before.field(field)
            val fresh = after.field(field)
            if (old?.status != fresh?.status || old?.rawPayloadHex != fresh?.rawPayloadHex) {
                changed.put(JSONObject()
                    .put("key", field.key)
                    .put("address", field.address)
                    .put("beforeStatus", old?.status?.name ?: JSONObject.NULL)
                    .put("afterStatus", fresh?.status?.name ?: JSONObject.NULL)
                    .put("beforeRaw", old?.rawPayloadHex ?: "")
                    .put("afterRaw", fresh?.rawPayloadHex ?: ""))
            }
        }
        return JSONObject()
            .put("id", "RECEIPT-${UUID.randomUUID()}")
            .put("preparationId", prepared.id)
            .put("action", prepared.action.name)
            .put("label", prepared.action.label)
            .put("commandHex", prepared.action.request.hex())
            .put("ackStatus", reply.status)
            .put("sessionId", prepared.sessionId)
            .put("startedAtMs", startedAt)
            .put("finishedAtMs", System.currentTimeMillis())
            .put("beforeHash", before.snapshotHash)
            .put("afterHash", after.snapshotHash)
            .put("beforePartial", before.partial)
            .put("afterPartial", after.partial)
            .put("changedFields", changed)
            .put("before", before.toJson())
            .put("after", after.toJson())
            .put("ecuMutation", true)
            .put("mayChangeMulAct", prepared.action.mayChangeMulAct)
            .put("humanConfirmed", true)
            .put("automatic", false)
            .put("manualOnly", true)
            .put("automaticRollback", false)
    }

    private fun ensureSession(prepared: Preparation) {
        require(isConnected()) { "USB desconectado durante a ação AutoCal" }
        require(currentSessionId() == prepared.sessionId) { "Sessão USB mudou durante a ação AutoCal" }
        require(!otherCalibrationBusy()) { "Outra calibração assumiu a sessão" }
        unsafeMutationReason()?.let { throw IllegalStateException(it) }
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