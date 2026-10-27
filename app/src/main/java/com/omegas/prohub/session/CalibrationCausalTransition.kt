package com.omegas.prohub.session

import java.security.MessageDigest
import kotlin.math.abs

enum class CausalTransitionStatus {
    AWAITING_POST_EVIDENCE,
    CONFIRMED,
    CONTRADICTED,
    INCONCLUSIVE,
}

data class CalibrationTransition(
    val suggestionId: String,
    val target: SuggestionTarget,
    val appliedAtMs: Long,
    val beforeRevision: CalibrationRevision,
    val afterRevision: CalibrationRevision,
    val beforeFingerprint: String,
    val afterFingerprint: String,
    val preErrorPercent: Double?,
    val postErrorPercent: Double? = null,
    val responseGain: Double? = null,
    val mapCells: List<String> = emptyList(),
    val curveIndexes: List<Int> = emptyList(),
    val status: CausalTransitionStatus = CausalTransitionStatus.AWAITING_POST_EVIDENCE,
)

/**
 * Identidade material da calibração. A revisão é metadado de causalidade;
 * a identidade física é definida pelo conteúdo completo de Curve K + Map K.
 */
fun CalibrationState.materialFingerprint(): String {
    val canonical = buildString {
        append("curve:")
        curveK.forEach { value ->
            append(java.lang.Double.doubleToLongBits(value)).append(',')
        }
        append("|map:")
        mapK.forEach { row ->
            row.forEach { value -> append(value).append(',') }
            append(';')
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

/**
 * Ledger causal derivado do estado persistido. O checkpoint prova o material
 * anterior; a sugestão APPLIED prova a intervenção; somente comparações da revisão
 * material posterior podem classificar sua resposta. Evidência antiga nunca é
 * reutilizada para confirmar uma intervenção nova.
 */
val SessionState.calibrationTransitions: List<CalibrationTransition>
    get() = suggestions.asSequence()
        .filter { it.lifecycle == SuggestionLifecycle.APPLIED }
        .mapNotNull { suggestion ->
            val checkpoint = checkpoints.lastOrNull { checkpoint ->
                checkpoint.calibration.revision == suggestion.expectedRevision &&
                    checkpoint.reason.contains(suggestion.id)
            } ?: return@mapNotNull null
            val before = checkpoint.calibration
            val after = applySuggestionMaterial(before, suggestion)
            val postComparisons = comparisons.filter { comparison ->
                comparison.revision == after.revision && comparison.createdAtMs >= suggestion.updatedAtMs
            }
            val response = classifyResponse(suggestion, postComparisons)
            CalibrationTransition(
                suggestionId = suggestion.id,
                target = suggestion.target,
                appliedAtMs = suggestion.updatedAtMs,
                beforeRevision = before.revision,
                afterRevision = after.revision,
                beforeFingerprint = before.materialFingerprint(),
                afterFingerprint = after.materialFingerprint(),
                preErrorPercent = suggestion.consolidatedErrorPercent,
                postErrorPercent = response.postErrorPercent,
                responseGain = response.responseGain,
                mapCells = suggestion.mapChanges.map { "${it.row}:${it.column}" }.distinct().sorted(),
                curveIndexes = suggestion.curveChanges.map { it.index }.distinct().sorted(),
                status = response.status,
            )
        }
        .sortedBy { it.appliedAtMs }
        .toList()

private data class CausalResponse(
    val postErrorPercent: Double?,
    val responseGain: Double?,
    val status: CausalTransitionStatus,
)

private fun classifyResponse(
    suggestion: LocalSuggestion,
    postComparisons: List<CalibrationFuelComparison>,
): CausalResponse {
    val stableErrors = when (suggestion.target) {
        SuggestionTarget.MAP_K -> suggestion.mapChanges.map { change ->
            LearningStability.mapCell(postComparisons, change.row, change.column)
        }.takeIf { it.isNotEmpty() && it.all { snapshot -> snapshot.state == LearningStabilityState.CONSOLIDATED } }
            ?.mapNotNull { it.consolidatedErrorPercent }
        SuggestionTarget.CURVE_K -> suggestion.curveChanges.map { change ->
            LearningStability.curvePoint(postComparisons, change.index)
        }.takeIf { snapshots ->
            snapshots.isNotEmpty() && snapshots.all { snapshot ->
                snapshot.state == LearningStabilityState.CONSOLIDATED &&
                    snapshot.rpmBandCount >= 2 && snapshot.mapBandCount >= 2
            }
        }?.mapNotNull { it.consolidatedErrorPercent }
    }
    if (stableErrors.isNullOrEmpty()) {
        return CausalResponse(null, null, CausalTransitionStatus.AWAITING_POST_EVIDENCE)
    }

    val post = stableErrors.average()
    val pre = suggestion.consolidatedErrorPercent
        ?: return CausalResponse(post, null, CausalTransitionStatus.INCONCLUSIVE)
    val stepPercent = interventionStepPercent(suggestion)
    if (!stepPercent.isFinite() || abs(stepPercent) <= 1e-9 || pre * stepPercent <= 0.0) {
        return CausalResponse(post, null, CausalTransitionStatus.INCONCLUSIVE)
    }

    val gain = (pre - post) / stepPercent
    val status = when {
        gain > 0.0 && abs(post) < abs(pre) -> CausalTransitionStatus.CONFIRMED
        gain < 0.0 && abs(post) > abs(pre) -> CausalTransitionStatus.CONTRADICTED
        else -> CausalTransitionStatus.INCONCLUSIVE
    }
    return CausalResponse(post, gain, status)
}

private fun interventionStepPercent(suggestion: LocalSuggestion): Double {
    val steps = when (suggestion.target) {
        SuggestionTarget.MAP_K -> suggestion.mapChanges.mapNotNull { change ->
            change.before.takeIf { it != 0 }?.let { before ->
                (change.after.toDouble() / before.toDouble() - 1.0) * 100.0
            }
        }
        SuggestionTarget.CURVE_K -> suggestion.curveChanges.mapNotNull { change ->
            change.before.takeIf { abs(it) > 1e-12 }?.let { before ->
                (change.after / before - 1.0) * 100.0
            }
        }
    }
    return if (steps.isEmpty()) Double.NaN else steps.average()
}

private fun applySuggestionMaterial(
    current: CalibrationState,
    suggestion: LocalSuggestion,
): CalibrationState {
    val nextRevision = current.revision.next(suggestion.target)
    return when (suggestion.target) {
        SuggestionTarget.CURVE_K -> {
            val curve = current.curveK.toMutableList()
            suggestion.curveChanges.forEach { change ->
                require(curve[change.index] == change.before)
                curve[change.index] = change.after
            }
            current.copy(revision = nextRevision, curveK = curve)
        }
        SuggestionTarget.MAP_K -> {
            val map = current.mapK.map { it.toMutableList() }.toMutableList()
            suggestion.mapChanges.forEach { change ->
                require(map[change.row][change.column] == change.before)
                map[change.row][change.column] = change.after
            }
            current.copy(revision = nextRevision, mapK = map.map { it.toList() })
        }
    }
}
