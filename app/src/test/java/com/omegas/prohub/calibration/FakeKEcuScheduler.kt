package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.KFactorProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Central falsa para os escritores K: responde por byte (Mapa K, K insertion, Curva K), registra cada quadro
 * enviado e deixa o teste segurar uma transação (por trecho do motivo) até soltar a trava.
 */
class FakeKEcuScheduler : Mp48SerialScheduler {
    val map = Array(Mp48Protocol.MAP_ROWS) { IntArray(Mp48Protocol.MAP_COLUMNS) { 120 } }
    val factors = IntArray(KFactorProtocol.POINT_COUNT) { 16_384 }
    val sent: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())
    /** Resposta forçada por quadro exato (ex.: ativar K insertion sem resposta). */
    val forced = Collections.synchronizedMap(mutableMapOf<String, UsbProtocolReply>())
    /** Resposta forçada por trecho do motivo (ex.: só a ativação da correção falha). */
    val forcedByReason = Collections.synchronizedMap(mutableMapOf<String, UsbProtocolReply>())
    /** Motivo que contém a chave espera a trava antes de responder. */
    val holds = Collections.synchronizedMap(mutableMapOf<String, CountDownLatch>())
    /** Avisa quando uma transação segurada começou a esperar. */
    val entered = Collections.synchronizedMap(mutableMapOf<String, CountDownLatch>())

    override fun isConnected(): Boolean = true
    override fun currentSessionId(): Long = 7L

    fun count(frame: ByteArray): Int = synchronized(sent) { sent.count { it.contentEquals(frame) } }

    fun hold(reasonPart: String) {
        holds[reasonPart] = CountDownLatch(1)
        entered[reasonPart] = CountDownLatch(1)
    }

    fun awaitEntered(reasonPart: String) = check(entered.getValue(reasonPart).await(5, TimeUnit.SECONDS)) { "não chegou em $reasonPart" }

    fun release(reasonPart: String) = holds.getValue(reasonPart).countDown()

    private fun waitIfHeld(reason: String) {
        holds.entries.firstOrNull { reason.contains(it.key) }?.let { (key, latch) ->
            entered[key]?.countDown()
            latch.await(10, TimeUnit.SECONDS)
        }
    }

    private fun answer(request: ByteArray, reason: String): UsbProtocolReply {
        waitIfHeld(reason)
        sent += request.copyOf()
        forced[key(request)]?.let { return it }
        forcedByReason.entries.firstOrNull { reason.contains(it.key) }?.let { return it.value }
        val ack = { payload: ByteArray -> UsbProtocolReply(ok = true, status = Mp48Protocol.STATUS_ACK, payload = payload, request = request) }
        repeat(Mp48Protocol.MAP_ROWS) { row ->
            if (request.contentEquals(Mp48Protocol.readKRow(row))) return ack(ByteArray(Mp48Protocol.MAP_COLUMNS) { map[row][it].toByte() })
        }
        if (request.size == 7 && request[0] == 0x14.toByte() && request[1] == 0x54.toByte()) {
            map[request[3].toInt()][request[4].toInt()] = request[5].toInt() and 0xFF
            return ack(byteArrayOf())
        }
        if (request.contentEquals(Mp48Protocol.kInsertionMode(true)) || request.contentEquals(Mp48Protocol.kInsertionMode(false))) {
            return ack(byteArrayOf())
        }
        if (request.contentEquals(KFactorProtocol.readFactors())) return ack(points(factors))
        if (request.contentEquals(KFactorProtocol.readPetrolAxis())) return ack(points(IntArray(KFactorProtocol.POINT_COUNT) { 512 * (it + 1) }))
        repeat(KFactorProtocol.POINT_COUNT) { index ->
            for (raw in FACTOR_TARGETS) {
                if (request.contentEquals(KFactorProtocol.writeFactor(index, raw))) { factors[index] = raw; return ack(byteArrayOf()) }
            }
        }
        return UsbProtocolReply(ok = false, error = "quadro desconhecido na central falsa", request = request)
    }

    private fun points(values: IntArray) = ByteArray(values.size * 2) { i ->
        val v = values[i / 2]
        (if (i % 2 == 0) v and 0xFF else (v shr 8) and 0xFF).toByte()
    }

    override fun transaction(
        request: ByteArray,
        reason: String,
        timeoutMs: Int,
        purgeBefore: Boolean,
        expectedSessionId: Long,
        workClass: Mp48WorkClass,
        telemetryAfter: Boolean,
    ): UsbProtocolReply = answer(request, reason)

    override fun <T> unit(
        reason: String,
        expectedSessionId: Long,
        workClass: Mp48WorkClass,
        telemetryAfter: Boolean,
        waitTimeoutMs: Long,
        block: (Mp48SerialUnit) -> T,
    ): T = waitIfHeld(reason).let { block(object : Mp48SerialUnit {
        override val sessionId: Long = 7L
        override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean) = answer(request, reason)
    }) }

    companion object {
        /** Valores Q14 que os testes gravam na Curva K (a central falsa só reconhece estes). */
        val FACTOR_TARGETS = listOf(16_384, 18_000)
        fun key(frame: ByteArray) = frame.joinToString(" ") { "%02X".format(it) }
        val TIMEOUT = UsbProtocolReply(ok = false, error = "Tempo esgotado sem resposta da central")
    }
}
