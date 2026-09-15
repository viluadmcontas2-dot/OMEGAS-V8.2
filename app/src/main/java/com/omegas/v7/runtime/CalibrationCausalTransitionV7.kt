package com.omegas.v7.runtime

import java.security.MessageDigest

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
 * Ledger causal derivado de estado já persistido: sugestão aplicada + checkpoint
 * anterior são suficientes para reconstruir de forma determinística a transição.
 * Não cria uma segunda autoridade paralela no snapshot.
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
            CalibrationTransitionV7(
                suggestionId = suggestion.id,
                target = suggestion.target,
                appliedAtMs = suggestion.updatedAtMs,
                beforeRevision = before.revision,
                afterRevision = after.revision,
                beforeFingerprint = before.materialFingerprint(),
                afterFingerprint = after.materialFingerprint(),
                preErrorPercent = suggestion.consolidatedErrorPercent,
            )
        }
        .sortedBy { it.appliedAtMs }
        .toList()

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
