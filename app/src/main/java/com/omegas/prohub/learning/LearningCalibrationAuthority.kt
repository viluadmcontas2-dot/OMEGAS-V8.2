package com.omegas.prohub.learning

import java.util.concurrent.atomic.AtomicReference

/**
 * Ponte process-local entre a identidade física já reconciliada e o Learning.
 * Não cria identidade, não lê ECU e não escreve nada: apenas publica/retira a
 * fotografia imutável produzida pela CalibrationIdentity.
 */
object LearningCalibrationAuthority {
    private val current = AtomicReference<LearningCalibrationBinding?>(null)

    fun publish(binding: LearningCalibrationBinding) {
        current.set(binding)
    }

    fun clear() {
        current.set(null)
    }

    fun snapshot(): LearningCalibrationBinding? = current.get()
}
