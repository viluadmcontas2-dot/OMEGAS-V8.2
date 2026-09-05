package com.omegas.prohub.service

import com.omegas.prohub.blue.BlueEvidenceProjection
import com.omegas.prohub.calibration.BlueCalibrationCoordinator
import com.omegas.prohub.learning.BlueEvidenceStore
import org.json.JSONObject
import java.io.File
import java.util.WeakHashMap

private object BlueCalibrationRegistry {
    private val lock = Any()
    private val coordinators = WeakHashMap<TelemetryForegroundService, BlueCalibrationCoordinator>()

    fun get(service: TelemetryForegroundService): BlueCalibrationCoordinator = synchronized(lock) {
        coordinators.getOrPut(service) {
            BlueCalibrationCoordinator(service.kWriter, service.kFactor)
        }
    }

    fun remove(service: TelemetryForegroundService) = synchronized(lock) {
        coordinators.remove(service)
        BlueProjectionCache.remove(service)
        Unit
    }
}

private object BlueProjectionCache {
    private data class Entry(val signature: String, val snapshot: JSONObject)
    private val lock = Any()
    private val entries = WeakHashMap<TelemetryForegroundService, Entry>()

    fun get(service: TelemetryForegroundService): JSONObject = synchronized(lock) {
        val signature = signature(service)
        entries[service]?.takeIf { it.signature == signature }?.let {
            return JSONObject(it.snapshot.toString())
        }
        val learning = service.runtime.exportLearning(service.settings.deviceId)
        val mapFile = File(service.paths.runtimeRoot, "k_map_cache.json")
        val map = try {
            mapFile.takeIf { it.isFile }?.let { JSONObject(it.readText(Charsets.UTF_8)) }
                ?.takeIf { it.optBoolean("complete", false) && it.optBoolean("sessionConfirmed", false) }
        } catch (_: Exception) { null }
        val snapshot = BlueEvidenceProjection.build(learning, map)
        entries[service] = Entry(signature, JSONObject(snapshot.toString()))
        JSONObject(snapshot.toString())
    }

    fun remove(service: TelemetryForegroundService) = synchronized(lock) {
        entries.remove(service)
        Unit
    }

    private fun signature(service: TelemetryForegroundService): String {
        val root = service.paths.runtimeRoot
        return listOf(
            File(root, BlueEvidenceStore.STATE_FILE),
            File(root, "k_map_cache.json"),
        ).joinToString("|") { file ->
            if (file.isFile) "${file.name}:${file.lastModified()}:${file.length()}" else "${file.name}:missing"
        }
    }
}

fun TelemetryForegroundService.blueCalibrationStateJson(): String = try {
    val coordinator = BlueCalibrationRegistry.get(this)
    JSONObject(coordinator.stateJson().toString())
        .put("evidenceProjection", BlueProjectionCache.get(this))
        .toString()
} catch (error: Exception) {
    JSONObject()
        .put("ready", false)
        .put("error", error.message ?: "Estado Blue indisponível")
        .put("decisionAuthority", "BLUE_CAUSAL_ENGINE")
        .toString()
}

fun TelemetryForegroundService.blueSynchronizeCalibration(): String = try {
    BlueCalibrationRegistry.get(this).synchronizeFromEcu().toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Falha ao sincronizar calibração").toString()
}

fun TelemetryForegroundService.blueReconcileConfirmedManualWrite(): String = try {
    BlueCalibrationRegistry.get(this).reconcileConfirmedManualWrite().toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Falha no readback após escrita").toString()
}

fun TelemetryForegroundService.blueIngestLearningSnapshot(payload: String): String = try {
    BlueCalibrationRegistry.get(this).ingestLearningSnapshot(JSONObject(payload)).toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Falha ao importar evidência").toString()
}

fun TelemetryForegroundService.blueProposalJson(): String = try {
    BlueCalibrationRegistry.get(this).proposalJson().toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Proposta Blue indisponível").toString()
}

fun TelemetryForegroundService.releaseBlueCalibrationCoordinator() {
    BlueCalibrationRegistry.remove(this)
}
