package com.omegas.prohub.calibration

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
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Autoridade Android do mapa K.
 *
 * Toda operação serial passa pelo scheduler único da engine MP48. Escritas
 * manuais usam ACK rápido por comando e readback final por linha afetada.
 * Nenhuma sugestão inicia este writer.
 */
class KWriteManager(
    private val paths: AppPaths,
    private val serial: Mp48SerialScheduler,
    private val log: RingLog,
    @Suppress("UNUSED_PARAMETER") private val isEngineRunning: () -> Boolean,
    @Suppress("UNUSED_PARAMETER") private val stopEngine: () -> Boolean,
    @Suppress("UNUSED_PARAMETER") private val startEngine: (String) -> Boolean,
    private val onBusyChanged: (Boolean) -> Unit,
    private val onConfirmedWrite: () -> Unit = {},
    private val onConfirmedBatch: (JSONObject) -> Unit = {},
    /** Trava única da serial: escrita K e ação AutoCal nunca rodam juntas. */
    private val guard: SerialWriteGuard = SerialWriteGuard.shared,
) {
    companion object {
        const val MAP_K_ADDRESS = 0x0054
        /** Doze linhas visíveis e graváveis no mapa de calibração. */
        const val ROW_COUNT = KMapPhysicalAxes.WRITABLE_ROWS
        const val COLUMN_COUNT = KMapPhysicalAxes.COLUMNS
        /** A ECU oficial também expõe a linha 0C, preservada separadamente. */
        const val EXTRA_ROW = KMapPhysicalAxes.WRITABLE_ROWS
        const val TOTAL_ROW_COUNT = KMapPhysicalAxes.PROTOCOL_ROWS
        const val MIN_SAFE_K = 100
        const val MAX_BATCH_CELLS = ROW_COUNT * COLUMN_COUNT
        /** Compatibilidade de API; o writer direto não usa mais ramping. */
        const val MAX_SAFE_STEP = 25
        const val MAX_SAFE_PAUSE_MS = 2_000
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-k-writer").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)
    private val historyFile = File(paths.runtimeRoot, "k_write_history.json")
    private val cacheFile = File(paths.runtimeRoot, "k_map_cache.json")
    private val safetyFile = File(paths.runtimeRoot, "k_write_safety.json")
    private val kBackupDir = File(paths.runtimeRoot, "k_map_backups").apply { mkdirs() }
    private val statusLock = Any()
    private val insertionStateUnknown = AtomicBoolean(loadInsertionSafetyLock(safetyFile))
    @Volatile private var status = JSONObject()
        .put("state", "IDLE")
        .put("busy", false)
        .put("message", "Aguardando ação manual")

    fun isBusy(): Boolean = busy.get()
    fun statusJson(): String = synchronized(statusLock) { JSONObject(status.toString()).toString() }

    @Synchronized
    fun beginUsbSession(sessionId: Long) {
        try {
            val cache = loadCache()
            cache.put("sessionConfirmed", false)
                .put("sessionStartedAt", System.currentTimeMillis())
                .put("sessionId", sessionId)
                .put("source", "PREVIOUS_SESSION")
            atomicWrite(cacheFile, cache.toString(2))
        } catch (error: Exception) {
            // Disco cheio/somente leitura: a sessão USB segue; o cache em disco fica como estava
            // (o gate de escrita exige leitura confirmada nesta sessão, então nada grava sem leitura nova).
            log.add("WARN", "K-WRITE", "Cache do mapa K não atualizado ao conectar: ${error.message}")
        }
        if (insertionStateUnknown.get()) {
            update("SAFETY_LOCKED_INSERTION_UNKNOWN", "Confirme a saída do modo K insertion antes de qualquer operação", 0)
        } else {
            update("MAP_PENDING", "Mapa K ainda não foi confirmado nesta sessão", 0)
        }
    }

    fun exportHistoryComponent(deviceId: String): JSONObject = JSONObject()
        .put("format", "omegas-k-history-v1")
        .put("deviceId", deviceId)
        .put("events", loadHistory())

    @Synchronized
    fun mergeHistoryComponent(payload: JSONObject): JSONObject {
        if (payload.optString("format") != "omegas-k-history-v1") {
            return JSONObject().put("ok", false).put("error", "Histórico incompatível")
        }
        val incoming = payload.optJSONArray("events") ?: JSONArray()
        val current = loadHistory()
        val ids = linkedSetOf<String>()
        fun eventId(item: JSONObject): String {
            val explicit = item.optString("id")
            if (explicit.isNotBlank()) return explicit
            val raw = listOf(
                item.optLong("timestamp"), item.optInt("row"), item.optInt("column"),
                item.optInt("before"), item.optInt("after"),
            ).joinToString(":")
            return java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        }
        val merged = mutableListOf<JSONObject>()
        repeat(current.length()) { index ->
            current.optJSONObject(index)?.let { item ->
                val id = eventId(item); ids += id
                if (!item.has("id")) item.put("id", id)
                merged += item
            }
        }
        var added = 0
        repeat(incoming.length()) { index ->
            incoming.optJSONObject(index)?.let { item ->
                val id = eventId(item)
                if (ids.add(id)) {
                    if (!item.has("id")) item.put("id", id)
                    merged += item
                    added += 1
                }
            }
        }
        merged.sortBy { it.optLong("timestamp", 0L) }
        val out = JSONArray()
        merged.takeLast(4_000).forEach(out::put)
        atomicWrite(historyFile, out.toString(2))
        return JSONObject().put("ok", true).put("added", added).put("total", out.length())
    }

    fun readCell(row: Int, column: Int): JSONObject {
        if (!validCell(row, column)) return error("Célula inválida")
        if (insertionStateUnknown.get()) return safetyError()
        val expectedSessionId = try { currentSessionId() } catch (error: Exception) {
            return error(error.message ?: "USB desconectado", FailureKind.TRANSPORT)
        }
        return runSynchronous("READING_CELL", "Lendo célula K") {
            val line = readRow(row, "leitura da célula K[$row,$column]", expectedSessionId)
            val value = line[column].toInt() and 0xFF
            updateCacheCell(row, column, value, "ECU_CELL_READ")
            JSONObject()
                .put("ok", true)
                .put("row", row)
                .put("column", column)
                .put("value", value)
                .put("confirmed", true)
                .put("timestamp", System.currentTimeMillis())
        }
    }

    fun readLine(row: Int): JSONObject {
        if (row !in 0 until ROW_COUNT) return error("Linha inválida")
        if (insertionStateUnknown.get()) return safetyError()
        val expectedSessionId = try { currentSessionId() } catch (error: Exception) {
            return error(error.message ?: "USB desconectado", FailureKind.TRANSPORT)
        }
        return runSynchronous("READING_LINE", "Lendo linha K") {
            val line = readRow(row, "leitura da linha K[$row]", expectedSessionId)
            updateCacheLine(row, line, "ECU_LINE_READ")
            JSONObject()
                .put("ok", true)
                .put("row", row)
                .put("values", JSONArray(line.map { it.toInt() and 0xFF }))
                .put("timestamp", System.currentTimeMillis())
        }
    }

    fun readFullMap(): JSONObject {
        // O projeto exige leitura manual. O monitor não deve iniciar uma leitura
        // completa escondida nem poluir o ciclo de telemetria.
        if (Thread.currentThread().name == "omegas-health-monitor") {
            update("MAP_PENDING", "Use Ler mapa K para confirmar o mapa desta sessão", 0)
            return JSONObject()
                .put("ok", false)
                .put("manualRequired", true)
                .put("error", "Leitura automática do mapa K desativada")
        }
        if (insertionStateUnknown.get()) return safetyError()
        val expectedSessionId = try { currentSessionId() } catch (error: Exception) {
            return error(error.message ?: "USB desconectado", FailureKind.TRANSPORT)
        }
        return runSynchronous("READING_MAP", "Lendo mapa K completo") {
            val allRows = JSONArray()
            repeat(TOTAL_ROW_COUNT) { row ->
                update(
                    "READING_MAP",
                    "Lendo linha ${row + 1} de $TOTAL_ROW_COUNT",
                    ((row + 1) * 100 / TOTAL_ROW_COUNT),
                )
                allRows.put(JSONArray(readRow(row, "mapa K linha ${row + 1}/$TOTAL_ROW_COUNT", expectedSessionId)
                    .map { it.toInt() and 0xFF }))
            }
            val visibleRows = JSONArray()
            repeat(ROW_COUNT) { visibleRows.put(JSONArray(allRows.getJSONArray(it).toString())) }
            val extraRow = JSONArray(allRows.getJSONArray(EXTRA_ROW).toString())
            val hash = canonicalFullMapHash(visibleRows, extraRow)
            val now = System.currentTimeMillis()
            val axes = KMapPhysicalAxes.json()
            val cache = JSONObject()
                .put("schema", 4)
                .put("updatedAt", now)
                .put("source", "ECU_FULL_READ_NATIVE")
                .put("complete", true)
                .put("sessionConfirmed", true)
                .put("sessionId", expectedSessionId)
                .put("hash", hash)
                .put("axes", axes)
                .put("rows", visibleRows)
                .put("extraRow", extraRow)
                .put("allRows", allRows)
            atomicWrite(cacheFile, cache.toString(2))
            try { onConfirmedWrite() } catch (error: Exception) { log.add("WARN", "K-WRITE", "Gravação confirmada; notificação falhou: ${error.message}") }
            val details = JSONObject()
                .put("hash", hash)
                .put("updatedAt", now)
                .put("cells", TOTAL_ROW_COUNT * COLUMN_COUNT)
                .put("writableCells", ROW_COUNT * COLUMN_COUNT)
                .put("axes", axes)
            update("MAP_SYNCED", "Mapa K confirmado pela ECU", 100, details)
            JSONObject()
                .put("ok", true)
                .put("rows", visibleRows)
                .put("extraRow", extraRow)
                .put("allRows", allRows)
                .put("hash", hash)
                .put("updatedAt", now)
                .put("cells", TOTAL_ROW_COUNT * COLUMN_COUNT)
                .put("writableCells", ROW_COUNT * COLUMN_COUNT)
                .put("axes", axes)
                .put("sessionConfirmed", true)
                .put("sessionId", expectedSessionId)
        }
    }

    /**
     * Toque do dono em "Liberar Mapa K": manda a SAÍDA do K insertion (o mesmo comando de sempre) e só
     * solta a trava depois do ACK da ECU. Sem ACK a trava continua e a falha vem classificada
     * (cabo/USB × ECU). Operação normal: usa `busy` e a trava da serial como qualquer escrita.
     */
    fun recoverInsertionState(): JSONObject {
        val expectedSessionId = try { currentSessionId() } catch (error: Exception) {
            return error(error.message ?: "USB desconectado", FailureKind.TRANSPORT)
        }
        return runSynchronous("RECOVERING_INSERTION", "Confirmando saída do modo K insertion", mutating = true) {
            requireAck(
                transaction(
                    Mp48Protocol.kInsertionMode(false),
                    "recuperação da saída K insertion",
                    1_200,
                    expectedSessionId,
                    Mp48WorkClass.SAFETY,
                    telemetryAfter = false,
                ),
                "recuperação da saída K insertion",
            )
            // A ECU confirmou a saída: o estado em memória solta primeiro; o arquivo é melhor esforço
            // (se não gravar, a trava antiga só reaparece num reinício e o dono toca de novo).
            insertionStateUnknown.set(false)
            try { setInsertionSafetyLock(false, "Saída confirmada manualmente") } catch (failure: Exception) {
                log.add("WARN", "K-WRITE", "Saída confirmada; trava em disco não limpa: ${failure.message}")
            }
            try {
                val stale = loadCache().put("sessionConfirmed", false).put("sessionId", expectedSessionId)
                atomicWrite(cacheFile, stale.toString(2))
            } catch (failure: Exception) {
                log.add("WARN", "K-WRITE", "Cache do mapa K não marcado como antigo: ${failure.message}")
            }
            update("MAP_PENDING", "Saída confirmada; releia o mapa K desta sessão", 100)
            JSONObject()
                .put("ok", true)
                .put("recovered", true)
                .put("released", true)
                .put("message", "Mapa K liberado")
                .put("sessionId", expectedSessionId)
        }
    }

    fun startWrite(
        row: Int,
        column: Int,
        expectedCurrent: Int,
        target: Int,
        maxStep: Int,
        pauseMs: Int,
        reason: String = "Manual",
    ): JSONObject {
        val cells = JSONArray().put(JSONObject()
            .put("row", row).put("column", column)
            .put("current", expectedCurrent).put("target", target))
        return startBatchWrite(cells, maxStep, pauseMs, reason)
    }

    fun startBatchWrite(
        cells: JSONArray,
        @Suppress("UNUSED_PARAMETER") maxStep: Int,
        @Suppress("UNUSED_PARAMETER") pauseMs: Int,
        reason: String = "Calibração manual",
        allowBelowFloor: Boolean = false,
    ): JSONObject {
        if (insertionStateUnknown.get()) return safetyError()
        if (cells.length() !in 1..MAX_BATCH_CELLS) return error("Selecione entre 1 e $MAX_BATCH_CELLS células")
        val normalized = JSONArray()
        val seen = linkedSetOf<String>()
        repeat(cells.length()) { index ->
            val item = cells.optJSONObject(index) ?: return error("Célula ${index + 1} inválida")
            val row = item.optInt("row", -1)
            val column = item.optInt("column", -1)
            val current = item.optInt("current", -1)
            val target = item.optInt("target", -1)
            if (!validCell(row, column)) return error("Célula [$row,$column] inválida")
            // O piso vale só para alvos NOVOS; restaurar um valor que a ECU já teve é permitido.
            val floor = if (allowBelowFloor) 0 else MIN_SAFE_K
            if (current !in 0..255 || target !in floor..255) {
                return error("Valor K alvo deve estar entre $floor e 255")
            }
            if (current == target) return error("A célula [$row,$column] não possui alteração")
            if (!seen.add("$row:$column")) return error("Célula [$row,$column] repetida")
            normalized.put(JSONObject()
                .put("row", row).put("column", column)
                .put("current", current).put("target", target))
        }
        if (!busy.compareAndSet(false, true)) return error("Outra operação K está em andamento")
        // Tudo entre adquirir `busy` e entregar o trabalho ao executor fica sob try/finally:
        // nenhuma exceção (nem Error) deixa `busy` ou a trava da serial presas.
        var guardHeld = false
        var submitted = false
        return try {
            val expectedSessionId = try { currentSessionId() } catch (cause: Exception) {
                return error(cause.message ?: "USB desconectado", FailureKind.TRANSPORT)
            }
            if (!guard.tryAcquire(SerialWriteGuard.OWNER_K_MAP)) {
                return error("Aguarde: ${SerialWriteGuard.label(guard.holder())} em andamento")
            }
            guardHeld = true
            val adjustmentId = "ADJ-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
            onBusyChanged(true)
            update("BATCH_QUEUED", "Alteração enfileirada entre telemetrias", 0,
                JSONObject().put("adjustmentId", adjustmentId).put("cells", normalized))
            executor.execute {
                executeBatch(adjustmentId, normalized, reason, expectedSessionId)
            }
            submitted = true
            JSONObject()
                .put("ok", true)
                .put("started", true)
                .put("adjustmentId", adjustmentId)
                .put("cells", normalized.length())
        } catch (_: RejectedExecutionException) {
            error("O escritor do Mapa K foi encerrado; reabra o aplicativo")
        } finally {
            if (!submitted) {
                if (guardHeld) guard.release(SerialWriteGuard.OWNER_K_MAP)
                busy.set(false)
                try { onBusyChanged(false) } catch (_: Throwable) {}
                synchronized(statusLock) { status.put("busy", false) }
            }
        }
    }

    /**
     * Lista as fotos do Mapa K (uma por escrita) para o Desfazer. Só lê arquivos locais.
     */
    fun listMapBackups(): JSONArray {
        val rows = kBackupDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .mapNotNull { file ->
                try {
                    val backup = JSONObject(file.readText(Charsets.UTF_8))
                    if (backup.optString("format") != "omegas-k-backup-v1") return@mapNotNull null
                    JSONObject()
                        .put("adjustmentId", file.nameWithoutExtension)
                        .put("createdAt", backup.optLong("createdAt", file.lastModified()))
                        .put("cells", backup.optJSONArray("cells")?.length() ?: 0)
                } catch (_: Exception) { null }
            }
            .sortedByDescending { it.optLong("createdAt", 0L) }
        return JSONArray(rows)
    }

    /**
     * Prepara a restauração de uma escrita do Mapa K: relê o mapa da ECU (somente leitura) e devolve,
     * para cada célula da foto cujo valor atual difere do original, {row, column, current, target=original}.
     * Nada é gravado aqui; a escrita é o toque seguinte, pelo mesmo escritor em lote.
     */
    fun prepareRestore(adjustmentId: String): JSONObject {
        val backup = try { loadMapBackup(adjustmentId) } catch (error: Exception) {
            return error(error.message ?: "Foto do Mapa K inválida")
        }
        val fresh = readFullMap()
        if (!fresh.optBoolean("ok", false)) return fresh
        val rows = fresh.optJSONArray("rows") ?: return error("Leitura do Mapa K incompleta")
        val originals = backup.optJSONArray("cells") ?: JSONArray()
        val cells = JSONArray()
        repeat(originals.length()) { index ->
            val item = originals.optJSONObject(index) ?: return@repeat
            val row = item.optInt("row", -1)
            val column = item.optInt("column", -1)
            val original = item.optInt("current", -1)
            if (!validCell(row, column) || original !in 0..255) return@repeat
            val now = rows.optJSONArray(row)?.optInt(column, -1) ?: -1
            if (now in 0..255 && now != original) {
                cells.put(JSONObject()
                    .put("row", row).put("column", column)
                    .put("current", now).put("target", original))
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("adjustmentId", adjustmentId)
            .put("createdAt", backup.optLong("createdAt", 0L))
            .put("changedCells", cells.length())
            .put("cells", cells)
    }

    /** Escreve de volta SOMENTE os valores originais da foto (confere cada alvo contra o arquivo). */
    fun startRestoreWrite(cells: JSONArray, adjustmentId: String, reason: String): JSONObject {
        val backup = try { loadMapBackup(adjustmentId) } catch (error: Exception) {
            return error(error.message ?: "Foto do Mapa K inválida")
        }
        val originals = hashMapOf<String, Int>()
        val saved = backup.optJSONArray("cells") ?: JSONArray()
        repeat(saved.length()) { index ->
            val item = saved.optJSONObject(index) ?: return@repeat
            originals["${item.optInt("row")}:${item.optInt("column")}"] = item.optInt("current", -1)
        }
        repeat(cells.length()) { index ->
            val item = cells.optJSONObject(index) ?: return error("Célula ${index + 1} inválida")
            val key = "${item.optInt("row", -1)}:${item.optInt("column", -1)}"
            if (originals[key] != item.optInt("target", -2)) {
                return error("A restauração não confere com a foto escolhida; refaça a prévia")
            }
        }
        return startBatchWrite(cells, 0, 0, reason, allowBelowFloor = true)
    }

    private fun loadMapBackup(adjustmentId: String): JSONObject {
        require(adjustmentId.isNotBlank() && File(adjustmentId).name == adjustmentId && !adjustmentId.contains("..")) {
            "Nome de foto inválido"
        }
        val file = File(kBackupDir, "$adjustmentId.json")
        require(file.isFile) { "Foto do Mapa K não encontrada" }
        val backup = JSONObject(file.readText(Charsets.UTF_8))
        require(backup.optString("format") == "omegas-k-backup-v1") { "Formato de foto inválido" }
        return backup
    }

    fun close() = executor.shutdownNow()

    private fun executeBatch(
        adjustmentId: String,
        cells: JSONArray,
        reason: String,
        expectedSessionId: Long,
    ) {
        val startedAt = System.currentTimeMillis()
        val confirmed = JSONArray()
        val affectedRows = linkedSetOf<Int>()
        var initialHash = ""
        var insertionEnabled = false
        var historyPersisted = false
        var writeStarted = false
        try {
            update("BATCH_PREPARING", "Conferindo somente as linhas afetadas", 3,
                JSONObject().put("adjustmentId", adjustmentId).put("cells", cells))
            val cache = loadCache()
            val cachedRows = cache.optJSONArray("rows") ?: JSONArray()
            val extraRow = cache.optJSONArray("extraRow") ?: JSONArray()
            if (!cache.optBoolean("complete") || !cache.optBoolean("sessionConfirmed") ||
                !isCompleteVisibleMap(cachedRows) || extraRow.length() != COLUMN_COUNT ||
                cache.optLong("sessionId", -1L) != expectedSessionId
            ) {
                throw IllegalStateException("Leia o mapa K desta sessão antes de aplicar alterações")
            }
            val workingRows = JSONArray(cachedRows.toString())
            initialHash = canonicalFullMapHash(workingRows, extraRow)
            createPreWriteBackup(adjustmentId, cache, cells, initialHash)
            repeat(cells.length()) { affectedRows += cells.getJSONObject(it).getInt("row") }

            serial.unit(
                reason = "conferência prévia Mapa K",
                expectedSessionId = expectedSessionId,
                workClass = Mp48WorkClass.READ_ONLY,
                telemetryAfter = false,
                waitTimeoutMs = 6_000L,
            ) { unit ->
                affectedRows.forEachIndexed { index, row ->
                    update("BATCH_CHECKING_ROWS", "Conferindo linha ${index + 1} de ${affectedRows.size}",
                        5 + ((index + 1) * 12 / affectedRows.size), JSONObject().put("row", row))
                    val ecuLine = readRow(unit, row, "conferência antes da escrita K[$row]")
                    val cachedLine = workingRows.getJSONArray(row)
                    repeat(COLUMN_COUNT) { column ->
                        val actual = ecuLine[column].toInt() and 0xFF
                        val expected = cachedLine.getInt(column)
                        if (actual != expected) {
                            throw IllegalStateException(
                                "A ECU mudou [$row,$column]: esperado $expected, encontrado $actual. Leia o mapa K novamente.",
                            )
                        }
                    }
                }
            }

            serial.unit(
                reason = "escrita direta Mapa K",
                expectedSessionId = expectedSessionId,
                workClass = Mp48WorkClass.MANUAL_WRITE,
                telemetryAfter = true,
                waitTimeoutMs = 10_000L,
            ) { unit ->
                requireAck(
                    unit.transaction(
                        Mp48Protocol.kInsertionMode(true),
                        "ativar K insertion",
                        800,
                        purgeBefore = false,
                    ),
                    "ativação K insertion",
                )
                insertionEnabled = true
                setInsertionSafetyLock(true, "K insertion ativo durante $adjustmentId")

                repeat(cells.length()) { cellIndex ->
                    val item = cells.getJSONObject(cellIndex)
                    val row = item.getInt("row")
                    val column = item.getInt("column")
                    val expected = item.getInt("current")
                    val target = item.getInt("target")
                    val workingLine = workingRows.getJSONArray(row)
                    if (workingLine.getInt(column) != expected) {
                        throw IllegalStateException("O valor confirmado [$row,$column] não é $expected")
                    }
                    val progress = 18 + ((cellIndex + 1) * 65 / cells.length())
                    update(
                        "BATCH_WRITING",
                        "Célula ${cellIndex + 1}/${cells.length()} • $expected → $target",
                        progress,
                        JSONObject().put("adjustmentId", adjustmentId)
                            .put("row", row).put("column", column).put("target", target),
                    )
                    writeStarted = true
                    requireAck(
                        unit.transaction(
                            Mp48Protocol.writeKCell(row, column, target),
                            "escrita MAP_K[$row,$column]=$target",
                            800,
                            purgeBefore = false,
                        ),
                        "escrita K",
                    )
                    workingLine.put(column, target)
                    workingRows.put(row, workingLine)
                    confirmed.put(
                        JSONObject()
                            .put("id", UUID.randomUUID().toString())
                            .put("adjustmentId", adjustmentId)
                            .put("timestamp", System.currentTimeMillis())
                            .put("row", row).put("column", column)
                            .put("axisSchema", KMapPhysicalAxes.SCHEMA)
                            .put("axisLockSha256", KMapPhysicalAxes.LOCK_SHA256)
                            .put("petrolMs", KMapPhysicalAxes.petrolBins()[row])
                            .put("rpm", KMapPhysicalAxes.rpmBins()[column])
                            .put("before", expected).put("after", target)
                            .put("reason", reason)
                            .put("acknowledged", true)
                            .put("confirmed", false)
                            .put("batchFinalized", false),
                    )
                }

                requireAck(
                    unit.transaction(
                        Mp48Protocol.kInsertionMode(false),
                        "desativar K insertion",
                        800,
                        purgeBefore = false,
                    ),
                    "saída K insertion",
                )
                insertionEnabled = false
                setInsertionSafetyLock(false, "Saída K insertion confirmada em $adjustmentId")
            }

            update("BATCH_VERIFYING_ROWS", "Confirmando somente as linhas alteradas", 88,
                JSONObject().put("adjustmentId", adjustmentId).put("rows", JSONArray(affectedRows.toList())))
            serial.unit(
                reason = "readback final Mapa K",
                expectedSessionId = expectedSessionId,
                workClass = Mp48WorkClass.READ_ONLY,
                telemetryAfter = true,
                waitTimeoutMs = 6_000L,
            ) { unit ->
                affectedRows.forEach { row ->
                    val verified = readRow(unit, row, "confirmação final K[$row]")
                    val expectedLine = workingRows.getJSONArray(row)
                    repeat(COLUMN_COUNT) { column ->
                        val actual = verified[column].toInt() and 0xFF
                        val expectedValue = expectedLine.getInt(column)
                        if (actual != expectedValue) {
                            throw IllegalStateException(
                                "Confirmação final divergente [$row,$column]: esperado $expectedValue, ECU $actual",
                            )
                        }
                    }
                }
            }

            val finalHash = canonicalFullMapHash(workingRows, extraRow)
            repeat(confirmed.length()) { index ->
                confirmed.getJSONObject(index)
                    .put("confirmed", true)
                    .put("readback", confirmed.getJSONObject(index).getInt("after"))
                    .put("batchFinalized", true)
                    .put("finalMapHash", finalHash)
            }
            appendHistoryBatch(confirmed)
            historyPersisted = true
            val now = System.currentTimeMillis()
            val allRows = JSONArray()
            repeat(ROW_COUNT) { allRows.put(JSONArray(workingRows.getJSONArray(it).toString())) }
            allRows.put(JSONArray(extraRow.toString()))
            val finalCache = JSONObject()
                .put("schema", 4)
                .put("updatedAt", now)
                .put("source", "ECU_BATCH_VERIFIED_NATIVE")
                .put("complete", true)
                .put("sessionConfirmed", true)
                .put("sessionId", expectedSessionId)
                .put("hash", finalHash)
                .put("axes", KMapPhysicalAxes.json())
                .put("rows", workingRows)
                .put("extraRow", extraRow)
                .put("allRows", allRows)
                .put("verifiedRows", JSONArray(affectedRows.toList()))
            atomicWrite(cacheFile, finalCache.toString(2))
            val payload = JSONObject()
                .put("ok", true)
                .put("calibrationType", "MAP_K")
                .put("adjustmentId", adjustmentId)
                .put("oldHash", initialHash)
                .put("newHash", finalHash)
                .put("axes", KMapPhysicalAxes.json())
                .put("rows", workingRows)
                .put("extraRow", extraRow)
                .put("verifiedRows", JSONArray(affectedRows.toList()))
                .put("cells", cells)
                .put("confirmedEvents", confirmed)
                .put("elapsedMs", System.currentTimeMillis() - startedAt)
                .put("humanConfirmed", true)
                .put("readbackValid", true)
                .put("backupId", adjustmentId)
                .put("confirmedAt", now)
            try { onConfirmedWrite() } catch (error: Exception) { log.add("WARN", "K-WRITE", "Gravação confirmada; notificação falhou: ${error.message}") }
            try { onConfirmedBatch(payload) } catch (error: Exception) {
                log.add("WARN", "K-BATCH", "Lote confirmado; notificação falhou: ${error.message}")
            }
            update("BATCH_CONFIRMED", "Alterações confirmadas por ACK e readback", 100, payload)
            log.add("INFO", "K-BATCH", "$adjustmentId confirmado • ${cells.length()} células")
        } catch (error: Throwable) {
            if (!historyPersisted && confirmed.length() > 0) {
                try { appendHistoryBatch(confirmed) } catch (_: Exception) {}
                historyPersisted = true
            }
            if (insertionEnabled) {
                try {
                    requireAck(
                        transaction(
                            Mp48Protocol.kInsertionMode(false),
                            "saída K insertion após falha",
                            800,
                            expectedSessionId,
                            Mp48WorkClass.SAFETY,
                            telemetryAfter = false,
                        ),
                        "saída K insertion após falha",
                    )
                    insertionEnabled = false
                    setInsertionSafetyLock(false, "Saída após falha confirmada")
                } catch (_: Exception) {
                    // O bloco finally realiza a última tentativa e, se necessário,
                    // persiste a trava de segurança.
                }
            }
            val recovery = JSONObject()
            if (!insertionEnabled) {
                affectedRows.forEach { row ->
                    try {
                        val verified = readRow(row, "recuperação da linha K[$row]", expectedSessionId)
                        updateCacheLine(row, verified, "ECU_PARTIAL_RECOVERY_NATIVE")
                        recovery.put(row.toString(), JSONArray(verified.map { it.toInt() and 0xFF }))
                    } catch (_: Exception) {}
                }
            }
            try {
                val stale = loadCache().put("sessionConfirmed", false)
                atomicWrite(cacheFile, stale.toString(2))
            } catch (_: Exception) {}
            update("BATCH_PARTIAL_FAILED", error.message ?: "Lote interrompido", 100,
                JSONObject().put("adjustmentId", adjustmentId)
                    .put("oldHash", initialHash)
                    .put("confirmedEvents", confirmed)
                    .put("partial", confirmed.length() > 0)
                    .put("mutationMayHaveStarted", writeStarted)
                    .put("backupId", adjustmentId)
                    .put("failureKind", FailureKind.of(error))
                    .put("recoveryRows", recovery))
            log.add("ERROR", "K-BATCH", "$adjustmentId interrompido: ${error.message}")
        } finally {
            try {
                if (insertionEnabled) {
                    try {
                        requireAck(
                            transaction(
                                Mp48Protocol.kInsertionMode(false),
                                "saída segura K insertion",
                                800,
                                expectedSessionId,
                                Mp48WorkClass.SAFETY,
                                telemetryAfter = false,
                            ),
                            "saída segura K insertion",
                        )
                        try { setInsertionSafetyLock(false, "Saída segura confirmada") } catch (failure: Exception) {
                            log.add("WARN", "K-WRITE", "Saída confirmada; trava em disco não limpa: ${failure.message}")
                        }
                    } catch (failure: Exception) {
                        // Sem ACK de saída: a trava (em memória e em disco) segura o Mapa K até o dono tocar em "Liberar".
                        insertionStateUnknown.set(true)
                        try { setInsertionSafetyLock(true, failure.message ?: "Saída K insertion não confirmada") } catch (_: Exception) {}
                        try {
                            val stale = loadCache().put("sessionConfirmed", false)
                            atomicWrite(cacheFile, stale.toString(2))
                        } catch (_: Exception) {}
                        update(
                            "SAFETY_LOCKED_INSERTION_UNKNOWN",
                            "Saída K insertion não confirmada; toque em Liberar Mapa K antes de continuar",
                            100,
                            JSONObject().put("error", failure.message ?: "Sem ACK")
                                .put("failureKind", FailureKind.of(failure)),
                        )
                    }
                }
            } finally {
                guard.release(SerialWriteGuard.OWNER_K_MAP)
                busy.set(false)
                try { onBusyChanged(false) } catch (_: Throwable) {}
                synchronized(statusLock) { status.put("busy", false) }
            }
        }
    }

    private fun <T> runSynchronous(state: String, message: String, mutating: Boolean = false, block: () -> T): T {
        if (!busy.compareAndSet(false, true)) {
            @Suppress("UNCHECKED_CAST")
            return error("Outra operação K está em andamento") as T
        }
        var guardHeld = false
        try {
            if (mutating) {
                if (!guard.tryAcquire(SerialWriteGuard.OWNER_K_MAP)) {
                    @Suppress("UNCHECKED_CAST")
                    return error("Aguarde: ${SerialWriteGuard.label(guard.holder())} em andamento") as T
                }
                guardHeld = true
            }
            onBusyChanged(true)
            update(state, message, 5)
            return block()
        } catch (failure: Exception) {
            val kind = FailureKind.of(failure)
            update("FAILED", failure.message ?: "Falha na operação K", 100, JSONObject().put("failureKind", kind))
            @Suppress("UNCHECKED_CAST")
            return error(failure.message ?: "Falha na operação K", kind) as T
        } finally {
            if (guardHeld) guard.release(SerialWriteGuard.OWNER_K_MAP)
            busy.set(false)
            try { onBusyChanged(false) } catch (_: Throwable) {}
            synchronized(statusLock) { status.put("busy", false) }
        }
    }

    private fun readRow(row: Int, reason: String, expectedSessionId: Long): ByteArray {
        require(row in 0 until TOTAL_ROW_COUNT) { "Linha K inválida: $row" }
        val reply = transaction(
            Mp48Protocol.readKRow(row),
            reason,
            800,
            expectedSessionId,
            Mp48WorkClass.READ_ONLY,
        )
        return decodeRow(reply)
    }

    private fun readRow(unit: Mp48SerialUnit, row: Int, reason: String): ByteArray {
        require(row in 0 until TOTAL_ROW_COUNT) { "Linha K inválida: $row" }
        return decodeRow(
            unit.transaction(
                Mp48Protocol.readKRow(row),
                reason,
                800,
                purgeBefore = false,
            ),
        )
    }

    private fun decodeRow(reply: UsbProtocolReply): ByteArray {
        if (!reply.ok) throw CalibrationFailure(reply.error.ifBlank { "ECU não confirmou a leitura" }, FailureKind.ofReply(reply))
        if (reply.status != Mp48Protocol.STATUS_ACK) {
            throw CalibrationFailure("Resposta inesperada 0x%02X".format(reply.status), FailureKind.ECU)
        }
        if (reply.payload.size < COLUMN_COUNT) {
            throw IllegalStateException("Linha incompleta: ${reply.payload.size}/$COLUMN_COUNT")
        }
        return reply.payload.copyOf(COLUMN_COUNT)
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

    private fun safetyError(): JSONObject = error(
        "Estado do K insertion desconhecido. Confirme a saída antes de qualquer operação do mapa K.",
    ).put("safetyLocked", true)

    private fun setInsertionSafetyLock(locked: Boolean, reason: String) {
        insertionStateUnknown.set(locked)
        atomicWrite(
            safetyFile,
            JSONObject()
                .put("insertionStateUnknown", locked)
                .put("reason", reason)
                .put("updatedAt", System.currentTimeMillis())
                .toString(2),
        )
    }

    private fun loadInsertionSafetyLock(file: File): Boolean = try {
        file.isFile && JSONObject(file.readText()).optBoolean("insertionStateUnknown", false)
    } catch (_: Exception) { true }

    private fun requireAck(reply: UsbProtocolReply, action: String) {
        if (!reply.ok || reply.status != Mp48Protocol.STATUS_ACK) {
            throw CalibrationFailure(reply.error.ifBlank { "ACK inválido em $action" }, FailureKind.ofReply(reply))
        }
    }

    private fun validCell(row: Int, column: Int): Boolean =
        row in 0 until ROW_COUNT && column in 0 until COLUMN_COUNT

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
        }
    }

    private fun createPreWriteBackup(
        adjustmentId: String,
        cache: JSONObject,
        cells: JSONArray,
        initialHash: String,
    ) {
        val backup = JSONObject()
            .put("format", "omegas-k-backup-v1")
            .put("adjustmentId", adjustmentId)
            .put("createdAt", System.currentTimeMillis())
            .put("hash", initialHash)
            .put("cells", JSONArray(cells.toString()))
            .put("map", JSONObject(cache.toString()))
        val file = File(kBackupDir, "$adjustmentId.json")
        atomicWrite(file, backup.toString(2))
        val backups = kBackupDir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        backups.drop(30).forEach { it.delete() }
        log.add("INFO", "K-BACKUP", "Backup criado antes de $adjustmentId")
    }

    private fun loadHistory(): JSONArray = try {
        if (historyFile.exists()) JSONArray(historyFile.readText()) else JSONArray()
    } catch (_: Exception) { JSONArray() }

    private fun appendHistoryBatch(items: JSONArray) {
        if (items.length() == 0) return
        val history = loadHistory()
        repeat(items.length()) { index ->
            history.put(JSONObject(items.getJSONObject(index).toString()))
        }
        val trimmed = JSONArray()
        val start = (history.length() - 4_000).coerceAtLeast(0)
        for (index in start until history.length()) trimmed.put(history.get(index))
        atomicWrite(historyFile, trimmed.toString(2))
    }

    private fun loadCache(): JSONObject = try {
        if (cacheFile.exists()) JSONObject(cacheFile.readText())
        else JSONObject().put("schema", 4).put("rows", JSONArray()).put("extraRow", JSONArray())
    } catch (_: Exception) {
        JSONObject().put("schema", 4).put("rows", JSONArray()).put("extraRow", JSONArray())
    }

    private fun updateCacheLine(row: Int, values: ByteArray, source: String) {
        val cache = loadCache()
        if (row == EXTRA_ROW) {
            cache.put("extraRow", JSONArray(values.map { it.toInt() and 0xFF }))
        } else {
            val rows = cache.optJSONArray("rows") ?: JSONArray()
            while (rows.length() < ROW_COUNT) {
                rows.put(JSONArray(List(COLUMN_COUNT) { JSONObject.NULL }))
            }
            rows.put(row, JSONArray(values.map { it.toInt() and 0xFF }))
            cache.put("rows", rows)
        }
        refreshCacheMetadata(cache, source)
    }

    private fun updateCacheCell(row: Int, column: Int, value: Int, source: String) {
        val cache = loadCache()
        val rows = cache.optJSONArray("rows") ?: JSONArray()
        while (rows.length() < ROW_COUNT) rows.put(JSONArray(List(COLUMN_COUNT) { JSONObject.NULL }))
        val line = rows.optJSONArray(row) ?: JSONArray(List(COLUMN_COUNT) { JSONObject.NULL })
        while (line.length() < COLUMN_COUNT) line.put(JSONObject.NULL)
        line.put(column, value)
        rows.put(row, line)
        cache.put("rows", rows)
        refreshCacheMetadata(cache, source)
    }

    private fun refreshCacheMetadata(cache: JSONObject, source: String) {
        val rows = cache.optJSONArray("rows") ?: JSONArray()
        val extra = cache.optJSONArray("extraRow") ?: JSONArray()
        val complete = isCompleteVisibleMap(rows) && extra.length() == COLUMN_COUNT
        cache.put("schema", 4)
            .put("updatedAt", System.currentTimeMillis())
            .put("source", source)
            .put("complete", complete)
        if (!cache.has("sessionConfirmed")) cache.put("sessionConfirmed", false)
        if (complete) cache.put("hash", canonicalFullMapHash(rows, extra)) else cache.remove("hash")
        val allRows = JSONArray()
        if (isCompleteVisibleMap(rows)) {
            repeat(ROW_COUNT) { allRows.put(JSONArray(rows.getJSONArray(it).toString())) }
            if (extra.length() == COLUMN_COUNT) allRows.put(JSONArray(extra.toString()))
        }
        cache.put("allRows", allRows)
        atomicWrite(cacheFile, cache.toString(2))
    }

    private fun isCompleteVisibleMap(rows: JSONArray): Boolean {
        if (rows.length() != ROW_COUNT) return false
        repeat(ROW_COUNT) { row ->
            val line = rows.optJSONArray(row) ?: return false
            if (line.length() != COLUMN_COUNT) return false
            repeat(COLUMN_COUNT) { column -> if (line.opt(column) !is Number) return false }
        }
        return true
    }

    private fun canonicalFullMapHash(rows: JSONArray, extraRow: JSONArray): String {
        require(isCompleteVisibleMap(rows)) { "Mapa K exige $ROW_COUNT linhas visíveis" }
        require(extraRow.length() == COLUMN_COUNT) { "Linha K 0C incompleta" }
        val bytes = ByteArray(TOTAL_ROW_COUNT * COLUMN_COUNT)
        var offset = 0
        repeat(ROW_COUNT) { row ->
            val line = rows.getJSONArray(row)
            repeat(COLUMN_COUNT) { column -> bytes[offset++] = (line.getInt(column) and 0xFF).toByte() }
        }
        repeat(COLUMN_COUNT) { column -> bytes[offset++] = (extraRow.getInt(column) and 0xFF).toByte() }
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /** Temporário + fsync + troca atômica: nunca apaga o destino antes de o novo conteúdo estar no disco. */
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
