package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.ecu.Mp48GeometryCodec
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply

data class CompositeCalibrationRawRead(
    val usbSessionId: Long,
    val autoMatchCountStart: Int,
    val autoMatchCountEnd: Int,
    val curveAxisRaw: List<Int>,
    val mulActStartRaw: List<Int>,
    val mulActEndRaw: List<Int>,
    val mapTimeAxisRaw: List<Int>,
    val mapRpmAxisRaw: List<Int>,
    val mapRowsRaw: List<List<Int>>,
    val generationCheck: CalibrationGenerationCheck,
    val elapsedMs: Long = 0L,
)

/** Aquisição física composta e somente leitura da calibração corrente. */
class CompositeCalibrationReader(
    private val serial: Mp48SerialScheduler,
) {
    companion object {
        // 20 transações × 1,2 s de timeout interno + margem de fila/overhead.
        // Não adiciona espera artificial: é somente o teto para não abandonar
        // o Future enquanto a unidade serial ainda pode estar executando.
        const val WAIT_TIMEOUT_MS = 30_000L
        private const val TRANSACTION_TIMEOUT_MS = 1_200
    }

    fun readAtSessionStart(expectedSessionId: Long): CompositeCalibrationRawRead {
        require(expectedSessionId > 0L) { "Sessão USB inválida" }
        require(serial.isConnected()) { "USB desconectado" }
        require(serial.currentSessionId() == expectedSessionId) { "Sessão USB mudou antes da leitura composta" }
        val startedNs = System.nanoTime()

        val result = serial.unit(
            reason = "snapshot físico composto da calibração",
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
            telemetryAfter = true,
            waitTimeoutMs = WAIT_TIMEOUT_MS,
        ) { unit ->
            require(unit.sessionId == expectedSessionId) { "Unit serial pertence a outra sessão USB" }
            val countStart = readAutoMatchCount(unit)
            val curveAxis = readCurveVector(unit, KFactorProtocol.readPetrolAxis(), "Curve axis")
            val mulActStart = readCurveVector(unit, KFactorProtocol.readFactors(), "MUL_ACT start")
            val timeAxis = readGeometryAxis(unit, Mp48Protocol.readKPetrolAxis(), "TEMPI_PER_K")
            val rpmAxis = readGeometryAxis(unit, Mp48Protocol.readKRpmAxis(), "GIRI_PER_K")
            val mapRows = List(Mp48Protocol.MAP_ROWS) { row -> readMapRow(unit, row).toList() }
            val mulActEnd = readCurveVector(unit, KFactorProtocol.readFactors(), "MUL_ACT end")
            val countEnd = readAutoMatchCount(unit)
            val generation = CalibrationGenerationGuard.evaluate(
                countStart = countStart,
                countEnd = countEnd,
                mulActStart = mulActStart,
                mulActEnd = mulActEnd,
            )
            CompositeCalibrationRawRead(
                usbSessionId = unit.sessionId,
                autoMatchCountStart = countStart,
                autoMatchCountEnd = countEnd,
                curveAxisRaw = curveAxis.toList(),
                mulActStartRaw = mulActStart.toList(),
                mulActEndRaw = mulActEnd.toList(),
                mapTimeAxisRaw = timeAxis.toList(),
                mapRpmAxisRaw = rpmAxis.toList(),
                mapRowsRaw = mapRows,
                generationCheck = generation,
            )
        }

        require(serial.isConnected() && serial.currentSessionId() == expectedSessionId) {
            "Sessão USB mudou depois da leitura composta"
        }
        require(result.usbSessionId == expectedSessionId) { "Snapshot composto pertence a outra sessão" }
        val elapsedMs = ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(0L)
        return result.copy(elapsedMs = elapsedMs)
    }

    private fun readAutoMatchCount(unit: Mp48SerialUnit): Int {
        val reply = unit.transaction(
            AutoCalProtocol.read(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED),
            "NUM_AUTOMATCH_EXECUTED",
            TRANSACTION_TIMEOUT_MS,
            purgeBefore = false,
        )
        requireAck(reply, "NUM_AUTOMATCH_EXECUTED")
        return AutoCalProtocol.decode(
            AutoCalProtocol.NUM_AUTOMATCH_EXECUTED,
            reply.status,
            reply.payload,
        ).rawValues.single()
    }

    private fun readCurveVector(unit: Mp48SerialUnit, request: ByteArray, reason: String): IntArray {
        val reply = unit.transaction(request, reason, TRANSACTION_TIMEOUT_MS, purgeBefore = false)
        requireAck(reply, reason)
        require(reply.payload.size == KFactorProtocol.PAYLOAD_SIZE) {
            "$reason exige exatamente ${KFactorProtocol.PAYLOAD_SIZE} bytes; recebidos ${reply.payload.size}"
        }
        return KFactorProtocol.decodeRawPoints(reply.payload)
    }

    private fun readGeometryAxis(unit: Mp48SerialUnit, request: ByteArray, reason: String): IntArray {
        val reply = unit.transaction(request, reason, TRANSACTION_TIMEOUT_MS, purgeBefore = false)
        requireAck(reply, reason)
        return Mp48GeometryCodec.decodeAxisRaw(reply.payload)
    }

    private fun readMapRow(unit: Mp48SerialUnit, row: Int): IntArray {
        val reply = unit.transaction(
            Mp48Protocol.readKRow(row),
            "Mapa K row $row",
            TRANSACTION_TIMEOUT_MS,
            purgeBefore = false,
        )
        requireAck(reply, "Mapa K row $row")
        require(reply.payload.size == Mp48Protocol.MAP_COLUMNS) {
            "Mapa K row $row exige exatamente ${Mp48Protocol.MAP_COLUMNS} bytes; recebidos ${reply.payload.size}"
        }
        return IntArray(Mp48Protocol.MAP_COLUMNS) { column -> reply.payload[column].toInt() and 0xFF }
    }

    private fun requireAck(reply: UsbProtocolReply, reason: String) {
        require(reply.ok) { reply.error.ifBlank { "ECU não confirmou $reason" } }
        require(reply.status == Mp48Protocol.STATUS_ACK) {
            "Resposta inesperada 0x%02X em $reason".format(reply.status)
        }
    }
}
