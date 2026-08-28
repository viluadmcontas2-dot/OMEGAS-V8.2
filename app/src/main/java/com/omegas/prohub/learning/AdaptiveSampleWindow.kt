package com.omegas.prohub.learning

/** Regras puras da janela adaptativa; não altera as tolerâncias físicas. */
object AdaptiveSampleWindow {
    const val EARLY_QUALITY_MINIMUM = 0.85

    fun minimumFrames(desiredFrames: Int): Int {
        require(desiredFrames in 6..30) { "Alvo de janela inválido: $desiredFrames" }
        return when {
            desiredFrames >= 18 -> 12
            desiredFrames >= 14 -> 9
            else -> 6
        }.coerceAtMost(desiredFrames)
    }

    fun canAcceptEarly(
        sample: MotorSample,
        desiredFrames: Int,
        toleratedGapCount: Int,
        fullWindowRequired: Boolean,
        strongPetrolOscillationRatio: Double,
    ): Boolean =
        sample.frameCount < desiredFrames &&
            !fullWindowRequired &&
            toleratedGapCount == 0 &&
            sample.quality >= EARLY_QUALITY_MINIMUM &&
            sample.diagnostics.petrolOscillationRatio <= strongPetrolOscillationRatio
}
