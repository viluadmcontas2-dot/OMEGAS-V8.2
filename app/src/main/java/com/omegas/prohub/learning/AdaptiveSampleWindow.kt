package com.omegas.prohub.learning

/** Regras puras da janela adaptativa; não altera as tolerâncias físicas. */
object AdaptiveSampleWindow {
    const val EARLY_QUALITY_MINIMUM = 0.85
    const val STANDARD_QUALITY_MINIMUM = 0.80
    const val MICRO_CANDIDATE_FRAMES = 4
    const val STANDARD_ACCEPT_FRAMES = 8

    enum class Stage {
        FORMING,
        FAST_ACCEPT,
        STANDARD_ACCEPT,
        FULL_ACCEPT,
    }

    fun minimumFrames(desiredFrames: Int): Int {
        require(desiredFrames in 6..30) { "Alvo de janela inválido: $desiredFrames" }
        return when {
            desiredFrames >= 18 -> 12
            desiredFrames >= 14 -> 9
            else -> 6
        }.coerceAtMost(desiredFrames)
    }

    fun acceptanceStage(
        sample: MotorSample,
        desiredFrames: Int,
        toleratedGapCount: Int,
        fullWindowRequired: Boolean,
        strongPetrolOscillationRatio: Double,
    ): Stage {
        if (sample.frameCount >= desiredFrames) return Stage.FULL_ACCEPT
        if (fullWindowRequired || toleratedGapCount > 0) return Stage.FORMING
        if (sample.diagnostics.petrolOscillationRatio > strongPetrolOscillationRatio) return Stage.FORMING
        if (sample.frameCount >= STANDARD_ACCEPT_FRAMES && sample.quality >= STANDARD_QUALITY_MINIMUM) {
            return Stage.STANDARD_ACCEPT
        }
        if (sample.frameCount >= minimumFrames(desiredFrames) && sample.quality >= EARLY_QUALITY_MINIMUM) {
            return Stage.FAST_ACCEPT
        }
        return Stage.FORMING
    }

    fun canAcceptEarly(
        sample: MotorSample,
        desiredFrames: Int,
        toleratedGapCount: Int,
        fullWindowRequired: Boolean,
        strongPetrolOscillationRatio: Double,
    ): Boolean = acceptanceStage(
        sample = sample,
        desiredFrames = desiredFrames,
        toleratedGapCount = toleratedGapCount,
        fullWindowRequired = fullWindowRequired,
        strongPetrolOscillationRatio = strongPetrolOscillationRatio,
    ) in setOf(Stage.FAST_ACCEPT, Stage.STANDARD_ACCEPT)
}
