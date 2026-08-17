package com.omegas.prohub.ecu

import org.json.JSONObject

/**
 * Fonte única do protocolo MP48 observado nos logs oficiais do Landi Omegas.
 *
 * Nenhuma temporização artificial é definida aqui. O protocolo é orientado por
 * transações: envia, recebe eco, recebe resposta, valida e libera o próximo
 * comando imediatamente.
 */
object Mp48Protocol {
    val CMD_INIT_1 = byteArrayOf(0x00, 0x02, 0x02)
    val CMD_INIT_2 = byteArrayOf(0x01, 0x00, 0x3A, 0x3B)
    val CMD_IDENTIFY = byteArrayOf(0x00, 0x25, 0x25)
    val CMD_TELEMETRY = byteArrayOf(0x48, 0x01, 0x49)
    val CMD_SECONDARY_STATUS = byteArrayOf(0x48, 0x08, 0x50)
    val CMD_DISCONNECT = byteArrayOf(0x00, 0x01, 0x01)

    const val STATUS_ACK = 0x53
    const val STATUS_EXTENDED = 0xCA
    const val TELEMETRY_PAYLOAD_SIZE = 34
    const val TELEMETRY_SCALE_SCHEMA = "mp48-progbase-v2"
    const val MAP_K_ADDRESS = 0x0054
    const val MAP_ROWS = 13
    const val MAP_COLUMNS = 12
    const val TEMPI_PER_K_ADDRESS = 0x0037
    const val GIRI_PER_K_ADDRESS = 0x003D

    fun readKPetrolAxis(): ByteArray = frame(byteArrayOf(0x29, 0x37, 0x00))

    fun readKRpmAxis(): ByteArray = frame(byteArrayOf(0x29, 0x3D, 0x00))

    fun readKRow(row: Int): ByteArray {
        require(row in 0 until MAP_ROWS) { "Linha K inválida: $row" }
        return frame(byteArrayOf(0x2A, 0x54, 0x00, row.toByte()))
    }

    fun writeKCell(row: Int, column: Int, value: Int): ByteArray {
        require(row in 0 until MAP_ROWS) { "Linha K inválida: $row" }
        require(column in 0 until MAP_COLUMNS) { "Coluna K inválida: $column" }
        require(value in 0..255) { "Valor K inválido: $value" }
        return frame(
            byteArrayOf(
                0x14,
                0x54,
                0x00,
                row.toByte(),
                column.toByte(),
                value.toByte(),
            ),
        )
    }

    fun kInsertionMode(enabled: Boolean): ByteArray {
        val mode = if (enabled) 0x2C else 0x24
        return frame(byteArrayOf(0x35, 0x03, 0x00, 0x86.toByte(), mode.toByte(), 0x51, 0x10))
    }

    fun frame(body: ByteArray): ByteArray = body + byteArrayOf(checksum(body).toByte())

    fun checksum(bytes: ByteArray): Int = bytes.sumOf { it.toInt() and 0xFF } and 0xFF

    fun decodeTelemetry(payload: ByteArray, capturedAtElapsedMs: Long): Mp48Telemetry {
        if (payload.size < TELEMETRY_PAYLOAD_SIZE) {
            throw IllegalArgumentException("Telemetria MP48 exige no mínimo $TELEMETRY_PAYLOAD_SIZE bytes; recebidos ${payload.size}")
        }
        var bestFit: Mp48Telemetry? = null
        for (i in 0..payload.size - TELEMETRY_PAYLOAD_SIZE) {
            val telemetry = decodeStrict(payload.copyOfRange(i, i + TELEMETRY_PAYLOAD_SIZE), capturedAtElapsedMs)
            if (telemetry.plausible) return telemetry
            if (bestFit == null) bestFit = telemetry
        }
        return bestFit ?: throw IllegalArgumentException("Nenhuma telemetria extraída")
    }

    private fun decodeStrict(payload: ByteArray, capturedAtElapsedMs: Long): Mp48Telemetry {

        val rpm = u16le(payload, 0)
        val gasRaw = u16le(payload, 6)
        val petrolRaw = u16le(payload, 8)
        val fuelByte = u8(payload, 11)
        val waterRaw = u8(payload, 12)
        val levelRaw = u8(payload, 13)
        val gasPressureRaw = u16le(payload, 14)
        val gasTemperatureRaw = u8(payload, 16)
        val mapRaw = u16le(payload, 17)
        val unknownRaw19 = u8(payload, 19)
        val gas2Raw = u16le(payload, 24)
        val petrol2Raw = u16le(payload, 28)

        // O ProgBase exibe as contagens brutas dos bancos sem subtrações.
        val petrolCounts = petrolRaw
        val petrolMs = Mp48TelemetryScale.injectionMs(petrolRaw)
        val gasMs = if (gasRaw == 0) null else Mp48TelemetryScale.injectionMs(gasRaw)
        val gas2Ms = if (gas2Raw == 0) null else Mp48TelemetryScale.injectionMs(gas2Raw)
        val petrol2Ms = if (petrol2Raw == 0) null else Mp48TelemetryScale.injectionMs(petrol2Raw)
        val waterC = Mp48TelemetryScale.waterC(waterRaw)
        val gasC = Mp48TelemetryScale.gasC(gasTemperatureRaw)
        val gasPressureAbsBar = Mp48TelemetryScale.gasPressureAbsBar(gasPressureRaw)
        val mapBar = Mp48TelemetryScale.mapBar(mapRaw)
        val pressureDiffBar = gasPressureAbsBar - mapBar

        val physicalCutoff = rpm >= 1_200 && petrolMs < 0.70 && gasRaw == 0 && mapBar < 0.35
        val fuel = when {
            rpm <= 0 || fuelByte == 0x00 -> Mp48Fuel.ENGINE_OFF
            physicalCutoff -> Mp48Fuel.CUTOFF
            fuelByte == 0x80 -> Mp48Fuel.PETROL
            fuelByte == 0x88 -> Mp48Fuel.TRANSITION
            fuelByte == 0x90 -> Mp48Fuel.CNG
            else -> Mp48Fuel.UNKNOWN
        }
        val state = when (fuel) {
            Mp48Fuel.ENGINE_OFF -> "ECU_SEM_MOTOR"
            Mp48Fuel.CUTOFF -> "CUTOFF_DESACELERACAO"
            Mp48Fuel.PETROL -> if (petrolRaw > 0) "GASOLINA_ATIVA" else "GASOLINA_SEM_PULSO"
            Mp48Fuel.TRANSITION -> if (gasRaw > 0) "TRANSICAO_COM_PULSO_GNV" else "AGUARDANDO_COMUTACAO_GNV"
            Mp48Fuel.CNG -> "GNV_ATIVO"
            Mp48Fuel.UNKNOWN -> "ESTADO_0x%02X".format(fuelByte)
        }
        val basePlausibilityReasons = buildList {
            if (rpm !in 0..9000) add("RPM_OUT_OF_RANGE")
            if (petrolMs !in 0.0..40.0) add("PETROL_INJECTION_OUT_OF_RANGE")
            if (gasMs != null && gasMs !in 0.0..50.0) add("GAS_INJECTION_OUT_OF_RANGE")
            if (gas2Ms != null && gas2Ms !in 0.0..50.0) add("GAS_2_INJECTION_OUT_OF_RANGE")
            if (petrol2Ms != null && petrol2Ms !in 0.0..40.0) add("PETROL_2_INJECTION_OUT_OF_RANGE")
            if (waterC !in -40..150) add("WATER_TEMPERATURE_OUT_OF_RANGE")
            if (gasC !in -40..150) add("GAS_TEMPERATURE_OUT_OF_RANGE")
            if (mapBar !in 0.0..2.5) add("MAP_OUT_OF_RANGE")
        }
        val cngPressureReasons = buildList {
            if (gasPressureAbsBar !in 0.0..5.0) add("GAS_PRESSURE_ABSOLUTE_OUT_OF_RANGE")
            if (pressureDiffBar !in -0.30..4.5) add("GAS_PRESSURE_DIFFERENTIAL_OUT_OF_RANGE")
        }
        val basePlausible = basePlausibilityReasons.isEmpty()
        val cngPressurePlausible = cngPressureReasons.isEmpty()
        // A pressão residual do trilho é diagnóstico em gasolina. Ela só
        // participa da aceitação física quando a ECU confirma GNV ativo.
        val plausibilityReasons = basePlausibilityReasons +
            if (fuel == Mp48Fuel.CNG) cngPressureReasons else emptyList()
        val plausible = basePlausible && (fuel != Mp48Fuel.CNG || cngPressurePlausible)

        return Mp48Telemetry(
            capturedAtElapsedMs = capturedAtElapsedMs,
            rpm = rpm,
            levelRaw = levelRaw,
            gasRaw = gasRaw,
            gasMsDiagnostic = gasMs,
            petrolRaw = petrolRaw,
            petrolCounts = petrolCounts,
            petrolMs = petrolMs,
            dynamicCorrection = unknownRaw19,
            fuelByte = fuelByte,
            fuel = fuel,
            state = state,
            waterRaw = waterRaw,
            waterC = waterC,
            gasC = gasC,
            gasPressureRaw = gasPressureRaw,
            gasPressureAbsBar = gasPressureAbsBar,
            mapRaw = mapRaw,
            mapBar = mapBar,
            pressureDiffBar = pressureDiffBar,
            plausible = plausible,
            basePlausible = basePlausible,
            cngPressurePlausible = cngPressurePlausible,
            plausibilityReasons = plausibilityReasons,
            gasTemperatureRaw = gasTemperatureRaw,
            unknownRaw19 = unknownRaw19,
            gas2Raw = gas2Raw,
            gas2MsDiagnostic = gas2Ms,
            petrol2Raw = petrol2Raw,
            petrol2MsDiagnostic = petrol2Ms,
        )
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF

    private fun u16le(bytes: ByteArray, offset: Int): Int =
        u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)
}

enum class Mp48Fuel(val wireName: String) {
    ENGINE_OFF("DESLIGADO"),
    PETROL("GASOLINA"),
    TRANSITION("TRANSICAO"),
    CNG("GNV"),
    CUTOFF("CUTOFF"),
    UNKNOWN("DESCONHECIDO"),
}

data class Mp48Telemetry(
    val capturedAtElapsedMs: Long,
    val rpm: Int,
    val levelRaw: Int,
    val gasRaw: Int,
    val gasMsDiagnostic: Double?,
    val petrolRaw: Int,
    val petrolCounts: Int,
    val petrolMs: Double,
    /** Compatibilidade de API: representa apenas o byte bruto 19; não é uma correção. */
    val dynamicCorrection: Int,
    val fuelByte: Int,
    val fuel: Mp48Fuel,
    val state: String,
    val waterRaw: Int,
    val waterC: Int,
    val gasC: Int,
    val gasPressureRaw: Int,
    val gasPressureAbsBar: Double,
    val mapRaw: Int,
    val mapBar: Double,
    val pressureDiffBar: Double,
    val plausible: Boolean,
    val basePlausible: Boolean = plausible,
    val cngPressurePlausible: Boolean = plausible,
    val plausibilityReasons: List<String> = emptyList(),
    val gasTemperatureRaw: Int = gasC + Mp48TelemetryScale.GAS_TEMPERATURE_OFFSET_C,
    val unknownRaw19: Int = dynamicCorrection,
    val gas2Raw: Int = 0,
    val gas2MsDiagnostic: Double? = null,
    val petrol2Raw: Int = 0,
    val petrol2MsDiagnostic: Double? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("telemetry_scale_schema", Mp48Protocol.TELEMETRY_SCALE_SCHEMA)
        .put("captured_elapsed_ms", capturedAtElapsedMs)
        .put("rpm", rpm)
        .put("level_raw", levelRaw)
        .put("level_percentage", Mp48TelemetryScale.levelPercentage(levelRaw))
        .put("gas_raw", gasRaw)
        .put("gas_ms_diagnostic", gasMsDiagnostic ?: JSONObject.NULL)
        .put("gas_pulse_present", gasRaw > 0)
        .put("petrol_raw", petrolRaw)
        .put("petrol_counts", petrolCounts)
        .put("petrol_ms", petrolMs)
        .put("unknown_raw_19", unknownRaw19)
        .put("dynamic_correction", unknownRaw19)
        .put("fuel_byte", fuelByte)
        .put("fuel", fuel.wireName)
        .put("state", state)
        .put("water_raw", waterRaw)
        .put("water_c", waterC)
        .put("gas_temperature_raw", gasTemperatureRaw)
        .put("gas_c", gasC)
        .put("gas_pressure_raw", gasPressureRaw)
        .put("gas_pressure_abs_bar", gasPressureAbsBar)
        .put("map_raw", mapRaw)
        .put("load_bar", mapBar)
        .put("pressure_diff_bar", pressureDiffBar)
        .put("gas_2_raw", gas2Raw)
        .put("gas_2_ms_diagnostic", gas2MsDiagnostic ?: JSONObject.NULL)
        .put("petrol_2_raw", petrol2Raw)
        .put("petrol_2_ms_diagnostic", petrol2MsDiagnostic ?: JSONObject.NULL)
        .put("plausible", plausible)
        .put("base_plausible", basePlausible)
        .put("cng_pressure_plausible", cngPressurePlausible)
        .put("plausibility_reasons", org.json.JSONArray(plausibilityReasons))
}