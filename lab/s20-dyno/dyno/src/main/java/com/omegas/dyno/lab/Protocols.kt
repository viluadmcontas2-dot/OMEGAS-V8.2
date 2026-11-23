package com.omegas.dyno.lab

internal const val UNKNOWN = "—"

internal enum class ObdPid(val code: Int, val field: String, val bytes: Int = 1) {
    RPM(0x0C, "rpm", 2), SPEED(0x0D, "speed"), MAP(0x0B, "mapKpa"),
    TPS(0x11, "tps"), STFT(0x06, "stft"), LTFT(0x07, "ltft"), ECT(0x05, "ect"),
    ECU_VOLTAGE(0x42, "ecuVoltage", 2),
    FUEL_TYPE(0x51, "fuelTypeCode"),
    ETHANOL_PERCENT(0x52, "ethanolPercent");

    val command: String get() = "01" + code.toString(16).uppercase().padStart(2, '0')
}

internal object ObdProtocol {
    val adapterSetup = listOf("ATE0", "ATL0", "ATS1", "ATH0", "ATSP0")
    val discoveryCommands = mapOf(0 to "0100", 0x20 to "0120", 0x40 to "0140")
    private val hex = Regex("[0-9A-F]+")
    private val spaces = Regex("\\s+")

    fun bitmap(base: Int, response: String): Long? {
        require(base in discoveryCommands)
        val replies = responses(base, 4, response)
        return replies.takeIf { it.isNotEmpty() }?.fold(0L) { result, data ->
            result or data.fold(0L) { bits, byte -> (bits shl 8) or byte.toLong() }
        }
    }

    fun supports(bitmap: Long, offset: Int): Boolean =
        offset in 1..32 && bitmap and (1L shl (32 - offset)) != 0L

    fun decode(pid: ObdPid, response: String): Double? {
        val data = responses(pid.code, pid.bytes, response).distinct().singleOrNull() ?: return null
        val a = data[0]
        return when (pid) {
            ObdPid.RPM -> (a * 256 + data[1]) / 4.0
            ObdPid.TPS -> a * 100.0 / 255
            ObdPid.STFT, ObdPid.LTFT -> (a - 128) * 100.0 / 128
            ObdPid.ECT -> (a - 40).toDouble()
            ObdPid.ECU_VOLTAGE -> (a * 256 + data[1]) / 1000.0
            ObdPid.ETHANOL_PERCENT -> a * 100.0 / 255
            // Type code describes the ECU's reported fuel specification, not confirmed GNV state.
            ObdPid.FUEL_TYPE, ObdPid.SPEED, ObdPid.MAP -> a.toDouble()
        }
    }

    private fun responses(pid: Int, size: Int, response: String): List<List<Int>> =
        response.substringBefore('>').lineSequence().mapNotNull { raw ->
            val line = raw.uppercase().substringAfter("SEARCHING...").trim().replace(spaces, "")
            if (!hex.matches(line)) return@mapNotNull null
            val count = size + 2
            val header = when {
                line.length == count * 2 -> 0
                line.length % 2 == 1 && line.length >= 3 + (count + 1) * 2 -> 3
                line.startsWith("18DA") && line.length >= 8 + (count + 1) * 2 -> 8
                line.length == (count + 4) * 2 -> 6
                else -> return@mapNotNull null
            }
            val bytes = line.drop(header).chunked(2).map { it.toInt(16) }
            val payload = if (header == 3 || header == 8) {
                if (bytes[0] != count) return@mapNotNull null
                bytes.drop(1)
            } else bytes
            if (payload.getOrNull(0) != 0x41 || payload.getOrNull(1) != pid) return@mapNotNull null
            payload.drop(2).take(size).takeIf { it.size == size }
        }.toList()
}

internal enum class Mp48Command(private vararg val data: Int) {
    INIT_1(0x00, 0x02, 0x02), INIT_2(0x01, 0x00, 0x3A, 0x3B), TELEMETRY(0x48, 0x01, 0x49);

    fun bytes(): ByteArray = ByteArray(data.size) { data[it].toByte() }
}

internal enum class Fuel(val label: String) {
    PETROL("gasolina"), GNV("GNV"), TRANSITION("transição"), UNKNOWN("—");

    val verified get() = this == PETROL || this == GNV
}

internal data class Mp48Reading(
    val rpm: Int, val mapBar: Double, val gasPressureBar: Double,
    val petrolMs: Double, val gasMs: Double?, val fuel: Fuel, val fuelByte: Int,
)

internal object Mp48Protocol {
    const val MAX_PAYLOAD = 192

    fun replyLength(status: Int, secondByte: Int): Int = when (status) {
        0x96 -> 0
        0xCA -> 1
        else -> secondByte
    }

    fun payload(command: Mp48Command, frame: ByteArray, status: Int = 0x53): ByteArray? {
        val echo = command.bytes()
        val offset = echo.size
        if (frame.size < offset + 3 || !frame.copyOf(offset).contentEquals(echo)) return null
        val length = replyLength(frame.u8(offset), frame.u8(offset + 1))
        if (frame.u8(offset) != status || (status == 0xCA && frame.u8(offset + 1) != 1) || length > MAX_PAYLOAD || frame.size != offset + 3 + length) return null
        val sum = (offset until frame.lastIndex).sumOf { frame.u8(it) } and 255
        return if (sum == frame.u8(frame.lastIndex)) frame.copyOfRange(offset + 2, frame.lastIndex) else null
    }

    fun decode(payload: ByteArray): Mp48Reading? {
        if (payload.size != 34) return null
        // Offsets and units from Mp48Protocol and Mp48TelemetryScale at c3f15fad.
        val flags = payload.u8(11)
        val fuel = when (flags) {
            0x80, 0xA0 -> Fuel.PETROL
            0x90, 0xB0 -> Fuel.GNV
            0x88, 0xA8 -> Fuel.TRANSITION
            else -> Fuel.UNKNOWN
        }
        val rpm = payload.u16(0)
        val map = payload.u16(17).toShort() / 1000.0
        val petrol = payload.u16(8) * 0.00256
        val gas = payload.u16(6).takeIf { it != 0 }?.times(0.00256)
        val plausible = rpm in 0..9000 && map in 0.0..2.5 && petrol <= 40.0 && (gas ?: 0.0) <= 50.0
        val cutoff = rpm >= 1200 && petrol < 0.70 && gas == null && map < 0.35
        return Mp48Reading(
            rpm = rpm,
            mapBar = map,
            gasPressureBar = payload.u16(14) / 800.0,
            petrolMs = petrol,
            gasMs = gas,
            fuel = if (plausible && rpm > 0 && !cutoff) fuel else Fuel.UNKNOWN,
            fuelByte = flags,
        )
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 255
    private fun ByteArray.u16(index: Int): Int = u8(index) or (u8(index + 1) shl 8)
}

[executed on device: S20-FE (abd78d23-57fd-4034-814b-4a26d96e02ff)]