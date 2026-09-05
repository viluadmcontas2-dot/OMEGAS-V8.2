package com.omegas.prohub.diagnostics

enum class SessionRelevance { PROBE, VALID, PROTECTED }

/**
 * Relevância é evidência, não tamanho bruto de arquivo nem número de conexões.
 * Uma sessão com calibração confirmada/readback nunca é candidata a pruning.
 */
object SessionRelevancePolicy {
    const val MIN_VALID_TELEMETRY_FRAMES = 20L
    const val MIN_VALID_DURATION_MS = 5_000L

    fun classify(
        telemetryFrames: Long,
        durationMs: Long,
        protectedEvidence: Boolean,
        explicitlyProtected: Boolean = false,
    ): SessionRelevance = when {
        protectedEvidence || explicitlyProtected -> SessionRelevance.PROTECTED
        telemetryFrames >= MIN_VALID_TELEMETRY_FRAMES && durationMs >= MIN_VALID_DURATION_MS -> SessionRelevance.VALID
        else -> SessionRelevance.PROBE
    }
}
