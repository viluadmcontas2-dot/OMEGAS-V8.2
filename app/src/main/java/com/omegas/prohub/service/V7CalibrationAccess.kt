package com.omegas.prohub.service

import com.omegas.prohub.calibration.V7CalibrationCoordinator
import com.omegas.prohub.calibration.sanitizeUntrustedAdvisorIngressV7
import com.omegas.prohub.learning.LearningMutationAuthority
import com.omegas.prohub.learning.LearningTelemetrySchemaMigration
import com.omegas.prohub.learning.PredictorInterpolator
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
        PredictorStateCache.remove(service)
        Unit
    }
}

/**
 * Predictor é estrutural, não live. A assinatura usa somente metadados baratos
 * dos snapshots persistidos; se a ciência/mapa não mudou, não remonta 144 células.
 */
private object PredictorStateCache {
    private data class Entry(val signature: String, val snapshot: JSONObject)
    private val lock = Any()
    private val entries = WeakHashMap<TelemetryForegroundService, Entry>()

    fun get(service: TelemetryForegroundService): JSONObject = synchronized(lock) {
        val signature = signature(service)
        entries[service]?.takeIf { it.signature == signature }?.let { return JSONObject(it.snapshot.toString()) }
        val learning = service.runtime.exportLearning(service.settings.deviceId)
        val mapFile = File(service.paths.runtimeRoot, "k_map_cache.json")
        val map = try {
            mapFile.takeIf { it.isFile }?.let { JSONObject(it.readText(Charsets.UTF_8)) }
                ?.takeIf { it.optBoolean("complete", false) && it.optBoolean("sessionConfirmed", false) }
        } catch (_: Exception) {
            null
        }
        val snapshot = PredictorInterpolator.build(learning, map)
            .put("source", "V8_CALIBRATION_STATE")
            .put("cachedByStructuralRevision", true)
        entries[service] = Entry(signature, JSONObject(snapshot.toString()))
        JSONObject(snapshot.toString())
    }

    fun remove(service: TelemetryForegroundService) = synchronized(lock) {
        entries.remove(service)
        Unit
    }

    private fun signature(service: TelemetryForegroundService): String {
        val root = service.paths.runtimeRoot
        val files = listOf(
            File(root, LearningTelemetrySchemaMigration.ACTIVE_STATE_FILE),
            File(root, "learning_v6_evidence.json"),
            File(root, "k_map_cache.json"),
        )
        return files.joinToString("|") { file ->
            if (file.isFile) "${file.name}:${file.lastModified()}:${file.length()}" else "${file.name}:missing"
        }
    }
}

/**
 * Owner 096: a sessão V7 pode conservar sugestões históricas enquanto uma escrita
 * parcial/recovery torna a calibração física desconhecida. Nesse intervalo elas
 * continuam visíveis como contexto, mas nunca permanecem acionáveis nem PENDING
 * na projeção consumida pela UI. O estado persistido não é apagado.
 */
private fun mutationSafeCalibrationProjection(source: JSONObject): JSONObject {
    val mutation = LearningMutationAuthority.current()
    val projected = JSONObject(source.toString())
        .put("learningMutation", mutation.toJson())
        .put("actionabilityBlocked", mutation.blocksActiveScience)
    if (!mutation.blocksActiveScience) return projected

    var blockedPending = 0
    val items = projected.optJSONArray("suggestionItems")
    if (items != null) {
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            if (item.optString("lifecycle") == "PENDING") {
                blockedPending += 1
                item.put("storedLifecycle", "PENDING")
                    .put("lifecycle", "OBSERVING")
            }
            item.put("actionable", false)
                .put("actionabilityBlockedBy", mutation.state.name)
        }
    }
    projected.put("suggestions", 0)
        .put("suggestionPending", 0)
        .put("suggestionObserving", projected.optInt("suggestionObserving", 0) + blockedPending)
        .put("actionabilityBlockReason", mutation.state.name)
    return projected
}

private fun mutationBlockedOperation(operation: String): JSONObject? {
    val mutation = LearningMutationAuthority.current()
    if (!mutation.blocksActiveScience) return null
    return JSONObject()
        .put("ok", false)
        .put("operation", operation)
        .put("reasonCode", mutation.state.name)
        .put("error", "Calibração física ainda não reconciliada; releitura válida é obrigatória antes de usar sugestões")
        .put("learningMutation", mutation.toJson())
        .put("automaticWrite", false)
}

fun TelemetryForegroundService.v7CalibrationStateJson(): String =
    mutationSafeCalibrationProjection(V7CalibrationRegistry.get(this).stateJson())
        .put("predictor", try {
            PredictorStateCache.get(this)
        } catch (error: Exception) {
            JSONObject()
                .put("ok", false)
                .put("error", error.message ?: "Predictor indisponível")
                .put("automaticWrite", false)
        })
        .toString()

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
    mutationBlockedOperation("synchronize_advisor_suggestions")?.toString()
        ?: V7CalibrationRegistry.get(this)
            .synchronizeAdvisorSuggestions(sanitizeUntrustedAdvisorIngressV7(JSONObject(payload)))
            .toString()
} catch (error: Exception) {
    JSONObject()
        .put("ok", false)
        .put("error", error.message ?: "Falha ao registrar sugestões do advisor")
        .toString()
}

fun TelemetryForegroundService.v7IngestLearningSnapshot(payload: String): String = try {
    mutationBlockedOperation("ingest_learning_snapshot")?.toString()
        ?: V7CalibrationRegistry.get(this)
            .ingestLearningSnapshot(JSONObject(payload))
            .toString()
} catch (error: Exception) {
    JSONObject()
        .put("ok", false)
        .put("error", error.message ?: "Falha ao importar aprendizado V7")
        .toString()
}

fun TelemetryForegroundService.v7ApplySuggestion(suggestionId: String): String = try {
    mutationBlockedOperation("apply_suggestion")?.toString()
        ?: V7CalibrationRegistry.get(this).applySuggestionToEcu(suggestionId).toString()
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
