package com.omegas.prohub.ecu

/**
 * Contrato read-only dos vetores físicos que dão significado ao mapa K 0x0054.
 *
 * O ProgBase/MP48 expõe 12 referências de tempo de injeção em TEMPI_PER_K
 * (0x0037) e 12 referências de rotação em GIRI_PER_K (0x003D). Esses vetores
 * são configuráveis na ECU e, portanto, não podem ser substituídos por uma
 * tabela hardcoded como autoridade de runtime.
 */
object KMapGeometryProtocol {
    const val TIME_AXIS_ADDRESS = 0x0037
    const val RPM_AXIS_ADDRESS = 0x003D
    const val POINT_COUNT = Mp48Protocol.MAP_COLUMNS
    const val BYTES_PER_POINT = 2
    const val PAYLOAD_SIZE = POINT_COUNT * BYTES_PER_POINT
    const val TIME_MS_PER_COUNT = 0.00256

    fun readTimeAxis(): ByteArray =
        Mp48Protocol.frame(byteArrayOf(0x29, 0x37, 0x00))

    fun readRpmAxis(): ByteArray =
        Mp48Protocol.frame(byteArrayOf(0x29, 0x3D, 0x00))

    /**
     * Decodifica exatamente 12 U16 little-endian.
     *
     * Payload truncado ou com bytes extras não é aceito como geometria válida:
     * um snapshot parcial não pode receber status KNOWN por tolerância de parser.
     */
    fun decodeRawAxis(payload: ByteArray): IntArray {
        require(payload.size == PAYLOAD_SIZE) {
            "Eixo do mapa K exige exatamente $PAYLOAD_SIZE bytes; recebidos ${payload.size}"
        }
        return IntArray(POINT_COUNT) { index ->
            val offset = index * BYTES_PER_POINT
            (payload[offset].toInt() and 0xFF) or
                ((payload[offset + 1].toInt() and 0xFF) shl 8)
        }
    }

    fun decodeTimeAxisMs(payload: ByteArray): DoubleArray =
        decodeRawAxis(payload).map(::timeMsFromRaw).toDoubleArray()

    fun decodeRpmAxis(payload: ByteArray): IntArray = decodeRawAxis(payload)

    fun timeMsFromRaw(raw: Int): Double {
        require(raw in 0..0xFFFF) { "TEMPI_PER_K raw inválido: $raw" }
        return raw * TIME_MS_PER_COUNT
    }
}
