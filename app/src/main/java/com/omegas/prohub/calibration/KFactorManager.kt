package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.storage.AppPaths
import com.omegas.prohub.usb.UsbProtocolReply
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Autoridade Android da curva azul K factor.
 *
 * Toda escrita é iniciada pelo usuário, possui backup completo, ACK e readback
 * dos 30 pontos. Sugestões do aprendizado nunca chamam este gerenciador.
 */
class KFactorManager(
    private val paths: AppPaths,
    private val serial: Mp48SerialScheduler,
    private val log: RingLog,
    private val onBusyChanged: (Boolean) -> Unit,
    private val onConfirmedBatch: (JSONObject) -> Unit = {},
) {
    companion object {
        const val MAX_BATCH_POINTS = KFactorProtocol.POINT_COUNT
        const val MIN_SAFE_FACTOR = 0.60
        const val MAX_SAFE_FACTOR = KFactorProtocol.MAX_FACTOR
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-k-factor-writer").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)
    private val cacheFile = File(paths.runtimeRoot, "k_factor_cache.json")
    private val historyFile = File(paths.runtimeRoot, "k_factor_history.json")
    private val backupDir = File(paths.runtimeRoot, "k_factor_backups").apply { mkdirs() }
    private val statusLock = Any()
    @Volatile private var status = JSONObject()
        .put("state", "IDLE")
        .put("busy", false)
        .put("message", "Aguardando ação manual")

    fun isBusy(): Boolean = busy.get()

    fun statusJson(): String = synchronized(statusLock) { JSONObject(status.toString()).toString() }

    fun historyJson(): String = loadHistory().toString()

    @Synchronized
    fun beginUsbSession(sessionId: Long) {
        val cache = loadCache()
            .put("sessionConfirmed", false)
            .put("sessionStartedAt", System.currentTimeMillis())
            .put("sessionId", sessionId)
        atomicWrite(cacheFile, cache.toString(2))
        update("CURVE_PENDING", "Leia a curva K factor nesta conexão", 0)
    }

    fun readCurve(): JSONObject {
        val expectedSessionId = try { currentSessionId() } catch (error: Exception) {
            return error(error.message ?: "USB desconectado")
        }
        if (!busy.compareAndSet(false, true)) return error("Outra operação de calibração está em andamento")
        onBusyChanged(true)
        update("READING_AXIS", "Lendo eixo Petrol Inj", 10)
        return try {
            val axisRaw = readRawPoints(KFactorProtocol.readPetrolAxis(), "eixo Petrol Inj", expectedSessionId)
            update("READING_FACTORS", "Lendo 30 pontos K factor", 55)
            val factorsRaw = readRawPoints(KFactorProtocol.readFactors(), "curva K factor", expectedSessionId)
            val now = System.currentTimeMillis()
            val curveHash = hash(factorsRaw)
            val cache = curveJson(axisRaw, factorsRaw)
                .put("schema", "omegas-k-factor-cache-v1")
                .put("updatedAt", now)
                .put("complete", true)
                .put("sessionConfirmed", true)
                .put("sessionId", expectedSessionId)
                .put("source", "ECU_FULL_READ")
                .put("hash", curveHash)
            atomicWrite(cacheFile, cache.toString(2))
            update("CURVE_SYNCED", "Curva K factor confirmada pela ECU", 100, cache)
            JSONObject(cache.toString()).put("ok", true).put("manualOnly", true)
        } catch (error: Exception) {
            update("FAILED", error.message ?: "Falha ao ler K factor", 100)
            error(error.message ?: "Falha ao ler K factor")
        } finally {
            busy.set(false)
            onBusyChanged(false)
            synchronized(statusLock) { status.put("busy", false) }
        }
    }

    fun startBatchWrite(points: JSONArray, reason: String = "Ajuste manual assistido"): JSONObject {
        if (points.length() !in 1..MAX_BATCH_POINTS) {
            return error("Selecione entre 1 e $MAX_BATCH_POINTS pontos")
        }
        val normalized = JSONArray()
        val seen = linkedSetOf<Int>()
        val minimumRaw = KFactorProtocol.rawFromFactor(MIN_SAFE_FACTOR)
        val maximumRaw = KFactorProtocol.MAX_RAW
        repeat(points.length()) { position ->
            val point = points.optJSONObject(position) ?: return error("Ponto ${position + 1} inválido")
            val index = point.optInt("index", -1)
            val currentRaw = point.optInt("currentRaw", -1)
            val targetRaw = point.optInt("targetRaw", -1)
            if (index !in 0 until KFactorProtocol.POINT_COUNT) return error("Índice inválido: $index")
            if (currentRaw !in 0..KFactorProtocol.MAX_RAW || targetRaw !in minimumRaw..maximumRaw) {
                return error("K factor alvo deve estar entre %.2f e %.4f".format(MIN_SAFE_FACTOR, MAX_SAFE_FACTOR))
            }
            if (currentRaw == targetRaw) return error("O ponto $index não possui alteração")
            if (!seen.add(index)) return error("Ponto $index repetido")
            normalized.put(JSONObject()
                .put("index", index)
                .put("currentRaw", currentRaw)
                .put("targetRaw", targetRaw)
                .put("currentFactor", KFactorProtocol.factorFromRaw(currentRaw))
                .put("targetFactor", KFactorProtocol.factorFromRaw(targetRaw)))
        }
        if (!busy.compareAndSet(false, true)) return error("Outra operação de calibração está em andamento")
        val expectedSessionId = try { currentSessionId() } catch (error: Exception) {
            busy.set(false)
            return error(error.message ?: "USB desconectado")
        }
        val adjustmentId = "KF-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        onBusyChanged(true)
        update(
            "BATCH_QUEUED",
            "Alteração manual preparada para conferência",
            0,
            JSONObject().put("adjustmentId", adjustmentId).put("points", normalized),
        )
        executor.execute { executeBatch(adjustmentId, normalized, reason, expectedSessionId) }
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("adjustmentId", adjustmentId)
            .put("points", normalized.length())
            .put("automatic", false)
            .put("humanConfirmationRequired", true)
    }

    fun close() = executor.shutdownNow()

    private fun executeBatch(adjustmentId: String, points: JSONArray, reason: String, expectedSessionId: Long) {
        val startedAt = System.currentTimeMillis()
        val confirmed = JSONArray()
        var initialHash = ""
        try {
            val cache = loadCache()
            val cachedAxis = jsonIntArray(cache.optJSONArray("axisRaw"))
            val cachedFactors = jsonIntArray(cache.optJSONArray("factorsRaw"))
            if (!cache.optBoolean("complete") || !cache.optBoolean("sessionConfirmed") ||
                cache.optLong("sessionId", -1L) != expectedSessionId ||
                cachedAxis.size != KFactorProtocol.POINT_COUNT ||
                cachedFactors.size != KFactorProtocol.POINT_COUNT
            ) {
                throw IllegalStateException("Leia a curva K factor nesta conexão antes de aplicar")
            }

            update("VERIFYING_CURRENT", "Conferindo os 30 pontos antes da escrita", 8)
            val ecuBefore = readRawPoints(KFactorProtocol.readFactors(), "conferência K factor", expectedSessionId)
            if (!ecuBefore.contentEquals(cachedFactors)) {
                throw IllegalStateException("A curva da ECU mudou. Leia novamente antes de aplicar")
            }
            val working = ecuBefore.copyOf()
            initialHash = hash(working)
            createBackup(adjustmentId, cachedAxis, working, points, initialHash)

            repeat(points.length()) { pointPosition ->
                val point = points.getJSONObject(pointPosition)
                val index = point.getInt("index")
                val currentRaw = point.getInt("currentRaw")
                val targetRaw = point.getInt("targetRaw")
                if (working[index] != currentRaw) {
                    throw IllegalStateException("Ponto $index: esperado $currentRaw, encontrado ${working[index]}")
                }
                val progress = 15 + ((pointPosition + 1) * 65 / points.length())
                update(
                    "WRITING_POINT",
                    "Ponto ${pointPosition + 1}/${points.length()} • ${formatFactor(currentRaw)} → ${formatFactor(targetRaw)}",
                    progress,
                    JSONObject().put("adjustmentId", adjustmentId).put("index", index),
                )
                val readback = serial.unit(
                    reason = "escrita + readback K factor[$index]",
                    expectedSessionId = expectedSessionId,
                    workClass = Mp48WorkClass.MANUAL_WRITE,
                    telemetryAfter = true,
                    waitTimeoutMs = 3_500L,
                ) { unit ->
                    requireAck(
                        unit.transaction(
                            KFactorProtocol.writeFactor(index, targetRaw),
                            "escrita K factor[$index]",
                            900,
                            purgeBefore = false,
                        ),
                        "escrita do ponto $index",
                    )
                    readRawPoints(unit, KFactorProtocol.readFactors(), "readback K factor[$index]")
                }
                repeat(KFactorProtocol.POINT_COUNT) { other ->
                    val expected = if (other == index) targetRaw else working[other]
                    if (readback[other] != expected) {
                        throw IllegalStateException(
                            "Readback divergente no ponto $other: esperado $expected, ECU ${readback[other]}",
                        )
                    }
                }
                working[index] = targetRaw
                val event = JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("adjustmentId", adjustmentId)
                    .put("timestamp", System.currentTimeMillis())
                    .put("index", index)
                    .put("petrolMs", KFactorProtocol.petrolMsFromAxisRaw(cachedAxis[index]))
                    .put("beforeRaw", currentRaw)
                    .put("afterRaw", targetRaw)
                    .put("beforeFactor", KFactorProtocol.factorFromRaw(currentRaw))
                    .put("afterFactor", KFactorProtocol.factorFromRaw(targetRaw))
                    .put("reason", reason.take(180))
                    .put("confirmed", true)
                    .put("automatic", false)
                appendHistory(event)
                confirmed.put(event)
            }

            update("VERIFYING_FINAL", "Confirmando a curva completa", 90)
            val finalReadback = readRawPoints(KFactorProtocol.readFactors(), "confirmação final K factor", expectedSessionId)
            if (!finalReadback.contentEquals(working)) {
                throw IllegalStateException("A confirmação final da curva divergiu")
            }
            val finalHash = hash(working)
            val now = System.currentTimeMillis()
            val finalCache = curveJson(cachedAxis, working)
                .put("schema", "omegas-k-factor-cache-v1")
                .put("updatedAt", now)
                .put("complete", true)
                .put("sessionConfirmed", true)
                .put("sessionId", expectedSessionId)
                .put("source", "ECU_BATCH_VERIFIED")
                .put("hash", finalHash)
            atomicWrite(cacheFile, finalCache.toString(2))
            val payload = JSONObject()
                .put("ok", true)
                .put("calibrationType", "K_FACTOR")
                .put("adjustmentId", adjustmentId)
                .put("oldHash", initialHash)
                .put("newHash", finalHash)
                .put("points", points)
                .put("confirmedEvents", confirmed)
                .put("curve", finalCache)
                .put("elapsedMs", System.currentTimeMillis() - startedAt)
                .put("automatic", false)
                .put("humanConfirmed", true)
                .put("readbackValid", true)
                .put("confirmedAt", now)
            try { onConfirmedBatch(payload) } catch (error: Exception) {
                log.add("WARN", "K-FACTOR", "Curva confirmada; notificação falhou: ${error.message}")
            }
            update("BATCH_CONFIRMED", "K factor confirmado por ACK e readback", 100, payload)
            log.add("INFO", "K-FACTOR", "$adjustmentId confirmado • ${points.length()} pontos")
        } catch (error: Exception) {
            val stale = loadCache().put("sessionConfirmed", false)
            atomicWrite(cacheFile, stale.toString(2))
            update(
                "BATCH_FAILED",
                error.message ?: "Alteração K factor interrompida",
                100,
                JSONObject()
                    .put("adjustmentId", adjustmentId)
                    .put("oldHash", initialHash)
                    .put("confirmedEvents", confirmed)
                    .put("partial", confirmed.length() > 0),
            )
            log.add("ERROR", "K-FACTOR", "$adjustmentId interrompido: ${error.message}")
        } finally {
            busy.set(false)
            onBusyChanged(false)
            synchronized(statusLock) { status.put("busy", false) }
        }
    }

    private fun readRawPoints(request: ByteArray, reason: String, expectedSessionId: Long): IntArray {
        val reply = transaction(request, reason, 900, expectedSessionId)
        return decodeRawPoints(reply, reason)
    }

    private fun readRawPoints(unit: Mp48SerialUnit, request: ByteArray, reason: String): IntArray =
        decodeRawPoints(
            unit.transaction(request, reason, 900, purgeBefore = false),
            reason,
        )

    private fun decodeRawPoints(reply: UsbProtocolReply, reason: String): IntArray {
        if (!reply.ok) throw IllegalStateException(reply.error.ifBlank { "ECU não confirmou $reason" })
        if (reply.status != Mp48Protocol.STATUS_ACK) {
            throw IllegalStateException("Resposta inesperada 0x%02X em $reason".format(reply.status))
        }
        return KFactorProtocol.decodeRawPoints(reply.payload)
    }

    private fun transaction(
        request: ByteArray,
        reason: String,
        timeoutMs: Int,
        expectedSessionId: Long,
        workClass: Mp48WorkClass = Mp48WorkClass.READ_ONLY,
        telemetryAfter: Boolean = true,
    ): UsbProtocolReply {
        if (!serial.isConnected()) throw IllegalStateException("USB desconectado")
        return serial.transaction(
            request = request,
            reason = reason,
            timeoutMs = timeoutMs,
            purgeBefore = false,
            expectedSessionId = expectedSessionId,
            workClass = workClass,
            telemetryAfter = telemetryAfter,
        )
    }

    private fun currentSessionId(): Long = serial.currentSessionId().takeIf { serial.isConnected() && it > 0L }
        ?: throw IllegalStateException("USB desconectado")

    private fun requireAck(reply: UsbProtocolReply, action: String) {
        if (!reply.ok || reply.status != Mp48Protocol.STATUS_ACK) {
            throw IllegalStateException(reply.error.ifBlank { "ACK inválido em $action" })
        }
    }

    private fun curveJson(axisRaw: IntArray, factorsRaw: IntArray): JSONObject {
        val points = JSONArray()
        repeat(KFactorProtocol.POINT_COUNT) { index ->
            points.put(JSONObject()
                .put("index", index)
                .put("petrolAxisRaw", axisRaw[index])
                .put("petrolMs", KFactorProtocol.petrolMsFromAxisRaw(axisRaw[index]))
                .put("factorRaw", factorsRaw[index])
                .put("factor", KFactorProtocol.factorFromRaw(factorsRaw[index])))
        }
        return JSONObject()
            .put("axisRaw", JSONArray(axisRaw.toList()))
            .put("factorsRaw", JSONArray(factorsRaw.toList()))
            .put("points", points)
            .put("pointCount", KFactorProtocol.POINT_COUNT)
            .put("factorEncoding", "Q14")
            .put("axisEncoding", "raw/512 ms")
            .put("minimumFactor", MIN_SAFE_FACTOR)
            .put("maximumFactor", MAX_SAFE_FACTOR)
            .put("automatic", false)
    }

    private fun createBackup(
        adjustmentId: String,
        axisRaw: IntArray,
        factorsRaw: IntArray,
        points: JSONArray,
        initialHash: String,
    ) {
        val backup = curveJson(axisRaw, factorsRaw)
            .put("format", "omegas-k-factor-backup-v1")
            .put("adjustmentId", adjustmentId)
            .put("createdAt", System.currentTimeMillis())
            .put("hash", initialHash)
            .put("changes", JSONArray(points.toString()))
        atomicWrite(File(backupDir, "$adjustmentId.json"), backup.toString(2))
        backupDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(30)?.forEach { it.delete() }
    }

    private fun loadCache(): JSONObject = try {
        if (cacheFile.isFile) JSONObject(cacheFile.readText(Charsets.UTF_8)) else JSONObject()
    } catch (_: Exception) { JSONObject() }

    private fun loadHistory(): JSONArray = try {
        if (historyFile.isFile) JSONArray(historyFile.readText(Charsets.UTF_8)) else JSONArray()
    } catch (_: Exception) { JSONArray() }

    private fun appendHistory(event: JSONObject) {
        val history = loadHistory().put(event)
        val trimmed = JSONArray()
        val start = (history.length() - 2_000).coerceAtLeast(0)
        for (index in start until history.length()) trimmed.put(history.get(index))
        atomicWrite(historyFile, trimmed.toString(2))
    }

    private fun jsonIntArray(source: JSONArray?): IntArray {
        if (source == null) return IntArray(0)
        return IntArray(source.length()) { source.optInt(it, -1) }
    }

    private fun hash(values: IntArray): String {
        val canonical = values.joinToString(",")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun formatFactor(raw: Int): String = "%.3f".format(KFactorProtocol.factorFromRaw(raw))

    private fun error(message: String): JSONObject = JSONObject().put("ok", false).put("error", message)

    private fun update(state: String, message: String, progress: Int, details: JSONObject = JSONObject()) {
        synchronized(statusLock) {
            status = JSONObject()
                .put("state", state)
                .put("busy", busy.get())
                .put("message", message)
                .put("progress", progress.coerceIn(0, 100))
                .put("updatedAt", System.currentTimeMillis())
                .put("details", details)
                .put("automatic", false)
        }
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
            Files.move(
                temp.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
