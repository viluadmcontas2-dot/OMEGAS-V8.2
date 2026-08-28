package com.omegas.v7.runtime

import java.util.UUID

/** Runtime puro da sessão V7, com escrita real delegada ao writer da ECU. */
enum class FuelV7 { PETROL, CNG }

enum class SuggestionTargetV7 { CURVE_K, MAP_K }

enum class SuggestionLifecycleV7 { PENDING, OBSERVING, APPLIED, SUPERSEDED }

data class CalibrationRevisionV7(
    val curveK: Long,
    val mapK: Long,
) {
    init {
        require(curveK >= 0)
        require(mapK >= 0)
    }

    fun next(target: SuggestionTargetV7): CalibrationRevisionV7 = when (target) {
        SuggestionTargetV7.CURVE_K -> copy(curveK = curveK + 1)
        SuggestionTargetV7.MAP_K -> copy(mapK = mapK + 1)
    }
}

data class CalibrationStateV7(
    val revision: CalibrationRevisionV7,
    val curveK: List<Double>,
    val mapK: List<List<Int>>,
) {
    init {
        CalibrationShapeV7.requireCurve(curveK)
        require(curveK.all { it.isFinite() && it > 0.0 })
        CalibrationShapeV7.requireMap(mapK)
    }
}

data class EvidenceV7(
    val id: String,
    val fuel: FuelV7,
    val collectedAtMs: Long,
    val visitId: String,
    val rpm: Double,
    val mapBar: Double,
    val petrolMs: Double,
    val quality: Double,
    val cngRevision: CalibrationRevisionV7?,
    val waterC: Double = UNKNOWN_TEMPERATURE_C,
    val gasC: Double = UNKNOWN_TEMPERATURE_C,
    val pressureDiffBar: Double = 0.0,
) {
    companion object {
        const val UNKNOWN_TEMPERATURE_C = -273.15
    }

    init {
        require(id.isNotBlank())
        require(visitId.isNotBlank())
        require(collectedAtMs >= 0)
        require(rpm.isFinite() && rpm >= 0.0)
        require(mapBar.isFinite() && mapBar >= 0.0)
        require(petrolMs.isFinite() && petrolMs >= 0.0)
        require(quality.isFinite() && quality in 0.0..1.0)
        require(waterC.isFinite())
        require(gasC.isFinite())
        require(pressureDiffBar.isFinite())
        require((fuel == FuelV7.PETROL && cngRevision == null) || (fuel == FuelV7.CNG && cngRevision != null))
    }
}

data class CurvePointChangeV7(val index: Int, val before: Double, val after: Double) {
    init {
        require(index in 0 until CalibrationShapeV7.CURVE_K_POINTS)
        require(before.isFinite() && before > 0.0)
        require(after.isFinite() && after > 0.0)
    }
}

data class MapCellChangeV7(val row: Int, val column: Int, val before: Int, val after: Int) {
    init {
        CalibrationShapeV7.requireEditableCell(row, column)
        require(before in 0..0xFF)
        require(after in 0..0xFF)
        require(before != after)
    }
}

data class LocalSuggestionV7(
    val id: String,
    val createdAtMs: Long,
    val expectedRevision: CalibrationRevisionV7,
    val target: SuggestionTargetV7,
    val curveChanges: List<CurvePointChangeV7> = emptyList(),
    val mapChanges: List<MapCellChangeV7> = emptyList(),
    val rationale: String,
    val updatedAtMs: Long = createdAtMs,
    val lifecycle: SuggestionLifecycleV7 = SuggestionLifecycleV7.PENDING,
    val confidence: Double = 0.0,
    val stabilityGeneration: Int = -1,
    val stabilityState: String = "UNASSESSED",
    val consolidatedErrorPercent: Double? = null,
    val recentErrorPercent: Double? = null,
    val physics: PhysicsSuggestionMetadataV7 = PhysicsSuggestionMetadataV7(),
) {
    init {
        require(id.isNotBlank())
        require(createdAtMs >= 0)
        require(updatedAtMs >= createdAtMs)
        require(rationale.isNotBlank())
        require(confidence.isFinite() && confidence in 0.0..1.0)
        require(stabilityGeneration >= -1)
        require(consolidatedErrorPercent == null || consolidatedErrorPercent.isFinite())
        require(recentErrorPercent == null || recentErrorPercent.isFinite())
        when (target) {
            SuggestionTargetV7.CURVE_K -> require(mapChanges.isEmpty())
            SuggestionTargetV7.MAP_K -> require(curveChanges.isEmpty())
        }
        if (lifecycle == SuggestionLifecycleV7.PENDING) {
            when (target) {
                SuggestionTargetV7.CURVE_K -> require(curveChanges.isNotEmpty())
                SuggestionTargetV7.MAP_K -> require(mapChanges.isNotEmpty())
            }
        }
    }

    fun actionableAt(revision: CalibrationRevisionV7): Boolean =
        lifecycle == SuggestionLifecycleV7.PENDING && expectedRevision == revision &&
            when (target) {
                SuggestionTargetV7.CURVE_K -> curveChanges.isNotEmpty()
                SuggestionTargetV7.MAP_K -> mapChanges.isNotEmpty()
            } && (!id.startsWith("advisor-") || physics.authorizes(target))
}

data class CheckpointV7(
    val id: String,
    val createdAtMs: Long,
    val reason: String,
    val calibration: CalibrationStateV7,
)

data class CalibrationWriteResultV7(
    val success: Boolean,
    val readBack: CalibrationStateV7? = null,
    val message: String = "",
)

/** Adaptador único para a infraestrutura real de escrita já existente no aplicativo. */
fun interface CalibrationWriterV7 {
    fun write(
        current: CalibrationStateV7,
        desired: CalibrationStateV7,
        suggestion: LocalSuggestionV7,
    ): CalibrationWriteResultV7
}

data class V7SessionState(
    val sessionId: String,
    val calibration: CalibrationStateV7,
    val petrolEvidence: List<EvidenceV7> = emptyList(),
    val cngEvidenceByRevision: Map<CalibrationRevisionV7, List<EvidenceV7>> = emptyMap(),
    val comparisons: List<FuelComparisonV7> = emptyList(),
    val suggestions: List<LocalSuggestionV7> = emptyList(),
    val checkpoints: List<CheckpointV7> = emptyList(),
    val lastWriteMessage: String = "",
) {
    init {
        require(sessionId.isNotBlank())
    }

    fun activeCngEvidence(): List<EvidenceV7> = cngEvidenceByRevision[calibration.revision].orEmpty()
    fun activeComparisons(): List<FuelComparisonV7> = comparisons.filter { it.revision == calibration.revision }
    fun activeSuggestions(): List<LocalSuggestionV7> = suggestions.filter {
        it.expectedRevision == calibration.revision && it.lifecycle in setOf(
            SuggestionLifecycleV7.PENDING,
            SuggestionLifecycleV7.OBSERVING,
        )
    }
}

class V7SessionRuntime(
    initialState: V7SessionState,
    private val equivalenceEngine: V7EquivalenceEngine = V7EquivalenceEngine(),
) {
    var state: V7SessionState = initialState
        private set

    init {
        val revision = initialState.calibration.revision
        val normalizedSuggestions = initialState.suggestions.map { suggestion ->
            if (suggestion.expectedRevision != revision && suggestion.lifecycle in setOf(
                    SuggestionLifecycleV7.PENDING,
                    SuggestionLifecycleV7.OBSERVING,
                )
            ) {
                suggestion.copy(lifecycle = SuggestionLifecycleV7.SUPERSEDED)
            } else suggestion
        }
        state = initialState.copy(
            suggestions = normalizedSuggestions,
            comparisons = equivalenceEngine.reconcile(initialState),
        )
    }

    fun addEvidence(evidence: EvidenceV7) {
        val withEvidence = when (evidence.fuel) {
            FuelV7.PETROL -> state.copy(
                petrolEvidence = immutableByVisit(state.petrolEvidence, evidence),
            )
            FuelV7.CNG -> {
                require(evidence.cngRevision == state.calibration.revision) {
                    "Evidência GNV pertence a outra revisão de calibração"
                }
                val current = state.cngEvidenceByRevision[evidence.cngRevision].orEmpty()
                state.copy(
                    cngEvidenceByRevision = state.cngEvidenceByRevision +
                        (evidence.cngRevision to immutableByVisit(current, evidence)),
                )
            }
        }
        state = withEvidence.copy(comparisons = equivalenceEngine.reconcile(withEvidence))
    }

    fun mapStability(row: Int, column: Int): LearningStabilitySnapshotV7 =
        LearningStabilityV7.mapCell(state.activeComparisons(), row, column)

    fun curveStability(index: Int): LearningStabilitySnapshotV7 =
        LearningStabilityV7.curvePoint(state.activeComparisons(), index)

    fun registerSuggestion(suggestion: LocalSuggestionV7) {
        require(suggestion.expectedRevision == state.calibration.revision) {
            "Sugestão pertence a outra revisão"
        }
        state = state.copy(suggestions = state.suggestions.filterNot { it.id == suggestion.id } + suggestion)
    }

    /**
     * Atualiza sugestões sem apagar silenciosamente o que o operador ainda não aplicou.
     *
     * A magnitude não segue cada fotografia do advisor. Enquanto o mesmo consolidado
     * permanecer vigente, o alvo já preparado fica congelado. Uma evidência recente
     * contraditória muda a entidade para OBSERVING/REVALIDATING; somente uma promoção
     * científica do consolidado permite aceitar outra magnitude.
     */
    fun replaceSuggestions(suggestions: List<LocalSuggestionV7>, nowMs: Long = System.currentTimeMillis()) {
        require(suggestions.map { it.id }.distinct().size == suggestions.size) {
            "Lista de sugestões contém IDs repetidos"
        }
        require(suggestions.all { it.expectedRevision == state.calibration.revision }) {
            "Há sugestão pertencente a outra revisão"
        }
        val existingById = state.suggestions.associateBy { it.id }
        val stabilized = suggestions.map { fresh ->
            stabilizeSuggestion(fresh, existingById[fresh.id], nowMs)
        }
        val incoming = stabilized.associateBy { it.id }
        val merged = mutableListOf<LocalSuggestionV7>()
        val seen = linkedSetOf<String>()

        state.suggestions.forEach { existing ->
            val fresh = incoming[existing.id]
            val next = when {
                existing.lifecycle in setOf(SuggestionLifecycleV7.APPLIED, SuggestionLifecycleV7.SUPERSEDED) -> existing
                existing.expectedRevision != state.calibration.revision -> existing.copy(
                    lifecycle = SuggestionLifecycleV7.SUPERSEDED,
                    updatedAtMs = maxOf(existing.updatedAtMs, nowMs),
                )
                fresh != null -> fresh.copy(
                    createdAtMs = existing.createdAtMs,
                    updatedAtMs = maxOf(existing.updatedAtMs, fresh.updatedAtMs, nowMs),
                )
                else -> existing.copy(
                    lifecycle = SuggestionLifecycleV7.OBSERVING,
                    updatedAtMs = maxOf(existing.updatedAtMs, nowMs),
                    rationale = "A sugestão continua registrada, mas a evidência atual não justifica correção.",
                )
            }
            merged += next
            seen += existing.id
        }
        stabilized.filterNot { it.id in seen }.forEach { merged += it }
        state = state.copy(suggestions = merged.sortedWith(
            compareBy<LocalSuggestionV7> { lifecycleOrder(it.lifecycle) }
                .thenByDescending { it.updatedAtMs },
        ))
    }

    fun applySuggestionToEcu(
        suggestionId: String,
        nowMs: Long,
        writer: CalibrationWriterV7,
    ): CalibrationStateV7 {
        require(nowMs >= 0)
        val suggestion = state.suggestions.singleOrNull { it.id == suggestionId }
            ?: error("Sugestão não encontrada: $suggestionId")
        require(suggestion.actionableAt(state.calibration.revision)) {
            "Sugestão não está pronta para aplicação"
        }
        val checkpoint = CheckpointV7(
            id = UUID.randomUUID().toString(),
            createdAtMs = nowMs,
            reason = "Antes da escrita ECU da sugestão ${suggestion.id}",
            calibration = state.calibration,
        )
        val desired = calculateSuggestion(state.calibration, suggestion)

        state = state.copy(
            checkpoints = state.checkpoints + checkpoint,
            lastWriteMessage = "Escrita iniciada para ${suggestion.id}",
        )
        val result = try {
            writer.write(state.calibration, desired, suggestion)
        } catch (error: Exception) {
            state = state.copy(lastWriteMessage = error.message ?: "Escrita ECU interrompida")
            throw error
        }
        if (!result.success) {
            val message = result.message.ifBlank { "Escrita ECU rejeitada" }
            state = state.copy(lastWriteMessage = message)
            throw IllegalArgumentException(message)
        }
        val applied = result.readBack ?: error("Writer confirmou sem readback da ECU")
        require(applied.revision == desired.revision) { "Readback retornou revisão inesperada" }

        val oldRevision = state.calibration.revision
        val lifecycleUpdated = state.suggestions.map { item ->
            when {
                item.id == suggestion.id -> item.copy(
                    lifecycle = SuggestionLifecycleV7.APPLIED,
                    updatedAtMs = maxOf(item.updatedAtMs, nowMs),
                )
                item.expectedRevision == oldRevision && item.lifecycle in setOf(
                    SuggestionLifecycleV7.PENDING,
                    SuggestionLifecycleV7.OBSERVING,
                ) -> item.copy(
                    lifecycle = SuggestionLifecycleV7.SUPERSEDED,
                    updatedAtMs = maxOf(item.updatedAtMs, nowMs),
                )
                else -> item
            }
        }
        val withCalibration = state.copy(
            calibration = applied,
            suggestions = lifecycleUpdated,
            lastWriteMessage = result.message,
        )
        state = withCalibration.copy(comparisons = equivalenceEngine.reconcile(withCalibration))
        return applied
    }

    fun restoreCheckpoint(checkpointId: String): CalibrationStateV7 {
        val checkpoint = state.checkpoints.singleOrNull { it.id == checkpointId }
            ?: error("Checkpoint não encontrado: $checkpointId")
        state = state.copy(calibration = checkpoint.calibration)
        return checkpoint.calibration
    }

    private fun stabilizeSuggestion(
        fresh: LocalSuggestionV7,
        existing: LocalSuggestionV7?,
        nowMs: Long,
    ): LocalSuggestionV7 = when (fresh.target) {
        SuggestionTargetV7.MAP_K -> stabilizeMapSuggestion(fresh, existing, nowMs)
        SuggestionTargetV7.CURVE_K -> stabilizeCurveSuggestion(fresh, existing, nowMs)
    }

    private fun stabilizeMapSuggestion(
        fresh: LocalSuggestionV7,
        existing: LocalSuggestionV7?,
        nowMs: Long,
    ): LocalSuggestionV7 {
        val physical = fresh.mapChanges.firstOrNull() ?: existing?.mapChanges?.firstOrNull()
            ?: return fresh.copy(
                lifecycle = SuggestionLifecycleV7.OBSERVING,
                stabilityState = LearningStabilityStateV7.NO_EVIDENCE.name,
                updatedAtMs = maxOf(fresh.updatedAtMs, nowMs),
            )
        val stability = mapStability(physical.row, physical.column)
        val metadata = fresh.copy(
            confidence = stability.confidence,
            stabilityGeneration = stability.generation,
            stabilityState = stability.state.name,
            consolidatedErrorPercent = stability.consolidatedErrorPercent,
            recentErrorPercent = stability.recentErrorPercent,
            updatedAtMs = maxOf(fresh.updatedAtMs, nowMs),
        )

        if (stability.state == LearningStabilityStateV7.REVALIDATING && existing != null) {
            return existing.copy(
                lifecycle = SuggestionLifecycleV7.OBSERVING,
                confidence = stability.confidence,
                stabilityGeneration = stability.generation,
                stabilityState = stability.state.name,
                consolidatedErrorPercent = stability.consolidatedErrorPercent,
                recentErrorPercent = stability.recentErrorPercent,
                updatedAtMs = maxOf(existing.updatedAtMs, nowMs),
                rationale = "Revalidando esta célula: o alvo anterior foi preservado, mas fica bloqueado até a tendência recente se confirmar ou desaparecer.",
                physics = fresh.physics,
            )
        }

        val consolidated = stability.state == LearningStabilityStateV7.CONSOLIDATED
        if (!consolidated || fresh.lifecycle != SuggestionLifecycleV7.PENDING || fresh.mapChanges.isEmpty()) {
            return if (existing != null && existing.stabilityGeneration == stability.generation) {
                existing.copy(
                    lifecycle = SuggestionLifecycleV7.OBSERVING,
                    confidence = stability.confidence,
                    stabilityState = stability.state.name,
                    consolidatedErrorPercent = stability.consolidatedErrorPercent,
                    recentErrorPercent = stability.recentErrorPercent,
                    updatedAtMs = maxOf(existing.updatedAtMs, nowMs),
                    rationale = stability.reason,
                    physics = fresh.physics,
                )
            } else {
                metadata.copy(lifecycle = SuggestionLifecycleV7.OBSERVING, mapChanges = emptyList(), rationale = stability.reason)
            }
        }

        if (existing != null && existing.stabilityGeneration == stability.generation && existing.mapChanges.isNotEmpty()) {
            return existing.copy(
                lifecycle = SuggestionLifecycleV7.PENDING,
                confidence = stability.confidence,
                stabilityState = stability.state.name,
                consolidatedErrorPercent = stability.consolidatedErrorPercent,
                recentErrorPercent = null,
                updatedAtMs = maxOf(existing.updatedAtMs, nowMs),
                rationale = "Consolidado preservado; a sugestão permanece estável até aplicação ou revalidação real.",
                physics = fresh.physics,
            )
        }
        return metadata.copy(
            lifecycle = SuggestionLifecycleV7.PENDING,
            rationale = "Memória consolidada desta célula sustenta a correção local; aplicação continua exclusivamente manual.",
        )
    }

    private fun stabilizeCurveSuggestion(
        fresh: LocalSuggestionV7,
        existing: LocalSuggestionV7?,
        nowMs: Long,
    ): LocalSuggestionV7 {
        val physicalChanges = if (fresh.curveChanges.isNotEmpty()) fresh.curveChanges else existing?.curveChanges.orEmpty()
        if (physicalChanges.isEmpty()) {
            return fresh.copy(
                lifecycle = SuggestionLifecycleV7.OBSERVING,
                stabilityState = LearningStabilityStateV7.NO_EVIDENCE.name,
                updatedAtMs = maxOf(fresh.updatedAtMs, nowMs),
            )
        }
        val snapshots = physicalChanges.associate { change -> change.index to curveStability(change.index) }
        val anyRevalidating = snapshots.values.any { it.state == LearningStabilityStateV7.REVALIDATING }
        val stableIndexes = snapshots.filterValues { stability ->
            stability.state == LearningStabilityStateV7.CONSOLIDATED &&
                stability.rpmBandCount >= 2 && stability.mapBandCount >= 2
        }.keys
        val filteredFresh = fresh.curveChanges.filter { it.index in stableIndexes }
        val generationFingerprint = physicalChanges
            .sortedBy { it.index }
            .fold(17) { acc, change -> 31 * acc + change.index * 7 + (snapshots[change.index]?.generation ?: -1) }
        val confidence = snapshots.values.map { it.confidence }.minOrNull() ?: 0.0
        val consolidatedErrors = snapshots.values.mapNotNull { it.consolidatedErrorPercent }
        val recentErrors = snapshots.values.mapNotNull { it.recentErrorPercent }
        val consolidatedError = consolidatedErrors.takeIf { it.isNotEmpty() }?.average()
        val recentError = recentErrors.takeIf { it.isNotEmpty() }?.average()

        if (anyRevalidating && existing != null) {
            return existing.copy(
                lifecycle = SuggestionLifecycleV7.OBSERVING,
                confidence = confidence,
                stabilityGeneration = generationFingerprint,
                stabilityState = LearningStabilityStateV7.REVALIDATING.name,
                consolidatedErrorPercent = consolidatedError,
                recentErrorPercent = recentError,
                updatedAtMs = maxOf(existing.updatedAtMs, nowMs),
                rationale = "Revalidando tendência global: a proposta anterior foi preservada, mas fica bloqueada enquanto a mudança recente é verificada.",
                physics = fresh.physics,
            )
        }

        val fullySupported = filteredFresh.isNotEmpty() && filteredFresh.size == fresh.curveChanges.size &&
            fresh.lifecycle == SuggestionLifecycleV7.PENDING
        if (!fullySupported) {
            return if (existing != null && existing.stabilityGeneration == generationFingerprint) {
                existing.copy(
                    lifecycle = SuggestionLifecycleV7.OBSERVING,
                    confidence = confidence,
                    stabilityState = if (anyRevalidating) LearningStabilityStateV7.REVALIDATING.name else LearningStabilityStateV7.LEARNING.name,
                    consolidatedErrorPercent = consolidatedError,
                    recentErrorPercent = recentError,
                    updatedAtMs = maxOf(existing.updatedAtMs, nowMs),
                    rationale = "Tendência global preservada, mas ainda falta cobertura independente em RPM/MAP para liberar ajuste da Curva K.",
                    physics = fresh.physics,
                )
            } else {
                fresh.copy(
                    curveChanges = emptyList(),
                    lifecycle = SuggestionLifecycleV7.OBSERVING,
                    confidence = confidence,
                    stabilityGeneration = generationFingerprint,
                    stabilityState = if (anyRevalidating) LearningStabilityStateV7.REVALIDATING.name else LearningStabilityStateV7.LEARNING.name,
                    consolidatedErrorPercent = consolidatedError,
                    recentErrorPercent = recentError,
                    updatedAtMs = maxOf(fresh.updatedAtMs, nowMs),
                    rationale = "A Curva K exige tendência consolidada em mais de uma faixa de RPM e MAP; evidência localizada continua pertencendo ao Mapa K.",
                )
            }
        }

        if (existing != null && existing.stabilityGeneration == generationFingerprint && existing.curveChanges.isNotEmpty()) {
            return existing.copy(
                lifecycle = SuggestionLifecycleV7.PENDING,
                confidence = confidence,
                stabilityState = LearningStabilityStateV7.CONSOLIDATED.name,
                consolidatedErrorPercent = consolidatedError,
                recentErrorPercent = null,
                updatedAtMs = maxOf(existing.updatedAtMs, nowMs),
                rationale = "Tendência global consolidada preservada; a proposta não acompanha oscilações de cada nova amostra.",
                physics = fresh.physics,
            )
        }
        return fresh.copy(
            curveChanges = filteredFresh,
            lifecycle = SuggestionLifecycleV7.PENDING,
            confidence = confidence,
            stabilityGeneration = generationFingerprint,
            stabilityState = LearningStabilityStateV7.CONSOLIDATED.name,
            consolidatedErrorPercent = consolidatedError,
            recentErrorPercent = null,
            updatedAtMs = maxOf(fresh.updatedAtMs, nowMs),
            rationale = "Tendência global consolidada com cobertura independente em RPM e MAP; aplicação continua exclusivamente manual.",
        )
    }

    private fun calculateSuggestion(
        current: CalibrationStateV7,
        suggestion: LocalSuggestionV7,
    ): CalibrationStateV7 {
        val nextRevision = current.revision.next(suggestion.target)
        return when (suggestion.target) {
            SuggestionTargetV7.CURVE_K -> {
                val curve = current.curveK.toMutableList()
                suggestion.curveChanges.forEach { change ->
                    require(curve[change.index] == change.before) {
                        "Curva K mudou na posição ${change.index}"
                    }
                    curve[change.index] = change.after
                }
                current.copy(revision = nextRevision, curveK = curve)
            }
            SuggestionTargetV7.MAP_K -> {
                val map = current.mapK.map { it.toMutableList() }.toMutableList()
                suggestion.mapChanges.forEach { change ->
                    require(map[change.row][change.column] == change.before) {
                        "Mapa K mudou em ${change.row},${change.column}"
                    }
                    map[change.row][change.column] = change.after
                }
                current.copy(revision = nextRevision, mapK = map.map { it.toList() })
            }
        }
    }

    /**
     * visitId é a unidade física independente. Depois de registrado, o conteúdo
     * científico daquela visita é imutável; snapshots agregados posteriores não
     * podem reescrever RPM/MAP/Petrol Inj/qualidade históricos.
     */
    private fun immutableByVisit(current: List<EvidenceV7>, incoming: EvidenceV7): List<EvidenceV7> =
        if (current.any { it.visitId == incoming.visitId }) current else current + incoming

    private fun lifecycleOrder(value: SuggestionLifecycleV7): Int = when (value) {
        SuggestionLifecycleV7.PENDING -> 0
        SuggestionLifecycleV7.OBSERVING -> 1
        SuggestionLifecycleV7.APPLIED -> 2
        SuggestionLifecycleV7.SUPERSEDED -> 3
    }
}
