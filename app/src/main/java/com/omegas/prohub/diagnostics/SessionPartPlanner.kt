package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
 * que mudaram. Puro: sem Android.
 *
 * Um nome publicado nunca é reescrito: antes de escrever, o número da parte é reservado num marcador
 * vazio (`.reserved_part_NNNN`, sobrevive a estado corrompido porque é só o nome). Se o app morrer
 * entre publicar e registrar, a nova tentativa usa o PRÓXIMO número e repete a faixa desde o último
 * registro (bytes idênticos; o `parte.json` diz quais partes foram substituídas). O estado é gravado
 * de forma atômica (temporário + fsync + rename) e, se mesmo assim estiver ilegível, a próxima parte
 * leva tudo desde o byte 0 com número acima de qualquer reserva: duplica, nunca perde nem volta à 1.
 */
object SessionPartPlanner {
    const val STATE_FILE = ".public_parts.json"
    const val FORMAT = "omegas-session-part-v1"
    private const val RESERVATION_PREFIX = ".reserved_part_"

    data class Slice(val name: String, val file: File, val from: Long, val to: Long)

    class Plan(
        part: Int,
        val slices: List<Slice>,
        val files: List<File>,
        val final: Boolean,
        internal val dir: File,
        internal val nextOffsets: JSONObject,
        internal val nextMeta: JSONObject,
        /** Partes registradas antes desta (a ordem de junção). */
        val committedBefore: List<Int>,
        supersededParts: List<Int>,
        /** O estado estava ilegível: esta parte recomeça do byte 0 de cada arquivo. */
        val stateRecovered: Boolean,
    ) {
        var part: Int = part
            internal set

        /** Números reservados e nunca registrados (podem ter saído; a faixa deles está repetida aqui). */
        var supersededParts: List<Int> = supersededParts
            internal set

        /** Primeira publicação já final: é a sessão inteira, vira um ZIP só com nomes simples. */
        val single: Boolean get() = part == 1 && final
    }

    fun partName(sessionId: String, part: Int): String =
        DocumentsSessionMirror.safeName(sessionId) + "_parte_" + part.toString().padStart(4, '0') + ".zip"

    /** Nome do arquivo publicado: `<sessão>.zip` quando é a sessão inteira, `_parte_NNNN` nas demais. */
    fun fileName(sessionId: String, plan: Plan): String =
        if (plan.single) DocumentsSessionMirror.safeName(sessionId) + ".zip" else partName(sessionId, plan.part)

    private class StateRead(val state: JSONObject, val corrupted: Boolean)

    /**
     * Estado registrado. Um temporário completo que não chegou ao rename (queda no meio do registro)
     * vale mais que o principal: ele foi escrito depois de a parte ter saído.
     */
    private fun readState(dir: File): StateRead {
        val main = File(dir, STATE_FILE)
        val tmp = File(dir, "$STATE_FILE.tmp")
        parseState(tmp)?.let { return StateRead(it, corrupted = false) }
        if (!main.exists()) return StateRead(JSONObject(), corrupted = false)
        return parseState(main)?.let { StateRead(it, corrupted = false) } ?: StateRead(JSONObject(), corrupted = true)
    }

    private fun parseState(file: File): JSONObject? = try {
        if (file.isFile) JSONObject(file.readText(Charsets.UTF_8)).takeIf { it.has("nextPart") } else null
    } catch (_: Exception) {
        null
    }

    private fun reservationFile(dir: File, part: Int) =
        File(dir, RESERVATION_PREFIX + part.toString().padStart(4, '0'))

    /** Números de parte já reservados (publicados ou tentados) nesta sessão. */
    fun reservedParts(dir: File): List<Int> = dir.listFiles()
        ?.mapNotNull { file -> file.name.takeIf { it.startsWith(RESERVATION_PREFIX) }?.removePrefix(RESERVATION_PREFIX)?.toIntOrNull() }
        ?.sorted()
        .orEmpty()

    private fun intList(array: JSONArray?): List<Int> =
        if (array == null) emptyList() else List(array.length()) { array.optInt(it) }.filter { it > 0 }

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
        val read = readState(dir)
        val state = read.state
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
        val stateNext = state.optInt("nextPart", 1).coerceAtLeast(1)
        // Estado antigo (sem a lista): as partes 1..nextPart-1 foram registradas em ordem.
        val committed = state.optJSONArray("committedParts")?.let(::intList)
            ?: (1 until stateNext).toList()
        val reserved = reservedParts(dir)
        val part = maxOf(stateNext, (reserved.maxOrNull() ?: 0) + 1)
        val superseded = reserved.filter { it !in committed && it < part }
        return Plan(
            part = part,
            slices = slices,
            files = files,
            final = final,
            dir = dir,
            nextOffsets = nextOffsets,
            nextMeta = nextMeta,
            committedBefore = if (read.corrupted) emptyList() else committed,
            supersededParts = superseded,
            stateRecovered = read.corrupted,
        )
    }

    /**
     * Reserva o número da parte ANTES de escrever. Número já reservado ou nome já existente no destino
     * ([isTaken] recebe o plano já com o número candidato, ex.: `fileName(id, it)` no MediaStore) nunca é
     * reaproveitado: a parte sobe para o próximo livre.
     */
    fun reserve(plan: Plan, isTaken: (Plan) -> Boolean) {
        val first = plan.part
        val skipped = ArrayList<Int>()
        var part = first
        while (true) {
            require(part < first + 10_000) { "Nenhum número de parte livre" }
            plan.part = part
            val marker = reservationFile(plan.dir, part)
            if (isTaken(plan)) {
                // Nome já existe no destino sem reserva local (estado perdido): marca para não testar de novo.
                marker.createNewFile()
                skipped += part
            } else if (marker.createNewFile()) {
                break
            }
            part += 1
        }
        if (skipped.isNotEmpty()) plan.supersededParts = (plan.supersededParts + skipped).distinct().sorted()
    }

    /** Estado depois de [plan] registrada (também usado no teste de queda entre fsync e rename). */
    fun stateAfter(plan: Plan): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("nextPart", plan.part + 1)
        .put("offsets", plan.nextOffsets)
        .put("meta", plan.nextMeta)
        .put("committedParts", JSONArray((plan.committedBefore + plan.part).distinct().sorted()))

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
                .put("committedPartsBefore", JSONArray(plan.committedBefore))
                .put("supersededParts", JSONArray(plan.supersededParts))
                .put("expectedParts", if (plan.final) JSONArray((plan.committedBefore + plan.part).distinct().sorted()) else JSONObject.NULL)
                .put("cumulativeToByte", plan.nextOffsets)
                .put("stateRecovered", plan.stateRecovered)
                .put("howToJoin", if (plan.single) "Sessão inteira neste ZIP: nada a juntar."
                    else "Para cada events_NNNN, grave cada trecho .from_<byte> na posição fromByte. " +
                        "expectedParts (na parte final) lista todas as partes necessárias; falta de uma delas é buraco. " +
                        "supersededParts podem ter saído antes de uma queda: repetem faixas já cobertas, com bytes idênticos. " +
                        "cumulativeToByte é o tamanho de cada arquivo coberto até esta parte.")
                .put("entries", listing).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return plan.slices.size + plan.files.size
    }

    /** Só depois da parte publicada: avança os offsets (temporário + fsync + rename atômico). */
    fun commit(dir: File, plan: Plan) {
        val target = File(dir, STATE_FILE)
        val tmp = File(dir, "$STATE_FILE.tmp")
        FileOutputStream(tmp).use { output ->
            output.write(stateAfter(plan).toString().toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // Sem rename atômico: o temporário completo continua valendo para readState até o próximo registro.
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
