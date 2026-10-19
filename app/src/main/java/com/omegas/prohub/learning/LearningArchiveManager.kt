package com.omegas.prohub.learning

import com.omegas.prohub.calibration.KWriteManager
import com.omegas.prohub.ecu.NativeRuntimeManager
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.storage.AppPaths
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Formato portátil do aprendizado Android, independente da versão do APK. */
class LearningArchiveManager(
    private val paths: AppPaths,
    private val settings: AppSettings,
    private val runtime: NativeRuntimeManager,
    private val kWriter: KWriteManager,
    private val log: RingLog,
) {
    companion object {
        const val FORMAT = "omegas-project-calibration-v3"
        const val NATIVE_FORMAT = SignalLearningStore.FORMAT
        const val MIME = "application/vnd.omegas.learning+json"
    }

    /**
     * Cofre único do carro: a V6 não cria perfis de veículo. Todo checkpoint é
     * local, independente da versão do APK e mantido em histórico rotativo.
     */
    private val checkpointDir = File(paths.runtimeBackupsRoot, "learning_checkpoints").apply { mkdirs() }
    private val checkpointCurrent = File(checkpointDir, "OMEGAS_Aprendizado_Atual.omegas")
    private val checkpointPrevious = File(checkpointDir, "OMEGAS_Aprendizado_Anterior.omegas")
    private val checkpointHistoryDir = File(checkpointDir, "history").apply { mkdirs() }
    private val maxCheckpointHistory = 80

    @Synchronized
    fun saveInternalCheckpoint(reason: String): JSONObject = try {
        val now = System.currentTimeMillis()
        val safeReason = reason.take(160)
        val snapshot = snapshotJson()
            .put("checkpointReason", safeReason)
            .put("checkpointAt", now)
            .put("checkpointKind", "single-car-continuity-v1")
        val learningOk = snapshot.optJSONObject("learning")?.optBoolean("ok", false) == true
        if (!learningOk) {
            JSONObject().put("ok", false)
                .put("error", snapshot.optJSONObject("learning")
                    ?.optString("error", "Aprendizado nativo ainda indisponível"))
        } else {
            val bytes = snapshot.toString(2).toByteArray(Charsets.UTF_8)
            if (checkpointCurrent.isFile) {
                writeAtomically(checkpointPrevious, checkpointCurrent.readBytes())
            }
            writeAtomically(checkpointCurrent, bytes)
            val historyFile = File(checkpointHistoryDir, "${now}_${safeFilePart(safeReason)}.omegas")
            writeAtomically(historyFile, bytes)
            trimCheckpointHistory()
            log.add("INFO", "LEARNING-NATIVE", "Checkpoint salvo • $safeReason")
            JSONObject().put("ok", true)
                .put("bytes", checkpointCurrent.length())
                .put("updatedAt", checkpointCurrent.lastModified())
                .put("reason", safeReason)
                .put("historyCount", checkpointFiles().size)
                .put("historyId", historyFile.nameWithoutExtension)
        }
    } catch (error: Exception) {
        log.add("WARN", "LEARNING-NATIVE", "Checkpoint não salvo: ${error.message}")
        JSONObject().put("ok", false).put("error", error.message ?: "Falha no checkpoint")
    }

    @Synchronized
    fun checkpointStatus(): JSONObject {
        val history = checkpointFiles()
        val recent = JSONArray()
        history.take(8).forEach { file ->
            recent.put(JSONObject()
                .put("id", file.nameWithoutExtension)
                .put("updatedAt", file.lastModified())
                .put("bytes", file.length())
                .put("reason", checkpointReason(file)))
        }
        return JSONObject()
            .put("available", checkpointCurrent.isFile)
            .put("updatedAt", if (checkpointCurrent.isFile) checkpointCurrent.lastModified() else 0L)
            .put("bytes", if (checkpointCurrent.isFile) checkpointCurrent.length() else 0L)
            .put("previousAvailable", checkpointPrevious.isFile)
            .put("historyCount", history.size)
            .put("historyLimit", maxCheckpointHistory)
            .put("recent", recent)
            .put("storage", "single-car-continuity-v1")
    }

    private fun writeAtomically(destination: File, bytes: ByteArray) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".tmp")
        FileOutputStream(temp).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun checkpointFiles(): List<File> = checkpointHistoryDir.listFiles { file ->
        file.isFile && file.extension == "omegas"
    }?.sortedByDescending { it.lastModified() } ?: emptyList()

    private fun trimCheckpointHistory() {
        checkpointFiles().drop(maxCheckpointHistory).forEach { file ->
            if (!file.delete()) log.add("WARN", "LEARNING-NATIVE", "Não foi possível rotacionar ${file.name}")
        }
    }

    private fun checkpointReason(file: File): String = try {
        JSONObject(file.readText(Charsets.UTF_8)).optString("checkpointReason", "Checkpoint")
    } catch (_: Exception) {
        "Checkpoint"
    }

    private fun safeFilePart(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifBlank { "checkpoint" }

    fun snapshotJson(): JSONObject {
        val learning = try { runtime.exportLearning(settings.deviceId) }
        catch (error: Exception) { JSONObject().put("ok", false).put("error", error.message) }
        val history = try { JSONArray(kWriter.historyJson()) } catch (_: Exception) { JSONArray() }
        return JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", 6)
            .put("minimumReaderVersion", 3)
            .put("migrationPolicy", "MP48_V2_PHYSICAL_EVIDENCE_ONLY")
            .put("createdAt", System.currentTimeMillis())
            .put("sourceDeviceId", settings.deviceId)
            .put("sourceDeviceName", settings.deviceName)
            .put("learning", learning)
            .put("obd", org.json.JSONObject())
            .put("confirmedKHistory", history)
            .put("kHistoryComponent", kWriter.exportHistoryComponent(settings.deviceId))
            .put("rules", JSONObject()
                .put("sampling", "configurable-continuous-response-driven")
                .put("tolerancePolicy", LearningToleranceSettings.current.toJson())
                .put("confidence", "physical-evidence-without-usb-session-gating")
                .put("fuelEquivalence", "continuous-petrol-reference-surface")
                .put("telemetryScale", "mp48-progbase-v2")
                .put("target", "petrol-inj-on-cng-approaches-petrol-reference")
                .put("kMapAuthority", "ECU_ACK_AND_ROW_READBACK")
                .put("automaticCalibration", false)
                .put("obdIsolation", true))
    }

}
