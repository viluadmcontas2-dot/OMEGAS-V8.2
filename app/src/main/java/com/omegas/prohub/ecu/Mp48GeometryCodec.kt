package com.omegas.prohub.ecu

/**
 * Codec puro dos vetores físicos usados pela geometria do Mapa K.
 *
 * O contrato MP48 observado expõe exatamente 12 valores U16 little-endian
 * por vetor. Este codec preserva os raws sem aplicar fallback.
 */
object Mp48GeometryCodec {
    const val AXIS_POINTS = 12
    const val AXIS_PAYLOAD_BYTES = AXIS_POINTS * 2
    const val TIME_AXIS_MS_PER_RAW = 0.00256

    fun decodeAxisRaw(payload: ByteArray): IntArray {
        require(payload.size == AXIS_PAYLOAD_BYTES) {
            "Eixo MP48 exige exatamente $AXIS_PAYLOAD_BYTES bytes; recebidos ${payload.size}"
        }
        return IntArray(AXIS_POINTS) { index ->
            val offset = index * 2
            (payload[offset].toInt() and 0xFF) or
                ((payload[offset + 1].toInt() and 0xFF) shl 8)
        }
    }

    fun timeAxisMs(raw: IntArray): DoubleArray {
        require(raw.size == AXIS_POINTS) {
            "Eixo de tempo MP48 exige exatamente $AXIS_POINTS raws; recebidos ${raw.size}"
        }
        return DoubleArray(AXIS_POINTS) { index -> raw[index] * TIME_AXIS_MS_PER_RAW }
    }
}
