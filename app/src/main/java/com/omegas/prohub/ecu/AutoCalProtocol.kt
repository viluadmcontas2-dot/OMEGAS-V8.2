package com.omegas.prohub.ecu

/**
 * Contrato somente leitura dos objetos AutoCal já identificados.
 *
 * Não contém reset, ações, escrita ou acesso à porta USB.
 * Shape é validado pela identidade física de cada field. MODULE_VERSION é
 * preservado como dado observado, mas não reescreve globalmente famílias 18/30.
 */
object AutoCalProtocol {
    const val READ_SCALAR = 0x09
    const val READ_VECTOR = 0x29
    const val READ_INDEXED = 0x0A
    const val WRITE_U8 = 0x12

    /** Probe leve observado no ProgBase; payload de 14 bytes. */
    val CMD_NATIVE_STATUS = byteArrayOf(0x48, 0x0B, 0x53)

    enum class Encoding(val bytesPerElement: Int) {
        U8(1),
        U16_LE(2),
        S16_LE(2),
        Q14_U16_LE(2),
        /** Algumas firmwares retornam o contador 0x0174 em 1 byte e outras em 2. */
        U8_OR_U16_LE(0),
    }

    enum class Shape {
        SCALAR,
        VECTOR,
        INDEXED,
    }

    data class Field(
        val key: String,
        val address: Int,
        val encoding: Encoding,
        val shape: Shape,
        val expectedElementsHint: Int? = null,
        val physicalUnit: String = "RAW",
        val index: Int? = null,
    ) {
        init {
            require(address in 0..0xFFFF) { "Endereço AutoCal inválido: $address" }
            require(expectedElementsHint == null || expectedElementsHint > 0)
            if (shape == Shape.INDEXED) require(index != null && index in 0..0xFF)
        }

        /** Identidade lógica completa. Endereço sozinho não distingue 0x0165:1 de 0x0165:2. */
        val identity: String
            get() = "%04X:%s".format(address, index?.let { "%02X".format(it) } ?: "--")
    }

    data class Decoded(
        val field: Field,
        val rawPayload: ByteArray,
        val rawValues: IntArray,
        val physicalValues: DoubleArray,
    ) {
        val elementCount: Int get() = rawValues.size
    }

    val AUTO_CAL_ENABLE = Field("AUTO_CAL_ENABLE", 0x014A, Encoding.U8, Shape.SCALAR, 1)
    val PETR_INJ_TBP = Field("PETR_INJ_TBP", 0x014B, Encoding.U16_LE, Shape.VECTOR, 30, "MS")
    val MNFLD_PRESS_THD = Field("MNFLD_PRESS_THD", 0x014C, Encoding.S16_LE, Shape.VECTOR, 18, "BAR")
    val NUM_BUF_UPD_PETR = Field("NUM_BUF_UPD_PETR", 0x015B, Encoding.U16_LE, Shape.VECTOR, 18)
    val NUM_BUF_UPD_GAS = Field("NUM_BUF_UPD_GAS", 0x015C, Encoding.U16_LE, Shape.VECTOR, 18)
    val VECT_AUTOCAL_U8_1 = Field("VECT_AUTOCAL_U8_1", 0x0165, Encoding.U8, Shape.INDEXED, 1, index = 1)
    val MAX_AUTOMATCH = Field("MAX_AUTOMATCH", 0x0165, Encoding.U8, Shape.INDEXED, 1, index = 2)
    /** Alias de compatibilidade para snapshots/testes antigos; 0x0165:2 é MaxAutomatch. */
    val VECT_AUTOCAL_U8_2 = MAX_AUTOMATCH
    val PETR_INJ_TBUF_GAS_PREV = Field("PETR_INJ_TBUF_GAS_PREV", 0x015D, Encoding.U16_LE, Shape.VECTOR, 18, "MS")
    val MNFLD_PRESS_BUF_GAS_PREV = Field("MNFLD_PRESS_BUF_GAS_PREV", 0x015E, Encoding.S16_LE, Shape.VECTOR, 18, "BAR")
    val PETR_INJ_TBUF_GAS = Field("PETR_INJ_TBUF_GAS", 0x015F, Encoding.U16_LE, Shape.VECTOR, 18, "MS")
    val MNFLD_PRESS_BUF_GAS = Field("MNFLD_PRESS_BUF_GAS", 0x0160, Encoding.S16_LE, Shape.VECTOR, 18, "BAR")
    val MUL_ACT = Field("MUL_ACT", 0x0161, Encoding.Q14_U16_LE, Shape.VECTOR, 30, "FACTOR")
    val PETR_INJ_TBUF = Field("PETR_INJ_TBUF", 0x0162, Encoding.U16_LE, Shape.VECTOR, 18, "MS")
    val MNFLD_PRESS_BUF = Field("MNFLD_PRESS_BUF", 0x0163, Encoding.S16_LE, Shape.VECTOR, 18, "BAR")

    /**
     * CP161: TAebNumber, DataLength=2, DataMask=65535, SerialCode=0x0167,
     * transformação raw/1024. Unidade/consumer/semântica física permanecem UNKNOWN.
     */
    val RAW_AUTOCAL_0167 = Field(
        "RAW_AUTOCAL_0167",
        0x0167,
        Encoding.U16_LE,
        Shape.SCALAR,
        1,
        "RAW_DIV_1024_UNKNOWN",
    )

    val ACQUIRED_ZONES_PETROL = Field("ACQUIRED_ZONES_PETROL", 0x016F, Encoding.U8, Shape.VECTOR, 4)
    val ACQUIRED_ZONES_GAS = Field("ACQUIRED_ZONES_GAS", 0x0170, Encoding.U8, Shape.VECTOR, 4)
    val CALIBRATION_VAL_1 = Field("CALIBRATION_VAL_1", 0x0172, Encoding.U8, Shape.VECTOR, 10)
    val MODULE_VERSION = Field("MODULE_VERSION", 0x0173, Encoding.U8, Shape.SCALAR, 1)
    val NUM_AUTOMATCH_EXECUTED = Field("NUM_AUTOMATCH_EXECUTED", 0x0174, Encoding.U8_OR_U16_LE, Shape.SCALAR, 1)
    val MAX_RPM_FOR_AUTOCAL = Field("MAX_RPM_FOR_AUTOCAL", 0x017A, Encoding.U16_LE, Shape.SCALAR, 1)
    val PETR_MNFLD_PRESS_RV = Field("PETR_MNFLD_PRESS_RV", 0x018D, Encoding.S16_LE, Shape.VECTOR, 30, "BAR")
    val GAS_MNFLD_PRESS_RV = Field("GAS_MNFLD_PRESS_RV", 0x018E, Encoding.S16_LE, Shape.VECTOR, 30, "BAR")

    val ACQUISITION_18_FIELDS: Set<String> = setOf(
        NUM_BUF_UPD_PETR.identity,
        NUM_BUF_UPD_GAS.identity,
        PETR_INJ_TBUF_GAS_PREV.identity,
        MNFLD_PRESS_BUF_GAS_PREV.identity,
        PETR_INJ_TBUF_GAS.identity,
        MNFLD_PRESS_BUF_GAS.identity,
        PETR_INJ_TBUF.identity,
        MNFLD_PRESS_BUF.identity,
        MNFLD_PRESS_THD.identity,
    )

    val REFERENCE_30_FIELDS: Set<String> = setOf(
        PETR_INJ_TBP.identity,
        MUL_ACT.identity,
        PETR_MNFLD_PRESS_RV.identity,
        GAS_MNFLD_PRESS_RV.identity,
    )

    /**
     * Leitura somente observacional. MODULE_VERSION permanece primeiro apenas
     * como provenance/version observada; shape é propriedade do field.
     */
    val READ_ONLY_FIELDS: List<Field> = listOf(
        MODULE_VERSION,
        PETR_INJ_TBP,
        MNFLD_PRESS_THD,
        MUL_ACT,
        PETR_MNFLD_PRESS_RV,
        GAS_MNFLD_PRESS_RV,
        AUTO_CAL_ENABLE,
        NUM_BUF_UPD_PETR,
        NUM_BUF_UPD_GAS,
        VECT_AUTOCAL_U8_1,
        MAX_AUTOMATCH,
        PETR_INJ_TBUF_GAS_PREV,
        MNFLD_PRESS_BUF_GAS_PREV,
        PETR_INJ_TBUF_GAS,
        MNFLD_PRESS_BUF_GAS,
        PETR_INJ_TBUF,
        MNFLD_PRESS_BUF,
        RAW_AUTOCAL_0167,
        CALIBRATION_VAL_1,
        ACQUIRED_ZONES_PETROL,
        ACQUIRED_ZONES_GAS,
        NUM_AUTOMATCH_EXECUTED,
        MAX_RPM_FOR_AUTOCAL,
    )

    /**
     * Compatibilidade de assinatura: moduleVersion é deliberadamente ignorada
     * para cardinalidade. O owner 103A fechou que 18/30 pertence à identidade
     * física do field; o corpus real contém moduleVersion=100 com vetores 30 válidos.
     */
    fun expectedElements(field: Field, moduleVersion: Int?): Int? {
        @Suppress("UNUSED_VARIABLE") val observedModuleVersion = moduleVersion
        return field.expectedElementsHint
    }

    fun requireExpectedShape(decoded: Decoded, moduleVersion: Int?) {
        val expected = expectedElements(decoded.field, moduleVersion) ?: return
        require(decoded.elementCount == expected) {
            "${decoded.field.key}: ${decoded.elementCount} elementos; esperado $expected pela identidade física do field; MODULE_VERSION observado=${moduleVersion ?: "UNKNOWN"}"
        }
    }

    data class NativeStatus(
        val nativeFlag13: Int,
        val autoMatchCount: Int,
        val rawPayload: ByteArray,
    )

    /** Frames confirmados no PortmonLOGNOVO: 12 4A 01 01 5E / 12 4A 01 00 5D. */
    fun setEnabled(enabled: Boolean): ByteArray = frameWriteU8(AUTO_CAL_ENABLE.address, if (enabled) 1 else 0)

    fun decodeNativeStatus(status: Int, payload: ByteArray): NativeStatus {
        require(status == Mp48Protocol.STATUS_ACK) {
            "Status compacto AutoCal inesperado: 0x%02X".format(status)
        }
        require(payload.size == 14) {
            "Status compacto AutoCal exige 14 bytes; recebidos ${payload.size}"
        }
        return NativeStatus(
            nativeFlag13 = payload[12].toInt() and 0xFF,
            autoMatchCount = payload[13].toInt() and 0xFF,
            rawPayload = payload.copyOf(),
        )
    }

    private fun frameWriteU8(address: Int, value: Int): ByteArray {
        require(address in 0..0xFFFF)
        require(value in 0..0xFF)
        return Mp48Protocol.frame(
            byteArrayOf(
                WRITE_U8.toByte(),
                (address and 0xFF).toByte(),
                ((address ushr 8) and 0xFF).toByte(),
                value.toByte(),
            ),
        )
    }

    fun read(field: Field): ByteArray = when (field.shape) {
        Shape.SCALAR -> readScalar(field.address)
        Shape.VECTOR -> readVector(field.address)
        Shape.INDEXED -> readIndexed(field.address, field.index!!)
    }

    fun readScalar(address: Int): ByteArray = genericRead(READ_SCALAR, address)
    fun readVector(address: Int): ByteArray = genericRead(READ_VECTOR, address)

    fun readIndexed(address: Int, index: Int): ByteArray {
        require(index in 0..0xFF)
        return Mp48Protocol.frame(
            byteArrayOf(
                READ_INDEXED.toByte(),
                (address and 0xFF).toByte(),
                ((address ushr 8) and 0xFF).toByte(),
                index.toByte(),
            ),
        )
    }

    private fun genericRead(command: Int, address: Int): ByteArray {
        require(command == READ_SCALAR || command == READ_VECTOR)
        require(address in 0..0xFFFF) { "Endereço AutoCal inválido: $address" }
        return Mp48Protocol.frame(
            byteArrayOf(
                command.toByte(),
                (address and 0xFF).toByte(),
                ((address ushr 8) and 0xFF).toByte(),
            ),
        )
    }

    fun decode(field: Field, status: Int, payload: ByteArray): Decoded {
        require(status == Mp48Protocol.STATUS_ACK) {
            "Status AutoCal inesperado: 0x%02X".format(status)
        }
        require(payload.isNotEmpty()) { "${field.key}: payload vazio" }

        val raw = when (field.encoding) {
            Encoding.U8_OR_U16_LE -> {
                require(payload.size == 1 || payload.size == 2) {
                    "${field.key}: contador retornou ${payload.size} bytes; esperado 1 ou 2"
                }
                intArrayOf(if (payload.size == 1) u8(payload, 0) else u16le(payload, 0))
            }
            else -> {
                val width = field.encoding.bytesPerElement
                require(payload.size % width == 0) {
                    "${field.key}: ${payload.size} bytes não são divisíveis por $width"
                }
                val count = payload.size / width
                IntArray(count) { index ->
                    val offset = index * width
                    when (field.encoding) {
                        Encoding.U8 -> u8(payload, offset)
                        Encoding.U16_LE, Encoding.Q14_U16_LE -> u16le(payload, offset)
                        Encoding.S16_LE -> s16le(payload, offset)
                        Encoding.U8_OR_U16_LE -> error("tratado acima")
                    }
                }
            }
        }

        if (field.shape == Shape.SCALAR || field.shape == Shape.INDEXED) {
            require(raw.size == 1) { "${field.key}: escalar retornou ${raw.size} elementos" }
        }
        val physical = DoubleArray(raw.size) { index ->
            when (field.physicalUnit) {
                "MS" -> AutoCalScale.injectionMs(raw[index])
                "BAR" -> AutoCalScale.mapBar(raw[index])
                "FACTOR" -> AutoCalScale.multiplierFromRaw(raw[index])
                "RAW_DIV_1024_UNKNOWN" -> raw[index] / 1024.0
                else -> raw[index].toDouble()
            }
        }
        return Decoded(field, payload.copyOf(), raw, physical)
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF

    private fun u16le(bytes: ByteArray, offset: Int): Int =
        u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    private fun s16le(bytes: ByteArray, offset: Int): Int = u16le(bytes, offset).toShort().toInt()
}