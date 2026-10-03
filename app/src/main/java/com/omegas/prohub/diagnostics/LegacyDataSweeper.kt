package com.omegas.prohub.diagnostics

import java.io.File

/**
 * Limpeza única dos arquivos do aprendizado antigo (cérebro 2, sync de aprendizado e sessões V7)
 * deixados por APKs anteriores. Apaga só nomes desta lista; nunca toca os arquivos vivos do
 * Refino/Equivalência, caches K, recibos AutoCal, sessões, backups de curva nem preferências.
 */
object LegacyDataSweeper {
    val RUNTIME_FILES: List<String> = listOf(
        "native_learning_state_mp48_v4.json", "native_learning_state_mp48_v4.json.bak", "native_learning_state_mp48_v4.json.tmp",
        "native_learning_state_mp48_v3.json", "native_learning_state_mp48_v2.json",
        "native_learning_state.json", "native_learning_state.json.bak",
        "learning_v6_evidence.json", "learning_v6_evidence.json.tmp", "learning_v6_evidence.json.invalid",
        "learning_live_only_policy_v2.json", "learning_scale_reset_mp48_v4.json",
    )
    val RUNTIME_DIRS: List<String> = listOf("learning_quarantine", "v7_sessions")
    val BACKUP_DIRS: List<String> = listOf("learning_checkpoints")

    /** Apaga só o que existe; devolve, nesta ordem, os arquivos (nome), depois "dir/" de runtime, depois "dir/" de backups. Nunca lança. */
    fun sweep(runtimeRoot: File, backupsRoot: File): List<String> {
        val removed = mutableListOf<String>()
        RUNTIME_FILES.forEach { name ->
            val file = File(runtimeRoot, name)
            if (runCatching { file.isFile && file.delete() }.getOrDefault(false)) removed += name
        }
        RUNTIME_DIRS.forEach { name ->
            if (removeDirectory(File(runtimeRoot, name))) removed += "$name/"
        }
        BACKUP_DIRS.forEach { name ->
            if (removeDirectory(File(backupsRoot, name))) removed += "$name/"
        }
        return removed
    }

    private fun removeDirectory(dir: File): Boolean = runCatching {
        dir.isDirectory && dir.deleteRecursively() && !dir.exists()
    }.getOrDefault(false)
}
