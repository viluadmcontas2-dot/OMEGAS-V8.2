package com.omegas.prohub.service

import com.omegas.prohub.calibration.V7CalibrationCoordinator
import com.omegas.v7.runtime.SuggestionTargetV7
import org.json.JSONObject
import java.io.File
import java.util.WeakHashMap

/**
 * Associação única entre o serviço Android e a sessão V7.
 *
 * Usa os managers já pertencentes ao serviço; não cria outra conexão USB,
 * outra fila serial ou outro writer.
 */
private object V7CalibrationRegistry {
    private val lock = Any()
    private val coordinators = WeakHashMap<TelemetryForegroundService, V7CalibrationCoordinator>()

    fun get(service: TelemetryForegroundService): V7CalibrationCoordinator = synchronized(lock) {
        coordinators.getOrPut(service) {
            V7CalibrationCoordinator(
                directory = File(service.paths.runtimeRoot, "v7_sessions"),
                mapManager = service.kWriter,
                factorManager = service.kFactor,
            )
        }
    }

    fun remove(service: TelemetryForegroundService) = synchronized(lock) {
        coordinators.remove(service)
        Unit
    }
}

fun TelemetryForegroundService.v7CalibrationStateJson(): String =
    V7CalibrationRegistry.get(this).stateJson().toString()

fun TelemetryForegroundService.v7SynchronizeCalibration(fileName: String = "sessao-atual"): String = try {
    V7CalibrationRegistry.get(this).synchronizedFromEcu(fileName).toString()
} catch (error: Exception) {
    JSONObject()
        .put("ok", false)
        .put("error", error.message ?: "Falha ao sincronizar calibração V7")
        .toString()
}

fun TelemetryForegroundService.v7ReconcileConfirmedManualWrite(target: String): String = try {
    val parsed = SuggestionTargetV7.valueOf(target)
    V7CalibrationRegistry.get(this)
        .reconcileConfirmedManualWrite(parsed)
        .toString()
} catch (error: Exception) {
    JSONObject()
        .put("ok", false)
        .put("error", error.message ?: "Falha ao reconciliar sugestões após readback")
        .toString()
}

fun TelemetryForegroundService.v7SynchronizeAdvisorSuggestions(payload: String): String = try {
    V7CalibrationRegistry.get(this)
        .synchronizeAdvisorSuggestions(JSONObject(payload))
        .toString()
} catch (error: Exception) {
    JSONObject()
        .put("ok", false)
        .put("error", error.message ?: "Falha ao registrar sugestões do advisor")
        .toString()
}

fun TelemetryForegroundService.v7IngestLearningSnapshot(payload: String): String = try {
    V7CalibrationRegistry.get(this)
        .ingestLearningSnapshot(JSONObject(payload))
        .toString()
} catch (error: Exception) {
    JSONObject()
        .put("ok", false)
        .put("error", error.message ?: "Falha ao importar aprendizado V7")
        .toString()
}

fun TelemetryForegroundService.v7ApplySuggestion(suggestionId: String): String = try {
    V7CalibrationRegistry.get(this).applySuggestionToEcu(suggestionId).toString()
} catch (error: Exception) {
    JSONObject()
        .put("ok", false)
        .put("error", error.message ?: "Falha ao aplicar sugestão V7")
        .toString()
}

fun TelemetryForegroundService.v7SaveSession(fileName: String): String = try {
    V7CalibrationRegistry.get(this).saveAs(fileName).toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Falha ao salvar sessão V7").toString()
}

fun TelemetryForegroundService.v7LoadSession(fileName: String): String = try {
    V7CalibrationRegistry.get(this).load(fileName).toString()
} catch (error: Exception) {
    JSONObject().put("ok", false).put("error", error.message ?: "Falha ao carregar sessão V7").toString()
}

fun TelemetryForegroundService.v7SessionFilesJson(): String =
    V7CalibrationRegistry.get(this).listFiles().toString()

fun TelemetryForegroundService.releaseV7CalibrationCoordinator() {
    V7CalibrationRegistry.remove(this)
}
