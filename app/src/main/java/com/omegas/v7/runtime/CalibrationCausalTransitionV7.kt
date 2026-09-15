package com.omegas.v7.runtime

import java.security.MessageDigest
import kotlin.math.abs

enum class CausalTransitionStatusV7 {
    AWAITING_POST_EVIDENCE,
    CONFIRMED,
    CONTRADICTED,
    INCONCLUSIVE,
}

data class CalibrationTransitionV7(
    val suggestionId: String,
    val target: SuggestionTargetV7,
    val appliedAtMs: Long,
    val beforeRevision: CalibrationRevisionV7,
    val afterRevision: CalibrationRevisionV7,
    val beforeFingerprint: String,
    val afterFingerprint: String,
    val preErrorPercent: Double?,
    val postErrorPercent: Double? = null,
    val responseGain: Double? = null,
    val mapCells: List<String> = emptyList(),
    val curveIndexes: List<Int> = emptyList(),
    val status: CausalTransitionStatusV7 = CausalTransitionStatusV7.AWAITING_POST_EVIDENCE,
)

/**
 * Identidade material da calibração. A revisão é metadado de causalidade;
 * a identidade física é definida pelo conteúdo completo de Curve K + Map K.
 */
fun CalibrationStateV7.materialFingerprint(): String {
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
val V7SessionState.calibrationTransitions: List<CalibrationTransitionV7>
    get() = suggestions.asSequence()
        .filter { it.lifecycle == SuggestionLifecycleV7.APPLIED }
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
            CalibrationTransitionV7(
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

private data class CausalResponseV7(
    val postErrorPercent: Double?,
    val responseGain: Double?,
    val status: CausalTransitionStatusV7,
)

private fun classifyResponse(
    suggestion: LocalSuggestionV7,
    postComparisons: List<FuelComparisonV7>,
): CausalResponseV7 {
    val stableErrors = when (suggestion.target) {
        SuggestionTargetV7.MAP_K -> suggestion.mapChanges.map { change ->
            LearningStabilityV7.mapCell(postComparisons, change.row, change.column)
        }.takeIf { it.isNotEmpty() && it.all { snapshot -> snapshot.state == LearningStabilityStateV7.CONSOLIDATED } }
            ?.mapNotNull { it.consolidatedErrorPercent }
        SuggestionTargetV7.CURVE_K -> suggestion.curveChanges.map { change ->
            LearningStabilityV7.curvePoint(postComparisons, change.index)
        }.takeIf { snapshots ->
            snapshots.isNotEmpty() && snapshots.all { snapshot ->
                snapshot.state == LearningStabilityStateV7.CONSOLIDATED &&
                    snapshot.rpmBandCount >= 2 && snapshot.mapBandCount >= 2
            }
        }?.mapNotNull { it.consolidatedErrorPercent }
    }
    if (stableErrors.isNullOrEmpty()) {
        return CausalResponseV7(null, null, CausalTransitionStatusV7.AWAITING_POST_EVIDENCE)
    }

    val post = stableErrors.average()
    val pre = suggestion.consolidatedErrorPercent
        ?: return CausalResponseV7(post, null, CausalTransitionStatusV7.INCONCLUSIVE)
    val stepPercent = interventionStepPercent(suggestion)
    if (!stepPercent.isFinite() || abs(stepPercent) <= 1e-9 || pre * stepPercent <= 0.0) {
        return CausalResponseV7(post, null, CausalTransitionStatusV7.INCONCLUSIVE)
    }

    val gain = (pre - post) / stepPercent
    val status = when {
        gain > 0.0 && abs(post) < abs(pre) -> CausalTransitionStatusV7.CONFIRMED
        gain < 0.0 && abs(post) > abs(pre) -> CausalTransitionStatusV7.CONTRADICTED
        else -> CausalTransitionStatusV7.INCONCLUSIVE
    }
    return CausalResponseV7(post, gain, status)
}

private fun interventionStepPercent(suggestion: LocalSuggestionV7): Double {
    val steps = when (suggestion.target) {
        SuggestionTargetV7.MAP_K -> suggestion.mapChanges.mapNotNull { change ->
            change.before.takeIf { it != 0 }?.let { before ->
                (change.after.toDouble() / before.toDouble() - 1.0) * 100.0
            }
        }
        SuggestionTargetV7.CURVE_K -> suggestion.curveChanges.mapNotNull { change ->
            change.before.takeIf { abs(it) > 1e-12 }?.let { before ->
                (change.after / before - 1.0) * 100.0
            }
        }
    }
    return if (steps.isEmpty()) Double.NaN else steps.average()
}

private fun applySuggestionMaterial(
    current: CalibrationStateV7,
    suggestion: LocalSuggestionV7,
): CalibrationStateV7 {
    val nextRevision = current.revision.next(suggestion.target)
    return when (suggestion.target) {
        SuggestionTargetV7.CURVE_K -> {
            val curve = current.curveK.toMutableList()
            suggestion.curveChanges.forEach { change ->
                require(curve[change.index] == change.before)
                curve[change.index] = change.after
            }
            current.copy(revision = nextRevision, curveK = curve)
        }
        SuggestionTargetV7.MAP_K -> {
            val map = current.mapK.map { it.toMutableList() }.toMutableList()
            suggestion.mapChanges.forEach { change ->
                require(map[change.row][change.column] == change.before)
                map[change.row][change.column] = change.after
            }
            current.copy(revision = nextRevision, mapK = map.map { it.toList() })
        }
    }
}
