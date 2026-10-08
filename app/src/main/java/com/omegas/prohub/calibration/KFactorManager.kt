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
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Autoridade Android da curva azul K factor.
 *
 * Toda escrita é iniciada pelo usuário e confirmada por ACK + readback.
 * Backup da Curva K é uma ação manual separada; não é pré-requisito para escrever.
 * Sugestões do aprendizado nunca chamam este gerenciador.
 */
class KFactorManager(
    private val paths: AppPaths,
    private val serial: Mp48SerialScheduler,
    private val log: RingLog,
    private val onBusyChanged: (Boolean) -> Unit,
    private val onConfirmedBatch: (JSONObject) -> Unit = {},
    /** Falha com a ECU possivelmente alterada (escrita começou): só observação, nunca comando. */
    private val onFailedBatch: (JSONObject) -> Unit = {},
    private val publishManualBackup: (File) -> JSONObject = {
        JSONObject().put("ok", true).put("published", false)
    },
    /** Trava única da serial: escrita K e ação AutoCal nunca rodam juntas. */
    private val guard: SerialWriteGuard = SerialWriteGuard.shared,
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

    /**
     * Réplica funcional do ActionResetKFactorExecute do ProgBase 4.2.0.6.
     *
     * A prova canônica em 0x0051A070 itera o vetor MUL_ACT e chama
     * TAebVector.SetDouble com 1.0 em cada elemento. O OMEGAS preserva o
     * mesmo alvo (MUL_ACT 0x0161, Q14 0x4000) e acrescenta ACK + readback.
     * VECT_AUTOCAL_EE é uma superfície distinta e não é o alvo deste reset.
     */
    fun startResetToNeutral(reason: String = "Neutralizar MUL_ACT live em 1.0 · OMEGAS"): JSONObject {
        val expectedSessionId = try { currentSessionId() } catch (error: Exception) {
            return error(error.message ?: "USB desconectado", FailureKind.TRANSPORT)
        }
        if (!busy.compareAndSet(false, true)) return error("Outra operação de calibração está em andamento")
        // Tudo entre adquirir `busy` e entregar ao executor fica sob try/finally: nada vaza `busy`/trava.
        var submitted = false
        val resetId = "KRESET-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().take(8)
        try {
            if (!guard.tryAcquire(SerialWriteGuard.OWNER_K_FACTOR)) {
                return error("Aguarde: ${SerialWriteGuard.label(guard.holder())} em andamento")
            }
            onBusyChanged(true)
            update("RESET_READING", "Lendo Curva K antes do reset", 0, JSONObject().put("resetId", resetId))
            executor.execute {
                // O reset segue para o escritor canônico NA MESMA execução, sem soltar `busy` nem a
                // trava da serial entre a leitura e a escrita (executeBatch libera ambos no fim).
                var delegated = false
                try {
                    val axisRaw = readRawPoints(KFactorProtocol.readPetrolAxis(), "eixo Petrol Inj para reset K", expectedSessionId)
                    val factorsRaw = readRawPoints(KFactorProtocol.readFactors(), "Curva K antes do reset", expectedSessionId)
                    val now = System.currentTimeMillis()
                    val cache = curveJson(axisRaw, factorsRaw)
                        .put("schema", "omegas-k-factor-cache-v1")
                        .put("updatedAt", now)
                        .put("complete", true)
                        .put("sessionConfirmed", true)
                        .put("sessionId", expectedSessionId)
                        .put("source", "ECU_RESET_PRECHECK")
                        .put("hash", hash(factorsRaw))
                    atomicWrite(cacheFile, cache.toString(2))

                    val targetRaw = KFactorProtocol.rawFromFactor(1.0)
                    val points = JSONArray()
                    repeat(KFactorProtocol.POINT_COUNT) { index ->
                        val currentRaw = factorsRaw[index]
                        if (currentRaw != targetRaw) {
                            points.put(JSONObject()
                                .put("index", index)
                                .put("currentRaw", currentRaw)
                                .put("targetRaw", targetRaw))
                        }
                    }

                    if (points.length() == 0) {
                        update(
                            "RESET_CONFIRMED",
                            "Curva K já está neutra em 1.0",
                            100,
                            JSONObject().put("resetId", resetId).put("changedPoints", 0),
                        )
                    } else {
                        val normalized = normalizePoints(points, allowBelowFloor = false)
                        delegated = true
                        executeBatch(resetId, normalized, reason, expectedSessionId)
                    }
                } catch (error: Throwable) {
                    update(
                        "RESET_FAILED",
                        error.message ?: "Reset da Curva K interrompido",
                        100,
                        JSONObject().put("resetId", resetId).put("failureKind", FailureKind.of(error)),
                    )
                } finally {
                    if (!delegated) releaseWriter()
                }
            }
            submitted = true
        } catch (_: RejectedExecutionException) {
            return error("O escritor da Curva K foi encerrado; reabra o aplicativo")
        } finally {
            if (!submitted) releaseWriter()
        }
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("action", "RESET_K_FACTOR")
            .put("resetId", resetId)
            .put("targetFactor", 1.0)
            .put("targetRaw", KFactorProtocol.rawFromFactor(1.0))
            .put("automatic", false)
            .put("humanConfirmed", true)
    }

    /** Solta `busy` e a trava da serial exatamente uma vez por operação de escrita. */
    private fun releaseWriter() {
        busy.set(false)
        guard.release(SerialWriteGuard.OWNER_K_FACTOR)
        try { onBusyChanged(false) } catch (_: Throwable) {}
        synchronized(statusLock) { status.put("busy", false) }
    }

    @Synchronized
    fun beginUsbSession(sessionId: Long) {
        try {
            val cache = loadCache()
                .put("sessionConfirmed", false)
                .put("sessionStartedAt", System.currentTimeMillis())
                .put("sessionId", sessionId)
            atomicWrite(cacheFile, cache.toString(2))
        } catch (error: Exception) {
            // Disco cheio/somente leitura não pode derrubar a transição USB; a escrita exige leitura nova.
            log.add("WARN", "K-FACTOR", "Cache da Curva K não atualizado ao conectar: ${error.message}")
        }
        update("CURVE_PENDING", "Leia a curva K factor nesta conexão", 0)
    }

    fun readCurve(): JSONObject {
        val expectedSessionId = try { currentSessionId() } catch (error: Exception) {
            return error(error.message ?: "USB desconectado", FailureKind.TRANSPORT)
        }
        if (!busy.compareAndSet(false, true)) return error("Outra operação de calibração está em andamento")
        return try {
            // Escritor K ou ação AutoCal (ex.: Reset K, 30 transações) detém a serial: a curva da ECU pode estar
            // pela metade agora. Nada de registrar curva parcial como "confirmada nesta sessão".
            guard.holder()?.let { holder ->
                return error("Aguarde: ${SerialWriteGuard.label(holder)} em andamento")
            }
            onBusyChanged(true)
            update("READING_AXIS", "Lendo eixo Petrol Inj", 10)
            val axisRaw = readRawPoints(KFactorProtocol.readPetrolAxis(), "eixo Petrol Inj", expectedSessionId)
            update("READING_FACTORS", "Lendo 30 pontos K factor", 55)
            val factorsRaw = readRawPoints(KFactorProtocol.readFactors(), "curva K factor", expectedSessionId)
            // A leitura só vale se nenhuma escrita começou enquanto líamos.
            guard.holder()?.let { holder ->
                throw IllegalStateException("Leitura descartada: ${SerialWriteGuard.label(holder)} começou durante a leitura")
            }
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
        } catch (failure: Exception) {
            val kind = FailureKind.of(failure)
            update("FAILED", failure.message ?: "Falha ao ler K factor", 100, JSONObject().put("failureKind", kind))
            error(failure.message ?: "Falha ao ler K factor", kind)
        } finally {
            busy.set(false)
            try { onBusyChanged(false) } catch (_: Throwable) {}
            synchronized(statusLock) { status.put("busy", false) }
        }
    }

    fun saveCurrentBackup(label: String = "Curva salva manualmente"): JSONObject {
        val curve = readCurve()
        if (!curve.optBoolean("ok", false)) return curve
        return try {
            val axisRaw = jsonIntArray(curve.optJSONArray("axisRaw"))
            val factorsRaw = jsonIntArray(curve.optJSONArray("factorsRaw"))
            require(axisRaw.size == KFactorProtocol.POINT_COUNT && factorsRaw.size == KFactorProtocol.POINT_COUNT) {
                "Curva K incompleta; backup não salvo"
            }
            val photo = writeManualPhoto(axisRaw, factorsRaw, label)
            val fileName = photo.fileName
            val curveHash = photo.hash
            val createdAt = photo.createdAt
            val backupFile = File(backupDir, fileName)
            val publicName = KFactorCurveFileName.of(createdAt, KFactorProtocol.POINT_COUNT)
            val publicCopy = try {
                // Cópia com o nome didático numa pasta privada; é ela que vai para Download/Omegas/Curva.
                val staging = File(paths.runtimeRoot, "k_factor_publish").apply { mkdirs() }
                val staged = File(staging, publicName)
                backupFile.copyTo(staged, overwrite = true)
                try { publishManualBackup(staged) } finally { staged.delete() }
            } catch (error: Exception) {
                JSONObject().put("ok", false).put("error", error.message ?: "Falha ao publicar backup")
            }
            if (!publicCopy.optBoolean("ok", false)) {
                return error("Curva validada, mas não foi possível salvar em Download/Omegas/Curva")
                    .put("internalSaved", true)
                    .put("fileName", fileName)
                    .put("hash", curveHash)
                    .put("publicError", publicCopy.optString("error", "Armazenamento público indisponível"))
            }
            pruneBackups()
            JSONObject()
                .put("ok", true)
                .put("fileName", fileName)
                .put("publicPath", publicCopy.optString("path", "Download/Omegas/${KFactorCurveFileName.PUBLIC_SUBFOLDER}/$publicName"))
                .put("publicName", publicName)
                .put("publicCopy", publicCopy)
                .put("createdAt", createdAt)
                .put("hash", curveHash)
                .put("type", "MANUAL_SNAPSHOT")
                .put("label", photo.label)
                .put("pointCount", KFactorProtocol.POINT_COUNT)
                .put("curve", curve)
        } catch (error: Exception) {
            error(error.message ?: "Falha ao salvar backup da Curva K")
        }
    }

    private class ManualPhoto(val fileName: String, val hash: String, val createdAt: Long, val label: String)

    /**
     * Foto MANUAL- da curva: só disco (a curva já foi lida), validada por hash antes de devolver.
     * Usada pelo botão "Salvar curva" e, antes do primeiro ACK, por toda escrita em lote.
     */
    private fun writeManualPhoto(
        axisRaw: IntArray,
        factorsRaw: IntArray,
        label: String,
        preWrite: Boolean = false,
    ): ManualPhoto {
        val createdAt = System.currentTimeMillis()
        val curveHash = hash(factorsRaw)
        // "Salvar curva" (dono) = MANUAL-, nunca apagada. A foto automática de antes de cada escrita
        // = PREWRITE-, entra na rotação (as 30 mais novas ficam; a mais nova é a do último Desfazer).
        val namePrefix = if (preWrite) "PREWRITE" else "MANUAL"
        val fileName = "$namePrefix-$createdAt-${curveHash.take(8)}.json"
        val cleanLabel = label.trim().take(80).ifBlank { "Curva salva manualmente" }
        val backup = curveJson(axisRaw, factorsRaw)
            .put("format", "omegas-k-factor-backup-v1")
            .put("type", if (preWrite) "PRE_WRITE" else "MANUAL_SNAPSHOT")
            .put("label", cleanLabel)
            .put("createdAt", createdAt)
            .put("hash", curveHash)
            .put("changes", JSONArray())
        atomicWrite(File(backupDir, fileName), backup.toString(2))
        val verified = loadBackup(fileName)
        require(verified.getString("hash") == curveHash) { "Hash do backup salvo divergiu" }
        return ManualPhoto(fileName, curveHash, createdAt, cleanLabel)
    }

    fun listBackups(): JSONArray {
        val rows = KFactorBackupRetention.visibleFiles(backupDir.listFiles())
            .mapNotNull { file ->
                try {
                    val backup = loadBackup(file.name)
                    JSONObject()
                        .put("fileName", file.name)
                        .put("createdAt", backup.optLong("createdAt", file.lastModified()))
                        .put("hash", backup.getString("hash"))
                        .put("type", backup.optString("type", "PRE_WRITE"))
                        .put("label", backup.optString("label", if (file.name.startsWith("MANUAL-")) "Curva salva" else "Backup automático"))
                        .put("pointCount", KFactorProtocol.POINT_COUNT)
                } catch (_: Exception) {
                    null
                }
            }
            .sortedByDescending { it.optLong("createdAt", 0L) }
        return JSONArray(rows)
    }

    fun prepareRestore(fileName: String): JSONObject {
        return try {
            val backup = loadBackup(fileName)
            val targetAxis = jsonIntArray(backup.optJSONArray("axisRaw"))
            val targetFactors = jsonIntArray(backup.optJSONArray("factorsRaw"))
            val fresh = readCurve()
            if (!fresh.optBoolean("ok", false)) return fresh
            val currentAxis = jsonIntArray(fresh.optJSONArray("axisRaw"))
            val currentFactors = jsonIntArray(fresh.optJSONArray("factorsRaw"))
            if (!currentAxis.contentEquals(targetAxis)) {
                return error("O eixo Petrol Inj. atual difere do backup; restauração bloqueada")
                    .put("geometryMismatch", true)
                    .put("fileName", fileName)
            }
            // O piso 0.60 vale só para alvos NOVOS. Restaurar um valor que a ECU já teve
            // (ex.: a foto de uma curva com fator baixo) é permitido; o readback confirma.
            val points = JSONArray()
            repeat(KFactorProtocol.POINT_COUNT) { index ->
                val currentRaw = currentFactors[index]
                val targetRaw = targetFactors[index]
                if (targetRaw !in 0..KFactorProtocol.MAX_RAW) {
                    return error("O backup contém fator fora do formato da ECU no ponto ${index + 1}")
                        .put("unsafeBackup", true)
                        .put("fileName", fileName)
                }
                if (currentRaw != targetRaw) {
                    points.put(
                        JSONObject()
                            .put("index", index)
                            .put("petrolMs", KFactorProtocol.petrolMsFromAxisRaw(currentAxis[index]))
                            .put("currentRaw", currentRaw)
                            .put("targetRaw", targetRaw)
                            .put("currentFactor", KFactorProtocol.factorFromRaw(currentRaw))
                            .put("targetFactor", KFactorProtocol.factorFromRaw(targetRaw))
                            .put(
                                "deltaPercent",
                                if (currentRaw == 0) 0.0
                                else (targetRaw.toDouble() / currentRaw.toDouble() - 1.0) * 100.0,
                            ),
                    )
                }
            }
            JSONObject()
                .put("ok", true)
                .put("fileName", fileName)
                .put("createdAt", backup.optLong("createdAt", 0L))
                .put("hash", backup.getString("hash"))
                .put("type", backup.optString("type", "PRE_WRITE"))
                .put("label", backup.optString("label", "Backup Curva K"))
                .put("currentHash", hash(currentFactors))
                .put("changedPoints", points.length())
                .put("points", points)
                .put("currentCurve", fresh)
                .put("restoreUsesExistingWriter", true)
                .put("restoreFile", fileName)
                .put("preWriteBackupRequired", false)
                .put("automaticBackup", false)
        } catch (error: Exception) {
            error(error.message ?: "Backup da Curva K inválido")
        }
    }

    /**
     * Valida e normaliza o lote. [allowBelowFloor] só é verdadeiro na restauração de uma foto:
     * o piso 0.60 vale para alvos novos, não para valores que a ECU já tinha.
     */
    private fun normalizePoints(
        points: JSONArray,
        allowBelowFloor: Boolean,
        /** (índice, raw) que a ECU já teve segundo uma foto salva: desfazer a partir do diário volta a ele. */
        hadBefore: (Int, Int) -> Boolean = { _, _ -> false },
    ): JSONArray {
        require(points.length() in 1..MAX_BATCH_POINTS) { "Selecione entre 1 e $MAX_BATCH_POINTS pontos" }
        val normalized = JSONArray()
        val seen = linkedSetOf<Int>()
        val minimumRaw = if (allowBelowFloor) 0 else KFactorProtocol.rawFromFactor(MIN_SAFE_FACTOR)
        val maximumRaw = KFactorProtocol.MAX_RAW
        repeat(points.length()) { position ->
            val point = points.optJSONObject(position) ?: throw IllegalArgumentException("Ponto ${position + 1} inválido")
            val index = point.optInt("index", -1)
            val currentRaw = point.optInt("currentRaw", -1)
            val targetRaw = point.optInt("targetRaw", -1)
            require(index in 0 until KFactorProtocol.POINT_COUNT) { "Índice inválido: $index" }
            // O piso vale para alvos NOVOS: um valor abaixo dele só passa se a ECU já o teve (consta numa foto salva).
            val floorOk = targetRaw >= minimumRaw || (targetRaw in 0 until minimumRaw && hadBefore(index, targetRaw))
            require(currentRaw in 0..KFactorProtocol.MAX_RAW && targetRaw <= maximumRaw && floorOk) {
                "K factor alvo deve estar entre %.2f e %.4f".format(MIN_SAFE_FACTOR, MAX_SAFE_FACTOR)
            }
            require(currentRaw != targetRaw) { "O ponto $index não possui alteração" }
            require(seen.add(index)) { "Ponto $index repetido" }
            normalized.put(JSONObject()
                .put("index", index)
                .put("currentRaw", currentRaw)
                .put("targetRaw", targetRaw)
                .put("currentFactor", KFactorProtocol.factorFromRaw(currentRaw))
                .put("targetFactor", KFactorProtocol.factorFromRaw(targetRaw)))
        }
        return normalized
    }

    /** Cache dos valores brutos por ponto que as fotos salvas guardam; lido só quando um alvo cai abaixo do piso. */
    private fun photoHadRaw(index: Int, raw: Int): Boolean {
        for (file in KFactorBackupRetention.visibleFiles(backupDir.listFiles())) {
            try {
                val factors = jsonIntArray(loadBackup(file.name).optJSONArray("factorsRaw"))
                if (index in factors.indices && factors[index] == raw) return true
            } catch (_: Exception) {
                // Foto ilegível não prova nada: segue para a próxima.
            }
        }
        return false
    }

    /**
     * Restauração de uma foto: cada alvo precisa ser exatamente o valor guardado na foto.
     * Reusa o escritor em lote (foto antes, ACK por ponto, readback final).
     */
    fun startRestoreWrite(points: JSONArray, fileName: String, reason: String): JSONObject {
        val backup = try { loadBackup(fileName) } catch (error: Exception) {
            return error(error.message ?: "Backup da Curva K inválido")
        }
        val target = jsonIntArray(backup.optJSONArray("factorsRaw"))
        // O eixo Petrol Inj. da ECU (última leitura desta sessão) precisa ser o da foto: índice igual a ponto igual.
        val photoAxis = jsonIntArray(backup.optJSONArray("axisRaw"))
        val currentAxis = jsonIntArray(loadCache().optJSONArray("axisRaw"))
        if (currentAxis.size != KFactorProtocol.POINT_COUNT || !currentAxis.contentEquals(photoAxis)) {
            return error("O eixo Petrol Inj. atual difere da foto; restauração bloqueada. Refaça a prévia.")
                .put("geometryMismatch", true)
        }
        repeat(points.length()) { position ->
            val point = points.optJSONObject(position) ?: return error("Ponto ${position + 1} inválido")
            val index = point.optInt("index", -1)
            if (index !in target.indices || point.optInt("targetRaw", -1) != target[index]) {
                return error("A restauração não confere com a foto escolhida; releia a prévia")
            }
        }
        return startBatchWrite(points, reason, allowBelowFloor = true)
    }

    fun startBatchWrite(points: JSONArray, reason: String = "Ajuste manual assistido", allowBelowFloor: Boolean = false): JSONObject {
        val normalized = try {
            // Abaixo do piso só no Desfazer/restauração (allowBelowFloor): gravação nova nunca herda um valor baixo de uma foto antiga.
            normalizePoints(points, allowBelowFloor, hadBefore = { index, raw -> allowBelowFloor && photoHadRaw(index, raw) })
        } catch (invalid: IllegalArgumentException) {
            return error(invalid.message ?: "Lote de pontos inválido")
        }
        if (!busy.compareAndSet(false, true)) return error("Outra operação de calibração está em andamento")
        // Tudo entre adquirir `busy` e entregar ao executor fica sob try/finally: nada vaza `busy`/trava.
        var submitted = false
        return try {
            val expectedSessionId = try { currentSessionId() } catch (cause: Exception) {
                return error(cause.message ?: "USB desconectado", FailureKind.TRANSPORT)
            }
            if (!guard.tryAcquire(SerialWriteGuard.OWNER_K_FACTOR)) {
                return error("Aguarde: ${SerialWriteGuard.label(guard.holder())} em andamento")
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
            submitted = true
            JSONObject()
                .put("ok", true)
                .put("started", true)
                .put("adjustmentId", adjustmentId)
                .put("points", normalized.length())
                .put("automatic", false)
                .put("humanConfirmationRequired", true)
                .put("automaticBackup", false)
        } catch (_: RejectedExecutionException) {
            error("O escritor da Curva K foi encerrado; reabra o aplicativo")
        } finally {
            if (!submitted) releaseWriter()
        }
    }

    fun close() = executor.shutdownNow()

    private fun executeBatch(adjustmentId: String, points: JSONArray, reason: String, expectedSessionId: Long) {
        val startedAt = System.currentTimeMillis()
        val confirmed = JSONArray()
        var initialHash = ""
        var historyPersisted = false
        var photoFile = ""
        var writeStarted = false
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

            val working = serial.unit(
                reason = "escrita direta Curva K",
                expectedSessionId = expectedSessionId,
                workClass = Mp48WorkClass.MANUAL_WRITE,
                telemetryAfter = true,
                waitTimeoutMs = 10_000L,
            ) { unit ->
                update("VERIFYING_CURRENT", "Conferindo os 30 pontos antes da escrita", 8)
                val ecuBefore = readRawPoints(unit, KFactorProtocol.readFactors(), "conferência K factor")
                if (!ecuBefore.contentEquals(cachedFactors)) {
                    throw IllegalStateException("A curva da ECU mudou. Leia novamente antes de aplicar")
                }
                val live = ecuBefore.copyOf()
                initialHash = hash(live)

                // Foto antes do primeiro ACK: a curva que acabou de ser lida e conferida vai para o
                // disco (nenhum comando novo à ECU). Sem foto validada, a escrita não começa.
                val photo = writeManualPhoto(cachedAxis, ecuBefore, "Antes de gravar", preWrite = true)
                photoFile = photo.fileName
                // Rotação: sem isto cada escrita deixaria uma foto para sempre. Falha de limpeza nunca barra a escrita.
                try { pruneBackups() } catch (_: Exception) {}
                update(
                    "PHOTO_SAVED",
                    "Foto da curva salva antes de gravar",
                    12,
                    JSONObject().put("adjustmentId", adjustmentId).put("photoFile", photoFile),
                )

                repeat(points.length()) { pointPosition ->
                    val point = points.getJSONObject(pointPosition)
                    val index = point.getInt("index")
                    val currentRaw = point.getInt("currentRaw")
                    val targetRaw = point.getInt("targetRaw")
                    if (live[index] != currentRaw) {
                        throw IllegalStateException("Ponto $index: esperado $currentRaw, encontrado ${live[index]}")
                    }
                    val progress = 15 + ((pointPosition + 1) * 65 / points.length())
                    update(
                        "WRITING_POINT",
                        "Ponto ${pointPosition + 1}/${points.length()} • ${formatFactor(currentRaw)} → ${formatFactor(targetRaw)}",
                        progress,
                        JSONObject().put("adjustmentId", adjustmentId).put("index", index),
                    )
                    writeStarted = true
                    requireAck(
                        unit.transaction(
                            KFactorProtocol.writeFactor(index, targetRaw),
                            "escrita K factor[$index]",
                            900,
                            purgeBefore = false,
                        ),
                        "escrita do ponto $index",
                    )
                    live[index] = targetRaw
                    confirmed.put(
                        JSONObject()
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
                            .put("acknowledged", true)
                            .put("confirmed", false)
                            .put("batchFinalized", false)
                            .put("automatic", false),
                    )
                }

                update("VERIFYING_FINAL", "Confirmando readback K factor final", 90)
                val finalReadback = readRawPoints(unit, KFactorProtocol.readFactors(), "confirmação final K factor")
                if (!finalReadback.contentEquals(live)) {
                    throw IllegalStateException("A confirmação final da curva divergiu")
                }
                live
            }

            val finalHash = hash(working)
            repeat(confirmed.length()) { index ->
                confirmed.getJSONObject(index)
                    .put("confirmed", true)
                    .put("batchFinalized", true)
                    .put("finalCurveHash", finalHash)
            }
            appendHistoryBatch(confirmed)
            historyPersisted = true

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
                .put("automaticBackup", false)
                .put("photoFile", photoFile)
                .put("confirmedAt", now)
            try { onConfirmedBatch(payload) } catch (error: Exception) {
                log.add("WARN", "K-FACTOR", "Curva confirmada; notificação falhou: ${error.message}")
            }
            update("BATCH_CONFIRMED", "K factor confirmado por ACK e readback", 100, payload)
            log.add("INFO", "K-FACTOR", "$adjustmentId confirmado • ${points.length()} pontos")
        } catch (error: Throwable) {
            if (!historyPersisted && confirmed.length() > 0) {
                try {
                    appendHistoryBatch(confirmed)
                    historyPersisted = true
                } catch (_: Throwable) {}
            }
            // O estado terminal vem primeiro: nada que falhe depois pode deixar a UI esperando.
            update(
                "BATCH_FAILED",
                error.message ?: "Alteração K factor interrompida",
                100,
                JSONObject()
                    .put("adjustmentId", adjustmentId)
                    .put("oldHash", initialHash)
                    .put("confirmedEvents", confirmed)
                    .put("partial", confirmed.length() > 0)
                    .put("mutationMayHaveStarted", writeStarted)
                    .put("photoFile", photoFile)
                    .put("failureKind", FailureKind.of(error)),
            )
            if (writeStarted) {
                try {
                    onFailedBatch(
                        JSONObject().put("adjustmentId", adjustmentId).put("photoFile", photoFile)
                            .put("partial", confirmed.length() > 0).put("mutationMayHaveStarted", true),
                    )
                } catch (notify: Throwable) {
                    try { log.add("WARN", "K-FACTOR", "Falha registrada; notificação falhou: ${notify.message}") } catch (_: Throwable) {}
                }
            }
            try {
                val stale = loadCache().put("sessionConfirmed", false)
                atomicWrite(cacheFile, stale.toString(2))
            } catch (_: Throwable) {}
            try { log.add("ERROR", "K-FACTOR", "$adjustmentId interrompido: ${error.message}") } catch (_: Throwable) {}
        } finally {
            releaseWriter()
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
        if (!reply.ok) throw CalibrationFailure(reply.error.ifBlank { "ECU não confirmou $reason" }, FailureKind.ofReply(reply))
        if (reply.status != Mp48Protocol.STATUS_ACK) {
            throw CalibrationFailure("Resposta inesperada 0x%02X em $reason".format(reply.status), FailureKind.ECU)
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
            throw CalibrationFailure(reply.error.ifBlank { "ACK inválido em $action" }, FailureKind.ofReply(reply))
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

    private fun loadBackup(fileName: String): JSONObject {
        require(fileName.isNotBlank() && File(fileName).name == fileName && !fileName.contains("..")) {
            "Nome de backup inválido"
        }
        val file = File(backupDir, fileName)
        require(file.isFile) { "Backup da Curva K não encontrado" }
        val backup = JSONObject(file.readText(Charsets.UTF_8))
        require(backup.optString("format") == "omegas-k-factor-backup-v1") { "Formato de backup inválido" }
        val axisRaw = jsonIntArray(backup.optJSONArray("axisRaw"))
        val factorsRaw = jsonIntArray(backup.optJSONArray("factorsRaw"))
        require(axisRaw.size == KFactorProtocol.POINT_COUNT && factorsRaw.size == KFactorProtocol.POINT_COUNT) {
            "Backup da Curva K incompleto"
        }
        val expectedHash = backup.optString("hash")
        require(expectedHash.isNotBlank() && expectedHash == hash(factorsRaw)) { "Hash do backup da Curva K inválido" }
        return backup
    }

    private fun pruneBackups() {
        KFactorBackupRetention.pruneAutomatic(backupDir)
    }

    private fun loadCache(): JSONObject = try {
        if (cacheFile.isFile) JSONObject(cacheFile.readText(Charsets.UTF_8)) else JSONObject()
    } catch (_: Exception) { JSONObject() }

    private fun loadHistory(): JSONArray = try {
        if (historyFile.isFile) JSONArray(historyFile.readText(Charsets.UTF_8)) else JSONArray()
    } catch (_: Exception) { JSONArray() }

    private fun appendHistoryBatch(items: JSONArray) {
        if (items.length() == 0) return
        val history = loadHistory()
        repeat(items.length()) { index ->
            history.put(JSONObject(items.getJSONObject(index).toString()))
        }
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

    private fun error(message: String, kind: String? = null): JSONObject =
        JSONObject().put("ok", false).put("error", message).apply { if (kind != null) put("failureKind", kind) }

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