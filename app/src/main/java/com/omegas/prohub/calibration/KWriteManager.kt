package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Autoridade Android do mapa K.
 *
 * Toda operação serial passa pelo scheduler único da engine MP48. Escritas
 * manuais usam ACK rápido por comando e readback final por linha afetada.
 * Nenhuma sugestão inicia este writer.
 */
class KWriteManager(
    /** Pasta de trabalho (AppPaths.runtimeRoot no app; pasta temporária nos testes JVM). */
    private val runtimeRoot: File,
    private val serial: Mp48SerialScheduler,
    private val log: RingLog,
    @Suppress("UNUSED_PARAMETER") private val isEngineRunning: () -> Boolean,
    @Suppress("UNUSED_PARAMETER") private val stopEngine: () -> Boolean,
    @Suppress("UNUSED_PARAMETER") private val startEngine: (String) -> Boolean,
    private val onBusyChanged: (Boolean) -> Unit,
    private val onConfirmedWrite: () -> Unit = {},
    private val onConfirmedBatch: (JSONObject) -> Unit = {},
    /** Mapa K lido por inteiro pelo dono (leitura manual): vai para a sessão exportada (tipo k_read_map). */
    private val onMapRead: (JSONObject) -> Unit = {},
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
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-k-writer").apply { isDaemon = true }
    }
    private val busy = OwnedBusyLock()
    private val historyFile = File(runtimeRoot, "k_write_history.json")
    private val cacheFile = File(runtimeRoot, "k_map_cache.json")
    private val safetyFile = File(runtimeRoot, "k_write_safety.json")
    private val kBackupDir = File(runtimeRoot, "k_map_backups").apply { mkdirs() }
    private val statusLock = Any()
    private val insertionStateUnknown = AtomicBoolean(loadInsertionSafetyLock(safetyFile))
    @Volatile private var status = JSONObject()
        .put("state", "IDLE")
        .put("busy", false)
        .put("message", "Aguardando ação manual")

    fun isBusy(): Boolean = busy.isBusy()
    @Volatile private var verify = JSONObject().put("phase", KBatchVerifyCore.Phase.IDLE)
    fun writeVerifyJson(): String = synchronized(statusLock) { verify.toString() }
    private fun setVerify(phase: String, detail: String, cell: String?) = synchronized(statusLock) {
        verify = JSONObject().put("phase", phase).put("detail", detail).put("kind", "map")
            .put("label", if (phase == KBatchVerifyCore.Phase.FAILED) "não confere" else if (phase == KBatchVerifyCore.Phase.FIXING) "corrigindo" else "conferindo")
            .also { if (cell != null) it.put("cell", cell) }
    }
    fun statusJson(): String = synchronized(statusLock) { JSONObject(status.toString()).toString() }
    fun historyJson(): String = loadHistory().toString()

    @Synchronized
    fun beginUsbSession(sessionId: Long) {
        val cache = loadCache()
        cache.put("sessionConfirmed", false)
            .put("sessionStartedAt", System.currentTimeMillis())
            .put("sessionId", sessionId)
            .put("source", "PREVIOUS_SESSION")
        atomicWrite(cacheFile, cache.toString(2))
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
            return error(error.message ?: "USB desconectado")
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
            return error(error.message ?: "USB desconectado")
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

    /** Último Mapa K confirmado pela ECU (sem a cópia bruta allRows), para a linha do tempo da sessão. */
    fun cachedMapJson(): JSONObject = try {
        if (cacheFile.isFile) JSONObject(cacheFile.readText()).also { it.remove("allRows") } else JSONObject()
    } catch (_: Exception) {
        JSONObject()
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
            return error(error.message ?: "USB desconectado")
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
            try { onConfirmedWrite() } catch (_: Exception) {}
            try {
                onMapRead(
                    JSONObject().put("hash", hash).put("updatedAt", now).put("sessionId", expectedSessionId)
                        .put("axes", axes).put("rows", visibleRows).put("extraRow", extraRow),
                )
            } catch (_: Exception) {}
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

    fun recoverInsertionState(): JSONObject {
        val expectedSessionId = try { currentSessionId() } catch (error: Exception) {
            return error(error.message ?: "USB desconectado")
        }
        return runSynchronous("RECOVERING_INSERTION", "Confirmando saída do modo K insertion") {
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
            setInsertionSafetyLock(false, "Saída confirmada manualmente")
            val stale = loadCache().put("sessionConfirmed", false).put("sessionId", expectedSessionId)
            atomicWrite(cacheFile, stale.toString(2))
            update("MAP_PENDING", "Saída confirmada; releia o mapa K desta sessão", 100)
            JSONObject().put("ok", true).put("recovered", true).put("sessionId", expectedSessionId)
        }
    }

    fun startWrite(row: Int, column: Int, expectedCurrent: Int, target: Int, reason: String = "Manual"): JSONObject {
        val cells = JSONArray().put(JSONObject()
            .put("row", row).put("column", column)
            .put("current", expectedCurrent).put("target", target))
        return startBatchWrite(cells, reason)
    }

    /** Gravação direta, sem limite de passo nem pausa (dono, 16/10): só a conferência (readback) a encerra. */
    fun startBatchWrite(cells: JSONArray, reason: String = "Calibração manual"): JSONObject {
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
            if (current !in 0..255 || target !in MIN_SAFE_K..255) {
                return error("Valor K alvo deve estar entre $MIN_SAFE_K e 255")
            }
            if (current == target) return error("A célula [$row,$column] não possui alteração")
            if (!seen.add("$row:$column")) return error("Célula [$row,$column] repetida")
            normalized.put(JSONObject()
                .put("row", row).put("column", column)
                .put("current", current).put("target", target))
        }
        val token = busy.tryAcquire()
        if (token == 0L) return error("Outra operação K está em andamento")
        val expectedSessionId = try { currentSessionId() } catch (error: Exception) {
            busy.release(token)
            return error(error.message ?: "USB desconectado")
        }
        val adjustmentId = "ADJ-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        onBusyChanged(true)
        update("BATCH_QUEUED", "Alteração enfileirada entre telemetrias", 0,
            JSONObject().put("adjustmentId", adjustmentId).put("cells", normalized))
        executor.execute {
            executeBatch(adjustmentId, normalized, reason, expectedSessionId, token)
        }
        return JSONObject()
            .put("ok", true)
            .put("started", true)
            .put("adjustmentId", adjustmentId)
            .put("cells", normalized.length())
    }

    fun close() = executor.shutdownNow()

    private fun executeBatch(
        adjustmentId: String,
        cells: JSONArray,
        reason: String,
        expectedSessionId: Long,
        busyToken: Long,
    ) {
        val startedAt = System.currentTimeMillis()
        val confirmed = JSONArray()
        val affectedRows = linkedSetOf<Int>()
        var initialHash = ""
        var insertionEnabled = false
        var historyPersisted = false
        var acked = false
        var exitAttempted = false
        // Saída segura: no máximo UMA tentativa por lote (sem laço); sem ACK = trava persistida + estado legível.
        fun exitInsertionOnce(action: String) {
            if (!insertionEnabled || exitAttempted) return
            exitAttempted = true
            if (safeInsertionExit(expectedSessionId, action)) insertionEnabled = false
        }
        try {
            update("BATCH_PREPARING", "Preparando a gravação…", 3,
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
                reason = "escrita direta Mapa K",
                expectedSessionId = expectedSessionId,
                workClass = Mp48WorkClass.MANUAL_WRITE,
                telemetryAfter = true,
                // Vez na porta: se a conferência em segundo plano do bloco anterior ainda ocupa a serial, ESPERA a vez (nunca falha por "porta ocupada"):
                // a espera termina quando a unidade anterior termina (evento), o teto de 8 s só protege contra travamento real. A tela não bloqueia.
                waitTimeoutMs = 8_000L,
            ) { unit ->
                // Enviado o ativar, a central pode estar em K insertion mesmo sem resposta: marca ANTES do envio
                // para que falha, tempo esgotado ou cancelamento sempre tentem a saída segura.
                insertionEnabled = true
                setInsertionSafetyLock(true, "K insertion ativo durante $adjustmentId")
                requireAck(
                    unit.transaction(
                        Mp48Protocol.kInsertionMode(true),
                        "ativar K insertion",
                        800,
                        purgeBefore = false,
                    ),
                    "ativação K insertion",
                )

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
                    val progress = 10 + (cellIndex * 70 / cells.length())
                    update(
                        "BATCH_WRITING",
                        KBatchVerifyCore.writingMessage(cellIndex + 1, cells.length()),
                        progress,
                        JSONObject().put("adjustmentId", adjustmentId)
                            .put("row", row).put("column", column).put("target", target)
                            .put("written", cellIndex).put("total", cells.length()),
                    )
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

            // A CONFIRMAR com o dono: "Gravado" sai só com ACK, antes da leitura de volta (decisão de 2026-10-19,
            // exceção à regra "Gravado só depois do readback"). Comportamento mantido de propósito.
            // Gravar é INSTANTÂNEO (dono, 2026-10-19): todos os ACKs chegaram e a saída do K insertion foi confirmada = "Gravado".
            // A tela libera na hora; a leitura de volta (1 transação por linha, só as células alteradas) segue em segundo plano
            // e só avisa na barra do topo se falhar, depois de 1 correção autônoma (regrava só as divergentes).
            acked = true
            setVerify(KBatchVerifyCore.Phase.CHECKING, "", null)
            update("BATCH_CONFIRMED", "Gravado", 100,
                JSONObject().put("ok", true).put("calibrationType", "MAP_K").put("adjustmentId", adjustmentId)
                    .put("cells", cells).put("acked", true).put("readbackValid", false))
            releaseBusy(busyToken)

            val changedByRow = linkedMapOf<Int, MutableList<Int>>()
            repeat(cells.length()) { i ->
                val c = cells.getJSONObject(i)
                changedByRow.getOrPut(c.getInt("row")) { mutableListOf() }.add(c.getInt("column"))
            }
            val expectedRows = JSONArray(workingRows.toString())
            var retried = false
            while (true) {
                val bad = mutableListOf<Pair<Int, Int>>()
                serial.unit(
                    reason = "conferência em segundo plano Mapa K",
                    expectedSessionId = expectedSessionId,
                    workClass = Mp48WorkClass.MANUAL_WRITE,
                    telemetryAfter = true,
                    waitTimeoutMs = 3_000L,
                ) { unit ->
                    changedByRow.forEach { (row, columns) ->
                        val verified = readRow(unit, row, "conferência K[$row]")
                        val expectedLine = expectedRows.getJSONArray(row)
                        val expectedValues = IntArray(COLUMN_COUNT) { expectedLine.getInt(it) }
                        val actualValues = IntArray(COLUMN_COUNT) { verified[it].toInt() and 0xFF }
                        KBatchVerifyCore.divergent(expectedValues, actualValues, columns).forEach { bad += row to it }
                        workingRows.put(row, JSONArray(actualValues.toList())) // o cache fica com o que a ECU realmente tem
                    }
                }
                val phase = KBatchVerifyCore.nextPhase(bad.size, retried)
                if (phase == KBatchVerifyCore.Phase.OK) break
                val first = bad.first()
                val label = KBatchVerifyCore.mapCellLabel(first.first, first.second)
                if (phase == KBatchVerifyCore.Phase.FAILED) {
                    setVerify(KBatchVerifyCore.Phase.FAILED, KBatchVerifyCore.mismatchDetail(label, false), label)
                    val stale = loadCache().put("sessionConfirmed", false)
                    atomicWrite(cacheFile, stale.toString(2))
                    log.add("ERROR", "K-BATCH", "$adjustmentId conferência falhou em $label")
                    return
                }
                setVerify(KBatchVerifyCore.Phase.FIXING, KBatchVerifyCore.mismatchDetail(label, true), label)
                retried = true
                val fixes = bad.toList()
                val targetByCell = HashMap<String, Int>()
                repeat(cells.length()) { i -> val c = cells.getJSONObject(i); targetByCell["${c.getInt("row")}:${c.getInt("column")}"] = c.getInt("target") }
                serial.unit(
                    reason = "correção autônoma Mapa K",
                    expectedSessionId = expectedSessionId,
                    workClass = Mp48WorkClass.MANUAL_WRITE,
                    telemetryAfter = true,
                    waitTimeoutMs = 3_000L,
                ) { unit ->
                    // Mesma regra do lote: marcado antes de enviar o ativar; qualquer falha cai na saída segura única do catch.
                    insertionEnabled = true
                    setInsertionSafetyLock(true, "K insertion ativo na correção de $adjustmentId")
                    requireAck(unit.transaction(Mp48Protocol.kInsertionMode(true), "ativar K insertion (correção)", 800, purgeBefore = false), "ativação K insertion")
                    fixes.forEach { (row, column) ->
                        val target = targetByCell["$row:$column"] ?: return@forEach
                        requireAck(unit.transaction(Mp48Protocol.writeKCell(row, column, target), "regravar MAP_K[$row,$column]=$target", 800, purgeBefore = false), "escrita K")
                    }
                    requireAck(unit.transaction(Mp48Protocol.kInsertionMode(false), "desativar K insertion (correção)", 800, purgeBefore = false), "saída K insertion")
                    insertionEnabled = false
                    setInsertionSafetyLock(false, "Saída K insertion confirmada na correção de $adjustmentId")
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
                .put("confirmedAt", now)
            try { onConfirmedWrite() } catch (_: Exception) {}
            try { onConfirmedBatch(payload) } catch (error: Exception) {
                log.add("WARN", "K-BATCH", "Lote confirmado; notificação falhou: ${error.message}")
            }
            setVerify(KBatchVerifyCore.Phase.OK, "", null)
            log.add("INFO", "K-BATCH", "$adjustmentId confirmado • ${cells.length()} células")
        } catch (error: Exception) {
            if (!historyPersisted && confirmed.length() > 0) {
                try { appendHistoryBatch(confirmed) } catch (_: Exception) {}
                historyPersisted = true
            }
            exitInsertionOnce("saída K insertion após falha")
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
            val stale = loadCache().put("sessionConfirmed", false)
            atomicWrite(cacheFile, stale.toString(2))
            if (acked) setVerify(KBatchVerifyCore.Phase.FAILED, error.message ?: "Conferência não concluída", null)
            // Saída não confirmada: o estado SAFETY_LOCKED (com a próxima ação) fica na tela, não é sobrescrito.
            else if (!insertionEnabled) update("BATCH_PARTIAL_FAILED", error.message ?: "Lote interrompido", 100,
                JSONObject().put("adjustmentId", adjustmentId)
                    .put("oldHash", initialHash)
                    .put("confirmedEvents", confirmed)
                    .put("partial", confirmed.length() > 0)
                    .put("recoveryRows", recovery))
            log.add("ERROR", "K-BATCH", "$adjustmentId interrompido: ${error.message}")
        } finally {
            // Só chega aqui com o K insertion marcado se algo fora de Exception escapou do catch.
            exitInsertionOnce("saída segura K insertion")
            releaseBusy(busyToken)
        }
    }

    /** Uma tentativa da saída segura (o mesmo quadro de saída 0x24). Sem ACK: trava persistida e estado legível. */
    private fun safeInsertionExit(expectedSessionId: Long, action: String): Boolean {
        // Cancelamento (close) interrompe a thread: a saída ainda precisa chegar à porta; a interrupção volta depois.
        val interrupted = Thread.interrupted()
        try {
            requireAck(
                transaction(
                    Mp48Protocol.kInsertionMode(false),
                    action,
                    800,
                    expectedSessionId,
                    Mp48WorkClass.SAFETY,
                    telemetryAfter = false,
                ),
                action,
            )
            setInsertionSafetyLock(false, "Saída segura confirmada")
            return true
        } catch (error: Exception) {
            setInsertionSafetyLock(true, error.message ?: "Saída K insertion não confirmada")
            val stale = loadCache().put("sessionConfirmed", false)
            atomicWrite(cacheFile, stale.toString(2))
            update(
                "SAFETY_LOCKED_INSERTION_UNKNOWN",
                "Saída K insertion não confirmada; execute a recuperação antes de continuar",
                100,
                JSONObject().put("error", error.message ?: "Sem ACK"),
            )
            return false
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    /** Só a operação dona do "ocupado" o libera; liberar de novo (ou com o token de outra) não faz nada. */
    private fun releaseBusy(token: Long) {
        if (!busy.release(token)) return
        synchronized(statusLock) { status.put("busy", busy.isBusy()) }
        onBusyChanged(false)
    }

    private fun <T> runSynchronous(state: String, message: String, block: () -> T): T {
        val token = busy.tryAcquire()
        if (token == 0L) {
            @Suppress("UNCHECKED_CAST")
            return error("Outra operação K está em andamento") as T
        }
        onBusyChanged(true)
        update(state, message, 5)
        return try {
            block()
        } catch (error: Exception) {
            update("FAILED", error.message ?: "Falha na operação K", 100)
            @Suppress("UNCHECKED_CAST")
            error(error.message ?: "Falha na operação K") as T
        } finally {
            releaseBusy(token)
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
        if (!reply.ok) throw IllegalStateException(reply.error.ifBlank { "ECU não confirmou a leitura" })
        if (reply.status != Mp48Protocol.STATUS_ACK) {
            throw IllegalStateException("Resposta inesperada 0x%02X".format(reply.status))
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
            throw IllegalStateException(reply.error.ifBlank { "ACK inválido em $action" })
        }
    }

    private fun validCell(row: Int, column: Int): Boolean =
        row in 0 until ROW_COUNT && column in 0 until COLUMN_COUNT

    private fun error(message: String): JSONObject = JSONObject().put("ok", false).put("error", message)

    private fun update(state: String, message: String, progress: Int, details: JSONObject = JSONObject()) {
        synchronized(statusLock) {
            status = JSONObject()
                .put("state", state)
                .put("busy", busy.isBusy())
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

    private fun atomicWrite(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }
}