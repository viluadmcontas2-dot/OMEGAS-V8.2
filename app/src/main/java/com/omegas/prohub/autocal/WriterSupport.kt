package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import org.json.JSONObject
import java.util.concurrent.Executors

internal fun writerExecutor(name: String) = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, name).apply { isDaemon = true }
}

internal fun Mp48SerialScheduler.sessionOrThrow(): Long = currentSessionId().takeIf { isConnected() && it > 0L }
    ?: throw IllegalStateException("USB desconectado")

internal fun Mp48SerialScheduler.currentSessionOrZero(): Long = if (isConnected()) currentSessionId() else 0L

internal fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

internal fun fail(message: String) = JSONObject().put("ok", false).put("error", message)

internal fun stopped(publish: (JSONObject) -> Unit): JSONObject {
    val message = "O app está encerrando; nada foi enviado."
    publish(JSONObject().put("state", "ERROR").put("message", message))
    return fail(message)
}

internal fun Mp48SerialUnit.writerTransaction(frame: ByteArray, reason: String) =
    transaction(frame, reason, 900, purgeBefore = false)

internal fun Mp48SerialUnit.readPayload(frame: ByteArray, reason: String, errorMessage: String): IntArray {
    val reply = writerTransaction(frame, reason)
    if (!reply.ok || reply.status != Mp48Protocol.STATUS_ACK) throw IllegalStateException(reply.error.ifBlank { errorMessage })
    return IntArray(reply.payload.size) { reply.payload[it].toInt() and 0xFF }
}

internal fun <T> Mp48SerialUnit.writeFrame(
    frame: ByteArray,
    reason: String,
    result: (Boolean, Boolean, String, String) -> T,
): T {
    val reply = try {
        writerTransaction(frame, reason)
    } catch (error: Exception) {
        return result(false, true, "", error.message ?: error.javaClass.simpleName)
    }
    val acked = reply.ok && reply.status == Mp48Protocol.STATUS_ACK
    val detail = if (acked) "ACK" else "status 0x%02X %s".format(reply.status and 0xFF, reply.error).trim()
    return result(acked, !acked && reply.rawResponse.isEmpty() && reply.status < 0, hex(reply.rawResponse), detail)
}
