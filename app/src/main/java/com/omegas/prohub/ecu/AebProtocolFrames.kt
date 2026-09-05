package com.omegas.prohub.ecu

import com.omegas.prohub.calibration.CalibrationShape

/**
 * Quadros AEB reconstruídos do ProgBase e validados contra tráfego serial real.
 * O checksum é a soma dos bytes anteriores truncada em U8.
 */
object AebProtocolFrames {
    const val ADDRESS_MAP_K = 0x0054
    const val ADDRESS_AUTOCAL_INJECTION_AXIS = 0x014B
    const val ADDRESS_AUTOCAL_PRESSURE_THRESHOLDS = 0x014C
    const val ADDRESS_MUL_ACT = 0x0161
    const val ADDRESS_PETROL_PRESSURE_RESAMPLED = 0x018D
    const val ADDRESS_CNG_PRESSURE_RESAMPLED = 0x018E

    fun getNumber(address: Int): ByteArray = addressed(0x09, address)
    fun getNumber(address: Int, index: Int): ByteArray = addressed(0x0A, address, byte(index))
    fun getNumber(address: Int, row: Int, column: Int): ByteArray =
        addressed(0x0B, address, byte(row), byte(column))
    fun getVector(address: Int): ByteArray = addressed(0x29, address)
    fun getVector(address: Int, row: Int): ByteArray = addressed(0x2A, address, byte(row))

    fun setNumber(address: Int, body: ByteArray): ByteArray {
        require(body.isNotEmpty() && body.size <= 6)
        return addressed(0x11 + body.size, address, *body)
    }

    fun setVector(address: Int, payload: ByteArray): ByteArray {
        requireAddress(address)
        return if (payload.size <= 5) {
            withChecksum(byteArrayOf((0x31 + payload.size).toByte(), low(address), high(address)) + payload)
        } else {
            val blockLength = payload.size + 2
            require(blockLength <= 0xFF)
            withChecksum(
                byteArrayOf(0x37, low(address), blockLength.toByte(), high(address)) + payload,
            )
        }
    }

    fun readMapRow(row: Int): ByteArray {
        require(row in 0 until CalibrationShape.MAP_K_STORAGE_ROWS)
        return getVector(ADDRESS_MAP_K, row)
    }

    fun writeMapCell(row: Int, column: Int, value: Int): ByteArray {
        CalibrationShape.requireEditableCell(row, column)
        require(value in 0..0xFF)
        return setNumber(
            ADDRESS_MAP_K,
            byteArrayOf(row.toByte(), column.toByte(), value.toByte()),
        )
    }

    fun writeMapRow(row: Int, values: List<Int>): ByteArray {
        require(row in 0 until CalibrationShape.MAP_K_STORAGE_ROWS)
        require(values.size == CalibrationShape.MAP_K_COLUMNS)
        require(values.all { it in 0..0xFF })
        return setVector(ADDRESS_MAP_K, byteArrayOf(row.toByte()) + values.map { it.toByte() }.toByteArray())
    }

    fun readMulAct(): ByteArray = getVector(ADDRESS_MUL_ACT)

    fun writeMulActPoint(index: Int, rawU16: Int): ByteArray {
        require(index in 0 until CalibrationShape.CURVE_K_POINTS)
        require(rawU16 in 0..0xFFFF)
        return setNumber(
            ADDRESS_MUL_ACT,
            byteArrayOf(index.toByte(), low(rawU16), high(rawU16)),
        )
    }

    fun writeMulAct(rawU16: List<Int>): ByteArray {
        require(rawU16.size == CalibrationShape.CURVE_K_POINTS)
        require(rawU16.all { it in 0..0xFFFF })
        val payload = ByteArray(rawU16.size * 2)
        rawU16.forEachIndexed { index, value ->
            payload[index * 2] = low(value)
            payload[index * 2 + 1] = high(value)
        }
        return setVector(ADDRESS_MUL_ACT, payload)
    }

    fun checksumIsValid(frame: ByteArray): Boolean =
        frame.isNotEmpty() && checksum(frame.copyOf(frame.size - 1)) == frame.last()

    private fun addressed(opcode: Int, address: Int, vararg body: Byte): ByteArray {
        requireAddress(address)
        return withChecksum(byteArrayOf(opcode.toByte(), low(address), high(address)) + body)
    }

    private fun withChecksum(content: ByteArray): ByteArray = content + checksum(content)
    private fun checksum(content: ByteArray): Byte =
        content.fold(0) { sum, value -> (sum + value.toUByte().toInt()) and 0xFF }.toByte()
    private fun requireAddress(address: Int) = require(address in 0..0xFFFF)
    private fun low(value: Int): Byte = (value and 0xFF).toByte()
    private fun high(value: Int): Byte = ((value ushr 8) and 0xFF).toByte()
    private fun byte(value: Int): Byte {
        require(value in 0..0xFF)
        return value.toByte()
    }
}
