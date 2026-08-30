package com.omegas.prohub.calibration

/**
 * Política operacional escolhida para o OMEGAS RED.
 *
 * A ECU continua usando um campo U8 (0..255). Estes limites não descrevem o
 * protocolo: restringem somente os novos alvos que o aplicativo pode preparar.
 */
object KOperatingPolicy {
    const val MIN_TARGET_K = 100
    const val MAX_TARGET_K = 180

    fun isAllowedTarget(value: Int): Boolean = value in MIN_TARGET_K..MAX_TARGET_K
}
