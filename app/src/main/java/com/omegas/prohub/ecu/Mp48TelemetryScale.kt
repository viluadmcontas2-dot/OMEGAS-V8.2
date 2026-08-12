package com.omegas.prohub.ecu

/**
 * Conversões físicas comprovadas para o pacote de telemetria MP48 0x48 0x01.
 *
 * Esta é a única autoridade de cálculo das escalas. A interface recebe valores
 * já convertidos pelo Kotlin e não replica fórmulas em JavaScript.
 */
object Mp48TelemetryScale {
    const val INJECTION_MS_PER_COUNT = 0.00256
    const val GAS_PRESSURE_COUNTS_PER_BAR = 800.0
    const val MAP_COUNTS_PER_BAR = 1_000.0
    const val WATER_OFFSET_C = 109
    const val GAS_TEMPERATURE_OFFSET_C = 20

    fun injectionMs(raw: Int): Double {
        require(raw >= 0) { "Contagem de injeção inválida: $raw" }
        return raw * INJECTION_MS_PER_COUNT
    }

    fun waterC(raw: Int): Int {
        require(raw in 0..255) { "Temperatura de água bruta inválida: $raw" }
        return WATER_OFFSET_C - raw
    }

    fun gasC(raw: Int): Int {
        require(raw in 0..255) { "Temperatura de gás bruta inválida: $raw" }
        return raw - GAS_TEMPERATURE_OFFSET_C
    }

    fun gasPressureAbsBar(raw: Int): Double {
        require(raw >= 0) { "Pressão de gás bruta inválida: $raw" }
        return raw / GAS_PRESSURE_COUNTS_PER_BAR
    }

    fun mapBar(raw: Int): Double {
        require(raw >= 0) { "MAP bruto inválido: $raw" }
        return raw / MAP_COUNTS_PER_BAR
    }

    fun levelPercentage(raw: Int): Int {
        require(raw in 0..255) { "Nível de gás bruto inválido: $raw" }
        // Sensores padrão Landi Renzo/AEB (ex: 1050) operam de forma invertida.
        // ADC aumenta conforme a pressão/volume de gás diminui no cilindro.
        return ((255 - raw) * 100) / 255
    }
}

