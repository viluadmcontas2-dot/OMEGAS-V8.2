package com.omegas.prohub.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gerencia somente dados do aplicativo.
 *
 * Não conhece projeto, script ou engine substituível. Na primeira abertura,
 * qualquer conteúdo antigo do diretório de projeto é movido para quarentena.
 */
class DataArchiveManager(
    private val paths: AppPaths,
    private val log: RingLog,
) {
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context, paths: AppPaths, log: RingLog) : this(paths, log)

    companion object {
        private const val CORE_IDENTITY = "OMEGAS_ANDROID_NATIVE_CORE_V3"
    }

    init {
        sanitizeLegacyProjects()
    }

    fun exportData(resolver: ContentResolver, uri: Uri) {
        resolver.openOutputStream(uri, "w").use { output ->
            requireNotNull(output) { "Não foi possível criar o arquivo" }
            ZipOutputStream(output.buffered()).use { zip ->
                zipDirectory(paths.externalRoot, paths.externalRoot, zip)
            }
        }
    }

    fun exportLogs(resolver: ContentResolver, uri: Uri, text: String) {
        resolver.openOutputStream(uri, "w").use { output ->
            requireNotNull(output) { "Não foi possível criar o arquivo" }
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    fun identitySha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(CORE_IDENTITY.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun statusJson(): JSONObject = JSONObject()
        .put("core", CORE_IDENTITY)
        .put("native", true)
        .put("editable", false)
        .put("legacyQuarantine", paths.quarantineRoot.absolutePath)
        .put("identitySha256", identitySha256())

    private fun sanitizeLegacyProjects() {
        val active = paths.activeProject
        val legacy = active.listFiles().orEmpty()
        if (legacy.isEmpty()) return
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val quarantine = File(paths.quarantineRoot, "legacy_engine_$stamp").apply { mkdirs() }
        legacy.forEach { source ->
            val target = File(quarantine, source.name)
            val moved = source.renameTo(target)
            if (!moved) {
                if (source.isDirectory) source.copyRecursively(target, overwrite = true)
                else source.copyTo(target, overwrite = true)
                source.deleteRecursively()
            }
        }
        log.add("WARN", "SANITIZE", "Engine antiga movida para quarentena: ${quarantine.name}")
    }

    private fun zipDirectory(root: File, current: File, zip: ZipOutputStream) {
        current.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.isDirectory) {
                zipDirectory(root, file, zip)
            } else {
                val relative = file.relativeTo(root).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(relative))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

