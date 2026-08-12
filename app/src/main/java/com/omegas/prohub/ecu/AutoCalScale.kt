package com.omegas.prohub.ecu

/**
 * Conversões físicas dos objetos AutoCal lidos separadamente da telemetria 0x48 0x01.
 *
 * Estas escalas não substituem [Mp48TelemetryScale]. O pacote rápido de telemetria
 * e os vetores AutoCal usam representações diferentes no protocolo observado.
 */
object AutoCalScale {
    const val INJECTION_COUNTS_PER_MS = 512.0
    const val MAP_COUNTS_PER_BAR = 1_024.0
    const val Q14_COUNTS_PER_FACTOR = 16_384.0

    fun injectionMs(raw: Int): Double {
        require(raw in 0..0xFFFF) { "Tempo AutoCal bruto inválido: $raw" }
        return raw / INJECTION_COUNTS_PER_MS
    }

    fun mapBar(rawSigned: Int): Double {
        require(rawSigned in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt()) {
            "MAP AutoCal S16 inválido: $rawSigned"
        }
        return rawSigned / MAP_COUNTS_PER_BAR
    }

    fun multiplierFromRaw(rawQ14: Int): Double {
        require(rawQ14 in 0..0xFFFF) { "Multiplicador AutoCal Q14 inválido: $rawQ14" }
        return rawQ14 / Q14_COUNTS_PER_FACTOR
    }
}
