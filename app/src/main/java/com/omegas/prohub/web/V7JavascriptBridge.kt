package com.omegas.prohub.web

import android.webkit.JavascriptInterface
import com.omegas.prohub.MainActivity
import com.omegas.prohub.service.v7CalibrationStateJson
import com.omegas.prohub.service.v7IngestLearningSnapshot
import com.omegas.prohub.service.v7LoadSession
import com.omegas.prohub.service.v7SaveSession
import com.omegas.prohub.service.v7SessionFilesJson
import com.omegas.prohub.service.v7SynchronizeAdvisorSuggestions
import com.omegas.prohub.service.v7SynchronizeCalibration
import org.json.JSONObject

/**
 * Ponte de compatibilidade V7 dentro do OMEGAS V8. Não tem endpoints de escrita: Curva K e Mapa K
 * passam por [CalibrationOperationsBridge]. A sincronização serial usa a mesma fila daquela ponte.
 */
class V7JavascriptBridge(activity: MainActivity, private val operations: CalibrationOperationsBridge) {
    private val activityRef = java.lang.ref.WeakReference(activity)
    private val activity: MainActivity? get() = activityRef.get()

    @JavascriptInterface
    fun getState(): String = activity?.serviceOrNull()?.v7CalibrationStateJson()
        ?: unavailable()

    @JavascriptInterface
    fun listSessionFiles(): String = activity?.serviceOrNull()?.v7SessionFilesJson()
        ?: "[]"

    /** Operação pura: converte a saída do advisor em sugestões versionadas. */
    @JavascriptInterface
    fun synchronizeAdvisorSuggestions(adviceJson: String): String =
        activity?.serviceOrNull()?.v7SynchronizeAdvisorSuggestions(adviceJson)
            ?: unavailable()

    /** Importação explícita da memória física atual para a sessão V8. */
    @JavascriptInterface
    fun ingestLearningSnapshot(snapshotJson: String): String =
        activity?.serviceOrNull()?.v7IngestLearningSnapshot(snapshotJson)
            ?: unavailable()

    @JavascriptInterface
    fun synchronizeFromEcu(fileName: String): String =
        operations.startOperation("SYNCHRONIZING_ECU") { it.v7SynchronizeCalibration(fileName) }

    /**
     * Compatibilidade de API: sugestão nunca mais alcança writer diretamente.
     * A interface deve abrir Curva K/Mapa K, preparar a proposta e exigir revisão.
     */
    @JavascriptInterface
    fun applySuggestion(suggestionId: String): String = JSONObject()
        .put("ok", true)
        .put("suggestionId", suggestionId)
        .put("state", "MANUAL_REVIEW_REQUIRED")
        .put("prepared", false)
        .put("writesStarted", false)
        .put("automatic", false)
        .put("humanConfirmationRequired", true)
        .put("message", "Sugestão não escreve diretamente. Abra o editor, revise a proposta e confirme manualmente.")
        .toString()

    @JavascriptInterface
    fun saveSession(fileName: String): String = activity?.serviceOrNull()?.v7SaveSession(fileName)
        ?: unavailable()

    @JavascriptInterface
    fun loadSession(fileName: String): String = activity?.serviceOrNull()?.v7LoadSession(fileName)
        ?: unavailable()

    private fun unavailable(): String = JSONObject()
        .put("ok", false)
        .put("error", "Serviço V8 indisponível")
        .toString()
}
