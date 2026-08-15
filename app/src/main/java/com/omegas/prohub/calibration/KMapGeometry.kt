package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KMapGeometryProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Geometria física nativa do mapa K para uma sessão MP48 específica.
 *
 * O fingerprint representa somente os vetores físicos. Sessão, horário e fonte
 * pertencem à proveniência e não alteram a igualdade da geometria em si.
 */
data class KMapGeometrySnapshot(
    val timeAxisRaw: List<Int>,
    val timeAxisMs: List<Double>,
    val rpmAxis: List<Int>,
    val sessionId: Long,
    val source: String,
    val readAtEpochMs: Long,
    val fingerprint: String = fingerprint(timeAxisRaw, rpmAxis),
) {
    init {
        require(timeAxisRaw.size == KMapGeometryProtocol.POINT_COUNT) { "TEMPI_PER_K incompleto" }
        require(timeAxisMs.size == KMapGeometryProtocol.POINT_COUNT) { "Eixo Tinj físico incompleto" }
        require(rpmAxis.size == KMapGeometryProtocol.POINT_COUNT) { "GIRI_PER_K incompleto" }
        require(sessionId > 0L) { "Sessão MP48 inválida" }
        require(source.isNotBlank()) { "Proveniência da geometria ausente" }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("source", source)
        .put("sessionId", sessionId)
        .put("readAtEpochMs", readAtEpochMs)
        .put("fingerprint", fingerprint)
        .put("timeAxisAddress", "0x%04X".format(KMapGeometryProtocol.TIME_AXIS_ADDRESS))
        .put("rpmAxisAddress", "0x%04X".format(KMapGeometryProtocol.RPM_AXIS_ADDRESS))
        .put("timeAxisRaw", JSONArray(timeAxisRaw))
        .put("petrolBins", JSONArray(timeAxisMs))
        .put("rpmBins", JSONArray(rpmAxis))
        .put("runtimeAuthority", true)

    companion object {
        const val SCHEMA = "mp48-k-map-geometry-v1"

        fun fingerprint(timeAxisRaw: List<Int>, rpmAxis: List<Int>): String {
            require(timeAxisRaw.size == KMapGeometryProtocol.POINT_COUNT) { "TEMPI_PER_K incompleto" }
            require(rpmAxis.size == KMapGeometryProtocol.POINT_COUNT) { "GIRI_PER_K incompleto" }
            val canonical = buildString {
                append("schema=").append(SCHEMA)
                append(";time=").append(timeAxisRaw.joinToString(","))
                append(";rpm=").append(rpmAxis.joinToString(","))
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

/**
 * Leitura host-atômica dos dois vetores nativos de geometria.
 *
 * Não escreve eixos. Se ACK, shape ou sessão não puderem ser provados, lança
 * erro e não produz snapshot parcialmente conhecido.
 */
class KMapGeometryReader(
    private val serial: Mp48SerialScheduler,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun read(expectedSessionId: Long = currentSessionId()): KMapGeometrySnapshot {
        require(expectedSessionId > 0L) { "Sessão MP48 inválida" }
        if (!serial.isConnected() || serial.currentSessionId() != expectedSessionId) {
            throw IllegalStateException("Sessão MP48 mudou antes da leitura da geometria")
        }

        val (timeRaw, rpmRaw) = serial.unit(
            reason = "leitura nativa da geometria do mapa K",
            expectedSessionId = expectedSessionId,
            workClass = Mp48WorkClass.READ_ONLY,
            telemetryAfter = true,
            waitTimeoutMs = 4_000L,
        ) { unit ->
            if (unit.sessionId != expectedSessionId) {
                throw IllegalStateException("Sessão MP48 mudou dentro da leitura da geometria")
            }
            val time = readAxis(
                unit,
                KMapGeometryProtocol.readTimeAxis(),
                "TEMPI_PER_K 0x0037",
            )
            val rpm = readAxis(
                unit,
                KMapGeometryProtocol.readRpmAxis(),
                "GIRI_PER_K 0x003D",
            )
            time to rpm
        }

        if (!serial.isConnected() || serial.currentSessionId() != expectedSessionId) {
            throw IllegalStateException("Sessão MP48 mudou durante a leitura da geometria")
        }

        return KMapGeometrySnapshot(
            timeAxisRaw = timeRaw.toList(),
            timeAxisMs = timeRaw.map(KMapGeometryProtocol::timeMsFromRaw),
            rpmAxis = rpmRaw.toList(),
            sessionId = expectedSessionId,
            source = "ECU_NATIVE_GEOMETRY_READ",
            readAtEpochMs = nowEpochMs(),
        )
    }

    private fun readAxis(
        unit: Mp48SerialUnit,
        request: ByteArray,
        label: String,
    ): IntArray {
        val reply = unit.transaction(
            request = request,
            reason = "leitura $label",
            timeoutMs = 1_200,
            purgeBefore = false,
        )
        requireAck(reply, label)
        return KMapGeometryProtocol.decodeRawAxis(reply.payload)
    }

    private fun requireAck(reply: UsbProtocolReply, label: String) {
        if (!reply.ok) {
            throw IllegalStateException(reply.error.ifBlank { "ECU não confirmou $label" })
        }
        if (reply.status != Mp48Protocol.STATUS_ACK) {
            throw IllegalStateException("Resposta inesperada 0x%02X em $label".format(reply.status))
        }
    }

    private fun currentSessionId(): Long =
        serial.currentSessionId().takeIf { serial.isConnected() && it > 0L }
            ?: throw IllegalStateException("USB desconectado")
}
