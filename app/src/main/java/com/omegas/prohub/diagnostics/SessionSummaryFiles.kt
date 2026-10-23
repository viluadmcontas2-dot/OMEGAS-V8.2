package com.omegas.prohub.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Arquivos do resumo leve em `<sessão>/resumo/`: `session_summary.json`, `series_1hz.csv` e `manifest.json`
 * (IDs, SHA-256, tamanhos, brutos). Pasta própria: o `session_summary.json` da raiz é o resumo semântico antigo
 * (`omegas-session-semantic-v1`) e o `manifest.json` da raiz é o do log (`omegas-session-log-v1`); nenhum dos dois muda.
 * Escrita atômica (temporário + troca) com fsync: o aparelho morre quando o carro é desligado.
 */
object SessionSummaryFiles {
    const val DIR = "resumo"
    const val SUMMARY = "session_summary.json"
    const val SERIES = SessionSummaryBuilder.SERIES_FILE
    const val MANIFEST = "manifest.json"
    const val MANIFEST_SCHEMA = "omegas-session-summary-manifest-v1"
    const val LEDGER_NAME = "tank_ledger.json"

    /** Escreve série, resumo e (por último) o manifesto, que descreve exatamente os dois primeiros. */
    @JvmStatic
    fun write(sessionDir: File, builder: SessionSummaryBuilder, final: Boolean, hashRaw: Boolean = false): JSONObject {
        val out = File(sessionDir, DIR).apply { mkdirs() }
        val summary = builder.summary(final)
        val series = builder.seriesCsv().toByteArray(Charsets.UTF_8)
        val summaryBytes = summary.toString(2).toByteArray(Charsets.UTF_8)
        atomicWrite(File(out, SERIES), series)
        atomicWrite(File(out, SUMMARY), summaryBytes)
        val files = JSONArray()
            .put(fileEntry(SERIES, series))
            .put(fileEntry(SUMMARY, summaryBytes))
        val raw = JSONArray()
        rawFiles(sessionDir).forEach { f ->
            raw.put(
                JSONObject().put("name", f.name).put("bytes", f.length())
                    .put("sha256", if (hashRaw) sha256(f) else JSONObject.NULL),
            )
        }
        val identity = summary.getJSONObject("identity")
        // Fluxos brutos v2 (raw/*.tsv): colunas CONSTANTES no manifesto (não se repetem por parte), arquivos, bytes e SHA-256.
        val streams = JSONObject()
        rawStreamFiles(sessionDir).forEach { f ->
            val stream = f.name.substringBefore('.')
            val entry = streams.optJSONObject(stream) ?: JSONObject().put("columns", RawStreams.COLUMNS[stream] ?: JSONObject.NULL)
                .put("files", JSONArray()).also { streams.put(stream, it) }
            entry.getJSONArray("files").put(
                JSONObject().put("name", "raw/" + f.name).put("bytes", f.length()).put("sha256", if (hashRaw) sha256(f) else JSONObject.NULL),
            )
        }
        val manifest = JSONObject()
            .put("schema", MANIFEST_SCHEMA)
            .put("sessionId", identity.getString("sessionId"))
            .put("sessionUuid", identity.getString("sessionUuid"))
            .put("startMs", identity.opt("startMs") ?: JSONObject.NULL)
            .put("sessionKey", identity.opt("sessionKey") ?: JSONObject.NULL)
            .put("bootId", identity.opt("bootId") ?: JSONObject.NULL)
            .put("final", final)
            .put("closedCleanly", summary.opt("closedCleanly") ?: JSONObject.NULL)
            .put("summarySchema", SessionSummaryBuilder.SCHEMA)
            .put("appVersion", summary.getJSONObject("build").opt("appVersion") ?: JSONObject.NULL)
            .put("commit", summary.getJSONObject("build").opt("commit") ?: JSONObject.NULL)
            .put("clockSuspect", summary.getJSONObject("clock").optBoolean("clockSuspect"))
            .put("files", files)
            .put("rawFiles", raw)
            .put("rawFormat", RawStreams.FORMAT)
            .put("streams", streams)
            .put("packageSha256", Totals.sha256((0 until files.length()).joinToString("\n") {
                files.getJSONObject(it).let { e -> e.getString("name") + ":" + e.getString("sha256") }
            }))
        atomicWrite(File(out, MANIFEST), manifest.toString(2).toByteArray(Charsets.UTF_8))
        return manifest
    }

    /** Refaz o resumo lendo os `events_*.jsonl` (sessão interrompida por queda de energia). Linha cortada ou lixo é ignorada. */
    @JvmStatic
    fun rebuild(sessionDir: File, context: SummaryContext? = null): SessionSummaryBuilder {
        val ctx = context ?: contextFrom(sessionDir)
        val builder = SessionSummaryBuilder(ctx)
        rawFiles(sessionDir).forEach { f ->
            f.useLines(Charsets.UTF_8) { lines ->
                lines.forEach { line ->
                    if (line.isBlank() || !line.startsWith("{")) return@forEach
                    try {
                        val e = JSONObject(line)
                        builder.observe(
                            e.optLong("sequence"), e.optString("type"), e.optString("source"),
                            e.optJSONObject("data") ?: JSONObject(), e.optLong("recordedAtMs"),
                        )
                    } catch (_: Exception) {
                        // linha cortada na queda de energia
                    }
                }
            }
        }
        return builder
    }

    /** Identidade da sessão a partir do `manifest.json` do log; sem UUID gravado, deriva um estável do nome. */
    @JvmStatic
    fun contextFrom(sessionDir: File): SummaryContext {
        val m = try { JSONObject(File(sessionDir, "manifest.json").readText(Charsets.UTF_8)) } catch (_: Exception) { JSONObject() }
        val id = m.optString("sessionId", sessionDir.name).ifBlank { sessionDir.name }
        val meta = m.optJSONObject("metadata") ?: JSONObject()
        return SummaryContext(
            sessionId = id,
            sessionUuid = m.optString("sessionUuid").ifBlank { UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8)).toString() },
            startMs = m.optLong("createdAtMs", m.optLong("startedAtMs", 0L)),
            sessionKey = m.optString("sessionKey").takeIf { it.isNotBlank() },
            bootId = m.optString("bootId").takeIf { it.isNotBlank() },
            startElapsedMs = if (m.has("startElapsedMs") && !m.isNull("startElapsedMs")) m.optLong("startElapsedMs") else null,
            buildType = meta.optString("buildType").takeIf { it.isNotBlank() },
            appVersion = meta.optString("appVersion").takeIf { it.isNotBlank() },
            commit = meta.optString("commit").takeIf { it.isNotBlank() },
            deviceId = m.optString("deviceId").takeIf { it.isNotBlank() },
            carProfile = meta.optString("carProfile").takeIf { it.isNotBlank() },
            vin = meta.optString("vin").takeIf { it.isNotBlank() },
            mapHashAtStart = meta.optString("mapHashAtStart").takeIf { it.isNotBlank() },
            curveHashAtStart = meta.optString("curveHashAtStart").takeIf { it.isNotBlank() },
            previousGnvEndRaw = if (meta.has("previousGnvEndRaw") && !meta.isNull("previousGnvEndRaw")) meta.optInt("previousGnvEndRaw") else null,
        )
    }

    @JvmStatic
    fun isFinal(sessionDir: File): Boolean = try {
        JSONObject(File(File(sessionDir, DIR), MANIFEST).readText(Charsets.UTF_8)).optBoolean("final", false)
    } catch (_: Exception) { false }

    @JvmStatic
    fun hasSummary(sessionDir: File): Boolean = File(File(sessionDir, DIR), SUMMARY).isFile

    /** Pacote leve (resumo + série + manifesto + livro-caixa), separado do bruto. Devolve os bytes escritos. */
    @JvmStatic
    fun packageZip(sessionDir: File, ledger: File?, output: OutputStream): Long {
        val counting = CountingOutputStream(output)
        val prefix = PrivateSessionArchive.safeName(sessionDir.name)
        ZipOutputStream(counting).use { zip ->
            listOf(SUMMARY, SERIES, MANIFEST).forEach { name ->
                val f = File(File(sessionDir, DIR), name)
                if (!f.isFile) return@forEach
                zip.putNextEntry(ZipEntry("$prefix/$name"))
                f.inputStream().use { it.copyTo(zip, 32 * 1024) }
                zip.closeEntry()
            }
            if (ledger != null && ledger.isFile) {
                zip.putNextEntry(ZipEntry(LEDGER_NAME))
                ledger.inputStream().use { it.copyTo(zip, 32 * 1024) }
                zip.closeEntry()
            }
        }
        return counting.count
    }

    /** Tamanho do pacote leve (null se ainda não há resumo): mostrado no botão "Exportar resumo". */
    @JvmStatic
    fun packageSize(sessionDir: File, ledger: File?): Long? {
        if (!hasSummary(sessionDir)) return null
        return packageZip(sessionDir, ledger, ByteArrayOutputStream())
    }

    private fun rawStreamFiles(dir: File): List<File> =
        File(dir, "raw").listFiles { f -> f.isFile && f.name.endsWith(".tsv") }?.sortedBy { it.name } ?: emptyList()

    /** Nome do pacote leve na pasta espelho (sai ao lado das partes brutas, pequeno, para o download pelo Drive). */
    @JvmStatic
    fun packageName(sessionId: String): String = PrivateSessionArchive.safeName(sessionId) + "_resumo.zip"

    private fun rawFiles(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith("events_") && f.name.endsWith(".jsonl") }?.sortedBy { it.name } ?: emptyList()

    private fun fileEntry(name: String, bytes: ByteArray) =
        JSONObject().put("name", name).put("bytes", bytes.size).put("sha256", Totals.sha256(bytes))

    private fun sha256(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; d.update(buf, 0, n) }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    @JvmStatic
    internal fun atomicWrite(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, "." + target.name + ".tmp")
        FileOutputStream(tmp, false).use { o -> o.write(bytes); o.flush(); try { o.fd.sync() } catch (_: Exception) { /* fsync best-effort: o arquivo já foi escrito */ } }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private class CountingOutputStream(private val inner: OutputStream) : OutputStream() {
        var count = 0L
        override fun write(b: Int) { inner.write(b); count += 1 }
        override fun write(b: ByteArray, off: Int, len: Int) { inner.write(b, off, len); count += len }
        override fun flush() = inner.flush()
        override fun close() = inner.close()
    }
}
