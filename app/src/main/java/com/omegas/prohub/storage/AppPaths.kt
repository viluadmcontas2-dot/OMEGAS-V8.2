package com.omegas.prohub.storage

import android.content.Context
import java.io.File

class AppPaths(context: Context) {
    val internalRoot = File(context.filesDir, "OmegasProHub")
    val projectsRoot = File(internalRoot, "projects")
    val activeProject = File(projectsRoot, "active")
    val stagingRoot = File(projectsRoot, "staging")
    val backupsRoot = File(projectsRoot, "backups")
    val tempRoot = File(internalRoot, "temp")

    val externalRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, "OmegasProHub")
    val runtimeRoot = File(externalRoot, "runtime")
    val databasesRoot = File(runtimeRoot, "databases")
    val runtimeBackupsRoot = File(runtimeRoot, "backups")
    val quarantineRoot = File(runtimeRoot, "quarantine")
    val exportsRoot = File(externalRoot, "exports")
    val logsRoot = File(externalRoot, "logs")
    val sessionLogsRoot = File(externalRoot, "session_records")
    val logFile = File(logsRoot, "omegas_hub.log")

    init {
        listOf(
            internalRoot, projectsRoot, activeProject, stagingRoot, backupsRoot, tempRoot,
            externalRoot, runtimeRoot, databasesRoot, runtimeBackupsRoot, quarantineRoot,
            exportsRoot, logsRoot, sessionLogsRoot,
        ).forEach { it.mkdirs() }
    }

    fun clearTemp() {
        tempRoot.listFiles()?.forEach { it.deleteRecursively() }
        stagingRoot.listFiles()?.forEach { it.deleteRecursively() }
    }
}

