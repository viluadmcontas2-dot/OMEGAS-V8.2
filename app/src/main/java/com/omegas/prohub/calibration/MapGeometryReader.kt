package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.Mp48GeometryCodec
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply

data class MapGeometryRawRead(
    val unitSessionId: Long,
    val timeAxisRaw: IntArray,
    val rpmAxisRaw: IntArray,
)

/** Lê os dois vetores físicos do Mapa K dentro de uma única unidade serial. */
class MapGeometryReader(
    private val serial: Mp48SerialScheduler,
) {
    fun readRaw(expectedSessionId: Long): MapGeometryRawRead =
        serial.unit(
            reason = "leitura dos eixos físicos do Mapa K",
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
            telemetryAfter = true,
        ) { unit ->
            val timeRaw = decodeAxis(
                unit.transaction(
                    request = Mp48Protocol.readKPetrolAxis(),
                    reason = "leitura TEMPI_PER_K",
                    timeoutMs = 800,
                    purgeBefore = false,
                ),
            )
            val rpmRaw = decodeAxis(
                unit.transaction(
                    request = Mp48Protocol.readKRpmAxis(),
                    reason = "leitura GIRI_PER_K",
                    timeoutMs = 800,
                    purgeBefore = false,
                ),
            )
            MapGeometryRawRead(
                unitSessionId = unit.sessionId,
                timeAxisRaw = timeRaw,
                rpmAxisRaw = rpmRaw,
            )
        }

    private fun decodeAxis(reply: UsbProtocolReply): IntArray {
        if (!reply.ok) {
            throw IllegalStateException(reply.error.ifBlank { "ECU não confirmou a leitura de geometria" })
        }
        if (reply.status != Mp48Protocol.STATUS_ACK) {
            throw IllegalStateException("Resposta inesperada 0x%02X na leitura de geometria".format(reply.status))
        }
        return Mp48GeometryCodec.decodeAxisRaw(reply.payload)
    }
}
