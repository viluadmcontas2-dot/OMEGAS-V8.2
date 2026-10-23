package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Sessão publicada em PARTES imutáveis: cada parte leva só os bytes novos dos
 * `events_*.jsonl` (até a última linha completa) e os arquivos pequenos que mudaram.
 * Uma parte publicada nunca é reescrita (sem duplicata no Drive); um corte de energia
 * perde no máximo o que veio depois da última parte. Puro: sem Android.
 */
object SessionPartPlanner {
    const val STATE_FILE = ".public_parts.json"
    const val FORMAT = "omegas-session-part-v1"

    /**
     * Teto de bytes BRUTOS de eventos por parte (dono, 2026-10-23: o download pelo Drive quebra acima de ~8 MB). O JSON dos
     * eventos comprime 8x ou mais (corpus real: 15x), então 16 MiB brutos saem em ZIP bem abaixo de 4 MB.
     */
    const val PART_MAX_RAW_BYTES = 16L * 1024L * 1024L

    data class Slice(val name: String, val file: File, val from: Long, val to: Long)

    class Plan(
        val part: Int,
        val slices: List<Slice>,
        val files: List<File>,
        val final: Boolean,
        internal val nextState: JSONObject,
        /** Sessão inteira num ZIP só (dono, 2026-10-13: "49 partes é ridículo"): primeira e última publicação ao mesmo tempo. */
        val single: Boolean = false,
        /** Ficou evento por publicar (teto de bytes): a parte não é a última. */
        val capped: Boolean = false,
    ) {
        fun fileName(sessionId: String): String = if (single) singleName(sessionId) else partName(sessionId, part)
    }

    fun partName(sessionId: String, part: Int): String =
        PrivateSessionArchive.safeName(sessionId) + "_parte_" + part.toString().padStart(4, '0') + ".zip"

    fun singleName(sessionId: String): String = PrivateSessionArchive.safeName(sessionId) + ".zip"

    private fun readState(dir: File): JSONObject = try {
        File(dir, STATE_FILE).takeIf { it.isFile }?.let { JSONObject(it.readText()) } ?: JSONObject()
    } catch (_: Exception) {
        JSONObject()
    }

    /** Posição logo após o último '\n' (linha cortada por queda de energia fica para depois). */
    fun completeLength(file: File): Long = completeLength(file, file.length())

    /** Como [completeLength], mas só olhando os primeiros [upTo] bytes. */
    fun completeLength(file: File, upTo: Long): Long {
        val length = minOf(file.length(), upTo)
        if (length == 0L) return 0L
        RandomAccessFile(file, "r").use { raf ->
            var pos = length
            val buffer = ByteArray(4096)
            while (pos > 0) {
                val start = maxOf(0L, pos - buffer.size)
                val count = (pos - start).toInt()
                raf.seek(start)
                raf.readFully(buffer, 0, count)
                for (i in count - 1 downTo 0) if (buffer[i] == '\n'.code.toByte()) return start + i + 1
                pos = start
            }
        }
        return 0L
    }

    /** Posição logo após o primeiro '\n' a partir de [pos] (no máximo [limit]). */
    private fun nextLineEnd(file: File, pos: Long, limit: Long): Long {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(pos)
            var at = pos
            while (at < limit) {
                if (raf.read() == '\n'.code) return at + 1
                at += 1
            }
        }
        return limit
    }

    private fun isEvents(file: File) = file.name.startsWith("events_") && file.name.endsWith(".jsonl")

    /** O que entraria na próxima parte (até [maxRawBytes] brutos de eventos); null quando não há nada novo. */
    fun plan(dir: File, final: Boolean, maxRawBytes: Long = PART_MAX_RAW_BYTES): Plan? {
        if (!dir.isDirectory) return null
        val state = readState(dir)
        val offsets = state.optJSONObject("offsets") ?: JSONObject()
        val meta = state.optJSONObject("meta") ?: JSONObject()
        val nextOffsets = JSONObject(offsets.toString())
        val nextMeta = JSONObject(meta.toString())
        val slices = ArrayList<Slice>()
        val files = ArrayList<File>()
        var budget = maxRawBytes
        var capped = false
        PrivateSessionArchive.sessionFiles(dir).forEach { file ->
            if (isEvents(file)) {
                val from = offsets.optLong(file.name, 0L)
                val complete = completeLength(file)
                if (complete <= from) return@forEach
                if (budget <= 0L) { capped = true; return@forEach }
                var to = complete
                if (to - from > budget) {
                    // Corta na última linha inteira dentro do teto; linha maior que o teto vai sozinha (nunca divide linha).
                    to = completeLength(file, from + budget).takeIf { it > from } ?: nextLineEnd(file, from + budget, complete)
                    capped = to < complete
                }
                slices += Slice(file.name, file, from, to)
                nextOffsets.put(file.name, to)
                budget -= (to - from)
            } else {
                val signature = "${file.length()}:${file.lastModified()}"
                if (meta.optString(file.name) != signature) {
                    files += file
                    nextMeta.put(file.name, signature)
                }
            }
        }
        if (slices.isEmpty() && files.isEmpty()) return null
        val part = state.optInt("nextPart", 1)
        val next = JSONObject().put("format", FORMAT).put("nextPart", part + 1)
            .put("offsets", nextOffsets).put("meta", nextMeta)
        // Nada foi publicado ainda e a sessão fechou (ou foi recuperada depois de queda): um ZIP só, com tudo.
        return Plan(part, slices, files, final && !capped, next, single = final && !capped && part == 1 && !state.has("offsets"), capped = capped)
    }

    /** ZIP da parte: `<sessão>/events_0001.from_<byte>.jsonl`, arquivos mudados e `parte.json`. */
    fun writeZip(plan: Plan, sessionId: String, output: OutputStream): Int {
        val prefix = PrivateSessionArchive.safeName(sessionId)
        val listing = JSONArray()
        val identity = identityOf(plan, sessionId)
        val contentDigest = MessageDigest.getInstance("SHA-256")
        ZipOutputStream(output.buffered()).use { zip ->
            plan.slices.forEach { slice ->
                val entryName = if (plan.single && slice.from == 0L) slice.name else slice.name.removeSuffix(".jsonl") + ".from_" + slice.from + ".jsonl"
                zip.putNextEntry(ZipEntry("$prefix/$entryName"))
                val digest = MessageDigest.getInstance("SHA-256")
                RandomAccessFile(slice.file, "r").use { raf ->
                    raf.seek(slice.from)
                    var remaining = slice.to - slice.from
                    val buffer = ByteArray(64 * 1024)
                    while (remaining > 0) {
                        val count = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count < 0) break
                        zip.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        remaining -= count
                    }
                }
                zip.closeEntry()
                val sliceSha = digest.digest().joinToString("") { "%02x".format(it) }
                contentDigest.update("${slice.name}:${slice.from}-${slice.to}:$sliceSha\n".toByteArray())
                listing.put(JSONObject().put("entry", entryName).put("source", slice.name)
                    .put("fromByte", slice.from).put("toByte", slice.to)
                    .put("sha256", sliceSha))
            }
            plan.files.forEach { file ->
                zip.putNextEntry(ZipEntry("$prefix/${file.name}"))
                file.inputStream().use { it.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
                val wholeSha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                contentDigest.update("${file.name}:$wholeSha\n".toByteArray())
                listing.put(JSONObject().put("entry", file.name).put("source", file.name).put("whole", true).put("sha256", wholeSha))
            }
            zip.putNextEntry(ZipEntry("$prefix/parte.json"))
            zip.write(JSONObject().put("format", FORMAT).put("sessionId", sessionId).put("part", plan.part)
                .put("final", plan.final).put("createdAtMs", System.currentTimeMillis())
                // Identidade estável (dor 1): a mesma sessão em várias cópias/zips se reconhece por uuid + início + índice.
                .put("sessionUuid", identity.first).put("startMs", identity.second ?: JSONObject.NULL)
                .put("partIndex", plan.part).put("isLast", plan.final)
                .put("totalParts", if (plan.final) plan.part else JSONObject.NULL)
                .put("rawBytes", plan.slices.sumOf { it.to - it.from })
                .put("contentSha256", contentDigest.digest().joinToString("") { "%02x".format(it) })
                .put("single", plan.single)
                .put("howToJoin", if (plan.single) "Sessão completa neste ZIP: events_NNNN.jsonl inteiros." else "Concatene os events_NNNN.from_*.jsonl de todas as partes em ordem de byte.")
                .put("entries", listing).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return plan.slices.size + plan.files.size
    }

    /** UUID e início da sessão a partir do `manifest.json` da pasta (UUID derivado do nome quando a sessão é antiga). */
    private fun identityOf(plan: Plan, sessionId: String): Pair<String, Long?> {
        val dir = (plan.slices.firstOrNull()?.file ?: plan.files.firstOrNull())?.let { f ->
            generateSequence(f.parentFile) { it.parentFile }.firstOrNull { File(it, "manifest.json").isFile }
        }
        val m = try { dir?.let { JSONObject(File(it, "manifest.json").readText()) } } catch (_: Exception) { null }
        val uuid = m?.optString("sessionUuid").orEmpty().ifBlank { java.util.UUID.nameUUIDFromBytes(sessionId.toByteArray()).toString() }
        val start = m?.let { if (it.has("createdAtMs")) it.optLong("createdAtMs") else null }
        return uuid to start
    }

    /** Só depois da parte publicada: avança os offsets (escrita atômica). */
    fun commit(dir: File, plan: Plan) {
        val target = File(dir, STATE_FILE)
        val tmp = File(dir, "$STATE_FILE.tmp")
        tmp.writeText(plan.nextState.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(target)) { target.writeText(plan.nextState.toString(), Charsets.UTF_8); tmp.delete() }
    }
}
