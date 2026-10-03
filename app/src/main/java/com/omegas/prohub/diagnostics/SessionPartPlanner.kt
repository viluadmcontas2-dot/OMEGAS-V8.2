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
 * Publicação de sessão em ZIP imutável.
 *
 * Regra normal: UMA sessão = UM ZIP (`<sessão>.zip`), publicado quando a sessão fecha ou, se o app
 * morreu no meio, na próxima abertura (recuperação). Durante a gravação nada vai para o Drive:
 * os dados ficam no armazenamento do app, descarregados no disco a cada poucos eventos.
 *
 * O mecanismo de partes continua aqui para sessões antigas que já têm partes publicadas: cada parte
 * leva só os bytes novos dos `events_*.jsonl` (até a última linha completa) e os arquivos pequenos
 * que mudaram, e uma parte publicada nunca é reescrita (sem duplicata no Drive). Puro: sem Android.
 */
object SessionPartPlanner {
    const val STATE_FILE = ".public_parts.json"
    const val FORMAT = "omegas-session-part-v1"

    data class Slice(val name: String, val file: File, val from: Long, val to: Long)

    class Plan(
        val part: Int,
        val slices: List<Slice>,
        val files: List<File>,
        val final: Boolean,
        internal val nextState: JSONObject,
    ) {
        /** Primeira publicação já final: é a sessão inteira, vira um ZIP só com nomes simples. */
        val single: Boolean get() = part == 1 && final
    }

    fun partName(sessionId: String, part: Int): String =
        DocumentsSessionMirror.safeName(sessionId) + "_parte_" + part.toString().padStart(4, '0') + ".zip"

    /** Nome do arquivo publicado: `<sessão>.zip` quando é a sessão inteira, `_parte_NNNN` nas demais. */
    fun fileName(sessionId: String, plan: Plan): String =
        if (plan.single) DocumentsSessionMirror.safeName(sessionId) + ".zip" else partName(sessionId, plan.part)

    private fun readState(dir: File): JSONObject = try {
        File(dir, STATE_FILE).takeIf { it.isFile }?.let { JSONObject(it.readText()) } ?: JSONObject()
    } catch (_: Exception) {
        JSONObject()
    }

    /** Posição logo após o último '\n' (linha cortada por queda de energia fica para depois). */
    fun completeLength(file: File): Long {
        val length = file.length()
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

    private fun isEvents(file: File) = file.name.startsWith("events_") && file.name.endsWith(".jsonl")

    /** O que entraria na próxima parte; null quando não há nada novo. */
    fun plan(dir: File, final: Boolean): Plan? {
        if (!dir.isDirectory) return null
        val state = readState(dir)
        val offsets = state.optJSONObject("offsets") ?: JSONObject()
        val meta = state.optJSONObject("meta") ?: JSONObject()
        val nextOffsets = JSONObject(offsets.toString())
        val nextMeta = JSONObject(meta.toString())
        val slices = ArrayList<Slice>()
        val files = ArrayList<File>()
        DocumentsSessionMirror.sessionFiles(dir).forEach { file ->
            if (isEvents(file)) {
                val from = offsets.optLong(file.name, 0L)
                val to = completeLength(file)
                if (to > from) {
                    slices += Slice(file.name, file, from, to)
                    nextOffsets.put(file.name, to)
                }
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
        return Plan(part, slices, files, final, next)
    }

    /** ZIP da parte: `<sessão>/events_0001.from_<byte>.jsonl`, arquivos mudados e `parte.json`. */
    fun writeZip(plan: Plan, sessionId: String, output: OutputStream): Int {
        val prefix = DocumentsSessionMirror.safeName(sessionId)
        val listing = JSONArray()
        ZipOutputStream(output.buffered()).use { zip ->
            plan.slices.forEach { slice ->
                val entryName = if (plan.single) slice.name else slice.name.removeSuffix(".jsonl") + ".from_" + slice.from + ".jsonl"
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
                listing.put(JSONObject().put("entry", entryName).put("source", slice.name)
                    .put("fromByte", slice.from).put("toByte", slice.to)
                    .put("sha256", digest.digest().joinToString("") { "%02x".format(it) }))
            }
            plan.files.forEach { file ->
                zip.putNextEntry(ZipEntry("$prefix/${file.name}"))
                file.inputStream().use { it.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
                listing.put(JSONObject().put("entry", file.name).put("source", file.name).put("whole", true))
            }
            zip.putNextEntry(ZipEntry("$prefix/parte.json"))
            zip.write(JSONObject().put("format", FORMAT).put("sessionId", sessionId).put("part", plan.part)
                .put("final", plan.final).put("single", plan.single).put("createdAtMs", System.currentTimeMillis())
                .put("howToJoin", if (plan.single) "Sessão inteira neste ZIP: nada a juntar."
                    else "Concatene os events_NNNN.from_*.jsonl de todas as partes em ordem de byte.")
                .put("entries", listing).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return plan.slices.size + plan.files.size
    }

    /** Só depois da parte publicada: avança os offsets (escrita atômica). */
    fun commit(dir: File, plan: Plan) {
        val target = File(dir, STATE_FILE)
        val tmp = File(dir, "$STATE_FILE.tmp")
        tmp.writeText(plan.nextState.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(target)) { target.writeText(plan.nextState.toString(), Charsets.UTF_8); tmp.delete() }
    }
}
