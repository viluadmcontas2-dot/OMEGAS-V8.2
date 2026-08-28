package com.omegas.prohub.ecu

/** Protocolo e escalas da curva azul K factor do ProgBase. */
object KFactorProtocol {
    const val FACTOR_ADDRESS = 0x0161
    const val AXIS_ADDRESS = 0x014B
    const val POINT_COUNT = 30
    const val BYTES_PER_POINT = 2
    const val PAYLOAD_SIZE = POINT_COUNT * BYTES_PER_POINT
    const val Q14_SCALE = 16_384
    const val Q14_ONE = 16_384.0
    const val MAX_RAW = 0xFFFF
    const val MAX_FACTOR = MAX_RAW / Q14_ONE
    const val AXIS_COUNTS_PER_MS = 512.0

    /** Eixo observado no bloco 0x014B; depois de 10 ms ele deixa de ser uniforme. */
    val OBSERVED_PETROL_AXIS_MS = doubleArrayOf(
        0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0,
        5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0,
        11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 20.0, 22.0,
    )

    fun readFactors(): ByteArray = Mp48Protocol.frame(byteArrayOf(0x29, 0x61, 0x01))

    fun readPetrolAxis(): ByteArray = Mp48Protocol.frame(byteArrayOf(0x29, 0x4B, 0x01))

    fun writeFactor(index: Int, rawQ14: Int): ByteArray {
        require(index in 0 until POINT_COUNT) { "Índice K factor inválido: $index" }
        require(rawQ14 in 0..MAX_RAW) { "K factor Q14 inválido: $rawQ14" }
        return Mp48Protocol.frame(
            byteArrayOf(
                0x14,
                0x61,
                0x01,
                index.toByte(),
                (rawQ14 and 0xFF).toByte(),
                ((rawQ14 ushr 8) and 0xFF).toByte(),
            ),
        )
    }

    fun decodeRawPoints(payload: ByteArray): IntArray {
        require(payload.size >= PAYLOAD_SIZE) {
            "Curva K factor incompleta: ${payload.size}/$PAYLOAD_SIZE bytes"
        }
        return IntArray(POINT_COUNT) { index ->
            val offset = index * 2
            (payload[offset].toInt() and 0xFF) or
                ((payload[offset + 1].toInt() and 0xFF) shl 8)
        }
    }

    fun decodePetrolAxisMs(payload: ByteArray): DoubleArray =
        decodeRawPoints(payload).map(::petrolMsFromAxisRaw).toDoubleArray()

    fun factorFromRaw(rawQ14: Int): Double {
        require(rawQ14 in 0..MAX_RAW) { "K factor Q14 inválido: $rawQ14" }
        return rawQ14 / Q14_ONE
    }

    /**
     * Converte para Q14 com truncamento em direção a zero.
     *
     * O fator é sempre não negativo, portanto esse comportamento equivale a
     * floor(fator * 16384), preservando a aritmética inteira reconstruída da ECU.
     * O único teto é o próprio campo U16 do protocolo.
     */
    fun rawFromFactor(factor: Double): Int {
        require(factor.isFinite() && factor >= 0.0) { "K factor inválido: $factor" }
        return (factor * Q14_SCALE)
            .coerceIn(0.0, MAX_RAW.toDouble())
            .toInt()
    }

    fun petrolMsFromAxisRaw(raw: Int): Double {
        require(raw in 0..0xFFFF) { "Eixo Petrol Inj inválido: $raw" }
        return raw / AXIS_COUNTS_PER_MS
    }

    fun blendAxis(petrolMs: Double, axisMs: DoubleArray = OBSERVED_PETROL_AXIS_MS): Triple<Int, Int, Double> {
        require(axisMs.size == POINT_COUNT) { "Eixo K factor incompleto" }
        if (petrolMs <= axisMs.first()) return Triple(0, 0, 0.0)
        if (petrolMs >= axisMs.last()) return Triple(axisMs.lastIndex, axisMs.lastIndex, 0.0)
        val upper = axisMs.indexOfFirst { it >= petrolMs }.coerceAtLeast(1)
        val lower = upper - 1
        val span = (axisMs[upper] - axisMs[lower]).coerceAtLeast(1e-9)
        return Triple(lower, upper, ((petrolMs - axisMs[lower]) / span).coerceIn(0.0, 1.0))
    }

    fun interpolateFactor(
        petrolMs: Double,
        rawFactors: IntArray,
        axisMs: DoubleArray = OBSERVED_PETROL_AXIS_MS,
    ): Double {
        require(rawFactors.size == POINT_COUNT) { "Curva K factor incompleta" }
        val (lower, upper, fraction) = blendAxis(petrolMs, axisMs)
        val first = factorFromRaw(rawFactors[lower])
        if (lower == upper) return first
        val second = factorFromRaw(rawFactors[upper])
        return first + (second - first) * fraction
    }
}
