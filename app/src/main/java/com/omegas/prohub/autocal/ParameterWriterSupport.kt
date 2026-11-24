package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONObject
import java.util.concurrent.Executors

internal fun writerExecutor(name: String) = Executors.newSingleThreadExecutor { task ->
    Thread(task, name).apply { isDaemon = true }
}

internal fun Mp48SerialScheduler.requireWriterSession(): Long =
    currentSessionId().takeIf { isConnected() && it > 0L } ?: throw IllegalStateException("USB desconectado")

internal fun Mp48SerialScheduler.writerSessionOrZero(): Long = if (isConnected()) currentSessionId() else 0L

internal fun writerFrameHex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

internal fun writerFailure(message: String) = JSONObject().put("ok", false).put("error", message)

internal fun stoppedWriter(lock: Any, clear: (String) -> Unit): JSONObject {
    val message = "O app está encerrando; nada foi enviado."
    synchronized(lock) { clear(message) }
    return writerFailure(message)
}

internal val UsbProtocolReply.parameterAcknowledged: Boolean
    get() = ok && status == Mp48Protocol.STATUS_ACK

internal fun Mp48SerialUnit.parameterTransaction(frame: ByteArray, reason: String) =
    transaction(frame, reason, 900, purgeBefore = false)

internal data class ParameterWriteReply(val acked: Boolean, val transportError: Boolean, val rawHex: String, val message: String)

internal fun Mp48SerialUnit.writeParameter(frame: ByteArray, reason: String): ParameterWriteReply = try {
    val reply = parameterTransaction(frame, reason)
    val acked = reply.parameterAcknowledged
    ParameterWriteReply(
        acked,
        !acked && reply.rawResponse.isEmpty() && reply.status < 0,
        writerFrameHex(reply.rawResponse),
        if (acked) "ACK" else "status 0x%02X %s".format(reply.status and 0xFF, reply.error).trim(),
    )
} catch (error: Exception) {
    ParameterWriteReply(false, true, "", error.message ?: error.javaClass.simpleName)
}
