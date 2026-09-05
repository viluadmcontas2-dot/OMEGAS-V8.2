#!/usr/bin/env python3
"""One-shot remote-first convergence patch for OMEGAS Blue.

Runs only on the GitHub-hosted runner, edits the checked-out branch deterministically,
and commits the resulting production/test changes. It is intentionally idempotent.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def p(rel: str) -> Path:
    return ROOT / rel


def replace(rel: str, old: str, new: str, required: bool = True) -> None:
    path = p(rel)
    text = path.read_text(encoding="utf-8")
    if old not in text:
        if required and new not in text:
            raise SystemExit(f"expected block not found in {rel}: {old[:100]!r}")
        return
    path.write_text(text.replace(old, new), encoding="utf-8")


def write(rel: str, content: str) -> None:
    path = p(rel)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def delete(rel: str) -> None:
    path = p(rel)
    if path.exists():
        path.unlink()


# 1) Meaningful-session defaults: 30 useful sessions, never configurable below 20.
replace(
    "app/src/main/java/com/omegas/prohub/settings/AppSettings.kt",
    '''    init {\n        // Migra somente os antigos valores-padrão excessivos. Valores personalizados\n        // permanecem intocados.\n        if (!prefs.getBoolean("recorderSafeDefaultsV52", false)) {\n            val edit = prefs.edit().putBoolean("recorderSafeDefaultsV52", true)\n            if (!prefs.contains("sessionTelemetryEveryMs") || prefs.getLong("sessionTelemetryEveryMs", 0L) < 250L) {\n                edit.putLong("sessionTelemetryEveryMs", 250L)\n            }\n            if (!prefs.contains("sessionLogMaxMb") || prefs.getInt("sessionLogMaxMb", 1_024) == 1_024) {\n                edit.putInt("sessionLogMaxMb", 256)\n            }\n            if (!prefs.contains("sessionKeepCount") || prefs.getInt("sessionKeepCount", 5) == 5) {\n                edit.putInt("sessionKeepCount", 3)\n            }\n            edit.apply()\n        }\n    }''',
    '''    init {\n        // BLUE: retenção é por sessão útil, não por reconexão USB. Valores antigos\n        // abaixo do mínimo seguro são migrados uma única vez para 30.\n        if (!prefs.getBoolean("recorderMeaningfulRetentionV60", false)) {\n            val edit = prefs.edit().putBoolean("recorderMeaningfulRetentionV60", true)\n            if (!prefs.contains("sessionTelemetryEveryMs") || prefs.getLong("sessionTelemetryEveryMs", 0L) < 250L) {\n                edit.putLong("sessionTelemetryEveryMs", 250L)\n            }\n            if (!prefs.contains("sessionLogMaxMb") || prefs.getInt("sessionLogMaxMb", 1_024) == 1_024) {\n                edit.putInt("sessionLogMaxMb", 256)\n            }\n            if (!prefs.contains("sessionKeepCount") || prefs.getInt("sessionKeepCount", 3) < 20) {\n                edit.putInt("sessionKeepCount", 30)\n            }\n            edit.apply()\n        }\n    }''',
)
replace(
    "app/src/main/java/com/omegas/prohub/settings/AppSettings.kt",
    '''    var sessionKeepCount: Int\n        get() = prefs.getInt("sessionKeepCount", 3)\n        set(value) = prefs.edit().putInt("sessionKeepCount", value.coerceIn(1, 20)).apply()''',
    '''    var sessionKeepCount: Int\n        get() = prefs.getInt("sessionKeepCount", 30)\n        set(value) = prefs.edit().putInt("sessionKeepCount", value.coerceIn(20, 100)).apply()\n    var sessionVaultTreeUri: String\n        get() = prefs.getString("sessionVaultTreeUri", "") ?: ""\n        set(value) = prefs.edit().putString("sessionVaultTreeUri", value.trim()).apply()''',
)
replace(
    "app/src/main/java/com/omegas/prohub/settings/AppSettings.kt",
    '''        .put("sessionKeepCount", sessionKeepCount)\n        .put("deviceId", deviceId)''',
    '''        .put("sessionKeepCount", sessionKeepCount)\n        .put("sessionVaultTreeUri", sessionVaultTreeUri)\n        .put("deviceId", deviceId)''',
)

# 2) Pure relevance policy — tiny USB probes never consume useful retention.
write(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRelevancePolicy.kt",
    r'''package com.omegas.prohub.diagnostics

enum class SessionRelevance { PROBE, VALID, PROTECTED }

/**
 * Relevância é evidência, não tamanho bruto de arquivo nem número de conexões.
 * Uma sessão com calibração confirmada/readback nunca é candidata a pruning.
 */
object SessionRelevancePolicy {
    const val MIN_VALID_TELEMETRY_FRAMES = 20L
    const val MIN_VALID_DURATION_MS = 5_000L

    fun classify(
        telemetryFrames: Long,
        durationMs: Long,
        protectedEvidence: Boolean,
        explicitlyProtected: Boolean = false,
    ): SessionRelevance = when {
        protectedEvidence || explicitlyProtected -> SessionRelevance.PROTECTED
        telemetryFrames >= MIN_VALID_TELEMETRY_FRAMES && durationMs >= MIN_VALID_DURATION_MS -> SessionRelevance.VALID
        else -> SessionRelevance.PROBE
    }
}
''',
)

# 3) User-visible vault. Hot recording stays on private spool; immutable qualified
# sessions are copied as ZIP to Documents/OMEGAS/Sessions. Persisted SAF is an
# optional override; failure never deletes the spool.
write(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionVault.kt",
    r'''package com.omegas.prohub.diagnostics

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.storage.AppPaths
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Fail-safe promotion from the fast private session spool to a human-owned vault.
 * The private spool is never deleted by this class, even after a successful copy.
 */
class SessionVault(
    private val context: Context,
    private val paths: AppPaths,
    private val settings: AppSettings,
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omegas-session-vault").apply { isDaemon = true }
    }

    fun persistTreePermission(uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        settings.sessionVaultTreeUri = uri.toString()
        true
    } catch (_: Exception) {
        false
    }

    fun promoteAsync(sessionDir: File) {
        if (!sessionDir.isDirectory) return
        executor.execute { promote(sessionDir) }
    }

    fun close() = executor.shutdown()

    private fun promote(sessionDir: File) {
        val statusFile = File(sessionDir, "vault_status.json")
        val temporaryZip = File(paths.tempRoot, "${sessionDir.name}.zip.tmp")
        try {
            zipDirectory(sessionDir, temporaryZip)
            val destination = promoteToPersistedTree(temporaryZip, sessionDir.name)
                ?: promoteToDocuments(temporaryZip, sessionDir.name)
                ?: error("Nenhum destino público de sessão disponível")
            statusFile.writeText(
                JSONObject()
                    .put("ok", true)
                    .put("state", "PROMOTED")
                    .put("destination", destination.toString())
                    .put("promotedAtMs", System.currentTimeMillis())
                    .put("spoolPreserved", true)
                    .toString(2),
                Charsets.UTF_8,
            )
        } catch (error: Exception) {
            statusFile.writeText(
                JSONObject()
                    .put("ok", false)
                    .put("state", "PENDING")
                    .put("error", error.message ?: error.javaClass.simpleName)
                    .put("spoolPreserved", true)
                    .toString(2),
                Charsets.UTF_8,
            )
        } finally {
            temporaryZip.delete()
        }
    }

    private fun promoteToPersistedTree(zip: File, sessionName: String): Uri? {
        val raw = settings.sessionVaultTreeUri
        if (raw.isBlank()) return null
        return try {
            val tree = Uri.parse(raw)
            val resolver = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            var parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            parent = findOrCreateDirectory(tree, parent, "OMEGAS") ?: return null
            parent = findOrCreateDirectory(tree, parent, "Sessions") ?: return null
            val target = DocumentsContract.createDocument(
                resolver,
                parent,
                "application/zip",
                "$sessionName.zip",
            ) ?: return null
            resolver.openOutputStream(target, "w")?.use { output ->
                FileInputStream(zip).use { input -> input.copyTo(output, 64 * 1024) }
            } ?: return null
            target
        } catch (_: Exception) {
            null
        }
    }

    private fun findOrCreateDirectory(tree: Uri, parent: Uri, name: String): Uri? {
        val resolver = context.contentResolver
        val parentId = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idColumn))
                }
            }
        }
        return DocumentsContract.createDocument(
            resolver,
            parent,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        )
    }

    private fun promoteToDocuments(zip: File, sessionName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "OMEGAS/Sessions")
            if (!root.exists() && !root.mkdirs()) return null
            val target = File(root, "$sessionName.zip")
            FileInputStream(zip).use { input -> FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) } }
            return Uri.fromFile(target)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$sessionName.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/OMEGAS/Sessions")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val target = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(target, "w")?.use { output ->
                FileInputStream(zip).use { input -> input.copyTo(output, 64 * 1024) }
            } ?: return null
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(target, values, null, null)
            target
        } catch (error: Exception) {
            resolver.delete(target, null, null)
            throw error
        }
    }

    private fun zipDirectory(source: File, target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(target).buffered()).use { zip ->
            source.walkTopDown()
                .filter { it.isFile && it.name != "vault_status.json" }
                .forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(source).invariantSeparatorsPath))
                    FileInputStream(file).use { input -> input.copyTo(zip, 64 * 1024) }
                    zip.closeEntry()
                }
        }
    }
}
''',
)

# 4) SessionRecorder: logical session survives USB segments; retention is relevance-aware.
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    "import android.content.ContentResolver\n",
    "import android.content.ContentResolver\nimport android.content.Context\n",
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''class SessionRecorder(\n    private val paths: AppPaths,\n    private val settings: AppSettings,\n) {''',
    '''class SessionRecorder(\n    context: Context,\n    private val paths: AppPaths,\n    private val settings: AppSettings,\n) {''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''    private val sequence = AtomicLong(0L)\n    private val previewLock = Any()''',
    '''    private val sequence = AtomicLong(0L)\n    private val vault = SessionVault(context.applicationContext, paths, settings)\n    private val previewLock = Any()''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''    @Volatile private var byteCount = 0L\n    @Volatile private var currentSegment = 0''',
    '''    @Volatile private var byteCount = 0L\n    @Volatile private var telemetryCount = 0L\n    @Volatile private var usbSegmentCount = 0\n    @Volatile private var protectedEvidence = false\n    @Volatile private var explicitlyProtected = false\n    @Volatile private var currentSegment = 0''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''        return try {\n            pruneOldSessions()\n            val now = System.currentTimeMillis()''',
    '''        return try {\n            val now = System.currentTimeMillis()''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''            eventCount = 0L\n            byteCount = 0L\n            currentSegment = 0''',
    '''            eventCount = 0L\n            byteCount = 0L\n            telemetryCount = 0L\n            usbSegmentCount = 0\n            protectedEvidence = false\n            explicitlyProtected = false\n            currentSegment = 0''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''            stopReason = reason\n            closeWriter()\n            updateManifest()\n            statusObject().put("ok", true)''',
    '''            stopReason = reason\n            closeWriter()\n            updateManifest()\n            val dir = sessionDir\n            val relevance = currentRelevance()\n            pruneOldSessions()\n            if (dir != null && relevance != SessionRelevance.PROBE) vault.promoteAsync(dir)\n            statusObject().put("ok", true).put("relevance", relevance.name)''',
)
# Add segment/protection API before record().
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''    fun record(type: String, source: String, data: JSONObject, force: Boolean = false) {''',
    '''    @Synchronized\n    fun recordUsbSegment(connected: Boolean, usbSessionId: Long, device: String = "") {\n        if (!recording) return\n        if (connected) usbSegmentCount += 1\n        recordNow(\n            if (connected) "usb_segment_started" else "usb_segment_ended",\n            "usb",\n            JSONObject()\n                .put("usbSessionId", usbSessionId)\n                .put("device", device)\n                .put("logicalSessionId", sessionId)\n                .put("segment", usbSegmentCount),\n        )\n        updateManifest()\n    }\n\n    @Synchronized\n    fun protectCurrentSession(reason: String = "operador"): JSONObject {\n        if (!recording) return statusObject().put("ok", false).put("error", "Nenhuma sessão ativa")\n        explicitlyProtected = true\n        recordNow("session_protected", "native", JSONObject().put("reason", reason.take(180)))\n        updateManifest()\n        return statusObject().put("ok", true).put("relevance", SessionRelevance.PROTECTED.name)\n    }\n\n    fun record(type: String, source: String, data: JSONObject, force: Boolean = false) {''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''            .put("bytes", byteCount)\n            .put("megabytes", byteCount / (1024.0 * 1024.0))''',
    '''            .put("bytes", byteCount)\n            .put("telemetryFrames", telemetryCount)\n            .put("usbSegments", usbSegmentCount)\n            .put("relevance", currentRelevance().name)\n            .put("megabytes", byteCount / (1024.0 * 1024.0))''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''                        .put("bytes", size)\n                        .put("active", active),''',
    '''                        .put("bytes", size)\n                        .put("relevance", manifest.optString("relevance", SessionRelevance.PROBE.name))\n                        .put("telemetryFrames", manifest.optLong("telemetryFrames", 0L))\n                        .put("usbSegments", manifest.optInt("usbSegments", 0))\n                        .put("active", active),''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''    fun close() {\n        if (recording) stop("serviço encerrado")\n        worker.shutdownNow()\n    }''',
    '''    fun close() {\n        if (recording) stop("serviço encerrado")\n        worker.shutdownNow()\n        vault.close()\n    }''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''            eventCount += 1L\n            byteCount += bytes\n            segmentBytes += bytes''',
    '''            eventCount += 1L\n            byteCount += bytes\n            if (type == "telemetry") telemetryCount += 1L\n            if (type in setOf("k_batch_confirmed", "k_factor_batch_confirmed", "autocal_native_calibration_epoch")) {\n                protectedEvidence = true\n            }\n            segmentBytes += bytes''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''            .put("schemaVersion", 2)''',
    '''            .put("schemaVersion", 3)''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''            .put("bytes", byteCount)\n            .put("segments", currentSegment)\n            .put("stopReason", stopReason)''',
    '''            .put("bytes", byteCount)\n            .put("telemetryFrames", telemetryCount)\n            .put("usbSegments", usbSegmentCount)\n            .put("relevance", currentRelevance().name)\n            .put("protectedEvidence", protectedEvidence || explicitlyProtected)\n            .put("segments", currentSegment)\n            .put("stopReason", stopReason)''',
)
replace(
    "app/src/main/java/com/omegas/prohub/diagnostics/SessionRecorder.kt",
    '''    private fun pruneOldSessions() {\n        val keep = settings.sessionKeepCount.coerceIn(1, 20)\n        val dirs = paths.sessionLogsRoot.listFiles { file -> file.isDirectory }\n            ?.sortedByDescending { it.lastModified() }\n            .orEmpty()\n        dirs.drop((keep - 1).coerceAtLeast(0)).forEach { old -> old.deleteRecursively() }\n    }''',
    '''    private fun currentRelevance(nowMs: Long = System.currentTimeMillis()): SessionRelevance =\n        SessionRelevancePolicy.classify(\n            telemetryFrames = telemetryCount,\n            durationMs = if (startedAt > 0L) ((stoppedAt.takeIf { it > 0L } ?: nowMs) - startedAt).coerceAtLeast(0L) else 0L,\n            protectedEvidence = protectedEvidence,\n            explicitlyProtected = explicitlyProtected,\n        )\n\n    private fun pruneOldSessions() {\n        val dirs = paths.sessionLogsRoot.listFiles { file -> file.isDirectory }\n            ?.sortedByDescending { it.lastModified() }\n            .orEmpty()\n        data class Stored(val dir: File, val relevance: SessionRelevance)\n        val stored = dirs.map { dir ->\n            val relevance = try {\n                val manifest = JSONObject(File(dir, "manifest.json").readText(Charsets.UTF_8))\n                SessionRelevance.valueOf(manifest.optString("relevance", SessionRelevance.PROBE.name))\n            } catch (_: Exception) {\n                SessionRelevance.PROBE\n            }\n            Stored(dir, relevance)\n        }\n        // Probes são diagnóstico efêmero e nunca ocupam o orçamento das sessões úteis.\n        stored.filter { it.relevance == SessionRelevance.PROBE }.drop(10).forEach { it.dir.deleteRecursively() }\n        // PROTECTED é permanente até ação explícita do operador. O orçamento vale\n        // somente para VALID, que é o histórico útil rotativo.\n        stored.filter { it.relevance == SessionRelevance.VALID }\n            .drop(settings.sessionKeepCount.coerceIn(20, 100))\n            .forEach { it.dir.deleteRecursively() }\n    }''',
)

# 5) Service: recorder gets Context; USB reconnects become segments, not new logical sessions.
replace(
    "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt",
    "sessionRecorder = SessionRecorder(paths, settings)",
    "sessionRecorder = SessionRecorder(this, paths, settings)",
)
replace(
    "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt",
    '''            if (generationChanged) {\n                runtime.endUsbSession("USB_SESSION_REPLACED")''',
    '''            if (generationChanged) {\n                if (sessionRecorder.statusObject().optBoolean("recording")) {\n                    sessionRecorder.recordUsbSegment(false, previousSessionId, usb.deviceLabel)\n                }\n                runtime.endUsbSession("USB_SESSION_REPLACED")''',
)
replace(
    "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt",
    '''                sessionRecorder.start(\n                    "MP48 conectado",\n                    JSONObject().put("appVersion", BuildConfig.VERSION_NAME).put("usb", usb.deviceLabel),\n                )\n            }\n            if (settings.autoStartEngine''',
    '''                sessionRecorder.start(\n                    "MP48 conectado",\n                    JSONObject().put("appVersion", BuildConfig.VERSION_NAME).put("usb", usb.deviceLabel),\n                )\n            }\n            if (sessionRecorder.statusObject().optBoolean("recording")) {\n                sessionRecorder.recordUsbSegment(true, sessionId, usb.deviceLabel)\n            }\n            if (settings.autoStartEngine''',
)
replace(
    "app/src/main/java/com/omegas/prohub/service/TelemetryForegroundService.kt",
    '''            if (sessionRecorder.statusObject().optBoolean("recording")) {\n                sessionRecorder.stop("MP48 desconectado")\n            }''',
    '''            if (sessionRecorder.statusObject().optBoolean("recording")) {\n                sessionRecorder.recordUsbSegment(false, previousSessionId, usb.deviceLabel)\n            }''',
)

# 6) Remove compile-time ghosts of engines already intentionally deleted.
for rel in [
    "app/src/test/java/com/omegas/prohub/autocal/AutoMatchDraftReviewValidatorTest.kt",
    "app/src/test/java/com/omegas/prohub/autocal/AutoMatchKFactorDraftTest.kt",
    "app/src/test/java/com/omegas/prohub/autocal/AutoMatchResidualPlannerTest.kt",
    "app/src/test/java/com/omegas/prohub/autocal/AutoMatchSnapshotAnalysisTest.kt",
    "app/src/test/java/com/omegas/prohub/autocal/AutoMatchV5EngineTest.kt",
    "app/src/test/java/com/omegas/prohub/learning/PredictorInterpolatorTest.kt",
    "app/src/test/java/com/omegas/prohub/learning/PredictorSpatialConfidenceTest.kt",
    "app/src/test/java/com/omegas/prohub/learning/VisitConfidenceTest.kt",
    "app/src/test/java/com/omegas/v7/runtime/V7EquivalenceEngineTest.kt",
]:
    delete(rel)

print("BLUE_REMOTE_CONVERGENCE_PATCH=APPLIED")
