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

/**
 * Brings the normal Learning publication into the Blue causal coordinator.
 * Calibration is hydrated only from full readbacks already confirmed in the
 * current USB session; this path never starts serial I/O or an ECU write.
 */
private fun refreshBlueLearningEvidence(
    service: TelemetryForegroundService,
    coordinator: BlueCalibrationCoordinator,
): JSONObject {
    try {
        if (!coordinator.stateJson().optBoolean("ready", false)) {
            val mapFile = File(service.paths.runtimeRoot, "k_map_cache.json")
            val curveFile = File(service.paths.runtimeRoot, "k_factor_cache.json")
            val map = mapFile.takeIf(File::isFile)?.let { JSONObject(it.readText(Charsets.UTF_8)) }
            val curve = curveFile.takeIf(File::isFile)?.let { JSONObject(it.readText(Charsets.UTF_8)) }
            if (map == null || curve == null ||
                !map.optBoolean("complete", false) || !map.optBoolean("sessionConfirmed", false) ||
                !curve.optBoolean("complete", false) || !curve.optBoolean("sessionConfirmed", false)
            ) {
                return JSONObject()
                    .put("ok", false)
                    .put("state", "CALIBRATION_READBACK_REQUIRED")
                    .put("error", "Leia e confirme Mapa K e Curva K nesta sessão para calcular o desvio medido")
                    .put("serialReadStarted", false)
                    .put("automaticWrite", false)
            }
            coordinator.synchronizeFromConfirmedSnapshot(map, curve)
        }

        val learning = service.runtime.exportLearning(service.settings.deviceId)
        return coordinator.ingestLearningSnapshot(learning)
            .put("state", "LEARNING_EVIDENCE_INGESTED")
            .put("serialReadStarted", false)
            .put("automaticWrite", false)
    } catch (error: Exception) {
        return JSONObject()
            .put("ok", false)
            .put("state", "BLUE_LEARNING_INGEST_FAILED")
            .put("error", error.message ?: "Falha ao reconciliar evidência Blue")
            .put("serialReadStarted", false)
            .put("automaticWrite", false)
    }
}

/**
 * Synchronizes the already-paired OBD witness into the Blue coordinator only
 * when Blue state/proposal is requested. This keeps OBD read-only and avoids a
 * reverse dependency from the OBD acquisition path into calibration writers.
 */
private fun syncObdWitness(
    service: TelemetryForegroundService,
    coordinator: BlueCalibrationCoordinator,
) {
    try {
        coordinator.updateObdWitness(JSONObject(service.obdWitnessStatusJson()))
    } catch (_: Exception) {
        coordinator.updateObdWitness(JSONObject())
    }
}

fun TelemetryForegroundService.blueCalibrationStateId(): String = try {
    val state = BlueCalibrationRegistry.get(this).stateJson()
    if (!state.optBoolean("ready", false)) "" else {
        val revision = state.optJSONObject("revision") ?: JSONObject()
        "map-${revision.optInt("mapK", 0)}:curve-${revision.optInt("curveK", 0)}"
    }
} catch (_: Exception) {
    ""
}

fun TelemetryForegroundService.blueCalibrationStateJson(): String = try {
    val coordinator = BlueCalibrationRegistry.get(this)
    val ingestion = refreshBlueLearningEvidence(this, coordinator)
    syncObdWitness(this, coordinator)
    val state = JSONObject(coordinator.stateJson().toString())
        .put("evidenceProjection", BlueProjectionCache.get(this))
        .put("learningIngestion", ingestion)
    if (!state.optBoolean("ready", false) && !ingestion.optBoolean("ok", false)) {
        state.put("reason", ingestion.optString("state", "CALIBRATION_NOT_SYNCED"))
            .put("error", ingestion.optString("error", "Estado Blue indisponível"))
    }
    state.toString()
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
    val coordinator = BlueCalibrationRegistry.get(this)
    refreshBlueLearningEvidence(this, coordinator)
    syncObdWitness(this, coordinator)
    coordinator.proposalJson().toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Proposta Blue indisponível").toString()
}

fun TelemetryForegroundService.releaseBlueCalibrationCoordinator() {
    BlueCalibrationRegistry.remove(this)
}