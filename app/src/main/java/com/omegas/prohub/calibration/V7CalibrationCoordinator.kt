package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.learning.AssistedCalibrationAdvisor
import com.omegas.prohub.physics.decoratePhysicsAuthority
import com.omegas.v7.runtime.CalibrationRevisionV7
import com.omegas.v7.runtime.CalibrationShapeV7
import com.omegas.v7.runtime.CalibrationStateV7
import com.omegas.v7.runtime.EvidenceV7
import com.omegas.v7.runtime.FuelV7
import com.omegas.v7.runtime.LearningStabilityJsonV7
import com.omegas.v7.runtime.LocalSuggestionV7
import com.omegas.v7.runtime.SuggestionLifecycleV7
import com.omegas.v7.runtime.SuggestionTargetV7
import com.omegas.v7.runtime.V7SessionFileStore
import com.omegas.v7.runtime.V7SessionRuntime
import com.omegas.v7.runtime.V7SessionState
import com.omegas.v7.runtime.V7UiProjection
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs

/**
 * Autoridade de integração da sessão V7 dentro do serviço Android.
 *
 * Não possui transporte próprio: leitura, ACK, escrita e readback continuam sob
 * KWriteManager e KFactorManager. O coordenador mantém revisão, evidência,
 * sugestões concretas e persistência depois do resultado real da ECU.
 */
class V7CalibrationCoordinator(
    directory: File,
    private val mapManager: KWriteManager,
    private val factorManager: KFactorManager,
) {
    private val lock = Any()
    private val store = V7SessionFileStore(directory)
    private val ecuWriter = ExistingCalibrationWriterV7(mapManager, factorManager)
    private val suggestionAdapter = AdvisorSuggestionAdapterV7()
    private var activeFileName = "sessao-atual"
    private var runtime: V7SessionRuntime? = loadLatest()

    fun synchronizedFromEcu(fileName: String = activeFileName): JSONObject = synchronized(lock) {
        val mapResult = mapManager.readFullMap()
        require(mapResult.optBoolean("ok")) {
            mapResult.optString("error", "Falha ao ler Mapa K")
        }
        val curveResult = factorManager.readCurve()
        require(curveResult.optBoolean("ok")) {
            curveResult.optString("error", "Falha ao ler Curva K")
        }

        val map = decodeMap(mapResult.optJSONArray("allRows"))
        val curve = decodeCurve(curveResult.optJSONArray("factorsRaw"))
        val previous = runtime?.state
        val revision = when (previous) {
            null -> CalibrationRevisionV7(0, 0)
            else -> CalibrationRevisionV7(
                curveK = previous.calibration.revision.curveK +
                    if (previous.calibration.curveK == curve) 0 else 1,
                mapK = previous.calibration.revision.mapK +
                    if (previous.calibration.mapK == map) 0 else 1,
            )
        }
        val calibration = CalibrationStateV7(revision, curve, map)
        val next = if (previous == null) {
            V7SessionState(
                sessionId = UUID.randomUUID().toString(),
                calibration = calibration,
            )
        } else {
            previous.copy(
                calibration = calibration,
                // O runtime marca pendentes da revisão antiga como SUPERSEDED.
                // Nunca apagamos silenciosamente a fila/histórico na sincronização.
                suggestions = previous.suggestions,
            )
        }
        runtime = V7SessionRuntime(next)
        activeFileName = normalizeName(fileName)
        persistLocked()
        stateJsonLocked()
            .put("ok", true)
            .put("source", "ECU_ACK_READBACK")
            .put("curveChanged", previous != null && previous.calibration.curveK != curve)
            .put("mapChanged", previous != null && previous.calibration.mapK != map)
    }

    /**
     * Chamado somente depois de o writer manual já ter encerrado com ACK + readback.
     * Faz uma nova leitura fresca da ECU e marca APPLIED apenas quando o alvo exato
     * de uma sugestão da revisão anterior está realmente presente na calibração lida.
     */
    fun reconcileConfirmedManualWrite(
        target: SuggestionTargetV7,
        nowMs: Long = System.currentTimeMillis(),
    ): JSONObject = synchronized(lock) {
        require(nowMs >= 0)
        val before = requireRuntime().state
        val previousRevision = before.calibration.revision
        val candidates = before.suggestions.filter { suggestion ->
            suggestion.expectedRevision == previousRevision &&
                suggestion.target == target &&
                suggestion.actionableAt(previousRevision)
        }

        val sync = synchronizedFromEcu(activeFileName)
        val active = requireRuntime()
        val actual = active.state.calibration
        val matchedIds = candidates
            .filter { suggestionMatchesCalibration(it, actual) }
            .map { it.id }
            .toSet()

        if (matchedIds.isNotEmpty()) {
            val updated = active.state.suggestions.map { suggestion ->
                if (suggestion.id in matchedIds) {
                    suggestion.copy(
                        lifecycle = SuggestionLifecycleV7.APPLIED,
                        updatedAtMs = maxOf(suggestion.updatedAtMs, nowMs),
                    )
                } else {
                    suggestion
                }
            }
            runtime = V7SessionRuntime(active.state.copy(suggestions = updated))
            persistLocked()
        }

        stateJsonLocked()
            .put("ok", true)
            .put("source", "CONFIRMED_MANUAL_WRITE_READBACK")
            .put("target", target.name)
            .put("previousRevision", revisionJson(previousRevision))
            .put("currentRevision", revisionJson(requireRuntime().state.calibration.revision))
            .put("appliedSuggestionIds", JSONArray(matchedIds.toList().sorted()))
            .put("appliedSuggestionCount", matchedIds.size)
            .put("sync", sync)
    }

    /**
     * Reutiliza a coleta física já produzida por MotorLearningMemory.
     *
     * O snapshot legado contém IDs de visita e a média atual da região, não os
     * valores individuais antigos de cada visita. O runtime portanto trata o
     * primeiro registro recebido de cada visitId como imutável: snapshots futuros
     * nunca reescrevem RPM/MAP/Petrol Inj. históricos usando outra média regional.
     * GNV de épocas antigas não entra na revisão ativa.
     */
    fun ingestLearningSnapshot(snapshot: JSONObject): JSONObject = synchronized(lock) {
        val active = requireRuntime()
        val regions = snapshot.optJSONArray("regions") ?: JSONArray()
        val sourceEpoch = snapshot.optInt("epoch", 1)
        var petrolImported = 0
        var cngImported = 0
        repeat(regions.length()) { index ->
            val region = regions.optJSONObject(index) ?: return@repeat
            val fuel = when (region.optString("fuel").uppercase()) {
                "PETROL", "GASOLINA" -> FuelV7.PETROL
                "CNG", "GNV", "GAS" -> FuelV7.CNG
                else -> return@repeat
            }
            if (fuel == FuelV7.CNG && region.optInt("epoch", sourceEpoch) != sourceEpoch) {
                return@repeat
            }
            val visits = region.optJSONArray("visits")
            val visitIds = buildList {
                if (visits != null) {
                    repeat(visits.length()) {
                        visits.optString(it).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                if (isEmpty()) add(region.optString("id", "region-$index"))
            }.distinct()
            visitIds.forEach { visitId ->
                val evidence = EvidenceV7(
                    id = "${region.optString("id", "region-$index")}:$visitId",
                    fuel = fuel,
                    collectedAtMs = region.optLong("updated_at", System.currentTimeMillis()).coerceAtLeast(0L),
                    visitId = visitId,
                    rpm = region.optDouble("rpm", 0.0).coerceAtLeast(0.0),
                    mapBar = region.optDouble("map_bar", 0.0).coerceAtLeast(0.0),
                    petrolMs = region.optDouble("petrol_ms", 0.0).coerceAtLeast(0.0),
                    quality = region.optDouble("quality", region.optDouble("confidence", 0.0)).coerceIn(0.0, 1.0),
                    cngRevision = if (fuel == FuelV7.CNG) active.state.calibration.revision else null,
                    waterC = finiteOrUnknown(region.optDouble("water_c", EvidenceV7.UNKNOWN_TEMPERATURE_C)),
                    gasC = finiteOrUnknown(region.optDouble("gas_c", EvidenceV7.UNKNOWN_TEMPERATURE_C)),
                    pressureDiffBar = finiteOrZero(region.optDouble("pressure_diff_bar", 0.0)),
                )
                active.addEvidence(evidence)
                if (fuel == FuelV7.PETROL) petrolImported += 1 else cngImported += 1
            }
        }
        val registered = replaceAdvisorSuggestionsLocked(resolveAdvice(snapshot)).size
        persistLocked()
        stateJsonLocked()
            .put("ok", true)
            .put("petrolImported", petrolImported)
            .put("cngImported", cngImported)
            .put("registeredSuggestions", registered)
            .put("activeComparisons", active.state.activeComparisons().size)
    }

    fun synchronizeAdvisorSuggestions(payload: JSONObject): JSONObject = synchronized(lock) {
        val generated = replaceAdvisorSuggestionsLocked(resolveAdvice(payload))
        persistLocked()
        stateJsonLocked()
            .put("ok", true)
            .put("registeredSuggestions", generated.size)
    }

    fun addEvidence(evidence: EvidenceV7): JSONObject = synchronized(lock) {
        val active = requireRuntime()
        active.addEvidence(evidence)
        persistLocked()
        stateJsonLocked().put("ok", true)
    }

    fun registerSuggestion(suggestion: LocalSuggestionV7): JSONObject = synchronized(lock) {
        val active = requireRuntime()
        active.registerSuggestion(suggestion)
        persistLocked()
        stateJsonLocked().put("ok", true)
    }

    /** Deve ser chamado em thread de trabalho; aguarda ACK e readback reais. */
    fun applySuggestionToEcu(suggestionId: String): JSONObject = synchronized(lock) {
        val active = requireRuntime()
        try {
            val applied = active.applySuggestionToEcu(
                suggestionId = suggestionId,
                nowMs = System.currentTimeMillis(),
                writer = ecuWriter,
            )
            persistLocked()
            stateJsonLocked()
                .put("ok", true)
                .put("appliedRevision", revisionJson(applied.revision))
                .put("writeMessage", active.state.lastWriteMessage)
        } catch (error: Exception) {
            persistLocked()
            stateJsonLocked()
                .put("ok", false)
                .put("error", error.message ?: "Falha ao aplicar sugestão V7")
                .put("writeMessage", active.state.lastWriteMessage)
        }
    }

    fun stateJson(): JSONObject = synchronized(lock) { stateJsonLocked() }

    fun saveAs(fileName: String): JSONObject = synchronized(lock) {
        activeFileName = normalizeName(fileName)
        val file = persistLocked()
        JSONObject()
            .put("ok", true)
            .put("file", file.name)
            .put("state", stateJsonLocked())
    }

    fun load(fileName: String): JSONObject = synchronized(lock) {
        val restored = store.load(fileName)
        runtime = V7SessionRuntime(restored)
        activeFileName = normalizeName(fileName)
        stateJsonLocked().put("ok", true)
    }

    fun listFiles(): JSONArray = synchronized(lock) {
        JSONArray().also { output ->
            store.list().forEach { file ->
                output.put(
                    JSONObject()
                        .put("name", file.name)
                        .put("updatedAt", file.lastModified())
                        .put("bytes", file.length()),
                )
            }
        }
    }

    private fun replaceAdvisorSuggestionsLocked(advice: JSONObject): List<LocalSuggestionV7> {
        val active = requireRuntime()
        val generated = suggestionAdapter.adapt(advice, active.state.calibration)
        active.replaceSuggestions(generated)
        return generated
    }

    private fun suggestionMatchesCalibration(
        suggestion: LocalSuggestionV7,
        calibration: CalibrationStateV7,
    ): Boolean = when (suggestion.target) {
        SuggestionTargetV7.CURVE_K -> suggestion.curveChanges.isNotEmpty() && suggestion.curveChanges.all { change ->
            val actual = calibration.curveK.getOrNull(change.index) ?: return@all false
            abs(actual - change.after) <= 1e-9
        }
        SuggestionTargetV7.MAP_K -> suggestion.mapChanges.isNotEmpty() && suggestion.mapChanges.all { change ->
            calibration.mapK.getOrNull(change.row)?.getOrNull(change.column) == change.after
        }
    }

    private fun resolveAdvice(payload: JSONObject): JSONObject {
        payload.optJSONObject("assistedCalibration")?.let { return it }
        payload.optJSONObject("assisted_calibration")?.let { return it }
        return if (payload.has("regions") || payload.has("comparisons")) {
            AssistedCalibrationAdvisor.decoratePhysicsAuthority(
                AssistedCalibrationAdvisor.analyze(payload),
            )
        } else {
            payload
        }
    }

    private fun loadLatest(): V7SessionRuntime? {
        val latest = store.list().firstOrNull() ?: return null
        return try {
            activeFileName = latest.name.removeSuffix(".omegas7")
            V7SessionRuntime(store.load(latest.name))
        } catch (_: Exception) {
            null
        }
    }

    private fun requireRuntime(): V7SessionRuntime = runtime
        ?: error("Sincronize Curva K e Mapa K com a ECU antes de usar a sessão V7")

    private fun persistLocked(): File = store.save(activeFileName, requireRuntime().state)

    private fun stateJsonLocked(): JSONObject {
        val active = runtime ?: return JSONObject()
            .put("ready", false)
            .put("reason", "CALIBRATION_NOT_SYNCED")
            .put("files", listFiles())
        val state = active.state
        val ui = V7UiProjection.from(state)
        val pending = state.suggestions.count { it.actionableAt(state.calibration.revision) }
        val observing = state.suggestions.count {
            it.expectedRevision == state.calibration.revision &&
                it.lifecycle in setOf(SuggestionLifecycleV7.PENDING, SuggestionLifecycleV7.OBSERVING) &&
                !it.actionableAt(state.calibration.revision)
        }
        val applied = state.suggestions.count { it.lifecycle == SuggestionLifecycleV7.APPLIED }
        val superseded = state.suggestions.count { it.lifecycle == SuggestionLifecycleV7.SUPERSEDED }
        return JSONObject()
            .put("ready", true)
            .put("sessionId", state.sessionId)
            .put("file", "$activeFileName.omegas7")
            .put("revision", revisionJson(state.calibration.revision))
            .put("curvePoints", state.calibration.curveK.size)
            .put("mapStorageRows", state.calibration.mapK.size)
            .put("mapEditableRows", CalibrationShapeV7.MAP_K_EDITABLE_ROWS)
            .put("mapColumns", CalibrationShapeV7.MAP_K_COLUMNS)
            .put("petrolEvidence", state.petrolEvidence.size)
            .put("activeCngEvidence", state.activeCngEvidence().size)
            .put("activeComparisons", state.activeComparisons().size)
            .put("historicalComparisons", state.comparisons.size - state.activeComparisons().size)
            .put("learningStability", LearningStabilityJsonV7.from(active))
            .put("suggestions", pending)
            .put("suggestionPending", pending)
            .put("suggestionObserving", observing)
            .put("suggestionApplied", applied)
            .put("suggestionSuperseded", superseded)
            .put("suggestionItems", JSONArray(state.suggestions.map { suggestionJson(it, state.calibration.revision) }))
            .put("checkpoints", state.checkpoints.size)
            .put("lastWriteMessage", state.lastWriteMessage)
            .put("headline", ui.now.headline)
            .put("learningExplanation", ui.learning.explanation)
    }

    private fun suggestionJson(value: LocalSuggestionV7, currentRevision: CalibrationRevisionV7): JSONObject = JSONObject()
        .put("id", value.id)
        .put("createdAt", value.createdAtMs)
        .put("updatedAt", value.updatedAtMs)
        .put("expectedRevision", revisionJson(value.expectedRevision))
        .put("target", value.target.name)
        .put("lifecycle", value.lifecycle.name)
        .put("actionable", value.actionableAt(currentRevision))
        .put("confidence", value.confidence)
        .put("stabilityGeneration", value.stabilityGeneration)
        .put("stabilityState", value.stabilityState)
        .put("consolidatedErrorPercent", value.consolidatedErrorPercent ?: JSONObject.NULL)
        .put("recentErrorPercent", value.recentErrorPercent ?: JSONObject.NULL)
        .put("rationale", value.rationale)
        .put("magnitudeAuthority", value.physics.magnitudeAuthority.name)
        .put("stepAuthority", value.physics.stepAuthority.name)
        .put("correctionMechanism", value.physics.correctionMechanism.name)
        .put("expectedEffectDirection", value.physics.effectDirection.name)
        .put("expectedEffectAuthority", value.physics.effectAuthority.name)
        .put("expectedEffectLowerBound", value.physics.lowerBound ?: JSONObject.NULL)
        .put("expectedEffectUpperBound", value.physics.upperBound ?: JSONObject.NULL)
        .put("expectedEffectAssumptions", JSONArray(value.physics.assumptions))
        .put("expectedEffectFalsifier", value.physics.falsifier)
        .put("mechanismEvidencePath", JSONArray(value.physics.evidencePath))
        .put("idealTarget", value.physics.idealTarget)
        .put("curveChanges", JSONArray(value.curveChanges.map { change ->
            JSONObject()
                .put("index", change.index)
                .put("before", change.before)
                .put("after", change.after)
        }))
        .put("mapChanges", JSONArray(value.mapChanges.map { change ->
            JSONObject()
                .put("row", change.row)
                .put("column", change.column)
                .put("before", change.before)
                .put("after", change.after)
        }))

    private fun decodeCurve(rawArray: JSONArray?): List<Double> {
        require(rawArray != null && rawArray.length() == CalibrationShapeV7.CURVE_K_POINTS) {
            "Readback da Curva K não possui 30 pontos"
        }
        return List(rawArray.length()) { index ->
            KFactorProtocol.factorFromRaw(rawArray.getInt(index))
        }
    }

    private fun decodeMap(allRows: JSONArray?): List<List<Int>> {
        require(allRows != null && allRows.length() == CalibrationShapeV7.MAP_K_STORAGE_ROWS) {
            "Readback do Mapa K não possui 13 linhas"
        }
        return List(allRows.length()) { row ->
            val values = allRows.getJSONArray(row)
            require(values.length() == CalibrationShapeV7.MAP_K_COLUMNS) {
                "Linha K $row não possui 12 colunas"
            }
            List(values.length()) { column -> values.getInt(column) }
        }
    }

    private fun revisionJson(value: CalibrationRevisionV7): JSONObject = JSONObject()
        .put("curveK", value.curveK)
        .put("mapK", value.mapK)

    private fun finiteOrUnknown(value: Double): Double =
        if (value.isFinite()) value else EvidenceV7.UNKNOWN_TEMPERATURE_C

    private fun finiteOrZero(value: Double): Double = if (value.isFinite()) value else 0.0

    private fun normalizeName(value: String): String = value
        .trim()
        .removeSuffix(".omegas7")
        .ifBlank { "sessao-atual" }
}
