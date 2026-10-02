package com.omegas.prohub.calibration

import java.io.File
import java.io.IOException

/**
 * Retenção local da Curva K.
 *
 * BACKUP MANUAL É POSSE DO OPERADOR: nunca é eliminado pela rotação automática.
 * Só snapshots PRE_WRITE automáticos concorrem ao limite de 30 arquivos.
 * O prefixo MANUAL- é parte do formato de arquivo emitido por saveCurrentBackup.
 */
internal object KFactorBackupRetention {
    private const val MAX_AUTOMATIC_BACKUPS = 30

    private val newestFirst = compareByDescending<File> { it.lastModified() }
        .thenByDescending { it.name }

    private fun isManual(file: File): Boolean = file.name.startsWith("MANUAL-")

    private fun jsonBackups(files: Array<File>?): List<File> =
        files?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) } ?: emptyList()

    fun visibleFiles(files: Array<File>?): List<File> {
        val all = jsonBackups(files)
        val manual = all.filter(::isManual)
        val automatic = all.filterNot(::isManual).sortedWith(newestFirst).take(MAX_AUTOMATIC_BACKUPS)
        return (manual + automatic).sortedWith(newestFirst)
    }

    fun pruneAutomatic(backupDir: File) {
        val outdated = jsonBackups(backupDir.listFiles())
            .filterNot(::isManual)
            .sortedWith(newestFirst)
            .drop(MAX_AUTOMATIC_BACKUPS)
        outdated.forEach { file ->
            if (!file.delete()) throw IOException("Não foi possível limpar backup automático antigo: ${file.name}")
        }
    }
}
